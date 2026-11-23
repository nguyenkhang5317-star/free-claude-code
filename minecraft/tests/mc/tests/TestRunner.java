package mc.tests;

import mc.client.Animations;
import mc.client.Textures;
import mc.content.*;
import mc.core.*;
import mc.entity.Player;
import mc.gen.OverworldGenerator;
import mc.gen.NetherGenerator;
import mc.server.IntegratedServer;
import mc.util.Json;

import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Verification suite (Section 16 / deliverable 4). Dependency-free: run with
 *   java -cp build/classes;build/tests -Dmc.data=data mc.tests.TestRunner [--update-golden]
 * Exit code 0 = all tests passed.
 */
public final class TestRunner {
    static int passed, failed;
    static final List<String> failures = new ArrayList<>();
    static boolean updateGolden;

    interface Test { void run() throws Exception; }

    static void test(String name, Test t) {
        long t0 = System.nanoTime();
        try {
            t.run();
            passed++;
            System.out.printf("  PASS  %-44s %6.0f ms%n", name, (System.nanoTime() - t0) / 1e6);
        } catch (Throwable e) {
            failed++;
            failures.add(name + ": " + e.getMessage());
            System.out.printf("  FAIL  %-44s %s%n", name, e.getMessage());
        }
    }

    static void check(boolean cond, String msg) { if (!cond) throw new AssertionError(msg); }

    static void near(double actual, double expected, double tol, String what) {
        if (Math.abs(actual - expected) > tol) throw new AssertionError(what + ": expected " + expected + " ± " + tol + ", got " + actual);
    }

    public static void main(String[] args) throws Exception {
        updateGolden = Arrays.asList(args).contains("--update-golden");
        System.setProperty("java.awt.headless", "true");
        Registries.load();
        Textures.load();
        System.out.println("Minecraft 1.16.5 rebuild - verification suite");

        System.out.println("[registry completeness]");
        test("blocks: textures resolve", TestRunner::blockTextures);
        test("blocks: loot tables resolve to items", TestRunner::blockDrops);
        test("blocks: every placeable block has an item", TestRunner::blockItems);
        test("items: block items point at real blocks", TestRunner::itemBlocks);
        test("recipes: every ingredient and result exists", TestRunner::recipes);
        test("recipes: vanilla spot checks", TestRunner::recipeSpotChecks);
        test("entities: models, sizes and sounds", TestRunner::entities);
        test("biomes: surface blocks exist", TestRunner::biomes);

        System.out.println("[world generation determinism]");
        test("same seed -> identical chunk hashes", TestRunner::determinism);
        test("generation order does not matter", TestRunner::orderIndependence);
        test("different seeds differ", TestRunner::seedsDiffer);
        test("golden chunk hashes (cross-platform)", TestRunner::golden);
        test("nether generator deterministic", TestRunner::netherDeterminism);

        System.out.println("[persistence]");
        test("chunk codec round trip", TestRunner::chunkRoundTrip);
        test("world save -> reopen round trip", TestRunner::worldRoundTrip);
        test("corrupt region chunk is regenerated, not fatal", TestRunner::corruptRegion);

        System.out.println("[gameplay rules]");
        test("mining times match vanilla", TestRunner::mining);
        test("harvest tiers", TestRunner::harvest);
        test("physics: walk / sprint / sneak speeds", TestRunner::speeds);
        test("physics: jump apex and fall damage", TestRunner::jumpAndFall);

        System.out.println("[animation validator]");
        test("Animations.validate()", () -> {
            List<String> p = Animations.validate();
            check(p.isEmpty(), String.join("; ", p));
        });

        System.out.println("[sound audit]");
        test("every referenced sound id exists", TestRunner::soundReferences);
        test("every sound synthesises audible output", TestRunner::soundSynthesis);
        test("resource-pack sounds: sounds.json + Ogg Vorbis decode", TestRunner::soundPack);

        test("resource-pack sounds: corrupt files and cyclic events are safe", TestRunner::soundPackRobust);

        System.out.println("[robustness]");
        test("commands reject bad arguments", TestRunner::commandArgs);
        test("server ignores hostile input packets", TestRunner::hostilePackets);
        test("xp: removal clamps at level 0, level-up sound rate-limited", TestRunner::xpClamp);

        System.out.printf("%n%d passed, %d failed%n", passed, failed);
        for (String f : failures) System.out.println("  - " + f);
        System.exit(failed == 0 ? 0 : 1);
    }

    // ================================================================== registry
    static void blockTextures() {
        List<String> bad = new ArrayList<>();
        for (Block b : Blocks.ALL) {
            if (b.shape == Block.SHAPE_NONE) continue;
            for (String t : new String[]{b.texTop, b.texSide, b.texBottom, b.texFront})
                if (t != null && !Textures.INDEX.containsKey(t)) bad.add(b.id + "->" + t);
        }
        check(bad.isEmpty(), "missing textures: " + bad);
        check(Blocks.ALL.size() >= 150, "expected at least 150 blocks, got " + Blocks.ALL.size());
    }

    static void blockDrops() {
        List<String> bad = new ArrayList<>();
        for (Block b : Blocks.ALL) {
            if (b.drops == null) continue;
            for (Map<String, Object> d : b.drops) {
                String item = Json.str(d, "item", null);
                if (item != null && Items.get(item) == null) bad.add(b.id + "->" + item);
            }
        }
        check(bad.isEmpty(), "unknown drop items: " + bad);
    }

    static void blockItems() {
        List<String> bad = new ArrayList<>();
        for (Block b : Blocks.ALL) if (b.hasItem && Items.get(b.id) == null) bad.add(b.id);
        check(bad.isEmpty(), "blocks flagged hasItem without an item: " + bad);
    }

    static void itemBlocks() {
        List<String> bad = new ArrayList<>();
        for (Item it : Items.ALL) if (it.block != null && (it.placesState < 0 || Blocks.block(it.placesState) == null)) bad.add(it.id + "->" + it.block.id);
        check(bad.isEmpty(), "items placing unknown blocks: " + bad);
        check(Items.ALL.size() >= 250, "expected at least 250 items, got " + Items.ALL.size());
    }

    static void recipes() {
        check(!Recipes.SHAPED.isEmpty() && !Recipes.SMELTING.isEmpty(), "no recipes loaded");
        Map<String, Object> raw = Registries.read("recipes.json");
        List<String> bad = new ArrayList<>();
        Pattern id = Pattern.compile("\"(minecraft:[a-z0-9_]+)\"");
        Matcher m = id.matcher(Json.write(raw));
        while (m.find()) {
            String s = m.group(1);
            if (Items.get(s) == null && !Recipes.TAGS.containsKey(s) && !Recipes.TAGS.containsKey("#" + s) && !s.contains("planks_tag")) bad.add(s);
        }
        check(bad.isEmpty(), "unknown ids in recipes.json: " + new TreeSet<>(bad));
    }

    static ItemStack craft(int size, String... ids) {
        ItemStack[] g = new ItemStack[size * size];
        for (int i = 0; i < ids.length; i++) g[i] = ids[i] == null ? null : ItemStack.of(ids[i], 1);
        return Recipes.match(g, size);
    }

    static void recipeSpotChecks() {
        ItemStack planks = craft(2, "minecraft:oak_log");
        check(planks != null && planks.item.id.equals("minecraft:oak_planks") && planks.count == 4, "oak_log -> 4 oak_planks, got " + planks);
        ItemStack table = craft(2, "minecraft:oak_planks", "minecraft:oak_planks", "minecraft:oak_planks", "minecraft:oak_planks");
        check(table != null && table.item.id.equals("minecraft:crafting_table"), "4 planks -> crafting table, got " + table);
        ItemStack sticks = craft(3, null, "minecraft:oak_planks", null, null, "minecraft:oak_planks");
        check(sticks != null && sticks.item.id.equals("minecraft:stick") && sticks.count == 4, "planks column -> 4 sticks, got " + sticks);
        String c = "minecraft:cobblestone", s = "minecraft:stick";
        ItemStack pick = craft(3, c, c, c, null, s, null, null, s, null);
        check(pick != null && pick.item.id.equals("minecraft:stone_pickaxe"), "stone pickaxe, got " + pick);
        ItemStack furnace = craft(3, c, c, c, c, null, c, c, c, c);
        check(furnace != null && furnace.item.id.equals("minecraft:furnace"), "furnace, got " + furnace);
        Recipes.Smelting iron = Recipes.smelting(Items.get("minecraft:iron_ore"));
        check(iron != null && iron.result.id.equals("minecraft:iron_ingot"), "iron_ore smelts to iron_ingot");
        check(Recipes.fuel(Items.get("minecraft:coal")) == 1600, "coal burns 1600 ticks, got " + Recipes.fuel(Items.get("minecraft:coal")));
    }

    static void entities() {
        List<String> bad = new ArrayList<>();
        for (EntityType t : EntityTypes.ALL) {
            if (t.width <= 0 || t.height <= 0) bad.add(t.id + " has no size");
            if (t.category.equals("creature") || t.category.equals("monster")) {
                if (t.model == null || t.model.isEmpty()) bad.add(t.id + " has no model");
                for (String k : new String[]{"hurt", "death"})
                    if (t.sound(k) == null || !Registries.SOUNDS.containsKey(t.sound(k))) bad.add(t.id + " sound " + k);
            }
        }
        check(bad.isEmpty(), String.join(", ", bad));
        for (String id : new String[]{"cow", "pig", "sheep", "chicken", "zombie", "skeleton", "creeper", "spider"})
            check(EntityTypes.get("minecraft:" + id) != null, "missing entity " + id);
    }

    static void biomes() {
        List<String> bad = new ArrayList<>();
        for (Biome b : Biomes.ALL) {
            if (b.top < 0 || b.top >= Blocks.stateCount) bad.add(b.id + " top");
            if (b.filler < 0 || b.filler >= Blocks.stateCount) bad.add(b.id + " filler");
            if (b.underwater < 0 || b.underwater >= Blocks.stateCount) bad.add(b.id + " underwater");
        }
        check(bad.isEmpty(), String.join(", ", bad));
        check(Biomes.ALL.size() >= 30, "expected >= 30 biomes");
    }

    // ================================================================== determinism
    static long hash(Chunk c) {
        long h = 0xcbf29ce484222325L;
        for (int y = 0; y < 256; y++)
            for (int z = 0; z < 16; z++)
                for (int x = 0; x < 16; x++) {
                    h ^= c.get(x, y, z);
                    h *= 0x100000001b3L;
                }
        for (byte b : c.biomes) { h ^= b; h *= 0x100000001b3L; }
        return h;
    }

    static final int[][] SAMPLE = {{0, 0}, {1, 0}, {-3, 7}, {12, -9}, {-40, -40}, {100, 3}, {7, 250}};

    static long[] hashes(ChunkGenerator g, int[][] coords) {
        long[] o = new long[coords.length];
        for (int i = 0; i < coords.length; i++) o[i] = hash(g.generate(coords[i][0], coords[i][1]));
        return o;
    }

    static void determinism() {
        long[] a = hashes(new OverworldGenerator(12345L), SAMPLE);
        long[] b = hashes(new OverworldGenerator(12345L), SAMPLE);
        check(Arrays.equals(a, b), "hash mismatch between runs");
    }

    static void orderIndependence() {
        long[] fwd = hashes(new OverworldGenerator(-987654321L), SAMPLE);
        int[][] rev = SAMPLE.clone();
        Collections.reverse(Arrays.asList(rev));
        long[] back = hashes(new OverworldGenerator(-987654321L), rev);
        for (int i = 0; i < fwd.length; i++) check(fwd[i] == back[back.length - 1 - i], "chunk " + Arrays.toString(SAMPLE[i]) + " depends on generation order");
    }

    static void seedsDiffer() {
        check(hash(new OverworldGenerator(1L).generate(0, 0)) != hash(new OverworldGenerator(2L).generate(0, 0)), "seeds 1 and 2 produced the same chunk");
    }

    static void golden() throws Exception {
        Path f = Paths.get(System.getProperty("mc.tests", "tests"), "golden_hashes.json");
        long[] seeds = {0L, 12345L, -1L, 0x5DEECE66DL};
        Map<String, Object> now = new LinkedHashMap<>();
        for (long s : seeds) {
            List<Object> l = new ArrayList<>();
            for (long h : hashes(new OverworldGenerator(s), SAMPLE)) l.add(Long.toHexString(h));
            now.put(Long.toString(s), l);
        }
        if (updateGolden || !Files.exists(f)) {
            Files.writeString(f, Json.write(now));
            System.out.println("        (golden hashes written to " + f + ")");
            return;
        }
        Map<String, Object> want = Json.obj(Json.parse(Files.readString(f)));
        check(want.equals(now) || Json.write(want).equals(Json.write(now)), "chunk hashes differ from " + f + " - world generation changed (run with --update-golden if intended)");
    }

    static void netherDeterminism() {
        check(hash(new NetherGenerator(42).generate(3, -2)) == hash(new NetherGenerator(42).generate(3, -2)), "nether mismatch");
    }

    // ================================================================== persistence
    static void sameBlocks(Chunk a, Chunk b) {
        for (int y = 0; y < 256; y++)
            for (int z = 0; z < 16; z++)
                for (int x = 0; x < 16; x++)
                    if (a.get(x, y, z) != b.get(x, y, z)) throw new AssertionError("block differs at " + x + "," + y + "," + z);
        check(Arrays.equals(a.biomes, b.biomes), "biomes differ");
    }

    static void chunkRoundTrip() throws Exception {
        Chunk c = new OverworldGenerator(777).generate(5, -5);
        c.set(3, 200, 4, Blocks.base("minecraft:glowstone"));
        Chunk d = RegionStore.roundTrip(c);
        sameBlocks(c, d);
    }

    static Path tempDir(String name) throws Exception { return Files.createTempDirectory("mc-test-" + name); }

    static void worldRoundTrip() throws Exception {
        Path dir = tempDir("world");
        IntegratedServer s = IntegratedServer.create(dir, "RoundTrip", 4242L, Player.SURVIVAL, true);
        World w = s.world(Dimension.OVERWORLD);
        int px = (int) Math.floor(s.player.x), pz = (int) Math.floor(s.player.z);
        int gold = Blocks.base("minecraft:gold_block");
        w.setBlock(px + 2, 120, pz + 2, gold);
        s.player.inventory.add(ItemStack.of("minecraft:diamond", 7));
        s.level.dayTime = 6000;
        s.save(true);
        s.shutdown();
        IntegratedServer r = IntegratedServer.open(dir, true);
        World w2 = r.world(Dimension.OVERWORLD);
        w2.loadNow((px + 2) >> 4, (pz + 2) >> 4);
        check(w2.getBlock(px + 2, 120, pz + 2) == gold, "placed block lost");
        check(r.player.inventory.count(Items.get("minecraft:diamond")) == 7, "inventory lost");
        check(r.level.dayTime == 6000, "time lost: " + r.level.dayTime);
        near(r.player.x, s.player.x, 1e-6, "player x");
        r.shutdown();
    }

    static void corruptRegion() throws Exception {
        Path dir = tempDir("corrupt");
        IntegratedServer s = IntegratedServer.create(dir, "Corrupt", 99L, Player.SURVIVAL, true);
        s.save(true);
        s.shutdown();
        try (var st = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) st.filter(p -> p.toString().endsWith(".mca") || p.toString().endsWith(".mcr") || p.toString().contains("region"))::iterator) {
                if (!Files.isRegularFile(p) || Files.size(p) < 9000) continue;
                byte[] b = Files.readAllBytes(p);
                for (int i = 8192 + 40; i < Math.min(b.length, 8192 + 400); i++) b[i] ^= 0x5A; // damage first chunk payload
                Files.write(p, b);
            }
        }
        IntegratedServer r = IntegratedServer.open(dir, true);
        World w = r.world(Dimension.OVERWORLD);
        for (int cx = -2; cx <= 2; cx++) for (int cz = -2; cz <= 2; cz++) check(w.loadNow(cx, cz) != null, "chunk " + cx + "," + cz + " failed to load");
        r.shutdown();
    }

    // ================================================================== gameplay
    static void mining() {
        int stone = Blocks.base("minecraft:stone"), dirt = Blocks.base("minecraft:dirt"), log = Blocks.base("minecraft:oak_log"),
                obsidian = Blocks.base("minecraft:obsidian");
        check(Mining.breakTicks(stone, null) == 150, "stone by hand = 7.5 s (150 ticks), got " + Mining.breakTicks(stone, null));
        int wp = Mining.breakTicks(stone, ItemStack.of("minecraft:wooden_pickaxe", 1));
        check(wp == 23, "stone with wooden pickaxe = 1.15 s (23 ticks), got " + wp);
        check(Mining.breakTicks(dirt, null) == 15, "dirt by hand = 0.75 s, got " + Mining.breakTicks(dirt, null));
        check(Mining.breakTicks(log, null) == 60, "log by hand = 3 s, got " + Mining.breakTicks(log, null));
        int ob = Mining.breakTicks(obsidian, ItemStack.of("minecraft:diamond_pickaxe", 1));
        check(Math.abs(ob - 188) <= 1, "obsidian with diamond pickaxe = 9.4 s (188 ticks), got " + ob);
    }

    static void harvest() {
        int diamond = Blocks.base("minecraft:diamond_ore"), stone = Blocks.base("minecraft:stone");
        check(!Mining.canHarvest(stone, null), "stone must not drop by hand");
        check(Mining.canHarvest(stone, ItemStack.of("minecraft:wooden_pickaxe", 1)), "stone drops with wooden pickaxe");
        check(!Mining.canHarvest(diamond, ItemStack.of("minecraft:stone_pickaxe", 1)), "diamond ore needs iron pickaxe");
        check(Mining.canHarvest(diamond, ItemStack.of("minecraft:iron_pickaxe", 1)), "iron pickaxe harvests diamond ore");
        List<ItemStack> d = Mining.drops(diamond, ItemStack.of("minecraft:iron_pickaxe", 1), new Random(1));
        check(!d.isEmpty() && d.get(0).item.id.equals("minecraft:diamond"), "diamond ore drops a diamond, got " + d);
    }

    /** Flat stone test platform at y=150 around the player; returns a ready server. */
    static IntegratedServer arena() throws Exception {
        IntegratedServer s = IntegratedServer.create(null, "Physics", 5L, Player.SURVIVAL, true);
        World w = s.world(Dimension.OVERWORLD);
        int px = (int) Math.floor(s.player.x), pz = (int) Math.floor(s.player.z);
        for (int cx = (px >> 4) - 4; cx <= (px >> 4) + 4; cx++) for (int cz = (pz >> 4) - 4; cz <= (pz >> 4) + 4; cz++) w.loadNow(cx, cz);
        int stone = Blocks.base("minecraft:stone");
        for (int x = px - 60; x <= px + 60; x++)
            for (int z = pz - 3; z <= pz + 3; z++) {
                w.setBlock(x, 150, z, stone, 0);
                for (int y = 151; y < 156; y++) w.setBlock(x, y, z, Blocks.AIR, 0);
            }
        s.player.setPos(px + 0.5 - 50, 151, pz + 0.5);
        s.player.vx = s.player.vy = s.player.vz = 0;
        s.player.yaw = -90; // facing +x (east)
        s.player.food = 20;
        return s;
    }

    static double speed(boolean sprint, boolean sneak) throws Exception {
        IntegratedServer s = arena();
        Player p = s.player;
        for (int i = 0; i < 5; i++) p.tick();
        p.inForward = 1;
        p.inSprint = sprint;
        p.inSneak = sneak;
        for (int i = 0; i < 40; i++) p.tick(); // reach terminal speed
        double x0 = p.x;
        for (int i = 0; i < 40; i++) p.tick();
        return Math.abs(p.x - x0) / 2.0; // blocks per second
    }

    static void speeds() throws Exception {
        near(speed(false, false), 4.317, 0.15, "walk speed b/s");
        near(speed(true, false), 5.612, 0.2, "sprint speed b/s");
        near(speed(false, true), 1.295, 0.1, "sneak speed b/s");
    }

    static void jumpAndFall() throws Exception {
        IntegratedServer s = arena();
        Player p = s.player;
        for (int i = 0; i < 5; i++) p.tick();
        double ground = p.y, apex = ground;
        p.inJump = true;
        p.tick();
        p.inJump = false;
        for (int i = 0; i < 30; i++) { p.tick(); apex = Math.max(apex, p.y); }
        near(apex - ground, 1.2522, 0.03, "jump apex height");
        // fall 10 blocks -> 7 damage (distance - 3)
        p.setPos(p.x, 161, p.z);
        p.vy = 0;
        p.fallDistance = 0;
        float hp = p.health;
        for (int i = 0; i < 60 && !(p.onGround && i > 2); i++) p.tick();
        near(hp - p.health, 7, 0.51, "fall damage from 10 blocks");
    }

    // ================================================================== sounds
    static void soundReferences() throws Exception {
        Set<String> used = new TreeSet<>();
        Path src = Paths.get(System.getProperty("mc.src", "src"));
        Pattern lit = Pattern.compile("\"((?:block|entity|item|ui|ambient|random|weather)\\.[a-z0-9_.]+)\"");
        try (var st = Files.walk(src)) {
            for (Path f : (Iterable<Path>) st.filter(p -> p.toString().endsWith(".java"))::iterator) {
                Matcher m = lit.matcher(Files.readString(f));
                while (m.find()) used.add(m.group(1));
            }
        }
        // dynamic ids: "block.<family>.<event>" for every block family, entity sounds from data
        for (Block b : Blocks.ALL) if (b.sound != null) for (String ev : new String[]{"break", "place", "step", "hit", "fall"}) used.add("block." + b.sound + "." + ev);
        for (EntityType t : EntityTypes.ALL) for (String k : new String[]{"ambient", "hurt", "death", "step"}) if (t.sound(k) != null) used.add(t.sound(k));
        List<String> missing = new ArrayList<>();
        for (String u : used) if (!Registries.SOUNDS.containsKey(u)) missing.add(u);
        check(missing.isEmpty(), "sound ids referenced but not defined: " + missing);
        System.out.println("        (" + used.size() + " sound ids referenced, " + Registries.SOUNDS.size() + " defined)");
    }

    static void soundSynthesis() throws Exception {
        List<String> silent = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> e : Registries.SOUNDS.entrySet()) {
            float[] pcm = mc.client.Audio.pcm(e.getKey());
            double peak = 0;
            for (float f : pcm) peak = Math.max(peak, Math.abs(f));
            if (pcm.length == 0 || peak < 0.01 || peak > 1.0001) silent.add(e.getKey() + "(peak " + String.format("%.3f", peak) + ")");
        }
        check(silent.isEmpty(), "silent or clipping sounds: " + silent);
    }

    static void soundPackRobust() throws Exception {
        Path dir = tempDir("badpack");
        Path snd = Files.createDirectories(dir.resolve("Bad/assets/minecraft/sounds"));
        byte[] junk = new byte[4096];
        new Random(5).nextBytes(junk);
        Files.write(snd.resolve("junk.ogg"), junk);
        Files.writeString(snd.getParent().resolve("sounds.json"), "{\"x.junk\": {\"sounds\": [{\"name\": \"junk\", \"stream\": true}]},"
                + "\"x.a\": {\"sounds\": [{\"name\": \"x.b\", \"type\": \"event\"}]},"
                + "\"x.b\": {\"sounds\": [{\"name\": \"x.a\", \"type\": \"event\", \"weight\": 99999999999}]}}");
        mc.client.SoundPack sp = mc.client.SoundPack.discover(List.of(dir), List.of());
        check(sp != null, "pack not discovered");
        int failures = 0;
        for (int i = 0; i < 50; i++) {
            try { sp.stream(sp.pick("x.junk", new Random(i)).variant()).close(); } catch (java.io.IOException e) { failures++; }
        }
        check(failures == 50, "corrupt ogg should fail to open as a stream");
        sp.prewarm(List.of("x.a")); // must terminate (cycle a -> b -> a)
        check(sp.pick("x.a", new Random(1)) == null, "cyclic event reference should resolve to silence");
        sp.close();
        // the file handle was released: the pack directory can be deleted (would fail on Windows if leaked)
        Files.delete(snd.resolve("junk.ogg"));
    }

    static void commandArgs() throws Exception {
        IntegratedServer s = IntegratedServer.create(null, "Cmd", 7L, Player.SURVIVAL, true);
        Player p = s.player;
        double x0 = p.x, y0 = p.y, z0 = p.z;
        long t0 = System.nanoTime();
        mc.server.Commands.chat(s, "/give stone 2000000000");
        check(System.nanoTime() - t0 < 2_000_000_000L, "/give with a huge count must not hang");
        check(p.inventory.count(Items.get("minecraft:stone")) == 0, "/give over the 6400 limit must be rejected");
        mc.server.Commands.chat(s, "/give stone 70");
        check(p.inventory.count(Items.get("minecraft:stone")) == 70, "/give stone 70");
        for (String bad : new String[]{"/tp NaN 80 0", "/tp 0 Infinity 0", "/tp 1e309 0 0", "/tp ~NaN ~ ~", "/tp 99999999 70 0"}) {
            mc.server.Commands.chat(s, bad);
            check(p.x == x0 && p.y == y0 && p.z == z0, bad + " moved the player to " + p.x + "," + p.y + "," + p.z);
        }
        mc.server.Commands.chat(s, "/effect banana");
        mc.server.Commands.chat(s, "/effect regeneration 10 -1");
        mc.server.Commands.chat(s, "/effect speed 2000000000");
        check(p.effects.isEmpty(), "invalid /effect calls applied effects: " + p.effects.keySet());
        mc.server.Commands.chat(s, "/effect speed 10 1");
        check(p.effects.containsKey("speed") && p.effects.get("speed")[1] == 200, "/effect speed 10 1");
        s.level.dayTime = 1000;
        mc.server.Commands.chat(s, "/time add -999999999");
        check(s.level.dayTime >= 0, "negative day time " + s.level.dayTime);
        mc.server.Commands.chat(s, "/time foo 5");
        mc.server.Commands.chat(s, "/weather rain 2000000000");
        check(s.level.rainTime >= 0, "weather duration overflowed: " + s.level.rainTime);
        mc.server.Commands.chat(s, "/gamerule doDaylightCycle banana");
        mc.server.Commands.chat(s, "/gamerule randomTickSpeed fast");
        check(s.level.rule("doDaylightCycle") && "3".equals(s.level.gamerules.get("randomTickSpeed")), "invalid gamerule values were stored: " + s.level.gamerules);
        mc.server.Commands.chat(s, "/");
        mc.server.Commands.chat(s, "/   ");
        s.shutdown();
    }

    static void hostilePackets() throws Exception {
        IntegratedServer s = IntegratedServer.create(null, "Packets", 8L, Player.SURVIVAL, true);
        Player p = s.player;
        for (int i = 0; i < 40; i++) {
            mc.server.Actions.handle(s, new mc.net.C2S.Input(Float.NaN, Float.POSITIVE_INFINITY, false, false, true, Float.NaN, Float.NaN));
            s.tick();
        }
        check(Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z) && Float.isFinite(p.yaw) && Float.isFinite(p.pitch),
                "NaN input corrupted the player: " + p.x + "," + p.y + "," + p.z + " yaw " + p.yaw + " pitch " + p.pitch);
        mc.server.Actions.handle(s, new mc.net.C2S.Dig((int) p.x + 500, 40, (int) p.z, 1));
        check(!p.digging, "server accepted a dig 500 blocks away");
        mc.server.Actions.handle(s, new mc.net.C2S.Select(99));
        mc.server.Actions.handle(s, new mc.net.C2S.Select(-1));
        check(p.inventory.selected >= 0 && p.inventory.selected < 9, "bad hotbar slot accepted");
        for (int slot : new int[]{-999, -1, 46, 999, Integer.MAX_VALUE, Integer.MIN_VALUE})
            for (int button : new int[]{0, 1, 2, -1, 99})
                mc.server.Actions.handle(s, new mc.net.C2S.Click(0, slot, button, slot % 2 == 0));
        mc.server.Actions.handle(s, new mc.net.C2S.Attack(Integer.MIN_VALUE));
        mc.server.Actions.handle(s, new mc.net.C2S.CreativeGive("minecraft:stone", 64)); // survival: ignored
        check(p.inventory.count(Items.get("minecraft:stone")) == 0, "creative give accepted in survival");
        s.tick();
        s.shutdown();
    }

    static void xpClamp() throws Exception {
        IntegratedServer s = IntegratedServer.create(null, "Xp", 9L, Player.SURVIVAL, false); // not headless: sounds reach outbound
        Player p = s.player;
        p.giveXp(-50);
        check(p.xpLevel == 0 && p.xpProgress >= 0 && p.xpTotal >= 0, "negative xp: level " + p.xpLevel + " progress " + p.xpProgress);
        p.giveXp(7 + 9 + 11); // exactly 3 levels (7, 9, 11 points)
        check(p.xpLevel == 3 && p.xpProgress < 1e-4, "3 levels expected, got " + p.xpLevel + " + " + p.xpProgress);
        p.giveXp(-8); // back into level 2 (11 points per level) with 11 - 8 = 3 points
        check(p.xpLevel == 2 && Math.abs(p.xpProgress - 3 / 11f) < 1e-3, "removal across a level: " + p.xpLevel + " + " + p.xpProgress);
        p.addXpLevels(-1000);
        check(p.xpLevel == 0 && p.xpProgress == 0, "levels below zero");
        s.outbound.clear();
        p.giveXp(5_000_000); // hundreds of level-ups in one call
        long sounds = s.outbound.stream().filter(pk -> pk.toString().contains("entity.player.levelup")).count();
        check(p.xpLevel > 100, "large xp gain: level " + p.xpLevel);
        check(sounds <= 1, "level-up sound must be rate limited, got " + sounds);
        s.shutdown();
    }

    static void soundPack() throws Exception {
        Path dir = Paths.get(System.getProperty("mc.tests", "tests"), "fixtures", "soundpack");
        mc.client.SoundPack sp = mc.client.SoundPack.discover(List.of(dir), List.of());
        check(sp != null, "fixture pack not found in " + dir);
        Random r = new Random(1);
        mc.client.SoundPack.Pick pk = sp.pick("entity.cow.ambient", r); // event reference -> block.stone.break -> test/tone
        check(pk != null && pk.variant().name.equals("test/tone"), "event reference not followed: " + (pk == null ? null : pk.variant().name));
        check(Math.abs(pk.pitch() - 0.9f) < 1e-6 && Math.abs(pk.volume() - 0.8f) < 1e-6, "variant volume/pitch not applied");
        mc.client.OggDecoder.Clip c = sp.clip(pk.variant());
        check(c != null && c.rate == 44100, "tone.ogg did not decode");
        check(Math.abs(c.seconds() - 0.25) < 0.03, "decoded length " + c.seconds());
        // the decoded waveform must match the 440 Hz source tone
        double num = 0, da = 0, db = 0;
        for (int i = 0; i < Math.min(c.pcm.length, 11025); i++) {
            double t = i / 44100.0, ref = 0.5 * Math.sin(2 * Math.PI * 440 * t) * Math.min(1, (0.25 - t) * 40), x = c.pcm[i] / 32768.0;
            num += x * ref; da += x * x; db += ref * ref;
        }
        check(num / Math.sqrt(da * db) > 0.99, "decoded audio does not match the source (corr " + num / Math.sqrt(da * db) + ")");
        mc.client.SoundPack.Pick m = sp.pick("music.menu", r);
        check(m.variant().stream, "stream flag lost");
        try (mc.client.OggDecoder d = sp.stream(m.variant())) {
            check(d.channels == 2 && d.rate == 48000, "stereo stream header");
            float[] buf = new float[2048 * 2];
            long n = 0; int k;
            while ((k = d.read(buf, 2048)) > 0) n += k;
            check(Math.abs(n / 48000.0 - 0.25) < 0.03, "streamed length " + n / 48000.0);
        }
    }
}
