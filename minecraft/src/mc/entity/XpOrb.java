package mc.entity;

import mc.content.EntityTypes;
import mc.core.World;
import mc.util.Json;

import java.util.Map;

/** Experience orb: drifts toward the nearest player within 8 blocks. */
public final class XpOrb extends Entity {
    public int value;

    public XpOrb(World w, int value) {
        super(EntityTypes.XP_ORB, w);
        this.value = value;
    }

    @Override
    public void tick() {
        super.tick();
        vy -= 0.03;
        Player target = null;
        double best = 64;
        for (Player p : world.players) {
            if (p.dead || p.isSpectator()) continue;
            double d = p.distSq(x, y, z);
            if (d < best) { best = d; target = p; }
        }
        if (target != null) {
            double dx = target.x - x, dy = target.eyeY() - 0.5 - y, dz = target.z - z;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz) / 8.0;
            double f = 1 - d;
            if (f > 0) {
                f *= f;
                vx += dx / (d * 8 + 1e-4) * f * 0.1;
                vy += dy / (d * 8 + 1e-4) * f * 0.1;
                vz += dz / (d * 8 + 1e-4) * f * 0.1;
            }
            if (best < 1.5 && age > 2 && target.xpCooldown == 0) {
                target.xpCooldown = 2;
                target.giveXp(value);
                world.playSound("entity.experience_orb.pickup", x, y, z, 0.1f, 0.5f * ((rng.nextFloat() - rng.nextFloat()) * 0.7f + 1.8f));
                remove();
                return;
            }
        }
        move(vx, vy, vz);
        double f = onGround ? 0.588 : 0.98;
        vx *= f; vz *= f; vy *= 0.98;
        if (onGround) vy *= -0.9;
        if (age > 6000 || inLava) remove();
    }

    @Override
    public Map<String, Object> save() {
        Map<String, Object> m = super.save();
        m.put("value", value);
        return m;
    }

    @Override
    public void load(Map<String, Object> m) {
        super.load(m);
        value = Json.integer(m, "value", 1);
    }

    @Override
    public int renderData() { return value; }
}
