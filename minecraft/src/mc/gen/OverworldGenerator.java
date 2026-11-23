package mc.gen;

import mc.content.*;
import mc.core.*;
import mc.util.*;

import java.util.*;

/**
 * Overworld generator (Section 4, Appendix BF). Pure function of (seed, cx, cz). Stages:
 * SHAPE (density on a 4x8x4 control grid, trilinear) → SURFACE (biome top/filler, bedrock) → CARVE (cheese,
 * noodle, ravine) → FEATURES (ores) → DECORATE (trees, plants; neighbors' features clipped into this chunk)
 * → STRUCTURES (dungeons) → LIGHT (done by the world worker) → FULL.
 */
public class OverworldGenerator implements ChunkGenerator {
    public final long seed;
    protected final Noise cont, ero, ridge, temp, humid, river, detail, cheese, noodleA, noodleB, noodleW, variant, surfNoise;
    public static final int SEA = 63;
    private final List<Map<String, Object>> ores;
    private final int[] oreState;
    private static final long SALT_ORE = 0x0E5, SALT_TREE = 0x7EE, SALT_PLANT = 0x9A47, SALT_RAVINE = 0x4A7, SALT_DUNGEON = 0xD06, SALT_BEDROCK = 0xBED;

    // biome handles
    private final Biome OCEAN, DEEP_OCEAN, FROZEN_OCEAN, COLD_OCEAN, LUKEWARM_OCEAN, WARM_OCEAN, RIVER, FROZEN_RIVER, BEACH, SNOWY_BEACH,
            STONE_SHORE, PLAINS, SUNFLOWER_PLAINS, FOREST, FLOWER_FOREST, BIRCH_FOREST, DARK_FOREST, TAIGA, SNOWY_TAIGA, GIANT_TREE_TAIGA,
            SNOWY_TUNDRA, ICE_SPIKES, DESERT, SAVANNA, JUNGLE, SWAMP, BADLANDS, MOUNTAINS, GRAVELLY_MOUNTAINS, SNOWY_MOUNTAINS, MUSHROOM_FIELDS;

    public OverworldGenerator(long seed) {
        this.seed = seed;
        cont = new Noise(seed, 0xC0A7);
        ero = new Noise(seed, 0xE120);
        ridge = new Noise(seed, 0x21D6);
        temp = new Noise(seed, 0x7E3B);
        humid = new Noise(seed, 0x4D1D);
        river = new Noise(seed, 0x21FE);
        detail = new Noise(seed, 0xDE7A);
        cheese = new Noise(seed, 0xC4EE);
        noodleA = new Noise(seed, 0x400A);
        noodleB = new Noise(seed, 0x400B);
        noodleW = new Noise(seed, 0x400C);
        variant = new Noise(seed, 0x7A21);
        surfNoise = new Noise(seed, 0x5BF0);
        ores = new ArrayList<>();
        for (Object o : Json.arr(Registries.WORLDGEN.get("ores"))) ores.add(Json.obj(o));
        oreState = new int[ores.size()];
        for (int i = 0; i < ores.size(); i++) oreState[i] = Blocks.base(Json.str(ores.get(i), "block", "minecraft:stone"));
        OCEAN = b("ocean"); DEEP_OCEAN = b("deep_ocean"); FROZEN_OCEAN = b("frozen_ocean"); COLD_OCEAN = b("cold_ocean");
        LUKEWARM_OCEAN = b("lukewarm_ocean"); WARM_OCEAN = b("warm_ocean"); RIVER = b("river"); FROZEN_RIVER = b("frozen_river");
        BEACH = b("beach"); SNOWY_BEACH = b("snowy_beach"); STONE_SHORE = b("stone_shore"); PLAINS = b("plains");
        SUNFLOWER_PLAINS = b("sunflower_plains"); FOREST = b("forest"); FLOWER_FOREST = b("flower_forest"); BIRCH_FOREST = b("birch_forest");
        DARK_FOREST = b("dark_forest"); TAIGA = b("taiga"); SNOWY_TAIGA = b("snowy_taiga"); GIANT_TREE_TAIGA = b("giant_tree_taiga");
        SNOWY_TUNDRA = b("snowy_tundra"); ICE_SPIKES = b("ice_spikes"); DESERT = b("desert"); SAVANNA = b("savanna"); JUNGLE = b("jungle");
        SWAMP = b("swamp"); BADLANDS = b("badlands"); MOUNTAINS = b("mountains"); GRAVELLY_MOUNTAINS = b("gravelly_mountains");
        SNOWY_MOUNTAINS = b("snowy_mountains"); MUSHROOM_FIELDS = b("mushroom_fields");
    }

    private static Biome b(String id) {
        Biome x = Biomes.get(id);
        if (x == null) throw new IllegalStateException("missing biome " + id);
        return x;
    }

    // ------------------------------------------------------------------ climate & height (Section 4.2)
    /** Continentalness in ~[-1,1]: negative = ocean. */
    double continent(double x, double z) { return cont.fbm2(x / 1100.0, z / 1100.0, 5) * 2.2 + 0.18; }

    double riverness(double x, double z) {
        double r = Math.abs(river.fbm2(x / 420.0, z / 420.0, 3));
        return r < 0.022 ? 1.0 - r / 0.022 : 0;
    }

    /** Column height before 3D detail; also returns mountain factor in out[1]. */
    double height(double x, double z, double[] out) {
        double c = continent(x, z);
        double e = ero.fbm2(x / 520.0, z / 520.0, 4) * 1.8;            // erosion: high = flat
        double rg = ridge.ridged2(x / 330.0, z / 330.0, 5);              // 0..1 ridges
        double h;
        double mountain = 0;
        if (c < -0.12) {
            double t = Math.min(1, (-0.12 - c) / 0.45);
            h = 61 - t * 26;                                              // ocean floor 61 → 35
        } else {
            double land = Math.min(1, (c + 0.12) / 0.9);
            double hills = (detail.fbm2(x / 140.0, z / 140.0, 4)) * 34 * (1.0 - Math.max(0, e) * 0.8);
            mountain = Math.max(0, (rg - 0.62) / 0.38) * Math.max(0, 0.6 - e) * Math.min(1, land * 2.2);
            h = 64 + land * 14 + Math.max(-6, hills) + mountain * 64;
            double rv = riverness(x, z);
            if (rv > 0 && h < 100) {
                double target = 58;
                h = h + (target - h) * Math.min(1, rv * 1.6);
            }
        }
        if (h < 32) h = 32;
        if (h > 124) h = 124;
        if (out != null) { out[0] = c; out[1] = mountain; }
        return h;
    }

    double temperature(double x, double z) { return temp.fbm2(x / 1400.0, z / 1400.0, 3) * 2.4 + 0.5; }   // ~0..1

    double humidity(double x, double z) { return humid.fbm2(x / 1100.0, z / 1100.0, 3) * 2.4 + 0.5; }

    @Override
    public Biome biomeAt(int x, int z) {
        double[] o = new double[2];
        double h = height(x, z, o);
        return pickBiome(x, z, h, o[0], o[1]);
    }

    Biome pickBiome(int x, int z, double h, double c, double mountain) {
        double t = temperature(x, z), hu = humidity(x, z);
        double v = variant.noise2(x / 96.0, z / 96.0) * 1.6 + 0.5;
        if (c < -0.12 && h < 62) {
            boolean deep = h < 46;
            if (t < 0.18) return FROZEN_OCEAN;
            if (t < 0.36) return COLD_OCEAN;
            if (t > 0.86) return WARM_OCEAN;
            if (t > 0.68) return LUKEWARM_OCEAN;
            return deep ? DEEP_OCEAN : OCEAN;
        }
        if (riverness(x, z) > 0.35 && h < 62.5) return t < 0.18 ? FROZEN_RIVER : RIVER;
        if (h < 65.5 && c < 0.02 && mountain < 0.05) {
            if (t < 0.18) return SNOWY_BEACH;
            if (v > 0.85) return STONE_SHORE;
            return BEACH;
        }
        if (mountain > 0.32 && h > 92) {
            if (t < 0.25) return SNOWY_MOUNTAINS;
            return v > 0.7 ? GRAVELLY_MOUNTAINS : MOUNTAINS;
        }
        if (t < 0.18) {
            if (hu > 0.55) return SNOWY_TAIGA;
            return v > 0.82 ? ICE_SPIKES : SNOWY_TUNDRA;
        }
        if (t < 0.38) {
            if (hu > 0.62) return v > 0.6 ? GIANT_TREE_TAIGA : TAIGA;
            if (hu > 0.4) return TAIGA;
            return PLAINS;
        }
        if (t < 0.66) {
            if (hu > 0.8 && h < 70) return v > 0.9 ? MUSHROOM_FIELDS : SWAMP;
            if (hu > 0.66) return DARK_FOREST;
            if (hu > 0.5) return v > 0.65 ? BIRCH_FOREST : FOREST;
            if (hu > 0.36) return v > 0.75 ? FLOWER_FOREST : FOREST;
            return v > 0.8 ? SUNFLOWER_PLAINS : PLAINS;
        }
        if (t < 0.82) {
            if (hu > 0.7) return JUNGLE;
            if (hu > 0.45) return FOREST;
            return PLAINS;
        }
        if (hu > 0.62) return JUNGLE;
        if (hu > 0.38) return SAVANNA;
        return v > 0.72 ? BADLANDS : DESERT;
    }

    @Override
    public int surfaceHeight(int x, int z) { return (int) Math.round(height(x, z, null)); }

    // ------------------------------------------------------------------ density grid
    protected double density(double x, double y, double z, double h, double mountain) {
        double d = (h - y) / 6.0;
        double amp = 0.35 + mountain * 2.2;
        if (y > h - 24 && y < h + 24) d += detail.fbm(x / 48.0, y / 32.0, z / 48.0, 3) * amp * 2.0;
        return d;
    }

    /** Fill density control points for the 5x33x5 grid of chunk (cx,cz). */
    private double[] densityGrid(int cx, int cz, double[] heights, double[] mountains) {
        double[] g = new double[5 * 5 * 33];
        double[] o = new double[2];
        for (int i = 0; i < 5; i++)
            for (int k = 0; k < 5; k++) {
                int x = (cx << 4) + i * 4, z = (cz << 4) + k * 4;
                double h = height(x, z, o);
                heights[i * 5 + k] = h;
                mountains[i * 5 + k] = o[1];
                for (int j = 0; j < 33; j++) g[(i * 5 + k) * 33 + j] = density(x, j * 8, z, h, o[1]);
            }
        return g;
    }

    private static double tri(double[] g, int x, int y, int z) {
        int i = x >> 2, k = z >> 2, j = y >> 3;
        double fx = (x & 3) / 4.0, fz = (z & 3) / 4.0, fy = (y & 7) / 8.0;
        if (j >= 32) { j = 31; fy = 1; }
        double c000 = g[(i * 5 + k) * 33 + j], c100 = g[((i + 1) * 5 + k) * 33 + j];
        double c001 = g[(i * 5 + k + 1) * 33 + j], c101 = g[((i + 1) * 5 + k + 1) * 33 + j];
        double c010 = g[(i * 5 + k) * 33 + j + 1], c110 = g[((i + 1) * 5 + k) * 33 + j + 1];
        double c011 = g[(i * 5 + k + 1) * 33 + j + 1], c111 = g[((i + 1) * 5 + k + 1) * 33 + j + 1];
        double a = c000 + (c100 - c000) * fx, b2 = c001 + (c101 - c001) * fx;
        double c = c010 + (c110 - c010) * fx, d = c011 + (c111 - c011) * fx;
        double lo = a + (b2 - a) * fz, hi = c + (d - c) * fz;
        return lo + (hi - lo) * fy;
    }

    /** Topmost solid y of a column after SHAPE (before carving), computed from the same control grid cell. */
    public int columnTop(int x, int z) {
        int gx = Math.floorDiv(x, 4) * 4, gz = Math.floorDiv(z, 4) * 4;
        double[] o = new double[2];
        double[][] col = new double[4][33];
        int idx = 0;
        for (int a = 0; a <= 1; a++)
            for (int c = 0; c <= 1; c++) {
                int px = gx + a * 4, pz = gz + c * 4;
                double h = height(px, pz, o);
                for (int j = 0; j < 33; j++) col[idx][j] = density(px, j * 8, pz, h, o[1]);
                idx++;
            }
        double fx = (x - gx) / 4.0, fz = (z - gz) / 4.0;
        for (int y = 200; y > 0; y--) {
            int j = y >> 3;
            double fy = (y & 7) / 8.0;
            double v00 = col[0][j] + (col[0][j + 1] - col[0][j]) * fy, v01 = col[1][j] + (col[1][j + 1] - col[1][j]) * fy;
            double v10 = col[2][j] + (col[2][j + 1] - col[2][j]) * fy, v11 = col[3][j] + (col[3][j + 1] - col[3][j]) * fy;
            double v0 = v00 + (v10 - v00) * fx, v1 = v01 + (v11 - v01) * fx;
            if (v0 + (v1 - v0) * fz > 0) return y;
        }
        return 0;
    }

    // ------------------------------------------------------------------ generation
    @Override
    public Chunk generate(int cx, int cz) {
        Chunk ch = new Chunk(cx, cz);
        int bx = cx << 4, bz = cz << 4;
        char[] blocks = new char[65536]; // y<<8|z<<4|x
        double[] hs = new double[25], ms = new double[25];
        double[] g = densityGrid(cx, cz, hs, ms);
        int stone = Blocks.STONE, water = Blocks.WATER, air = Blocks.AIR;
        // SHAPE
        for (int y = 0; y < 256; y++)
            for (int z = 0; z < 16; z++)
                for (int x = 0; x < 16; x++) {
                    int s = air;
                    if (tri(g, x, y, z) > 0) s = stone;
                    else if (y <= SEA) s = water;
                    blocks[(y << 8) | (z << 4) | x] = (char) s;
                }
        ch.status = 1;
        // biomes per column
        Biome[] biomes = new Biome[256];
        double[] o = new double[2];
        for (int z = 0; z < 16; z++)
            for (int x = 0; x < 16; x++) {
                double h = height(bx + x, bz + z, o);
                Biome bi = pickBiome(bx + x, bz + z, h, o[0], o[1]);
                biomes[z << 4 | x] = bi;
                ch.biomes[z << 4 | x] = (byte) bi.index;
            }
        // SURFACE
        SplitMix bed = new SplitMix(Hash.chunk(seed, cx, cz, SALT_BEDROCK));
        for (int z = 0; z < 16; z++)
            for (int x = 0; x < 16; x++) {
                Biome bi = biomes[z << 4 | x];
                int depthNoise = 3 + (int) (surfNoise.noise2((bx + x) / 8.0, (bz + z) / 8.0) * 3 + 1.5);
                int run = -1;
                boolean stony = bi.flag("stony_peaks");
                for (int y = 255; y >= 0; y--) {
                    int i = (y << 8) | (z << 4) | x;
                    if (y <= 4 && (y == 0 || bed.nextInt(5) >= y)) { blocks[i] = (char) Blocks.BEDROCK; continue; }
                    int s = blocks[i];
                    if (s != stone) { run = -1; continue; }
                    run++;
                    if (run > depthNoise) {
                        if (run <= depthNoise + 3 && bi.flag("stone_below") && blocks[i] == stone) blocks[i] = (char) Blocks.SANDSTONE;
                        continue;
                    }
                    boolean underwater = y < SEA && (blocks[Math.min(255, y + run + 1) << 8 | (z << 4) | x] == water || blocks[((y + 1) << 8) | (z << 4) | x] == water);
                    int top, fill;
                    if (stony && y > 100) { top = stone; fill = stone; }
                    else if (underwater) { top = bi.underwater; fill = bi.underwater == Blocks.GRAVEL ? Blocks.GRAVEL : bi.filler; if (y < SEA - 20 && bi == OCEAN) top = Blocks.GRAVEL; }
                    else { top = bi.top; fill = bi.filler; }
                    if (bi.flag("bands") && !underwater) {
                        String[] bands = {"orange_terracotta", "terracotta", "yellow_terracotta", "brown_terracotta", "red_terracotta", "white_terracotta", "terracotta", "light_gray_terracotta"};
                        int band = Blocks.base("minecraft:" + bands[Math.floorMod(y + (int) (surfNoise.noise2((bx + x) / 40.0, (bz + z) / 40.0) * 4), bands.length)]);
                        if (run == 0) top = y > 70 ? band : bi.top;
                        fill = band;
                        if (run > 0 && y > 60) { blocks[i] = (char) band; continue; }
                    }
                    blocks[i] = (char) (run == 0 ? top : fill);
                    if (run == 0 && top == Blocks.GRASS_BLOCK && y < SEA - 1) blocks[i] = (char) Blocks.DIRT;
                }
            }
        ch.status = 2;
        // CARVE (Section 4.4)
        carve(cx, cz, blocks);
        ravines(cx, cz, blocks);
        ch.status = 3;
        // FEATURES: ores
        SplitMix orng = new SplitMix(Hash.chunk(seed, cx, cz, SALT_ORE));
        for (int i = 0; i < ores.size(); i++) {
            Map<String, Object> ore = ores.get(i);
            List<Object> bl = Json.arr(ore.get("biomes"));
            if (!bl.isEmpty() && !bl.contains(biomes[8 * 16 + 8].id)) continue;
            int attempts = Json.integer(ore, "attempts", 1), size = Json.integer(ore, "size", 8);
            int minY = Json.integer(ore, "min_y", 0), maxY = Json.integer(ore, "max_y", 64);
            for (int a = 0; a < attempts; a++) {
                int x = orng.nextInt(16), z = orng.nextInt(16), y = orng.range(minY, maxY);
                vein(blocks, x, y, z, size, oreState[i], stone, orng);
            }
        }
        ch.status = 4;
        // DECORATE
        Features.BlockSink sink = new Features.BlockSink() {
            public int get(int x, int y, int z) {
                x -= bx; z -= bz;
                if (x < 0 || x > 15 || z < 0 || z > 15 || y < 0 || y > 255) return Blocks.AIR;
                return blocks[(y << 8) | (z << 4) | x];
            }

            public void set(int x, int y, int z, int s) {
                x -= bx; z -= bz;
                if (x < 0 || x > 15 || z < 0 || z > 15 || y < 0 || y > 255) return;
                blocks[(y << 8) | (z << 4) | x] = (char) s;
            }
        };
        plants(cx, cz, blocks, biomes);
        for (int dcx = -1; dcx <= 1; dcx++)
            for (int dcz = -1; dcz <= 1; dcz++) trees(cx + dcx, cz + dcz, sink, dcx == 0 && dcz == 0 ? blocks : null);
        freeze(blocks, biomes);
        ch.status = 5;
        // STRUCTURES
        dungeon(cx, cz, blocks, ch);
        ch.status = 6;
        for (int s = 0; s < Chunk.SECTIONS; s++) ch.sections[s] = PalettedContainer.fromArray(blocks, s << 12);
        ch.recomputeHeightmap();
        ch.status = 7;
        return ch;
    }

    private void vein(char[] blocks, int x, int y, int z, int size, int ore, int stone, SplitMix r) {
        for (int n = 0; n < size; n++) {
            if (x >= 0 && x < 16 && z >= 0 && z < 16 && y > 0 && y < 256) {
                int i = (y << 8) | (z << 4) | x;
                int s = blocks[i];
                if (s == stone || s == Blocks.GRANITE || s == Blocks.DIORITE || s == Blocks.ANDESITE) blocks[i] = (char) ore;
            }
            switch (r.nextInt(6)) {
                case 0: x++; break;
                case 1: x--; break;
                case 2: y++; break;
                case 3: y--; break;
                case 4: z++; break;
                default: z--; break;
            }
        }
    }

    /** Cheese caves (4x8x4 interpolated cells) and noodle caves (two ridged fields). */
    private void carve(int cx, int cz, char[] blocks) {
        int bx = cx << 4, bz = cz << 4;
        double[] gc = new double[5 * 5 * 33], gn = new double[5 * 5 * 33];
        for (int i = 0; i < 5; i++)
            for (int k = 0; k < 5; k++)
                for (int j = 0; j < 33; j++) {
                    double x = bx + i * 4, z = bz + k * 4, y = j * 8;
                    int id = (i * 5 + k) * 33 + j;
                    gc[id] = cheese.fbm(x / 80.0, y / 40.0, z / 80.0, 2) * 2.0 - (y > 50 ? (y - 50) / 70.0 : 0);
                    double a = noodleA.noise(x / 40.0, y / 24.0, z / 40.0), b = noodleB.noise(x / 40.0, y / 24.0, z / 40.0);
                    double w = 0.06 + noodleW.noise2(x / 120.0, z / 120.0) * 0.03;
                    gn[id] = Math.max(Math.abs(a), Math.abs(b)) - w;
                }
        int lavaLevel = Registries.tunei("lava_level");
        for (int z = 0; z < 16; z++)
            for (int x = 0; x < 16; x++) {
                int topSolid = 0;
                for (int y = 255; y > 0; y--) if (Blocks.solidCollision[blocks[(y << 8) | (z << 4) | x]]) { topSolid = y; break; }
                for (int y = 6; y < Math.min(topSolid + 1, 200); y++) {
                    int i = (y << 8) | (z << 4) | x;
                    int s = blocks[i];
                    if (s == Blocks.AIR || Blocks.fluid[s] || s == Blocks.BEDROCK) continue;
                    double ch = tri(gc, x, y, z);
                    if (y > topSolid - 10) ch -= (y - (topSolid - 10)) * 0.06;
                    boolean cave = ch > 0.5;
                    if (!cave && y < 130) cave = tri(gn, x, y, z) < 0;
                    if (!cave) continue;
                    // water guard: never open into water or right under the sea floor
                    if (y < 255 && Blocks.fluid[blocks[((y + 1) << 8) | (z << 4) | x]]) continue;
                    if (y >= SEA - 6 && y + 3 <= 255 && Blocks.fluid[blocks[((Math.min(255, y + 3)) << 8) | (z << 4) | x]]) continue;
                    if (neighborWater(blocks, x, y, z)) continue;
                    blocks[i] = (char) (y <= lavaLevel ? Blocks.LAVA : Blocks.AIR);
                    // expose dirt below grass as grass when the cave opens to the sky
                    if (y > 0 && blocks[((y - 1) << 8) | (z << 4) | x] == Blocks.DIRT && y >= topSolid) blocks[((y - 1) << 8) | (z << 4) | x] = (char) Blocks.GRASS_BLOCK;
                }
            }
    }

    private static boolean neighborWater(char[] b, int x, int y, int z) {
        if (x > 0 && Blocks.isWater(b[(y << 8) | (z << 4) | (x - 1)])) return true;
        if (x < 15 && Blocks.isWater(b[(y << 8) | (z << 4) | (x + 1)])) return true;
        if (z > 0 && Blocks.isWater(b[(y << 8) | ((z - 1) << 4) | x])) return true;
        if (z < 15 && Blocks.isWater(b[(y << 8) | ((z + 1) << 4) | x])) return true;
        return false;
    }

    /** Ravines: rare per-chunk carvers; neighbors within 3 chunks are evaluated so the carve crosses borders. */
    private void ravines(int cx, int cz, char[] blocks) {
        int bx = cx << 4, bz = cz << 4;
        for (int ox = -3; ox <= 3; ox++)
            for (int oz = -3; oz <= 3; oz++) {
                SplitMix r = new SplitMix(Hash.chunk(seed, cx + ox, cz + oz, SALT_RAVINE));
                if (r.nextInt(60) != 0) continue;
                double x = ((cx + ox) << 4) + r.nextInt(16), z = ((cz + oz) << 4) + r.nextInt(16), y = 20 + r.nextInt(40);
                double yaw = r.nextDouble() * Math.PI * 2, pitch = (r.nextDouble() - 0.5) * 0.25;
                int len = 70 + r.nextInt(40);
                double width = 2 + r.nextDouble() * 2;
                for (int s = 0; s < len; s++) {
                    double rad = 1.5 + Math.sin(s * Math.PI / len) * width;
                    double vrad = rad * 3;
                    x += Math.cos(yaw);
                    z += Math.sin(yaw);
                    y += Math.sin(pitch);
                    yaw += (r.nextDouble() - 0.5) * 0.15;
                    pitch *= 0.7;
                    if (x + rad < bx || x - rad > bx + 16 || z + rad < bz || z - rad > bz + 16) continue;
                    for (int px = (int) Math.floor(x - rad); px <= x + rad; px++)
                        for (int pz = (int) Math.floor(z - rad); pz <= z + rad; pz++) {
                            if (px < bx || px >= bx + 16 || pz < bz || pz >= bz + 16) continue;
                            double dx = (px + 0.5 - x) / rad, dz = (pz + 0.5 - z) / rad;
                            if (dx * dx + dz * dz >= 1) continue;
                            for (int py = (int) Math.max(6, y - vrad); py <= Math.min(180, y + vrad); py++) {
                                double dy = (py + 0.5 - y) / vrad;
                                if (dx * dx + dz * dz + dy * dy >= 1) continue;
                                int lx = px - bx, lz = pz - bz;
                                int i = (py << 8) | (lz << 4) | lx;
                                int st = blocks[i];
                                if (st == Blocks.AIR || Blocks.fluid[st] || st == Blocks.BEDROCK) continue;
                                if (py < 255 && Blocks.fluid[blocks[((py + 1) << 8) | (lz << 4) | lx]]) continue;
                                if (neighborWater(blocks, lx, py, lz)) continue;
                                blocks[i] = (char) (py <= 10 ? Blocks.LAVA : Blocks.AIR);
                            }
                        }
                }
            }
    }

    private int top(char[] blocks, int x, int z) {
        for (int y = 254; y > 0; y--) {
            int s = blocks[(y << 8) | (z << 4) | x];
            if (s != Blocks.AIR && !Blocks.block(s).id.endsWith("_leaves")) return y;
        }
        return 0;
    }

    /** Trees from chunk (tcx,tcz) written through the sink (clipped to the current chunk). */
    private void trees(int tcx, int tcz, Features.BlockSink sink, char[] own) {
        SplitMix r = new SplitMix(Hash.chunk(seed, tcx, tcz, SALT_TREE));
        int cxm = (tcx << 4) + 8, czm = (tcz << 4) + 8;
        Biome bi = biomeAt(cxm, czm);
        if (bi.trees.isEmpty() || bi.treeDensity <= 0) return;
        double expected = bi.treeDensity * 256 / 9.0;
        int n = (int) expected + (r.nextDouble() < expected - (int) expected ? 1 : 0);
        for (int t = 0; t < n; t++) {
            int x = (tcx << 4) + r.nextInt(16), z = (tcz << 4) + r.nextInt(16);
            String wood = bi.trees.get(r.nextInt(bi.trees.size()));
            long tseed = r.nextLong();
            Biome here = biomeAt(x, z);
            if (here.trees.isEmpty()) continue;
            int y = columnTop(x, z);
            if (y < SEA || y > 200) continue;
            if (own != null) {
                int lx = x & 15, lz = z & 15;
                int ground = blocks(own, lx, y, lz);
                if (ground != Blocks.GRASS_BLOCK && ground != Blocks.DIRT && ground != Blocks.PODZOL) continue;
                if (blocks(own, lx, y + 1, lz) != Blocks.AIR) continue;
            } else {
                int top = here.top;
                if (top != Blocks.GRASS_BLOCK && top != Blocks.PODZOL && top != Blocks.DIRT) continue;
            }
            SplitMix tr = new SplitMix(tseed);
            if (own != null) sink.set(x, y, z, Blocks.DIRT);
            Features.tree(sink, wood, x, y + 1, z, Features.of(tr), false);
        }
    }

    private static int blocks(char[] b, int x, int y, int z) { return y < 0 || y > 255 ? Blocks.AIR : b[(y << 8) | (z << 4) | x]; }

    private void plants(int cx, int cz, char[] blocks, Biome[] biomes) {
        SplitMix r = new SplitMix(Hash.chunk(seed, cx, cz, SALT_PLANT));
        String[] flowers = {"dandelion", "poppy", "azure_bluet", "oxeye_daisy", "cornflower", "red_tulip", "orange_tulip", "white_tulip",
                "pink_tulip", "allium", "lily_of_the_valley"};
        for (int z = 0; z < 16; z++)
            for (int x = 0; x < 16; x++) {
                Biome bi = biomes[z << 4 | x];
                int y = top(blocks, x, z);
                if (y <= 0 || y >= 254) continue;
                int ground = blocks[(y << 8) | (z << 4) | x];
                int ai = ((y + 1) << 8) | (z << 4) | x;
                if (blocks[ai] != Blocks.AIR) continue;
                double roll = r.nextDouble();
                if (ground == Blocks.GRASS_BLOCK || ground == Blocks.PODZOL) {
                    if (roll < bi.flowerDensity) {
                        String f = bi.id.equals("minecraft:swamp") ? "blue_orchid" : flowers[r.nextInt(bi.id.contains("flower") ? flowers.length : 2)];
                        blocks[ai] = (char) Blocks.base("minecraft:" + f);
                    } else if (roll < bi.flowerDensity + bi.grassDensity) {
                        blocks[ai] = (char) (bi.flag("ferns") && r.nextInt(3) == 0 ? Blocks.FERN : Blocks.GRASS);
                    } else if (roll > 0.9985 && y > SEA) {
                        blocks[ai] = (char) Blocks.PUMPKIN;
                    } else if (bi.flag("mushrooms") && roll > 0.99) {
                        blocks[ai] = (char) (r.nextInt(2) == 0 ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM);
                    }
                } else if (ground == Blocks.SAND || ground == Blocks.RED_SAND) {
                    if (bi.flag("cactus") && roll < 0.004 && cactusOk(blocks, x, y + 1, z)) {
                        int h = 1 + r.nextInt(3);
                        for (int i = 0; i < h && y + 1 + i < 255; i++) blocks[((y + 1 + i) << 8) | (z << 4) | x] = (char) Blocks.CACTUS;
                    } else if (bi.flag("dead_bush") && roll > 0.993) {
                        blocks[ai] = (char) Blocks.DEAD_BUSH;
                    }
                }
                if (ground == Blocks.MYCELIUM && roll < 0.01) blocks[ai] = (char) (r.nextInt(2) == 0 ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM);
                // sugar cane next to water
                if ((ground == Blocks.GRASS_BLOCK || ground == Blocks.SAND || ground == Blocks.DIRT) && y == SEA && r.nextInt(14) == 0 && waterAdjacent(blocks, x, y, z)) {
                    int h = 1 + r.nextInt(3);
                    for (int i = 0; i < h; i++) blocks[((y + 1 + i) << 8) | (z << 4) | x] = (char) Blocks.SUGAR_CANE;
                }
            }
        // underground mushrooms / lava & water springs omitted (Tier 2)
    }

    private static boolean cactusOk(char[] b, int x, int y, int z) {
        if (x == 0 || x == 15 || z == 0 || z == 15) return false;
        return b[(y << 8) | (z << 4) | (x - 1)] == Blocks.AIR && b[(y << 8) | (z << 4) | (x + 1)] == Blocks.AIR
                && b[(y << 8) | ((z - 1) << 4) | x] == Blocks.AIR && b[(y << 8) | ((z + 1) << 4) | x] == Blocks.AIR;
    }

    private static boolean waterAdjacent(char[] b, int x, int y, int z) {
        return (x > 0 && Blocks.isWater(b[(y << 8) | (z << 4) | (x - 1)])) || (x < 15 && Blocks.isWater(b[(y << 8) | (z << 4) | (x + 1)]))
                || (z > 0 && Blocks.isWater(b[(y << 8) | ((z - 1) << 4) | x])) || (z < 15 && Blocks.isWater(b[(y << 8) | ((z + 1) << 4) | x]));
    }

    /** Snow layers and ice in cold biomes (Section 4.5 "freeze top layer"). */
    private void freeze(char[] blocks, Biome[] biomes) {
        for (int z = 0; z < 16; z++)
            for (int x = 0; x < 16; x++) {
                Biome bi = biomes[z << 4 | x];
                if (!bi.snowy()) continue;
                int y = 254;
                while (y > 0 && blocks[(y << 8) | (z << 4) | x] == Blocks.AIR) y--;
                int i = (y << 8) | (z << 4) | x;
                int s = blocks[i];
                if (Blocks.isWater(s)) blocks[i] = (char) Blocks.ICE;
                else if (Blocks.opaqueCube[s] || Blocks.block(s).id.endsWith("_leaves")) {
                    blocks[((y + 1) << 8) | (z << 4) | x] = (char) Blocks.SNOW;
                    if (s == Blocks.GRASS_BLOCK) blocks[i] = (char) Blocks.GRASS_BLOCK; // snowy variant is a render-time check
                }
                if (bi.id.equals("minecraft:ice_spikes") && ((x * 7 + z * 13) & 63) == 0 && Blocks.opaqueCube[s]) {
                    int h = 6 + ((x + z) % 10);
                    for (int k = 1; k <= h && y + k < 250; k++) blocks[((y + k) << 8) | (z << 4) | x] = (char) Blocks.PACKED_ICE;
                }
            }
    }

    /** Small dungeon room with a spawner and a loot chest (Section 4.6 structures, Tier 1 subset). */
    private void dungeon(int cx, int cz, char[] blocks, Chunk ch) {
        SplitMix r = new SplitMix(Hash.chunk(seed, cx, cz, SALT_DUNGEON));
        if (r.nextInt(14) != 0) return;
        int x0 = 4 + r.nextInt(4), z0 = 4 + r.nextInt(4), y0 = 12 + r.nextInt(36);
        int w = 7, d = 7, h = 5;
        int open = 0;
        for (int x = x0; x < x0 + w; x++)
            for (int z = z0; z < z0 + d; z++) {
                if (blocks[(y0 << 8) | (z << 4) | x] == Blocks.AIR) open++;
                if (Blocks.fluid[blocks[((y0 + h) << 8) | (z << 4) | x]]) return;
            }
        if (open > 20) return;
        for (int x = x0; x < x0 + w; x++)
            for (int z = z0; z < z0 + d; z++)
                for (int y = y0; y < y0 + h; y++) {
                    boolean wall = x == x0 || x == x0 + w - 1 || z == z0 || z == z0 + d - 1 || y == y0 || y == y0 + h - 1;
                    int i = (y << 8) | (z << 4) | x;
                    if (wall) {
                        if (blocks[i] != Blocks.AIR || y == y0) blocks[i] = (char) (y == y0 && r.nextInt(4) != 0 ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE);
                    } else blocks[i] = (char) Blocks.AIR;
                }
        int sx = x0 + 3, sz = z0 + 3;
        blocks[((y0 + 1) << 8) | (sz << 4) | sx] = (char) Blocks.SPAWNER;
        int chx = x0 + 1, chz = z0 + 1 + r.nextInt(5);
        blocks[((y0 + 1) << 8) | (chz << 4) | chx] = (char) Blocks.CHEST;
        BlockEntity be = new BlockEntity("chest", (cx << 4) + chx, y0 + 1, (cz << 4) + chz);
        String[][] loot = {{"minecraft:bread", "1", "3"}, {"minecraft:wheat", "1", "4"}, {"minecraft:iron_ingot", "1", "4"}, {"minecraft:gold_ingot", "1", "3"},
                {"minecraft:string", "1", "4"}, {"minecraft:bone", "1", "6"}, {"minecraft:gunpowder", "1", "4"}, {"minecraft:coal", "1", "4"},
                {"minecraft:apple", "1", "3"}, {"minecraft:diamond", "1", "1"}, {"minecraft:golden_apple", "1", "1"}, {"minecraft:bucket", "1", "1"}};
        int rolls = 3 + r.nextInt(4);
        for (int i = 0; i < rolls; i++) {
            String[] l = loot[r.nextInt(loot.length)];
            Item it = Items.get(l[0]);
            if (it == null) continue;
            be.items[r.nextInt(27)] = new ItemStack(it, Integer.parseInt(l[1]) + r.nextInt(Integer.parseInt(l[2]) - Integer.parseInt(l[1]) + 1));
        }
        ch.blockEntities.put(BlockEntity.localKey(be.x, be.y, be.z), be);
    }
}
