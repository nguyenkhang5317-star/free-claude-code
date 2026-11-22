package mc.util;

/**
 * Improved-Perlin gradient noise (Section 4.1): integer lattice gradients hashed from (field seed, lattice
 * coordinates), quintic interpolation, output normalized to roughly [-1, 1]. Each field gets its own seed
 * derived from hash(worldSeed, salt) so no two fields correlate. Pure function — deterministic on every JVM.
 */
public final class Noise {
    private final int[] perm = new int[512];
    private final double ox, oy, oz;

    public Noise(long worldSeed, long salt) {
        SplitMix rng = new SplitMix(Hash.mix(worldSeed, salt));
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        for (int i = 255; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int t = p[i]; p[i] = p[j]; p[j] = t;
        }
        for (int i = 0; i < 512; i++) perm[i] = p[i & 255];
        ox = rng.nextDouble() * 256;
        oy = rng.nextDouble() * 256;
        oz = rng.nextDouble() * 256;
    }

    private static double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }

    private static double lerp(double t, double a, double b) { return a + t * (b - a); }

    private static double grad(int h, double x, double y, double z) {
        switch (h & 15) {
            case 0: return x + y;   case 1: return -x + y;  case 2: return x - y;   case 3: return -x - y;
            case 4: return x + z;   case 5: return -x + z;  case 6: return x - z;   case 7: return -x - z;
            case 8: return y + z;   case 9: return -y + z;  case 10: return y - z;  case 11: return -y - z;
            case 12: return x + y;  case 13: return -x + y; case 14: return -y + z; default: return -y - z;
        }
    }

    public double noise(double x, double y, double z) {
        x += ox; y += oy; z += oz;
        double fx = Math.floor(x), fy = Math.floor(y), fz = Math.floor(z);
        int X = ((int) fx) & 255, Y = ((int) fy) & 255, Z = ((int) fz) & 255;
        x -= fx; y -= fy; z -= fz;
        double u = fade(x), v = fade(y), w = fade(z);
        int[] p = perm;
        int A = p[X] + Y, AA = p[A] + Z, AB = p[A + 1] + Z, B = p[X + 1] + Y, BA = p[B] + Z, BB = p[B + 1] + Z;
        double r = lerp(w,
                lerp(v, lerp(u, grad(p[AA], x, y, z), grad(p[BA], x - 1, y, z)),
                        lerp(u, grad(p[AB], x, y - 1, z), grad(p[BB], x - 1, y - 1, z))),
                lerp(v, lerp(u, grad(p[AA + 1], x, y, z - 1), grad(p[BA + 1], x - 1, y, z - 1)),
                        lerp(u, grad(p[AB + 1], x, y - 1, z - 1), grad(p[BB + 1], x - 1, y - 1, z - 1))));
        return r * 0.97; // normalize to ~[-1,1]
    }

    public double noise2(double x, double z) { return noise(x, 0.5, z); }

    /** Fractal sum, amplitude-normalized to ~[-1,1]. */
    public double fbm(double x, double y, double z, int octaves) {
        double sum = 0, amp = 1, f = 1, norm = 0;
        for (int o = 0; o < octaves; o++) {
            sum += amp * noise(x * f, y * f, z * f);
            norm += amp;
            amp *= 0.5;
            f *= 2.0;
        }
        return sum / norm;
    }

    public double fbm2(double x, double z, int octaves) { return fbm(x, 0.5, z, octaves); }

    /** Ridged noise in [0,1]: 1 at ridge crests. */
    public double ridged2(double x, double z, int octaves) {
        double sum = 0, amp = 1, f = 1, norm = 0;
        for (int o = 0; o < octaves; o++) {
            double n = 1.0 - Math.abs(noise(x * f, 0.5, z * f));
            sum += amp * n * n;
            norm += amp;
            amp *= 0.5;
            f *= 2.0;
        }
        return sum / norm;
    }
}
