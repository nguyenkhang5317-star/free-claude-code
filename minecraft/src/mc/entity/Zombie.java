package mc.entity;

import mc.content.EntityType;
import mc.core.World;

/** Zombie: melee, arms raised while chasing (Section 8.1). */
public final class Zombie extends Monster {
    public Zombie(World w, EntityType t) { super(w, t); }

    @Override
    public int renderData() { return target != null ? 1 : 0; }
}
