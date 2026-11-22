package mc.entity;

import mc.content.*;
import mc.core.World;
import mc.core.Inventory;
import mc.util.Json;

import java.util.Map;

/** Dropped item stack (Section 7.4): pickup delay 10 ticks, merges with nearby equal stacks, despawns after 5 minutes. */
public final class ItemEntity extends Entity {
    public ItemStack stack;
    public int pickupDelay;
    public float bob;

    public ItemEntity(World w, ItemStack s) {
        super(EntityTypes.ITEM, w);
        this.stack = s;
        this.pickupDelay = Registries.tunei("item_pickup_delay");
        this.bob = (float) (Math.random() * Math.PI * 2);
    }

    @Override
    public void tick() {
        super.tick();
        if (stack == null || stack.count <= 0) { remove(); return; }
        if (pickupDelay > 0) pickupDelay--;
        if (inWater) {
            vy += 0.005;
            if (vy > 0.06) vy = 0.06;
            vx *= 0.99; vz *= 0.99;
        } else {
            vy -= 0.04;
        }
        if (inLava) { remove(); world.playSound("block.fire.extinguish", x, y, z, 0.4f, 2f); return; }
        move(vx, vy, vz);
        double f = 0.98;
        if (onGround) f = Blocks.block(world.getBlock((int) Math.floor(x), (int) Math.floor(y - 1), (int) Math.floor(z))).slipperiness * 0.98;
        vx *= f; vz *= f; vy *= 0.98;
        if (onGround) vy *= -0.5;
        if (age % 20 == 0) mergeNearby();
        if (age >= Registries.tunei("item_despawn_ticks")) remove();
        if (y < -64) remove();
    }

    private void mergeNearby() {
        if (stack.count >= stack.max()) return;
        for (Entity e : world.entitiesIn(bbox().grow(0.5), this)) {
            if (!(e instanceof ItemEntity) || e.removed) continue;
            ItemEntity o = (ItemEntity) e;
            if (!stack.canStack(o.stack)) continue;
            int move = Math.min(stack.max() - stack.count, o.stack.count);
            if (move <= 0) continue;
            stack.count += move;
            o.stack.count -= move;
            if (o.stack.count <= 0) o.remove();
            pickupDelay = Math.max(pickupDelay, o.pickupDelay);
            age = Math.min(age, o.age);
        }
    }

    public void tryPickup(Player p) {
        if (pickupDelay > 0 || removed || p.dead || p.isSpectator()) return;
        int before = stack.count;
        ItemStack rest = p.inventory.add(stack);
        int taken = before - (rest == null ? 0 : rest.count);
        if (taken > 0) {
            world.playSound("entity.item.pickup", x, y, z, 0.2f, ((rng.nextFloat() - rng.nextFloat()) * 0.7f + 1f) * 2f);
            p.onPickup(this, taken);
        }
        if (rest == null || rest.count <= 0) remove();
        else stack = rest;
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (src.fire || src.explosion) { remove(); return true; }
        return false;
    }

    @Override
    public Map<String, Object> save() {
        Map<String, Object> m = super.save();
        m.put("item", Inventory.saveStacks(new ItemStack[]{stack}));
        m.put("pickup", pickupDelay);
        return m;
    }

    @Override
    public void load(Map<String, Object> m) {
        super.load(m);
        ItemStack[] a = new ItemStack[1];
        Inventory.loadStacks(m.get("item"), a);
        stack = a[0];
        pickupDelay = Json.integer(m, "pickup", 0);
    }

    @Override
    public boolean shouldSave() { return !removed && stack != null; }

    @Override
    public int renderData() { return stack == null ? 0 : stack.item.index; }

    @Override
    public float renderData2() { return stack == null ? 1 : stack.count; }
}
