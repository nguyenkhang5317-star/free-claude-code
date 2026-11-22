package mc.client;

import mc.content.Registries;

import javax.sound.sampled.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Audio (Section 2.8 IAudioBus). All sounds are synthesised procedurally from data/sounds.json parameters — no
 * sampled or copied audio. A mixer thread streams 22.05 kHz stereo with distance attenuation and panning.
 */
public final class Audio {
    private Audio() { }

    public interface IAudioBus {
        void play(String id, double x, double y, double z, float volume, float pitch);

        void playUi(String id, float volume, float pitch);

        void listener(double x, double y, double z, float yaw);

        void setVolume(float master);

        void setRain(float level);

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

    public static final int RATE = 22050;
    static final Map<String, float[]> CACHE = new ConcurrentHashMap<>();

    /** Synthesise (and cache) the mono PCM of a sound id. Returns null if the id is unknown. */
    public static float[] pcm(String id) {
        float[] c = CACHE.get(id);
        if (c != null) return c;
        Map<String, Object> d = Registries.SOUNDS.get(id);
        if (d == null) return null;
        String synth = mc.util.Json.str(d, "synth", "noise");
        double freq = mc.util.Json.num(d, "freq", 1000), dur = mc.util.Json.num(d, "duration", 0.2), vol = mc.util.Json.num(d, "volume", 1);
        float[] out = synth(synth, freq, dur, vol, id.hashCode());
        CACHE.put(id, out);
        return out;
    }

    static float[] synth(String kind, double freq, double dur, double vol, long seed) {
        int n = Math.max(1, (int) (dur * RATE));
        float[] o = new float[n];
        Random r = new Random(seed);
        double lp = 0, lp2 = 0, phase = 0;
        double alpha = Math.min(1, 2 * Math.PI * freq / RATE);
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE, f = i / (double) n;
            double env, s;
            switch (kind) {
                case "noise": { // filtered noise burst (footsteps, digging, placing)
                    double w = r.nextDouble() * 2 - 1;
                    lp += (w - lp) * alpha;
                    lp2 += (lp - lp2) * alpha;
                    env = Math.exp(-f * 5) * Math.min(1, t * 400);
                    s = (lp2 * 2.5 + lp * 0.5) * env;
                    break;
                }
                case "hiss": {
                    double w = r.nextDouble() * 2 - 1;
                    lp += (w - lp) * 0.6;
                    env = Math.min(1, f * 4) * Math.min(1, (1 - f) * 3);
                    s = (w - lp) * env * 0.8;
                    break;
                }
                case "boom": {
                    double w = r.nextDouble() * 2 - 1;
                    lp += (w - lp) * 0.02;
                    lp2 += (w - lp2) * 0.2;
                    env = Math.exp(-f * 4);
                    s = (lp * 6 + lp2 * 0.6 * Math.exp(-f * 12) + Math.sin(2 * Math.PI * freq * t * (1 - f * 0.5)) * 0.5) * env;
                    break;
                }
                case "pop": {
                    double fr = freq * (1 + (1 - f) * 0.6);
                    phase += 2 * Math.PI * fr / RATE;
                    env = Math.exp(-f * 6);
                    s = Math.sin(phase) * env;
                    break;
                }
                case "chime": {
                    phase += 2 * Math.PI * freq / RATE;
                    env = Math.exp(-f * 4);
                    s = (Math.sin(phase) + 0.4 * Math.sin(phase * 2.01) + 0.2 * Math.sin(phase * 3.02)) * env * 0.6;
                    break;
                }
                case "twang": {
                    double fr = freq * (1 + Math.exp(-f * 10) * 0.5);
                    phase += 2 * Math.PI * fr / RATE;
                    env = Math.exp(-f * 5);
                    s = (Math.sin(phase) + 0.5 * Math.signum(Math.sin(phase * 2))) * env * 0.5;
                    break;
                }
                case "creak": {
                    double fr = freq * (1 + 0.3 * Math.sin(t * 25));
                    phase += 2 * Math.PI * fr / RATE;
                    env = Math.sin(Math.PI * f);
                    s = ((phase / Math.PI) % 2 - 1) * env * 0.4 + (r.nextDouble() - 0.5) * 0.1 * env;
                    break;
                }
                case "crunch": {
                    double w = r.nextDouble() * 2 - 1;
                    lp += (w - lp) * alpha;
                    double grains = Math.max(0, Math.sin(t * 2 * Math.PI * 28)) * Math.max(0, Math.sin(t * 2 * Math.PI * 7));
                    env = Math.sin(Math.PI * f);
                    s = lp * grains * env * 3;
                    break;
                }
                default: { // vocal-like creature sounds: oscillator + formant-ish shaping + vibrato
                    double vib = 1, shape = 0.5;
                    double fr = freq;
                    switch (kind) {
                        case "moo": fr = freq * (1 + 0.15 * Math.sin(f * Math.PI)); shape = 0.8; break;
                        case "oink": fr = freq * (1 + 0.4 * Math.sin(f * Math.PI * 3)); shape = 0.6; break;
                        case "baa": fr = freq * (1 + 0.04 * Math.sin(t * 2 * Math.PI * 22)); shape = 0.7; break;
                        case "cluck": fr = freq * (1.2 - f * 0.4); shape = 0.4; break;
                        case "groan": fr = freq * (1 - 0.2 * f + 0.05 * Math.sin(t * 30)); shape = 0.9; break;
                        case "rattle": fr = freq; shape = 0.2; break;
                        case "oof": fr = freq * (1.2 - f * 0.5); shape = 0.7; break;
                        default: break;
                    }
                    phase += 2 * Math.PI * fr * vib / RATE;
                    double saw = (phase / Math.PI) % 2 - 1;
                    double sq = Math.signum(Math.sin(phase));
                    double raw = saw * shape + sq * (1 - shape) * 0.5;
                    lp += (raw - lp) * 0.25;
                    env = Math.min(1, f * 12) * Math.pow(1 - f, kind.equals("cluck") || kind.equals("rattle") ? 3 : 0.8);
                    if (kind.equals("rattle")) env *= Math.max(0, Math.sin(t * 2 * Math.PI * 18));
                    if (kind.equals("cluck")) env *= f < 0.3 || (f > 0.45 && f < 0.7) ? 1 : 0.1;
                    s = lp * env;
                }
            }
            o[i] = (float) (s * vol);
        }
        // normalise peaks to ~0.8 * volume
        float peak = 1e-6f;
        for (float v : o) peak = Math.max(peak, Math.abs(v));
        float k = (float) (0.8 * vol / peak);
        for (int i = 0; i < n; i++) o[i] *= k;
        return o;
    }

    /** Streaming mixer using javax.sound.sampled. */
    public static final class Synth implements IAudioBus {
        static final class Voice {
            float[] pcm;
            double pos, rate;
            float gl, gr;
            double x, y, z;
            boolean positional;
            float vol;
        }

        final ConcurrentLinkedQueue<Voice> incoming = new ConcurrentLinkedQueue<>();
        final List<Voice> voices = new ArrayList<>();
        volatile double lx, ly, lz;
        volatile float lyaw, master = 1, rain;
        volatile boolean running = true;
        SourceDataLine line;
        Thread thread;

        public static IAudioBus open() {
            try {
                Synth s = new Synth();
                AudioFormat fmt = new AudioFormat(RATE, 16, 2, true, false);
                s.line = AudioSystem.getSourceDataLine(fmt);
                s.line.open(fmt, 4096 * 2);
                s.line.start();
                s.thread = new Thread(s::run, "audio");
                s.thread.setDaemon(true);
                s.thread.start();
                return s;
            } catch (Throwable e) {
                System.err.println("[audio] unavailable, continuing muted: " + e);
                return new Null();
            }
        }

        public void play(String id, double x, double y, double z, float volume, float pitch) {
            float[] p = pcm(id);
            if (p == null) return;
            Voice v = new Voice();
            v.pcm = p; v.rate = Math.max(0.25, Math.min(4, pitch)); v.x = x; v.y = y; v.z = z; v.positional = true; v.vol = volume;
            incoming.add(v);
        }

        public void playUi(String id, float volume, float pitch) {
            float[] p = pcm(id);
            if (p == null) return;
            Voice v = new Voice();
            v.pcm = p; v.rate = pitch; v.vol = volume; v.gl = v.gr = volume;
            incoming.add(v);
        }

        public void listener(double x, double y, double z, float yaw) { lx = x; ly = y; lz = z; lyaw = yaw; }

        public void setVolume(float m) { master = m; }

        public void setRain(float l) { rain = l; }

        void spatial(Voice v) {
            if (!v.positional) return;
            double dx = v.x - lx, dy = v.y - ly, dz = v.z - lz;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double range = 16 * Math.max(1, v.vol);
            float att = (float) Math.max(0, 1 - d / range) * Math.min(1, v.vol);
            double yaw = Math.toRadians(lyaw);
            // listener right vector = (-cos yaw, 0, -sin yaw)
            double pan = d < 0.01 ? 0 : (dx * -Math.cos(yaw) + dz * -Math.sin(yaw)) / d;
            v.gl = (float) (att * Math.sqrt(0.5 * (1 - pan)));
            v.gr = (float) (att * Math.sqrt(0.5 * (1 + pan)));
        }

        void run() {
            int frames = 512;
            byte[] buf = new byte[frames * 4];
            float[] mixL = new float[frames], mixR = new float[frames];
            Random rnd = new Random();
            double rl = 0;
            while (running) {
                Voice nv;
                while ((nv = incoming.poll()) != null) { if (voices.size() < 48) voices.add(nv); }
                Arrays.fill(mixL, 0);
                Arrays.fill(mixR, 0);
                for (int vi = voices.size() - 1; vi >= 0; vi--) {
                    Voice v = voices.get(vi);
                    spatial(v);
                    for (int i = 0; i < frames; i++) {
                        int p = (int) v.pos;
                        if (p >= v.pcm.length) break;
                        float s = v.pcm[p];
                        mixL[i] += s * v.gl;
                        mixR[i] += s * v.gr;
                        v.pos += v.rate;
                    }
                    if (v.pos >= v.pcm.length) voices.remove(vi);
                }
                float rv = rain * 0.12f;
                for (int i = 0; i < frames; i++) {
                    if (rv > 0) { rl += ((rnd.nextDouble() * 2 - 1) - rl) * 0.3; mixL[i] += rl * rv; mixR[i] += rl * rv; }
                    int L = (int) (Math.tanh(mixL[i] * master) * 30000), R = (int) (Math.tanh(mixR[i] * master) * 30000);
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
