package mc.util;

/** Deterministic 64-bit hashing used for all seed derivations (Section 4.1). */
public final class Hash {
    private Hash() {}

    public static long mix(long a) {
        a ^= a >>> 33;
        a *= 0xFF51AFD7ED558CCDL;
        a ^= a >>> 33;
        a *= 0xC4CEB9FE1A85EC53L;
        a ^= a >>> 33;
        return a;
    }

    public static long mix(long a, long b) { return mix(a * 0x9E3779B97F4A7C15L + mix(b)); }

    public static long chunk(long seed, int cx, int cz, long salt) {
        return mix(mix(mix(seed, cx), cz), salt);
    }

    public static long pos(long seed, int x, int y, int z) { return mix(mix(mix(seed, x), y), z); }

    /** Seed from a user string: numeric strings are used directly, otherwise a stable string hash. */
    public static long seedFromString(String s) {
        s = s == null ? "" : s.trim();
        if (s.isEmpty()) return System.nanoTime() ^ mix(System.currentTimeMillis());
        try { return Long.parseLong(s); } catch (NumberFormatException e) { /* fallthrough */ }
        long h = 1125899906842597L;
        for (int i = 0; i < s.length(); i++) h = 31 * h + s.charAt(i);
        return h;
    }
}
