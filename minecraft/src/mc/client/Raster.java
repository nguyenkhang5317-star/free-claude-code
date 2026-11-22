package mc.client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Software rasteriser. Geometry is transformed + near-clipped + projected into {@link PolyBuf}s, then horizontal
 * screen bands are rasterised in parallel (perspective-correct texturing, 1/z depth buffer, Gouraud brightness,
 * per-pixel distance fog, alpha-tested cutout and alpha-blended translucent passes).
 */
public final class Raster {
    public static final int MODE_OPAQUE = 0, MODE_TRANSLUCENT = 1, MODE_NODEPTH = 2, MODE_LINE = 3;

    public int w, h;
    public int[] color;
    public float[] depth;
    public int[][] textures = new int[0][];
    public float fogStart = 64, fogEnd = 96;
    public int fogColor = 0xC0D8FF;
    public float focal;
    // camera
    public double camX, camY, camZ;
    public float rx, ry, rz, ux, uy, uz, fx, fy, fz;
    static final float NEAR = 0.05f;

    private final ExecutorService pool;
    private final int threads;

    public Raster(int w, int h) {
        threads = Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors()));
        pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "raster");
            t.setDaemon(true);
            return t;
        });
        resize(w, h);
    }

    public int threads() { return threads; }

    public void resize(int w, int h) {
        this.w = w;
        this.h = h;
        color = new int[w * h];
        depth = new float[w * h];
    }

    public void setCamera(double x, double y, double z, float yawDeg, float pitchDeg, float fovDeg) { setCamera(x, y, z, yawDeg, pitchDeg, 0, fovDeg); }

    /** Camera with an additional roll (degrees, positive tilts the view clockwise) used by view bobbing / hurt tilt. */
    public void setCamera(double x, double y, double z, float yawDeg, float pitchDeg, float rollDeg, float fovDeg) {
        camX = x; camY = y; camZ = z;
        double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
        fx = (float) (-Math.sin(yaw) * Math.cos(pitch));
        fy = (float) (-Math.sin(pitch));
        fz = (float) (Math.cos(yaw) * Math.cos(pitch));
        rx = (float) -Math.cos(yaw); ry = 0; rz = (float) -Math.sin(yaw);
        // u = r x f
        ux = ry * fz - rz * fy;
        uy = rz * fx - rx * fz;
        uz = rx * fy - ry * fx;
        if (rollDeg != 0) {
            float cr = (float) Math.cos(Math.toRadians(rollDeg)), sr = (float) Math.sin(Math.toRadians(rollDeg));
            float nrx = rx * cr + ux * sr, nry = ry * cr + uy * sr, nrz = rz * cr + uz * sr;
            float nux = ux * cr - rx * sr, nuy = uy * cr - ry * sr, nuz = uz * cr - rz * sr;
            rx = nrx; ry = nry; rz = nrz; ux = nux; uy = nuy; uz = nuz;
        }
        focal = (float) ((h / 2.0) / Math.tan(Math.toRadians(fovDeg) / 2));
    }

    // ------------------------------------------------------------------ polygon buffer
    public static final class PolyBuf {
        public int np, nv;
        int[] vstart = new int[4096], cnt = new int[4096], tex = new int[4096], tint = new int[4096], miny = new int[4096], maxy = new int[4096];
        byte[] mode = new byte[4096];
        float[] sx = new float[16384], sy = new float[16384], iz = new float[16384], uz = new float[16384], vz = new float[16384], br = new float[16384];
        int[] lineColor = new int[4096];
        // clip scratch
        final float[] cx = new float[12], cy = new float[12], cz = new float[12], cu = new float[12], cv = new float[12], cb = new float[12];
        final float[] tx = new float[12], ty = new float[12], tz = new float[12], tu = new float[12], tv = new float[12], tb = new float[12];

        public void clear() { np = 0; nv = 0; }

        void ensure(int moreV) {
            if (np + 1 >= vstart.length) {
                int c = vstart.length * 2;
                vstart = java.util.Arrays.copyOf(vstart, c); cnt = java.util.Arrays.copyOf(cnt, c); tex = java.util.Arrays.copyOf(tex, c);
                tint = java.util.Arrays.copyOf(tint, c); miny = java.util.Arrays.copyOf(miny, c); maxy = java.util.Arrays.copyOf(maxy, c);
                mode = java.util.Arrays.copyOf(mode, c); lineColor = java.util.Arrays.copyOf(lineColor, c);
            }
            if (nv + moreV >= sx.length) {
                int c = sx.length * 2;
                sx = java.util.Arrays.copyOf(sx, c); sy = java.util.Arrays.copyOf(sy, c); iz = java.util.Arrays.copyOf(iz, c);
                uz = java.util.Arrays.copyOf(uz, c); vz = java.util.Arrays.copyOf(vz, c); br = java.util.Arrays.copyOf(br, c);
            }
        }
    }

    /**
     * Transform, clip and project a world-space polygon (n <= 4 vertices typical) into the buffer.
     * px/py/pz world coordinates, u/v texel coords, b brightness 0..1.
     */
    public void project(PolyBuf o, float[] px, float[] py, float[] pz, float[] u, float[] v, float[] b, int n, int tex, int tint, int mode) {
        float[] cx = o.cx, cy = o.cy, cz = o.cz, cu = o.cu, cv = o.cv, cb = o.cb;
        int behind = 0;
        float ox = (float) camX, oy = (float) camY, oz = (float) camZ;
        for (int i = 0; i < n; i++) {
            float dx = px[i] - ox, dy = py[i] - oy, dz = pz[i] - oz;
            cx[i] = dx * rx + dy * ry + dz * rz;
            cy[i] = dx * ux + dy * uy + dz * uz;
            cz[i] = dx * fx + dy * fy + dz * fz;
            cu[i] = u[i]; cv[i] = v[i]; cb[i] = b[i];
            if (cz[i] < NEAR) behind++;
        }
        if (behind == n) return;
        // quick frustum rejection against the side planes
        float kx = (w / 2f) / focal * 1.05f, ky = (h / 2f) / focal * 1.05f;
        int l = 0, r = 0, t = 0, btm = 0;
        for (int i = 0; i < n; i++) {
            if (cx[i] < -cz[i] * kx) l++;
            if (cx[i] > cz[i] * kx) r++;
            if (cy[i] > cz[i] * ky) t++;
            if (cy[i] < -cz[i] * ky) btm++;
        }
        if (l == n || r == n || t == n || btm == n) return;
        if (cz[0] > fogEnd + 24 && cz[n - 1] > fogEnd + 24 && cz[n / 2] > fogEnd + 24) return;
        int m = n;
        float[] X = cx, Y = cy, Z = cz, U = cu, V = cv, B = cb;
        if (behind > 0) {
            // Sutherland-Hodgman against z = NEAR
            float[] tx = o.tx, ty = o.ty, tz = o.tz, tu = o.tu, tv = o.tv, tb = o.tb;
            m = 0;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                boolean in1 = cz[i] >= NEAR, in2 = cz[j] >= NEAR;
                if (in1) { tx[m] = cx[i]; ty[m] = cy[i]; tz[m] = cz[i]; tu[m] = cu[i]; tv[m] = cv[i]; tb[m] = cb[i]; m++; }
                if (in1 != in2) {
                    float k = (NEAR - cz[i]) / (cz[j] - cz[i]);
                    tx[m] = cx[i] + (cx[j] - cx[i]) * k; ty[m] = cy[i] + (cy[j] - cy[i]) * k; tz[m] = NEAR;
                    tu[m] = cu[i] + (cu[j] - cu[i]) * k; tv[m] = cv[i] + (cv[j] - cv[i]) * k; tb[m] = cb[i] + (cb[j] - cb[i]) * k;
                    m++;
                }
            }
            if (m < 3) return;
            X = tx; Y = ty; Z = tz; U = tu; V = tv; B = tb;
        }
        o.ensure(m);
        int base = o.nv;
        float hw = w / 2f, hh = h / 2f;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE, minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        for (int i = 0; i < m; i++) {
            float iz = 1f / Z[i];
            float sx = hw + X[i] * iz * focal, sy = hh - Y[i] * iz * focal;
            o.sx[base + i] = sx; o.sy[base + i] = sy; o.iz[base + i] = iz;
            o.uz[base + i] = U[i] * iz; o.vz[base + i] = V[i] * iz; o.br[base + i] = B[i];
            if (sy < minY) minY = sy;
            if (sy > maxY) maxY = sy;
            if (sx < minX) minX = sx;
            if (sx > maxX) maxX = sx;
        }
        if (maxX < 0 || minX >= w || maxY < 0 || minY >= h) return;
        int y0 = Math.max(0, (int) Math.ceil(minY - 0.5f)), y1 = Math.min(h - 1, (int) Math.ceil(maxY - 0.5f) - 1);
        if (y1 < y0) return;
        int p = o.np++;
        o.vstart[p] = base; o.cnt[p] = m; o.tex[p] = tex; o.tint[p] = tint; o.mode[p] = (byte) mode; o.miny[p] = y0; o.maxy[p] = y1;
        o.nv += m;
    }

    /** 3D line (selection outline etc.), drawn with depth test. */
    public void line(PolyBuf o, float x0, float y0, float z0, float x1, float y1, float z1, int argb) {
        float ox = (float) camX, oy = (float) camY, oz = (float) camZ;
        float ax = (x0 - ox) * rx + (y0 - oy) * ry + (z0 - oz) * rz, ay = (x0 - ox) * ux + (y0 - oy) * uy + (z0 - oz) * uz, az = (x0 - ox) * fx + (y0 - oy) * fy + (z0 - oz) * fz;
        float bx = (x1 - ox) * rx + (y1 - oy) * ry + (z1 - oz) * rz, by = (x1 - ox) * ux + (y1 - oy) * uy + (z1 - oz) * uz, bz = (x1 - ox) * fx + (y1 - oy) * fy + (z1 - oz) * fz;
        if (az < NEAR && bz < NEAR) return;
        if (az < NEAR) { float k = (NEAR - az) / (bz - az); ax += (bx - ax) * k; ay += (by - ay) * k; az = NEAR; }
        if (bz < NEAR) { float k = (NEAR - bz) / (az - bz); bx += (ax - bx) * k; by += (ay - by) * k; bz = NEAR; }
        o.ensure(2);
        int base = o.nv;
        float hw = w / 2f, hh = h / 2f;
        o.sx[base] = hw + ax / az * focal; o.sy[base] = hh - ay / az * focal; o.iz[base] = 1 / az;
        o.sx[base + 1] = hw + bx / bz * focal; o.sy[base + 1] = hh - by / bz * focal; o.iz[base + 1] = 1 / bz;
        int p = o.np++;
        o.vstart[p] = base; o.cnt[p] = 2; o.mode[p] = MODE_LINE; o.lineColor[p] = argb;
        o.miny[p] = Math.max(0, (int) Math.floor(Math.min(o.sy[base], o.sy[base + 1])) - 1);
        o.maxy[p] = Math.min(h - 1, (int) Math.ceil(Math.max(o.sy[base], o.sy[base + 1])) + 1);
        o.nv += 2;
    }

    // ------------------------------------------------------------------ rasterisation
    /** Rasterise buffers in order, splitting the screen into bands processed in parallel. */
    public void rasterize(List<PolyBuf> bufs) {
        int bands = threads;
        List<Callable<Void>> jobs = new ArrayList<>();
        for (int i = 0; i < bands; i++) {
            final int y0 = i * h / bands, y1 = (i + 1) * h / bands - 1;
            jobs.add(() -> {
                Span sp = new Span();
                for (PolyBuf b : bufs) for (int p = 0; p < b.np; p++) if (b.maxy[p] >= y0 && b.miny[p] <= y1) draw(b, p, y0, y1, sp);
                return null;
            });
        }
        try {
            for (Future<Void> f : pool.invokeAll(jobs)) f.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static final class Span {
        float xl, xr, izl, izr, ul, ur, vl, vr, bl, brr;
    }

    private void draw(PolyBuf o, int p, int by0, int by1, Span sp) {
        int mode = o.mode[p];
        if (mode == MODE_LINE) { drawLine(o, p, by0, by1); return; }
        int base = o.vstart[p], n = o.cnt[p];
        int y0 = Math.max(by0, o.miny[p]), y1 = Math.min(by1, o.maxy[p]);
        int[] tx = textures[o.tex[p]];
        int tint = o.tint[p];
        boolean tinted = tint != BlockColors.IDENTITY;
        int tr = (tint >> 16) & 255, tg = (tint >> 8) & 255, tb = tint & 255;
        float[] sx = o.sx, sy = o.sy, iz = o.iz, uz = o.uz, vz = o.vz, br = o.br;
        int[] col = color;
        float[] dep = depth;
        int W = w;
        float fs = fogStart, fr = 1f / Math.max(1, fogEnd - fogStart);
        int fogR = (fogColor >> 16) & 255, fogG = (fogColor >> 8) & 255, fogB = fogColor & 255;
        for (int y = y0; y <= y1; y++) {
            float yc = y + 0.5f;
            boolean any = false;
            float xl = Float.MAX_VALUE, xr = -Float.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                int a = base + i, b = base + (i + 1 == n ? 0 : i + 1);
                float ya = sy[a], yb = sy[b];
                if ((ya <= yc && yb > yc) || (yb <= yc && ya > yc)) {
                    float t = (yc - ya) / (yb - ya);
                    float x = sx[a] + (sx[b] - sx[a]) * t;
                    float z = iz[a] + (iz[b] - iz[a]) * t, uu = uz[a] + (uz[b] - uz[a]) * t, vv = vz[a] + (vz[b] - vz[a]) * t, bb = br[a] + (br[b] - br[a]) * t;
                    if (x < xl) { xl = x; sp.izl = z; sp.ul = uu; sp.vl = vv; sp.bl = bb; }
                    if (x > xr) { xr = x; sp.izr = z; sp.ur = uu; sp.vr = vv; sp.brr = bb; }
                    any = true;
                }
            }
            if (!any || xr <= xl) continue;
            int x0 = Math.max(0, (int) Math.ceil(xl - 0.5f)), x1 = Math.min(W - 1, (int) Math.ceil(xr - 0.5f) - 1);
            if (x1 < x0) continue;
            float inv = 1f / (xr - xl);
            float dz = (sp.izr - sp.izl) * inv, du = (sp.ur - sp.ul) * inv, dv = (sp.vr - sp.vl) * inv, db = (sp.brr - sp.bl) * inv;
            float pre = x0 + 0.5f - xl;
            float z = sp.izl + dz * pre, u = sp.ul + du * pre, v = sp.vl + dv * pre, bb = sp.bl + db * pre;
            int row = y * W;
            for (int x = x0; x <= x1; x++, z += dz, u += du, v += dv, bb += db) {
                int i = row + x;
                if (z <= dep[i] && mode != MODE_NODEPTH) continue;
                float wz = 1f / z;
                int c = tx[((((int) (v * wz)) & 15) << 4) | (((int) (u * wz)) & 15)];
                int a = c >>> 24;
                if (mode == MODE_OPAQUE && a < 128) continue;
                if (a == 0) continue;
                int r = (c >> 16) & 255, g = (c >> 8) & 255, bl = c & 255;
                if (tinted) { r = Math.min(255, r * tr >> 7); g = Math.min(255, g * tg >> 7); bl = Math.min(255, bl * tb >> 7); }
                int k = (int) (bb * 256);
                if (k > 256) k = 256;
                r = r * k >> 8; g = g * k >> 8; bl = bl * k >> 8;
                float f = (wz - fs) * fr;
                if (f > 0) {
                    int fi = f >= 1 ? 256 : (int) (f * 256);
                    r += (fogR - r) * fi >> 8; g += (fogG - g) * fi >> 8; bl += (fogB - bl) * fi >> 8;
                }
                if (mode == MODE_OPAQUE) {
                    dep[i] = z;
                    col[i] = r << 16 | g << 8 | bl;
                } else {
                    int d = col[i];
                    int dr = (d >> 16) & 255, dg = (d >> 8) & 255, dbb = d & 255;
                    col[i] = (dr + ((r - dr) * a >> 8)) << 16 | (dg + ((g - dg) * a >> 8)) << 8 | (dbb + ((bl - dbb) * a >> 8));
                }
            }
        }
    }

    private void drawLine(PolyBuf o, int p, int by0, int by1) {
        int a = o.vstart[p];
        float x0 = o.sx[a], y0 = o.sy[a], z0 = o.iz[a], x1 = o.sx[a + 1], y1 = o.sy[a + 1], z1 = o.iz[a + 1];
        int steps = (int) Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) + 1;
        if (steps > 4 * (w + h)) return;
        int c = o.lineColor[p], alpha = c >>> 24;
        int cr = (c >> 16) & 255, cg = (c >> 8) & 255, cb = c & 255;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int x = (int) (x0 + (x1 - x0) * t), y = (int) (y0 + (y1 - y0) * t);
            if (x < 0 || x >= w || y < by0 || y > by1) continue;
            float z = z0 + (z1 - z0) * t;
            int idx = y * w + x;
            if (z * 1.004f < depth[idx]) continue;
            int d = color[idx];
            int dr = (d >> 16) & 255, dg = (d >> 8) & 255, db = d & 255;
            color[idx] = (dr + ((cr - dr) * alpha >> 8)) << 16 | (dg + ((cg - dg) * alpha >> 8)) << 8 | (db + ((cb - db) * alpha >> 8));
        }
    }

    public void shutdown() { pool.shutdownNow(); }
}
