package mc.client;

import mc.content.Registries;

import javax.sound.sampled.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Audio (Section 2.8 IAudioBus).
 * <p>
 * Two sound sources share one mixer:
 * <ul>
 *   <li><b>Original Minecraft sounds</b> ({@link SoundPack}): when the player has Minecraft Java installed (or drops a
 *   resource pack into {@code resourcepacks/}), every event id is looked up in that pack's {@code sounds.json} and the
 *   .ogg files are decoded at runtime. Nothing is bundled. Playback then follows vanilla's OpenAL setup exactly: linear
 *   roll-off to {@code attenuation_distance × max(1, volume)}, gain {@code min(1, volume) × variant volume}, pitch clamped to
 *   0.5–2.0, no filtering or reverb; music and ambient loops are streamed.</li>
 *   <li><b>Built-in sounds</b> ({@link Sfx}): procedural synthesis for every registered id (original work), used for
 *   anything the pack lacks or when no pack is found. These get a little reverb/air-absorption to sit better.</li>
 * </ul>
 */
public final class Audio {
    private Audio() { }

    /** A looping, fadeable sound (underwater / biome ambience). */
    public interface Loop {
        void setVolume(float v);

        void stop();

        boolean alive();
    }

    public interface IAudioBus {
        void play(String id, double x, double y, double z, float volume, float pitch);

        void playUi(String id, float volume, float pitch);

        void listener(double x, double y, double z, float yaw);

        void setVolume(float master);

        void setRain(float level);

        /** reverb 0..1 (enclosed / cave), underwater 0..1 (muffle). Built-in sounds only. */
        default void setEnvironment(float reverb, float underwater) { }

        /** True when original sounds from a resource pack / Minecraft installation are active. */
        default boolean usingPack() { return false; }

        /** True when the active pack defines this event (used for pack-only ambience and music). */
        default boolean packHas(String id) { return false; }

        /** Human readable description of the sound source. */
        default String source() { return "built-in"; }

        /** Re-scan resource packs / Minecraft installation; enable=false forces built-in sounds. */
        default void configure(boolean enablePack) { }

        default Loop loop(String id, float volume) { return null; }

        default boolean playMusic(String id) { return false; }

        default boolean musicPlaying() { return false; }

        default void stopMusic() { }

        default void setMusicVolume(float v) { }

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

    /** Synthesise (and cache) all variants of a built-in sound id. Returns null if the id is unknown. */
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

    /** Mono PCM of the first built-in variant of a sound id (used by tests); null if unknown. */
    public static float[] pcm(String id) {
        float[][] v = variants(id);
        return v == null ? null : v[0];
    }

    /** Streaming mixer using javax.sound.sampled. */
    public static final class Synth implements IAudioBus {
        /** One-shot voice (mono, optionally positional). */
        static final class Voice {
            float[] pf;             // built-in PCM
            short[] ps;             // pack PCM
            int len;
            double pos, rate;
            float gl, gr, tl, tr;   // current / target gains (ramped per block to avoid zipper noise)
            float lpk = 1;          // one-pole low-pass coefficient (built-in only)
            double sl, sr;
            double x, y, z;
            boolean positional, fresh = true, vanilla;
            float vol, range = 16, loud;
        }

        /** Streamed voice: music or ambient loop, non-positional, stereo or mono. */
        final class Stream implements Loop {
            final SoundPack.Variant variant;
            OggDecoder dec;
            final boolean music, looping;
            float[] buf = new float[0];
            int ch = 1, frames;     // decoded frames in buf
            double pos, step;
            volatile float target;
            float gain;
            volatile boolean stopped;
            boolean ended;

            Stream(SoundPack.Variant v, OggDecoder d, boolean music, boolean looping, float vol) {
                variant = v; dec = d; this.music = music; this.looping = looping; target = vol; gain = music ? vol : 0;
                ch = d.channels;
                step = d.rate / (double) RATE;
            }

            public void setVolume(float v) { target = v; }

            public void stop() { stopped = true; }

            public boolean alive() { return !ended; }

            /** Ensure at least idx+2 frames are buffered; returns false at end of data. */
            boolean fill(int need) {
                if (need < frames) return true;
                int keep = Math.max(0, frames - (int) pos);
                int start = (int) pos;
                float[] nb = buf.length >= (keep + 4096) * ch ? buf : new float[(keep + 4096) * ch];
                System.arraycopy(buf, start * ch, nb, 0, keep * ch);
                buf = nb;
                pos -= start;
                frames = keep;
                need -= start;
                try {
                    while (frames <= need) {
                        int n = dec.read(tmp(ch), 2048);
                        if (n == 0) {
                            if (!looping || stopped) return false;
                            dec.close();
                            dec = pack.stream(variant);
                            n = dec.read(tmp(ch), 2048);
                            if (n == 0) return false;
                        }
                        if ((frames + n) * ch > buf.length) buf = Arrays.copyOf(buf, (frames + n + 4096) * ch);
                        System.arraycopy(tmpBuf, 0, buf, frames * ch, n * ch);
                        frames += n;
                    }
                } catch (Exception e) {
                    System.err.println("[sound] stream error " + variant.path() + ": " + e);
                    return false;
                }
                return true;
            }
        }

        float[] tmpBuf = new float[0];

        float[] tmp(int ch) {
            if (tmpBuf.length < 2048 * ch) tmpBuf = new float[2048 * ch];
            return tmpBuf;
        }

        static final int MAX_VOICES = 48, MAX_STREAMS = 8, BLOCK = 256;
        final ConcurrentLinkedQueue<Voice> incoming = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<Stream> incomingStreams = new ConcurrentLinkedQueue<>();
        final List<Voice> voices = new ArrayList<>();
        final List<Stream> streams = new ArrayList<>();
        final Random rnd = new Random();
        volatile double lx, ly, lz;
        volatile float lyaw, master = 1, rain, reverb, underwater, musicVol = 1;
        volatile boolean running = true;
        volatile SoundPack pack;
        volatile Stream music;
        java.nio.file.Path gameDir;
        SourceDataLine line;
        Thread thread;

        public static IAudioBus open() { return open(null, true); }

        public static IAudioBus open(java.nio.file.Path gameDir, boolean usePack) {
            try {
                Synth s = new Synth();
                s.gameDir = gameDir;
                AudioFormat fmt = new AudioFormat(RATE, 16, 2, true, false);
                s.line = AudioSystem.getSourceDataLine(fmt);
                s.line.open(fmt, BLOCK * 4 * 12);
                s.line.start();
                s.thread = new Thread(s::run, "audio");
                s.thread.setDaemon(true);
                s.thread.setPriority(Thread.MAX_PRIORITY - 1);
                s.thread.start();
                s.configure(usePack);
                return s;
            } catch (Throwable e) {
                System.err.println("[audio] unavailable, continuing muted: " + e);
                return new Null();
            }
        }

        final java.util.concurrent.atomic.AtomicInteger configGen = new java.util.concurrent.atomic.AtomicInteger();

        @Override public void configure(boolean enablePack) {
            int gen = configGen.incrementAndGet();
            Thread t = new Thread(() -> {
                SoundPack p;
                synchronized (configGen) {
                    // toggling the option quickly must not let an older, slower discovery win
                    if (gen != configGen.get()) return;
                    p = enablePack ? SoundPack.discover(gameDir) : null;
                    if (gen != configGen.get()) { if (p != null) p.close(); return; }
                    SoundPack old = pack;
                    pack = p;
                    if (old != null) {
                        stopStreams(); // they read from the old pack's files
                        try { Thread.sleep(250); } catch (InterruptedException ignored) { }
                        old.close();
                    }
                }
                // warm caches in the background so the first play of a sound never stalls
                if (p != null) {
                    List<String> ids = new ArrayList<>(Registries.SOUNDS.keySet());
                    ids.removeIf(i -> i.startsWith("ambient.") || i.startsWith("music."));
                    p.prewarm(ids);
                    System.out.printf("[sound] decoded %d clips (%.1f MB)%n", p.clips.size(), p.memoryBytes() / 1048576.0);
                }
                for (String id : new ArrayList<>(Registries.SOUNDS.keySet())) {
                    if (p != null && p.has(id)) continue;
                    try { variants(id); } catch (Throwable ignored) { }
                }
            }, "audio-load");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY + 1);
            t.start();
        }

        @Override public boolean usingPack() { return pack != null; }

        @Override public boolean packHas(String id) { SoundPack p = pack; return p != null && p.has(id); }

        @Override public String source() {
            SoundPack p = pack;
            return p == null ? "built-in" : String.join(", ", p.description);
        }

        /** Try the pack first; returns true if the pack handled the event. */
        boolean playPack(String id, double x, double y, double z, float volume, float pitch, boolean positional) {
            SoundPack p = pack;
            if (p == null || !p.has(id)) return false;
            SoundPack.Pick pk = p.pick(id, rnd);
            if (pk == null) return true; // defined but empty (vanilla: silent)
            if (pk.variant().stream) {
                try {
                    Stream s = new Stream(pk.variant(), p.stream(pk.variant()), false, false, Math.min(1, volume) * pk.volume());
                    s.gain = s.target;
                    s.step *= Math.max(0.5, Math.min(2, pitch * pk.pitch()));
                    incomingStreams.add(s);
                } catch (Exception e) { return false; }
                return true;
            }
            if (p.isBroken(pk.variant())) return false; // missing/corrupt file: use the built-in sound
            OggDecoder.Clip c = p.cached(pk.variant());
            if (c == null) {
                // first use: decode off the game thread, then start the voice (a few ms late, never a frame hitch)
                long asked = System.nanoTime();
                loader.execute(() -> {
                    OggDecoder.Clip dc = p.clip(pk.variant());
                    if (dc != null && dc.pcm.length > 0 && System.nanoTime() - asked < 1_500_000_000L)
                        incoming.add(packVoice(dc, pk, x, y, z, volume, pitch, positional));
                });
                return true;
            }
            if (c.pcm.length == 0) return false;
            incoming.add(packVoice(c, pk, x, y, z, volume, pitch, positional));
            return true;
        }

        final java.util.concurrent.ExecutorService loader = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "audio-decode");
            t.setDaemon(true);
            return t;
        });

        Voice packVoice(OggDecoder.Clip c, SoundPack.Pick pk, double x, double y, double z, float volume, float pitch, boolean positional) {
            Voice v = new Voice();
            v.ps = c.pcm;
            v.len = c.pcm.length;
            v.vanilla = true;
            float pt = Math.max(0.5f, Math.min(2f, pitch * pk.pitch()));
            v.rate = pt * c.rate / (double) RATE;
            v.vol = Math.min(1, Math.max(0, volume)) * pk.volume();
            v.range = pk.variant().attenuation * Math.max(1, volume);
            v.positional = positional;
            v.x = x; v.y = y; v.z = z;
            if (!positional) { v.tl = v.tr = v.gl = v.gr = v.vol * 0.7071f; v.fresh = false; }
            return v;
        }

        public void play(String id, double x, double y, double z, float volume, float pitch) {
            if (playPack(id, x, y, z, volume, pitch, true)) return;
            float[][] vs = variants(id);
            if (vs == null) return;
            Voice v = new Voice();
            double jitter = 1 + (rnd.nextDouble() - 0.5) * 0.04; // a touch of random pitch so repeats never machine-gun
            v.pf = vs[rnd.nextInt(vs.length)];
            v.len = v.pf.length;
            v.rate = Math.max(0.25, Math.min(4, pitch * jitter));
            v.x = x; v.y = y; v.z = z; v.positional = true;
            v.vol = Math.min(1, volume) * 0.8f; // built-in clips are peak-normalised; keep them at the old loudness
            v.range = 16 * Math.max(1, volume);
            incoming.add(v);
        }

        public void playUi(String id, float volume, float pitch) {
            if (playPack(id, 0, 0, 0, volume, pitch, false)) return;
            float[][] vs = variants(id);
            if (vs == null) return;
            Voice v = new Voice();
            v.pf = vs[rnd.nextInt(vs.length)];
            v.len = v.pf.length;
            v.rate = pitch; v.vol = volume;
            v.tl = v.tr = v.gl = v.gr = volume;
            v.fresh = false;
            incoming.add(v);
        }

        @Override public Loop loop(String id, float volume) {
            SoundPack p = pack;
            if (p == null || !p.has(id)) return null;
            SoundPack.Pick pk = p.pick(id, rnd);
            if (pk == null) return null;
            try {
                Stream s = new Stream(pk.variant(), p.stream(pk.variant()), false, true, volume * pk.volume());
                incomingStreams.add(s);
                return s;
            } catch (Exception e) {
                System.err.println("[sound] loop " + id + ": " + e);
                return null;
            }
        }

        @Override public boolean playMusic(String id) {
            SoundPack p = pack;
            if (p == null || !p.has(id)) return false;
            SoundPack.Pick pk = p.pick(id, rnd);
            if (pk == null) return false;
            try {
                Stream s = new Stream(pk.variant(), p.stream(pk.variant()), true, false, pk.volume());
                s.step *= Math.max(0.5, Math.min(2, pk.pitch()));
                Stream old = music;
                if (old != null) old.stop();
                music = s;
                incomingStreams.add(s);
                System.out.println("[sound] music: " + pk.variant().name);
                return true;
            } catch (Exception e) {
                System.err.println("[sound] music " + id + ": " + e);
                return false;
            }
        }

        @Override public boolean musicPlaying() { Stream m = music; return m != null && !m.ended && !m.stopped; }

        volatile boolean killStreams;

        /** Fade out every stream (used before the pack that feeds them is closed). */
        void stopStreams() { killStreams = true; music = null; }

        @Override public void stopMusic() { Stream m = music; if (m != null) m.stop(); music = null; }

        @Override public void setMusicVolume(float v) { musicVol = v; }

        public void listener(double x, double y, double z, float yaw) { lx = x; ly = y; lz = z; lyaw = yaw; }

        public void setVolume(float m) { master = m; }

        public void setRain(float l) { rain = l; }

        @Override public void setEnvironment(float rv, float uw) { reverb = rv; underwater = uw; }

        void spatial(Voice v) {
            if (!v.positional) return;
            double dx = v.x - lx, dy = v.y - ly, dz = v.z - lz;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            float att = (float) Math.max(0, 1 - d / v.range) * v.vol;
            double yaw = Math.toRadians(lyaw);
            double side = d < 1e-4 ? 0 : (dx * -Math.cos(yaw) + dz * -Math.sin(yaw)) / d;
            double front = d < 1e-4 ? 1 : (dx * -Math.sin(yaw) + dz * Math.cos(yaw)) / d;
            double pan;
            if (v.vanilla) {
                pan = side; // OpenAL-style panning of a mono source, no filtering
                v.lpk = 1;
            } else {
                pan = side * Math.min(1, d / 1.5) * 0.85;
                if (front < 0) att *= (float) (1 + front * 0.12);
                double cutoff = 18000 / (1 + d / 10.0) * (front < 0 ? 0.75 : 1);
                if (underwater > 0) cutoff = Math.min(cutoff, 900);
                v.lpk = (float) Math.min(1, 1 - Math.exp(-2 * Math.PI * cutoff / RATE));
            }
            v.tl = (float) (att * Math.sqrt(0.5 * (1 - pan)));
            v.tr = (float) (att * Math.sqrt(0.5 * (1 + pan)));
            v.loud = att;
            if (v.fresh) { v.gl = v.tl; v.gr = v.tr; v.fresh = false; }
        }

        // --- reverb for built-in sounds (Freeverb-style: 4 parallel combs + 2 allpasses per channel)
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

        /** Transparent below 0.85 full scale, soft knee above (vanilla OpenAL would hard-clip). */
        static double limit(double x) {
            double a = Math.abs(x);
            if (a <= 0.85) return x;
            double y = 0.85 + 0.15 * Math.tanh((a - 0.85) / 0.15);
            return x < 0 ? -y : y;
        }

        void mixVoices(float[] mixL, float[] mixR, float[] send, int frames) {
            for (int vi = voices.size() - 1; vi >= 0; vi--) {
                Voice v = voices.get(vi);
                spatial(v);
                float dl = (v.tl - v.gl) / frames, dr = (v.tr - v.gr) / frames;
                int len = v.len;
                double k = v.lpk;
                boolean filt = k < 0.9999;
                float[] pf = v.pf;
                short[] ps = v.ps;
                for (int i = 0; i < frames; i++) {
                    int p = (int) v.pos;
                    if (p >= len) break;
                    double fr = v.pos - p;
                    float a, b;
                    if (ps != null) { a = ps[p] * (1f / 32768f); b = p + 1 < len ? ps[p + 1] * (1f / 32768f) : 0; }
                    else { a = pf[p]; b = p + 1 < len ? pf[p + 1] : 0; }
                    float s = (float) (a + (b - a) * fr);
                    if (filt) {
                        v.sl += (s * v.gl - v.sl) * k;
                        v.sr += (s * v.gr - v.sr) * k;
                    } else {
                        v.sl = s * v.gl;
                        v.sr = s * v.gr;
                    }
                    mixL[i] += (float) v.sl;
                    mixR[i] += (float) v.sr;
                    if (!v.vanilla) send[i] += (float) ((v.sl + v.sr) * 0.5);
                    v.gl += dl;
                    v.gr += dr;
                    v.pos += v.rate;
                }
                v.gl = v.tl;
                v.gr = v.tr;
                if (v.pos >= len) voices.remove(vi);
            }
        }

        void mixStreams(float[] mixL, float[] mixR, int frames) {
            for (int si = streams.size() - 1; si >= 0; si--) {
                Stream s = streams.get(si);
                float tgt = s.stopped ? 0 : s.target * (s.music ? musicVol : 1);
                // music stops with a short fade; loops fade with their handle (vanilla ticks the fade at 20 Hz)
                float rate = s.music ? 0.0008f : 0.0006f;
                int ch = s.ch;
                boolean alive = true;
                for (int i = 0; i < frames; i++) {
                    s.gain += Math.max(-rate, Math.min(rate, tgt - s.gain));
                    int p = (int) s.pos;
                    if (!s.fill(p + 1)) { alive = false; break; }
                    p = (int) s.pos;
                    double fr = s.pos - p;
                    float l, r;
                    if (ch == 1) {
                        float a = s.buf[p], b = s.buf[p + 1];
                        l = r = (float) (a + (b - a) * fr) * 0.7071f;
                    } else {
                        float a = s.buf[p * ch], b = s.buf[(p + 1) * ch];
                        float c = s.buf[p * ch + 1], d = s.buf[(p + 1) * ch + 1];
                        l = (float) (a + (b - a) * fr);
                        r = (float) (c + (d - c) * fr);
                    }
                    mixL[i] += l * s.gain;
                    mixR[i] += r * s.gain;
                    s.pos += s.step;
                }
                if (!alive || (s.stopped && s.gain <= 0.0005f)) {
                    s.ended = true;
                    s.dec.close();
                    streams.remove(si);
                }
            }
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
                        // nearest-first priority: drop the quietest voice
                        int q = 0;
                        for (int i = 1; i < voices.size(); i++) if (voices.get(i).loud < voices.get(q).loud) q = i;
                        voices.remove(q);
                    }
                    voices.add(nv);
                }
                boolean kill = killStreams;
                if (kill) { killStreams = false; for (Stream st : streams) st.stop(); }
                Stream ns;
                while ((ns = incomingStreams.poll()) != null) {
                    if (kill) ns.stop();
                    if (streams.size() >= MAX_STREAMS) {
                        int victim = 0; // oldest non-music stream (music keeps playing when many loops overlap)
                        while (victim < streams.size() - 1 && streams.get(victim).music) victim++;
                        Stream o = streams.remove(victim); o.ended = true; o.dec.close();
                    }
                    streams.add(ns);
                }
                Arrays.fill(mixL, 0);
                Arrays.fill(mixR, 0);
                Arrays.fill(send, 0);
                mixVoices(mixL, mixR, send, frames);
                mixStreams(mixL, mixR, frames);
                boolean builtinEnv = pack == null;
                float rainT = builtinEnv ? rain : 0, revT = builtinEnv ? reverb : 0, uwT = builtinEnv ? underwater : 0;
                float m = master;
                for (int i = 0; i < frames; i++) {
                    rainNow += (rainT - rainNow) * 0.0005f;
                    revNow += (revT - revNow) * 0.0002f;
                    uwNow += (uwT - uwNow) * 0.001f;
                    if (rainNow > 0.001f) { // built-in rain bed (the pack mode plays weather.rain events instead)
                        rl += ((rnd.nextDouble() * 2 - 1) - rl) * 0.25; rr += ((rnd.nextDouble() * 2 - 1) - rr) * 0.25;
                        rl2 += (rl - rl2) * 0.5; rr2 += (rr - rr2) * 0.5;
                        float drop = rnd.nextInt(900) == 0 ? (float) (rnd.nextDouble() * 0.6) : 0;
                        mixL[i] += (float) (rl2 * 0.22 + drop) * rainNow;
                        mixR[i] += (float) (rr2 * 0.22 + (rnd.nextInt(900) == 0 ? rnd.nextDouble() * 0.6 : 0)) * rainNow;
                    }
                    if (builtinEnv || revNow > 0.0001f) {
                        float wet = builtinEnv ? 0.06f + revNow * 0.32f : 0;
                        float in = send[i] * wet;
                        mixL[i] += reverbTap(in, true);
                        mixR[i] += reverbTap(in, false);
                    }
                    double outL = mixL[i], outR = mixR[i];
                    if (uwNow > 0.001f) {
                        double kk = 0.06;
                        uwL += (outL - uwL) * kk; uwR += (outR - uwR) * kk;
                        outL = outL + (uwL * 0.9 - outL) * uwNow;
                        outR = outR + (uwR * 0.9 - outR) * uwNow;
                    }
                    int L = (int) (limit(outL * m) * 32767), R = (int) (limit(outR * m) * 32767);
                    buf[i * 4] = (byte) L; buf[i * 4 + 1] = (byte) (L >> 8);
                    buf[i * 4 + 2] = (byte) R; buf[i * 4 + 3] = (byte) (R >> 8);
                }
                line.write(buf, 0, buf.length);
            }
            for (Stream st : streams) { st.ended = true; st.dec.close(); }
            streams.clear();
            Stream ns;
            while ((ns = incomingStreams.poll()) != null) ns.dec.close();
        }

        public void close() {
            running = false;
            configGen.incrementAndGet();
            SoundPack p = pack;
            pack = null;
            if (p != null) {
                try { if (thread != null) thread.join(500); } catch (InterruptedException ignored) { }
                p.close();
            }
            try { if (line != null) { line.stop(); line.close(); } } catch (Exception ignored) { }
        }
    }
}
