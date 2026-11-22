package mc.entity;

import mc.content.*;
import mc.core.Raycast;
import mc.core.World;
import mc.util.AABB;

import java.util.Map;

/** Arrow projectile (Section 7.5): gravity 0.05, drag 0.99, damage = speed x base 2, sticks in blocks. */
public final class Arrow extends Entity {
    public Entity owner;
    public boolean inGround, pickupAllowed;
    public double baseDamage = 2;
    public boolean crit;
    private int life;

    public Arrow(World w, Entity owner) {
        super(EntityTypes.ARROW, w);
        this.owner = owner;
        this.width = 0.5;
        this.height = 0.5;
    }

    public void shoot(double dx, double dy, double dz, double speed, double inaccuracy) {
        double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx = dx / l + rng.nextGaussian() * 0.0075 * inaccuracy;
        dy = dy / l + rng.nextGaussian() * 0.0075 * inaccuracy;
        dz = dz / l + rng.nextGaussian() * 0.0075 * inaccuracy;
        vx = dx * speed; vy = dy * speed; vz = dz * speed;
        yaw = (float) Math.toDegrees(Math.atan2(-vx, vz));
        pitch = (float) Math.toDegrees(-Math.atan2(vy, Math.sqrt(vx * vx + vz * vz)));
    }

    @Override
    public void tick() {
        super.tick();
        if (inGround) {
            if (++life > 1200) remove();
            int s = world.getBlock((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
            if (!Blocks.solidCollision[s]) { inGround = false; life = 0; }
            for (Player p : world.players)
                if (pickupAllowed && p.bbox().grow(0.5).intersects(bbox()) && !p.dead) {
                    if (p.inventory.add(new ItemStack(Items.get("minecraft:arrow"), 1)) == null) {
                        world.playSound("entity.item.pickup", x, y, z, 0.2f, 1.5f);
                        remove();
                    }
                }
            return;
        }
        double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
        Raycast.Hit hit = speed > 1e-6 ? Raycast.cast(world, x, y, z, vx / speed, vy / speed, vz / speed, speed) : null;
        double travel = hit != null ? hit.dist : speed;
        // entity hit along the segment
        Entity target = null;
        double tBest = travel;
        AABB sweep = bbox().expand(vx, vy, vz).grow(1);
        for (Entity e : world.entitiesIn(sweep, this)) {
            if (!(e instanceof LivingEntity) || e == owner && age < 5 || ((LivingEntity) e).dead) continue;
            double t = e.bbox().grow(0.3).rayHit(x, y, z, vx / speed, vy / speed, vz / speed, tBest);
            if (t >= 0 && t < tBest) { tBest = t; target = e; }
        }
        if (target != null) {
            float dmg = (float) Math.ceil(speed * baseDamage);
            if (crit) dmg += rng.nextInt((int) dmg / 2 + 2);
            if (target.hurt(DamageSource.arrow(this, owner), dmg)) {
                if (target instanceof LivingEntity) ((LivingEntity) target).knockback(0.4, -vx, -vz);
                world.playSound("entity.arrow.hit", x, y, z, 1f, 1.2f / (rng.nextFloat() * 0.2f + 0.9f));
                remove();
            } else {
                vx *= -0.1; vy *= -0.1; vz *= -0.1;
            }
            return;
        }
        if (hit != null) {
            x = hit.hx - vx / speed * 0.05;
            y = hit.hy - vy / speed * 0.05;
            z = hit.hz - vz / speed * 0.05;
            inGround = true;
            world.playSound("entity.arrow.hit", x, y, z, 1f, 1.2f / (rng.nextFloat() * 0.2f + 0.9f));
            return;
        }
        x += vx; y += vy; z += vz;
        double drag = inWater ? 0.6 : 0.99;
        vx *= drag; vy *= drag; vz *= drag;
        vy -= 0.05;
        yaw = (float) Math.toDegrees(Math.atan2(-vx, vz));
        pitch = (float) Math.toDegrees(-Math.atan2(vy, Math.sqrt(vx * vx + vz * vz)));
        if (y < -64 || age > 1200) remove();
    }

    @Override
    public Map<String, Object> save() {
        Map<String, Object> m = super.save();
        m.put("in_ground", inGround);
        m.put("pickup", pickupAllowed);
        return m;
    }

    @Override
    public void load(Map<String, Object> m) {
        super.load(m);
        inGround = Boolean.TRUE.equals(m.get("in_ground"));
        pickupAllowed = Boolean.TRUE.equals(m.get("pickup"));
    }
}
