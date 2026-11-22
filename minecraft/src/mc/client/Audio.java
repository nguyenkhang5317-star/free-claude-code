package mc.client;

import mc.content.Registries;

import javax.sound.sampled.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Audio (Section 2.8 IAudioBus). All sounds are synthesised procedurally ({@link Sfx}) from data/sounds.json ids — no
 * sampled or copied audio. Each id has several variants picked at random per play (like vanilla's multiple
 * recordings). The mixer streams 44.1 kHz stereo with vanilla-style linear distance attenuation, equal-power panning,
 * distance air-absorption low-pass, smooth gain ramps, cave reverb, underwater muffling, rain bed and a soft limiter.
 */
public final class Audio {
    private Audio() { }

    public interface IAudioBus {
        void play(String id, double x, double y, double z, float volume, float pitch);

        void playUi(String id, float volume, float pitch);

        void listener(double x, double y, double z, float yaw);

        void setVolume(float master);

        void setRain(float level);

        /** reverb 0..1 (enclosed / cave), underwater 0..1 (muffle). */
        default void setEnvironment(float reverb, float underwater) { }

        void close();
    }

    /** Headless / muted implementation. */
    public static final class Null implements IAudioBus {
        public int played;
        public final List<String> log = new ArrayList<>();

        public void play(String id, double x, double y, double z, float v, float p) { played++; if (log.size() < 1000) log.add(id); }

        public void playUi(String id, float v, float p) { played++; }

        public void listener(double x, double y, double z, float yaw) { }

        public void setVolume(float m) { }

        public void setRain(float l) { }

        public void close() { }
    }

    public static final int RATE = 44100;
    public static final int VARIANTS = 4;
    static final Map<String, float[][]> CACHE = new ConcurrentHashMap<>();

    /** Synthesise (and cache) all variants of a sound id. Returns null if the id is unknown. */
    public static float[][] variants(String id) {
        float[][] c = CACHE.get(id);
        if (c != null) return c;
        Map<String, Object> d = Registries.SOUNDS.get(id);
        if (d == null) return null;
        String synth = mc.util.Json.str(d, "synth", "noise");
        double freq = mc.util.Json.num(d, "freq", 1000), dur = mc.util.Json.num(d, "duration", 0.2), vol = mc.util.Json.num(d, "volume", 1);
        int nv = id.equals("weather.rain") ? 1 : VARIANTS;
        float[][] out = new float[nv][];
        for (int v = 0; v < nv; v++) out[v] = Sfx.render(id, synth, freq, dur, vol, v);
        CACHE.put(id, out);
        return out;
    }

    /** Mono PCM of the first variant of a sound id (used by tests); null if unknown. */
    public static float[] pcm(String id) {
        float[][] v = variants(id);
        return v == null ? null : v[0];
    }

    /** Render every registered sound in the background so the first play never stalls the game. */
    public static void prewarm() {
        Thread t = new Thread(() -> {
            for (String id : new ArrayList<>(Registries.SOUNDS.keySet())) {
                try { variants(id); } catch (Throwable ignored) { }
            }
        }, "audio-prewarm");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    /** Streaming mixer using javax.sound.sampled. */
    public static final class Synth implements IAudioBus {
        static final class Voice {
            float[] pcm;
            double pos, rate;
            float gl, gr, tl, tr;   // current / target gains (ramped per block to avoid zipper noise)
            float lpk = 1;          // one-pole low-pass coefficient (air absorption / occlusion)
            double sl, sr;          // filter state
            double x, y, z;
            boolean positional, fresh = true;
            float vol, loud;
        }

        static final int MAX_VOICES = 64, BLOCK = 256;
        final ConcurrentLinkedQueue<Voice> incoming = new ConcurrentLinkedQueue<>();
        final List<Voice> voices = new ArrayList<>();
        final Random rnd = new Random();
        volatile double lx, ly, lz;
        volatile float lyaw, master = 1, rain, reverb, underwater;
        volatile boolean running = true;
        SourceDataLine line;
        Thread thread;

        public static IAudioBus open() {
            try {
                Synth s = new Synth();
                AudioFormat fmt = new AudioFormat(RATE, 16, 2, true, false);
                s.line = AudioSystem.getSourceDataLine(fmt);
                s.line.open(fmt, BLOCK * 4 * 12);
                s.line.start();
                s.thread = new Thread(s::run, "audio");
                s.thread.setDaemon(true);
                s.thread.setPriority(Thread.MAX_PRIORITY - 1);
                s.thread.start();
                prewarm();
                return s;
            } catch (Throwable e) {
                System.err.println("[audio] unavailable, continuing muted: " + e);
                return new Null();
            }
        }

        float[] pick(String id) {
            float[][] v = variants(id);
            if (v == null) return null;
            return v[rnd.nextInt(v.length)];
        }

        public void play(String id, double x, double y, double z, float volume, float pitch) {
            float[] p = pick(id);
            if (p == null) return;
            Voice v = new Voice();
            // a touch of random pitch so repeated sounds never machine-gun
            double jitter = 1 + (rnd.nextDouble() - 0.5) * 0.04;
            v.pcm = p; v.rate = Math.max(0.25, Math.min(4, pitch * jitter)); v.x = x; v.y = y; v.z = z; v.positional = true; v.vol = volume;
            incoming.add(v);
        }

        public void playUi(String id, float volume, float pitch) {
            float[] p = pick(id);
            if (p == null) return;
            Voice v = new Voice();
            v.pcm = p; v.rate = pitch; v.vol = volume; v.tl = v.tr = volume; v.gl = v.gr = volume;
            incoming.add(v);
        }

        public void listener(double x, double y, double z, float yaw) { lx = x; ly = y; lz = z; lyaw = yaw; }

        public void setVolume(float m) { master = m; }

        public void setRain(float l) { rain = l; }

        @Override public void setEnvironment(float rv, float uw) { reverb = rv; underwater = uw; }

        void spatial(Voice v) {
            if (!v.positional) { v.lpk = 1; return; }
            double dx = v.x - lx, dy = v.y - ly, dz = v.z - lz;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            // vanilla: linear roll-off to 16 blocks (further for loud sounds), gain capped at 1
            double range = 16 * Math.max(1, v.vol);
            float att = (float) Math.max(0, 1 - d / range) * Math.min(1, v.vol);
            double yaw = Math.toRadians(lyaw);
            double side = d < 0.01 ? 0 : (dx * -Math.cos(yaw) + dz * -Math.sin(yaw)) / d;
            double front = d < 0.01 ? 1 : (dx * -Math.sin(yaw) + dz * Math.cos(yaw)) / d;
            double pan = side * Math.min(1, d / 1.5) * 0.85; // very close sounds stay centred
            if (front < 0) att *= (float) (1 + front * 0.12); // slightly duller/quieter behind
            v.tl = (float) (att * Math.sqrt(0.5 * (1 - pan)));
            v.tr = (float) (att * Math.sqrt(0.5 * (1 + pan)));
            double cutoff = 18000 / (1 + d / 10.0) * (front < 0 ? 0.75 : 1);
            if (underwater > 0) cutoff = Math.min(cutoff, 900);
            v.lpk = (float) Math.min(1, 1 - Math.exp(-2 * Math.PI * cutoff / RATE));
            v.loud = att;
            if (v.fresh) { v.gl = v.tl; v.gr = v.tr; v.fresh = false; }
        }

        // --- reverb (Freeverb-style: 4 parallel combs + 2 allpasses per channel)
        static final int[] COMB = {1116, 1188, 1277, 1356}, AP = {556, 441};
        final float[][] combL = new float[4][], combR = new float[4][], apL = new float[2][], apR = new float[2][];
        final int[] ciL = new int[4], ciR = new int[4], aiL = new int[2], aiR = new int[2];
        final float[] dampL = new float[4], dampR = new float[4];

        {
            for (int i = 0; i < 4; i++) { combL[i] = new float[COMB[i]]; combR[i] = new float[COMB[i] + 23]; }
            for (int i = 0; i < 2; i++) { apL[i] = new float[AP[i]]; apR[i] = new float[AP[i] + 23]; }
        }

        float reverbTap(float in, boolean left) {
            float[][] cb = left ? combL : combR, ap = left ? apL : apR;
            int[] ci = left ? ciL : ciR, ai = left ? aiL : aiR;
            float[] dm = left ? dampL : dampR;
            float out = 0;
            for (int i = 0; i < 4; i++) {
                float[] b = cb[i];
                float y = b[ci[i]];
                dm[i] = y * 0.7f + dm[i] * 0.3f;
                b[ci[i]] = in + dm[i] * 0.80f;
                if (++ci[i] >= b.length) ci[i] = 0;
                out += y;
            }
            for (int i = 0; i < 2; i++) {
                float[] b = ap[i];
                float bo = b[ai[i]];
                b[ai[i]] = out + bo * 0.5f;
                out = bo - out * 0.5f;
                if (++ai[i] >= b.length) ai[i] = 0;
            }
            return out * 0.25f;
        }

        void run() {
            int frames = BLOCK;
            byte[] buf = new byte[frames * 4];
            float[] mixL = new float[frames], mixR = new float[frames], send = new float[frames];
            double rl = 0, rr = 0, rl2 = 0, rr2 = 0, uwL = 0, uwR = 0;
            float rainNow = 0, revNow = 0, uwNow = 0;
            while (running) {
                Voice nv;
                while ((nv = incoming.poll()) != null) {
                    if (voices.size() >= MAX_VOICES) {
                        // steal the quietest voice
                        int q = 0;
                        for (int i = 1; i < voices.size(); i++) if (voices.get(i).loud < voices.get(q).loud) q = i;
                        voices.remove(q);
                    }
                    voices.add(nv);
                }
                Arrays.fill(mixL, 0);
                Arrays.fill(mixR, 0);
                Arrays.fill(send, 0);
                for (int vi = voices.size() - 1; vi >= 0; vi--) {
                    Voice v = voices.get(vi);
                    spatial(v);
                    float dl = (v.tl - v.gl) / frames, dr = (v.tr - v.gr) / frames;
                    float[] pcm = v.pcm;
                    int len = pcm.length;
                    double k = v.lpk;
                    for (int i = 0; i < frames; i++) {
                        int p = (int) v.pos;
                        if (p >= len) break;
                        double fr = v.pos - p;
                        float s = (float) (pcm[p] + ((p + 1 < len ? pcm[p + 1] : 0) - pcm[p]) * fr); // linear interpolation
                        v.sl += (s * v.gl - v.sl) * k;
                        v.sr += (s * v.gr - v.sr) * k;
                        mixL[i] += (float) v.sl;
                        mixR[i] += (float) v.sr;
                        send[i] += (float) ((v.sl + v.sr) * 0.5);
                        v.gl += dl;
                        v.gr += dr;
                        v.pos += v.rate;
                    }
                    v.gl = v.tl;
                    v.gr = v.tr;
                    if (v.pos >= len) voices.remove(vi);
                }
                float rainT = rain, revT = reverb, uwT = underwater;
                for (int i = 0; i < frames; i++) {
                    rainNow += (rainT - rainNow) * 0.0005f;
                    revNow += (revT - revNow) * 0.0002f;
                    uwNow += (uwT - uwNow) * 0.001f;
                    // rain bed: decorrelated low-passed noise + sparse drops
                    if (rainNow > 0.001f) {
                        rl += ((rnd.nextDouble() * 2 - 1) - rl) * 0.25; rr += ((rnd.nextDouble() * 2 - 1) - rr) * 0.25;
                        rl2 += (rl - rl2) * 0.5; rr2 += (rr - rr2) * 0.5;
                        float drop = rnd.nextInt(900) == 0 ? (float) (rnd.nextDouble() * 0.6) : 0;
                        mixL[i] += (float) (rl2 * 0.22 + drop) * rainNow;
                        mixR[i] += (float) (rr2 * 0.22 + (rnd.nextInt(900) == 0 ? rnd.nextDouble() * 0.6 : 0)) * rainNow;
                    }
                    // cave / enclosed reverb (always a little, more underground)
                    float wet = 0.06f + revNow * 0.32f;
                    float in = send[i] * wet;
                    mixL[i] += reverbTap(in, true);
                    mixR[i] += reverbTap(in, false);
                    // underwater muffle on the whole mix
                    double outL = mixL[i], outR = mixR[i];
                    if (uwNow > 0.001f) {
                        double kk = 0.06;
                        uwL += (outL - uwL) * kk; uwR += (outR - uwR) * kk;
                        outL = outL + (uwL * 0.9 - outL) * uwNow;
                        outR = outR + (uwR * 0.9 - outR) * uwNow;
                    }
                    int L = (int) (Math.tanh(outL * master * 0.9) * 31000), R = (int) (Math.tanh(outR * master * 0.9) * 31000);
                    buf[i * 4] = (byte) L; buf[i * 4 + 1] = (byte) (L >> 8);
                    buf[i * 4 + 2] = (byte) R; buf[i * 4 + 3] = (byte) (R >> 8);
                }
                line.write(buf, 0, buf.length);
            }
        }

        public void close() {
            running = false;
            try { if (line != null) { line.stop(); line.close(); } } catch (Exception ignored) { }
        }
    }
}
