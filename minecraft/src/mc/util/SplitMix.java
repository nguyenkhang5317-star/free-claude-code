package mc.util;

/** splitmix64 sequence (Section 4.1): per-chunk RNG seeded from hash(worldSeed, chunkX, chunkZ, stageSalt). */
public final class SplitMix {
    private long state;

    public SplitMix(long seed) { state = seed; }

    public long nextLong() {
        long z = (state += 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public int nextInt(int bound) {
        if (bound <= 0) return 0;
        return (int) Long.remainderUnsigned(nextLong(), bound);
    }

    public int range(int min, int maxInclusive) { return min + nextInt(maxInclusive - min + 1); }

    public double nextDouble() { return (nextLong() >>> 11) * 0x1.0p-53; }

    public float nextFloat() { return (float) nextDouble(); }

    public boolean nextBoolean() { return (nextLong() & 1) != 0; }

    public boolean chance(double p) { return nextDouble() < p; }
}
