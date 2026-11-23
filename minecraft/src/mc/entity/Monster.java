package mc.entity;

import mc.content.*;
import mc.core.World;

/** Hostile mob base (Section 8.1): burns in sunlight if burns_in_sun, removed on peaceful. */
public class Monster extends Mob {
    public Monster(World w, EntityType t) { super(w, t); }

    @Override
    public void tick() {
        if (world.level.difficulty == 0 && !removed) { remove(); return; }
        super.tick();
        if (dead) return;
        if (type.flag("burns_in_sun") && world.dim.hasSky && !inWater && fireTicks <= 0) {
            int bx = (int) Math.floor(x), by = (int) Math.floor(eyeY()), bz = (int) Math.floor(z);
            if (world.level.skyDarken() < 4 && world.canSeeSky(bx, by, bz) && !world.isRainingAt(bx, by, bz)) {
                float bright = world.getSkyLight(bx, by, bz) / 15f;
                if (rng.nextFloat() * 30f < (bright - 0.4f) * 2f) fireTicks = 160;
            }
        }
    }

    @Override
    protected boolean fireImmune() { return false; }
}
