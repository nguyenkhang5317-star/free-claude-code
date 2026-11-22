package mc.core;

import mc.content.Biome;

/** Pure generation function (Section 2.7): output depends only on (seed, chunk coordinate). */
public interface ChunkGenerator {
    Chunk generate(int cx, int cz);

    Biome biomeAt(int x, int z);

    /** Approximate surface height of the column before carving (used for spawn search). */
    int surfaceHeight(int x, int z);
}
