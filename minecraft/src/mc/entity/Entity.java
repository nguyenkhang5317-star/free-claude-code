package mc.entity;

import mc.content.*;
import mc.core.World;
import mc.util.AABB;
import mc.util.Json;

import java.util.*;

/** Base entity: transform, bounding box, fluid state, fall tracking (Section 2.5). */
public abstract class Entity {
    private static int NEXT_ID = 1;

    public final int id;
    public final EntityType type;
    public World world;
    public double x, y, z, prevX, prevY, prevZ;
    public double vx, vy, vz;
    public float yaw, pitch, headYaw, prevYaw, prevPitch, prevHeadYaw;
    public boolean onGround, horizontalCollision, verticalCollision, removed, noClip, inWater, inLava, inWeb, sneakSafe;
    public boolean submerged, onLadder, persistent;
    public double fallDistance, stepHeight = 0;
    public double width, height;
    public int age, fireTicks, portalCooldown;
    public final Random rng = new Random();

    protected Entity(EntityType type, World world) {
        synchronized (Entity.class) { this.id = NEXT_ID++; }
        this.type = type;
        this.world = world;
        this.width = type.width;
        this.height = type.height;
    }

    public AABB bbox() {
        double h = width / 2;
        return new AABB(x - h, y, z - h, x + h, y + height, z + h);
    }

    public void setPos(double x, double y, double z) {
        this.x = this.prevX = x;
        this.y = this.prevY = y;
        this.z = this.prevZ = z;
    }

    public double eyeY() { return y + type.eye * (height / type.height); }

    public double distSq(Entity o) {
        double dx = o.x - x, dy = o.y - y, dz = o.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public double distSq(double px, double py, double pz) {
        double dx = px - x, dy = py - y, dz = pz - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public void tick() {
        prevX = x; prevY = y; prevZ = z;
        prevYaw = yaw; prevPitch = pitch; prevHeadYaw = headYaw;
        age++;
        if (portalCooldown > 0) portalCooldown--;
        updateFluidState();
        if (fireTicks > 0) {
            if (inWater) fireTicks = 0;
            else fireTicks--;
        }
        if (inLava) fireTicks = Math.max(fireTicks, 300);
    }

    protected void updateFluidState() {
        AABB b = bbox();
        boolean water = false, lava = false, web = false, ladder = false;
        int x0 = (int) Math.floor(b.minX + 0.001), x1 = (int) Math.floor(b.maxX - 0.001);
        int y0 = (int) Math.floor(b.minY + 0.001), y1 = (int) Math.floor(b.maxY - 0.001);
        int z0 = (int) Math.floor(b.minZ + 0.001), z1 = (int) Math.floor(b.maxZ - 0.001);
        for (int bx = x0; bx <= x1; bx++)
            for (int by = y0; by <= y1; by++)
                for (int bz = z0; bz <= z1; bz++) {
                    int s = world.getBlock(bx, by, bz);
                    if (s == Blocks.AIR) continue;
                    if (Blocks.isWater(s)) water = true;
                    else if (Blocks.isLava(s)) lava = true;
                    else if (Blocks.block(s).has("web")) web = true;
                }
        int fs = world.getBlock((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        ladder = Blocks.block(fs).has("climbable");
        int es = world.getBlock((int) Math.floor(x), (int) Math.floor(eyeY()), (int) Math.floor(z));
        submerged = Blocks.isWater(es);
        if (water && !inWater && age > 1) onEnterWater();
        inWater = water;
        inLava = lava;
        inWeb = web;
        onLadder = ladder;
        if (inWater) fallDistance = 0;
    }

    protected void onEnterWater() {
        if (Math.abs(vy) > 0.2) world.playSound("entity.generic.splash", x, y, z, 0.4f, 1.0f);
    }

    /** Apply movement and fall tracking. */
    public void move(double dx, double dy, double dz) {
        double oy = y;
        boolean wasGround = onGround;
        Physics.move(this, dx, dy, dz);
        if (onGround) {
            if (fallDistance > 0 && !wasGround) onLanded(fallDistance);
            fallDistance = 0;
        } else if (y < oy && !inWater && !onLadder) {
            fallDistance += oy - y;
        } else if (onLadder || inWater) {
            fallDistance = 0;
        }
    }

    protected void onLanded(double distance) { }

    public void remove() { removed = true; }

    public boolean isAlive() { return !removed; }

    public boolean hurt(DamageSource src, float amount) { return false; }

    public boolean pickable() { return false; }

    public double lightAt() {
        return world.getRawBrightness((int) Math.floor(x), (int) Math.floor(y + 0.1), (int) Math.floor(z));
    }

    // ---- persistence ----
    public Map<String, Object> save() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type.id);
        m.put("pos", Arrays.asList(x, y, z));
        m.put("motion", Arrays.asList(vx, vy, vz));
        m.put("rot", Arrays.asList((double) yaw, (double) pitch));
        m.put("fire", fireTicks);
        m.put("age", age);
        return m;
    }

    public void load(Map<String, Object> m) {
        List<Object> p = Json.arr(m.get("pos"));
        if (p.size() == 3) setPos(((Number) p.get(0)).doubleValue(), ((Number) p.get(1)).doubleValue(), ((Number) p.get(2)).doubleValue());
        List<Object> v = Json.arr(m.get("motion"));
        if (v.size() == 3) { vx = ((Number) v.get(0)).doubleValue(); vy = ((Number) v.get(1)).doubleValue(); vz = ((Number) v.get(2)).doubleValue(); }
        List<Object> r = Json.arr(m.get("rot"));
        if (r.size() == 2) { yaw = ((Number) r.get(0)).floatValue(); pitch = ((Number) r.get(1)).floatValue(); headYaw = yaw; }
        fireTicks = Json.integer(m, "fire", 0);
        age = Json.integer(m, "age", 0);
    }

    /** Should this entity be written to disk? (Appendix AZ.1) */
    public boolean shouldSave() { return !removed; }

    /** Extra state for client rendering (e.g. creeper swell, item id). */
    public int renderData() { return 0; }

    public float renderData2() { return 0; }
}
