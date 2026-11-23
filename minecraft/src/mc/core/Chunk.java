package mc.core;

import mc.content.Blocks;

import java.util.*;

/** A 16x256x16 column stored as 16 palette sections plus per-section sky/block light nibble arrays (Section 2.3). */
public final class Chunk {
    public static final int SECTIONS = 16, HEIGHT = 256;
    public final int cx, cz;
    public final PalettedContainer[] sections = new PalettedContainer[SECTIONS];
    public final byte[][] skyLight = new byte[SECTIONS][];   // 2048 bytes each (nibbles)
    public final byte[][] blockLight = new byte[SECTIONS][];
    public final int[] heightmap = new int[256];
    public final byte[] biomes = new byte[256];              // biome index per column (z<<4|x)             // y of highest light-blocking block + 1
    public final Map<Integer, BlockEntity> blockEntities = new HashMap<>();
    public int status;           // ChunkStatus stage (Appendix BF)
    public boolean dirty;        // needs saving
    public boolean lightSeamed;  // border light merge done
    public long inhabitedTicks;
    public boolean hadEntities;
    public boolean fresh;        // generated this session (not loaded) -> initial animal population  // last save contained entities
    public final List<Map<String, Object>> pendingEntities = new ArrayList<>(); // entities loaded from disk

    public static final int STATUS_FULL = 8;

    public Chunk(int cx, int cz) {
        this.cx = cx;
        this.cz = cz;
        for (int i = 0; i < SECTIONS; i++) {
            sections[i] = new PalettedContainer(Blocks.AIR);
            skyLight[i] = new byte[2048];
            blockLight[i] = new byte[2048];
        }
    }

    public static long key(int cx, int cz) { return ((long) cx << 32) | (cz & 0xFFFFFFFFL); }

    public long key() { return key(cx, cz); }

    public int get(int lx, int y, int lz) {
        if (y < 0 || y >= HEIGHT) return Blocks.AIR;
        return sections[y >> 4].get(lx, y & 15, lz);
    }

    public void set(int lx, int y, int lz, int state) {
        if (y < 0 || y >= HEIGHT) return;
        sections[y >> 4].set(lx, y & 15, lz, state);
    }

    private static int nib(byte[] a, int i) { return (a[i >> 1] >> ((i & 1) << 2)) & 15; }

    private static void setNib(byte[] a, int i, int v) {
        int sh = (i & 1) << 2;
        a[i >> 1] = (byte) ((a[i >> 1] & ~(15 << sh)) | ((v & 15) << sh));
    }

    public int sky(int lx, int y, int lz) {
        if (y >= HEIGHT) return 15;
        if (y < 0) return 0;
        return nib(skyLight[y >> 4], ((y & 15) << 8) | (lz << 4) | lx);
    }

    public int blockL(int lx, int y, int lz) {
        if (y < 0 || y >= HEIGHT) return 0;
        return nib(blockLight[y >> 4], ((y & 15) << 8) | (lz << 4) | lx);
    }

    public void setSky(int lx, int y, int lz, int v) {
        if (y < 0 || y >= HEIGHT) return;
        setNib(skyLight[y >> 4], ((y & 15) << 8) | (lz << 4) | lx, v);
    }

    public void setBlockL(int lx, int y, int lz, int v) {
        if (y < 0 || y >= HEIGHT) return;
        setNib(blockLight[y >> 4], ((y & 15) << 8) | (lz << 4) | lx, v);
    }

    public void recomputeHeightmap() {
        for (int lz = 0; lz < 16; lz++)
            for (int lx = 0; lx < 16; lx++) updateHeight(lx, lz);
    }

    public void updateHeight(int lx, int lz) {
        int y = HEIGHT - 1;
        while (y >= 0 && Blocks.opacity[get(lx, y, lz)] == 0) y--;
        heightmap[lz << 4 | lx] = y + 1;
    }

    public int height(int lx, int lz) { return heightmap[lz << 4 | lx]; }

    /** Flat copy of all block states (index y<<8|z<<4|x) for client snapshots / hashing. */
    public char[] snapshotBlocks() {
        char[] out = new char[65536];
        for (int s = 0; s < SECTIONS; s++) sections[s].toArray(out, s << 12);
        return out;
    }

    /** Light packed sky<<4|block, same indexing. */
    public byte[] snapshotLight() {
        byte[] out = new byte[65536];
        for (int s = 0; s < SECTIONS; s++) {
            byte[] sk = skyLight[s], bl = blockLight[s];
            int base = s << 12;
            for (int i = 0; i < 4096; i++) out[base + i] = (byte) ((nib(sk, i) << 4) | nib(bl, i));
        }
        return out;
    }

    /** Stable content hash used by the determinism and round-trip tests (Section 1.5). */
    public long contentHash() {
        long h = 1469598103934665603L;
        char[] b = snapshotBlocks();
        for (char c : b) {
            h ^= c;
            h *= 1099511628211L;
        }
        return h;
    }
}
