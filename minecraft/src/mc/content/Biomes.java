package mc.content;

import mc.util.Json;

import java.util.*;

/** Biome registry. */
public final class Biomes {
    private Biomes() {}

    public static final List<Biome> ALL = new ArrayList<>();
    public static final Map<String, Biome> BY_ID = new HashMap<>();

    static void load(Map<String, Object> root) {
        ALL.clear();
        BY_ID.clear();
        for (Object o : Json.arr(root.get("biomes"))) {
            Map<String, Object> m = Json.obj(o);
            String id = Json.str(m, "id", null);
            List<String> trees = new ArrayList<>();
            for (Object t : Json.arr(m.get("trees"))) trees.add(t.toString());
            Map<String, List<Object[]>> spawns = new HashMap<>();
            for (Map.Entry<String, Object> e : Json.obj(m.get("spawns")).entrySet()) {
                List<Object[]> l = new ArrayList<>();
                for (Object s : Json.arr(e.getValue())) {
                    Map<String, Object> sm = Json.obj(s);
                    EntityType t = EntityTypes.get(Json.str(sm, "type", ""));
                    if (t == null) throw new IllegalStateException("biomes.json: unknown spawn type " + sm.get("type") + " in " + id);
                    l.add(new Object[]{t, Json.integer(sm, "weight", 1)});
                }
                spawns.put(e.getKey(), l);
            }
            Biome b = new Biome(ALL.size(), id, Json.num(m, "temperature", 0.5), Json.num(m, "humidity", 0.5),
                    st(m, "top", id), st(m, "filler", id), st(m, "underwater", id),
                    EntityTypes.color(Json.str(m, "grass_color", "#91BD59")), EntityTypes.color(Json.str(m, "foliage_color", "#77AB2F")),
                    trees, Json.num(m, "tree_density", 0), Json.num(m, "flower_density", 0), Json.num(m, "grass_density", 0),
                    Json.num(m, "terrain_amp", 1), spawns, Json.str(m, "precipitation", "rain"), m);
            ALL.add(b);
            BY_ID.put(id, b);
        }
    }

    private static int st(Map<String, Object> m, String k, String id) {
        String v = Json.str(m, k, "minecraft:stone");
        int s = Blocks.base(v);
        if (s < 0) throw new IllegalStateException("biomes.json: unknown block " + v + " in " + id);
        return s;
    }

    public static Biome get(String id) { return BY_ID.get(id.contains(":") ? id : "minecraft:" + id); }
}
