package mc.core;

import mc.content.Blocks;

/**
 * Sky and block light (Section 2.3 nibble storage, Appendix AB.1). Chunk-local initial light runs on generation
 * workers; seam merging and incremental edits (removal BFS + re-propagation) run on the server tick thread.
 */
public final class LightEngine {
    private LightEngine() {}

    private static final int[] DX = {1, -1, 0, 0, 0, 0}, DY = {0, 0, 1, -1, 0, 0}, DZ = {0, 0, 0, 0, 1, -1};

    /** Growable int-triple queue. */
    static final class Q {
        int[] a = new int[3 * 4096];
        int head, tail;

        void push(int x, int y, int z, int v) {
            if (tail + 4 > a.length) {
                if (head > 0) {
                    System.arraycopy(a, head, a, 0, tail - head);
                    tail -= head;
                    head = 0;
                }
                if (tail + 4 > a.length) a = java.util.Arrays.copyOf(a, a.length * 2);
            }
            a[tail++] = x; a[tail++] = y; a[tail++] = z; a[tail++] = v;
        }

        boolean empty() { return head >= tail; }

        void clear() { head = tail = 0; }
    }

    // ---------------- chunk-local initial light (generation stage 7) ----------------
    public static void initChunk(Chunk c, boolean hasSky) {
        c.recomputeHeightmap();
        Q q = new Q();
        if (hasSky) {
            for (int lz = 0; lz < 16; lz++)
                for (int lx = 0; lx < 16; lx++) {
                    int light = 15;
                    for (int y = 255; y >= 0; y--) {
                        int op = Blocks.opacity[c.get(lx, y, lz)];
                        if (op > 0) light = Math.max(0, light - Math.max(1, op));
                        if (op >= 15) light = 0;
                        c.setSky(lx, y, lz, light);
                        if (light == 0) break;
                    }
                }
            for (int lz = 0; lz < 16; lz++)
                for (int lx = 0; lx < 16; lx++) {
                    int h = c.height(lx, lz);
                    int maxN = h;
                    if (lx > 0) maxN = Math.max(maxN, c.height(lx - 1, lz));
                    if (lx < 15) maxN = Math.max(maxN, c.height(lx + 1, lz));
                    if (lz > 0) maxN = Math.max(maxN, c.height(lx, lz - 1));
                    if (lz < 15) maxN = Math.max(maxN, c.height(lx, lz + 1));
                    for (int y = Math.max(0, h - 1); y <= Math.min(255, maxN); y++) {
                        int l = c.sky(lx, y, lz);
                        if (l > 1) q.push(lx, y, lz, l);
                    }
                }
            spreadLocal(c, q, true);
        }
        q.clear();
        for (int s = 0; s < Chunk.SECTIONS; s++) {
            if (c.sections[s].isUniform() && Blocks.emission[c.sections[s].get(0)] == 0) continue;
            for (int i = 0; i < 4096; i++) {
                int st = c.sections[s].get(i);
                int em = Blocks.emission[st];
                if (em > 0) {
                    int lx = i & 15, lz = (i >> 4) & 15, y = (s << 4) | (i >> 8);
                    c.setBlockL(lx, y, lz, em);
                    q.push(lx, y, lz, em);
                }
            }
        }
        spreadLocal(c, q, false);
    }

    private static void spreadLocal(Chunk c, Q q, boolean sky) {
        while (!q.empty()) {
            int x = q.a[q.head++], y = q.a[q.head++], z = q.a[q.head++], l = q.a[q.head++];
            for (int d = 0; d < 6; d++) {
                int nx = x + DX[d], ny = y + DY[d], nz = z + DZ[d];
                if (nx < 0 || nx > 15 || nz < 0 || nz > 15 || ny < 0 || ny > 255) continue;
                int st = c.get(nx, ny, nz);
                int op = Blocks.opacity[st];
                if (op >= 15) continue;
                int nl = (sky && d == 3 && l == 15 && op == 0) ? 15 : l - Math.max(1, op);
                if (nl <= 0) continue;
                int cur = sky ? c.sky(nx, ny, nz) : c.blockL(nx, ny, nz);
                if (nl > cur) {
                    if (sky) c.setSky(nx, ny, nz, nl); else c.setBlockL(nx, ny, nz, nl);
                    if (nl > 1) q.push(nx, ny, nz, nl);
                }
            }
        }
    }

    // ---------------- world-level propagation ----------------
    private static int get(World w, int x, int y, int z, boolean sky) {
        if (y < 0) return 0;
        if (y > 255) return sky ? 15 : 0;
        Chunk c = w.chunkAt(x >> 4, z >> 4);
        if (c == null) return -1;
        return sky ? c.sky(x & 15, y, z & 15) : c.blockL(x & 15, y, z & 15);
    }

    private static void set(World w, int x, int y, int z, boolean sky, int v) {
        if (y < 0 || y > 255) return;
        Chunk c = w.chunkAt(x >> 4, z >> 4);
        if (c == null) return;
        if (sky) c.setSky(x & 15, y, z & 15, v); else c.setBlockL(x & 15, y, z & 15, v);
        w.markLightChanged(c);
    }

    private static void spread(World w, Q q, boolean sky) {
        while (!q.empty()) {
            int x = q.a[q.head++], y = q.a[q.head++], z = q.a[q.head++], l = q.a[q.head++];
            if (get(w, x, y, z, sky) != l) continue; // stale entry
            for (int d = 0; d < 6; d++) {
                int nx = x + DX[d], ny = y + DY[d], nz = z + DZ[d];
                if (ny < 0 || ny > 255) continue;
                int cur = get(w, nx, ny, nz, sky);
                if (cur < 0) continue;
                int op = Blocks.opacity[w.getBlock(nx, ny, nz)];
                if (op >= 15) continue;
                int nl = (sky && d == 3 && l == 15 && op == 0) ? 15 : l - Math.max(1, op);
                if (nl > cur) {
                    set(w, nx, ny, nz, sky, nl);
                    if (nl > 1) q.push(nx, ny, nz, nl);
                }
            }
        }
    }

    /** Merge light across the borders of a freshly loaded chunk and its loaded neighbors. */
    public static void seam(World w, Chunk c) {
        Q q = new Q();
        for (int pass = 0; pass < 2; pass++) {
            boolean sky = pass == 0;
            if (sky && !w.dim.hasSky) continue;
            q.clear();
            int bx = c.cx << 4, bz = c.cz << 4;
            int[][] edges = {{-1, 0}, {16, 0}, {0, -1}, {0, 16}};
            for (int[] e : edges) {
                int ncx = (bx + (e[0] == -1 ? -1 : e[0] == 16 ? 16 : 0)) >> 4, ncz = (bz + (e[1] == -1 ? -1 : e[1] == 16 ? 16 : 0)) >> 4;
                if (w.chunkAt(ncx, ncz) == null) continue;
                for (int i = 0; i < 16; i++) {
                    int ax, az, nx, nz;
                    if (e[0] != 0) { ax = e[0] == -1 ? bx : bx + 15; nx = e[0] == -1 ? bx - 1 : bx + 16; az = nz = bz + i; }
                    else { az = e[1] == -1 ? bz : bz + 15; nz = e[1] == -1 ? bz - 1 : bz + 16; ax = nx = bx + i; }
                    for (int y = 0; y < 256; y++) {
                        int la = get(w, ax, y, az, sky), lb = get(w, nx, y, nz, sky);
                        if (la > 1 && la > lb + 1) q.push(ax, y, az, la);
                        if (lb > 1 && lb > la + 1) q.push(nx, y, nz, lb);
                    }
                }
            }
            spread(w, q, sky);
        }
        c.lightSeamed = true;
    }

    /** Incremental update after a block change (opacity or emission may differ). */
    public static void update(World w, int x, int y, int z, int oldState, int newState) {
        boolean opChanged = Blocks.opacity[oldState] != Blocks.opacity[newState];
        boolean emChanged = Blocks.emission[oldState] != Blocks.emission[newState];
        if (!opChanged && !emChanged) return;
        relight(w, x, y, z, false, Blocks.emission[newState]);
        if (w.dim.hasSky && opChanged) relight(w, x, y, z, true, 0);
    }

    private static void relight(World w, int x, int y, int z, boolean sky, int newEmission) {
        Q rem = new Q(), add = new Q();
        int old = get(w, x, y, z, sky);
        if (old < 0) return;
        set(w, x, y, z, sky, 0);
        rem.push(x, y, z, old);
        while (!rem.empty()) {
            int cx = rem.a[rem.head++], cy = rem.a[rem.head++], cz = rem.a[rem.head++], l = rem.a[rem.head++];
            for (int d = 0; d < 6; d++) {
                int nx = cx + DX[d], ny = cy + DY[d], nz = cz + DZ[d];
                if (ny < 0 || ny > 255) continue;
                int nl = get(w, nx, ny, nz, sky);
                if (nl <= 0) continue;
                if (nl < l || (sky && d == 3 && l == 15 && nl == 15)) {
                    set(w, nx, ny, nz, sky, 0);
                    rem.push(nx, ny, nz, nl);
                } else {
                    add.push(nx, ny, nz, nl);
                }
            }
        }
        if (sky) {
            // column exposed to the sky above this point?
            int above = get(w, x, y + 1, z, true);
            if (above >= 0) add.push(x, y + 1, z, above);
        } else if (newEmission > 0) {
            set(w, x, y, z, false, newEmission);
            add.push(x, y, z, newEmission);
        }
        // neighbors re-flood the changed cell
        for (int d = 0; d < 6; d++) {
            int nx = x + DX[d], ny = y + DY[d], nz = z + DZ[d];
            int nl = get(w, nx, ny, nz, sky);
            if (nl > 0) add.push(nx, ny, nz, nl);
        }
        spread(w, add, sky);
    }
}
