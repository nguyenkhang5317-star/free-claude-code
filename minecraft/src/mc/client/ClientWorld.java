package mc.client;

import mc.content.*;
import mc.core.IChunkAccess;
import mc.net.S2C;

import java.util.*;
import java.util.concurrent.*;

/** The client's read-only copy of the world: immutable chunk snapshots received from the server plus section meshes. */
public final class ClientWorld implements IChunkAccess {
    public static final class CChunk {
        public final int cx, cz;
        public volatile char[] blocks;
        public volatile byte[] light;
        public volatile byte[] biomes;
        public final Mesh[] meshes = new Mesh[16];
        public final boolean[] dirty = new boolean[16];
        public final boolean[] building = new boolean[16];
        public int nonEmptyMask;

        CChunk(int cx, int cz) { this.cx = cx; this.cz = cz; }
    }

    public final ConcurrentHashMap<Long, CChunk> chunks = new ConcurrentHashMap<>();
    public String dimension = "minecraft:overworld";
    public boolean hasSky = true;
    public int fogColor = 0xC0D8FF;
    private final ExecutorService meshers;
    private final ConcurrentLinkedQueue<Object[]> finished = new ConcurrentLinkedQueue<>();
    public final java.util.concurrent.atomic.AtomicInteger meshQueue = new java.util.concurrent.atomic.AtomicInteger();

    public ClientWorld() {
        int n = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 2));
        meshers = Executors.newFixedThreadPool(n, r -> {
            Thread t = new Thread(r, "mesher");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
    }

    public static long key(int cx, int cz) { return ((long) cx << 32) | (cz & 0xFFFFFFFFL); }

    public CChunk chunk(int cx, int cz) { return chunks.get(key(cx, cz)); }

    @Override
    public int getBlock(int x, int y, int z) {
        if (y < 0 || y > 255) return Blocks.AIR;
        CChunk c = chunks.get(key(x >> 4, z >> 4));
        if (c == null) return Blocks.AIR;
        return c.blocks[(y << 8) | ((z & 15) << 4) | (x & 15)];
    }

    public boolean isLoaded(int x, int z) { return chunks.containsKey(key(x >> 4, z >> 4)); }

    @Override
    public int getSkyLight(int x, int y, int z) {
        if (y > 255) return 15;
        if (y < 0) return 0;
        CChunk c = chunks.get(key(x >> 4, z >> 4));
        if (c == null) return 15;
        return (c.light[(y << 8) | ((z & 15) << 4) | (x & 15)] >> 4) & 15;
    }

    @Override
    public int getBlockLight(int x, int y, int z) {
        if (y < 0 || y > 255) return 0;
        CChunk c = chunks.get(key(x >> 4, z >> 4));
        if (c == null) return 0;
        return c.light[(y << 8) | ((z & 15) << 4) | (x & 15)] & 15;
    }

    public Biome biomeAt(int x, int z) {
        CChunk c = chunks.get(key(x >> 4, z >> 4));
        if (c == null || c.biomes == null) return Biomes.ALL.isEmpty() ? null : Biomes.ALL.get(0);
        int i = c.biomes[((z & 15) << 4) | (x & 15)] & 255;
        return i < Biomes.ALL.size() ? Biomes.ALL.get(i) : Biomes.ALL.get(0);
    }

    // ------------------------------------------------------------------ packets
    public void accept(S2C.ChunkData d) {
        long k = key(d.cx(), d.cz());
        CChunk c = chunks.get(k);
        boolean isNew = c == null;
        if (isNew) c = new CChunk(d.cx(), d.cz());
        char[] ob = c.blocks;
        byte[] ol = c.light;
        int changedSections = 0, borderSections = 0;
        if (ob == null) {
            changedSections = 0xFFFF;
            borderSections = 0xFFFF;
        } else {
            char[] nb = d.blocks();
            byte[] nl = d.light();
            for (int s = 0; s < 16; s++) {
                int base = s << 12;
                for (int i = base; i < base + 4096; i++) {
                    if (ob[i] != nb[i] || ol[i] != nl[i]) {
                        changedSections |= 1 << s;
                        int lx = i & 15, lz = (i >> 4) & 15;
                        if (lx == 0 || lx == 15 || lz == 0 || lz == 15) borderSections |= 1 << s;
                    }
                }
            }
        }
        c.blocks = d.blocks();
        c.light = d.light();
        c.biomes = d.biomes();
        int mask = 0;
        for (int s = 0; s < 16; s++) {
            int base = s << 12;
            for (int i = base; i < base + 4096; i++) if (c.blocks[i] != Blocks.AIR) { mask |= 1 << s; break; }
        }
        c.nonEmptyMask = mask;
        if (isNew) chunks.put(k, c);
        // a change in a section affects the meshes of vertically adjacent sections (light, culled faces) too
        int spread = changedSections | (changedSections << 1) | (changedSections >> 1);
        for (int s = 0; s < 16; s++) if ((spread & (1 << s)) != 0) c.dirty[s] = true;
        int nspread = borderSections | (borderSections << 1) | (borderSections >> 1);
        if (nspread != 0) {
            for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                CChunk n = chunk(d.cx() + o[0], d.cz() + o[1]);
                if (n == null) continue;
                for (int s = 0; s < 16; s++) if ((nspread & (1 << s)) != 0 || isNew) n.dirty[s] = true;
            }
        }
    }

    public void unload(int cx, int cz) { chunks.remove(key(cx, cz)); }

    public void clear() { chunks.clear(); }

    // ------------------------------------------------------------------ meshing
    /** Schedule dirty sections near the camera for rebuilding; integrate finished meshes. */
    public void updateMeshes(double camX, double camZ, int radius) {
        Object[] f;
        while ((f = finished.poll()) != null) {
            CChunk c = (CChunk) f[0];
            int s = (Integer) f[1];
            c.meshes[s] = (Mesh) f[2];
            c.building[s] = false;
        }
        int pcx = (int) Math.floor(camX) >> 4, pcz = (int) Math.floor(camZ) >> 4;
        List<long[]> todo = new ArrayList<>();
        for (CChunk c : chunks.values()) {
            int dx = c.cx - pcx, dz = c.cz - pcz;
            if (dx * dx + dz * dz > (radius + 1) * (radius + 1)) continue;
            // only mesh when all 4 neighbours are present (correct face culling and light at borders)
            if (chunk(c.cx + 1, c.cz) == null || chunk(c.cx - 1, c.cz) == null || chunk(c.cx, c.cz + 1) == null || chunk(c.cx, c.cz - 1) == null) continue;
            for (int s = 0; s < 16; s++) {
                boolean need = c.dirty[s] || (c.meshes[s] == null && (c.nonEmptyMask & (1 << s)) != 0);
                if (need && !c.building[s]) todo.add(new long[]{dx * dx + dz * dz, key(c.cx, c.cz), s});
            }
        }
        todo.sort(Comparator.comparingLong(a -> a[0]));
        int limit = Math.max(0, 48 - meshQueue.get());
        for (int i = 0; i < todo.size() && i < limit; i++) {
            long[] t = todo.get(i);
            CChunk c = chunks.get(t[1]);
            if (c == null) continue;
            int s = (int) t[2];
            c.dirty[s] = false;
            c.building[s] = true;
            meshQueue.incrementAndGet();
            final CChunk cc = c;
            meshers.submit(() -> {
                Mesh m;
                try {
                    m = (cc.nonEmptyMask & (1 << s)) == 0 ? Mesh.EMPTY : Mesher.build(this, cc, s);
                } catch (Throwable e) {
                    e.printStackTrace();
                    m = Mesh.EMPTY;
                }
                finished.add(new Object[]{cc, s, m});
                meshQueue.decrementAndGet();
            });
        }
    }

    public void shutdown() { meshers.shutdownNow(); }
}
