package mc.client;

/**
 * Immutable quad list for one 16x16x16 section (Section 2.5 meshing output). Opaque/cutout quads come first
 * ({@code [0, nOpaque)}), translucent quads follow. Positions are world coordinates.
 * Per vertex: smoothed sky light and block light (0..60, i.e. 4x the 0..15 level) and ambient occlusion 0..3.
 */
public final class Mesh {
    public static final Mesh EMPTY = new Mesh(0, 0, new float[0], new float[0], new byte[0], new byte[0], new byte[0], new short[0], new int[0], new byte[0]);
    /** face codes: 0 down, 1 up, 2 north, 3 south, 4 west, 5 east, 6 double sided (no back-face cull). */
    public final int n, nOpaque;
    public final float[] pos;   // 12 per quad
    public final float[] uv;    // 8 per quad, texel units (tiling by &15)
    public final byte[] sky, blk, ao; // 4 per quad
    public final short[] tex;
    public final int[] tint;
    public final byte[] face;

    public Mesh(int n, int nOpaque, float[] pos, float[] uv, byte[] sky, byte[] blk, byte[] ao, short[] tex, int[] tint, byte[] face) {
        this.n = n; this.nOpaque = nOpaque; this.pos = pos; this.uv = uv; this.sky = sky; this.blk = blk; this.ao = ao;
        this.tex = tex; this.tint = tint; this.face = face;
    }

    /** Growable builder. */
    public static final class Builder {
        int n;
        float[] pos = new float[12 * 256], uv = new float[8 * 256];
        byte[] sky = new byte[4 * 256], blk = new byte[4 * 256], ao = new byte[4 * 256];
        short[] tex = new short[256];
        int[] tint = new int[256];
        byte[] face = new byte[256];

        void grow() {
            int c = tex.length * 2;
            pos = java.util.Arrays.copyOf(pos, c * 12); uv = java.util.Arrays.copyOf(uv, c * 8);
            sky = java.util.Arrays.copyOf(sky, c * 4); blk = java.util.Arrays.copyOf(blk, c * 4); ao = java.util.Arrays.copyOf(ao, c * 4);
            tex = java.util.Arrays.copyOf(tex, c); tint = java.util.Arrays.copyOf(tint, c); face = java.util.Arrays.copyOf(face, c);
        }

        /** Add a quad; v = 4 x (x,y,z), t = 4 x (u,v), lights 4 each. */
        public void quad(float[] v, float[] t, int[] s, int[] b, int[] a, int texId, int tintC, int faceCode) {
            if (n == tex.length) grow();
            System.arraycopy(v, 0, pos, n * 12, 12);
            System.arraycopy(t, 0, uv, n * 8, 8);
            for (int i = 0; i < 4; i++) { sky[n * 4 + i] = (byte) s[i]; blk[n * 4 + i] = (byte) b[i]; ao[n * 4 + i] = (byte) a[i]; }
            tex[n] = (short) texId;
            tint[n] = tintC;
            face[n] = (byte) faceCode;
            n++;
        }

        public Mesh build(Builder translucent) {
            int total = n + translucent.n;
            if (total == 0) return EMPTY;
            float[] p = new float[total * 12], u = new float[total * 8];
            byte[] s = new byte[total * 4], b = new byte[total * 4], a = new byte[total * 4], f = new byte[total];
            short[] t = new short[total];
            int[] ti = new int[total];
            copy(this, 0, p, u, s, b, a, t, ti, f);
            copy(translucent, n, p, u, s, b, a, t, ti, f);
            return new Mesh(total, n, p, u, s, b, a, t, ti, f);
        }

        private static void copy(Builder src, int off, float[] p, float[] u, byte[] s, byte[] b, byte[] a, short[] t, int[] ti, byte[] f) {
            System.arraycopy(src.pos, 0, p, off * 12, src.n * 12);
            System.arraycopy(src.uv, 0, u, off * 8, src.n * 8);
            System.arraycopy(src.sky, 0, s, off * 4, src.n * 4);
            System.arraycopy(src.blk, 0, b, off * 4, src.n * 4);
            System.arraycopy(src.ao, 0, a, off * 4, src.n * 4);
            System.arraycopy(src.tex, 0, t, off, src.n);
            System.arraycopy(src.tint, 0, ti, off, src.n);
            System.arraycopy(src.face, 0, f, off, src.n);
        }
    }
}
