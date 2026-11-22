package mc.core;

import mc.content.*;
import mc.util.Json;

import java.util.*;

/** Player inventory: 36 main slots (0-8 hotbar), 4 armor slots, 1 offhand. */
public final class Inventory {
    public final ItemStack[] main = new ItemStack[36];
    public final ItemStack[] armor = new ItemStack[4]; // helmet, chest, legs, boots
    public final ItemStack[] offhandSlot = new ItemStack[1];

    public ItemStack offhand() { return offhandSlot[0]; }

    public void setOffhand(ItemStack s) { offhandSlot[0] = s; }
    public int selected;

    public ItemStack held() { return main[selected]; }

    public void setHeld(ItemStack s) { main[selected] = s; }

    /** Adds a stack, returns leftover (null when fully inserted). Hotbar first, then main. */
    public ItemStack add(ItemStack s) {
        if (ItemStack.isEmpty(s)) return null;
        s = s.copy();
        for (int i = 0; i < 36 && s.count > 0; i++) {
            ItemStack m = main[i];
            if (m != null && m.canStack(s) && m.count < m.max()) {
                int mv = Math.min(s.count, m.max() - m.count);
                m.count += mv;
                s.count -= mv;
            }
        }
        for (int i = 0; i < 36 && s.count > 0; i++) {
            if (main[i] == null) {
                int mv = Math.min(s.count, s.max());
                main[i] = s.copy(mv);
                s.count -= mv;
            }
        }
        return s.count > 0 ? s : null;
    }

    public int count(Item it) {
        int n = 0;
        for (ItemStack s : main) if (s != null && s.item == it) n += s.count;
        return n;
    }

    public int armorPoints() {
        int p = 0;
        for (ItemStack s : armor) if (s != null && s.item.isArmor()) p += s.item.armorPoints;
        return p;
    }

    public double armorToughness() {
        double t = 0;
        for (ItemStack s : armor) if (s != null && s.item.isArmor()) t += s.item.armorToughness;
        return t;
    }

    public void clear() {
        Arrays.fill(main, null);
        Arrays.fill(armor, null);
        offhandSlot[0] = null;
    }

    public List<ItemStack> takeAll() {
        List<ItemStack> l = new ArrayList<>();
        for (ItemStack s : main) if (s != null) l.add(s);
        for (ItemStack s : armor) if (s != null) l.add(s);
        if (offhandSlot[0] != null) l.add(offhandSlot[0]);
        clear();
        return l;
    }

    // ---------- persistence ----------
    public static List<Object> saveStacks(ItemStack[] arr) {
        List<Object> l = new ArrayList<>();
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("slot", i);
            m.put("id", arr[i].item.id);
            m.put("count", arr[i].count);
            if (arr[i].damage > 0) m.put("damage", arr[i].damage);
            l.add(m);
        }
        return l;
    }

    public static void loadStacks(Object o, ItemStack[] arr) {
        Arrays.fill(arr, null);
        for (Object e : Json.arr(o)) {
            Map<String, Object> m = Json.obj(e);
            int slot = Json.integer(m, "slot", -1);
            Item it = Items.get(Json.str(m, "id", ""));
            if (it != null && slot >= 0 && slot < arr.length)
                arr[slot] = new ItemStack(it, Json.integer(m, "count", 1), Json.integer(m, "damage", 0));
        }
    }

    public Map<String, Object> save() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("main", saveStacks(main));
        m.put("armor", saveStacks(armor));
        m.put("offhand", saveStacks(offhandSlot));
        m.put("selected", selected);
        return m;
    }

    public void load(Map<String, Object> m) {
        loadStacks(m.get("main"), main);
        loadStacks(m.get("armor"), armor);
        ItemStack[] oh = new ItemStack[1];
        loadStacks(m.get("offhand"), oh);
        offhandSlot[0] = oh[0];
        selected = Json.integer(m, "selected", 0);
    }
}
