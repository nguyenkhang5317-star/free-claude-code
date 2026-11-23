package mc.core;

import mc.content.*;

import java.util.*;

/** Stateful block data (chests, furnaces) stored per chunk keyed by packed local coordinate (Section 2.3). */
public class BlockEntity {
    public final String type;
    public final int x, y, z;
    public final ItemStack[] items;
    // furnace state
    public int burnTime, burnTotal, cookTime, cookTotal = 200;
    public double storedXp;

    public BlockEntity(String type, int x, int y, int z) {
        this.type = type;
        this.x = x; this.y = y; this.z = z;
        this.items = new ItemStack[type.equals("chest") ? 27 : type.equals("furnace") ? 3 : 0];
    }

    public static int localKey(int x, int y, int z) { return (y << 8) | ((z & 15) << 4) | (x & 15); }

    /** Furnace tick (Section 7.2): slot 0 input, 1 fuel, 2 output. Returns true when lit state changed. */
    public boolean tickFurnace() {
        boolean wasLit = burnTime > 0;
        if (burnTime > 0) burnTime--;
        ItemStack in = items[0];
        Recipes.Smelting r = in == null ? null : Recipes.smelting(in.item);
        boolean canCook = r != null && (items[2] == null || (items[2].item == r.result && items[2].count < items[2].max()));
        if (burnTime == 0 && canCook) {
            int f = items[1] == null ? 0 : Recipes.fuel(items[1].item);
            if (f > 0) {
                burnTime = burnTotal = f;
                if (items[1].item.id.equals("minecraft:lava_bucket")) items[1] = new ItemStack(Items.get("minecraft:bucket"), 1);
                else if (--items[1].count <= 0) items[1] = null;
            }
        }
        if (burnTime > 0 && canCook) {
            cookTotal = r.cookTicks;
            if (++cookTime >= cookTotal) {
                cookTime = 0;
                if (items[2] == null) items[2] = new ItemStack(r.result, 1);
                else items[2].count++;
                storedXp += r.xp;
                if (--items[0].count <= 0) items[0] = null;
            }
        } else if (cookTime > 0) {
            cookTime = Math.max(0, cookTime - 2);
        }
        return wasLit != (burnTime > 0);
    }

    public Map<String, Object> save() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("x", x); m.put("y", y); m.put("z", z);
        m.put("items", Inventory.saveStacks(items));
        m.put("burn", burnTime); m.put("burn_total", burnTotal); m.put("cook", cookTime); m.put("xp", storedXp);
        return m;
    }

    public static BlockEntity load(Map<String, Object> m) {
        BlockEntity be = new BlockEntity(mc.util.Json.str(m, "type", "chest"), mc.util.Json.integer(m, "x", 0),
                mc.util.Json.integer(m, "y", 0), mc.util.Json.integer(m, "z", 0));
        Inventory.loadStacks(m.get("items"), be.items);
        be.burnTime = mc.util.Json.integer(m, "burn", 0);
        be.burnTotal = mc.util.Json.integer(m, "burn_total", 0);
        be.cookTime = mc.util.Json.integer(m, "cook", 0);
        be.storedXp = mc.util.Json.num(m, "xp", 0);
        return be;
    }
}
