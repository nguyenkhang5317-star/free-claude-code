package mc.client;

import mc.content.*;

/**
 * Builds section meshes: greedy-merged full-cube faces with smooth lighting + ambient occlusion (faces merge only when
 * texture, tint and all four vertex light/AO values match), and individually emitted special shapes.
 */
public final class Mesher {
    private Mesher() { }

    static final int P = 18; // padded size
    static final int[][] DIR = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
    // corner sign pairs (da, db) for corners 0..3
    static final int[][] CORNER = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};

    static int idx(int x, int y, int z) { return ((y + 1) * P + (z + 1)) * P + (x + 1); }

    /** Unit vectors of the A and B axes of each face direction. */
    static int[] axisA(int d) { return d < 2 ? new int[]{1, 0, 0} : d < 4 ? new int[]{1, 0, 0} : new int[]{0, 0, 1}; }

    static int[] axisB(int d) { return d < 2 ? new int[]{0, 0, 1} : new int[]{0, 1, 0}; }

    public static Mesh build(ClientWorld w, ClientWorld.CChunk c, int sec) {
        char[] st = new char[P * P * P];
        byte[] lt = new byte[P * P * P];
        int bx = c.cx * 16, by = sec * 16, bz = c.cz * 16;
        // gather the padded neighbourhood
        ClientWorld.CChunk[][] near = new ClientWorld.CChunk[3][3];
        for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) near[i + 1][j + 1] = w.chunk(c.cx + i, c.cz + j);
        for (int y = -1; y <= 16; y++) {
            int wy = by + y;
            for (int z = -1; z <= 16; z++)
                for (int x = -1; x <= 16; x++) {
                    int id = idx(x, y, z);
                    if (wy < 0) { st[id] = (char) Blocks.BEDROCK; lt[id] = 0; continue; }
                    if (wy > 255) { st[id] = (char) Blocks.AIR; lt[id] = (byte) 0xF0; continue; }
                    ClientWorld.CChunk n = near[(x >> 4) + 1 + (x < 0 ? 0 : 0)][(z >> 4) + 1];
                    if (n == null || n.blocks == null) { st[id] = (char) Blocks.AIR; lt[id] = (byte) 0xF0; continue; }
                    int li = (wy << 8) | ((z & 15) << 4) | (x & 15);
                    st[id] = n.blocks[li];
                    lt[id] = n.light[li];
                }
        }
        Mesh.Builder solid = new Mesh.Builder(), trans = new Mesh.Builder();
        boolean[] opaque = Blocks.opaqueCube;
        Biome[] colBiome = new Biome[256];
        for (int i = 0; i < 256; i++) {
            int bi = c.biomes == null ? 0 : c.biomes[i] & 255;
            colBiome[i] = bi < Biomes.ALL.size() ? Biomes.ALL.get(bi) : Biomes.ALL.get(0);
        }

        // ---- full cube faces into per-direction masks
        int[][] fTex = new int[6][4096];
        int[][] fTint = new int[6][4096];
        long[][] fKey = new long[6][4096];
        boolean[][] fTrans = new boolean[6][4096];
        for (int d = 0; d < 6; d++) java.util.Arrays.fill(fTex[d], -1);
        float[] v = new float[12], t = new float[8];
        int[] s4 = new int[4], b4 = new int[4], a4 = new int[4];
        for (int y = 0; y < 16; y++)
            for (int z = 0; z < 16; z++)
                for (int x = 0; x < 16; x++) {
                    int s = st[idx(x, y, z)];
                    if (s == Blocks.AIR) continue;
                    Block b = Blocks.stateBlock[s];
                    int shape = b.shape;
                    if (shape == Block.SHAPE_CUBE) {
                        boolean translucent = b.layer == Block.LAYER_TRANSLUCENT;
                        for (int d = 0; d < 6; d++) {
                            int[] dv = DIR[d];
                            int ns = st[idx(x + dv[0], y + dv[1], z + dv[2])];
                            if (opaque[ns]) continue;
                            if (b.layer != Block.LAYER_SOLID && Blocks.stateBlock[ns] == b) continue;
                            if (translucent && Blocks.stateBlock[ns].layer == Block.LAYER_TRANSLUCENT && Blocks.stateBlock[ns].shape == Block.SHAPE_CUBE) continue;
                            int cell = cellIndex(d, x, y, z);
                            fTex[d][cell] = faceTex(s, b, d);
                            fTint[d][cell] = BlockColors.tint(b, d, colBiome[(z << 4) | x]);
                            fKey[d][cell] = cornerKey(st, lt, opaque, x, y, z, d);
                            fTrans[d][cell] = translucent;
                        }
                    } else {
                        special(st, lt, opaque, s, b, x, y, z, bx, by, bz, solid, trans, colBiome[(z << 4) | x], v, t, s4, b4, a4);
                    }
                }
        // ---- greedy merge
        int[] mt = new int[256], mti = new int[256];
        long[] mk = new long[256];
        boolean[] mtr = new boolean[256];
        for (int d = 0; d < 6; d++) {
            for (int slice = 0; slice < 16; slice++) {
                int off = slice * 256;
                System.arraycopy(fTex[d], off, mt, 0, 256);
                System.arraycopy(fTint[d], off, mti, 0, 256);
                System.arraycopy(fKey[d], off, mk, 0, 256);
                System.arraycopy(fTrans[d], off, mtr, 0, 256);
                for (int bb = 0; bb < 16; bb++)
                    for (int aa = 0; aa < 16; aa++) {
                        int i = bb * 16 + aa;
                        if (mt[i] < 0) continue;
                        int wdt = 1;
                        while (aa + wdt < 16 && same(mt, mti, mk, i, i + wdt)) wdt++;
                        int hgt = 1;
                        outer:
                        while (bb + hgt < 16) {
                            for (int k = 0; k < wdt; k++) if (!same(mt, mti, mk, i, (bb + hgt) * 16 + aa + k)) break outer;
                            hgt++;
                        }
                        emitRect(d, slice, aa, bb, wdt, hgt, mt[i], mti[i], mk[i], bx, by, bz, mtr[i] ? trans : solid, v, t, s4, b4, a4);
                        for (int q = 0; q < hgt; q++) for (int k = 0; k < wdt; k++) mt[(bb + q) * 16 + aa + k] = -1;
                    }
            }
        }
        return solid.build(trans);
    }

    static boolean same(int[] mt, int[] mti, long[] mk, int i, int j) { return mt[j] == mt[i] && mti[j] == mti[i] && mk[j] == mk[i]; }

    /** Index inside the per-direction mask: slice*256 + b*16 + a. */
    static int cellIndex(int d, int x, int y, int z) {
        if (d < 2) return y * 256 + z * 16 + x;
        if (d < 4) return z * 256 + y * 16 + x;
        return x * 256 + y * 16 + z;
    }

    static int faceTex(int s, Block b, int d) {
        if (b.propNames.length > 0) {
            int ai = b.propIndex("axis");
            if (ai >= 0) {
                String ax = Blocks.value(s, "axis");
                boolean end = ax.equals("y") ? d < 2 : ax.equals("x") ? d >= 4 : (d == 2 || d == 3);
                return Textures.id(end ? b.texTop : b.texSide);
            }
            int fi = b.propIndex("facing");
            if (fi >= 0 && d >= 2) {
                String f = Blocks.value(s, "facing");
                int fd = f.equals("north") ? 2 : f.equals("south") ? 3 : f.equals("west") ? 4 : 5;
                if (fd == d) {
                    if (b.propIndex("lit") >= 0 && "true".equals(Blocks.value(s, "lit")) && b.extraStr("lit_front") != null) return Textures.id(b.extraStr("lit_front"));
                    return Textures.id(b.texFront);
                }
                return Textures.id(b.texSide);
            }
        }
        return Textures.id(d == 1 ? b.texTop : d == 0 ? b.texBottom : b.texSide);
    }

    static int lightOf(byte[] lt, int i, boolean sky) { return sky ? (lt[i] >> 4) & 15 : lt[i] & 15; }

    /** 4 corners x (sky 6 bits, block 6 bits, ao 2 bits). */
    static long cornerKey(char[] st, byte[] lt, boolean[] opaque, int x, int y, int z, int d) {
        int[] dv = DIR[d], A = axisA(d), B = axisB(d);
        int fx = x + dv[0], fy = y + dv[1], fz = z + dv[2];
        long key = 0;
        int fi = idx(fx, fy, fz);
        for (int c = 0; c < 4; c++) {
            int da = CORNER[c][0], db = CORNER[c][1];
            int s1 = idx(fx + A[0] * da, fy + A[1] * da, fz + A[2] * da);
            int s2 = idx(fx + B[0] * db, fy + B[1] * db, fz + B[2] * db);
            int cc = idx(fx + A[0] * da + B[0] * db, fy + A[1] * da + B[1] * db, fz + A[2] * da + B[2] * db);
            boolean o1 = opaque[st[s1]], o2 = opaque[st[s2]], oc = opaque[st[cc]];
            int sky = lightOf(lt, fi, true), blk = lightOf(lt, fi, false), n = 1;
            if (!o1) { sky += lightOf(lt, s1, true); blk += lightOf(lt, s1, false); n++; }
            if (!o2) { sky += lightOf(lt, s2, true); blk += lightOf(lt, s2, false); n++; }
            if (!oc && !(o1 && o2)) { sky += lightOf(lt, cc, true); blk += lightOf(lt, cc, false); n++; }
            int ao = (o1 && o2) ? 0 : 3 - ((o1 ? 1 : 0) + (o2 ? 1 : 0) + (oc ? 1 : 0));
            long k = (sky * 4 / n) | ((long) (blk * 4 / n) << 6) | ((long) ao << 12);
            key |= k << (c * 14);
        }
        return key;
    }

    static void emitRect(int d, int slice, int a0, int b0, int wa, int wb, int tex, int tint, long key, int bx, int by, int bz,
                         Mesh.Builder out, float[] v, float[] t, int[] s4, int[] b4, int[] a4) {
        int plane = slice + (d % 2 == 1 ? 1 : 0);
        int[][] ab = {{a0, b0}, {a0 + wa, b0}, {a0 + wa, b0 + wb}, {a0, b0 + wb}};
        for (int c = 0; c < 4; c++) {
            float lx, ly, lz;
            int A = ab[c][0], B = ab[c][1];
            if (d < 2) { lx = A; ly = plane; lz = B; } else if (d < 4) { lx = A; ly = B; lz = plane; } else { lx = plane; ly = B; lz = A; }
            v[c * 3] = bx + lx; v[c * 3 + 1] = by + ly; v[c * 3 + 2] = bz + lz;
            uvFor(d, lx, ly, lz, t, c);
            long k = (key >> (c * 14)) & 0x3FFF;
            s4[c] = (int) (k & 63); b4[c] = (int) ((k >> 6) & 63); a4[c] = (int) ((k >> 12) & 3);
        }
        out.quad(v, t, s4, b4, a4, tex, tint, d);
    }

    /** Texel coordinates from local position (0..16 per section). */
    static void uvFor(int d, float lx, float ly, float lz, float[] t, int c) {
        float u, w;
        switch (d) {
            case 0: case 1: u = lx * 16; w = lz * 16; break;
            case 2: u = (16 - lx) * 16; w = (16 - ly) * 16; break;
            case 3: u = lx * 16; w = (16 - ly) * 16; break;
            case 4: u = lz * 16; w = (16 - ly) * 16; break;
            default: u = (16 - lz) * 16; w = (16 - ly) * 16; break;
        }
        t[c * 2] = u; t[c * 2 + 1] = w;
    }

    // ------------------------------------------------------------------ special shapes
    static void special(char[] st, byte[] lt, boolean[] opaque, int s, Block b, int x, int y, int z, int bx, int by, int bz,
                        Mesh.Builder solid, Mesh.Builder trans, Biome bi, float[] v, float[] t, int[] s4, int[] b4, int[] a4) {
        int li = idx(x, y, z);
        int sky = lightOf(lt, li, true) * 4, blk = lightOf(lt, li, false) * 4;
        // light of non-full blocks: use the brightest of self and above (slabs/farmland have dark interior cells)
        int up = idx(x, y + 1, z);
        if (!opaque[st[up]]) { sky = Math.max(sky, lightOf(lt, up, true) * 4); blk = Math.max(blk, lightOf(lt, up, false) * 4); }
        for (int i = 0; i < 4; i++) { s4[i] = sky; b4[i] = blk; a4[i] = 3; }
        float X = bx + x, Y = by + y, Z = bz + z;
        int side = Textures.id(b.texSide), top = Textures.id(b.texTop), bot = Textures.id(b.texBottom);
        int tint = BlockColors.tint(b, 2, bi);
        switch (b.shape) {
            case Block.SHAPE_CROSS: case Block.SHAPE_TORCH: {
                float in = b.shape == Block.SHAPE_TORCH ? 0.35f : 0.15f;
                plane(solid, X + in, Y, Z + in, X + 1 - in, Y + 1, Z + 1 - in, side, tint, s4, b4, a4, v, t);
                plane(solid, X + 1 - in, Y, Z + in, X + in, Y + 1, Z + 1 - in, side, tint, s4, b4, a4, v, t);
                break;
            }
            case Block.SHAPE_CROP: {
                int age = b.propIndex("age") >= 0 ? Blocks.prop(s, "age") : 7;
                int tx = Textures.id("wheat_stage" + age);
                if (tx == Textures.missing) tx = side;
                for (float o : new float[]{0.25f, 0.75f}) {
                    plane(solid, X + o, Y, Z, X + o, Y + 1, Z + 1, tx, tint, s4, b4, a4, v, t);
                    plane(solid, X, Y, Z + o, X + 1, Y + 1, Z + o, tx, tint, s4, b4, a4, v, t);
                }
                break;
            }
            case Block.SHAPE_LADDER: {
                String f = b.propIndex("facing") >= 0 ? Blocks.value(s, "facing") : "north";
                float e = 1 / 16f;
                switch (f) {
                    case "north": plane(solid, X, Y, Z + 1 - e, X + 1, Y + 1, Z + 1 - e, side, tint, s4, b4, a4, v, t); break;
                    case "south": plane(solid, X, Y, Z + e, X + 1, Y + 1, Z + e, side, tint, s4, b4, a4, v, t); break;
                    case "west": plane(solid, X + 1 - e, Y, Z, X + 1 - e, Y + 1, Z + 1, side, tint, s4, b4, a4, v, t); break;
                    default: plane(solid, X + e, Y, Z, X + e, Y + 1, Z + 1, side, tint, s4, b4, a4, v, t);
                }
                break;
            }
            case Block.SHAPE_PORTAL: {
                boolean ax = b.propIndex("axis") < 0 || "x".equals(Blocks.value(s, "axis"));
                for (int i = 0; i < 4; i++) b4[i] = Math.max(b4[i], 44);
                if (ax) plane(trans, X, Y, Z + 0.5f, X + 1, Y + 1, Z + 0.5f, side, tint, s4, b4, a4, v, t);
                else plane(trans, X + 0.5f, Y, Z, X + 0.5f, Y + 1, Z + 1, side, tint, s4, b4, a4, v, t);
                break;
            }
            case Block.SHAPE_SLAB: box(st, opaque, x, y, z, X, Y, Z, 0, 0, 0, 1, 0.5f, 1, top, side, bot, tint, solid, s4, b4, a4, v, t, false); break;
            case Block.SHAPE_SNOW: box(st, opaque, x, y, z, X, Y, Z, 0, 0, 0, 1, 0.125f, 1, top, side, bot, tint, solid, s4, b4, a4, v, t, false); break;
            case Block.SHAPE_FARMLAND: {
                int tt = b.propIndex("moisture") >= 0 && Blocks.prop(s, "moisture") >= 7 ? Textures.id("farmland_moist") : top;
                box(st, opaque, x, y, z, X, Y, Z, 0, 0, 0, 1, 15 / 16f, 1, tt, side, bot, tint, solid, s4, b4, a4, v, t, false);
                break;
            }
            case Block.SHAPE_BED: box(st, opaque, x, y, z, X, Y, Z, 0, 0, 0, 1, 9 / 16f, 1, top, side, bot, tint, solid, s4, b4, a4, v, t, false); break;
            case Block.SHAPE_CACTUS: {
                float e = 1 / 16f;
                box(st, opaque, x, y, z, X, Y, Z, e, 0, e, 1 - e, 1, 1 - e, top, side, bot, tint, solid, s4, b4, a4, v, t, true);
                break;
            }
            case Block.SHAPE_FLUID: fluid(st, lt, opaque, s, b, x, y, z, X, Y, Z, side, solid, trans, s4, b4, a4, v, t); break;
            default: break;
        }
    }

    /** Double-sided textured plane between two opposite corners (vertical planes). */
    static void plane(Mesh.Builder out, float x0, float y0, float z0, float x1, float y1, float z1, int tex, int tint,
                      int[] s4, int[] b4, int[] a4, float[] v, float[] t) {
        v[0] = x0; v[1] = y1; v[2] = z0;
        v[3] = x1; v[4] = y1; v[5] = z1;
        v[6] = x1; v[7] = y0; v[8] = z1;
        v[9] = x0; v[10] = y0; v[11] = z0;
        t[0] = 0; t[1] = 0; t[2] = 15.99f; t[3] = 0; t[4] = 15.99f; t[5] = 15.99f; t[6] = 0; t[7] = 15.99f;
        out.quad(v, t, s4, b4, a4, tex, tint, 6);
    }

    /** Axis-aligned box inside the cell, local bounds in block units. Faces flush with the cell are culled against opaque neighbours. */
    static void box(char[] st, boolean[] opaque, int x, int y, int z, float X, float Y, float Z, float x0, float y0, float z0, float x1, float y1,
                    float z1, int top, int side, int bot, int tint, Mesh.Builder out, int[] s4, int[] b4, int[] a4, float[] v, float[] t, boolean sidesAlways) {
        for (int d = 0; d < 6; d++) {
            int[] dv = DIR[d];
            boolean flush = d == 0 ? y0 == 0 : d == 1 ? y1 == 1 : d == 2 ? z0 == 0 : d == 3 ? z1 == 1 : d == 4 ? x0 == 0 : x1 == 1;
            if (flush && !(sidesAlways && d >= 2) && opaque[st[idx(x + dv[0], y + dv[1], z + dv[2])]]) continue;
            float[][] q = faceCorners(d, x0, y0, z0, x1, y1, z1);
            for (int c = 0; c < 4; c++) {
                v[c * 3] = X + q[c][0]; v[c * 3 + 1] = Y + q[c][1]; v[c * 3 + 2] = Z + q[c][2];
                uvFor(d, q[c][0], q[c][1] + 15, q[c][2], t, c); // +15 keeps v positive for unit-local coordinates
            }
            out.quad(v, t, s4, b4, a4, d == 1 ? top : d == 0 ? bot : side, d == 1 ? tint : BlockColors.IDENTITY, d);
        }
    }

    static float[][] faceCorners(int d, float x0, float y0, float z0, float x1, float y1, float z1) {
        switch (d) {
            case 0: return new float[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}};
            case 1: return new float[][]{{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}};
            case 2: return new float[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}};
            case 3: return new float[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
            case 4: return new float[][]{{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}};
            default: return new float[][]{{x1, y0, z0}, {x1, y0, z1}, {x1, y1, z1}, {x1, y1, z0}};
        }
    }

    static float fluidHeight(int s) {
        int lvl = Blocks.stateBlock[s].propIndex("level") >= 0 ? Blocks.prop(s, "level") : 0;
        if (lvl >= 8) return 1f;
        return lvl == 0 ? 14 / 16f : Math.max(2 / 16f, (8 - lvl) / 9f);
    }

    static void fluid(char[] st, byte[] lt, boolean[] opaque, int s, Block b, int x, int y, int z, float X, float Y, float Z, int tex,
                      Mesh.Builder solid, Mesh.Builder trans, int[] s4, int[] b4, int[] a4, float[] v, float[] t) {
        boolean water = Blocks.isWater(s);
        Mesh.Builder out = water ? trans : solid;
        int above = st[idx(x, y + 1, z)];
        boolean coveredSame = Blocks.stateBlock[above] == b;
        float h = coveredSame ? 1 : fluidHeight(s);
        if (!water) for (int i = 0; i < 4; i++) b4[i] = 60;
        for (int d = 0; d < 6; d++) {
            int[] dv = DIR[d];
            int ns = st[idx(x + dv[0], y + dv[1], z + dv[2])];
            if (Blocks.stateBlock[ns] == b) continue;
            if (opaque[ns] && d != 1) continue;
            if (d == 1 && coveredSame) continue;
            if (water && d != 1 && Blocks.stateBlock[ns].layer == Block.LAYER_TRANSLUCENT && Blocks.stateBlock[ns].shape == Block.SHAPE_CUBE) continue;
            float[][] q = faceCorners(d, 0, 0, 0, 1, h, 1);
            for (int c = 0; c < 4; c++) {
                v[c * 3] = X + q[c][0]; v[c * 3 + 1] = Y + q[c][1]; v[c * 3 + 2] = Z + q[c][2];
                uvFor(d, q[c][0], q[c][1] + 15, q[c][2], t, c);
            }
            out.quad(v, t, s4, b4, a4, tex, BlockColors.IDENTITY, d == 1 ? 6 : d);
        }
    }
}
