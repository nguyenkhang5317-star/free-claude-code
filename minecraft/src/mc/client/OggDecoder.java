package mc.client;

import com.jcraft.jogg.Packet;
import com.jcraft.jogg.Page;
import com.jcraft.jogg.StreamState;
import com.jcraft.jogg.SyncState;
import com.jcraft.jorbis.Block;
import com.jcraft.jorbis.Comment;
import com.jcraft.jorbis.DspState;
import com.jcraft.jorbis.Info;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Incremental Ogg Vorbis decoder on top of JOrbis (third_party/jorbis, LGPL). Used for sounds that come from resource
 * packs / the player's own Minecraft assets; short clips are decoded fully ({@link #decodeMono}), music and ambient
 * loops are pulled block by block ({@link #read}).
 */
public final class OggDecoder implements AutoCloseable {
    private final InputStream in;
    private final SyncState oy = new SyncState();
    private final StreamState os = new StreamState();
    private final Page og = new Page();
    private final Packet op = new Packet();
    private final Info vi = new Info();
    private final Comment vc = new Comment();
    private final DspState vd = new DspState();
    private final Block vb = new Block(vd);
    private final float[][][] pcm = new float[1][][];
    private int[] index;
    private boolean eos;
    public final int channels, rate;

    public OggDecoder(InputStream in) throws IOException {
        this.in = in;
        oy.init();
        if (!nextPage()) throw new IOException("not an Ogg stream");
        os.init(og.serialno());
        vi.init();
        vc.init();
        if (os.pagein(og) < 0 || os.packetout(op) != 1 || vi.synthesis_headerin(vc, op) < 0) throw new IOException("not a Vorbis stream");
        int headers = 1;
        while (headers < 3) {
            int r = os.packetout(op);
            if (r == 1) {
                if (vi.synthesis_headerin(vc, op) < 0) throw new IOException("corrupt Vorbis header");
                headers++;
            } else if (r == 0) {
                if (!nextPage()) throw new IOException("truncated Vorbis header");
                os.pagein(og);
            }
        }
        channels = vi.channels;
        rate = vi.rate;
        vd.synthesis_init(vi);
        vb.init(vd);
        index = new int[channels];
    }

    private boolean nextPage() throws IOException {
        while (true) {
            int r = oy.pageout(og);
            if (r == 1) return true;
            if (r < 0) continue; // hole in data: skip
            int off = oy.buffer(8192);
            int n = in.read(oy.data, off, 8192);
            if (n <= 0) return false;
            oy.wrote(n);
        }
    }

    /**
     * Decode up to {@code frames} frames into {@code out} (interleaved, {@link #channels} per frame).
     * Returns the number of frames written; 0 means end of stream.
     */
    public int read(float[] out, int frames) throws IOException {
        int done = 0;
        while (done < frames) {
            int avail = vd.synthesis_pcmout(pcm, index);
            if (avail > 0) {
                int n = Math.min(avail, frames - done);
                float[][] p = pcm[0];
                for (int c = 0; c < channels; c++) {
                    float[] ch = p[c];
                    int base = index[c];
                    for (int i = 0; i < n; i++) out[(done + i) * channels + c] = ch[base + i];
                }
                vd.synthesis_read(n);
                done += n;
                continue;
            }
            if (eos) break;
            int r = os.packetout(op);
            if (r == 1) {
                if (vb.synthesis(op) == 0) vd.synthesis_blockin(vb);
            } else if (r == 0) {
                if (!nextPage()) { eos = true; continue; }
                os.pagein(og);
                if (og.eos() != 0) { /* last page: drain remaining packets */ }
            }
        }
        return done;
    }

    /** Decoded clip: mono 16-bit PCM at its native sample rate. */
    public static final class Clip {
        public final short[] pcm;
        public final int rate;

        Clip(short[] pcm, int rate) { this.pcm = pcm; this.rate = rate; }

        public double seconds() { return pcm.length / (double) rate; }
    }

    /** Fully decode an Ogg Vorbis file, down-mixing to mono (positional sounds are mono in vanilla too). */
    public static Clip decodeMono(byte[] data) throws IOException {
        try (OggDecoder d = new OggDecoder(new ByteArrayInputStream(data))) {
            int ch = d.channels;
            float[] buf = new float[4096 * ch];
            short[] out = new short[Math.max(4096, data.length * 6)];
            int len = 0;
            while (true) {
                int n = d.read(buf, 4096);
                if (n == 0) break;
                if (len + n > out.length) out = java.util.Arrays.copyOf(out, Math.max(out.length * 2, len + n));
                for (int i = 0; i < n; i++) {
                    float s = 0;
                    for (int c = 0; c < ch; c++) s += buf[i * ch + c];
                    s /= ch;
                    int v = Math.round(s * 32767f);
                    out[len++] = (short) Math.max(-32768, Math.min(32767, v));
                }
            }
            return new Clip(java.util.Arrays.copyOf(out, len), d.rate);
        }
    }

    @Override public void close() {
        try { in.close(); } catch (IOException ignored) { }
        try { os.clear(); vb.clear(); vd.clear(); vi.clear(); oy.clear(); } catch (Throwable ignored) { }
    }
}
