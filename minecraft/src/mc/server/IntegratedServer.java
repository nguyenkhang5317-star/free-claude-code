package mc.server;

import mc.content.*;
import mc.core.*;
import mc.entity.*;
import mc.gen.*;
import mc.net.C2S;
import mc.net.S2C;
import mc.util.Json;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Integrated single-player server (Section 2.3). Runs the 20 TPS simulation; talks to the client only through
 * packet queues. Owns the save directory, the level metadata and one World per visited dimension.
 */
public final class IntegratedServer implements WorldListener {
    public final Path dir;
    public final Level level;
    public final RegionStore store;
    public final Map<Dimension, World> worlds = new LinkedHashMap<>();
    public Player player;
    public final ConcurrentLinkedQueue<C2S.Packet> inbound = new ConcurrentLinkedQueue<>();
    public final ConcurrentLinkedQueue<S2C.Packet> outbound = new ConcurrentLinkedQueue<>();
    public int viewDistance = Registries.tunei("default_view_distance");
    public boolean paused;
    public double lastTickMs;
    private long ticks;
    private final Set<Long> sentChunks = new HashSet<>();
    private boolean menuWasOpen;
    public final boolean headless;

    private IntegratedServer(Path dir, Level level, boolean headless) {
        this.dir = dir;
        this.level = level;
        this.store = dir == null ? null : new RegionStore(dir);
        this.headless = headless;
    }

    public static ChunkGenerator generatorFor(Dimension d, long seed) {
        if (d == Dimension.NETHER) return new NetherGenerator(seed);
        if (d == Dimension.END) return new EndGenerator(seed);
        return new OverworldGenerator(seed);
    }

    public World world(Dimension d) {
        World w = worlds.get(d);
        if (w == null) {
            w = new World(d, level, generatorFor(d, level.seed), store);
            w.listener = this;
            worlds.put(d, w);
        }
        return w;
    }

    // ------------------------------------------------------------------ lifecycle
    public static IntegratedServer create(Path dir, String name, long seed, int gameMode, boolean headless) throws IOException {
        Level l = new Level();
        l.name = name;
        l.seed = seed;
        l.defaultGameMode = gameMode == Player.CREATIVE ? "creative" : "survival";
        IntegratedServer s = new IntegratedServer(dir, l, headless);
        World ow = s.world(Dimension.OVERWORLD);
        s.findSpawn(ow);
        s.player = new Player(ow);
        s.player.setGameMode(gameMode);
        s.placeAtSpawn(s.player, ow);
        ow.addEntity(s.player);
        if (dir != null) s.save(true);
        return s;
    }

    public static IntegratedServer open(Path dir, boolean headless) throws IOException {
        RegionStore tmp = new RegionStore(dir);
        Map<String, Object> m = tmp.readJson("level.json");
        if (m == null) throw new IOException("No level.json in " + dir);
        Level l = Level.load(Json.obj(m.get("level")));
        IntegratedServer s = new IntegratedServer(dir, l, headless);
        Map<String, Object> pm = Json.obj(m.get("player"));
        Dimension d = Dimension.byId(Json.str(pm, "dimension", "minecraft:overworld"));
        if (d == null) d = Dimension.OVERWORLD;
        World w = s.world(d);
        if (!l.spawnFound) s.findSpawn(s.world(Dimension.OVERWORLD));
        s.player = new Player(w);
        if (!pm.isEmpty()) {
            s.player.load(pm);
            int cx = (int) Math.floor(s.player.x) >> 4, cz = (int) Math.floor(s.player.z) >> 4;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) w.loadNow(cx + dx, cz + dz);
        } else {
            s.placeAtSpawn(s.player, w);
        }
        w.addEntity(s.player);
        return s;
    }

    /** Spiral search from (0,0) for a dry land column (Appendix AN spawn rules). */
    private void findSpawn(World ow) {
        ChunkGenerator g = ow.gen;
        int x = 0, z = 0, dx = 0, dz = -1;
        for (int i = 0; i < 4096; i++) {
            int wx = x * 16 + 8, wz = z * 16 + 8;
            Biome b = g.biomeAt(wx, wz);
            int h = g.surfaceHeight(wx, wz);
            String id = b.id;
            if (h >= 64 && !id.contains("ocean") && !id.contains("river") && !id.contains("beach") && !id.contains("mountains")) {
                level.spawnX = wx;
                level.spawnZ = wz;
                break;
            }
            if (x == z || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) { int t = dx; dx = -dz; dz = t; }
            x += dx;
            z += dz;
        }
        Chunk c = ow.loadNow(level.spawnX >> 4, level.spawnZ >> 4);
        // pick the best column in the chunk: solid ground, two air blocks above
        int bestY = -1;
        for (int i = 0; i < 256 && bestY < 0; i++) {
            int lx = (i * 7) & 15, lz = (i * 11 + i / 16) & 15;
            int y = ow.topSolidY((c.cx << 4) + lx, (c.cz << 4) + lz);
            int top = ow.getBlock((c.cx << 4) + lx, y - 1, (c.cz << 4) + lz);
            if (y > 62 && !Blocks.fluid[top] && !Blocks.block(top).id.endsWith("_leaves")) {
                level.spawnX = (c.cx << 4) + lx;
                level.spawnZ = (c.cz << 4) + lz;
                bestY = y;
            }
        }
        level.spawnY = bestY > 0 ? bestY : ow.topSolidY(level.spawnX, level.spawnZ);
        level.spawnFound = true;
    }

    public void placeAtSpawn(Player p, World w) {
        int sx = level.spawnX, sz = level.spawnZ;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) w.loadNow((sx >> 4) + dx, (sz >> 4) + dz);
        int y = w.topSolidY(sx, sz);
        while (y < 250 && (Blocks.solidCollision[w.getBlock(sx, y, sz)] || Blocks.solidCollision[w.getBlock(sx, y + 1, sz)])) y++;
        p.setPos(sx + 0.5, y, sz + 0.5);
    }

    // ------------------------------------------------------------------ tick
    public void tick() {
        long t0 = System.nanoTime();
        C2S.Packet pk;
        int budget = 256;
        while (budget-- > 0 && (pk = inbound.poll()) != null) {
            try {
                Actions.handle(this, pk);
            } catch (RuntimeException e) {
                System.err.println("[server] packet " + pk + " failed: " + e);
                e.printStackTrace();
            }
        }
        if (paused) { sendState(); return; }
        ticks++;
        World pw = player.world;
        World ow = world(Dimension.OVERWORLD);
        for (World w : worlds.values()) {
            if (w != pw && w != ow) continue;
            if (w == pw) w.updateTickets(viewDistance);
            else if (w.players.isEmpty()) w.updateTickets(-1);
            w.tick(w == ow);
        }
        sleepCheck();
        if (player.wantsPortal) {
            player.wantsPortal = false;
            Portals.travel(this, player);
        }
        if (store != null && ticks % Registries.tunei("autosave_ticks") == 0) {
            try {
                save(false);
            } catch (IOException e) {
                System.err.println("[save] autosave failed: " + e);
            }
        }
        sendState();
        lastTickMs = (System.nanoTime() - t0) / 1e6;
    }

    private void sleepCheck() {
        Player p = player;
        if (!p.sleeping) return;
        if (!Weather.canSleep(level) && level.timeOfDay() < 12000) { p.wake(); return; }
        if (p.sleepTimer >= 100) {
            long tod = level.dayTime % 24000;
            level.dayTime += 24000 - tod;
            level.raining = false;
            level.thundering = false;
            level.rainTime = Registries.tunei("clear_min_ticks");
            p.wake();
        }
    }

    // ------------------------------------------------------------------ outbound
    private void sendState() {
        Player p = player;
        World w = p.world;
        // entity frames (within view distance)
        List<S2C.EntityFrame> frames = new ArrayList<>();
        double r = (viewDistance + 1) * 16;
        for (Entity e : w.entities) {
            if (e == p || e.removed && !(e instanceof LivingEntity)) continue;
            if (Math.abs(e.x - p.x) > r || Math.abs(e.z - p.z) > r) continue;
            boolean living = e instanceof LivingEntity;
            LivingEntity le = living ? (LivingEntity) e : null;
            int swing = e instanceof Mob ? ((Mob) e).swingTime : 0;
            frames.add(new S2C.EntityFrame(e.id, e.type.index, e.x, e.y, e.z, e.yaw, living ? le.headYaw : e.yaw, e.pitch,
                    living ? le.limbSwing : 0, living ? le.limbSwingAmount : 0, living ? le.hurtTime : 0, living ? le.deathTime : 0,
                    living && le.dead, e.renderData(), e.renderData2(), swing, e.fireTicks > 0, e.width, e.height));
        }
        outbound.add(new S2C.Entities(frames));
        ItemStack[] main = new ItemStack[36], arm = new ItemStack[4];
        for (int i = 0; i < 36; i++) main[i] = p.inventory.main[i] == null ? null : p.inventory.main[i].copy();
        for (int i = 0; i < 4; i++) arm[i] = p.inventory.armor[i] == null ? null : p.inventory.armor[i].copy();
        String[] eff = new String[p.effects.size()];
        int k = 0;
        for (Map.Entry<String, int[]> e : p.effects.entrySet()) eff[k++] = e.getKey() + " " + (e.getValue()[0] + 1) + " " + e.getValue()[1] / 20 + "s";
        int feet = w.getBlock((int) Math.floor(p.x), (int) Math.floor(p.y + 0.2), (int) Math.floor(p.z));
        outbound.add(new S2C.PlayerState(p.id, p.x, p.y, p.z, p.health, p.maxHealth, p.absorption, p.food, p.saturation, p.air, 300,
                p.armorValue(), p.xpLevel, p.xpProgress, p.gameMode, p.flying, p.dead, p.deathMessage, main, arm,
                p.inventory.offhand() == null ? null : p.inventory.offhand().copy(), p.inventory.selected, p.digging, p.digX, p.digY, p.digZ,
                p.digProgress, p.sleeping, p.sleepTimer, p.attackStrength(), p.hurtTime, p.fireTicks > 0, p.submerged, p.inLava, p.sneaking,
                p.sprinting, p.onGround, p.useTicks, p.useItem != null && p.useItem.item.id.equals("minecraft:bow"), eff, p.eyeY() - p.y,
                p.yaw, p.pitch, p.limbSwing, p.limbSwingAmount, p.swingTime, Blocks.is(feet, Blocks.NETHER_PORTAL)));
        if (p.swingTime > 0) p.swingTime--;
        Menu m = p.menu;
        if (m != p.invMenu || menuWasOpen) {
            if (m != p.invMenu) {
                BlockEntity be = m.be;
                outbound.add(new S2C.MenuState(m.windowId, m.kind, m.title, m.snapshot(), p.carried == null ? null : p.carried.copy(),
                        be == null ? 0 : be.burnTime, be == null ? 0 : be.burnTotal, be == null ? 0 : be.cookTime, be == null ? 200 : be.cookTotal));
                menuWasOpen = true;
            } else {
                outbound.add(new S2C.MenuClosed());
                menuWasOpen = false;
            }
        }
        if (playerInventoryOpen) {
            outbound.add(new S2C.MenuState(0, Menu.INVENTORY, "Crafting", p.invMenu.snapshot(), p.carried == null ? null : p.carried.copy(), 0, 0, 0, 0));
        }
        int bx = (int) Math.floor(p.x), bz = (int) Math.floor(p.z);
        outbound.add(new S2C.WorldInfo(level.gameTime, level.dayTime, level.raining && w.dim.hasSky, level.thundering && w.dim.hasSky,
                w.dim.hasSky ? level.skyDarken() : 0, level.moonPhase(), w.biomeAt(bx, bz).id, w.loadedChunks().size(), w.pendingCount(),
                w.entities.size(), w.stageNanos.clone(), lastTickMs, level.seed, level.difficulty));
    }

    public boolean playerInventoryOpen;

    /** Send every loaded chunk of the player's world (on join and after a dimension change). */
    public void resendAll() {
        World w = player.world;
        sentChunks.clear();
        outbound.add(new S2C.Dimension(w.dim.id, w.dim.hasSky, w.dim.fogColor));
        for (Chunk c : w.loadedChunks()) chunkChanged(w, c);
    }

    // ------------------------------------------------------------------ WorldListener
    @Override
    public void chunkChanged(World w, Chunk c) {
        if (headless || player == null || w != player.world) return;
        outbound.add(new S2C.ChunkData(c.cx, c.cz, c.snapshotBlocks(), c.snapshotLight(), c.biomes.clone()));
        sentChunks.add(c.key());
    }

    @Override
    public void chunkUnloaded(World w, int cx, int cz) {
        if (headless || player == null || w != player.world) return;
        sentChunks.remove(Chunk.key(cx, cz));
        outbound.add(new S2C.ChunkUnload(cx, cz));
    }

    @Override
    public void sound(World w, String event, double x, double y, double z, float volume, float pitch) {
        if (headless || player == null || w != player.world) return;
        outbound.add(new S2C.Sound(event, x, y, z, volume, pitch));
    }

    @Override
    public void particles(World w, String kind, double x, double y, double z, int count, int data) {
        if (headless || player == null || w != player.world) return;
        outbound.add(new S2C.Particles(kind, x, y, z, count, data));
    }

    @Override
    public void message(String text) {
        if (headless) System.out.println("[chat] " + text);
        else outbound.add(new S2C.Message(text));
    }

    // ------------------------------------------------------------------ saving
    public synchronized void save(boolean flush) throws IOException {
        if (store == null) return;
        long t0 = System.nanoTime();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("level", level.save());
        root.put("player", player.save());
        store.writeJson("level.json", root);
        for (World w : worlds.values()) w.saveAll(flush);
        if (flush) store.flush();
        if (headless) System.out.printf("[save] saved in %.1f ms%n", (System.nanoTime() - t0) / 1e6);
    }

    public void shutdown() {
        try {
            if (player.menu != player.invMenu) player.closeMenu();
            save(true);
        } catch (IOException e) {
            System.err.println("[save] final save failed: " + e);
        }
        if (store != null) store.close();
    }

    // ------------------------------------------------------------------ respawn
    public void respawn() {
        Player p = player;
        if (!p.dead) return;
        World target = world(Dimension.OVERWORLD);
        boolean bed = false;
        if (p.hasSpawn) {
            World sw = world(Dimension.byId(p.spawnDim) == null ? Dimension.OVERWORLD : Dimension.byId(p.spawnDim));
            sw.loadNow(p.spawnX >> 4, p.spawnZ >> 4);
            if (Blocks.block(sw.getBlock(p.spawnX, p.spawnY, p.spawnZ)).shape == Block.SHAPE_BED) { target = sw; bed = true; }
            else { p.hasSpawn = false; message("You have no home bed or charged respawn anchor, or it was obstructed"); }
        }
        if (target != p.world) {
            p.world.removePlayer(p);
            p.world = target;
            target.addEntity(p);
        }
        p.respawnReset();
        if (bed) {
            int[] spot = freeSpotNear(target, p.spawnX, p.spawnY, p.spawnZ);
            p.setPos(spot[0] + 0.5, spot[1], spot[2] + 0.5);
        } else placeAtSpawn(p, target);
        resendAll();
    }

    private int[] freeSpotNear(World w, int x, int y, int z) {
        for (int r = 1; r <= 2; r++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++)
                    for (int dy = -1; dy <= 1; dy++)
                        if (Pathfinder.standable(w, x + dx, y + dy, z + dz, 2)) return new int[]{x + dx, y + dy, z + dz};
        return new int[]{x, y + 1, z};
    }
}
