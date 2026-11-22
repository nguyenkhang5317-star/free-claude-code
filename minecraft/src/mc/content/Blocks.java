package mc.content;

import mc.util.Json;

import java.util.*;

/** Block registry with global block-state IDs (Section 2.4). A block state is an integer; all properties come from
 * shared immutable per-state tables (no per-block objects, Section 2.3). */
public final class Blocks {
    private Blocks() {}

    public static final List<Block> ALL = new ArrayList<>();
    public static final Map<String, Block> BY_ID = new HashMap<>();
    public static Block[] stateBlock = new Block[0];
    public static int stateCount;
    // per-state fast tables
    public static byte[] opacity, emission, shape, layer;
    public static boolean[] opaqueCube, solidCollision, fluid, replaceable;

    // Frequently used states (resolved after load)
    public static int AIR, STONE, GRASS_BLOCK, DIRT, COBBLESTONE, BEDROCK, WATER, LAVA, SAND, RED_SAND, GRAVEL, SANDSTONE, OAK_LOG,
            OAK_LEAVES, SNOW, SNOW_BLOCK, ICE, PACKED_ICE, CLAY, NETHERRACK, GLOWSTONE, SOUL_SAND, SOUL_SOIL, END_STONE, OBSIDIAN,
            NETHER_PORTAL, FIRE, TORCH, FARMLAND, WHEAT, GRASS, FERN, DEAD_BUSH, CACTUS, SUGAR_CANE, PODZOL, MYCELIUM, TERRACOTTA,
            CRIMSON_NYLIUM, WARPED_NYLIUM, BASALT, BLACKSTONE, NETHER_WART_BLOCK, SHROOMLIGHT, CRIMSON_STEM, WARPED_STEM,
            RED_MUSHROOM, BROWN_MUSHROOM, PUMPKIN, MELON, CHEST, FURNACE, CRAFTING_TABLE, TNT, RED_BED, COBWEB, MOSSY_COBBLESTONE,
            SPAWNER, LADDER, GRANITE, DIORITE, ANDESITE, ANCIENT_DEBRIS;

    private static final Set<String> ALLOWED = new HashSet<>(Arrays.asList("id", "name", "hardness", "blast_resistance", "tool",
            "min_tier", "requires_tool", "light", "opacity", "layer", "shape", "sound", "textures", "drops", "flammable", "gravity",
            "slipperiness", "collision", "item", "properties", "tint", "random_tick", "interact", "support", "fluid", "tick_rate",
            "spread", "nether_spread", "replaceable", "contact_damage", "climbable", "web", "speed_factor", "xp", "lit_light",
            "lit_front", "ripe_drops"));
    private static final Set<String> EXTRA_KEYS = new HashSet<>(Arrays.asList("tint", "random_tick", "interact", "support", "fluid",
            "tick_rate", "spread", "nether_spread", "replaceable", "contact_damage", "climbable", "web", "speed_factor", "xp",
            "lit_light", "lit_front", "ripe_drops"));

    static void load(Map<String, Object> root) {
        ALL.clear();
        BY_ID.clear();
        int state = 0;
        for (Object o : Json.arr(root.get("blocks"))) {
            Map<String, Object> m = Json.obj(o);
            for (String k : m.keySet())
                if (!ALLOWED.contains(k)) throw new IllegalStateException("blocks.json: unknown field '" + k + "' in " + m.get("id"));
            String id = Json.str(m, "id", null);
            if (id == null || BY_ID.containsKey(id)) throw new IllegalStateException("blocks.json: bad or duplicate id " + id);
            Map<String, Object> t = Json.obj(m.get("textures"));
            String[] tex = {Json.str(t, "top", "stone"), Json.str(t, "side", "stone"), Json.str(t, "bottom", "stone"), Json.str(t, "front", "stone")};
            Object dr = m.get("drops");
            List<Map<String, Object>> drops;
            if ("self".equals(dr)) drops = null;
            else if ("none".equals(dr)) drops = Collections.emptyList();
            else {
                drops = new ArrayList<>();
                for (Object d : Json.arr(dr)) drops.add(Json.obj(d));
            }
            List<Object> fl = Json.arr(m.get("flammable"));
            LinkedHashMap<String, String[]> props = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : Json.obj(m.get("properties")).entrySet()) {
                List<Object> vals = Json.arr(e.getValue());
                String[] vs = new String[vals.size()];
                for (int i = 0; i < vs.length; i++) vs[i] = vals.get(i).toString();
                props.put(e.getKey(), vs);
            }
            Map<String, Object> extra = new HashMap<>();
            for (String k : EXTRA_KEYS) if (m.containsKey(k)) extra.put(k, m.get(k));
            Block b = new Block(ALL.size(), id, Json.str(m, "name", id), Json.num(m, "hardness", 1), Json.num(m, "blast_resistance", 1),
                    Json.str(m, "tool", "none"), Json.integer(m, "min_tier", 0), Json.bool(m, "requires_tool", false),
                    Json.integer(m, "light", 0), Json.integer(m, "opacity", 15), layerOf(Json.str(m, "layer", "solid")),
                    shapeOf(Json.str(m, "shape", "cube")), Json.str(m, "sound", "stone"), tex, drops,
                    fl.size() > 0 ? ((Number) fl.get(0)).intValue() : 0, fl.size() > 1 ? ((Number) fl.get(1)).intValue() : 0,
                    Json.bool(m, "gravity", false), Json.num(m, "slipperiness", 0.6), Json.bool(m, "collision", true),
                    Json.bool(m, "item", true), props, extra);
            b.baseState = state;
            state += b.stateCount;
            ALL.add(b);
            BY_ID.put(id, b);
        }
        if (state > 65535) throw new IllegalStateException("too many block states: " + state);
        stateCount = state;
        stateBlock = new Block[state];
        opacity = new byte[state]; emission = new byte[state]; shape = new byte[state]; layer = new byte[state];
        opaqueCube = new boolean[state]; solidCollision = new boolean[state]; fluid = new boolean[state]; replaceable = new boolean[state];
        for (Block b : ALL) {
            for (int s = b.baseState; s < b.baseState + b.stateCount; s++) {
                stateBlock[s] = b;
                opacity[s] = (byte) b.opacity;
                int em = b.light;
                if (b.has("lit_light") && b.propIndex("lit") >= 0 && "true".equals(value(s, "lit"))) em = (int) b.extraNum("lit_light", 0);
                emission[s] = (byte) em;
                shape[s] = (byte) b.shape;
                layer[s] = (byte) b.layer;
                opaqueCube[s] = b.isOpaqueCube();
                solidCollision[s] = b.collision && b.shape != Block.SHAPE_NONE;
                fluid[s] = b.has("fluid");
                replaceable[s] = b.shape == Block.SHAPE_NONE || Boolean.TRUE.equals(b.extra.get("replaceable"));
            }
        }
        resolveCommon();
    }

    private static int layerOf(String s) {
        switch (s) {
            case "none": return Block.LAYER_NONE;
            case "cutout": return Block.LAYER_CUTOUT;
            case "cutout_mipmap": return Block.LAYER_CUTOUT_MIPPED;
            case "translucent": return Block.LAYER_TRANSLUCENT;
            default: return Block.LAYER_SOLID;
        }
    }

    private static int shapeOf(String s) {
        switch (s) {
            case "none": return Block.SHAPE_NONE;
            case "cross": return Block.SHAPE_CROSS;
            case "torch": return Block.SHAPE_TORCH;
            case "slab": return Block.SHAPE_SLAB;
            case "snow_layer": return Block.SHAPE_SNOW;
            case "farmland": return Block.SHAPE_FARMLAND;
            case "cactus": return Block.SHAPE_CACTUS;
            case "fluid": return Block.SHAPE_FLUID;
            case "ladder": return Block.SHAPE_LADDER;
            case "bed": return Block.SHAPE_BED;
            case "portal": return Block.SHAPE_PORTAL;
            case "crop": return Block.SHAPE_CROP;
            default: return Block.SHAPE_CUBE;
        }
    }

    private static void resolveCommon() {
        AIR = s("air"); STONE = s("stone"); GRASS_BLOCK = s("grass_block"); DIRT = s("dirt"); COBBLESTONE = s("cobblestone");
        BEDROCK = s("bedrock"); WATER = s("water"); LAVA = s("lava"); SAND = s("sand"); RED_SAND = s("red_sand"); GRAVEL = s("gravel");
        SANDSTONE = s("sandstone"); OAK_LOG = s("oak_log"); OAK_LEAVES = s("oak_leaves"); SNOW = s("snow"); SNOW_BLOCK = s("snow_block");
        ICE = s("ice"); PACKED_ICE = s("packed_ice"); CLAY = s("clay"); NETHERRACK = s("netherrack"); GLOWSTONE = s("glowstone");
        SOUL_SAND = s("soul_sand"); SOUL_SOIL = s("soul_soil"); END_STONE = s("end_stone"); OBSIDIAN = s("obsidian");
        NETHER_PORTAL = s("nether_portal"); FIRE = s("fire"); TORCH = s("torch"); FARMLAND = s("farmland"); WHEAT = s("wheat");
        GRASS = s("grass"); FERN = s("fern"); DEAD_BUSH = s("dead_bush"); CACTUS = s("cactus"); SUGAR_CANE = s("sugar_cane");
        PODZOL = s("podzol"); MYCELIUM = s("mycelium"); TERRACOTTA = s("terracotta"); CRIMSON_NYLIUM = s("crimson_nylium");
        WARPED_NYLIUM = s("warped_nylium"); BASALT = s("basalt"); BLACKSTONE = s("blackstone"); NETHER_WART_BLOCK = s("nether_wart_block");
        SHROOMLIGHT = s("shroomlight"); CRIMSON_STEM = s("crimson_stem"); WARPED_STEM = s("warped_stem"); RED_MUSHROOM = s("red_mushroom");
        BROWN_MUSHROOM = s("brown_mushroom"); PUMPKIN = s("pumpkin"); MELON = s("melon"); CHEST = s("chest"); FURNACE = s("furnace");
        CRAFTING_TABLE = s("crafting_table"); TNT = s("tnt"); RED_BED = s("red_bed"); COBWEB = s("cobweb");
        MOSSY_COBBLESTONE = s("mossy_cobblestone"); SPAWNER = s("spawner"); LADDER = s("ladder"); GRANITE = s("granite");
        DIORITE = s("diorite"); ANDESITE = s("andesite"); ANCIENT_DEBRIS = s("ancient_debris");
    }

    private static int s(String name) {
        Block b = BY_ID.get("minecraft:" + name);
        if (b == null) throw new IllegalStateException("required block missing from data: minecraft:" + name);
        return b.baseState;
    }

    // ---------------- state helpers ----------------
    public static Block block(int state) { return state >= 0 && state < stateBlock.length ? stateBlock[state] : stateBlock[0]; }

    public static int base(String id) {
        Block b = BY_ID.get(id.contains(":") ? id : "minecraft:" + id);
        return b == null ? -1 : b.baseState;
    }

    public static int prop(int state, String name) {
        Block b = stateBlock[state];
        int p = b.propIndex(name);
        if (p < 0) return 0;
        return ((state - b.baseState) / b.propStrides[p]) % b.propValues[p].length;
    }

    public static String value(int state, String name) {
        Block b = stateBlock[state];
        int p = b.propIndex(name);
        if (p < 0) return null;
        return b.propValues[p][prop(state, name)];
    }

    public static int with(int state, String name, int valueIndex) {
        Block b = stateBlock[state];
        int p = b.propIndex(name);
        if (p < 0) return state;
        int cur = prop(state, name);
        valueIndex = Math.max(0, Math.min(b.propValues[p].length - 1, valueIndex));
        return state + (valueIndex - cur) * b.propStrides[p];
    }

    public static int with(int state, String name, String value) {
        Block b = stateBlock[state];
        int p = b.propIndex(name);
        if (p < 0) return state;
        String[] vs = b.propValues[p];
        for (int i = 0; i < vs.length; i++) if (vs[i].equals(value)) return with(state, name, i);
        return state;
    }

    public static boolean is(int state, int baseState) { return stateBlock[state].baseState == baseState; }

    /** Canonical name, e.g. minecraft:furnace[facing=north,lit=false] (registry manifest, Appendix O.3). */
    public static String stateName(int state) {
        Block b = stateBlock[state];
        if (b.propNames.length == 0) return b.id;
        StringBuilder sb = new StringBuilder(b.id).append('[');
        for (int i = 0; i < b.propNames.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(b.propNames[i]).append('=').append(b.propValues[i][((state - b.baseState) / b.propStrides[i]) % b.propValues[i].length]);
        }
        return sb.append(']').toString();
    }

    public static int parseState(String name) {
        int br = name.indexOf('[');
        String id = br < 0 ? name : name.substring(0, br);
        int st = base(id);
        if (st < 0) return -1;
        if (br >= 0) {
            for (String kv : name.substring(br + 1, name.length() - 1).split(",")) {
                String[] p = kv.split("=");
                if (p.length == 2) st = with(st, p[0].trim(), p[1].trim());
            }
        }
        return st;
    }

    public static boolean isFluid(int s) { return fluid[s]; }

    public static boolean isWater(int s) { return stateBlock[s].baseState == WATER; }

    public static boolean isLava(int s) { return stateBlock[s].baseState == LAVA; }

    public static boolean isAir(int s) { return s == AIR; }
}
