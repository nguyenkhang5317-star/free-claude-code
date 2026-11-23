package mc.core;

import mc.content.*;

import java.util.*;

/** Mining model (Appendix CH): damage per tick = toolSpeed / (hardness x 30) with the correct tool, or
 * / (hardness x 100) when the block cannot be harvested; the block breaks when accumulated damage >= 1. */
public final class Mining {
    private Mining() {}

    public static boolean canHarvest(int state, ItemStack tool) {
        Block b = Blocks.block(state);
        if (!b.requiresTool) return true;
        if (tool == null || tool.item.toolKind == null) return false;
        Item it = tool.item;
        if (b.tool.equals("shears")) return it.toolKind.equals("shears") || (b.has("web") && it.toolKind.equals("sword"));
        if (b.tool.equals("sword")) return it.toolKind.equals("sword") || it.toolKind.equals("shears");
        return it.toolKind.equals(b.tool) && it.toolTier >= b.minTier;
    }

    public static double toolSpeed(int state, ItemStack tool) {
        if (tool == null || tool.item.toolKind == null) return 1.0;
        Block b = Blocks.block(state);
        String k = tool.item.toolKind;
        if (k.equals("shears")) {
            if (b.has("web") || b.id.endsWith("_leaves")) return 15.0;
            if (b.id.endsWith("_wool")) return 5.0;
            return 1.0;
        }
        if (k.equals("sword")) return b.has("web") ? 15.0 : (b.id.endsWith("_leaves") || b.id.equals("minecraft:pumpkin") || b.id.equals("minecraft:melon")) ? 1.5 : 1.0;
        return k.equals(b.tool) ? tool.item.toolSpeed : 1.0;
    }

    /** Damage dealt per tick (0..∞). */
    public static double damagePerTick(int state, ItemStack tool, boolean onGround, boolean submerged) {
        Block b = Blocks.block(state);
        if (b.unbreakable()) return 0;
        if (b.hardness == 0) return 1.0;
        double speed = toolSpeed(state, tool);
        if (!onGround) speed /= 5.0;
        if (submerged) speed /= 5.0;
        return speed / b.hardness / (canHarvest(state, tool) ? 30.0 : 100.0);
    }

    /** Ticks to break (Integer.MAX_VALUE when unbreakable). */
    public static int breakTicks(int state, ItemStack tool) {
        double d = damagePerTick(state, tool, true, false);
        if (d <= 0) return Integer.MAX_VALUE;
        if (d >= 1) return 1;
        return (int) Math.ceil(1.0 / d - 1e-9);
    }

    /** Loot for a broken block (Appendix BX). */
    public static List<ItemStack> drops(int state, ItemStack tool, Random rng) {
        List<ItemStack> out = new ArrayList<>();
        Block b = Blocks.block(state);
        if (!canHarvest(state, tool)) return out;
        boolean shears = tool != null && "shears".equals(tool.item.toolKind);
        if (shears && (b.id.endsWith("_leaves") || b.id.equals("minecraft:grass") || b.id.equals("minecraft:fern")
                || b.id.equals("minecraft:cobweb") || b.id.equals("minecraft:dead_bush"))) {
            Item self = Items.get(b.id.equals("minecraft:cobweb") ? "minecraft:cobweb" : b.id);
            if (self != null) out.add(new ItemStack(self, 1));
            return out;
        }
        if (b.baseState == Blocks.RED_BED && "head".equals(Blocks.value(state, "part"))) return out; // foot drops the bed
        List<Map<String, Object>> drops = b.drops;
        if (b.has("ripe_drops") && Blocks.prop(state, "age") >= 7) {
            drops = new ArrayList<>();
            for (Object o : mc.util.Json.arr(b.extra.get("ripe_drops"))) drops.add(mc.util.Json.obj(o));
        }
        if (drops == null) {
            Item self = Items.get(b.id);
            if (self != null) out.add(new ItemStack(self, 1));
            return out;
        }
        for (Map<String, Object> d : drops) {
            double chance = mc.util.Json.num(d, "chance", 1.0);
            String id = mc.util.Json.str(d, "item", null);
            if (rng.nextDouble() >= chance) {
                String alt = mc.util.Json.str(d, "else", null);
                if (alt != null) out.add(ItemStack.of(alt, 1));
                continue;
            }
            int mn = mc.util.Json.integer(d, "min", 1), mx = mc.util.Json.integer(d, "max", mn);
            int n = mn + (mx > mn ? rng.nextInt(mx - mn + 1) : 0);
            if (n > 0 && id != null) out.add(ItemStack.of(id, n));
        }
        out.removeIf(Objects::isNull);
        return out;
    }

    public static int xp(int state, Random rng) {
        Object o = Blocks.block(state).extra.get("xp");
        if (!(o instanceof List)) return 0;
        List<?> l = (List<?>) o;
        int a = ((Number) l.get(0)).intValue(), b = ((Number) l.get(1)).intValue();
        return a + (b > a ? rng.nextInt(b - a + 1) : 0);
    }
}
