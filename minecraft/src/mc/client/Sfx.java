package mc.client;

import java.util.Random;

/**
 * Procedural sound design (no sampled or copied audio). Every sound id is rendered from physically-motivated models:
 * granular impacts for blocks (stone crumble, gravel crunch, grass rustle, sand hiss, wood knock modes, glass shatter,
 * metal ring...), formant-filtered glottal sources for creature voices (moo, oink, baa, cluck, groan...), Karplus-Strong
 * strings for bows, filtered-noise weather and explosions. Each id is rendered in several random variants, the way
 * vanilla ships several recordings per event.
 */
final class Sfx {
    private Sfx() { }

    static final int RATE = Audio.RATE;

    // ------------------------------------------------------------------ DSP building blocks
    /** RBJ biquad. */
    static final class BQ {
        double b0, b1, b2, a1, a2, x1, x2, y1, y2;

        static BQ bp(double f, double q) { // constant 0 dB peak band-pass
            BQ b = new BQ();
            b.setBp(f, q);
            return b;
        }

        void setBp(double f, double q) {
            double w = 2 * Math.PI * Math.min(f, RATE * 0.45) / RATE, al = Math.sin(w) / (2 * q), a0 = 1 + al;
            b0 = al / a0; b1 = 0; b2 = -al / a0; a1 = -2 * Math.cos(w) / a0; a2 = (1 - al) / a0;
        }

        static BQ lp(double f, double q) {
            BQ b = new BQ();
            b.setLp(f, q);
            return b;
        }

        void setLp(double f, double q) {
            double w = 2 * Math.PI * Math.min(f, RATE * 0.45) / RATE, al = Math.sin(w) / (2 * q), c = Math.cos(w), a0 = 1 + al;
            b0 = (1 - c) / 2 / a0; b1 = (1 - c) / a0; b2 = b0; a1 = -2 * c / a0; a2 = (1 - al) / a0;
        }

        static BQ hp(double f, double q) {
            BQ b = new BQ();
            double w = 2 * Math.PI * Math.min(f, RATE * 0.45) / RATE, al = Math.sin(w) / (2 * q), c = Math.cos(w), a0 = 1 + al;
            b.b0 = (1 + c) / 2 / a0; b.b1 = -(1 + c) / a0; b.b2 = b.b0; b.a1 = -2 * c / a0; b.a2 = (1 - al) / a0;
            return b;
        }

        double f(double x) {
            double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1; x1 = x; y2 = y1; y1 = y;
            return y;
        }
    }

    static int n(double sec) { return Math.max(1, (int) (sec * RATE)); }

    static double nz(Random r) { return r.nextDouble() * 2 - 1; }

    /** Band-passed noise burst with exponential decay. */
    static void grain(float[] o, double t0, double len, double freq, double q, double amp, Random r) {
        int s = (int) (t0 * RATE), m = n(len);
        BQ f = BQ.bp(freq, q);
        double att = Math.max(1, 0.0015 * RATE);
        for (int i = 0; i < m && s + i < o.length; i++) {
            if (s + i < 0) continue;
            double env = Math.min(1, i / att) * Math.exp(-5.0 * i / m);
            o[s + i] += (float) (f.f(nz(r)) * env * amp);
        }
    }

    /** Sum of exponentially damped sinusoidal modes (knocks, rings, pings). */
    static void modes(float[] o, double t0, double f0, double[] ratios, double[] amps, double decay, double amp, Random r) {
        int s = (int) (t0 * RATE);
        int m = n(decay * 6);
        for (int k = 0; k < ratios.length; k++) {
            double fr = f0 * ratios[k] * (1 + nz(r) * 0.01), ph = r.nextDouble() * 6.283, dk = decay / (1 + k * 0.6);
            if (fr > RATE * 0.45) continue;
            double w = 2 * Math.PI * fr / RATE;
            for (int i = 0; i < m && s + i < o.length; i++) {
                if (s + i < 0) continue;
                double e = Math.exp(-i / (dk * RATE));
                if (e < 1e-4) break;
                o[s + i] += (float) (Math.sin(ph + w * i) * e * amps[k] * amp * Math.min(1, i / 20.0));
            }
        }
    }

    /** Low thud: falling sine + low-passed noise. */
    static void thump(float[] o, double t0, double f, double len, double amp, Random r) {
        int s = (int) (t0 * RATE), m = n(len);
        BQ lp = BQ.lp(f * 3, 0.7);
        double ph = 0;
        for (int i = 0; i < m && s + i < o.length; i++) {
            if (s + i < 0) continue;
            double x = i / (double) m, fr = f * (1.4 - 0.6 * x);
            ph += 2 * Math.PI * fr / RATE;
            double env = Math.min(1, i / 30.0) * Math.exp(-6 * x);
            o[s + i] += (float) ((Math.sin(ph) * 0.7 + lp.f(nz(r)) * 1.6) * env * amp);
        }
    }

    /** Filtered noise with a free envelope function. */
    interface Env { double at(double x); }

    static void noise(float[] o, double t0, double len, BQ f1, BQ f2, Env env, double amp, Random r) {
        int s = (int) (t0 * RATE), m = n(len);
        for (int i = 0; i < m && s + i < o.length; i++) {
            if (s + i < 0) continue;
            double v = nz(r);
            if (f1 != null) v = f1.f(v);
            if (f2 != null) v = f2.f(v);
            o[s + i] += (float) (v * env.at(i / (double) m) * amp);
        }
    }

    static float[] normalize(float[] o, double peak) {
        // DC block + short fade in/out, then peak normalise
        double xp = 0, yp = 0;
        for (int i = 0; i < o.length; i++) { double y = o[i] - xp + 0.995 * yp; xp = o[i]; yp = y; o[i] = (float) y; }
        int fade = Math.min(o.length / 4, 64);
        for (int i = 0; i < fade; i++) { o[o.length - 1 - i] *= i / (float) fade; }
        float p = 1e-9f;
        for (float v : o) p = Math.max(p, Math.abs(v));
        float k = (float) (peak / p);
        for (int i = 0; i < o.length; i++) o[i] *= k;
        return o;
    }

    // ------------------------------------------------------------------ entry point
    static float[] render(String id, String synth, double freq, double dur, double vol, int variant) {
        Random r = new Random(id.hashCode() * 7919L + variant * 104729L + 17);
        double jit = 1 + (r.nextDouble() - 0.5) * 0.12; // per-variant timbre jitter
        String[] p = id.split("\\.");
        float[] o;
        double peak = 0.85;
        boolean own = false;
        boolean special = p.length > 1 && (p[1].equals("chest") || p[1].equals("fire") || p[1].equals("furnace") || p[1].equals("portal"));
        if (p.length == 3 && p[0].equals("block") && isEvent(p[2]) && !special) {
            o = block(material(p[1]), p[2], freq * jit, r);
            peak = 0.9;
            own = true;
        } else if (p.length == 3 && p[0].equals("entity") && isVoiceMob(p[1]) && (p[2].equals("ambient") || p[2].equals("hurt") || p[2].equals("death") || p[2].equals("step"))) {
            o = creature(p[1], p[2], jit, r);
            own = true;
        } else {
            o = special(id, synth, freq, dur, jit, r);
            if (id.equals("weather.rain")) peak = 0.4;
            if (id.equals("ambient.cave")) peak = 0.7;
            if (id.equals("block.fire.ambient")) peak = 0.55;
        }
        return normalize(o, own ? peak : Math.min(1.0, peak * Math.max(0.35, Math.min(1, vol + 0.3))));
    }

    static boolean isEvent(String e) { return e.equals("break") || e.equals("place") || e.equals("step") || e.equals("hit") || e.equals("fall"); }

    static boolean isVoiceMob(String m) {
        switch (m) {
            case "cow": case "pig": case "sheep": case "chicken": case "zombie": case "skeleton": case "creeper": case "spider": case "player": return true;
            default: return false;
        }
    }

    // ------------------------------------------------------------------ blocks
    enum Mat { STONE, GRAVEL, GRASS, SAND, SNOW, WOOD, WOOL, GLASS, METAL, NETHERRACK, SOUL, NYLIUM, WART, HEAVY }

    static Mat material(String fam) {
        switch (fam) {
            case "stone": case "basalt": case "blackstone": case "nether_bricks": return Mat.STONE;
            case "gravel": case "soul_soil": return Mat.GRAVEL;
            case "grass": return Mat.GRASS;
            case "sand": return Mat.SAND;
            case "snow": return Mat.SNOW;
            case "wood": case "ladder": case "nether_wood": return Mat.WOOD;
            case "wool": case "shroomlight": return Mat.WOOL;
            case "glass": return Mat.GLASS;
            case "metal": case "netherite_block": return Mat.METAL;
            case "netherrack": return Mat.NETHERRACK;
            case "soul_sand": return Mat.SOUL;
            case "nylium": return Mat.NYLIUM;
            case "nether_wart": return Mat.WART;
            case "ancient_debris": return Mat.HEAVY;
            default: return Mat.STONE;
        }
    }

    static float[] block(Mat m, String ev, double freq, Random r) {
        boolean brk = ev.equals("break"), place = ev.equals("place"), step = ev.equals("step"), hit = ev.equals("hit"), fall = ev.equals("fall");
        double len = brk ? 0.5 : place ? 0.26 : fall ? 0.3 : step ? 0.2 : 0.12;
        if (m == Mat.GLASS && brk) len = 0.75;
        if (m == Mat.METAL) len += 0.25;
        float[] o = new float[n(len)];
        double heavy = brk ? 1.0 : place || fall ? 0.8 : 0.5;
        switch (m) {
            case STONE: case NETHERRACK: case HEAVY: {
                double c = m == Mat.NETHERRACK ? 900 : m == Mat.HEAVY ? 700 : 1500;
                c *= freq / (m == Mat.STONE ? 1800 : freq);
                if (!step && !hit) thump(o, 0, m == Mat.HEAVY ? 70 : 110, 0.12, 0.5 * heavy, r);
                grain(o, 0, 0.04, c * 1.3, 1.6, 1.0, r); // the main tap / crack
                int g = brk ? 26 : place || fall ? 8 : step ? 4 : 2;
                for (int i = 0; i < g; i++) {
                    double t = brk ? Math.pow(r.nextDouble(), 1.8) * 0.4 : r.nextDouble() * len * 0.45;
                    grain(o, t, 0.015 + r.nextDouble() * 0.03, c * (0.6 + r.nextDouble() * 1.3), 1.4, (0.25 + r.nextDouble() * 0.5) * (1 - t / len), r);
                }
                if (m == Mat.NETHERRACK) noise(o, 0, len, BQ.bp(c * 0.8, 0.8), null, x -> Math.exp(-6 * x) * Math.min(1, x * 40), 0.4, r);
                if (m == Mat.HEAVY) modes(o, 0.005, 420, new double[]{1, 2.7, 4.1}, new double[]{0.4, 0.25, 0.15}, 0.06, 0.6, r);
                break;
            }
            case GRAVEL: {
                if (!step && !hit) thump(o, 0, 90, 0.1, 0.35 * heavy, r);
                int g = brk ? 70 : place || fall ? 30 : step ? 18 : 10;
                double span = brk ? 0.35 : len * 0.7;
                for (int i = 0; i < g; i++) {
                    double t = Math.pow(r.nextDouble(), 1.4) * span;
                    grain(o, t, 0.006 + r.nextDouble() * 0.014, 600 + r.nextDouble() * 1400, 1.1, (0.3 + r.nextDouble() * 0.7) * (1 - t / len * 0.8), r);
                }
                break;
            }
            case GRASS: case NYLIUM: {
                // leafy rustle: high-passed noise made of many tiny clicks with a soft swell
                double span = brk ? 0.4 : place ? 0.2 : step ? 0.16 : 0.1;
                BQ hp = BQ.hp(1800, 0.7), bp = BQ.bp(4200, 0.6);
                noise(o, 0, span, hp, bp, x -> Math.sin(Math.PI * Math.min(1, x * 1.15)) * (0.6 + 0.4 * Math.sin(x * 37)), 0.5, r);
                int g = brk ? 60 : place ? 26 : step ? 14 : 8;
                for (int i = 0; i < g; i++) grain(o, r.nextDouble() * span, 0.004 + r.nextDouble() * 0.008, 2500 + r.nextDouble() * 4500, 1.5, 0.3 + r.nextDouble() * 0.5, r);
                if (!step && !hit) thump(o, 0, 160, 0.06, 0.2, r);
                if (m == Mat.NYLIUM) for (int i = 0; i < 4; i++) grain(o, r.nextDouble() * span * 0.5, 0.02, 1100 + r.nextDouble() * 600, 1.3, 0.6, r);
                break;
            }
            case SAND: case SOUL: {
                double span = brk ? 0.42 : place ? 0.22 : step ? 0.18 : 0.1;
                double c = m == Mat.SOUL ? 1600 : 3800;
                noise(o, 0, span, BQ.bp(c, 0.5), BQ.lp(c * 2, 0.7), x -> Math.pow(Math.sin(Math.PI * x), 1.5), 1.0, r);
                int g = brk ? 30 : 10;
                for (int i = 0; i < g; i++) grain(o, r.nextDouble() * span * 0.8, 0.005, c * (0.7 + r.nextDouble()), 1.2, 0.25, r);
                if (m == Mat.SOUL) noise(o, 0, span * 1.4, BQ.bp(420, 1.2), null, x -> Math.sin(Math.PI * x) * 0.8, 0.35, r);
                break;
            }
            case SNOW: {
                double span = brk ? 0.36 : place ? 0.2 : step ? 0.17 : 0.1;
                int g = brk ? 50 : place ? 22 : step ? 14 : 8;
                for (int i = 0; i < g; i++) {
                    double t = r.nextDouble() * span;
                    grain(o, t, 0.008 + r.nextDouble() * 0.01, 900 + r.nextDouble() * 900, 2.5, 0.4 + r.nextDouble() * 0.6, r);
                }
                noise(o, 0, span, BQ.lp(2500, 0.7), null, x -> Math.sin(Math.PI * x) * 0.5, 0.4, r);
                break;
            }
            case WOOD: {
                double f0 = 150 + (freq - 400) * 0.25 + r.nextDouble() * 40;
                double[] rat = {1, 2.31, 3.86, 5.42, 7.1}, am = {1, 0.6, 0.35, 0.22, 0.12};
                if (brk) {
                    grain(o, 0, 0.03, 2200, 0.9, 1.2, r); // crack
                    modes(o, 0.002, f0, rat, am, 0.05, 1.0, r);
                    modes(o, 0.06 + r.nextDouble() * 0.05, f0 * 1.15, rat, am, 0.04, 0.6, r);
                    for (int i = 0; i < 18; i++) grain(o, r.nextDouble() * 0.35, 0.006 + r.nextDouble() * 0.01, 2000 + r.nextDouble() * 3500, 1.4, 0.3 + r.nextDouble() * 0.3, r);
                } else {
                    double a = step ? 0.6 : hit ? 0.7 : 1;
                    modes(o, 0, f0 * (step ? 1.1 : 1), rat, am, step ? 0.03 : 0.045, a, r);
                    grain(o, 0, 0.012, 1800 + r.nextDouble() * 800, 1.0, 0.6 * a, r);
                    if (place || fall) thump(o, 0, 95, 0.09, 0.4, r);
                }
                break;
            }
            case WOOL: case WART: {
                double span = brk ? 0.3 : place ? 0.18 : step ? 0.14 : 0.09;
                noise(o, 0, span, BQ.lp(m == Mat.WART ? 900 : 650, 0.8), BQ.lp(1200, 0.7), x -> Math.min(1, x * 12) * Math.exp(-4 * x), 1.0, r);
                if (m == Mat.WART) noise(o, 0, span, BQ.bp(380, 3), null, x -> Math.exp(-5 * x) * Math.min(1, x * 30), 0.5, r);
                if (!step) thump(o, 0, 80, 0.08, 0.3, r);
                break;
            }
            case GLASS: {
                if (brk) {
                    grain(o, 0, 0.05, 3500, 0.5, 1.2, r);
                    noise(o, 0, 0.3, BQ.hp(2500, 0.7), null, x -> Math.exp(-7 * x), 0.5, r);
                    for (int i = 0; i < 48; i++) {
                        double t = Math.pow(r.nextDouble(), 2) * 0.55;
                        modes(o, t, 2200 + r.nextDouble() * 5500, new double[]{1, 2.4}, new double[]{1, 0.4}, 0.012 + r.nextDouble() * 0.03, (0.25 + r.nextDouble() * 0.5) * (1 - t), r);
                    }
                } else {
                    // a "tink" on a hard glassy surface: stone tap + short high ring
                    grain(o, 0, 0.03, 2200, 1.4, 0.7, r);
                    modes(o, 0.001, 2600 + r.nextDouble() * 900, new double[]{1, 2.1, 3.3}, new double[]{1, 0.5, 0.3}, step ? 0.018 : 0.03, 0.6, r);
                }
                break;
            }
            case METAL: {
                double f0 = 520 + r.nextDouble() * 120;
                double[] rat = {1, 2.76, 5.40, 8.93}, am = {1, 0.7, 0.4, 0.2};
                grain(o, 0, 0.015, 3500, 1, 0.7, r);
                modes(o, 0, f0, rat, am, brk ? 0.14 : step ? 0.06 : 0.1, brk ? 1 : 0.8, r);
                if (brk) modes(o, 0.07, f0 * 1.31, rat, am, 0.1, 0.6, r);
                break;
            }
        }
        return o;
    }

    // ------------------------------------------------------------------ creatures (formant synthesis)
    /** Pitch contour / amplitude / formant description of one vocalisation. */
    static final class Voc {
        double dur, attack = 0.03, release = 0.15, breath = 0.08, vibHz = 5, vibDepth = 0.01, jitter = 0.01, rough = 0, roughHz = 30, trem = 0, tremHz = 20;
        double[] f0 = {200, 200};          // breakpoints over time (evenly spaced)
        double[] fa = {700, 1200, 2600};   // formants at start
        double[] fb = null;                // formants at end (morph); null = same
        double[] bw = {90, 110, 160};
        double[] fg = {1, 0.6, 0.3};       // formant gains
    }

    static double contour(double[] c, double x) {
        if (c.length == 1) return c[0];
        double p = x * (c.length - 1);
        int i = Math.min(c.length - 2, (int) p);
        double t = p - i;
        t = t * t * (3 - 2 * t);
        return c[i] + (c[i + 1] - c[i]) * t;
    }

    static void vocalise(float[] o, double t0, Voc v, double amp, Random r) {
        int s = (int) (t0 * RATE), m = n(v.dur);
        BQ[] f = new BQ[3];
        for (int k = 0; k < 3; k++) f[k] = new BQ();
        double ph = 0, jit = 0;
        for (int i = 0; i < m && s + i < o.length; i++) {
            double x = i / (double) m, t = i / (double) RATE;
            if ((i & 31) == 0) {
                for (int k = 0; k < 3; k++) {
                    double fr = v.fb == null ? v.fa[k] : v.fa[k] + (v.fb[k] - v.fa[k]) * x;
                    f[k].setBp(fr, fr / v.bw[k]);
                }
                jit += (nz(r) * v.jitter - jit) * 0.3;
            }
            double f0 = contour(v.f0, x) * (1 + Math.sin(2 * Math.PI * v.vibHz * t) * v.vibDepth + jit);
            ph += f0 / RATE;
            if (ph >= 1) ph -= 1;
            // glottal-ish pulse: asymmetric (Rosenberg) shape, rich in harmonics
            double g = ph < 0.6 ? 0.5 * (1 - Math.cos(Math.PI * ph / 0.6)) : Math.cos(Math.PI * (ph - 0.6) / 0.8);
            double src = (g - 0.4) * 2 + nz(r) * v.breath;
            if (v.rough > 0) src *= 1 - v.rough * (0.5 + 0.5 * Math.sin(2 * Math.PI * v.roughHz * t + nz(r) * 0.6));
            double y = f[0].f(src) * v.fg[0] + f[1].f(src) * v.fg[1] + f[2].f(src) * v.fg[2];
            double env = Math.min(1, x * v.dur / v.attack) * Math.min(1, (1 - x) * v.dur / v.release);
            if (v.trem > 0) env *= 1 - v.trem * (0.5 + 0.5 * Math.sin(2 * Math.PI * v.tremHz * t));
            o[s + i] += (float) (y * env * amp);
        }
    }

    static Voc voc(double dur, double... f0) { Voc v = new Voc(); v.dur = dur; v.f0 = f0; return v; }

    static float[] creature(String mob, String ev, double j, Random r) {
        boolean hurt = ev.equals("hurt"), death = ev.equals("death");
        float[] o;
        switch (mob) {
            case "cow": {
                if (ev.equals("step")) return hoofStep(r, 0.9);
                Voc v = hurt ? voc(0.5, 150 * j, 175 * j, 120 * j) : death ? voc(1.1, 125 * j, 110 * j, 70 * j) : voc(1.15, 92 * j, 118 * j, 108 * j, 84 * j);
                v.fa = new double[]{260, 900, 2300}; v.fb = new double[]{520, 950, 2400}; v.fg = new double[]{1, 0.45, 0.12};
                v.attack = hurt ? 0.02 : 0.16; v.release = 0.3; v.breath = 0.12; v.vibHz = 5.5; v.vibDepth = 0.015; v.jitter = 0.02; v.rough = hurt ? 0.3 : 0.1; v.roughHz = 45;
                o = new float[n(v.dur + 0.05)];
                vocalise(o, 0, v, 1, r);
                return o;
            }
            case "pig": {
                if (ev.equals("step")) return hoofStep(r, 1.2);
                if (hurt || death) {
                    Voc v = hurt ? voc(0.45, 520 * j, 820 * j, 640 * j) : voc(0.9, 700 * j, 760 * j, 480 * j, 300 * j);
                    v.fa = new double[]{900, 1900, 3000}; v.fg = new double[]{1, 0.7, 0.3}; v.attack = 0.015; v.release = 0.15; v.breath = 0.25; v.rough = 0.35; v.roughHz = 70; v.jitter = 0.03;
                    o = new float[n(v.dur + 0.05)];
                    vocalise(o, 0, v, 1, r);
                    return o;
                }
                int k = 1 + r.nextInt(3);
                o = new float[n(0.2 * k + 0.1)];
                for (int i = 0; i < k; i++) {
                    Voc v = voc(0.13 + r.nextDouble() * 0.06, 230 * j, 270 * j, 170 * j);
                    v.fa = new double[]{550, 1400, 2700}; v.fg = new double[]{1, 0.55, 0.2}; v.attack = 0.01; v.release = 0.05; v.breath = 0.45; v.rough = 0.6; v.roughHz = 55; v.jitter = 0.05;
                    vocalise(o, i * 0.19, v, 1 - i * 0.15, r);
                }
                return o;
            }
            case "sheep": {
                if (ev.equals("step")) return hoofStep(r, 1.1);
                Voc v = hurt ? voc(0.38, 340 * j, 400 * j, 330 * j) : death ? voc(0.9, 300 * j, 270 * j, 180 * j) : voc(0.8, 270 * j, 300 * j, 290 * j, 250 * j);
                v.fa = new double[]{620, 1150, 2600}; v.fb = new double[]{780, 1250, 2650}; v.fg = new double[]{1, 0.6, 0.25};
                v.attack = 0.04; v.release = 0.2; v.breath = 0.12; v.trem = 0.75; v.tremHz = hurt ? 26 : 21; v.vibDepth = 0.03; v.vibHz = 21; v.jitter = 0.02;
                o = new float[n(v.dur + 0.05)];
                vocalise(o, 0, v, 1, r);
                return o;
            }
            case "chicken": {
                if (ev.equals("step")) { o = new float[n(0.08)]; grain(o, 0, 0.01, 2600, 1.5, 1, r); grain(o, 0.03, 0.008, 3000, 1.5, 0.5, r); return o; }
                if (hurt || death) {
                    Voc v = hurt ? voc(0.3, 900 * j, 1350 * j, 1000 * j) : voc(0.5, 1100 * j, 900 * j, 600 * j);
                    v.fa = new double[]{1100, 2400, 3600}; v.fg = new double[]{1, 0.7, 0.3}; v.attack = 0.01; v.release = 0.08; v.breath = 0.3; v.rough = 0.5; v.roughHz = 90;
                    o = new float[n(v.dur + 0.05)];
                    vocalise(o, 0, v, 1, r);
                    return o;
                }
                int k = 2 + r.nextInt(3);
                o = new float[n(k * 0.11 + 0.15)];
                for (int i = 0; i < k; i++) {
                    boolean last = i == k - 1 && r.nextBoolean();
                    Voc v = voc(last ? 0.2 : 0.06, (last ? 620 : 520) * j, (last ? 820 : 460) * j);
                    v.fa = new double[]{950, 2100, 3300}; v.fg = new double[]{1, 0.6, 0.25}; v.attack = 0.005; v.release = 0.03; v.breath = 0.2; v.rough = 0.25; v.roughHz = 80;
                    vocalise(o, i * 0.11 + r.nextDouble() * 0.02, v, 1, r);
                }
                return o;
            }
            case "zombie": {
                if (ev.equals("step")) return footStep(r, 0.9);
                Voc v = hurt ? voc(0.45, 120 * j, 140 * j, 95 * j) : death ? voc(1.3, 105 * j, 90 * j, 55 * j) : voc(1.4, 78 * j, 92 * j, 85 * j, 66 * j);
                v.fa = new double[]{480, 980, 2350}; v.fb = new double[]{620, 1080, 2400}; v.fg = new double[]{1, 0.55, 0.2};
                v.attack = hurt ? 0.02 : 0.2; v.release = 0.3; v.breath = 0.35; v.jitter = 0.08; v.rough = 0.55; v.roughHz = 27; v.vibHz = 3;
                o = new float[n(v.dur + 0.05)];
                vocalise(o, 0, v, 1, r);
                return o;
            }
            case "skeleton": {
                if (ev.equals("step")) { o = new float[n(0.12)]; bone(o, 0, r, 0.8); bone(o, 0.025 + r.nextDouble() * 0.02, r, 0.4); return o; }
                double len = hurt ? 0.35 : death ? 1.0 : 0.6;
                o = new float[n(len + 0.1)];
                int k = hurt ? 12 : death ? 26 : 9;
                double t = 0;
                for (int i = 0; i < k && t < len; i++) {
                    bone(o, t, r, death ? 1 - t / len * 0.7 : 0.6 + r.nextDouble() * 0.4);
                    t += (hurt ? 0.02 : 0.035) + r.nextDouble() * (death ? 0.06 * (1 + t) : 0.05);
                }
                if (death) for (int i = 0; i < 3; i++) thump(o, 0.3 + r.nextDouble() * 0.6, 120, 0.06, 0.4, r);
                return o;
            }
            case "creeper": {
                double len = death ? 0.7 : 0.35;
                o = new float[n(len)];
                noise(o, 0, len, BQ.hp(2200, 0.7), BQ.bp(5000, 0.5), x -> Math.min(1, x * 20) * Math.exp(-3.5 * x), 0.8, r);
                for (int i = 0; i < (death ? 40 : 18); i++) grain(o, r.nextDouble() * len * 0.7, 0.006, 1500 + r.nextDouble() * 3500, 1.2, 0.5, r);
                return o;
            }
            case "spider": {
                if (ev.equals("step")) { o = new float[n(0.1)]; for (int i = 0; i < 3; i++) grain(o, i * 0.025 + r.nextDouble() * 0.01, 0.006, 3200 + r.nextDouble() * 1500, 2, 0.6, r); return o; }
                double len = hurt ? 0.35 : death ? 0.9 : 0.75;
                o = new float[n(len)];
                double hz = 18 + r.nextDouble() * 10;
                noise(o, 0, len, BQ.bp(2800 * j, 1.2), BQ.lp(6000, 0.7),
                        x -> Math.min(1, x * 15) * Math.min(1, (1 - x) * 5) * (0.35 + 0.65 * Math.abs(Math.sin(Math.PI * hz * x * len))), 1, r);
                for (int i = 0; i < (int) (len * 30); i++) grain(o, r.nextDouble() * len, 0.004, 4000 + r.nextDouble() * 2000, 2, 0.35, r);
                return o;
            }
            case "player": {
                if (ev.equals("step")) return footStep(r, 1);
                Voc v = death ? voc(0.42, 170 * j, 120 * j) : voc(0.2, 175 * j, 135 * j);
                v.fa = new double[]{620, 1150, 2500}; v.fg = new double[]{1, 0.6, 0.25}; v.attack = 0.008; v.release = death ? 0.2 : 0.09; v.breath = 0.3; v.rough = 0.25; v.roughHz = 40;
                o = new float[n(v.dur + 0.06)];
                thump(o, 0, 90, 0.08, 0.6, r);
                vocalise(o, 0.004, v, 1, r);
                return o;
            }
            default: return special("entity." + mob + "." + ev, "noise", 900, 0.2, j, r);
        }
    }

    static float[] hoofStep(Random r, double pitch) {
        float[] o = new float[n(0.16)];
        thump(o, 0, 110 * pitch, 0.06, 0.6, r);
        for (int i = 0; i < 10; i++) grain(o, r.nextDouble() * 0.09, 0.006, 1500 + r.nextDouble() * 2500, 1.2, 0.35, r);
        return o;
    }

    static float[] footStep(Random r, double pitch) {
        float[] o = new float[n(0.16)];
        thump(o, 0, 100 * pitch, 0.07, 0.5, r);
        for (int i = 0; i < 14; i++) grain(o, Math.pow(r.nextDouble(), 1.5) * 0.1, 0.007, 700 + r.nextDouble() * 1300, 1.1, 0.5, r);
        return o;
    }

    static void bone(float[] o, double t, Random r, double a) {
        double f = 900 + r.nextDouble() * 700;
        modes(o, t, f, new double[]{1, 2.2, 3.7}, new double[]{1, 0.5, 0.25}, 0.008 + r.nextDouble() * 0.008, a, r);
        grain(o, t, 0.006, f * 2.5, 1.5, a * 0.5, r);
    }

    // ------------------------------------------------------------------ everything else
    static float[] special(String id, String synth, double freq, double dur, double j, Random r) {
        float[] o;
        switch (id) {
            case "entity.generic.explode": {
                o = new float[n(2.2)];
                noise(o, 0, 0.06, BQ.hp(400, 0.7), null, x -> 1 - x, 1.4, r);                     // crack
                BQ lp = BQ.lp(1400, 0.8);
                int m = o.length;
                for (int i = 0; i < m; i++) {                                                         // rumble with falling cut-off
                    double x = i / (double) m;
                    if ((i & 63) == 0) lp.setLp(1400 * Math.exp(-x * 3.2) + 90, 0.9);
                    o[i] += (float) (lp.f(nz(r)) * Math.exp(-x * 3.2) * Math.min(1, i / 200.0) * 2.4);
                }
                double ph = 0;
                for (int i = 0; i < n(1.2); i++) { double x = i / (double) n(1.2); ph += 2 * Math.PI * (55 - 25 * x) * j / RATE; o[i] += (float) (Math.sin(ph) * Math.exp(-x * 4) * 0.9); }
                for (int i = 0; i < 60; i++) grain(o, Math.pow(r.nextDouble(), 2) * 0.9, 0.01 + r.nextDouble() * 0.03, 500 + r.nextDouble() * 2500, 1, 0.3 * r.nextDouble(), r);
                return o;
            }
            case "entity.lightning_bolt.thunder": {
                o = new float[n(3.6)];
                noise(o, 0, 0.12, BQ.hp(800, 0.7), null, x -> Math.exp(-x * 4), 1.2, r);
                BQ lp = BQ.lp(600, 0.8), lp2 = BQ.lp(300, 0.8);
                double swell = 1;
                for (int i = 0; i < o.length; i++) {
                    double x = i / (double) o.length;
                    if (i % 2205 == 0) swell = 0.5 + r.nextDouble() * 0.8;
                    o[i] += (float) (lp2.f(lp.f(nz(r))) * Math.exp(-x * 2.2) * swell * 4 * Math.min(1, i / 500.0));
                }
                for (int i = 0; i < 25; i++) grain(o, r.nextDouble() * 1.2, 0.02 + r.nextDouble() * 0.05, 200 + r.nextDouble() * 800, 0.8, 0.4, r);
                return o;
            }
            case "entity.creeper.primed": case "entity.tnt.primed": {
                double len = id.contains("tnt") ? 1.1 : 1.3;
                o = new float[n(len)];
                noise(o, 0, len, BQ.hp(2500, 0.7), BQ.bp(6000, 0.6), x -> Math.min(1, x * 6) * (0.6 + 0.4 * x) * Math.min(1, (1 - x) * 8), 1, r);
                for (int i = 0; i < 70; i++) grain(o, r.nextDouble() * len, 0.003, 3000 + r.nextDouble() * 4000, 2, 0.4 * r.nextDouble(), r);
                return o;
            }
            case "entity.skeleton.shoot": case "entity.arrow.shoot": {
                o = new float[n(0.45)];
                pluck(o, 0, 190 * j + r.nextDouble() * 20, 0.35, 0.9, r);
                noise(o, 0.01, 0.25, BQ.bp(900, 0.8), null, x -> Math.sin(Math.PI * x) * Math.exp(-2 * x), 0.5, r); // whoosh
                return o;
            }
            case "entity.arrow.hit": {
                o = new float[n(0.18)];
                modes(o, 0, 240 + r.nextDouble() * 60, new double[]{1, 2.4, 4}, new double[]{1, 0.5, 0.2}, 0.03, 1, r);
                grain(o, 0, 0.02, 1500, 1, 0.8, r);
                return o;
            }
            case "entity.generic.eat": {
                o = new float[n(0.2)];
                for (int i = 0; i < 26; i++) grain(o, Math.pow(r.nextDouble(), 1.5) * 0.13, 0.005 + r.nextDouble() * 0.01, 900 + r.nextDouble() * 2400, 1.2, 0.3 + r.nextDouble() * 0.6, r);
                thump(o, 0, 140, 0.05, 0.3, r);
                return o;
            }
            case "entity.player.burp": {
                Voc v = voc(0.42, 95 * j, 110 * j, 80 * j);
                v.fa = new double[]{450, 900, 2300}; v.fg = new double[]{1, 0.5, 0.15}; v.attack = 0.02; v.release = 0.12; v.breath = 0.3; v.rough = 0.8; v.roughHz = 24; v.jitter = 0.1;
                o = new float[n(0.47)];
                vocalise(o, 0, v, 1, r);
                return o;
            }
            case "entity.item.pickup": case "entity.chicken.egg": {
                o = new float[n(0.1)];
                double ph = 0;
                for (int i = 0; i < o.length; i++) {
                    double x = i / (double) o.length;
                    ph += 2 * Math.PI * (380 + 900 * Math.pow(x, 0.5)) * j / RATE;
                    o[i] = (float) (Math.sin(ph) * Math.exp(-x * 6) * Math.min(1, i / 40.0));
                }
                return o;
            }
            case "entity.experience_orb.pickup": {
                o = new float[n(0.35)];
                modes(o, 0, 1320 * j, new double[]{1, 2, 3.01, 4.2}, new double[]{1, 0.4, 0.2, 0.08}, 0.07, 1, r);
                return o;
            }
            case "entity.player.levelup": {
                o = new float[n(1.3)];
                double[] notes = {523.25, 659.25, 783.99, 1046.5};
                for (int k = 0; k < notes.length; k++)
                    modes(o, k * 0.11, notes[k], new double[]{1, 2, 3, 4.1}, new double[]{1, 0.35, 0.15, 0.06}, k == notes.length - 1 ? 0.2 : 0.12, 0.8, r);
                return o;
            }
            case "entity.player.attack.strong": case "entity.player.attack.crit": {
                boolean crit = id.endsWith("crit");
                o = new float[n(0.18)];
                noise(o, 0, 0.09, BQ.bp(crit ? 1600 : 900, 1), null, x -> Math.sin(Math.PI * x), 0.8, r); // swish
                thump(o, 0.03, crit ? 140 : 110, 0.07, 1, r);
                grain(o, 0.03, 0.015, crit ? 3000 : 1800, 1, 0.8, r);
                return o;
            }
            case "entity.player.splash": case "entity.generic.splash": case "item.bucket.fill": case "item.bucket.empty": case "entity.cow.milk": {
                double len = id.contains("player.splash") ? 0.6 : id.contains("bucket") || id.contains("milk") ? 0.45 : 0.4;
                o = new float[n(len)];
                if (id.contains("splash")) noise(o, 0, len, BQ.lp(3000, 0.7), BQ.hp(200, 0.7), x -> Math.min(1, x * 30) * Math.exp(-4 * x), 1, r);
                else noise(o, 0, len, BQ.bp(700, 0.9), null, x -> Math.sin(Math.PI * x) * (0.6 + 0.4 * Math.sin(x * 40)), 0.6, r);
                int k = id.contains("splash") ? 20 : 14;
                for (int i = 0; i < k; i++) bubble(o, r.nextDouble() * len * 0.8, 300 + r.nextDouble() * 900, 0.03 + r.nextDouble() * 0.04, 0.3 + r.nextDouble() * 0.4);
                return o;
            }
            case "entity.player.big_fall": case "entity.player.small_fall": {
                boolean big = id.contains("big");
                o = new float[n(big ? 0.3 : 0.15)];
                thump(o, 0, big ? 65 : 95, big ? 0.22 : 0.1, 1, r);
                for (int i = 0; i < 8; i++) grain(o, r.nextDouble() * 0.05, 0.01, 600 + r.nextDouble() * 900, 1, 0.3, r);
                return o;
            }
            case "entity.sheep.shear": {
                o = new float[n(0.22)];
                for (int k = 0; k < 2; k++) {
                    double t = k * 0.09;
                    modes(o, t, 2900 + r.nextDouble() * 300, new double[]{1, 1.6, 2.7}, new double[]{1, 0.6, 0.3}, 0.012, 0.8, r);
                    noise(o, t, 0.05, BQ.hp(5000, 0.7), null, x -> Math.exp(-5 * x), 0.5, r);
                }
                return o;
            }
            case "entity.item.break": {
                o = new float[n(0.35)];
                grain(o, 0, 0.03, 2600, 0.8, 1, r);
                for (int i = 0; i < 10; i++) modes(o, r.nextDouble() * 0.12, 1800 + r.nextDouble() * 3500, new double[]{1, 2.7}, new double[]{1, 0.4}, 0.03, 0.4, r);
                return o;
            }
            case "block.chest.open": case "block.chest.close": {
                boolean open = id.endsWith("open");
                double len = open ? 0.5 : 0.35;
                o = new float[n(len + 0.1)];
                creak(o, open ? 0.0 : 0.0, open ? 0.42 : 0.22, open ? 110 * j : 90 * j, r);
                if (!open) { modes(o, 0.22, 170, new double[]{1, 2.3, 3.9}, new double[]{1, 0.5, 0.3}, 0.05, 1.2, r); thump(o, 0.22, 90, 0.1, 0.8, r); }
                else modes(o, 0, 260, new double[]{1, 2.4}, new double[]{1, 0.4}, 0.02, 0.6, r);
                return o;
            }
            case "block.furnace.fire_crackle": case "block.fire.ambient": {
                boolean fire = id.contains("ambient");
                double len = fire ? 1.0 : 0.4;
                o = new float[n(len)];
                if (fire) noise(o, 0, len, BQ.lp(500, 0.7), null, x -> Math.sin(Math.PI * x) * (0.7 + 0.3 * Math.sin(x * 13)), 0.6, r);
                int k = fire ? 22 : 9;
                for (int i = 0; i < k; i++) {
                    double t = r.nextDouble() * len * 0.9;
                    grain(o, t, 0.002 + r.nextDouble() * 0.006, 1500 + r.nextDouble() * 4000, 1, 0.3 + r.nextDouble() * 0.7, r);
                }
                return o;
            }
            case "block.fire.extinguish": {
                o = new float[n(0.6)];
                noise(o, 0, 0.6, BQ.hp(2000, 0.7), BQ.bp(4500, 0.6), x -> Math.min(1, x * 25) * Math.exp(-4 * x), 1, r);
                for (int i = 0; i < 8; i++) bubble(o, r.nextDouble() * 0.2, 800 + r.nextDouble() * 800, 0.02, 0.2);
                return o;
            }
            case "item.flintandsteel.use": {
                o = new float[n(0.18)];
                noise(o, 0, 0.12, BQ.bp(5000, 1.2), null, x -> Math.min(1, x * 20) * Math.exp(-6 * x), 1, r);
                modes(o, 0, 3800, new double[]{1, 2.3}, new double[]{1, 0.5}, 0.01, 0.6, r);
                return o;
            }
            case "block.portal.trigger": case "block.portal.travel": {
                double len = id.endsWith("travel") ? 2.6 : 2.0;
                o = new float[n(len)];
                BQ f = BQ.bp(400, 2);
                double[] ph = new double[4], fr = {98, 98.7, 147.2, 196.4};
                for (int i = 0; i < o.length; i++) {
                    double x = i / (double) o.length, t = i / (double) RATE;
                    if ((i & 63) == 0) f.setBp(300 + 900 * (0.5 + 0.5 * Math.sin(2 * Math.PI * 0.7 * t + 1)) * (0.6 + x), 3);
                    double sum = 0;
                    for (int k = 0; k < 4; k++) { ph[k] += fr[k] * (1 + x * 0.3) / RATE; sum += (ph[k] % 1) * 2 - 1; }
                    double env = Math.min(1, x * 3) * Math.min(1, (1 - x) * 4);
                    o[i] = (float) ((f.f(sum) * 1.5 + f.f(nz(r)) * 0.3) * env);
                }
                return o;
            }
            case "ui.button.click": {
                o = new float[n(0.06)];
                noise(o, 0, 0.012, BQ.hp(3000, 0.7), null, x -> 1 - x, 0.8, r);
                modes(o, 0, 1700, new double[]{1, 2.5}, new double[]{1, 0.3}, 0.012, 0.8, r);
                return o;
            }
            case "weather.rain": {
                o = new float[n(1.0)];
                noise(o, 0, 1.0, BQ.lp(4000, 0.7), BQ.hp(300, 0.7), x -> 1, 1, r);
                for (int i = 0; i < 80; i++) grain(o, r.nextDouble(), 0.004, 2000 + r.nextDouble() * 4000, 2, 0.6, r);
                return o;
            }
            case "ambient.cave": {
                // eerie, distant, reverberant: a few kinds chosen by variant seed
                o = new float[n(3.5)];
                int kind = r.nextInt(4);
                if (kind == 0) { // low moaning wind
                    BQ f = BQ.bp(200, 6);
                    for (int i = 0; i < o.length; i++) {
                        double x = i / (double) o.length;
                        if ((i & 63) == 0) f.setBp(160 + 120 * Math.sin(x * 5 + 1) * j, 8);
                        o[i] = (float) (f.f(nz(r)) * Math.sin(Math.PI * x) * 3);
                    }
                } else if (kind == 1) { // distant rumble / falling gravel
                    noise(o, 0, 2.5, BQ.lp(250, 0.7), null, x -> Math.sin(Math.PI * x) * (0.6 + 0.4 * Math.sin(x * 9)), 2, r);
                    for (int i = 0; i < 20; i++) grain(o, 0.5 + r.nextDouble() * 1.5, 0.01, 600 + r.nextDouble() * 800, 1, 0.15, r);
                } else { // tonal "cave" drone with slow bend and echoes
                    double base = (kind == 2 ? 110 : 82) * j, ph = 0, ph2 = 0;
                    for (int i = 0; i < n(2.4); i++) {
                        double x = i / (double) n(2.4);
                        ph += base * (1 - 0.15 * x) / RATE;
                        ph2 += base * 1.5 * (1 - 0.1 * x) / RATE;
                        o[i] = (float) ((Math.sin(2 * Math.PI * ph) + 0.4 * Math.sin(2 * Math.PI * ph2) + 0.2 * nz(r)) * Math.sin(Math.PI * x));
                    }
                }
                // built-in echoes
                int d1 = n(0.23), d2 = n(0.41);
                for (int i = o.length - 1; i >= d2; i--) o[i] += o[i - d1] * 0.45f + o[i - d2] * 0.3f;
                return o;
            }
            default: return legacy(synth, freq, dur, r);
        }
    }

    /** Karplus-Strong plucked string. */
    static void pluck(float[] o, double t0, double f, double len, double amp, Random r) {
        int p = Math.max(2, (int) (RATE / f)), s = (int) (t0 * RATE), m = n(len);
        double[] buf = new double[p];
        for (int i = 0; i < p; i++) buf[i] = nz(r);
        for (int i = 0, k = 0; i < m && s + i < o.length; i++) {
            int k2 = (k + 1) % p;
            double v = buf[k];
            buf[k] = 0.996 * 0.5 * (buf[k] + buf[k2]);
            k = k2;
            o[s + i] += (float) (v * amp * Math.min(1, (m - i) / 400.0));
        }
    }

    /** Rising sine chirp (bubble). */
    static void bubble(float[] o, double t0, double f, double len, double amp) {
        int s = (int) (t0 * RATE), m = n(len);
        double ph = 0;
        for (int i = 0; i < m && s + i < o.length; i++) {
            double x = i / (double) m;
            ph += 2 * Math.PI * f * (1 + x * 1.5) / RATE;
            o[s + i] += (float) (Math.sin(ph) * Math.sin(Math.PI * x) * Math.exp(-3 * x) * amp);
        }
    }

    /** Wood creak: stick-slip friction pulses through wood resonances. */
    static void creak(float[] o, double t0, double len, double f, Random r) {
        int s = (int) (t0 * RATE), m = n(len);
        BQ a = BQ.bp(700, 4), b = BQ.bp(1500, 5), c = BQ.bp(2600, 6);
        double ph = 0;
        for (int i = 0; i < m && s + i < o.length; i++) {
            double x = i / (double) m;
            ph += f * (1 + 0.5 * Math.sin(x * 9) + nz(r) * 0.15) / RATE;
            double pulse = (ph % 1) < 0.08 ? 1 : 0;
            double y = a.f(pulse) + b.f(pulse) * 0.7 + c.f(pulse) * 0.4;
            o[s + i] += (float) (y * Math.sin(Math.PI * x) * 3);
        }
    }

    /** Fallback for ids without a dedicated model (kept compatible with the original parameter set). */
    static float[] legacy(String kind, double freq, double dur, Random r) {
        float[] o = new float[n(dur)];
        switch (kind) {
            case "hiss": noise(o, 0, dur, BQ.hp(freq * 0.6, 0.7), null, x -> Math.min(1, x * 4) * Math.min(1, (1 - x) * 3), 1, r); break;
            case "boom": thump(o, 0, freq, dur, 1, r); noise(o, 0, dur, BQ.lp(400, 0.7), null, x -> Math.exp(-4 * x), 1.5, r); break;
            case "pop": bubble(o, 0, freq, dur, 1); break;
            case "chime": modes(o, 0, freq, new double[]{1, 2.01, 3.02}, new double[]{1, 0.4, 0.2}, dur / 4, 1, r); break;
            case "twang": pluck(o, 0, freq, dur, 1, r); break;
            case "creak": creak(o, 0, dur, freq / 2, r); break;
            case "crunch": for (int i = 0; i < 20; i++) grain(o, r.nextDouble() * dur * 0.8, 0.008, freq * (0.6 + r.nextDouble()), 1.2, 0.6, r); break;
            default: {
                int g = Math.max(3, (int) (dur * 60));
                for (int i = 0; i < g; i++) grain(o, Math.pow(r.nextDouble(), 1.5) * dur * 0.7, 0.01 + r.nextDouble() * 0.02, freq * (0.6 + r.nextDouble() * 0.8), 1.2, 0.4 + r.nextDouble() * 0.6, r);
            }
        }
        return o;
    }
}
