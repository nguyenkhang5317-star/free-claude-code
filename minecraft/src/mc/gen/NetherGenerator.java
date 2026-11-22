package mc.gen;

import mc.content.*;
import mc.core.*;
import mc.util.*;

import java.util.*;

/** Nether generator (Section 5.6): 128-high cavern field between bedrock floor and roof, lava sea at Y 31,
 * five biomes chosen from two noise fields, glowstone clusters, nether ores. Pure in (seed, cx, cz). */
public final class NetherGenerator implements ChunkGenerator {
    private final long seed;
    private final Noise cave, cave2, bioA, bioB, surf;
    private final List<Map<String, Object>> ores = new ArrayList<>();
    private final Biome WASTES, CRIMSON, WARPED, SOUL, DELTAS;

    public NetherGenerator(long seed) {
        this.seed = seed;
        cave = new Noise(seed, 0x4E7A);
        cave2 = new Noise(seed, 0x4E7B);
        bioA = new Noise(seed, 0x4EB1);
        bioB = new Noise(seed, 0x4EB2);
        surf = new Noise(seed, 0x4E5F);
        for (Object o : Json.arr(Registries.WORLDGEN.get("nether_ores"))) ores.add(Json.obj(o));
        WASTES = Biomes.get("nether_wastes"); CRIMSON = Biomes.get("crimson_forest"); WARPED = Biomes.get("warped_forest");
        SOUL = Biomes.get("soul_sand_valley"); DELTAS = Biomes.get("basalt_deltas");
    }

    @Override
    public Biome biomeAt(int x, int z) {
        double a = bioA.fbm2(x / 260.0, z / 260.0, 3), b = bioB.fbm2(x / 260.0, z / 260.0, 3);
        if (a > 0.12) return b > 0 ? CRIMSON : WARPED;
        if (a < -0.14) return b > 0.05 ? SOUL : (b < -0.12 ? DELTAS : WASTES);
        return WASTES;
    }

    @Override
    public int surfaceHeight(int x, int z) { return 64; }

    private double density(double x, double y, double z) {
        double d = cave.fbm(x / 70.0, y / 36.0, z / 70.0, 3) * 2.0 + cave2.noise(x / 24.0, y / 18.0, z / 24.0) * 0.35;
        // close toward floor and roof
        if (y < 32) d += (32 - y) / 18.0;
        if (y > 100) d += (y - 100) / 14.0;
        return d - 0.05;
    }

    @Override
    public Chunk generate(int cx, int cz) {
        Chunk ch = new Chunk(cx, cz);
        int bx = cx << 4, bz = cz << 4;
        char[] b = new char[65536];
        double[] g = new double[5 * 5 * 17];
        for (int i = 0; i < 5; i++)
            for (int k = 0; k < 5; k++)
                for (int j = 0; j < 17; j++) g[(i * 5 + k) * 17 + j] = density(bx + i * 4, j * 8, bz + k * 4);
        int lava = Registries.tunei("nether_lava_level");
        SplitMix r = new SplitMix(Hash.chunk(seed, cx, cz, 0x4E));
        Biome[] bio = new Biome[256];
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) { bio[z << 4 | x] = biomeAt(bx + x, bz + z); ch.biomes[z << 4 | x] = (byte) bio[z << 4 | x].index; }
        for (int y = 0; y < 128; y++)
            for (int z = 0; z < 16; z++)
                for (int x = 0; x < 16; x++) {
                    int i = (y << 8) | (z << 4) | x;
                    if (y == 0 || y == 127 || (y < 5 && r.nextInt(5) >= y) || (y > 122 && r.nextInt(5) >= 127 - y)) { b[i] = (char) Blocks.BEDROCK; continue; }
                    int gi = x >> 2, gk = z >> 2, gj = y >> 3;
                    double fx = (x & 3) / 4.0, fz = (z & 3) / 4.0, fy = (y & 7) / 8.0;
                    double c00 = lerp(fy, g[(gi * 5 + gk) * 17 + gj], g[(gi * 5 + gk) * 17 + gj + 1]);
                    double c10 = lerp(fy, g[((gi + 1) * 5 + gk) * 17 + gj], g[((gi + 1) * 5 + gk) * 17 + gj + 1]);
                    double c01 = lerp(fy, g[(gi * 5 + gk + 1) * 17 + gj], g[(gi * 5 + gk + 1) * 17 + gj + 1]);
                    double c11 = lerp(fy, g[((gi + 1) * 5 + gk + 1) * 17 + gj], g[((gi + 1) * 5 + gk + 1) * 17 + gj + 1]);
                    double d = lerp(fz, lerp(fx, c00, c10), lerp(fx, c01, c11));
                    b[i] = (char) (d > 0 ? Blocks.NETHERRACK : y <= lava ? Blocks.LAVA : Blocks.AIR);
                }
        // surface by biome
        for (int z = 0; z < 16; z++)
            for (int x = 0; x < 16; x++) {
                Biome bi = bio[z << 4 | x];
                for (int y = 120; y > 1; y--) {
                    int i = (y << 8) | (z << 4) | x;
                    if (b[i] != Blocks.NETHERRACK || b[((y + 1) << 8) | (z << 4) | x] != Blocks.AIR) continue;
                    b[i] = (char) bi.top;
                    for (int k = 1; k <= 3 && y - k > 0; k++) {
                        int j = ((y - k) << 8) | (z << 4) | x;
                        if (b[j] == Blocks.NETHERRACK) b[j] = (char) bi.filler;
                    }
                    if (bi == WASTES && surf.noise2((bx + x) / 6.0, (bz + z) / 6.0) > 0.45 && y > lava && y < lava + 4) b[i] = (char) Blocks.SOUL_SAND;
                    if ((bi == CRIMSON || bi == WARPED) && r.nextInt(60) == 0) mushroom(b, x, y + 1, z, bi == CRIMSON, r);
                    if (bi == WASTES && r.nextInt(250) == 0 && y + 1 < 127) b[((y + 1) << 8) | (z << 4) | x] = (char) Blocks.FIRE;
                    if (bi == DELTAS && r.nextInt(90) == 0) for (int k = 1; k < 2 + r.nextInt(6) && y + k < 120; k++) b[((y + k) << 8) | (z << 4) | x] = (char) Blocks.BASALT;
                }
            }
        // glowstone clusters hanging from the roof
        for (int n = 0; n < 6; n++) {
            int x = r.nextInt(16), z = r.nextInt(16), y = 70 + r.nextInt(50);
            if (b[(y << 8) | (z << 4) | x] != Blocks.AIR || b[((y + 1) << 8) | (z << 4) | x] != Blocks.NETHERRACK) continue;
            for (int k = 0; k < 60; k++) {
                int px = x + r.nextInt(5) - 2, py = y - r.nextInt(5), pz = z + r.nextInt(5) - 2;
                if (px < 0 || px > 15 || pz < 0 || pz > 15) continue;
                int i = (py << 8) | (pz << 4) | px;
                if (b[i] == Blocks.AIR) b[i] = (char) Blocks.GLOWSTONE;
            }
        }
        for (Map<String, Object> ore : ores) {
            int st = Blocks.base(Json.str(ore, "block", "minecraft:netherrack"));
            int at = Json.integer(ore, "attempts", 1), size = Json.integer(ore, "size", 4);
            int lo = Json.integer(ore, "min_y", 10), hi = Json.integer(ore, "max_y", 117);
            for (int a = 0; a < at; a++) {
                int x = r.nextInt(16), z = r.nextInt(16), y = r.range(lo, hi);
                for (int s = 0; s < size; s++) {
                    if (x >= 0 && x < 16 && z >= 0 && z < 16 && y > 0 && y < 127) {
                        int i = (y << 8) | (z << 4) | x;
                        if (b[i] == Blocks.NETHERRACK || (st == Blocks.ANCIENT_DEBRIS && (b[i] == Blocks.BASALT || b[i] == Blocks.BLACKSTONE))) b[i] = (char) st;
                    }
                    switch (r.nextInt(6)) { case 0: x++; break; case 1: x--; break; case 2: y++; break; case 3: y--; break; case 4: z++; break; default: z--; }
                }
            }
        }
        for (int s = 0; s < Chunk.SECTIONS; s++) ch.sections[s] = PalettedContainer.fromArray(b, s << 12);
        ch.recomputeHeightmap();
        return ch;
    }

    private void mushroom(char[] b, int x, int y, int z, boolean crimson, SplitMix r) {
        int stem = crimson ? Blocks.CRIMSON_STEM : Blocks.WARPED_STEM;
        int cap = crimson ? Blocks.NETHER_WART_BLOCK : Math.max(Blocks.NETHER_WART_BLOCK, Blocks.base("minecraft:warped_wart_block"));
        int h = 4 + r.nextInt(5);
        if (y + h + 2 > 120) return;
        for (int dy = h - 2; dy <= h; dy++)
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++) {
                    int px = x + dx, pz = z + dz, py = y + dy;
                    if (px < 0 || px > 15 || pz < 0 || pz > 15) continue;
                    if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                    int i = (py << 8) | (pz << 4) | px;
                    if (b[i] == Blocks.AIR) b[i] = (char) (r.nextInt(12) == 0 ? Blocks.SHROOMLIGHT : cap);
                }
        for (int k = 0; k < h; k++) b[((y + k) << 8) | (z << 4) | x] = (char) stem;
    }

    private static double lerp(double t, double a, double b) { return a + (b - a) * t; }
}
