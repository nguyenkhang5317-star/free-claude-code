package mc.entity;

import mc.content.*;
import mc.core.World;
import mc.util.AABB;
import mc.util.Json;

import java.util.*;

/** Living entities: health in half-hearts, damage with invulnerability frames and armor, movement physics per
 * Appendix AB.4 / Section 16.1, effects (Appendix W), environmental damage. */
public abstract class LivingEntity extends Entity {
    public float health, maxHealth, absorption;
    public int hurtTime, deathTime, invulnerableTime, air;
    public float lastHurt;
    public float limbSwing, limbSwingAmount, prevLimbSwingAmount;
    public boolean jumping, sprinting, sneaking, dead;
    public float moveForward, moveStrafe;
    public double speedAttr;
    public Entity lastAttacker;
    public int lastAttackerTime;
    public final Map<String, int[]> effects = new HashMap<>(); // id -> {amplifier, ticksLeft}
    public DamageSource lastDamageSource;

    protected LivingEntity(EntityType type, World world) {
        super(type, world);
        maxHealth = health = (float) type.health;
        speedAttr = type.speed;
        stepHeight = Registries_step();
        air = maxAir();
    }

    private static double Registries_step() { return Registries.tune("step_height"); }

    protected int maxAir() { return Registries.tunei("max_air"); }

    @Override
    public boolean pickable() { return !dead && !removed; }

    public boolean isUndead() { return type.flag("undead"); }

    @Override
    public void tick() {
        super.tick();
        if (hurtTime > 0) hurtTime--;
        if (invulnerableTime > 0) invulnerableTime--;
        if (lastAttackerTime > 0 && --lastAttackerTime == 0) lastAttacker = null;
        if (dead) {
            if (++deathTime >= 20) remove();
            return;
        }
        baseEnvironment();
        tickEffects();
        aiStep();
        // walk animation (Section 12.2 generic walk cycle driven by distance moved)
        prevLimbSwingAmount = limbSwingAmount;
        double dx = x - prevX, dz = z - prevZ;
        float dist = (float) Math.sqrt(dx * dx + dz * dz) * 4f;
        if (dist > 1) dist = 1;
        limbSwingAmount += (dist - limbSwingAmount) * 0.4f;
        limbSwing += limbSwingAmount;
    }

    /** AI or input decides moveForward/moveStrafe/jumping, then physics runs. */
    protected void aiStep() {
        serverAi();
        if (jumping) {
            if (inWater || inLava) vy += Registries.tune("swim_up");
            else if (onGround) jump();
        }
        travel(moveStrafe * 0.98f, moveForward * 0.98f);
    }

    protected void serverAi() { }

    public void jump() {
        vy = Registries.tune("jump_velocity");
        if (sprinting) {
            double r = Math.toRadians(yaw);
            vx -= Math.sin(r) * Registries.tune("sprint_jump_boost");
            vz += Math.cos(r) * Registries.tune("sprint_jump_boost");
        }
    }

    public double movementSpeed() {
        double s = speedAttr;
        if (sprinting) s *= Registries.tune("sprint_multiplier");
        int[] sp = effects.get("speed");
        if (sp != null) s *= 1 + 0.2 * (sp[0] + 1);
        int[] sl = effects.get("slowness");
        if (sl != null) s *= Math.max(0, 1 - 0.15 * (sl[0] + 1));
        return s;
    }

    protected void moveRelative(double accel, double strafe, double forward) {
        double f = strafe * strafe + forward * forward;
        if (f < 1e-4) return;
        f = Math.sqrt(f);
        if (f < 1) f = 1;
        f = accel / f;
        strafe *= f;
        forward *= f;
        double r = Math.toRadians(yaw), sin = Math.sin(r), cos = Math.cos(r);
        vx += strafe * cos - forward * sin;
        vz += forward * cos + strafe * sin;
    }

    protected boolean isFlying() { return false; }

    /** Movement physics (Appendix AB.4). */
    public void travel(double strafe, double forward) {
        double gravity = Registries.tune("gravity");
        if (isFlying()) {
            travelFlying(strafe, forward);
            return;
        }
        if (inWater) {
            moveRelative(Registries.tune("air_accel"), strafe, forward);
            move(vx, vy, vz);
            double drag = Registries.tune("water_drag");
            vx *= drag; vy *= drag; vz *= drag;
            vy -= 0.02;
            if (horizontalCollision && !Physics.collides(world, bbox().offset(vx, 0.6, vz))) vy = 0.3;
        } else if (inLava) {
            moveRelative(Registries.tune("air_accel"), strafe, forward);
            move(vx, vy, vz);
            double drag = Registries.tune("lava_drag");
            vx *= drag; vy *= drag; vz *= drag;
            vy -= 0.02;
            if (horizontalCollision) vy = 0.3;
        } else {
            // vanilla 1.16: velocity decays by slipperiness*0.91, acceleration scales with 0.216/slipperiness^3 (raw slipperiness)
            double bs = blockSlipperiness();
            double slip = onGround ? bs * Registries.tune("air_drag") : Registries.tune("air_drag");
            double accel = onGround ? movementSpeed() * (Registries.tune("ground_accel_factor") / (bs * bs * bs))
                    : (sprinting ? 0.026 : Registries.tune("air_accel"));
            double sf = speedFactor();
            moveRelative(accel * sf, strafe, forward);
            if (onLadder) {
                double m = Registries.tune("ladder_max_fall");
                vx = Math.max(-m, Math.min(m, vx));
                vz = Math.max(-m, Math.min(m, vz));
                vy = Math.max(vy, -m);
                if (vy < 0 && sneaking) vy = 0;
                fallDistance = 0;
            }
            move(vx, vy, vz);
            if ((horizontalCollision || jumping) && onLadder) vy = Registries.tune("ladder_climb");
            if (climbsWalls() && horizontalCollision) vy = 0.2;
            vy -= gravity;
            if (type.flag("slow_fall") && vy < 0 && !onGround) vy *= 0.6;
            vy *= Registries.tune("vertical_drag");
            vx *= slip;
            vz *= slip;
        }
    }

    protected boolean climbsWalls() { return false; }

    protected void travelFlying(double strafe, double forward) {
        moveRelative(sprinting ? 0.1 : Registries.tune("fly_speed"), strafe, forward);
        move(vx, vy, vz);
        vx *= 0.91; vz *= 0.91; vy *= 0.6;
        fallDistance = 0;
    }

    protected double speedFactor() {
        int s = world.getBlock((int) Math.floor(x), (int) Math.floor(y - 0.2), (int) Math.floor(z));
        return Blocks.block(s).extraNum("speed_factor", 1.0);
    }

    protected double blockSlipperiness() {
        int s = world.getBlock((int) Math.floor(x), (int) Math.floor(y - 0.5), (int) Math.floor(z));
        return Blocks.block(s).slipperiness;
    }

    protected void baseEnvironment() {
        // void (Section 16.1 / tuning void_y)
        if (y < Registries.tune("void_y") && age % 10 == 0) hurt(DamageSource.VOID, (float) Registries.tune("void_damage"));
        if (inLava) {
            hurt(DamageSource.LAVA, 4);
            fireTicks = Math.max(fireTicks, 300);
        }
        if (fireTicks > 0 && !inLava && fireTicks % 20 == 0 && !fireImmune()) hurt(DamageSource.ON_FIRE, 1);
        // contact damage (cactus, fire)
        AABB b = bbox().grow(0.001);
        int x0 = (int) Math.floor(b.minX), x1 = (int) Math.floor(b.maxX);
        int y0 = (int) Math.floor(b.minY), y1 = (int) Math.floor(b.maxY);
        int z0 = (int) Math.floor(b.minZ), z1 = (int) Math.floor(b.maxZ);
        for (int bx = x0; bx <= x1; bx++)
            for (int by = y0; by <= y1; by++)
                for (int bz = z0; bz <= z1; bz++) {
                    int s = world.getBlock(bx, by, bz);
                    double cd = Blocks.block(s).extraNum("contact_damage", 0);
                    if (cd > 0) {
                        if (s == Blocks.FIRE) { hurt(DamageSource.IN_FIRE, (float) cd); fireTicks = Math.max(fireTicks, 160); }
                        else hurt(DamageSource.CACTUS, (float) cd);
                    }
                }
        // suffocation: eyes inside an opaque cube
        int es = world.getBlock((int) Math.floor(x), (int) Math.floor(eyeY()), (int) Math.floor(z));
        if (Blocks.opaqueCube[es] && !noClip && age % 10 == 0) hurt(DamageSource.SUFFOCATE, 1);
        // drowning (300 air ticks, 2 damage at -20)
        if (submerged && canDrown()) {
            air--;
            if (air <= -20) {
                air = 0;
                if (world.level.rule("drowningDamage")) hurt(DamageSource.DROWN, (float) Registries.tune("drown_damage"));
            }
        } else if (air < maxAir()) {
            air = Math.min(maxAir(), air + 4);
        }
    }

    protected boolean canDrown() { return true; }

    protected boolean fireImmune() { return false; }

    protected void tickEffects() {
        for (Iterator<Map.Entry<String, int[]>> it = effects.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, int[]> e = it.next();
            int[] v = e.getValue();
            int amp = v[0];
            switch (e.getKey()) {
                case "regeneration": { int iv = 50 >> amp; if (iv <= 0 || v[1] % iv == 0) heal(1); break; }
                case "poison": { int iv = 25 >> amp; if ((iv <= 0 || v[1] % iv == 0) && health > 1) hurt(DamageSource.MAGIC, 1); break; }
                default: break;
            }
            if (--v[1] <= 0) {
                if (e.getKey().equals("absorption")) absorption = 0;
                it.remove();
            }
        }
    }

    public void addEffect(String id, int ticks, int amp) {
        int[] cur = effects.get(id);
        if (cur == null || cur[0] < amp || cur[1] < ticks) effects.put(id, new int[]{amp, ticks});
        if (id.equals("absorption")) absorption = Math.max(absorption, 4 * (amp + 1));
    }

    public void heal(float amount) {
        if (dead) return;
        health = Math.min(maxHealth, health + amount);
    }

    public int armorValue() { return 0; }

    public double armorToughness() { return 0; }

    protected void damageArmor(float amount) { }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (dead || removed || amount <= 0) return false;
        if (isInvulnerableTo(src)) return false;
        if (src.fire && fireImmune()) return false;
        boolean partial = false;
        if (invulnerableTime > 10) {
            if (amount <= lastHurt) return false;
            float extra = amount - lastHurt;
            lastHurt = amount;
            amount = extra;
            partial = true;
        } else {
            lastHurt = amount;
            invulnerableTime = 20;
            hurtTime = 10;
        }
        if (!src.bypassArmor) {
            // Armor formula (Section 7.1 / Appendix AU.2)
            int armor = armorValue();
            double tough = armorToughness();
            double eff = Math.min(20.0, Math.max(armor / 5.0, armor - amount / (2.0 + tough / 4.0)));
            damageArmor(amount);
            amount = (float) (amount * (1.0 - eff / 25.0));
        }
        int[] res = effects.get("resistance");
        if (res != null) amount *= Math.max(0, 1 - 0.2 * (res[0] + 1));
        if (absorption > 0) {
            float a = Math.min(absorption, amount);
            absorption -= a;
            amount -= a;
        }
        health -= amount;
        lastDamageSource = src;
        if (src.attacker != null) {
            lastAttacker = src.attacker;
            lastAttackerTime = 100;
            if (!partial) {
                double dx = src.attacker.x - x, dz = src.attacker.z - z;
                knockback(Registries.tune("knockback_horizontal"), dx, dz);
            }
        }
        if (!partial) {
            String snd = type.sound("hurt");
            if (snd != null) world.playSound(snd, x, y, z, voiceVolume(), voicePitch());
        }
        onHurt(src, amount);
        if (health <= 0) die(src);
        return true;
    }

    protected boolean isInvulnerableTo(DamageSource src) { return false; }

    protected void onHurt(DamageSource src, float amount) { }

    /** Vanilla LivingEntity#getSoundPitch: (r - r) * 0.2 + 1, babies + 0.5. */
    public float voicePitch() { return (rng.nextFloat() - rng.nextFloat()) * 0.2f + (isBabyVoice() ? 1.5f : 1.0f); }

    protected boolean isBabyVoice() { return false; }

    /** Vanilla LivingEntity#getSoundVolume (cows talk at 0.4); data key "sound_volume". */
    public float voiceVolume() { return (float) type.num("sound_volume", 1.0); }

    public void knockback(double strength, double dx, double dz) {
        double l = Math.sqrt(dx * dx + dz * dz);
        if (l < 1e-4) return;
        vx /= 2; vz /= 2;
        vx -= dx / l * strength;
        vz -= dz / l * strength;
        if (onGround) vy = Math.min(Registries.tune("knockback_vertical"), vy / 2 + strength);
    }

    public void die(DamageSource src) {
        if (dead) return;
        dead = true;
        health = 0;
        deathTime = 0;
        String snd = type.sound("death");
        if (snd != null) world.playSound(snd, x, y, z, voiceVolume(), voicePitch());
        onDeath(src);
    }

    protected void onDeath(DamageSource src) { }

    @Override
    protected void onLanded(double distance) {
        if (!world.level.rule("fallDamage")) return;
        int[] jb = effects.get("jump_boost");
        double reduce = jb == null ? 0 : jb[0] + 1;
        int dmg = (int) Math.ceil(distance - Registries.tune("fall_damage_threshold") - reduce);
        if (dmg > 0 && !inWater) {
            world.playSound(dmg > 4 ? "entity.player.big_fall" : "entity.player.small_fall", x, y, z, 1f, 1f);
            hurt(DamageSource.FALL, dmg);
            int below = world.getBlock((int) Math.floor(x), (int) Math.floor(y - 0.2), (int) Math.floor(z));
            String fam = Blocks.block(below).sound;
            world.playSound("block." + fam + ".fall", x, y, z, 0.5f, 0.75f);
        }
    }

    public Vec look() {
        double ry = Math.toRadians(yaw), rp = Math.toRadians(pitch);
        return new Vec(-Math.sin(ry) * Math.cos(rp), -Math.sin(rp), Math.cos(ry) * Math.cos(rp));
    }

    public static final class Vec {
        public final double x, y, z;

        public Vec(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
    }

    @Override
    public Map<String, Object> save() {
        Map<String, Object> m = super.save();
        m.put("health", (double) health);
        m.put("air", air);
        List<Object> eff = new ArrayList<>();
        for (Map.Entry<String, int[]> e : effects.entrySet()) eff.add(Arrays.asList(e.getKey(), e.getValue()[0], e.getValue()[1]));
        m.put("effects", eff);
        return m;
    }

    @Override
    public void load(Map<String, Object> m) {
        super.load(m);
        health = (float) Json.num(m, "health", maxHealth);
        air = Json.integer(m, "air", maxAir());
        for (Object o : Json.arr(m.get("effects"))) {
            List<Object> l = Json.arr(o);
            effects.put(l.get(0).toString(), new int[]{((Number) l.get(1)).intValue(), ((Number) l.get(2)).intValue()});
        }
    }
}
