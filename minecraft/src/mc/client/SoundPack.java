package mc.client;

import mc.util.Json;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Resource-pack style sound lookup (Section 12: identifiers are kept identical "so the project is compatible with
 * community resource packs"). Nothing is bundled: sounds are read at runtime from, in priority order,
 * <ol>
 *   <li>resource packs in {@code <game dir>/resourcepacks/} (folders or .zip with {@code assets/minecraft/sounds.json}),</li>
 *   <li>the player's own Minecraft Java installation ({@code .minecraft/assets/indexes + objects}), found automatically
 *       or via {@code -Dmc.assets=DIR} / {@code MC_ASSETS}.</li>
 * </ol>
 * Event definitions follow vanilla {@code sounds.json}: weighted variants, per-variant volume/pitch,
 * {@code "type": "event"} references, {@code stream}, {@code attenuation_distance} and {@code replace}.
 */
public final class SoundPack {
    /** One playable variant of an event. */
    public static final class Variant {
        public final String name;          // e.g. "minecraft:step/grass1" or an event id when event == true
        public final float volume, pitch;
        public final int weight, attenuation;
        public final boolean stream, event;

        Variant(String name, float volume, float pitch, int weight, int attenuation, boolean stream, boolean event) {
            this.name = name; this.volume = volume; this.pitch = pitch; this.weight = weight; this.attenuation = attenuation;
            this.stream = stream; this.event = event;
        }

        /** Asset path, e.g. "minecraft/sounds/step/grass1.ogg". */
        public String path() {
            String ns = "minecraft", p = name;
            int c = name.indexOf(':');
            if (c >= 0) { ns = name.substring(0, c); p = name.substring(c + 1); }
            return ns + "/sounds/" + p + ".ogg";
        }
    }

    /** A resolved pick: the concrete file plus the multiplied volume/pitch along the event chain. */
    public record Pick(Variant variant, float volume, float pitch) { }

    interface Source {
        InputStream open(String path) throws IOException; // path like "minecraft/sounds.json"

        boolean has(String path);

        String name();

        default void close() { }
    }

    static final class DirPack implements Source {
        final Path root;

        DirPack(Path root) { this.root = root; }

        public InputStream open(String p) throws IOException { return Files.newInputStream(root.resolve("assets").resolve(p)); }

        public boolean has(String p) { return Files.isRegularFile(root.resolve("assets").resolve(p)); }

        public String name() { return "resource pack " + root.getFileName(); }
    }

    static final class ZipPack implements Source {
        final ZipFile zip;
        final String prefix;
        final String label;

        ZipPack(Path file) throws IOException {
            zip = new ZipFile(file.toFile());
            label = file.getFileName().toString();
            // packs are sometimes zipped with an extra top-level folder
            String pre = "";
            try {
                if (zip.getEntry("assets/minecraft/sounds.json") == null) {
                    for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                        String n = e.nextElement().getName();
                        int i = n.indexOf("assets/minecraft/");
                        if (i > 0 && n.indexOf('/') == i - 1) { pre = n.substring(0, i); break; }
                    }
                }
            } catch (RuntimeException e) {
                zip.close(); // never leak the file handle (Windows would keep the pack locked)
                throw e;
            }
            prefix = pre + "assets/";
        }

        @Override public void close() { try { zip.close(); } catch (IOException ignored) { } }

        public InputStream open(String p) throws IOException {
            ZipEntry e = zip.getEntry(prefix + p);
            if (e == null) throw new NoSuchFileException(p);
            return zip.getInputStream(e);
        }

        public boolean has(String p) { return zip.getEntry(prefix + p) != null; }

        public String name() { return "resource pack " + label; }
    }

    /** The official launcher's content-addressed asset store. */
    static final class AssetIndex implements Source {
        final Path objects;
        final Map<String, String> hashes = new HashMap<>();
        final String label;

        AssetIndex(Path assets, Path index) throws IOException {
            objects = assets.resolve("objects");
            label = "Minecraft assets " + assets + " (index " + index.getFileName() + ")";
            Map<String, Object> j = Json.obj(Json.parse(Files.readString(index, StandardCharsets.UTF_8)));
            Map<String, Object> objs = Json.obj(j.get("objects"));
            for (Map.Entry<String, Object> e : objs.entrySet()) {
                Object h = Json.obj(e.getValue()).get("hash");
                if (h instanceof String s && s.length() > 2) hashes.put(e.getKey(), s);
            }
        }

        Path file(String p) {
            String h = hashes.get(p);
            return h == null ? null : objects.resolve(h.substring(0, 2)).resolve(h);
        }

        public InputStream open(String p) throws IOException {
            Path f = file(p);
            if (f == null) throw new NoSuchFileException(p);
            return Files.newInputStream(f);
        }

        public boolean has(String p) { Path f = file(p); return f != null && Files.isRegularFile(f); }

        public String name() { return label; }
    }

    final List<Source> sources = new ArrayList<>(); // highest priority first
    final Map<String, List<Variant>> events = new HashMap<>();
    final Map<String, OggDecoder.Clip> clips = new ConcurrentHashMap<>();
    final Set<String> broken = ConcurrentHashMap.newKeySet();
    public final List<String> description = new ArrayList<>();

    private SoundPack() { }

    // ------------------------------------------------------------------ discovery

    /** Candidate Minecraft "assets" folders, most specific first. */
    public static List<Path> assetDirs() {
        List<Path> out = new ArrayList<>();
        String prop = System.getProperty("mc.assets", System.getenv("MC_ASSETS"));
        if (prop != null && !prop.isBlank()) {
            Path p = Paths.get(prop.trim());
            out.add(p);
            out.add(p.resolve("assets"));
        }
        String appData = System.getenv("APPDATA");
        String home = System.getProperty("user.home", ".");
        if (appData != null) out.add(Paths.get(appData, ".minecraft", "assets"));
        out.add(Paths.get(home, ".minecraft", "assets"));
        out.add(Paths.get(home, "Library", "Application Support", "minecraft", "assets"));
        out.add(Paths.get(home, ".var", "app", "com.mojang.Minecraft", ".minecraft", "assets")); // flatpak
        if (appData != null) out.add(Paths.get(appData, ".tlauncher", "legacy", "Minecraft", "game", "assets"));
        return out;
    }

    /** Picks the asset index to use: 1.16 if present, otherwise the most recently written one that has sounds. */
    static Path chooseIndex(Path assets) {
        Path dir = assets.resolve("indexes");
        if (!Files.isDirectory(dir) || !Files.isDirectory(assets.resolve("objects"))) return null;
        Path pref = dir.resolve("1.16.json");
        if (Files.isRegularFile(pref)) return pref;
        Path best = null;
        long bestT = Long.MIN_VALUE;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.json")) {
            for (Path p : ds) {
                String n = p.getFileName().toString();
                if (n.equals("legacy.json") || n.equals("pre-1.6.json")) continue;
                long t = Files.getLastModifiedTime(p).toMillis();
                if (t > bestT) { bestT = t; best = p; }
            }
        } catch (IOException ignored) { }
        return best;
    }

    /** Builds the pack from all available sources. Returns null when no source provides a sounds.json. */
    public static SoundPack discover(Path gameDir) {
        List<Path> dirs = new ArrayList<>();
        if (gameDir != null) dirs.add(gameDir.resolve("resourcepacks"));
        String data = System.getProperty("mc.data");
        if (data != null) { // resourcepacks/ next to Minecraft.exe (data lives in <install>/app/data)
            Path inst = Paths.get(data).toAbsolutePath().normalize().getParent();
            if (inst != null && inst.getFileName() != null && inst.getFileName().toString().equals("app")) inst = inst.getParent();
            if (inst != null) dirs.add(inst.resolve("resourcepacks"));
        }
        return discover(dirs, assetDirs());
    }

    public static SoundPack discover(List<Path> packDirs, List<Path> assetDirs) {
        SoundPack sp = new SoundPack();
        Set<Path> seen = new HashSet<>();
        for (Path rp : packDirs) {
            if (!seen.add(rp.toAbsolutePath().normalize())) continue;
            try {
                if (!Files.isDirectory(rp)) continue;
                List<Path> packs = new ArrayList<>();
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(rp)) { for (Path p : ds) packs.add(p); }
                packs.sort(Comparator.comparing(p -> p.getFileName().toString()));
                for (Path p : packs) {
                    try {
                        if (Files.isDirectory(p) && Files.isDirectory(p.resolve("assets"))) sp.sources.add(new DirPack(p));
                        else if (p.toString().toLowerCase(Locale.ROOT).endsWith(".zip")) sp.sources.add(new ZipPack(p));
                    } catch (Exception e) {
                        System.err.println("[sound] skipping resource pack " + p + ": " + e);
                    }
                }
            } catch (IOException ignored) { }
        }
        for (Path a : assetDirs) {
            try {
                Path idx = chooseIndex(a);
                if (idx == null) continue;
                AssetIndex ai = new AssetIndex(a, idx);
                if (!ai.has("minecraft/sounds.json")) continue;
                sp.sources.add(ai);
                break; // one installation is enough
            } catch (Exception e) {
                System.err.println("[sound] cannot read assets in " + a + ": " + e);
            }
        }
        // merge sounds.json from lowest to highest priority (vanilla resource-pack semantics)
        for (int i = sp.sources.size() - 1; i >= 0; i--) {
            Source s = sp.sources.get(i);
            if (!s.has("minecraft/sounds.json")) continue;
            try (InputStream in = s.open("minecraft/sounds.json")) {
                sp.merge(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                sp.description.add(s.name());
            } catch (Exception e) {
                System.err.println("[sound] bad sounds.json in " + s.name() + ": " + e);
            }
        }
        if (sp.events.isEmpty()) { sp.close(); return null; }
        Collections.reverse(sp.description);
        System.out.println("[sound] using " + sp.events.size() + " sound events from " + sp.description);
        return sp;
    }

    void merge(String json) {
        Map<String, Object> root = Json.obj(Json.parse(json));
        for (Map.Entry<String, Object> e : root.entrySet()) {
            String id = e.getKey();
            if (id.startsWith("minecraft:")) id = id.substring(10);
            Map<String, Object> def = Json.obj(e.getValue());
            boolean replace = Boolean.TRUE.equals(def.get("replace"));
            List<Variant> list = new ArrayList<>();
            Object sounds = def.get("sounds");
            if (sounds instanceof List<?> l) {
                for (Object o : l) {
                    if (o instanceof String s) list.add(new Variant(s, 1, 1, 1, 16, false, false));
                    else if (o instanceof Map) {
                        Map<String, Object> m = Json.obj(o);
                        String name = Json.str(m, "name", null);
                        if (name == null) continue;
                        boolean ev = "event".equals(Json.str(m, "type", "file"));
                        list.add(new Variant(name, (float) Json.num(m, "volume", 1), (float) Json.num(m, "pitch", 1),
                                (int) Math.max(1, Math.min(1_000_000, Json.num(m, "weight", 1))), (int) Json.num(m, "attenuation_distance", 16),
                                Boolean.TRUE.equals(m.get("stream")), ev));
                    }
                }
            }
            if (replace || !events.containsKey(id)) events.put(id, list);
            else events.get(id).addAll(list);
        }
    }

    // ------------------------------------------------------------------ lookup

    public boolean has(String event) {
        List<Variant> l = events.get(event);
        return l != null && !l.isEmpty();
    }

    public Set<String> eventIds() { return Collections.unmodifiableSet(events.keySet()); }

    /** Weighted random variant, following event references; null if the event is unknown or empty. */
    public Pick pick(String event, Random r) {
        float vol = 1, pitch = 1;
        for (int depth = 0; depth < 8; depth++) {
            List<Variant> l = events.get(event.startsWith("minecraft:") ? event.substring(10) : event);
            if (l == null || l.isEmpty()) return null;
            int total = 0;
            for (Variant v : l) total += v.weight;
            int k = r.nextInt(total);
            Variant chosen = l.get(l.size() - 1);
            for (Variant v : l) { k -= v.weight; if (k < 0) { chosen = v; break; } }
            vol *= chosen.volume;
            pitch *= chosen.pitch;
            if (!chosen.event) return new Pick(chosen, vol, pitch);
            event = chosen.name;
        }
        return null;
    }

    InputStream open(String path) throws IOException {
        for (Source s : sources) if (s.has(path)) return s.open(path);
        throw new NoSuchFileException(path);
    }

    public boolean exists(String path) {
        for (Source s : sources) if (s.has(path)) return true;
        return false;
    }

    public boolean isBroken(Variant v) { return broken.contains(v.path()); }

    volatile boolean closed;

    /** Releases zip handles; called when the sound mode is switched or the game exits. */
    public void close() {
        closed = true;
        for (Source s : sources) s.close();
    }

    /** Already-decoded clip or null (never blocks). */
    public OggDecoder.Clip cached(Variant v) { return clips.get(v.path()); }

    /** Decoded (cached) mono clip for a variant; null if the file is missing or undecodable. */
    public OggDecoder.Clip clip(Variant v) {
        String p = v.path();
        OggDecoder.Clip c = clips.get(p);
        if (c != null || broken.contains(p) || closed) return c;
        try (InputStream in = open(p)) {
            c = OggDecoder.decodeMono(in.readAllBytes());
            clips.put(p, c);
            return c;
        } catch (Exception e) {
            if (closed) return null;
            if (broken.add(p)) System.err.println("[sound] cannot load " + p + ": " + e);
            return null;
        }
    }

    /** Opens a streaming decoder (music, long ambient loops). */
    public OggDecoder stream(Variant v) throws IOException {
        InputStream in = new java.io.BufferedInputStream(open(v.path()), 65536);
        try {
            return new OggDecoder(in);
        } catch (IOException | RuntimeException e) {
            in.close(); // a corrupt file must not leak its handle
            throw e;
        }
    }

    /** Decode every file referenced by the given events (background warm-up so first plays never hitch). */
    public void prewarm(Collection<String> ids) { prewarm(ids, new HashSet<>()); }

    private void prewarm(Collection<String> ids, Set<String> seen) {
        for (String id : ids) {
            if (closed) return;
            if (!seen.add(id)) continue; // event references may form cycles in broken packs
            List<Variant> l = events.get(id);
            if (l == null) continue;
            for (Variant v : new ArrayList<>(l)) {
                if (v.event) { prewarm(List.of(v.name.startsWith("minecraft:") ? v.name.substring(10) : v.name), seen); continue; }
                if (!v.stream) clip(v);
            }
        }
    }

    public long memoryBytes() {
        long b = 0;
        for (OggDecoder.Clip c : clips.values()) b += c.pcm.length * 2L;
        return b;
    }
}
