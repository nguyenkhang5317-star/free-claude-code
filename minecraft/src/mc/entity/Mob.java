package mc.entity;

import mc.content.*;
import mc.core.Raycast;
import mc.core.World;
import mc.util.Json;

import java.util.*;

/**
 * AI-driven living entity (Section 8). Goals come from entities.json as [priority, name] pairs and run in a
 * prioritized selector with mutex flags (MOVE, LOOK, JUMP, TARGET). Movement follows A* paths.
 */
public class Mob extends LivingEntity {
    public static final int MOVE = 1, LOOK = 2, JUMP = 4, TARGET = 8;

    public LivingEntity target;
    protected final List<Goal> goals = new ArrayList<>();
    public Pathfinder.Path path;
    protected double pathSpeed = 1;
    private int pathStuck;
    private double lastPathX, lastPathZ;
    protected float lookYaw, lookPitch;
    protected boolean hasLook;
    public int attackCooldown;
    public int ambientTimer;
    public int noActionTime;

    public abstract static class Goal {
        public final int priority, flags;
        public boolean running;

        protected Goal(int priority, int flags) { this.priority = priority; this.flags = flags; }

        public abstract boolean canUse();

        public boolean canContinue() { return canUse(); }

        public void start() { }

        public void stop() { }

        public void tick() { }
    }

    public Mob(World w, EntityType t) {
        super(t, w);
        for (Object[] g : t.goals) {
            Goal goal = makeGoal(((Number) g[0]).intValue(), (String) g[1]);
            if (goal != null) goals.add(goal);
        }
        goals.sort(Comparator.comparingInt(a -> a.priority));
        ambientTimer = -rng.nextInt(120);
    }

    protected Goal makeGoal(int pri, String name) {
        String arg = null;
        int c = name.indexOf(':');
        if (c >= 0) { arg = name.substring(c + 1); name = name.substring(0, c); }
        switch (name) {
            case "swim": return new Swim(pri);
            case "wander": return new Wander(pri);
            case "look_at_player": return new LookAtPlayer(pri);
            case "random_look": return new RandomLook(pri);
            case "panic": return new Panic(pri);
            case "tempt": return new Tempt(pri, Items.get(arg));
            case "target": return new NearestTarget(pri);
            case "melee_attack": case "move_towards_target": return name.equals("melee_attack") ? new MeleeAttack(pri) : null;
            default: return null; // subclass-specific goals are added by subclasses
        }
    }

    // ------------------------------------------------------------------ AI loop
    @Override
    protected void serverAi() {
        moveForward = 0;
        moveStrafe = 0;
        jumping = false;
        hasLook = false;
        if (attackCooldown > 0) attackCooldown--;
        if (target != null && (target.dead || target.removed || target.world != world || (target instanceof Player && !((Player) target).attackable())))
            target = null;
        // goal selection
        int held = 0;
        for (Goal g : goals) if (g.running) {
            if (!g.canContinue()) { g.running = false; g.stop(); }
        }
        for (Goal g : goals) if (g.running) held |= g.flags;
        for (Goal g : goals) {
            if (g.running) continue;
            boolean blocked = false;
            for (Goal o : goals) if (o.running && (o.flags & g.flags) != 0 && o.priority <= g.priority) { blocked = true; break; }
            if (blocked || !g.canUse()) continue;
            for (Goal o : goals)
                if (o.running && (o.flags & g.flags) != 0 && o.priority > g.priority) { o.running = false; o.stop(); }
            g.running = true;
            g.start();
        }
        for (Goal g : goals) if (g.running) g.tick();
        followPath();
        if (hasLook) {
            headYaw = approach(headYaw, lookYaw, 10);
            pitch = approachLinear(pitch, lookPitch, 10);
        } else {
            headYaw = approach(headYaw, yaw, 10);
            pitch = approachLinear(pitch, 0, 10);
        }
        if (Math.abs(wrap(headYaw - yaw)) > 75) yaw = approach(yaw, headYaw, 10);
        // ambient sounds
        if (++ambientTimer > 80 && rng.nextInt(1000) < ambientTimer) {
            ambientTimer = -80;
            String s = type.sound("ambient");
            if (s != null) world.playSound(s, x, y, z, 1f, voicePitch());
        }
        // footsteps
        if (onGround && (Math.abs(x - prevX) + Math.abs(z - prevZ)) > 0.01 && age % 8 == 0) {
            String st = type.sound("step");
            if (st != null) world.playSound(st, x, y, z, 0.15f, 1f);
        }
    }

    public void lookAt(double tx, double ty, double tz) {
        double dx = tx - x, dz = tz - z, dy = ty - eyeY();
        lookYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        lookPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        hasLook = true;
    }

    static float wrap(float a) {
        a %= 360;
        if (a >= 180) a -= 360;
        if (a < -180) a += 360;
        return a;
    }

    static float approach(float cur, float target, float max) {
        float d = wrap(target - cur);
        if (d > max) d = max;
        if (d < -max) d = -max;
        return cur + d;
    }

    static float approachLinear(float cur, float target, float max) {
        float d = target - cur;
        return cur + Math.max(-max, Math.min(max, d));
    }

    // ------------------------------------------------------------------ navigation
    public boolean moveTo(double tx, double ty, double tz, double speed) {
        int h = (int) Math.ceil(height);
        Pathfinder.Path p = Pathfinder.find(world, (int) Math.floor(x), (int) Math.floor(y + 0.5), (int) Math.floor(z),
                (int) Math.floor(tx), (int) Math.floor(ty), (int) Math.floor(tz), h, 1.0);
        path = p;
        pathSpeed = speed;
        pathStuck = 0;
        return p != null;
    }

    public void stopPath() { path = null; }

    public boolean pathDone() { return path == null || path.done(); }

    private void followPath() {
        if (path == null) return;
        if (path.done()) { path = null; return; }
        int[] n = path.current();
        double tx = n[0] + 0.5, tz = n[2] + 0.5;
        double dx = tx - x, dz = tz - z;
        double hd = dx * dx + dz * dz;
        double reach = Math.max(0.35, width * 0.5);
        if (hd < reach * reach && Math.abs(y - n[1]) < 1.2) {
            path.index++;
            if (path.done()) { path = null; return; }
            n = path.current();
            tx = n[0] + 0.5; tz = n[2] + 0.5;
            dx = tx - x; dz = tz - z;
        }
        float want = (float) Math.toDegrees(Math.atan2(-dx, dz));
        yaw = approach(yaw, want, 90);
        moveForward = (float) pathSpeed;
        if (n[1] > y + 0.4 && onGround || (horizontalCollision && onGround)) jumping = true;
        if (inWater && n[1] >= Math.floor(y)) jumping = true;
        if (age % 20 == 0) {
            double moved = Math.abs(x - lastPathX) + Math.abs(z - lastPathZ);
            lastPathX = x;
            lastPathZ = z;
            if (moved < 0.2 && ++pathStuck > 2) path = null;
        }
    }

    @Override
    public double movementSpeed() { return super.movementSpeed(); }

    // ------------------------------------------------------------------ helpers
    public Player nearestPlayer(double range, boolean attackableOnly) {
        Player best = null;
        double bd = range * range;
        for (Player p : world.players) {
            if (p.dead || (attackableOnly && !p.attackable())) continue;
            double d = p.distSq(this);
            if (d < bd) { bd = d; best = p; }
        }
        return best;
    }

    public boolean canSee(Entity e) {
        return !Raycast.blocked(world, x, eyeY(), z, e.x, e.eyeY(), e.z);
    }

    public boolean doHurtTarget(LivingEntity t) {
        double dmg = 2;
        if (type.damage != null && type.damage.length == 3) dmg = type.damage[Math.max(0, Math.min(2, world.level.difficulty - 1))];
        boolean ok = t.hurt(DamageSource.mob(this), (float) dmg);
        if (ok) {
            double r = Math.toRadians(yaw);
            t.knockback(0.0, Math.sin(r), -Math.cos(r));
        }
        return ok;
    }

    // ------------------------------------------------------------------ shared goals
    class Swim extends Goal {
        Swim(int p) { super(p, JUMP); }

        public boolean canUse() { return inWater && submergedFraction() > 0.4 || inLava; }

        public void tick() { if (rng.nextFloat() < 0.8f) jumping = true; }
    }

    double submergedFraction() {
        int s = world.getBlock((int) Math.floor(x), (int) Math.floor(y + height * 0.5), (int) Math.floor(z));
        return Blocks.fluid[s] ? 1 : 0.2;
    }

    class Wander extends Goal {
        double tx, ty, tz;

        Wander(int p) { super(p, MOVE); }

        public boolean canUse() {
            if (rng.nextInt(120) != 0) return false;
            for (int i = 0; i < 10; i++) {
                int px = (int) Math.floor(x) + rng.nextInt(21) - 10, pz = (int) Math.floor(z) + rng.nextInt(21) - 10;
                int py = (int) Math.floor(y) + rng.nextInt(9) - 4;
                if (!world.isLoadedBlock(px, pz)) continue;
                if (Pathfinder.standable(world, px, py, pz, (int) Math.ceil(height))) { tx = px + 0.5; ty = py; tz = pz + 0.5; return true; }
            }
            return false;
        }

        public boolean canContinue() { return !pathDone(); }

        public void start() { moveTo(tx, ty, tz, 1.0); }

        public void stop() { stopPath(); }
    }

    class LookAtPlayer extends Goal {
        Player p;
        int time;

        LookAtPlayer(int pr) { super(pr, LOOK); }

        public boolean canUse() {
            if (rng.nextFloat() >= 0.02f) return false;
            p = nearestPlayer(8, false);
            return p != null;
        }

        public boolean canContinue() { return p != null && !p.dead && time > 0 && p.distSq(Mob.this) < 64; }

        public void start() { time = 40 + rng.nextInt(40); }

        public void tick() { time--; lookAt(p.x, p.eyeY(), p.z); }
    }

    class RandomLook extends Goal {
        double lx, lz;
        int time;

        RandomLook(int p) { super(p, LOOK); }

        public boolean canUse() { return rng.nextFloat() < 0.02f; }

        public boolean canContinue() { return time > 0; }

        public void start() {
            double a = rng.nextDouble() * Math.PI * 2;
            lx = Math.cos(a); lz = Math.sin(a);
            time = 20 + rng.nextInt(20);
        }

        public void tick() { time--; lookAt(x + lx, eyeY(), z + lz); }
    }

    class Panic extends Goal {
        Panic(int p) { super(p, MOVE); }

        public boolean canUse() { return lastAttacker != null && lastAttackerTime > 60 || fireTicks > 0; }

        public boolean canContinue() { return !pathDone() && lastAttackerTime > 0; }

        public void start() {
            for (int i = 0; i < 10; i++) {
                int px = (int) Math.floor(x) + rng.nextInt(11) - 5, pz = (int) Math.floor(z) + rng.nextInt(11) - 5, py = (int) Math.floor(y) + rng.nextInt(5) - 2;
                if (Pathfinder.standable(world, px, py, pz, (int) Math.ceil(height))) { moveTo(px + 0.5, py, pz + 0.5, 1.25 * 1.6); return; }
            }
        }

        public void stop() { stopPath(); }
    }

    class Tempt extends Goal {
        final Item item;
        Player p;
        int cooldown;

        Tempt(int pr, Item item) { super(pr, MOVE | LOOK); this.item = item; }

        public boolean canUse() {
            if (cooldown > 0) { cooldown--; return false; }
            if (item == null) return false;
            p = nearestPlayer(10, false);
            if (p == null) return false;
            ItemStack h = p.inventory.held(), o = p.inventory.offhand();
            return (h != null && h.item == item) || (o != null && o.item == item);
        }

        public void tick() {
            lookAt(p.x, p.eyeY(), p.z);
            if (p.distSq(Mob.this) < 6.25) stopPath();
            else if (age % 10 == 0 || pathDone()) moveTo(p.x, p.y, p.z, 1.2);
        }

        public void stop() { stopPath(); cooldown = 100; p = null; }
    }

    class NearestTarget extends Goal {
        NearestTarget(int p) { super(p, TARGET); }

        public boolean canUse() {
            if (target != null) return false;
            if (rng.nextInt(10) != 0) return false;
            Player p = nearestPlayer(16, true);
            if (p == null || !canSee(p) || !wantsToAttack(p)) return false;
            target = p;
            return true;
        }

        public boolean canContinue() {
            if (target == null) return false;
            if (target.distSq(Mob.this) > 35 * 35) { target = null; return false; }
            return true;
        }
    }

    protected boolean wantsToAttack(Player p) { return true; }

    class MeleeAttack extends Goal {
        int repath;

        MeleeAttack(int p) { super(p, MOVE | LOOK); }

        public boolean canUse() { return target != null; }

        public void start() { repath = 0; }

        public void stop() { stopPath(); }

        public void tick() {
            LivingEntity t = target;
            if (t == null) return;
            lookAt(t.x, t.eyeY(), t.z);
            double d = t.distSq(Mob.this);
            if (--repath <= 0) {
                repath = 4 + rng.nextInt(7) + (d > 256 ? 10 : 0);
                if (!moveTo(t.x, t.y, t.z, 1.0)) repath += 15;
            }
            double reach = width * 2.0 * width * 2.0 + t.width;
            if (d <= reach && attackCooldown <= 0) {
                attackCooldown = 20;
                swing();
                doHurtTarget(t);
            }
        }
    }

    public int swingTime;

    public void swing() { swingTime = 6; }

    @Override
    public void tick() {
        super.tick();
        if (swingTime > 0) swingTime--;
        noActionTime++;
        if (age % 20 == 0 && !removed) checkDespawn();
    }

    // ------------------------------------------------------------------ death & drops
    @Override
    protected void onDeath(DamageSource src) {
        if (!world.level.rule("doMobLoot")) return;
        boolean byPlayer = lastAttacker instanceof Player;
        for (Map<String, Object> d : type.drops) {
            double chance = Json.num(d, "chance", 1);
            if (rng.nextDouble() >= chance) continue;
            int mn = Json.integer(d, "min", 1), mx = Json.integer(d, "max", mn);
            int n = mn + rng.nextInt(Math.max(1, mx - mn + 1));
            if (n <= 0) continue;
            String id = fireTicks > 0 && d.containsKey("cooked") ? Json.str(d, "cooked", null) : Json.str(d, "item", null);
            Item it = id == null ? null : Items.get(id);
            if (it != null && dropAllowed(it)) world.dropItem(x, y + 0.5, z, new ItemStack(it, n));
        }
        if (byPlayer && type.xpMax > 0) world.spawnXp(x, y + 0.5, z, type.xpMin + rng.nextInt(type.xpMax - type.xpMin + 1));
    }

    protected boolean dropAllowed(Item it) { return true; }

    /** Despawn rules (Section 8.4): random beyond 32, forced beyond 128. */
    public boolean checkDespawn() {
        if (persistent) return false;
        Player p = nearestPlayer(1e9, false);
        if (p == null) return false;
        double d = p.distSq(this);
        double hard = Registries.tune("despawn_hard"), soft = Registries.tune("despawn_soft");
        if (d > hard * hard) { remove(); return true; }
        if (d > soft * soft && noActionTime > 600 && rng.nextInt(Registries.tunei("despawn_chance")) == 0) { remove(); return true; }
        if (d < soft * soft) noActionTime = 0;
        return false;
    }

    @Override
    public Map<String, Object> save() {
        Map<String, Object> m = super.save();
        m.put("persistent", persistent);
        return m;
    }

    @Override
    public void load(Map<String, Object> m) {
        super.load(m);
        persistent = Boolean.TRUE.equals(m.get("persistent"));
    }
}
