package mc.gen;

import mc.content.*;
import mc.core.*;
import mc.util.*;

/** The End (Section 5.6, Tier 3 subset): a central end-stone island with obsidian pillars over the void. */
public final class EndGenerator implements ChunkGenerator {
    private final long seed;
    private final Noise n;

    public EndGenerator(long seed) {
        this.seed = seed;
        n = new Noise(seed, 0xE4D);
    }

    @Override
    public Biome biomeAt(int x, int z) { return Biomes.get("the_end"); }

    @Override
    public int surfaceHeight(int x, int z) { return 64; }

    @Override
    public Chunk generate(int cx, int cz) {
        Chunk ch = new Chunk(cx, cz);
        char[] b = new char[65536];
        java.util.Arrays.fill(b, (char) Blocks.AIR);
        for (int z = 0; z < 16; z++)
            for (int x = 0; x < 16; x++) {
                int wx = (cx << 4) + x, wz = (cz << 4) + z;
                double d = Math.sqrt((double) wx * wx + (double) wz * wz);
                double edge = 90 + n.fbm2(wx / 50.0, wz / 50.0, 3) * 30;
                if (d > edge) continue;
                double t = 1 - d / edge;
                int top = 56 + (int) (t * 8 + n.noise2(wx / 20.0, wz / 20.0) * 3);
                int bottom = top - (int) (t * 40) - 2;
                for (int y = Math.max(1, bottom); y <= top; y++) b[(y << 8) | (z << 4) | x] = (char) Blocks.END_STONE;
                ch.biomes[z << 4 | x] = (byte) biomeAt(wx, wz).index;
            }
        // obsidian pillars on a ring of radius 42
        for (int p = 0; p < 10; p++) {
            double a = p * Math.PI * 2 / 10;
            int px = (int) Math.round(Math.cos(a) * 42), pz = (int) Math.round(Math.sin(a) * 42);
            int r = 2 + (p % 3), h = 76 + p % 4 * 9;
            for (int x = 0; x < 16; x++)
                for (int z = 0; z < 16; z++) {
                    int wx = (cx << 4) + x - px, wz = (cz << 4) + z - pz;
                    if (wx * wx + wz * wz > r * r) continue;
                    for (int y = 50; y < h; y++) b[(y << 8) | (z << 4) | x] = (char) Blocks.OBSIDIAN;
                }
        }
        for (int s = 0; s < Chunk.SECTIONS; s++) ch.sections[s] = PalettedContainer.fromArray(b, s << 12);
        ch.recomputeHeightmap();
        return ch;
    }
}
