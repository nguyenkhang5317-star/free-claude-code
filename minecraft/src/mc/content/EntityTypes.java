package mc.content;

import mc.util.Json;

import java.util.*;

/** Entity type registry (data/entities.json). */
public final class EntityTypes {
    private EntityTypes() {}

    public static final List<EntityType> ALL = new ArrayList<>();
    public static final Map<String, EntityType> BY_ID = new HashMap<>();
    public static EntityType PLAYER, ITEM, XP_ORB, ARROW, TNT, FALLING_BLOCK;
    private static final Set<String> ALLOWED = new HashSet<>(Arrays.asList("id", "name", "health", "damage", "speed", "width",
            "height", "eye", "category", "xp", "drops", "goals", "sounds", "model", "animations", "spawn", "breed_item", "interact",
            "slow_fall", "lays_eggs", "undead", "burns_in_sun", "ranged", "fuse", "explosion", "climbs", "neutral_in_light"));

    static void load(Map<String, Object> root) {
        ALL.clear();
        BY_ID.clear();
        for (Object o : Json.arr(root.get("entities"))) {
            Map<String, Object> m = Json.obj(o);
            for (String k : m.keySet())
                if (!ALLOWED.contains(k)) throw new IllegalStateException("entities.json: unknown field '" + k + "' in " + m.get("id"));
            String id = Json.str(m, "id", null);
            List<Object> dmg = Json.arr(m.get("damage"));
            double[] damage = new double[3];
            for (int i = 0; i < 3 && i < dmg.size(); i++) damage[i] = ((Number) dmg.get(i)).doubleValue();
            List<Object> xp = Json.arr(m.get("xp"));
            List<Map<String, Object>> drops = new ArrayList<>();
            for (Object d : Json.arr(m.get("drops"))) {
                Map<String, Object> dm = Json.obj(d);
                if (Items.get(Json.str(dm, "item", "")) == null)
                    throw new IllegalStateException("entities.json: " + id + " drops unknown item " + dm.get("item"));
                drops.add(dm);
            }
            List<Object[]> goals = new ArrayList<>();
            for (Object g : Json.arr(m.get("goals"))) {
                List<Object> gl = Json.arr(g);
                goals.add(new Object[]{((Number) gl.get(0)).intValue(), gl.get(1).toString()});
            }
            goals.sort(Comparator.comparingInt(a -> (Integer) a[0]));
            Map<String, String> sounds = new HashMap<>();
            for (Map.Entry<String, Object> e : Json.obj(m.get("sounds")).entrySet()) sounds.put(e.getKey(), e.getValue().toString());
            List<EntityType.Part> model = new ArrayList<>();
            for (Object p : Json.arr(m.get("model"))) {
                Map<String, Object> pm = Json.obj(p);
                double[] or = vec(pm.get("origin")), sz = vec(pm.get("size"));
                double[] pv = pm.containsKey("pivot") ? vec(pm.get("pivot")) : new double[]{or[0] + sz[0] / 2, or[1] + sz[1], or[2] + sz[2] / 2};
                model.add(new EntityType.Part(Json.str(pm, "name", "part"), or, sz, pv, color(Json.str(pm, "color", "#888888")),
                        color(Json.str(pm, "color2", Json.str(pm, "color", "#888888"))), Json.str(pm, "pattern", "noise"),
                        pm.containsKey("face") ? Json.obj(pm.get("face")) : null));
            }
            Map<String, Object> anims = Json.obj(m.get("animations"));
            List<EntityType.Anim> walk = new ArrayList<>();
            for (Map.Entry<String, Object> e : Json.obj(anims.get("walk")).entrySet()) {
                Map<String, Object> a = Json.obj(e.getValue());
                String ax = Json.str(a, "axis", "x");
                walk.add(new EntityType.Anim(e.getKey(), ax.equals("x") ? 0 : ax.equals("y") ? 1 : 2, Json.num(a, "amp", 1),
                        Json.num(a, "phase", 0), Json.num(a, "offset", 0)));
            }
            Map<String, String> attach = new HashMap<>();
            for (Map.Entry<String, Object> e : Json.obj(anims.get("attachments")).entrySet()) attach.put(e.getKey(), e.getValue().toString());
            EntityType t = new EntityType(ALL.size(), id, Json.str(m, "name", id), Json.num(m, "health", 10), Json.num(m, "speed", 0.25),
                    Json.num(m, "width", 0.6), Json.num(m, "height", 1.8), Json.num(m, "eye", 1.5), damage, Json.str(m, "category", "misc"),
                    xp.size() > 0 ? ((Number) xp.get(0)).intValue() : 0, xp.size() > 1 ? ((Number) xp.get(1)).intValue() : 0,
                    drops, goals, sounds, model, walk, attach, Json.obj(m.get("spawn")), m);
            ALL.add(t);
            BY_ID.put(id, t);
        }
        PLAYER = req("minecraft:player"); ITEM = req("minecraft:item"); XP_ORB = req("minecraft:experience_orb");
        ARROW = req("minecraft:arrow"); TNT = req("minecraft:tnt"); FALLING_BLOCK = req("minecraft:falling_block");
    }

    private static EntityType req(String id) {
        EntityType t = BY_ID.get(id);
        if (t == null) throw new IllegalStateException("entities.json missing " + id);
        return t;
    }

    private static double[] vec(Object o) {
        List<Object> l = Json.arr(o);
        return new double[]{((Number) l.get(0)).doubleValue(), ((Number) l.get(1)).doubleValue(), ((Number) l.get(2)).doubleValue()};
    }

    public static int color(String hex) {
        if (hex == null || hex.length() < 7) return 0x888888;
        return Integer.parseInt(hex.substring(1, 7), 16);
    }

    public static EntityType get(String id) {
        if (id == null) return null;
        return BY_ID.get(id.contains(":") ? id : "minecraft:" + id);
    }
}
