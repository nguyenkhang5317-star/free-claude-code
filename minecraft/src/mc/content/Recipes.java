package mc.content;

import mc.util.Json;

import java.util.*;

/** Crafting, smelting and fuel registry (Section 7.2, Appendix A). Shaped patterns match anywhere in the grid and
 * also mirrored on the x-axis. */
public final class Recipes {
    private Recipes() {}

    public static final class Shaped {
        public final int w, h;
        public final Set<Item>[] cells; // null = empty cell
        public final Item result;
        public final int count;

        Shaped(int w, int h, Set<Item>[] cells, Item result, int count) {
            this.w = w; this.h = h; this.cells = cells; this.result = result; this.count = count;
        }
    }

    public static final class Shapeless {
        public final List<Set<Item>> ingredients;
        public final Item result;
        public final int count;

        Shapeless(List<Set<Item>> ingredients, Item result, int count) {
            this.ingredients = ingredients; this.result = result; this.count = count;
        }
    }

    public static final class Smelting {
        public final Set<Item> input;
        public final Item result;
        public final double xp;
        public final int cookTicks;

        Smelting(Set<Item> input, Item result, double xp, int cookTicks) {
            this.input = input; this.result = result; this.xp = xp; this.cookTicks = cookTicks;
        }
    }

    public static final List<Shaped> SHAPED = new ArrayList<>();
    public static final List<Shapeless> SHAPELESS = new ArrayList<>();
    public static final List<Smelting> SMELTING = new ArrayList<>();
    public static final Map<Item, Integer> FUEL = new HashMap<>();
    public static final Map<String, Set<Item>> TAGS = new HashMap<>();

    static void load(Map<String, Object> root) {
        SHAPED.clear(); SHAPELESS.clear(); SMELTING.clear(); FUEL.clear(); TAGS.clear();
        for (Map.Entry<String, Object> e : Json.obj(root.get("tags")).entrySet()) {
            Set<Item> s = new LinkedHashSet<>();
            for (Object o : Json.arr(e.getValue())) s.add(req(o.toString(), "tag " + e.getKey()));
            TAGS.put(e.getKey(), s);
        }
        for (Object o : Json.arr(root.get("recipes"))) {
            Map<String, Object> m = Json.obj(o);
            String type = Json.str(m, "type", "");
            Item result = req(Json.str(m, "result", ""), "recipe result");
            switch (type) {
                case "shaped": {
                    List<Object> pat = Json.arr(m.get("pattern"));
                    Map<String, Object> key = Json.obj(m.get("key"));
                    int h = pat.size(), w = 0;
                    for (Object r : pat) w = Math.max(w, r.toString().length());
                    @SuppressWarnings("unchecked") Set<Item>[] cells = new Set[w * h];
                    for (int y = 0; y < h; y++) {
                        String row = pat.get(y).toString();
                        for (int x = 0; x < w; x++) {
                            char c = x < row.length() ? row.charAt(x) : ' ';
                            if (c != ' ') {
                                Object k = key.get(String.valueOf(c));
                                if (k == null) throw new IllegalStateException("recipe " + result.id + ": key '" + c + "' undefined");
                                cells[y * w + x] = ingredient(k.toString());
                            }
                        }
                    }
                    SHAPED.add(new Shaped(w, h, cells, result, Json.integer(m, "count", 1)));
                    break;
                }
                case "shapeless": {
                    List<Set<Item>> ing = new ArrayList<>();
                    for (Object i : Json.arr(m.get("ingredients"))) ing.add(ingredient(i.toString()));
                    SHAPELESS.add(new Shapeless(ing, result, Json.integer(m, "count", 1)));
                    break;
                }
                case "smelting":
                    SMELTING.add(new Smelting(ingredient(Json.str(m, "input", "")), result, Json.num(m, "xp", 0), Json.integer(m, "cook_ticks", 200)));
                    break;
                default:
                    throw new IllegalStateException("unknown recipe type " + type);
            }
        }
        for (Map.Entry<String, Object> e : Json.obj(root.get("fuel")).entrySet()) {
            int ticks = ((Number) e.getValue()).intValue();
            for (Item it : ingredient(e.getKey())) FUEL.put(it, ticks);
        }
    }

    private static Item req(String id, String ctx) {
        Item it = Items.get(id);
        if (it == null) throw new IllegalStateException("recipes.json: unknown item '" + id + "' in " + ctx);
        return it;
    }

    private static Set<Item> ingredient(String k) {
        if (k.startsWith("#")) {
            Set<Item> s = TAGS.get(k);
            if (s == null) throw new IllegalStateException("recipes.json: unknown tag " + k);
            return s;
        }
        return Collections.singleton(req(k, "ingredient"));
    }

    /** Match a crafting grid (size x size). Returns result stack or null. */
    public static ItemStack match(ItemStack[] grid, int size) {
        int minX = size, minY = size, maxX = -1, maxY = -1, n = 0;
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++)
                if (!ItemStack.isEmpty(grid[y * size + x])) {
                    n++;
                    minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                }
        if (n == 0) return null;
        int w = maxX - minX + 1, h = maxY - minY + 1;
        for (Shaped r : SHAPED) {
            if (r.w != w || r.h != h) continue;
            for (int mirror = 0; mirror < 2; mirror++) {
                boolean ok = true;
                for (int y = 0; y < h && ok; y++)
                    for (int x = 0; x < w && ok; x++) {
                        int rx = mirror == 1 ? (w - 1 - x) : x;
                        Set<Item> want = r.cells[y * w + rx];
                        ItemStack have = grid[(minY + y) * size + minX + x];
                        if (want == null) ok = ItemStack.isEmpty(have);
                        else ok = !ItemStack.isEmpty(have) && want.contains(have.item);
                    }
                if (ok) return new ItemStack(r.result, r.count);
            }
        }
        outer:
        for (Shapeless r : SHAPELESS) {
            if (r.ingredients.size() != n) continue;
            List<Set<Item>> need = new ArrayList<>(r.ingredients);
            for (ItemStack s : grid) {
                if (ItemStack.isEmpty(s)) continue;
                boolean found = false;
                for (Iterator<Set<Item>> it = need.iterator(); it.hasNext(); ) {
                    if (it.next().contains(s.item)) { it.remove(); found = true; break; }
                }
                if (!found) continue outer;
            }
            if (need.isEmpty()) return new ItemStack(r.result, r.count);
        }
        return null;
    }

    public static Smelting smelting(Item in) {
        if (in == null) return null;
        for (Smelting s : SMELTING) if (s.input.contains(in)) return s;
        return null;
    }

    public static int fuel(Item it) {
        if (it == null) return 0;
        Integer v = FUEL.get(it);
        return v == null ? 0 : v;
    }
}
