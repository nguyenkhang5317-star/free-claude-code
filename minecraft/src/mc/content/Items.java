package mc.content;

import mc.util.Json;

import java.util.*;

/** Item registry (Section 2.4 / 7.1). */
public final class Items {
    private Items() {}

    public static final List<Item> ALL = new ArrayList<>();
    public static final Map<String, Item> BY_ID = new HashMap<>();
    private static final Set<String> ALLOWED = new HashSet<>(Arrays.asList("id", "name", "max_stack", "icon", "tool", "durability",
            "attack_damage", "attack_speed", "armor", "food", "places", "use", "spawns", "ranged"));

    static void load(Map<String, Object> root) {
        ALL.clear();
        BY_ID.clear();
        // Block items first (same id as block)
        for (Block b : Blocks.ALL) {
            if (!b.hasItem) continue;
            add(new Item(ALL.size(), b.id, b.name, 64, 0, null, 0, 1, 1, 4, -1, 0, 0, 0, 0, null, b.baseState, null, null,
                    "block", new String[0], b));
        }
        for (Object o : Json.arr(root.get("items"))) {
            Map<String, Object> m = Json.obj(o);
            for (String k : m.keySet())
                if (!ALLOWED.contains(k)) throw new IllegalStateException("items.json: unknown field '" + k + "' in " + m.get("id"));
            String id = Json.str(m, "id", null);
            if (id == null || BY_ID.containsKey(id)) throw new IllegalStateException("items.json: bad or duplicate id " + id);
            Map<String, Object> tool = Json.obj(m.get("tool"));
            Map<String, Object> armor = Json.obj(m.get("armor"));
            Map<String, Object> food = Json.obj(m.get("food"));
            Map<String, Object> icon = Json.obj(m.get("icon"));
            List<Object> cols = Json.arr(icon.get("colors"));
            String[] ic = new String[cols.size()];
            for (int i = 0; i < ic.length; i++) ic[i] = cols.get(i).toString();
            List<Map<String, Object>> effects = new ArrayList<>();
            for (Object e : Json.arr(food.get("effects"))) effects.add(Json.obj(e));
            String places = Json.str(m, "places", null);
            int placeState = -1;
            if (places != null) {
                placeState = Blocks.base(places);
                if (placeState < 0) throw new IllegalStateException("items.json: " + id + " places unknown block " + places);
            }
            add(new Item(ALL.size(), id, Json.str(m, "name", id), Json.integer(m, "max_stack", 64), Json.integer(m, "durability", 0),
                    tool.isEmpty() ? null : Json.str(tool, "kind", null), Json.integer(tool, "tier", 0), Json.num(tool, "speed", 1),
                    Json.num(m, "attack_damage", 1), Json.num(m, "attack_speed", 4),
                    armor.isEmpty() ? -1 : Json.integer(armor, "slot", 0), Json.integer(armor, "points", 0), Json.num(armor, "toughness", 0),
                    Json.integer(food, "hunger", 0), Json.num(food, "saturation", 0), effects, placeState, Json.str(m, "use", null),
                    Json.str(m, "spawns", null), Json.str(icon, "shape", "generic"), ic, null)).eatTicks = Json.integer(food, "eat_ticks", 32);
        }
    }

    private static Item add(Item it) {
        ALL.add(it);
        BY_ID.put(it.id, it);
        return it;
    }

    public static Item get(String id) {
        if (id == null) return null;
        return BY_ID.get(id.contains(":") ? id : "minecraft:" + id);
    }

    public static Item forBlockState(int state) {
        Block b = Blocks.block(state);
        return BY_ID.get(b.id);
    }
}
