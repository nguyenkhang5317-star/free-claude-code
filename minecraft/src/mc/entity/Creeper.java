package mc.entity;

import mc.content.EntityType;
import mc.core.World;

/** Creeper: swells for 30 ticks within 3 blocks of its target, cancels beyond 6, explodes with power 3. */
public final class Creeper extends Monster {
    public int swell, swellDir, oldSwell;
    public final int fuse;

    public Creeper(World w, EntityType t) {
        super(w, t);
        fuse = (int) t.num("fuse", 30);
        goals.add(new Swell(2));
        goals.sort((a, b) -> Integer.compare(a.priority, b.priority));
    }

    @Override
    public void tick() {
        super.tick();
        if (dead || removed) return;
        oldSwell = swell;
        if (swellDir > 0 && swell == 0) world.playSound(type.sound("primed") != null ? type.sound("primed") : "entity.creeper.primed", x, y, z, 1f, 0.5f);
        swell += swellDir;
        if (swell < 0) swell = 0;
        if (swell >= fuse) {
            swell = fuse;
            dead = true;
            remove();
            world.explode(this, x, y, z, (float) type.num("explosion", 3), false);
        }
    }

    @Override
    protected void onLanded(double distance) {
        super.onLanded(distance);
        swell = Math.min(fuse - 5, swell + (int) (distance * 1.5));
    }

    class Swell extends Goal {
        Swell(int p) { super(p, MOVE); }

        public boolean canUse() { return swell > 0 || target != null && target.distSq(Creeper.this) < 9; }

        public void start() { stopPath(); }

        public void stop() { swellDir = -1; }

        public void tick() {
            if (target == null || target.distSq(Creeper.this) > 36 || !canSee(target)) swellDir = -1;
            else swellDir = 1;
            if (target != null) lookAt(target.x, target.eyeY(), target.z);
        }
    }

    @Override
    public boolean doHurtTarget(LivingEntity t) { return false; }

    @Override
    public int renderData() { return swell; }

    @Override
    public float renderData2() { return swell / (float) fuse; }
}
