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
                px[y * 16 + x] = col;
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
        double cb = Math.cos(by), sb = Math.sin(by), ch = Math.cos(hy), sh = Math.sin(hy);
        double scale = 1, sxz = 1;
        if (t.category.equals("creature") && (data & 1) != 0) scale = 0.5;
        if (t.id.equals("minecraft:creeper") && data > 0) sxz = 1 + data2 * 0.25;
        int tint = BlockColors.IDENTITY;
        if (hurt > 0 || deathTicks > 0) tint = 0xFF804040 | 0x800000;
        if (t.id.equals("minecraft:creeper") && data > 0 && ((int) (data2 * 10)) % 2 == 1) tint = 0xFFFFFFFF;
        double roll = Animations.deathRoll(deathTicks);
        boolean zombieArms = t.id.equals("minecraft:zombie");
        boolean bowAim = t.id.equals("minecraft:skeleton") && (data & 1) != 0;
        boolean sheared = t.id.equals("minecraft:sheep") && (data & 2) != 0;
        for (RPart rp : parts) {
            EntityType.Part p = rp.p;
            double ox = p.ox, oy = p.oy, oz = p.oz, ex = p.sx, ey = p.sy, ez = p.sz;
            if (sheared && p.name.equals("body")) { ox += 0.06; oy += 0.06; oz += 0.06; ex -= 0.12; ey -= 0.12; ez -= 0.12; }
            double ax = 0, ay = 0, az = 0; // local rotation around pivot
            if (rp.anim != null) {
                double a = Animations.walk(rp.anim, limbSwing, limbAmount);
                if (rp.anim.axis == 0) ax = a; else if (rp.anim.axis == 1) ay = a; else az = a;
            }
            if (rp.isRightArm || rp.isLeftArm) {
                int side = rp.isRightArm ? 1 : -1;
                az += Animations.idleSway(ticks, side);
                if (rp.isRightArm && swing > 0) ax += Animations.attackSwing(swing);
                if (bowAim) { ax = -Math.PI / 2 + hp; ay = side * 0.1; }
                if (zombieArms && swing > 0) ax += Animations.attackSwing(swing) * 0.5;
            }
            boolean head = rp.followsHead;
            double pvx = p.px, pvy = p.py, pvz = p.pz;
            if (head) { EntityType.Part hpp = t.part("head"); if (hpp != null) { pvx = hpp.px; pvy = hpp.py; pvz = hpp.pz; } }
            int sideTex = rp.side, frontTex = rp.front, topTex = rp.top;
            for (int f = 0; f < 6; f++) {
                float[][] q = Mesher.faceCorners(f, (float) ox, (float) oy, (float) oz, (float) (ox + ex), (float) (oy + ey), (float) (oz + ez));
                for (int k = 0; k < 4; k++) {
                    double mx = q[k][0] - pvx, my = q[k][1] - pvy, mz = q[k][2] - pvz;
                    // part rotation: x (pitch), y, z  (convention: negative x rotation swings forward, i.e. towards -z)
                    if (head) {
                        double cp = Math.cos(hp), sp = Math.sin(hp);
                        double ny = my * cp + mz * sp, nz = -my * sp + mz * cp;
                        my = ny; mz = nz;
                    }
                    if (ax != 0) { double c = Math.cos(ax), s = Math.sin(ax); double ny = my * c + mz * s, nz = -my * s + mz * c; my = ny; mz = nz; }
                    if (ay != 0) { double c = Math.cos(ay), s = Math.sin(ay); double nx = mx * c + mz * s, nz = -mx * s + mz * c; mx = nx; mz = nz; }
                    if (az != 0) { double c = Math.cos(az), s = Math.sin(az); double nx = mx * c - my * s, ny = mx * s + my * c; mx = nx; my = ny; }
                    double wx, wz, wy;
                    if (head) {
                        // head rotates by (headYaw - bodyYaw) around the pivot, expressed in body space
                        double rel = hy - by, c = Math.cos(rel), s = Math.sin(rel);
                        double nx = mx * c - mz * s, nz = mx * s + mz * c;
                        mx = nx; mz = nz;
                    }
                    mx += pvx; my += pvy; mz += pvz;
                    if (roll != 0) { double c = Math.cos(roll), s = Math.sin(roll); double nx = mx * c - my * s, ny = mx * s + my * c; mx = nx; my = ny; }
                    mx *= scale * sxz; mz *= scale * sxz; my *= scale;
                    wx = -cb * mx + sb * mz;
                    wz = -sb * mx - cb * mz;
                    wy = my;
                    PX[k] = (float) (x + wx); PY[k] = (float) (y + wy); PZ[k] = (float) (z + wz);
                }
                float shade = f == 1 ? 1f : f == 0 ? 0.5f : (f == 2 || f == 3) ? 0.8f : 0.65f;
                for (int k = 0; k < 4; k++) B[k] = light * shade;
                int tex = f == 2 ? frontTex : f == 1 ? topTex : sideTex;
                r.project(out, PX, PY, PZ, U, V, B, 4, tex, tint, Raster.MODE_OPAQUE);
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
