package mc.client;

import mc.content.*;
import mc.net.S2C;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * First-person hand / held item, rendered as real 3D geometry through the rasteriser (after the world, with a cleared
 * depth buffer and a fixed 70 degree FOV, like vanilla). The pose chain follows vanilla's ItemInHandRenderer:
 * equip (lower/raise when the held item changes or after an attack), swing arc, view-bob, arm lag behind camera
 * rotation, eating and bow-drawing poses. Flat items are extruded one pixel thick with per-pixel edge faces.
 */
public final class FirstPerson {
    // ------------------------------------------------------------------ tiny affine matrix stack (vanilla MatrixStack)
    static final class M {
        // row-major 3x4: [m00 m01 m02 m03; m10 ...; m20 ...]
        double[] a = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0};

        M copy() { M m = new M(); m.a = a.clone(); return m; }

        void mul(double[] b) { // this = this * b (b is 3x4 affine)
            double[] r = new double[12];
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) r[i * 4 + j] = a[i * 4] * b[j] + a[i * 4 + 1] * b[4 + j] + a[i * 4 + 2] * b[8 + j];
                r[i * 4 + 3] = a[i * 4] * b[3] + a[i * 4 + 1] * b[7] + a[i * 4 + 2] * b[11] + a[i * 4 + 3];
            }
            a = r;
        }

        M t(double x, double y, double z) { mul(new double[]{1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z}); return this; }

        M s(double x, double y, double z) { mul(new double[]{x, 0, 0, 0, 0, y, 0, 0, 0, 0, z, 0}); return this; }

        M rx(double deg) { double r = Math.toRadians(deg), c = Math.cos(r), s = Math.sin(r); mul(new double[]{1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0}); return this; }

        M ry(double deg) { double r = Math.toRadians(deg), c = Math.cos(r), s = Math.sin(r); mul(new double[]{c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0}); return this; }

        M rz(double deg) { double r = Math.toRadians(deg), c = Math.cos(r), s = Math.sin(r); mul(new double[]{c, -s, 0, 0, s, c, 0, 0, 0, 0, 1, 0}); return this; }

        /** vanilla Quaternion(x, y, z, degrees) == Rx * Ry * Rz */
        M euler(double x, double y, double z) { return rx(x).ry(y).rz(z); }

        // transform to raster space: view (x right, y up, -z forward) -> camera at origin, yaw 0 (right = -x, forward = +z)
        float X(double x, double y, double z) { return (float) -(a[0] * x + a[1] * y + a[2] * z + a[3]); }

        float Y(double x, double y, double z) { return (float) (a[4] * x + a[5] * y + a[6] * z + a[7]); }

        float Z(double x, double y, double z) { return (float) -(a[8] * x + a[9] * y + a[10] * z + a[11]); }
    }

    // ------------------------------------------------------------------ per-tick state
    float height = 0, oHeight = 0;          // vanilla mainHandHeight (1 = fully raised)
    float offHeight = 0, oOffHeight = 0;
    ItemStack shown, shownOff;              // the item currently drawn (swaps while the hand is lowered)
    boolean shownInit;
    float armYaw, armPitch, oArmYaw, oArmPitch;
    boolean armInit;

    static boolean same(ItemStack a, ItemStack b) {
        if (a == null || b == null) return a == b;
        return a.item == b.item;
    }

    /** 20 Hz update (call once per client tick while in game). */
    public void tick(GameClient g) {
        S2C.PlayerState ps = g.ps;
        if (ps == null) return;
        ItemStack held = ps.main()[ps.selected()], off = ps.offhand();
        if (!shownInit) { shown = held; shownOff = off; shownInit = true; height = 1; offHeight = 1; }
        oHeight = height;
        oOffHeight = offHeight;
        float str = Math.max(0, Math.min(1, ps.attackStrength()));
        float target = same(shown, held) ? str * str * str : 0;
        height += Math.max(-0.4f, Math.min(0.4f, target - height));
        if (height < 0.1f) shown = held;
        else if (same(shown, held)) shown = held; // count/damage changes without re-equip
        float tOff = same(shownOff, off) ? 1 : 0;
        offHeight += Math.max(-0.4f, Math.min(0.4f, tOff - offHeight));
        if (offHeight < 0.1f) shownOff = off;
        else if (same(shownOff, off)) shownOff = off;
        if (!armInit) { armYaw = g.yaw; armPitch = g.pitch; armInit = true; }
        oArmYaw = armYaw;
        oArmPitch = armPitch;
        armPitch += (g.pitch - armPitch) * 0.5f;
        armYaw += (g.yaw - armYaw) * 0.5f;
    }

    public void reset() { shownInit = false; armInit = false; }

    // ------------------------------------------------------------------ render
    final Raster.PolyBuf buf = new Raster.PolyBuf();
    private final float[] PX = new float[4], PY = new float[4], PZ = new float[4], B = new float[4];
    private static final float[] U = {0, 15.99f, 15.99f, 0}, V = {15.99f, 15.99f, 0, 0};

    public void render(Renderer rd, GameClient g, float partial) {
        S2C.PlayerState ps = g.ps;
        if (ps == null || g.perspective != 0 || g.hideGui || ps.dead() || ps.sleeping() || ps.gameMode() == 3) return;
        if (g.screen == GameClient.Screen.CONTAINER && false) return;
        if (!shownInit) tick(g);
        Raster r = rd.raster;
        // save camera / fog
        double cx = r.camX, cy = r.camY, cz = r.camZ;
        float rx = r.rx, ry = r.ry, rz = r.rz, ux = r.ux, uy = r.uy, uz = r.uz, fx = r.fx, fy = r.fy, fz = r.fz, focal = r.focal;
        float fs = r.fogStart, fe = r.fogEnd;
        r.setCamera(0, 0, 0, 0, 0, 70);
        r.fogStart = 1e6f;
        r.fogEnd = 2e6f;
        buf.clear();
        float light = rd.lightAt(g.world, g.plX, g.camY, g.plZ);
        if (ps.onFire()) light = 1;

        M base = new M();
        // view bobbing applied to the hand (vanilla renderItemInHand -> bobView)
        if (g.settings.viewBobbing && !ps.flying()) {
            double wd = -(g.walkDistO + (g.walkDistC - g.walkDistO) * partial);
            double bb = g.bobO + (g.bobC - g.bobO) * partial;
            base.t(Math.sin(wd * Math.PI) * bb * 0.5, -Math.abs(Math.cos(wd * Math.PI) * bb), 0);
            base.rz(Math.sin(wd * Math.PI) * bb * 3);
            base.rx(Math.abs(Math.cos(wd * Math.PI - 0.2) * bb) * 5);
        }
        // hand lags behind camera rotation
        float ap = oArmPitch + (armPitch - oArmPitch) * partial, ay = oArmYaw + (armYaw - oArmYaw) * partial;
        base.rx((g.pitch - ap) * 0.1);
        base.ry((g.yaw - ay) * 0.1);

        float swing = ps.swing() > 0 ? Math.max(0, Math.min(1, 1 - (ps.swing() - partial) / 6f)) : 0;
        float equip = 1 - (oHeight + (height - oHeight) * partial);
        ItemStack main = shown;
        if (main == null) arm(r, base.copy(), swing, equip, light);
        else item(r, base.copy(), main, 1, swing, equip, ps, partial, light, true);
        if (shownOff != null) {
            float eqo = 1 - (oOffHeight + (offHeight - oOffHeight) * partial);
            item(r, base.copy(), shownOff, -1, 0, eqo, ps, partial, light, false);
        }
        java.util.Arrays.fill(r.depth, 0f);
        r.rasterize(List.of(buf));
        // restore
        r.camX = cx; r.camY = cy; r.camZ = cz;
        r.rx = rx; r.ry = ry; r.rz = rz; r.ux = ux; r.uy = uy; r.uz = uz; r.fx = fx; r.fy = fy; r.fz = fz; r.focal = focal;
        r.fogStart = fs;
        r.fogEnd = fe;
    }

    /** Empty right hand (vanilla renderPlayerArm). */
    void arm(Raster r, M m, float swing, float equip, float light) {
        double f = 1;
        double f1 = Math.sqrt(swing), f2 = -0.3 * Math.sin(f1 * Math.PI), f3 = 0.4 * Math.sin(f1 * Math.PI * 2), f4 = -0.4 * Math.sin(swing * Math.PI);
        m.t(f * (f2 + 0.64000005), f3 - 0.6 + equip * -0.6, f4 - 0.71999997);
        m.ry(f * 45);
        double f5 = Math.sin(swing * swing * Math.PI), f6 = Math.sin(f1 * Math.PI);
        m.ry(f * f6 * 70);
        m.rz(f * f5 * -20);
        m.t(f * -1, 3.6, 3.5);
        m.rz(f * 120);
        m.rx(200);
        m.ry(f * -135);
        m.t(f * 5.6, 0, 0);
        // player right arm: pivot (-5, 2, 0) px, box (-3,-2,-2) size 4x12x4 px, vanilla model space (y down)
        m.t(-5 / 16.0, 2 / 16.0, 0);
        int[] tex = armTextures();
        box(r, m, -3 / 16.0, -2 / 16.0, -2 / 16.0, 1 / 16.0, 10 / 16.0, 2 / 16.0, tex, light);
    }

    int[] armTex;

    int[] armTextures() {
        if (armTex != null) return armTex;
        List<EntityModels.RPart> parts = EntityModels.MODELS.get(EntityTypes.PLAYER.index);
        int side = TexTable.WHITE, top = TexTable.WHITE;
        if (parts != null) for (EntityModels.RPart p : parts) if (p.isRightArm) { side = p.side; top = p.top; }
        armTex = new int[]{side, top};
        return armTex;
    }

    /** Axis-aligned box in the current (y-down model) space with per-face shading. */
    void box(Raster r, M m, double x0, double y0, double z0, double x1, double y1, double z1, int[] tex, float light) {
        for (int f = 0; f < 6; f++) {
            float[][] q = Mesher.faceCorners(f, (float) x0, (float) y0, (float) z0, (float) x1, (float) y1, (float) z1);
            for (int k = 0; k < 4; k++) { PX[k] = m.X(q[k][0], q[k][1], q[k][2]); PY[k] = m.Y(q[k][0], q[k][1], q[k][2]); PZ[k] = m.Z(q[k][0], q[k][1], q[k][2]); }
            float shade = f == 1 ? 0.55f : f == 0 ? 1f : (f == 2 || f == 3) ? 0.8f : 0.7f; // y is down here
            for (int k = 0; k < 4; k++) B[k] = light * shade;
            r.project(buf, PX, PY, PZ, U, V, B, 4, f <= 1 ? tex[1] : tex[0], BlockColors.IDENTITY, Raster.MODE_OPAQUE);
        }
    }

    /** Held item (vanilla renderArmWithItem) for side i (+1 main/right, -1 off/left). */
    void item(Raster r, M m, ItemStack st, int i, float swing, float equip, S2C.PlayerState ps, float partial, float light, boolean mainHand) {
        Item it = st.item;
        boolean using = mainHand && ps.useTicks() > 0;
        boolean eating = using && (it.isFood() || "drink_milk".equals(it.use));
        boolean bow = mainHand && ps.usingBow();
        if (eating) {
            m.t(i * 0.56, -0.52 + equip * -0.6, -0.72);
            int dur = it.isFood() && it.eatTicks > 0 ? it.eatTicks : 32;
            double f = (dur - ps.useTicks()) - partial + 1, f1 = f / dur;
            if (f1 < 0.8) m.t(0, Math.abs(Math.cos(f / 4 * Math.PI) * 0.1), 0);
            double f3 = 1 - Math.pow(Math.max(0, f1), 27);
            m.t(f3 * 0.6 * i, f3 * -0.5, 0);
            m.ry(i * f3 * 90);
            m.rx(f3 * 10);
            m.rz(i * f3 * 30);
        } else if (bow) {
            m.t(i * 0.56, -0.52 + equip * -0.6, -0.72);
            m.t(i * -0.2785682, 0.18344387, 0.15731531);
            m.rx(-13.935);
            m.ry(i * 35.3);
            m.rz(i * -9.785);
            double f7 = ps.useTicks() + partial, f11 = f7 / 20;
            f11 = Math.min(1, (f11 * f11 + f11 * 2) / 3);
            if (f11 > 0.1) m.t(0, Math.sin((f7 - 0.1) * 1.3) * (f11 - 0.1) * 0.004, 0);
            m.t(0, 0, f11 * 0.04);
            m.s(1, 1, 1 + f11 * 0.2);
            m.ry(-i * 45);
        } else {
            double sq = Math.sqrt(swing);
            m.t(i * -0.4 * Math.sin(sq * Math.PI), 0.2 * Math.sin(sq * Math.PI * 2), -0.2 * Math.sin(swing * Math.PI));
            m.t(i * 0.56, -0.52 + equip * -0.6, -0.72);
            double f = Math.sin(swing * swing * Math.PI);
            m.ry(i * (45 + f * -20));
            double f1 = Math.sin(sq * Math.PI);
            m.rz(i * f1 * -20);
            m.rx(f1 * -80);
            m.ry(i * -45);
        }
        boolean block = it.block != null && "block".equals(it.iconShape) && it.block.shape != Block.SHAPE_CROSS && it.block.shape != Block.SHAPE_TORCH;
        if (block) {
            // block/block.json firstperson_righthand: rotation [0, 45, 0], scale 0.40
            m.euler(0, i * 45, 0);
            m.s(0.4, 0.4, 0.4);
            m.t(-0.5, -0.5, -0.5);
            cube(r, m, it.block, light);
        } else {
            // item/generated firstperson_righthand: rotation [0, -90, 25], translation [1.13, 3.2, 1.13], scale 0.68
            m.t(i * 1.13 / 16, 3.2 / 16, 1.13 / 16);
            m.euler(0, i * 90, i * 25); // sign matched to vanilla's on-screen result (blade points up toward the crosshair)
            m.s(0.68, 0.68, 0.68);
            m.t(-0.5, -0.5, -0.5);
            flat(r, m, TexTable.ITEM_BASE + it.index, light);
        }
    }

    void cube(Raster r, M m, Block b, float light) {
        int state = b.baseState;
        double y1 = b.shape == Block.SHAPE_SLAB ? 0.5 : b.shape == Block.SHAPE_SNOW ? 0.125 : 1;
        int tint = BlockColors.itemTint(b);
        if (BlockColors.mode(b) != 0 && tint == BlockColors.IDENTITY) tint = -1; // grass/foliage: default plains tint below
        for (int f = 0; f < 6; f++) {
            float[][] q = Mesher.faceCorners(f, 0, 0, 0, 1, (float) y1, 1);
            for (int k = 0; k < 4; k++) { PX[k] = m.X(q[k][0], q[k][1], q[k][2]); PY[k] = m.Y(q[k][0], q[k][1], q[k][2]); PZ[k] = m.Z(q[k][0], q[k][1], q[k][2]); }
            float shade = f == 1 ? 1f : f == 0 ? 0.5f : (f == 2 || f == 3) ? 0.8f : 0.6f;
            for (int k = 0; k < 4; k++) B[k] = light * shade;
            int t = tint;
            if (t == -1) t = BlockColors.tint(b, f, Biomes.ALL.isEmpty() ? null : Biomes.get("minecraft:plains"));
            float[] v = y1 < 1 && f >= 2 ? new float[]{15.99f, 15.99f, (float) (16 - y1 * 16), (float) (16 - y1 * 16)} : V;
            r.project(buf, PX, PY, PZ, U, v, B, 4, Mesher.faceTex(state, b, f), t, Raster.MODE_OPAQUE);
        }
    }

    // flat item: front/back quads + one thin face per exposed pixel edge (cached per texture)
    static final Map<Integer, float[]> EDGES = new HashMap<>();

    static float[] edges(int tex) {
        return EDGES.computeIfAbsent(tex, k -> {
            int[] px = TexTable.array()[k];
            float[] out = new float[256 * 4 * 6];
            int n = 0;
            for (int y = 0; y < 16; y++)
                for (int x = 0; x < 16; x++) {
                    if ((px[y * 16 + x] >>> 24) < 128) continue;
                    int[][] nb = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
                    for (int d = 0; d < 4; d++) {
                        int nx = x + nb[d][0], ny = y + nb[d][1];
                        boolean open = nx < 0 || ny < 0 || nx > 15 || ny > 15 || (px[ny * 16 + nx] >>> 24) < 128;
                        if (!open) continue;
                        out[n++] = x; out[n++] = y; out[n++] = d; out[n++] = 0; out[n++] = 0; out[n++] = 0;
                    }
                }
            return java.util.Arrays.copyOf(out, n);
        });
    }

    void flat(Raster r, M m, int tex, float light) {
        double z0 = 7.5 / 16, z1 = 8.5 / 16;
        // front (+z) and back (-z)
        quad(r, m, 0, 0, z1, 1, 0, z1, 1, 1, z1, 0, 1, z1, new float[]{0, 15.99f, 15.99f, 0}, new float[]{15.99f, 15.99f, 0, 0}, tex, light);
        quad(r, m, 1, 0, z0, 0, 0, z0, 0, 1, z0, 1, 1, z0, new float[]{15.99f, 0, 0, 15.99f}, new float[]{15.99f, 15.99f, 0, 0}, tex, light * 0.85f);
        float[] e = edges(tex);
        for (int j = 0; j < e.length; j += 6) {
            int x = (int) e[j], y = (int) e[j + 1], d = (int) e[j + 2];
            double xa = x / 16.0, xb = (x + 1) / 16.0, ya = (15 - y) / 16.0, yb = (16 - y) / 16.0;
            float uc = x + 0.5f, vc = y + 0.5f;
            float[] uu = {uc, uc, uc, uc}, vv = {vc, vc, vc, vc};
            float sh = d < 2 ? 0.7f : d == 2 ? 1f : 0.55f;
            switch (d) {
                case 0: quad(r, m, xa, ya, z0, xa, ya, z1, xa, yb, z1, xa, yb, z0, uu, vv, tex, light * sh); break;
                case 1: quad(r, m, xb, ya, z1, xb, ya, z0, xb, yb, z0, xb, yb, z1, uu, vv, tex, light * sh); break;
                case 2: quad(r, m, xa, yb, z1, xb, yb, z1, xb, yb, z0, xa, yb, z0, uu, vv, tex, light * sh); break;
                default: quad(r, m, xa, ya, z0, xb, ya, z0, xb, ya, z1, xa, ya, z1, uu, vv, tex, light * sh); break;
            }
        }
    }

    void quad(Raster r, M m, double x0, double y0, double z0, double x1, double y1, double z1, double x2, double y2, double z2,
              double x3, double y3, double z3, float[] u, float[] v, int tex, float light) {
        PX[0] = m.X(x0, y0, z0); PY[0] = m.Y(x0, y0, z0); PZ[0] = m.Z(x0, y0, z0);
        PX[1] = m.X(x1, y1, z1); PY[1] = m.Y(x1, y1, z1); PZ[1] = m.Z(x1, y1, z1);
        PX[2] = m.X(x2, y2, z2); PY[2] = m.Y(x2, y2, z2); PZ[2] = m.Z(x2, y2, z2);
        PX[3] = m.X(x3, y3, z3); PY[3] = m.Y(x3, y3, z3); PZ[3] = m.Z(x3, y3, z3);
        for (int k = 0; k < 4; k++) B[k] = Math.min(1f, light);
        r.project(buf, PX, PY, PZ, u, v, B, 4, tex, BlockColors.IDENTITY, Raster.MODE_OPAQUE);
    }
}
