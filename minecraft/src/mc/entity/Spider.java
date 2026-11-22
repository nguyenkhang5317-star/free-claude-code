package mc.entity;

import mc.content.EntityType;
import mc.core.World;

/** Spider: climbs walls, leaps at targets, neutral in bright light unless provoked (Section 8.1). */
public final class Spider extends Monster {
    public Spider(World w, EntityType t) {
        super(w, t);
        goals.add(new Leap(3));
        goals.sort((a, b) -> Integer.compare(a.priority, b.priority));
    }

    @Override
    protected boolean climbsWalls() { return true; }

    @Override
    protected boolean wantsToAttack(Player p) {
        int limit = (int) type.num("neutral_in_light", 12);
        return lightAt() < limit || lastAttacker == p;
    }

    @Override
    protected void serverAi() {
        super.serverAi();
        if (target != null && rng.nextInt(100) == 0 && lightAt() >= type.num("neutral_in_light", 12) && lastAttacker != target) target = null;
    }

    class Leap extends Goal {
        Leap(int p) { super(p, JUMP | MOVE); }

        public boolean canUse() {
            if (target == null || !onGround) return false;
            double d = target.distSq(Spider.this);
            return d >= 4 && d <= 16 && rng.nextInt(5) == 0;
        }

        public boolean canContinue() { return !onGround; }

        public void start() {
            double dx = target.x - x, dz = target.z - z, l = Math.sqrt(dx * dx + dz * dz) + 1e-4;
            vx += dx / l * 0.4 * 0.8 + vx * 0.2;
            vz += dz / l * 0.4 * 0.8 + vz * 0.2;
            vy = 0.4;
        }
    }
}
