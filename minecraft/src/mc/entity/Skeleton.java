package mc.entity;

import mc.content.EntityType;
import mc.core.World;
import mc.util.Json;

import java.util.Map;

/** Skeleton: ranged bow attack, keeps ~10 blocks, fires every 40 ticks after a 20-tick draw (Section 8.1). */
public final class Skeleton extends Monster {
    public int drawTicks;

    public Skeleton(World w, EntityType t) {
        super(w, t);
        goals.add(new Ranged(4));
        goals.sort((a, b) -> Integer.compare(a.priority, b.priority));
    }

    @Override
    public int renderData() { return drawTicks > 0 ? 1 : 0; }

    class Ranged extends Goal {
        final double range, preferred;
        final int draw, interval;
        int seeTime, repath;

        Ranged(int p) {
            super(p, MOVE | LOOK);
            Map<String, Object> r = Json.obj(type.raw.get("ranged"));
            range = Json.num(r, "range", 15);
            preferred = Json.num(r, "preferred", 10);
            draw = Json.integer(r, "draw_ticks", 20);
            interval = Json.integer(r, "interval", 40);
        }

        public boolean canUse() { return target != null; }

        public void stop() { stopPath(); drawTicks = 0; seeTime = 0; }

        public void tick() {
            LivingEntity t = target;
            if (t == null) return;
            double d = Math.sqrt(t.distSq(Skeleton.this));
            boolean see = canSee(t);
            seeTime = see ? seeTime + 1 : 0;
            lookAt(t.x, t.eyeY(), t.z);
            if (d > preferred || !see) {
                if (--repath <= 0) { repath = 10; moveTo(t.x, t.y, t.z, 1.0); }
            } else {
                stopPath();
                // strafe a little like vanilla
                if (age % 40 < 20) moveStrafe = 0.5f; else moveStrafe = -0.5f;
                yaw = approach(yaw, lookYaw, 30);
            }
            if (see && d <= range) {
                if (attackCooldown <= 0) {
                    drawTicks++;
                    if (drawTicks >= draw) {
                        shoot(t, drawTicks / 20f);
                        drawTicks = 0;
                        attackCooldown = interval - draw;
                    }
                }
            } else {
                drawTicks = 0;
            }
        }
    }

    private void shoot(LivingEntity t, float power) {
        Arrow a = new Arrow(world, this);
        a.setPos(x, eyeY() - 0.1, z);
        double dx = t.x - x, dz = t.z - z, dy = t.y + t.height / 3 - a.y;
        double hd = Math.sqrt(dx * dx + dz * dz);
        a.shoot(dx, dy + hd * 0.2, dz, 1.6, 14 - world.level.difficulty * 4);
        world.addEntity(a);
        swing();
        world.playSound(type.sound("shoot") != null ? type.sound("shoot") : "entity.skeleton.shoot", x, y, z, 1f, 1f / (rng.nextFloat() * 0.4f + 0.8f));
    }
}
