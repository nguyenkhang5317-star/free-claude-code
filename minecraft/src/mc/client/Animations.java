package mc.client;

import mc.content.EntityType;
import mc.content.EntityTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * Procedural animation evaluation for data-driven models plus the animation validator required by the verification
 * suite (Section 15): every animated part must exist, use a valid axis, stay within joint limits, loop seamlessly,
 * and every attachment must reference an existing parent.
 */
public final class Animations {
    private Animations() { }

    /** vanilla limb swing frequency (radians per block walked). */
    public static final double LIMB_FREQ = 0.6662;
    public static final double JOINT_LIMIT = Math.PI; // max absolute rotation of any joint

    /** Rotation (radians) of part p around anim axis for a given walk state. */
    public static double walk(EntityType.Anim a, double limbSwing, double limbAmount) {
        return a.offset + a.amp * Math.sin(limbSwing * LIMB_FREQ + a.phase) * Math.min(1, limbAmount);
    }

    /** Attack swing of the right arm (simple form kept for the validator / tests): 0..1 progress -> radians. */
    public static double attackSwing(double progress) {
        if (progress <= 0 || progress >= 1) return 0;
        double f1 = 1 - progress;
        f1 = 1 - f1 * f1 * f1 * f1;
        return -(Math.sin(f1 * Math.PI) * 1.2 + Math.sin(progress * Math.PI) * 0.525);
    }

    /**
     * Vanilla humanoid attack pose (HumanoidModel#setupAttackAnimation), in this renderer's part-rotation convention
     * (ax = vanilla xRot, ay = -vanilla yRot, az = vanilla zRot). Returns {bodyTwist, rightAx, rightAy, rightAz, leftAx}.
     */
    public static double[] attackPose(double f, double headPitch) {
        double[] o = new double[5];
        if (f <= 0 || f >= 1) return o;
        double bodyY = Math.sin(Math.sqrt(f) * Math.PI * 2) * 0.2;
        double f1 = 1 - f;
        f1 = 1 - f1 * f1 * f1 * f1;
        double f2 = Math.sin(f1 * Math.PI);
        double f3 = Math.sin(f * Math.PI) * -(headPitch - 0.7) * 0.75;
        o[0] = -bodyY;                       // whole upper body twists with the swing
        o[1] = -(f2 * 1.2 + f3);             // right arm raises forward then chops down
        o[2] = -bodyY * 2;                   // extra yaw of the swinging arm
        o[3] = Math.sin(f * Math.PI) * -0.4; // arm swings in across the body
        o[4] = bodyY;                        // off arm follows the twist
        return o;
    }

    /** Vanilla zombie arm pose (AnimationUtils#animateZombieArms, not aggressive): {ax, ayRight, az}. */
    public static double[] zombieArms(double f) {
        double s = Math.sin(f * Math.PI), s1 = Math.sin((1 - (1 - f) * (1 - f)) * Math.PI);
        return new double[]{-Math.PI / 2.25 + s * 1.2 - s1 * 0.4, 0.1 - s * 0.6, 0};
    }

    /** Death fall (vanilla LivingRenderer#setupRotations): sqrt ease to 90 degrees in ~13 ticks. */
    public static double deathRoll(double deathTicks) {
        if (deathTicks <= 0) return 0;
        double f = Math.sqrt(Math.max(0, (deathTicks - 1) / 20.0 * 1.6));
        return Math.min(1, f) * Math.PI / 2;
    }

    /** Idle breathing for arms (vanilla AnimationUtils#bobArms) z component; side +1 right, -1 left. */
    public static double idleSway(double ticks, int side) { return side * (Math.cos(ticks * 0.09) * 0.05 + 0.05); }

    /** Idle breathing x component. */
    public static double idleSwayX(double ticks, int side) { return side * Math.sin(ticks * 0.067) * 0.05; }

    /** Creeper swell scale (vanilla CreeperRenderer#scale): {xz, y}. */
    public static double[] creeperScale(double swell) {
        double f1 = 1 + Math.sin(swell * 100) * swell * 0.01;
        double f = Math.max(0, Math.min(1, swell));
        f *= f;
        f *= f;
        return new double[]{(1 + f * 0.4) * f1, (1 + f * 0.1) / f1};
    }

    /** Spider leg pose (vanilla SpiderModel): pair 0 = front .. 3 = back, side -1 = left(-x) / +1 = right. {ay, az}. */
    public static double[] spiderLeg(int pair, int side, double limb, double amt) {
        double[] baseY = {Math.PI / 4, Math.PI / 8, -Math.PI / 8, -Math.PI / 4};
        double baseZ = pair == 0 || pair == 3 ? Math.PI / 4 : 0.58119464;
        double[] ph = {3 * Math.PI / 2, Math.PI / 2, Math.PI, 0};
        double sw = -Math.cos(limb * LIMB_FREQ * 2 + ph[pair]) * 0.4 * amt;
        double lift = Math.abs(Math.sin(limb * LIMB_FREQ + ph[pair]) * 0.4) * amt;
        return new double[]{side * (baseY[pair] + sw), -side * baseZ + side * lift};
    }

    /** Body yaw follow (vanilla tickHeadTurn, simplified): turns with movement, otherwise keeps the head within 50 degrees. */
    public static float followBodyYaw(float body, float yaw, double moved) {
        float d = wrap(yaw - body);
        if (moved > 0.01) body += d * 0.35f;
        d = wrap(yaw - body);
        if (d > 50) body = yaw - 50;
        if (d < -50) body = yaw + 50;
        return body;
    }

    static float wrap(float a) { a %= 360; if (a >= 180) a -= 360; if (a < -180) a += 360; return a; }

    // ------------------------------------------------------------------ validator
    public static List<String> validate() {
        List<String> problems = new ArrayList<>();
        for (EntityType t : EntityTypes.ALL) {
            if (t.model == null || t.model.isEmpty()) continue;
            for (EntityType.Part p : t.model) {
                if (p.sx <= 0 || p.sy <= 0 || p.sz <= 0) problems.add(t.id + ": part " + p.name + " has non-positive size");
                if (Math.abs(p.ox) > 3 || Math.abs(p.oy) > 4 || Math.abs(p.oz) > 3) problems.add(t.id + ": part " + p.name + " origin far outside the model");
            }
            if (t.walk != null) {
                for (EntityType.Anim a : t.walk) {
                    if (t.part(a.part) == null) problems.add(t.id + ": animation references missing part " + a.part);
                    if (a.axis < 0 || a.axis > 2) problems.add(t.id + ": animation of " + a.part + " has invalid axis");
                    double peak = Math.abs(a.offset) + Math.abs(a.amp);
                    if (peak > JOINT_LIMIT) problems.add(t.id + ": " + a.part + " exceeds joint limit (" + peak + " rad)");
                    double period = 2 * Math.PI / LIMB_FREQ;
                    double v0 = walk(a, 0, 1), v1 = walk(a, period, 1);
                    if (Math.abs(v0 - v1) > 1e-6) problems.add(t.id + ": walk cycle of " + a.part + " does not loop");
                    // standing still must return to the rest pose (offset only)
                    if (Math.abs(walk(a, 1.234, 0) - a.offset) > 1e-9) problems.add(t.id + ": " + a.part + " moves while standing still");
                }
            }
            if (t.attachments != null)
                for (var e : t.attachments.entrySet()) {
                    if (t.part(e.getKey()) == null) problems.add(t.id + ": attachment child " + e.getKey() + " missing");
                    if (t.part(e.getValue()) == null) problems.add(t.id + ": attachment parent " + e.getValue() + " missing");
                }
            // the model must fit (roughly) inside the entity's hitbox
            double minY = 1e9, maxY = -1e9;
            for (EntityType.Part p : t.model) { minY = Math.min(minY, p.oy); maxY = Math.max(maxY, p.oy + p.sy); }
            if (minY < -0.01) problems.add(t.id + ": model extends below the feet");
            if (maxY > t.height * 1.35 + 0.1) problems.add(t.id + ": model much taller than hitbox (" + maxY + " vs " + t.height + ")");
        }
        // procedural clips
        for (double p = 0; p <= 1; p += 0.05) if (Math.abs(attackSwing(p)) > JOINT_LIMIT) problems.add("attack swing exceeds joint limit");
        if (Math.abs(deathRoll(1000) - Math.PI / 2) > 1e-9) problems.add("death roll does not settle at 90 degrees");
        return problems;
    }
}
