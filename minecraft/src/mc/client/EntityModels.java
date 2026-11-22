package mc.client;

import mc.content.*;
import mc.util.SplitMix;

import java.util.*;

/** Renders data-driven box models (entities.json "model") with walk/attack/hurt/death animation through the rasteriser. */
public final class EntityModels {
    private EntityModels() { }

    static final class RPart {
        EntityType.Part p;
        int side, front, top;
        int hSide, hFront, hTop;   // red hurt-overlay variants (vanilla: 70% texture + 30% red)
        int wSide, wFront, wTop;   // white flash variants (creeper fuse)
        int legPair = -1, legSide;
        EntityType.Anim anim;
        boolean followsHead, isHead, isRightArm, isLeftArm;
    }

    static final Map<Integer, List<RPart>> MODELS = new HashMap<>();

    static void init() {
        for (EntityType t : EntityTypes.ALL) {
            if (t.model == null || t.model.isEmpty()) continue;
            List<RPart> l = new ArrayList<>();
            for (EntityType.Part p : t.model) {
                RPart r = new RPart();
                r.p = p;
                SplitMix rng = new SplitMix((t.id + p.name).hashCode());
                r.side = TexTable.add(skin(p, rng, false));
                r.top = TexTable.add(skin(p, rng, false));
                r.front = p.face != null && !p.face.isEmpty() ? TexTable.add(skin(p, rng, true)) : r.side;
                int[][] all = TexTable.array();
                r.hSide = TexTable.add(overlay(all[r.side], 0xFF0000, 0.3)); r.hTop = TexTable.add(overlay(all[r.top], 0xFF0000, 0.3));
                r.hFront = r.front == r.side ? r.hSide : TexTable.add(overlay(all[r.front], 0xFF0000, 0.3));
                r.wSide = TexTable.add(overlay(all[r.side], 0xFFFFFF, 0.55)); r.wTop = TexTable.add(overlay(all[r.top], 0xFFFFFF, 0.55));
                r.wFront = r.front == r.side ? r.wSide : TexTable.add(overlay(all[r.front], 0xFFFFFF, 0.55));
                if (t.id.equals("minecraft:spider") && p.name.startsWith("leg")) {
                    int i = Integer.parseInt(p.name.substring(3));
                    r.legPair = i / 2;
                    r.legSide = i % 2 == 0 ? -1 : 1;
                }
                if (t.walk != null) for (EntityType.Anim a : t.walk) if (a.part.equals(p.name)) r.anim = a;
                r.isHead = p.name.equals("head");
                r.followsHead = r.isHead || (t.attachments != null && "head".equals(t.attachments.get(p.name)));
                r.isRightArm = p.name.equals("arm1");
                r.isLeftArm = p.name.equals("arm0");
                l.add(r);
            }
            MODELS.put(t.index, l);
        }
    }

    static int hex(String s) { return Textures.hex(s); }

    static int[] overlay(int[] src, int rgb, double k) {
        int[] o = new int[src.length];
        int R = (rgb >> 16) & 255, G = (rgb >> 8) & 255, B = rgb & 255;
        for (int i = 0; i < src.length; i++) {
            int c = src[i];
            int r = (int) (((c >> 16) & 255) * (1 - k) + R * k), g = (int) (((c >> 8) & 255) * (1 - k) + G * k), b = (int) ((c & 255) * (1 - k) + B * k);
            o[i] = (c & 0xFF000000) | r << 16 | g << 8 | b;
        }
        return o;
    }

    /** Original 16x16 skin for a part face from its colour/pattern; front faces get the face features. */
    static int[] skin(EntityType.Part p, SplitMix r, boolean front) {
        int[] px = new int[256];
        int c = p.color, c2 = p.color2 != 0 ? p.color2 : Textures.shade(p.color, 1.15);
        Textures.VNoise vn = new Textures.VNoise(r, 4);
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++) {
                double n = r.nextDouble();
                int col;
                switch (p.pattern == null ? "noise" : p.pattern) {
                    case "spots": col = vn.at(x, y) > 0.62 ? c2 : c; break;
                    case "wool": col = Textures.shade(c, 0.88 + ((x + y * 2) % 3) * 0.05 + n * 0.06); break;
                    case "creeper": col = n < 0.2 ? Textures.shade(c, 0.7) : n > 0.85 ? Textures.shade(c, 1.25) : (n > 0.6 ? 0xFFDDDDDD & Textures.shade(c, 1.05) | 0xFF000000 : c); break;
                    default: col = Textures.shade(c, 0.9 + n * 0.18);
                }
                px[y * 16 + x] = col | 0xFF000000; // part colours from data carry no alpha
            }
        if (front && p.face != null) {
            for (Map.Entry<String, Object> e : p.face.entrySet()) {
                int fc = hex(e.getValue().toString());
                switch (e.getKey()) {
                    case "eyes":
                        for (int[] q : new int[][]{{3, 6}, {4, 6}, {3, 7}, {4, 7}, {11, 6}, {12, 6}, {11, 7}, {12, 7}}) px[q[1] * 16 + q[0]] = fc;
                        px[6 * 16 + 4] = 0xFFFFFFFF; px[6 * 16 + 12] = 0xFFFFFFFF;
                        break;
                    case "mouth": for (int x = 5; x < 11; x++) px[11 * 16 + x] = fc; break;
                    case "snout":
                        for (int y = 9; y < 13; y++) for (int x = 5; x < 11; x++) px[y * 16 + x] = fc;
                        px[10 * 16 + 6] = 0xFF5A2020; px[10 * 16 + 9] = 0xFF5A2020;
                        break;
                    case "creeper":
                        for (int y = 4; y < 7; y++) for (int x = 2; x < 6; x++) { px[y * 16 + x] = fc; px[y * 16 + x + 8] = fc; }
                        for (int y = 7; y < 13; y++) for (int x = 6; x < 10; x++) px[y * 16 + x] = fc;
                        for (int y = 9; y < 14; y++) { px[y * 16 + 4] = fc; px[y * 16 + 5] = fc; px[y * 16 + 10] = fc; px[y * 16 + 11] = fc; }
                        break;
                    case "spider":
                        for (int[] q : new int[][]{{3, 5}, {4, 5}, {11, 5}, {12, 5}, {5, 7}, {10, 7}, {6, 6}, {9, 6}}) px[q[1] * 16 + q[0]] = fc;
                        break;
                    default: break;
                }
            }
        }
        return px;
    }

    // scratch
    private static final float[] PX = new float[4], PY = new float[4], PZ = new float[4], U = {0, 15.99f, 15.99f, 0}, V = {15.99f, 15.99f, 0, 0}, B = new float[4];

    /**
     * Render one entity model. Angles in degrees except limbSwing. swing = attack progress 0..1.
     */
    public static synchronized void render(Raster r, Raster.PolyBuf out, EntityType t, double x, double y, double z, float bodyYaw, float headYaw,
                                           float pitch, float limbSwing, float limbAmount, int hurt, float deathTicks, int data, float data2,
                                           float swing, float light, double ticks) {
        List<RPart> parts = MODELS.get(t.index);
        if (parts == null) return;
        double by = Math.toRadians(bodyYaw), hy = Math.toRadians(headYaw), hp = Math.toRadians(pitch);
        double cb = Math.cos(by), sb = Math.sin(by);
        boolean creeper = t.id.equals("minecraft:creeper");
        boolean baby = t.category.equals("creature") && (data & 1) != 0;
        double scale = baby ? 0.5 : 1, sxz = 1, sy = 1;
        if (creeper && data > 0) { double[] cs = Animations.creeperScale(data2); sxz = cs[0]; sy = cs[1]; }
        boolean hurtTint = hurt > 0 || deathTicks > 0;
        boolean flash = creeper && data > 0 && ((int) (data2 * 10)) % 2 == 1;
        double roll = Animations.deathRoll(deathTicks);
        boolean zombie = t.id.equals("minecraft:zombie");
        boolean biped = t.part("arm0") != null && t.part("arm1") != null;
        boolean bowAim = t.id.equals("minecraft:skeleton") && (data & 1) != 0;
        boolean sheared = t.id.equals("minecraft:sheep") && (data & 2) != 0;
        boolean grazing = t.id.equals("minecraft:sheep") && (data & 4) != 0;
        double amt = Math.min(1, limbAmount);
        double[] atk = biped && !zombie && !bowAim ? Animations.attackPose(swing, hp) : new double[5];
        double twist = atk[0];
        if (grazing) hp = 0.9 + Math.sin(ticks * 0.9) * 0.12; // sheep lowers its head to eat grass
        for (RPart rp : parts) {
            EntityType.Part p = rp.p;
            double ox = p.ox, oy = p.oy, oz = p.oz, ex = p.sx, ey = p.sy, ez = p.sz;
            if (sheared && p.name.equals("body")) { ox += 0.06; oy += 0.06; oz += 0.06; ex -= 0.12; ey -= 0.12; ez -= 0.12; }
            double ax = 0, ay = 0, az = 0; // local rotation around pivot
            if (rp.legPair >= 0) {
                double[] lg = Animations.spiderLeg(rp.legPair, rp.legSide, limbSwing, amt);
                ay = lg[0]; az = lg[1];
            } else if (rp.anim != null) {
                double a = Animations.walk(rp.anim, limbSwing, amt);
                if (rp.anim.axis == 0) ax = a; else if (rp.anim.axis == 1) ay = a; else az = a;
            }
            boolean upper = biped && (p.name.equals("body") || rp.isRightArm || rp.isLeftArm);
            if (rp.isRightArm || rp.isLeftArm) {
                int side = rp.isRightArm ? 1 : -1;
                if (zombie) {
                    double[] za = Animations.zombieArms(swing);
                    ax = za[0]; ay = side * za[1]; az = 0;
                } else if (bowAim) {
                    ax = -Math.PI / 2 + hp; ay = rp.isRightArm ? 0.1 : -0.5; az = 0;
                } else if (rp.isRightArm) {
                    ax += atk[1]; ay += atk[2]; az += atk[3];
                } else {
                    ax += atk[4];
                }
                az += Animations.idleSway(ticks, side);
                ax += Animations.idleSwayX(ticks, side);
            }
            boolean head = rp.followsHead;
            double pvx = p.px, pvy = p.py, pvz = p.pz;
            if (head) { EntityType.Part hpp = t.part("head"); if (hpp != null) { pvx = hpp.px; pvy = hpp.py; pvz = hpp.pz; } }
            double local = head && baby ? 1.5 : 1; // baby heads are proportionally bigger (vanilla AgeableModel)
            int sideTex = rp.side, frontTex = rp.front, topTex = rp.top;
            if (flash) { sideTex = rp.wSide; frontTex = rp.wFront; topTex = rp.wTop; }
            else if (hurtTint) { sideTex = rp.hSide; frontTex = rp.hFront; topTex = rp.hTop; }
            double cax = Math.cos(ax), sax = Math.sin(ax), cay = Math.cos(ay), say = Math.sin(ay), caz = Math.cos(az), saz = Math.sin(az);
            double chp = Math.cos(hp), shp = Math.sin(hp), rel = hy - by, crl = Math.cos(rel), srl = Math.sin(rel);
            double ctw = Math.cos(twist), stw = Math.sin(twist), cro = Math.cos(roll), sro = Math.sin(roll);
            for (int f = 0; f < 6; f++) {
                float[][] q = Mesher.faceCorners(f, (float) ox, (float) oy, (float) oz, (float) (ox + ex), (float) (oy + ey), (float) (oz + ez));
                for (int k = 0; k < 4; k++) {
                    double mx = (q[k][0] - pvx) * local, my = (q[k][1] - pvy) * local, mz = (q[k][2] - pvz) * local;
                    // part rotation: x (pitch), y, z  (convention: negative x rotation swings forward, i.e. towards -z)
                    if (head) { double ny = my * chp + mz * shp, nz = -my * shp + mz * chp; my = ny; mz = nz; }
                    if (ax != 0) { double ny = my * cax + mz * sax, nz = -my * sax + mz * cax; my = ny; mz = nz; }
                    if (ay != 0) { double nx = mx * cay + mz * say, nz = -mx * say + mz * cay; mx = nx; mz = nz; }
                    if (az != 0) { double nx = mx * caz - my * saz, ny = mx * saz + my * caz; mx = nx; my = ny; }
                    if (head) { double nx = mx * crl - mz * srl, nz = mx * srl + mz * crl; mx = nx; mz = nz; }
                    mx += pvx; my += pvy; mz += pvz;
                    if (upper && twist != 0) { double nx = mx * ctw + mz * stw, nz = -mx * stw + mz * ctw; mx = nx; mz = nz; }
                    if (roll != 0) { double nx = mx * cro - my * sro, ny = mx * sro + my * cro; mx = nx; my = ny; }
                    mx *= scale * sxz; mz *= scale * sxz; my *= scale * sy;
                    double wx = -cb * mx + sb * mz, wz = -sb * mx - cb * mz;
                    PX[k] = (float) (x + wx); PY[k] = (float) (y + my); PZ[k] = (float) (z + wz);
                }
                float shade = f == 1 ? 1f : f == 0 ? 0.5f : (f == 2 || f == 3) ? 0.8f : 0.65f;
                for (int k = 0; k < 4; k++) B[k] = light * shade;
                int tex = f == 2 ? frontTex : f == 1 ? topTex : sideTex;
                r.project(out, PX, PY, PZ, U, V, B, 4, tex, BlockColors.IDENTITY, Raster.MODE_OPAQUE);
            }
        }
    }

    /** Camera-facing sprite (items, xp orbs, particles). */
    public static void sprite(Raster r, Raster.PolyBuf out, double x, double y, double z, float size, int tex, float light, int tint, int mode) {
        float hx = r.rx * size / 2, hz = r.rz * size / 2;
        float vx = r.ux * size / 2, vy = r.uy * size / 2, vz = r.uz * size / 2;
        PX[0] = (float) (x - hx + vx); PY[0] = (float) (y + vy); PZ[0] = (float) (z - hz + vz);
        PX[1] = (float) (x + hx + vx); PY[1] = (float) (y + vy); PZ[1] = (float) (z + hz + vz);
        PX[2] = (float) (x + hx - vx); PY[2] = (float) (y - vy); PZ[2] = (float) (z + hz - vz);
        PX[3] = (float) (x - hx - vx); PY[3] = (float) (y - vy); PZ[3] = (float) (z - hz - vz);
        // u along camera right is mirrored (right vector points to screen right)
        float[] u = {0, 15.99f, 15.99f, 0}, v = {0, 0, 15.99f, 15.99f};
        for (int k = 0; k < 4; k++) B[k] = light;
        r.project(out, PX, PY, PZ, u, v, B, 4, tex, tint, mode);
    }

    /** Textured axis-aligned cube (tnt, falling blocks, dropped block items). */
    public static void cube(Raster r, Raster.PolyBuf out, double x, double y, double z, float size, int state, float light, int tint, float spin) {
        Block b = Blocks.block(state);
        double c = Math.cos(spin), s = Math.sin(spin);
        float h = size / 2;
        for (int f = 0; f < 6; f++) {
            float[][] q = Mesher.faceCorners(f, -h, 0, -h, h, size, h);
            for (int k = 0; k < 4; k++) {
                double mx = q[k][0], mz = q[k][2];
                PX[k] = (float) (x + mx * c - mz * s); PY[k] = (float) (y + q[k][1]); PZ[k] = (float) (z + mx * s + mz * c);
            }
            float shade = f == 1 ? 1f : f == 0 ? 0.5f : (f == 2 || f == 3) ? 0.8f : 0.65f;
            for (int k = 0; k < 4; k++) B[k] = light * shade;
            int tex = Mesher.faceTex(state, b, f);
            float[] u = {0, 15.99f, 15.99f, 0}, v = {15.99f, 15.99f, 0, 0};
            r.project(out, PX, PY, PZ, u, v, B, 4, tex, tint, Raster.MODE_OPAQUE);
        }
    }
}
