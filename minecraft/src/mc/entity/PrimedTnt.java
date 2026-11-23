package mc.entity;

import mc.content.EntityTypes;
import mc.core.World;
import mc.util.Json;

import java.util.Map;

/** Primed TNT: 80-tick fuse, power 4 explosion (Section 7.6). */
public final class PrimedTnt extends Entity {
    public int fuse;
    public final Entity igniter;

    public PrimedTnt(World w, Entity igniter) {
        super(EntityTypes.TNT, w);
        this.igniter = igniter;
        this.fuse = (int) type.num("fuse", 80);
        double a = Math.random() * Math.PI * 2;
        vx = -Math.sin(a) * 0.02;
        vy = 0.2;
        vz = -Math.cos(a) * 0.02;
    }

    @Override
    public void tick() {
        super.tick();
        if (age == 1) world.playSound("entity.tnt.primed", x, y, z, 1f, 1f);
        vy -= 0.04;
        move(vx, vy, vz);
        vx *= 0.98; vy *= 0.98; vz *= 0.98;
        if (onGround) { vx *= 0.7; vz *= 0.7; vy *= -0.5; }
        if (--fuse <= 0) {
            remove();
            world.explode(this, x, y + height / 16, z, (float) type.num("explosion", 4), false);
        } else if (age % 2 == 0) {
            world.particles("smoke", x, y + 0.5, z, 1, 0);
        }
    }

    @Override
    public Map<String, Object> save() {
        Map<String, Object> m = super.save();
        m.put("fuse", fuse);
        return m;
    }

    @Override
    public void load(Map<String, Object> m) {
        super.load(m);
        fuse = Json.integer(m, "fuse", 80);
    }

    @Override
    public int renderData() { return fuse; }
}
