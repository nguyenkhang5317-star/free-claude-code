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

    /** Attack swing of the right arm: 0..1 progress -> radians. */
    public static double attackSwing(double progress) {
        if (progress <= 0 || progress >= 1) return 0;
        return -Math.sin(Math.sqrt(progress) * Math.PI) * 1.2;
    }

    /** Death fall: entity rotates 90 degrees around its forward axis over 20 ticks. */
    public static double deathRoll(double deathTicks) {
        double f = Math.min(1, Math.sqrt(Math.max(0, deathTicks) / 20.0));
        return f * Math.PI / 2;
    }

    /** Idle breathing for arms (small z sway). */
    public static double idleSway(double ticks, int side) { return side * (Math.cos(ticks * 0.09) * 0.05 + 0.05); }

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
