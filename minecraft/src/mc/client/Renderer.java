package mc.client;

import mc.content.*;
import mc.core.Raycast;
import mc.net.S2C;

import java.util.*;
import java.util.concurrent.*;

/** Frame composition: sky, terrain sections (frustum culled, parallel projection), entities, translucency, overlays. */
public final class Renderer {
    public final Raster raster;
    final List<Raster.PolyBuf> opaqueBufs = new ArrayList<>();
    final Raster.PolyBuf skyBuf = new Raster.PolyBuf(), entBuf = new Raster.PolyBuf(), transBuf = new Raster.PolyBuf(), overlayBuf = new Raster.PolyBuf();
    final ExecutorService projPool;
    final int projThreads;
    static final float[] AO = {0.55f, 0.7f, 0.85f, 1f};
    static final float[] SHADE = {0.5f, 1f, 0.8f, 0.8f, 0.6f, 0.6f, 0.95f};
    final float[] lut = new float[61 * 61];
    final double[][] stars = new double[350][];
    // stats for F3
    public int sectionsDrawn, quadsDrawn, polys;
    public long projNanos, rasterNanos;
    public int skyColor, fogColorNow;
    public double celestial;
    public float skyBrightness;

    public Renderer(int w, int h) {
        raster = new Raster(w, h);
        projThreads = Math.max(1, Math.min(6, Runtime.getRuntime().availableProcessors()));
        projPool = Executors.newFixedThreadPool(projThreads, r -> {
            Thread t = new Thread(r, "project");
            t.setDaemon(true);
            return t;
        });
        for (int i = 0; i < projThreads; i++) opaqueBufs.add(new Raster.PolyBuf());
        Random r = new Random(10842);
        for (int i = 0; i < stars.length; i++) {
            double x = r.nextGaussian(), y = r.nextGaussian(), z = r.nextGaussian(), l = Math.sqrt(x * x + y * y + z * z);
            stars[i] = new double[]{x / l, y / l, z / l, 0.4 + r.nextDouble() * 0.6};
        }
    }

    static float curve(float level, float gamma) {
        float f = Math.max(0, Math.min(15, level)) / 15f;
        float b = f / (4 - 3 * f);
        float g = 1 - (float) Math.pow(1 - b, 4);
        return b + (g - b) * gamma;
    }

    public static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    public static double celestialAngle(double dayTime) {
        double d = ((dayTime / 24000.0) - 0.25) % 1.0;
        if (d < 0) d += 1;
        double e = d;
        d = 1 - (Math.cos(d * Math.PI) + 1) / 2;
        return e + (d - e) / 3;
    }

    public void render(GameClient g, float partial) {
        Raster r = raster;
        ClientWorld w = g.world;
        Settings s = g.settings;
        double dayTime = g.dayTime(partial);
        celestial = celestialAngle(dayTime);
        float sb = (float) Math.max(0, Math.min(1, Math.cos(celestial * Math.PI * 2) * 2 + 0.5));
        float rain = g.rainLevel(partial), thunder = g.info != null && g.info.thundering() ? rain : 0;
        sb *= 1 - rain * 5 / 16f;
        sb *= 1 - thunder * 5 / 16f;
        skyBrightness = sb;
        float darken = w.hasSky ? (1 - sb) * 11 : 0;
        // light table
        float gamma = (float) s.brightness;
        float ambient = w.dimension.equals("minecraft:the_nether") ? 0.1f : w.dimension.equals("minecraft:the_end") ? 0.08f : 0.02f;
        float flicker = 1f;
        for (int sk = 0; sk <= 60; sk++) {
            float skl = curve(Math.max(0, sk / 4f - darken), gamma);
            if (!w.hasSky) skl = 0;
            for (int bl = 0; bl <= 60; bl++) {
                float bll = curve(bl / 4f, gamma) * flicker;
                lut[sk * 61 + bl] = Math.min(1f, Math.max(skl, bll) * (1 - ambient) + ambient);
            }
        }
        // camera
        double ex = g.camX, ey = g.camY, ez = g.camZ;
        float fov = (float) s.fov;
        if (g.ps != null && g.ps.sprinting()) fov *= 1.1f;
        if (g.ps != null && g.ps.usingBow()) fov *= 1 - Math.min(1, g.ps.useTicks() / 20f) * 0.15f;
        r.setCamera(ex, ey, ez, g.camYaw, g.camPitch, fov);
        // fog & sky colours
        int skyTop, fog;
        if (w.hasSky) {
            Biome bi = w.biomeAt((int) Math.floor(ex), (int) Math.floor(ez));
            double temp = bi == null ? 0.8 : Math.max(-1, Math.min(2, bi.temperature)) / 3;
            int daySky = java.awt.Color.HSBtoRGB((float) (0.62 - temp * 0.05), (float) (0.5 + temp * 0.1), 1f) & 0xFFFFFF;
            skyTop = mix(0x000000, daySky, sb);
            fog = mix(0x0B0D16, 0xC0D8FF, sb);
            // sunrise / sunset glow toward the sun direction
            double sunHeight = Math.cos(celestial * Math.PI * 2);
            if (Math.abs(sunHeight) < 0.4) {
                double glow = (1 - Math.abs(sunHeight) / 0.4) * 0.6;
                double sunSide = Math.signum(-Math.sin(celestial * Math.PI * 2));
                double facing = Math.max(0, (-Math.sin(Math.toRadians(g.camYaw))) * sunSide);
                fog = mix(fog, 0xFF9A50, glow * (0.3 + 0.7 * facing));
            }
            if (rain > 0) { int gray = mix(0x000000, 0x8090A0, sb); skyTop = mix(skyTop, gray, rain * 0.8); fog = mix(fog, gray, rain * 0.6); }
        } else {
            skyTop = w.fogColor;
            fog = w.fogColor;
        }
        float viewDist = s.renderDistance * 16;
        float fs = viewDist * 0.6f, fe = viewDist * 0.95f;
        if (!w.hasSky) { fs = viewDist * 0.2f; fe = viewDist * 0.8f; }
        if (g.ps != null && g.ps.submerged()) { fog = mix(0x0A1E60, 0x3050C0, sb); skyTop = fog; fs = -4; fe = 28; }
        if (g.ps != null && g.ps.inLava()) { fog = 0xC04000; skyTop = fog; fs = 0; fe = 2.5f; }
        skyColor = skyTop;
        fogColorNow = fog;
        r.fogColor = fog;
        r.fogStart = fs;
        r.fogEnd = fe;
        r.textures = TexTable.array();
        // ---- clear: sky gradient
        int W = r.w, H = r.h;
        java.util.Arrays.fill(r.depth, 0f);
        float hh = H / 2f;
        for (int y = 0; y < H; y++) {
            float dy = r.fy + r.uy * (hh - y) / r.focal;
            float len = (float) Math.sqrt(1 + ((hh - y) / r.focal) * ((hh - y) / r.focal));
            float elev = dy / len;
            int c = elev < 0 && w.hasSky ? mix(fog, mix(0, skyTop, 0.5), Math.min(1, -elev * 2)) : mix(fog, skyTop, Math.min(1, Math.max(0, elev * 2.5 + 0.05)));
            java.util.Arrays.fill(r.color, y * W, y * W + W, c);
        }
        skyBuf.clear();
        if (w.hasSky && !(g.ps != null && g.ps.submerged())) drawCelestial(r, g, sb, rain);
        // ---- terrain
        long t0 = System.nanoTime();
        List<ClientWorld.CChunk> chunks = new ArrayList<>();
        int pcx = (int) Math.floor(ex) >> 4, pcz = (int) Math.floor(ez) >> 4;
        int rd = s.renderDistance;
        List<long[]> vis = new ArrayList<>(); // [dist, chunkIndex, section]
        for (ClientWorld.CChunk c : w.chunks.values()) {
            int dx = c.cx - pcx, dz = c.cz - pcz;
            if (dx * dx + dz * dz > rd * rd + 1) continue;
            int ci = chunks.size();
            chunks.add(c);
            for (int sct = 0; sct < 16; sct++) {
                Mesh m = c.meshes[sct];
                if (m == null || m.n == 0) continue;
                double cxw = c.cx * 16 + 8, cyw = sct * 16 + 8, czw = c.cz * 16 + 8;
                if (!sphereVisible(r, cxw, cyw, czw, 14)) continue;
                double ddx = cxw - ex, ddy = cyw - ey, ddz = czw - ez;
                vis.add(new long[]{(long) (ddx * ddx + ddy * ddy + ddz * ddz), ci, sct});
            }
        }
        vis.sort(Comparator.comparingLong(a -> a[0]));
        sectionsDrawn = vis.size();
        final float[] L = lut;
        List<Callable<Integer>> jobs = new ArrayList<>();
        for (int k = 0; k < projThreads; k++) {
            final int kk = k;
            final Raster.PolyBuf out = opaqueBufs.get(k);
            out.clear();
            jobs.add(() -> {
                float[] px = new float[4], py = new float[4], pz = new float[4], u = new float[4], v = new float[4], b = new float[4];
                int q = 0;
                for (int i = kk; i < vis.size(); i += projThreads) {
                    long[] e = vis.get(i);
                    Mesh m = chunks.get((int) e[1]).meshes[(int) e[2]];
                    q += emit(r, m, 0, m.nOpaque, out, L, ex, ey, ez, px, py, pz, u, v, b, Raster.MODE_OPAQUE);
                }
                return q;
            });
        }
        int quads = 0;
        try {
            for (Future<Integer> f : projPool.invokeAll(jobs)) quads += f.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // ---- entities
        entBuf.clear();
        renderEntities(g, partial);
        // ---- translucent (back to front)
        transBuf.clear();
        float[] px = new float[4], py = new float[4], pz = new float[4], u = new float[4], v = new float[4], b = new float[4];
        for (int i = vis.size() - 1; i >= 0; i--) {
            long[] e = vis.get(i);
            Mesh m = chunks.get((int) e[1]).meshes[(int) e[2]];
            if (m.n > m.nOpaque) quads += emit(r, m, m.nOpaque, m.n, transBuf, L, ex, ey, ez, px, py, pz, u, v, b, Raster.MODE_TRANSLUCENT);
        }
        quadsDrawn = quads;
        // ---- overlays: breaking, outline, particles, rain
        overlayBuf.clear();
        overlays(g, partial);
        projNanos = System.nanoTime() - t0;
        long t1 = System.nanoTime();
        List<Raster.PolyBuf> all = new ArrayList<>();
        all.add(skyBuf);
        all.addAll(opaqueBufs);
        all.add(entBuf);
        all.add(transBuf);
        all.add(overlayBuf);
        polys = 0;
        for (Raster.PolyBuf pb : all) polys += pb.np;
        r.rasterize(all);
        rasterNanos = System.nanoTime() - t1;
        post(g);
    }

    boolean sphereVisible(Raster r, double x, double y, double z, double rad) {
        double dx = x - r.camX, dy = y - r.camY, dz = z - r.camZ;
        double cx = dx * r.rx + dy * r.ry + dz * r.rz, cy = dx * r.ux + dy * r.uy + dz * r.uz, cz = dx * r.fx + dy * r.fy + dz * r.fz;
        if (cz < -rad) return false;
        double kx = (r.w / 2.0) / r.focal, ky = (r.h / 2.0) / r.focal;
        double nx = Math.sqrt(1 + kx * kx), ny = Math.sqrt(1 + ky * ky);
        if (cx - cz * kx > rad * nx) return false;
        if (-cx - cz * kx > rad * nx) return false;
        if (cy - cz * ky > rad * ny) return false;
        if (-cy - cz * ky > rad * ny) return false;
        return cz < r.fogEnd + 30;
    }

    static int emit(Raster r, Mesh m, int from, int to, Raster.PolyBuf out, float[] L, double ex, double ey, double ez,
                    float[] px, float[] py, float[] pz, float[] u, float[] v, float[] b, int mode) {
        int n = 0;
        float[] pos = m.pos, uv = m.uv;
        for (int q = from; q < to; q++) {
            int f = m.face[q];
            int o = q * 12;
            switch (f) {
                case 0: if (ey >= pos[o + 1]) continue; break;
                case 1: if (ey <= pos[o + 1]) continue; break;
                case 2: if (ez >= pos[o + 2]) continue; break;
                case 3: if (ez <= pos[o + 2]) continue; break;
                case 4: if (ex >= pos[o]) continue; break;
                case 5: if (ex <= pos[o]) continue; break;
                default: break;
            }
            float shade = SHADE[f];
            for (int k = 0; k < 4; k++) {
                px[k] = pos[o + k * 3]; py[k] = pos[o + k * 3 + 1]; pz[k] = pos[o + k * 3 + 2];
                u[k] = uv[q * 8 + k * 2]; v[k] = uv[q * 8 + k * 2 + 1];
                int li = q * 4 + k;
                b[k] = L[m.sky[li] * 61 + m.blk[li]] * AO[m.ao[li]] * shade;
            }
            r.project(out, px, py, pz, u, v, b, 4, m.tex[q], m.tint[q], mode);
            n++;
        }
        return n;
    }

    void drawCelestial(Raster r, GameClient g, float sb, float rain) {
        double a = celestial * Math.PI * 2;
        double sx = -Math.sin(a), sy = Math.cos(a);
        float vis = 1 - rain;
        float[] px = new float[4], py = new float[4], pz = new float[4], b = {1, 1, 1, 1};
        float[] u = {0, 15.99f, 15.99f, 0}, v = {0, 0, 15.99f, 15.99f};
        double dist = 100;
        for (int body = 0; body < 2; body++) {
            double dx = body == 0 ? sx : -sx, dy = body == 0 ? sy : -sy;
            double cx = r.camX + dx * dist, cy = r.camY + dy * dist, cz = r.camZ;
            double size = body == 0 ? 15 : 10;
            // quad perpendicular to direction (dx,dy,0): axes (0,0,1) and (-dy,dx,0)
            double ax = -dy * size, ay = dx * size;
            px[0] = (float) (cx - ax); py[0] = (float) (cy - ay); pz[0] = (float) (cz - size);
            px[1] = (float) (cx - ax); py[1] = (float) (cy - ay); pz[1] = (float) (cz + size);
            px[2] = (float) (cx + ax); py[2] = (float) (cy + ay); pz[2] = (float) (cz + size);
            px[3] = (float) (cx + ax); py[3] = (float) (cy + ay); pz[3] = (float) (cz - size);
            if (vis > 0.05f) r.project(skyBuf, px, py, pz, u, v, b, 4, body == 0 ? TexTable.SUN : TexTable.MOON, BlockColors.IDENTITY, Raster.MODE_NODEPTH);
        }
        // stars (direct pixels)
        float starAlpha = (1 - sb * 2) * vis;
        if (starAlpha > 0) {
            double ca = Math.cos(a), sa = Math.sin(a);
            int W = r.w, H = r.h;
            for (double[] st : stars) {
                double x = st[0] * ca - st[1] * sa, y = st[0] * sa + st[1] * ca, z = st[2];
                double cx = x * r.rx + y * r.ry + z * r.rz, cy = x * r.ux + y * r.uy + z * r.uz, cz = x * r.fx + y * r.fy + z * r.fz;
                if (cz <= 0.05) continue;
                int sxp = (int) (W / 2f + cx / cz * r.focal), syp = (int) (H / 2f - cy / cz * r.focal);
                if (sxp < 0 || syp < 0 || sxp >= W || syp >= H) continue;
                int k = (int) (st[3] * starAlpha * 255);
                int d = r.color[syp * W + sxp];
                r.color[syp * W + sxp] = mix(d, 0xFFFFFF, k / 255.0);
            }
        }
    }

    float lightAt(ClientWorld w, double x, double y, double z) {
        int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
        int sk = w.getSkyLight(bx, by, bz) * 4, bl = w.getBlockLight(bx, by, bz) * 4;
        return lut[Math.min(60, sk) * 61 + Math.min(60, bl)];
    }

    void renderEntities(GameClient g, float partial) {
        ClientWorld w = g.world;
        Raster r = raster;
        double maxD = g.settings.renderDistance * 16;
        List<S2C.EntityFrame> list = g.entities;
        for (S2C.EntityFrame f : list) {
            S2C.EntityFrame p = g.prevEntities.getOrDefault(f.id(), f);
            double x = p.x() + (f.x() - p.x()) * partial, y = p.y() + (f.y() - p.y()) * partial, z = p.z() + (f.z() - p.z()) * partial;
            double dx = x - r.camX, dz = z - r.camZ;
            if (dx * dx + dz * dz > maxD * maxD) continue;
            if (!sphereVisible(r, x, y + f.height() / 2, z, Math.max(f.width(), f.height()) + 0.5)) continue;
            EntityType t = f.type() >= 0 && f.type() < EntityTypes.ALL.size() ? EntityTypes.ALL.get(f.type()) : null;
            if (t == null) continue;
            float light = lightAt(w, x, y + 0.5, z);
            if (f.onFire()) light = 1;
            float yaw = lerpAngle(p.yaw(), f.yaw(), partial), hy = lerpAngle(p.headYaw(), f.headYaw(), partial);
            float limb = p.limbSwing() + (f.limbSwing() - p.limbSwing()) * partial, amt = p.limbAmount() + (f.limbAmount() - p.limbAmount()) * partial;
            if (t == EntityTypes.ITEM) {
                int idx = f.data();
                double bob = Math.sin((g.clientTicks + partial) / 10.0 + f.id()) * 0.1 + 0.1;
                Item it = idx >= 0 && idx < Items.ALL.size() ? Items.ALL.get(idx) : null;
                if (it == null) continue;
                int copies = f.data2() > 32 ? 3 : f.data2() > 1 ? 2 : 1;
                for (int k = 0; k < copies; k++) {
                    double ox = k * 0.07, oy = k * 0.05;
                    if (it.block != null && it.block.shape == Block.SHAPE_CUBE)
                        EntityModels.cube(r, entBuf, x + ox, y + bob + oy, z + ox, 0.25f, it.block.baseState, light, BlockColors.IDENTITY, (float) ((g.clientTicks + partial) / 20.0 + f.id()));
                    else
                        EntityModels.sprite(r, entBuf, x + ox, y + bob + 0.2 + oy, z + ox, 0.45f, TexTable.ITEM_BASE + idx, light, BlockColors.IDENTITY, Raster.MODE_OPAQUE);
                }
            } else if (t == EntityTypes.XP_ORB) {
                double bob = Math.sin((g.clientTicks + partial) / 6.0 + f.id()) * 0.05;
                EntityModels.sprite(r, entBuf, x, y + 0.15 + bob, z, 0.3f, TexTable.XP, 1f, BlockColors.IDENTITY, Raster.MODE_OPAQUE);
            } else if (t == EntityTypes.TNT) {
                boolean flash = (f.data() / 5) % 2 == 0;
                float sc = 1;
                if (f.data() < 10) sc = 1 + (10 - f.data()) / 10f * 0.15f;
                EntityModels.cube(r, entBuf, x, y, z, 0.98f * sc, Blocks.base("minecraft:tnt"), light, flash ? 0xFFFFFFFF : BlockColors.IDENTITY, 0);
            } else if (t == EntityTypes.FALLING_BLOCK) {
                EntityModels.cube(r, entBuf, x, y, z, 0.98f, f.data(), light, BlockColors.IDENTITY, 0);
            } else if (t == EntityTypes.ARROW) {
                arrow(r, x, y, z, yaw, f.pitch(), light);
            } else {
                float swing = f.swing() > 0 ? 1 - f.swing() / 6f : 0;
                EntityModels.render(r, entBuf, t, x, y, z, yaw, hy, f.pitch(), limb, amt, f.hurtTime(), f.dead() ? f.deathTime() + partial : 0, f.data(), f.data2(),
                        swing, light, g.clientTicks + partial);
            }
        }
        // the local player in third person
        if (g.perspective != 0 && g.ps != null && !g.ps.dead()) {
            float swing = g.ps.swing() > 0 ? 1 - g.ps.swing() / 6f : 0;
            EntityModels.render(r, entBuf, EntityTypes.PLAYER, g.plX, g.plY, g.plZ, g.yaw, g.yaw, g.pitch, g.ps.limbSwing(), g.ps.limbAmount(), g.ps.hurtTime(), 0, 0, 0,
                    swing, lightAt(w, g.plX, g.plY + 0.5, g.plZ), g.clientTicks + partial);
        }
    }

    void arrow(Raster r, double x, double y, double z, float yaw, float pitch, float light) {
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        double dx = -Math.sin(yr) * Math.cos(pr) * 0.25, dy = -Math.sin(pr) * 0.25, dz = Math.cos(yr) * Math.cos(pr) * 0.25;
        float[] px = new float[4], py = new float[4], pz = new float[4], b = {light, light, light, light};
        float[] u = {0, 15.99f, 15.99f, 0}, v = {6, 6, 9, 9};
        int tex = TexTable.ITEM_BASE + Items.get("minecraft:arrow").index;
        for (int k = 0; k < 2; k++) {
            double ox = k == 0 ? 0.05 : 0, oy = k == 0 ? 0 : 0.05;
            px[0] = (float) (x - dx - ox); py[0] = (float) (y - dy - oy); pz[0] = (float) (z - dz);
            px[1] = (float) (x + dx - ox); py[1] = (float) (y + dy - oy); pz[1] = (float) (z + dz);
            px[2] = (float) (x + dx + ox); py[2] = (float) (y + dy + oy); pz[2] = (float) (z + dz);
            px[3] = (float) (x - dx + ox); py[3] = (float) (y - dy + oy); pz[3] = (float) (z - dz);
            r.project(entBuf, px, py, pz, u, new float[]{4, 4, 12, 12}, b, 4, TexTable.WHITE, 0xFF6B4A22 & 0xFF606060 | 0xFF000000, Raster.MODE_OPAQUE);
        }
    }

    static float lerpAngle(float a, float b, float t) {
        float d = ((b - a) % 360 + 540) % 360 - 180;
        return a + d * t;
    }

    void overlays(GameClient g, float partial) {
        Raster r = raster;
        ClientWorld w = g.world;
        Raycast.Hit hit = g.hit;
        if (hit != null && g.ps != null && !g.ps.dead() && g.ps.gameMode() != 3 && !g.hideGui) {
            int st = w.getBlock(hit.x, hit.y, hit.z);
            List<mc.util.AABB> boxes = new ArrayList<>();
            Raycast.outline(st, hit.x, hit.y, hit.z, boxes);
            for (mc.util.AABB bb : boxes) {
                float e = 0.002f;
                float x0 = (float) bb.minX - e, y0 = (float) bb.minY - e, z0 = (float) bb.minZ - e, x1 = (float) bb.maxX + e, y1 = (float) bb.maxY + e, z1 = (float) bb.maxZ + e;
                int c = 0x70000000;
                r.line(overlayBuf, x0, y0, z0, x1, y0, z0, c); r.line(overlayBuf, x0, y0, z1, x1, y0, z1, c);
                r.line(overlayBuf, x0, y1, z0, x1, y1, z0, c); r.line(overlayBuf, x0, y1, z1, x1, y1, z1, c);
                r.line(overlayBuf, x0, y0, z0, x0, y0, z1, c); r.line(overlayBuf, x1, y0, z0, x1, y0, z1, c);
                r.line(overlayBuf, x0, y1, z0, x0, y1, z1, c); r.line(overlayBuf, x1, y1, z0, x1, y1, z1, c);
                r.line(overlayBuf, x0, y0, z0, x0, y1, z0, c); r.line(overlayBuf, x1, y0, z0, x1, y1, z0, c);
                r.line(overlayBuf, x0, y0, z1, x0, y1, z1, c); r.line(overlayBuf, x1, y0, z1, x1, y1, z1, c);
            }
            // breaking progress
            if (g.ps.digging() && g.ps.digProgress() > 0 && g.ps.digX() == hit.x && g.ps.digY() == hit.y && g.ps.digZ() == hit.z) {
                int stage = Math.min(9, (int) (g.ps.digProgress() * 10));
                float[] px = new float[4], py = new float[4], pz = new float[4], b = {1, 1, 1, 1};
                float[] u = {0, 15.99f, 15.99f, 0}, v = {15.99f, 15.99f, 0, 0};
                for (mc.util.AABB bb : boxes) {
                    for (int f = 0; f < 6; f++) {
                        float e = 0.004f;
                        float[][] q = Mesher.faceCorners(f, (float) bb.minX - e, (float) bb.minY - e, (float) bb.minZ - e, (float) bb.maxX + e, (float) bb.maxY + e, (float) bb.maxZ + e);
                        for (int k = 0; k < 4; k++) { px[k] = q[k][0]; py[k] = q[k][1]; pz[k] = q[k][2]; }
                        r.project(overlayBuf, px, py, pz, u, v, b, 4, TexTable.DESTROY_BASE + stage, BlockColors.IDENTITY, Raster.MODE_TRANSLUCENT);
                    }
                }
            }
        }
        // particles
        float[] px = new float[4], py = new float[4], pz = new float[4], b = new float[4];
        for (Particles.P p : g.particles.list) {
            double x = p.x + p.vx * partial, y = p.y + p.vy * partial, z = p.z + p.vz * partial;
            float light = p.emissive ? 1 : lightAt(w, x, y, z);
            float hs = p.size / 2;
            float hx = r.rx * hs, hz = r.rz * hs, vx = r.ux * hs, vy = r.uy * hs, vz = r.uz * hs;
            px[0] = (float) (x - hx + vx); py[0] = (float) (y + vy); pz[0] = (float) (z - hz + vz);
            px[1] = (float) (x + hx + vx); py[1] = (float) (y + vy); pz[1] = (float) (z + hz + vz);
            px[2] = (float) (x + hx - vx); py[2] = (float) (y - vy); pz[2] = (float) (z + hz - vz);
            px[3] = (float) (x - hx - vx); py[3] = (float) (y - vy); pz[3] = (float) (z - hz - vz);
            for (int k = 0; k < 4; k++) b[k] = light;
            float[] u = {p.u0, p.u0 + p.us - 0.01f, p.u0 + p.us - 0.01f, p.u0}, v = {p.v0, p.v0, p.v0 + p.us - 0.01f, p.v0 + p.us - 0.01f};
            r.project(overlayBuf, px, py, pz, u, v, b, 4, p.tex, p.tint, Raster.MODE_OPAQUE);
        }
        // rain / snow streaks around the camera
        float rain = g.rainLevel(partial);
        if (rain > 0.05f && w.hasSky) {
            Random rnd = new Random(1234);
            double t = (g.clientTicks + partial);
            int cx = (int) Math.floor(r.camX), cy = (int) Math.floor(r.camY), cz = (int) Math.floor(r.camZ);
            float[] u = {0, 1, 1, 0}, v = {0, 0, 15, 15};
            for (int i = 0; i < 260 * rain; i++) {
                int bx = cx + rnd.nextInt(21) - 10, bz = cz + rnd.nextInt(21) - 10;
                // stop at the first light-blocking block from the top (approximated with sky light at camera height)
                double fall = ((t * 0.9 + rnd.nextDouble() * 20) % 20);
                double y = cy + 10 - fall;
                if (w.getSkyLight(bx, (int) Math.floor(y), bz) < 15) continue;
                Biome bi = w.biomeAt(bx, bz);
                boolean snow = bi != null && bi.snowy();
                double x = bx + rnd.nextDouble(), z = bz + rnd.nextDouble();
                float len = snow ? 0.1f : 0.7f, wd = snow ? 0.1f : 0.03f;
                px[0] = (float) (x - r.rx * wd); py[0] = (float) (y + len); pz[0] = (float) (z - r.rz * wd);
                px[1] = (float) (x + r.rx * wd); py[1] = (float) (y + len); pz[1] = (float) (z + r.rz * wd);
                px[2] = (float) (x + r.rx * wd); py[2] = (float) y; pz[2] = (float) (z + r.rz * wd);
                px[3] = (float) (x - r.rx * wd); py[3] = (float) y; pz[3] = (float) (z - r.rz * wd);
                float l = 0.6f + skyBrightness * 0.4f;
                for (int k = 0; k < 4; k++) b[k] = l;
                r.project(overlayBuf, px, py, pz, u, v, b, 4, snow ? TexTable.WHITE : Textures.id("water"), snow ? 0xFFFFFFFF : 0xFF9090C0, Raster.MODE_TRANSLUCENT);
            }
        }
    }

    /** Full-screen post effects on the internal buffer: underwater, portal, fire, damage tint, vignette at night. */
    void post(GameClient g) {
        Raster r = raster;
        int[] c = r.color;
        if (g.ps == null) return;
        int overlay = -1;
        double k = 0;
        if (g.ps.inPortal()) { overlay = 0x9020F0; k = 0.35 + Math.sin(g.clientTicks * 0.2) * 0.05; }
        if (g.ps.hurtTime() > 0) { overlay = 0xC00000; k = g.ps.hurtTime() / 10.0 * 0.3; }
        if (overlay >= 0 && k > 0) for (int i = 0; i < c.length; i++) c[i] = mix(c[i], overlay, k);
        if (g.ps.onFire() && g.perspective == 0) {
            int W = r.w, H = r.h;
            Random rnd = new Random((long) (g.clientTicks / 2));
            for (int x = 0; x < W; x++) {
                int hgt = (int) (H * (0.25 + 0.1 * Math.sin(x * 0.15 + g.clientTicks * 0.5)) + rnd.nextInt(8));
                for (int y = H - hgt; y < H; y++) c[y * W + x] = mix(c[y * W + x], (y - (H - hgt)) < hgt / 3 ? 0xFFA020 : 0xE04010, 0.55);
            }
        }
    }

    public void shutdown() {
        projPool.shutdownNow();
        raster.shutdown();
    }
}
