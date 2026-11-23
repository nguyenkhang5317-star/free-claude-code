package mc.core;

import mc.content.Blocks;
import mc.util.Json;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/**
 * Region file store (Section 2.6 / Appendix AZ). One file per 32x32 chunks: an 8 KiB header of 1024 location
 * entries (sectorOffset<<8 | sectorCount) followed by 1024 CRC32 values, then 4 KiB sectors holding
 * [int length][byte compression=2 zlib][payload]. A record whose CRC does not match is treated as corrupt:
 * the region file is copied to *.corrupt (quarantine) and the chunk regenerates.
 */
public final class RegionStore implements ISaveStore {
    public final Path root;
    private final Map<Path, RandomAccessFile> open = new HashMap<>();
    public int corruptCount;

    private static final int MAGIC = 0x4D435231; // "MCR1"
    private static final int FORMAT = 2;

    public RegionStore(Path root) { this.root = root; }

    private Path regionPath(Dimension d, int rx, int rz) { return root.resolve(d.folder()).resolve("r." + rx + "." + rz + ".mcr"); }

    private synchronized RandomAccessFile file(Path p, boolean create) throws IOException {
        RandomAccessFile f = open.get(p);
        if (f != null) return f;
        if (!create && !Files.exists(p)) return null;
        Files.createDirectories(p.getParent());
        f = new RandomAccessFile(p.toFile(), "rw");
        if (f.length() < 8192) {
            f.setLength(8192);
        }
        open.put(p, f);
        return f;
    }

    @Override
    public synchronized Chunk load(Dimension d, int cx, int cz) throws IOException {
        Path p = regionPath(d, cx >> 5, cz >> 5);
        RandomAccessFile f = file(p, false);
        if (f == null) return null;
        int idx = (cx & 31) + (cz & 31) * 32;
        f.seek(idx * 4L);
        int loc = f.readInt();
        if (loc == 0) return null;
        f.seek(4096 + idx * 4L);
        int crc = f.readInt();
        int off = loc >>> 8, cnt = loc & 0xFF;
        try {
            if ((long) (off + cnt) * 4096 > f.length() || off < 2) throw new IOException("record outside file");
            f.seek(off * 4096L);
            int len = f.readInt();
            if (len <= 1 || len > cnt * 4096 - 4) throw new IOException("bad length " + len);
            int comp = f.readByte();
            byte[] buf = new byte[len - 1];
            f.readFully(buf);
            CRC32 c = new CRC32();
            c.update(buf);
            if ((int) c.getValue() != crc) throw new IOException("CRC mismatch");
            if (comp != 2) throw new IOException("unknown compression " + comp);
            return decode(new DataInputStream(new BufferedInputStream(new InflaterInputStream(new ByteArrayInputStream(buf)))), cx, cz);
        } catch (IOException | RuntimeException e) {
            quarantine(p, cx, cz, e);
            return null;
        }
    }

    private void quarantine(Path p, int cx, int cz, Exception e) {
        corruptCount++;
        System.err.println("[save] corrupt chunk " + cx + "," + cz + " in " + p.getFileName() + ": " + e.getMessage() + " (regenerating)");
        try {
            Path q = p.resolveSibling(p.getFileName() + ".corrupt");
            if (!Files.exists(q)) Files.copy(p, q);
            RandomAccessFile f = open.get(p);
            int idx = (cx & 31) + (cz & 31) * 32;
            f.seek(idx * 4L);
            f.writeInt(0);
        } catch (IOException ignored) {
        }
    }

    @Override
    public synchronized void save(Dimension d, Chunk c, List<Map<String, Object>> entities) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream(16384);
        DeflaterOutputStream z = new DeflaterOutputStream(raw, new Deflater(Deflater.BEST_SPEED));
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(z));
        encode(out, c, entities);
        out.close();
        byte[] buf = raw.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(buf);
        Path p = regionPath(d, c.cx >> 5, c.cz >> 5);
        RandomAccessFile f = file(p, true);
        int idx = (c.cx & 31) + (c.cz & 31) * 32;
        f.seek(idx * 4L);
        int loc = f.readInt();
        int need = (buf.length + 5 + 4095) / 4096;
        if (need > 255) throw new IOException("chunk too large");
        int off = loc >>> 8, cnt = loc & 0xFF;
        if (loc == 0 || need > cnt) {
            off = (int) ((f.length() + 4095) / 4096);
            if (off < 2) off = 2;
            cnt = need;
            f.setLength((long) (off + cnt) * 4096);
        }
        f.seek(off * 4096L);
        f.writeInt(buf.length + 1);
        f.writeByte(2);
        f.write(buf);
        f.seek(4096 + idx * 4L);
        f.writeInt((int) crc.getValue());
        f.seek(idx * 4L);
        f.writeInt((off << 8) | cnt);
    }

    @Override
    public synchronized void flush() {
        for (RandomAccessFile f : open.values()) {
            try {
                f.getFD().sync();
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public synchronized void close() {
        for (RandomAccessFile f : open.values()) {
            try {
                f.close();
            } catch (IOException ignored) {
            }
        }
        open.clear();
    }

    // ---------------------------------------------------------------- binary chunk format
    static void encode(DataOutputStream out, Chunk c, List<Map<String, Object>> entities) throws IOException {
        out.writeInt(MAGIC);
        out.writeShort(FORMAT);
        out.writeInt(Level.DATA_VERSION);
        out.writeInt(c.cx);
        out.writeInt(c.cz);
        out.writeLong(c.inhabitedTicks);
        out.write(c.biomes);
        // state palette by name so saves survive registry reordering (Section 2.6)
        for (int s = 0; s < Chunk.SECTIONS; s++) {
            PalettedContainer pc = c.sections[s];
            if (pc.isUniform()) {
                out.writeByte(0);
                out.writeUTF(Blocks.stateName(pc.get(0)));
            } else {
                char[] arr = new char[4096];
                pc.toArray(arr, 0);
                LinkedHashMap<Integer, Integer> pal = new LinkedHashMap<>();
                for (char ch : arr) pal.putIfAbsent((int) ch, pal.size());
                out.writeByte(1);
                out.writeShort(pal.size());
                for (int st : pal.keySet()) out.writeUTF(Blocks.stateName(st));
                boolean wide = pal.size() > 256;
                for (char ch : arr) {
                    int v = pal.get((int) ch);
                    if (wide) out.writeShort(v);
                    else out.writeByte(v);
                }
            }
            out.write(c.skyLight[s]);
            out.write(c.blockLight[s]);
        }
        List<Object> bes = new ArrayList<>();
        for (BlockEntity be : c.blockEntities.values()) bes.add(be.save());
        writeBig(out, Json.write(bes));
        writeBig(out, Json.write(entities == null ? Collections.emptyList() : entities));
    }

    private static void writeBig(DataOutputStream out, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        out.writeInt(b.length);
        out.write(b);
    }

    private static String readBig(DataInputStream in) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > (64 << 20)) throw new IOException("bad string length");
        byte[] b = new byte[n];
        in.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }

    static Chunk decode(DataInputStream in, int cx, int cz) throws IOException {
        if (in.readInt() != MAGIC) throw new IOException("bad magic");
        int fmt = in.readShort();
        if (fmt != FORMAT) throw new IOException("unsupported format " + fmt);
        in.readInt(); // data version
        int rcx = in.readInt(), rcz = in.readInt();
        if (rcx != cx || rcz != cz) throw new IOException("chunk position mismatch");
        Chunk c = new Chunk(cx, cz);
        c.inhabitedTicks = in.readLong();
        in.readFully(c.biomes);
        for (int s = 0; s < Chunk.SECTIONS; s++) {
            int kind = in.readByte();
            if (kind == 0) {
                c.sections[s] = new PalettedContainer(state(in.readUTF()));
            } else {
                int n = in.readShort() & 0xFFFF;
                int[] pal = new int[n];
                for (int i = 0; i < n; i++) pal[i] = state(in.readUTF());
                char[] arr = new char[4096];
                boolean wide = n > 256;
                for (int i = 0; i < 4096; i++) {
                    int v = wide ? in.readShort() & 0xFFFF : in.readByte() & 0xFF;
                    if (v >= n) throw new IOException("palette index out of range");
                    arr[i] = (char) pal[v];
                }
                c.sections[s] = PalettedContainer.fromArray(arr, 0);
            }
            in.readFully(c.skyLight[s]);
            in.readFully(c.blockLight[s]);
        }
        c.recomputeHeightmap();
        for (Object o : Json.arr(Json.parse(readBig(in)))) {
            BlockEntity be = BlockEntity.load(Json.obj(o));
            c.blockEntities.put(BlockEntity.localKey(be.x, be.y, be.z), be);
        }
        for (Object o : Json.arr(Json.parse(readBig(in)))) c.pendingEntities.add(Json.obj(o));
        c.hadEntities = !c.pendingEntities.isEmpty();
        c.status = Chunk.STATUS_FULL;
        c.lightSeamed = false;
        return c;
    }

    private static int state(String name) {
        int s = Blocks.parseState(name);
        return s < 0 ? Blocks.AIR : s; // unknown block ids become air (forward compatibility)
    }

    /** Serialize to bytes and back — used by the round-trip test. */
    public static Chunk roundTrip(Chunk c) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bo);
        encode(out, c, null);
        out.close();
        return decode(new DataInputStream(new ByteArrayInputStream(bo.toByteArray())), c.cx, c.cz);
    }

    // ---------------------------------------------------------------- level & player data (atomic writes)
    public void writeJson(String name, Object data) throws IOException {
        Files.createDirectories(root);
        Path tmp = root.resolve(name + ".tmp"), dst = root.resolve(name);
        Files.write(tmp, Json.write(data).getBytes(StandardCharsets.UTF_8));
        if (Files.exists(dst)) Files.copy(dst, root.resolve(name + "_old"), StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.move(tmp, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, dst, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public Map<String, Object> readJson(String name) {
        for (String n : new String[]{name, name + "_old"}) {
            Path p = root.resolve(n);
            if (!Files.exists(p)) continue;
            try {
                return Json.obj(Json.parse(new String(Files.readAllBytes(p), StandardCharsets.UTF_8)));
            } catch (Exception e) {
                System.err.println("[save] could not read " + p + ": " + e.getMessage());
            }
        }
        return null;
    }
}
