package mc.core;

import mc.content.*;
import mc.entity.Player;

import java.util.*;

/**
 * Server-side container menus (Section 7.2 / 14.3): player inventory with 2x2 crafting, crafting table (3x3),
 * furnace and chest. Slot coordinates follow the classic 176x166 GUI layout and are mirrored by the client.
 * Clicks: left/right pick-place-swap-split, shift quick-move, result slots consume ingredients.
 */
public final class Menu {
    public static final int INVENTORY = 0, CRAFTING = 1, FURNACE = 2, CHEST = 3;
    public static final int S_NORMAL = 0, S_RESULT = 1, S_OUTPUT = 2, S_ARMOR = 3; // armor: S_ARMOR + slot(0..3)
    private static int NEXT_ID = 1;

    public static final class Slot {
        public final ItemStack[] arr;
        public final int index, type, x, y;
        public final boolean playerInv;

        Slot(ItemStack[] arr, int index, int type, int x, int y, boolean playerInv) {
            this.arr = arr; this.index = index; this.type = type; this.x = x; this.y = y; this.playerInv = playerInv;
        }

        public ItemStack get() { return arr[index]; }

        public void set(ItemStack s) { arr[index] = ItemStack.isEmpty(s) ? null : s; }

        boolean accepts(ItemStack s) {
            if (type == S_RESULT || type == S_OUTPUT) return false;
            if (type >= S_ARMOR && type < S_ARMOR + 4) return s.item.isArmor() && s.item.armorSlot == type - S_ARMOR;
            return true;
        }

        int max(ItemStack s) { return type >= S_ARMOR && type < S_ARMOR + 4 ? 1 : s.max(); }
    }

    public final int kind, windowId;
    public final List<Slot> slots = new ArrayList<>();
    public final ItemStack[] grid;
    public final int gridSize;
    public final ItemStack[] result = new ItemStack[1];
    public final BlockEntity be;
    public final int bx, by, bz;
    public final String title;

    private Menu(int kind, ItemStack[] grid, int gridSize, BlockEntity be, int bx, int by, int bz, String title) {
        this.kind = kind;
        this.windowId = kind == INVENTORY ? 0 : NEXT_ID++;
        this.grid = grid;
        this.gridSize = gridSize;
        this.be = be;
        this.bx = bx; this.by = by; this.bz = bz;
        this.title = title;
    }

    private void addPlayer(Inventory inv) {
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++) slots.add(new Slot(inv.main, 9 + r * 9 + c, S_NORMAL, 8 + c * 18, 84 + r * 18, true));
        for (int c = 0; c < 9; c++) slots.add(new Slot(inv.main, c, S_NORMAL, 8 + c * 18, 142, true));
    }

    public static Menu inventory(Player p) {
        Menu m = new Menu(INVENTORY, p.craft2, 2, null, 0, 0, 0, "Crafting");
        m.slots.add(new Slot(m.result, 0, S_RESULT, 154, 28, false));
        for (int r = 0; r < 2; r++) for (int c = 0; c < 2; c++) m.slots.add(new Slot(p.craft2, r * 2 + c, S_NORMAL, 98 + c * 18, 18 + r * 18, false));
        for (int i = 0; i < 4; i++) m.slots.add(new Slot(p.inventory.armor, i, S_ARMOR + i, 8, 8 + i * 18, true));
        m.addPlayer(p.inventory);
        m.slots.add(new Slot(p.inventory.offhandSlot, 0, S_NORMAL, 77, 62, true));
        m.updateResult();
        return m;
    }

    public static Menu crafting(Player p, int x, int y, int z) {
        Menu m = new Menu(CRAFTING, new ItemStack[9], 3, null, x, y, z, "Crafting");
        m.slots.add(new Slot(m.result, 0, S_RESULT, 124, 35, false));
        for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++) m.slots.add(new Slot(m.grid, r * 3 + c, S_NORMAL, 30 + c * 18, 17 + r * 18, false));
        m.addPlayer(p.inventory);
        return m;
    }

    public static Menu furnace(Player p, BlockEntity be) {
        Menu m = new Menu(FURNACE, null, 0, be, be.x, be.y, be.z, "Furnace");
        m.slots.add(new Slot(be.items, 0, S_NORMAL, 56, 17, false));
        m.slots.add(new Slot(be.items, 1, S_NORMAL, 56, 53, false));
        m.slots.add(new Slot(be.items, 2, S_OUTPUT, 116, 35, false));
        m.addPlayer(p.inventory);
        return m;
    }

    public static Menu chest(Player p, BlockEntity be) {
        Menu m = new Menu(CHEST, null, 0, be, be.x, be.y, be.z, "Chest");
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) m.slots.add(new Slot(be.items, r * 9 + c, S_NORMAL, 8 + c * 18, 18 + r * 18, false));
        m.addPlayer(p.inventory);
        return m;
    }

    public boolean stillValid(Player p) {
        if (kind == INVENTORY) return true;
        if (p.distSq(bx + 0.5, by + 0.5, bz + 0.5) > 64) return false;
        int s = p.world.getBlock(bx, by, bz);
        Block b = Blocks.block(s);
        String inter = b.extraStr("interact");
        if (kind == CRAFTING) return "crafting".equals(inter);
        return p.world.blockEntity(bx, by, bz) == be;
    }

    public void updateResult() {
        if (grid == null) return;
        result[0] = Recipes.match(grid, gridSize);
    }

    // ------------------------------------------------------------------ clicks
    public void click(Player p, int slotIndex, int button, boolean shift) {
        if (slotIndex == -999) {
            if (p.carried != null) {
                if (button == 0) { p.dropStack(p.carried); p.carried = null; }
                else { p.dropStack(p.carried.copy(1)); if (--p.carried.count <= 0) p.carried = null; }
            }
            return;
        }
        if (slotIndex < 0 || slotIndex >= slots.size()) return;
        Slot s = slots.get(slotIndex);
        if (s.type == S_RESULT) { takeResult(p, s, shift); return; }
        if (s.type == S_OUTPUT) { takeOutput(p, s, shift); return; }
        if (shift) { quickMove(p, slotIndex); afterChange(); return; }
        ItemStack in = s.get(), c = p.carried;
        if (button == 0) {
            if (c == null) { p.carried = in; s.set(null); }
            else if (in == null) {
                if (s.accepts(c)) {
                    int n = Math.min(c.count, s.max(c));
                    s.set(c.copy(n));
                    c.count -= n;
                    if (c.count <= 0) p.carried = null;
                }
            } else if (in.canStack(c)) {
                int n = Math.min(c.count, s.max(in) - in.count);
                in.count += n;
                c.count -= n;
                if (c.count <= 0) p.carried = null;
            } else if (s.accepts(c) && c.count <= s.max(c)) { s.set(c); p.carried = in; }
        } else {
            if (c == null) {
                if (in != null) {
                    int half = (in.count + 1) / 2;
                    p.carried = in.copy(half);
                    in.count -= half;
                    if (in.count <= 0) s.set(null);
                }
            } else if (in == null) {
                if (s.accepts(c)) { s.set(c.copy(1)); if (--c.count <= 0) p.carried = null; }
            } else if (in.canStack(c)) {
                if (in.count < s.max(in)) { in.count++; if (--c.count <= 0) p.carried = null; }
            } else if (s.accepts(c) && c.count <= s.max(c)) { s.set(c); p.carried = in; }
        }
        afterChange();
    }

    private void afterChange() { updateResult(); }

    private void takeResult(Player p, Slot s, boolean shift) {
        int guard = 0;
        do {
            ItemStack r = result[0];
            if (r == null) return;
            if (shift) {
                ItemStack rest = p.inventory.add(r);
                if (rest != null) {
                    if (rest.count == r.count) return;
                    p.dropStack(rest);
                }
            } else {
                if (p.carried == null) p.carried = r.copy();
                else if (p.carried.canStack(r) && p.carried.count + r.count <= p.carried.max()) p.carried.count += r.count;
                else return;
            }
            consumeGrid(p);
            p.onCrafted(r);
            updateResult();
        } while (shift && ++guard < 64);
    }

    private void consumeGrid(Player p) {
        for (int i = 0; i < grid.length; i++) {
            ItemStack g = grid[i];
            if (g == null) continue;
            Item remainder = null;
            if (g.item.id.endsWith("_bucket") && !g.item.id.equals("minecraft:bucket")) remainder = Items.get("minecraft:bucket");
            if (--g.count <= 0) grid[i] = remainder == null ? null : new ItemStack(remainder, 1);
            else if (remainder != null) p.giveOrDrop(new ItemStack(remainder, 1));
        }
    }

    private void takeOutput(Player p, Slot s, boolean shift) {
        ItemStack out = s.get();
        if (out == null) return;
        int taken;
        if (shift) {
            ItemStack rest = p.inventory.add(out);
            taken = out.count - (rest == null ? 0 : rest.count);
            s.set(rest);
        } else {
            if (p.carried == null) { p.carried = out; s.set(null); taken = out.count; }
            else if (p.carried.canStack(out) && p.carried.count + out.count <= p.carried.max()) { p.carried.count += out.count; s.set(null); taken = out.count; }
            else return;
        }
        if (taken > 0 && be != null && be.storedXp > 0) {
            int xp = (int) Math.floor(be.storedXp);
            double frac = be.storedXp - xp;
            if (Math.random() < frac) xp++;
            be.storedXp = 0;
            if (xp > 0) p.world.spawnXp(p.x, p.y + 0.5, p.z, xp);
        }
    }

    private void quickMove(Player p, int idx) {
        Slot s = slots.get(idx);
        ItemStack st = s.get();
        if (st == null) return;
        List<Slot> targets = new ArrayList<>();
        if (!s.playerInv) {
            for (Slot o : slots) if (o.playerInv && o.type == S_NORMAL && o.arr == p.inventory.main) targets.add(o);
            Collections.reverse(targets); // hotbar first like vanilla (fill from the end)
        } else {
            if (kind == FURNACE) {
                if (Recipes.smelting(st.item) != null) targets.add(slots.get(0));
                else if (Recipes.fuel(st.item) > 0) targets.add(slots.get(1));
            } else if (kind == CHEST) {
                for (Slot o : slots) if (!o.playerInv) targets.add(o);
            } else if (kind == INVENTORY && st.item.isArmor()) {
                for (Slot o : slots) if (o.type == S_ARMOR + st.item.armorSlot && o.get() == null) targets.add(o);
            }
            if (targets.isEmpty()) {
                boolean hotbar = s.arr == p.inventory.main && s.index < 9;
                for (Slot o : slots)
                    if (o.arr == p.inventory.main && o.type == S_NORMAL && (hotbar ? o.index >= 9 : o.index < 9)) targets.add(o);
            }
        }
        // merge into existing stacks first, then empty slots
        for (Slot t : targets) {
            ItemStack ts = t.get();
            if (ts != null && ts.canStack(st) && ts.count < t.max(ts)) {
                int n = Math.min(st.count, t.max(ts) - ts.count);
                ts.count += n;
                st.count -= n;
                if (st.count <= 0) { s.set(null); return; }
            }
        }
        for (Slot t : targets) {
            if (t.get() == null && t.accepts(st)) {
                int n = Math.min(st.count, t.max(st));
                t.set(st.copy(n));
                st.count -= n;
                if (st.count <= 0) { s.set(null); return; }
            }
        }
    }

    /** Close: return crafting grid contents and the carried stack to the player. */
    public void close(Player p) {
        if (grid != null) {
            for (int i = 0; i < grid.length; i++) if (grid[i] != null) { p.giveOrDrop(grid[i]); grid[i] = null; }
            updateResult();
        }
        if (p.carried != null) { p.giveOrDrop(p.carried); p.carried = null; }
    }

    /** Snapshot of slot contents for the client (copies). */
    public ItemStack[] snapshot() {
        ItemStack[] out = new ItemStack[slots.size()];
        for (int i = 0; i < out.length; i++) { ItemStack s = slots.get(i).get(); out[i] = s == null ? null : s.copy(); }
        return out;
    }
}
