package mc.content;

import mc.util.Json;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Boot-time registry construction in the fixed order of Section 2.4: blocks, items, entity types, sound events,
 * recipes, biomes. Validators fail loudly on unknown fields. */
public final class Registries {
    private Registries() {}

    public static Map<String, Object> TUNING = new HashMap<>();
    public static Map<String, Map<String, Object>> SOUNDS = new LinkedHashMap<>();
    public static Map<String, Map<String, Object>> TEXTURES = new LinkedHashMap<>();
    public static Map<String, Object> WORLDGEN = new HashMap<>();
    public static Path dataDir; // null when loading from classpath
    private static boolean loaded;

    public static synchronized void load() {
        if (loaded) return;
        dataDir = findDir("data");
        Blocks.load(read("blocks.json"));
        Items.load(read("items.json"));
        EntityTypes.load(read("entities.json"));
        SOUNDS.clear();
        for (Map.Entry<String, Object> e : Json.obj(read("sounds.json").get("sounds")).entrySet()) SOUNDS.put(e.getKey(), Json.obj(e.getValue()));
        Recipes.load(read("recipes.json"));
        Biomes.load(read("biomes.json"));
        TEXTURES.clear();
        for (Map.Entry<String, Object> e : Json.obj(read("textures.json").get("textures")).entrySet()) TEXTURES.put(e.getKey(), Json.obj(e.getValue()));
        TUNING = read("tuning.json");
        WORLDGEN = read("worldgen.json");
        validate();
        loaded = true;
    }

    private static void validate() {
        for (Block b : Blocks.ALL) {
            for (String t : new String[]{b.texTop, b.texSide, b.texBottom, b.texFront})
                if (!TEXTURES.containsKey(t)) throw new IllegalStateException("block " + b.id + " references unknown texture " + t);
            if (b.drops != null)
                for (Map<String, Object> d : b.drops) {
                    if (Items.get(Json.str(d, "item", "")) == null)
                        throw new IllegalStateException("block " + b.id + " drops unknown item " + d.get("item"));
                }
        }
    }

    public static Path findDir(String name) {
        String prop = System.getProperty("mc." + name);
        if (prop != null && Files.isDirectory(Paths.get(prop))) return Paths.get(prop);
        List<Path> candidates = new ArrayList<>();
        candidates.add(Paths.get(name));
        candidates.add(Paths.get("..", name));
        candidates.add(Paths.get("minecraft", name));
        try {
            Path jar = Paths.get(Registries.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path dir = Files.isDirectory(jar) ? jar : jar.getParent();
            for (int i = 0; i < 4 && dir != null; i++) {
                candidates.add(dir.resolve(name));
                dir = dir.getParent();
            }
        } catch (Exception ignored) { }
        for (Path p : candidates) if (Files.isDirectory(p) && Files.exists(p.resolve(name.equals("data") ? "blocks.json" : "."))) return p;
        return null;
    }

    public static String readText(String file) {
        try {
            if (dataDir != null) return new String(Files.readAllBytes(dataDir.resolve(file)), StandardCharsets.UTF_8);
            try (InputStream in = Registries.class.getResourceAsStream("/data/" + file)) {
                if (in == null) throw new FileNotFoundException("data/" + file + " not found (set -Dmc.data=path)");
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Map<String, Object> read(String file) { return Json.obj(Json.parse(readText(file))); }

    // ---------------- tuning accessors (Section 1.6: values from data) ----------------
    public static double tune(String key) {
        Object o = TUNING.get(key);
        if (!(o instanceof Number)) throw new IllegalStateException("tuning.json missing numeric " + key);
        return ((Number) o).doubleValue();
    }

    public static int tunei(String key) { return (int) tune(key); }

    public static int cap(String category) {
        Object o = Json.obj(TUNING.get("spawn_caps")).get(category);
        return o instanceof Number ? ((Number) o).intValue() : 0;
    }

    /** Registry manifest: ordered list of block-state names (Appendix O.3); saved with each world for remapping. */
    public static List<Object> manifest() {
        List<Object> l = new ArrayList<>();
        for (int s = 0; s < Blocks.stateCount; s++) l.add(Blocks.stateName(s));
        return l;
    }
}
