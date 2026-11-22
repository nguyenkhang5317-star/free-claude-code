package mc.core;

import mc.content.*;
import mc.entity.*;
import mc.util.AABB;
import mc.util.Hash;

import java.util.*;
import java.util.concurrent.*;

/**
 * Server-side world for one dimension (Section 2). Owns the chunk map, background generation/loading workers,
 * block updates, scheduled ticks (fluids, falling blocks, fire), random ticks, block entities and entities.
 * All mutation happens on the server thread; workers only produce fresh Chunk objects.
 */
public final class World implements IWorld {
    public final Dimension dim;
    public final Level level;
    public final ChunkGenerator gen;
    public final RegionStore store;
    public WorldListener listener = WorldListener.NULL;
    public final Random rng;

    private final HashMap<Long, Chunk> chunks = new HashMap<>();
    private final HashMap<Long, Future<Chunk>> pending = new HashMap<>();
    private final Set<Long> lightDirty = new LinkedHashSet<>();
    public final List<Entity> entities = new ArrayList<>();
    private final List<Entity> addQueue = new ArrayList<>();
    public final List<Player> players = new ArrayList<>();
    private final PriorityQueue<long[]> scheduled = new PriorityQueue<>((a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[2], b[2]));
    private final Set<Long> scheduledSet = new HashSet<>();
    private long schedSeq;
    public final Spawner spawner;
    public int loadedThisTick;
    public long[] stageNanos = new long[4]; // chunks, blocks, entities, spawning (F3 timers)

    private static ExecutorService WORKERS;

    private static synchronized ExecutorService workers() {
        if (WORKERS == null) {
            int n = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
            WORKERS = Executors.newFixedThreadPool(n, r -> {
                Thread t = new Thread(r, "chunk-worker");
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            });
        }
        return WORKERS;
    }

    public World(Dimension dim, Level level, ChunkGenerator gen, RegionStore store) {
        this.dim = dim;
        this.level = level;
        this.gen = gen;
        this.store = store;
        this.rng = new Random(level.seed ^ (dim.index * 0x9E3779B97F4A7C15L));
        this.spawner = new Spawner(this);
    }

    // ------------------------------------------------------------------ chunk access
    public Chunk chunkAt(int cx, int cz) { return chunks.get(Chunk.key(cx, cz)); }

    public boolean isLoaded(int cx, int cz) { return chunks.containsKey(Chunk.key(cx, cz)); }

    public Collection<Chunk> loadedChunks() { return chunks.values(); }

    public int pendingCount() { return pending.size(); }

    @Override
    public int getBlock(int x, int y, int z) {
        if (y < 0 || y > 255) return Blocks.AIR;
        Chunk c = chunks.get(Chunk.key(x >> 4, z >> 4));
        return c == null ? Blocks.AIR : c.get(x & 15, y, z & 15);
    }

    public boolean isLoadedBlock(int x, int z) { return isLoaded(x >> 4, z >> 4); }

    @Override
    public int getSkyLight(int x, int y, int z) {
        if (y > 255) return dim.hasSky ? 15 : 0;
        if (y < 0) return 0;
        Chunk c = chunkAt(x >> 4, z >> 4);
        return c == null ? (dim.hasSky ? 15 : 0) : c.sky(x & 15, y, z & 15);
    }

    @Override
    public int getBlockLight(int x, int y, int z) {
        if (y < 0 || y > 255) return 0;
        Chunk c = chunkAt(x >> 4, z >> 4);
        return c == null ? 0 : c.blockL(x & 15, y, z & 15);
    }

    /** Combined light after sky darkening (used for spawning and growth). */
    public int getRawBrightness(int x, int y, int z) {
        int sky = getSkyLight(x, y, z) - (dim.hasSky ? level.skyDarken() : 0);
        return Math.max(sky, getBlockLight(x, y, z));
    }

    public int heightAt(int x, int z) {
        Chunk c = chunkAt(x >> 4, z >> 4);
        return c == null ? 0 : c.height(x & 15, z & 15);
    }

    /** Topmost block that blocks motion or is fluid, +1. */
    public int topSolidY(int x, int z) {
        for (int y = 255; y > 0; y--) {
            int s = getBlock(x, y, z);
            if (Blocks.solidCollision[s] || Blocks.fluid[s]) return y + 1;
        }
        return 0;
    }

    public boolean canSeeSky(int x, int y, int z) { return y >= heightAt(x, z); }

    public Biome biomeAt(int x, int z) {
        Chunk c = chunkAt(x >> 4, z >> 4);
        if (c != null) {
            int i = c.biomes[(z & 15) << 4 | (x & 15)] & 0xFF;
            if (i < Biomes.ALL.size()) return Biomes.ALL.get(i);
        }
        return gen.biomeAt(x, z);
    }

    public void markLightChanged(Chunk c) { lightDirty.add(c.key()); }

    public void markChanged(Chunk c) {
        c.dirty = true;
        lightDirty.add(c.key());
    }

    // ------------------------------------------------------------------ loading / unloading
    public void request(int cx, int cz) {
        long k = Chunk.key(cx, cz);
        if (chunks.containsKey(k) || pending.containsKey(k)) return;
        pending.put(k, workers().submit(() -> {
            Chunk c = null;
            try {
                if (store != null) c = store.load(dim, cx, cz);
            } catch (Exception e) {
                System.err.println("[save] failed to read chunk " + cx + "," + cz + ": " + e);
            }
            if (c == null) {
                c = gen.generate(cx, cz);
                LightEngine.initChunk(c, dim.hasSky);
                c.dirty = true;
                c.fresh = true;
            }
            c.status = Chunk.STATUS_FULL;
            return c;
        }));
    }

    /** Synchronously load or generate a chunk (used for spawn preparation and tests). */
    public Chunk loadNow(int cx, int cz) {
        Chunk c = chunkAt(cx, cz);
        if (c != null) return c;
        request(cx, cz);
        Future<Chunk> f = pending.remove(Chunk.key(cx, cz));
        try {
            c = f.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        integrate(c);
        return c;
    }

    private void integrate(Chunk c) {
        chunks.put(c.key(), c);
        LightEngine.seam(this, c);
        for (BlockEntity be : c.blockEntities.values()) if (be.type.equals("furnace")) furnaces.add(be);
        for (Map<String, Object> m : c.pendingEntities) {
            Entity e = EntityFactory.load(this, m);
            if (e != null) addEntity(e);
        }
        c.pendingEntities.clear();
        if (c.fresh) {
            c.fresh = false;
            if (dim == Dimension.OVERWORLD) spawner.populate(c);
        }
        markLightChanged(c);
        for (int d = 0; d < 4; d++) {
            Chunk n = chunkAt(c.cx + (d == 0 ? 1 : d == 1 ? -1 : 0), c.cz + (d == 2 ? 1 : d == 3 ? -1 : 0));
            if (n != null) markLightChanged(n);
        }
    }

    /** Integrate finished background chunks (bounded per tick to protect the tick budget). */
    public void pollLoaded(int max) {
        loadedThisTick = 0;
        if (pending.isEmpty()) return;
        Iterator<Map.Entry<Long, Future<Chunk>>> it = pending.entrySet().iterator();
        while (it.hasNext() && loadedThisTick < max) {
            Map.Entry<Long, Future<Chunk>> e = it.next();
            if (!e.getValue().isDone()) continue;
            it.remove();
            try {
                integrate(e.getValue().get());
                loadedThisTick++;
            } catch (Exception ex) {
                System.err.println("[world] chunk task failed: " + ex.getCause());
                ex.printStackTrace();
            }
        }
    }

    /** Keep chunks within view distance of players loaded; unload (and save) the rest. */
    public void updateTickets(int viewDistance) {
        Set<Long> want = new HashSet<>();
        List<long[]> order = new ArrayList<>();
        for (Player p : players) {
            int pcx = (int) Math.floor(p.x) >> 4, pcz = (int) Math.floor(p.z) >> 4;
            for (int dx = -viewDistance - 1; dx <= viewDistance + 1; dx++)
                for (int dz = -viewDistance - 1; dz <= viewDistance + 1; dz++) {
                    long k = Chunk.key(pcx + dx, pcz + dz);
                    if (want.add(k)) order.add(new long[]{dx * dx + dz * dz, pcx + dx, pcz + dz});
                }
        }
        order.sort(Comparator.comparingLong(a -> a[0]));
        int budget = 64;
        for (long[] o : order) {
            if (pending.size() >= budget) break;
            request((int) o[1], (int) o[2]);
        }
        // cancel pending far requests
        pending.entrySet().removeIf(e -> !want.contains(e.getKey()) && e.getValue().cancel(false));
        List<Chunk> drop = new ArrayList<>();
        for (Chunk c : chunks.values()) {
            boolean keep = false;
            for (Player p : players) {
                int pcx = (int) Math.floor(p.x) >> 4, pcz = (int) Math.floor(p.z) >> 4;
                if (Math.abs(c.cx - pcx) <= viewDistance + 3 && Math.abs(c.cz - pcz) <= viewDistance + 3) { keep = true; break; }
            }
            if (!keep) drop.add(c);
        }
        for (Chunk c : drop) unload(c);
    }

    private void unload(Chunk c) {
        // move non-player entities in this chunk into the chunk record
        List<Map<String, Object>> saved = new ArrayList<>();
        for (Entity e : entities) {
            if (e instanceof Player || e.removed) continue;
            if (((int) Math.floor(e.x) >> 4) == c.cx && ((int) Math.floor(e.z) >> 4) == c.cz) {
                if (e.shouldSave()) saved.add(e.save());
                e.removed = true;
                c.dirty = true;
            }
        }
        entities.removeIf(e -> e.removed && !(e instanceof Player));
        furnaces.removeIf(be -> (be.x >> 4) == c.cx && (be.z >> 4) == c.cz);
        if (store != null && c.dirty) {
            try {
                store.save(dim, c, saved);
            } catch (Exception ex) {
                System.err.println("[save] chunk " + c.cx + "," + c.cz + ": " + ex);
            }
        }
        chunks.remove(c.key());
        listener.chunkUnloaded(this, c.cx, c.cz);
    }

    /** Save every loaded chunk (autosave / quit). Entities are stored with their chunk. */
    public void saveAll(boolean flush) {
        if (store == null) return;
        Map<Long, List<Map<String, Object>>> byChunk = new HashMap<>();
        for (Entity e : entities) {
            if (e instanceof Player || e.removed || !e.shouldSave()) continue;
            long k = Chunk.key((int) Math.floor(e.x) >> 4, (int) Math.floor(e.z) >> 4);
            byChunk.computeIfAbsent(k, kk -> new ArrayList<>()).add(e.save());
        }
        for (Chunk c : chunks.values()) {
            List<Map<String, Object>> ents = byChunk.getOrDefault(c.key(), Collections.emptyList());
            if (!c.dirty && ents.isEmpty() && !c.hadEntities) continue;
            try {
                store.save(dim, c, ents);
                c.dirty = false;
                c.hadEntities = !ents.isEmpty();
            } catch (Exception ex) {
                System.err.println("[save] chunk " + c.cx + "," + c.cz + ": " + ex);
            }
        }
        if (flush) store.flush();
    }

    // ------------------------------------------------------------------ block mutation
    public static final int NOTIFY = 1, SEND = 2, NO_DROP_BE = 4;

    @Override
    public boolean setBlock(int x, int y, int z, int state) { return setBlock(x, y, z, state, NOTIFY | SEND); }

    public boolean setBlock(int x, int y, int z, int state, int flags) {
        if (y < 0 || y > 255) return false;
        Chunk c = chunkAt(x >> 4, z >> 4);
        if (c == null) return false;
        int lx = x & 15, lz = z & 15;
        int old = c.get(lx, y, lz);
        if (old == state) return false;
        c.set(lx, y, lz, state);
        c.updateHeight(lx, lz);
        Block ob = Blocks.block(old), nb = Blocks.block(state);
        if (ob != nb) {
            int key = BlockEntity.localKey(x, y, z);
            BlockEntity be = c.blockEntities.remove(key);
            if (be != null) {
                furnaces.remove(be);
                if ((flags & NO_DROP_BE) == 0) for (ItemStack s : be.items) if (s != null) dropItem(x + 0.5, y + 0.5, z + 0.5, s);
            }
            String bet = nb.has("interact") ? nb.extraStr("interact") : null;
            if ("chest".equals(bet) || "furnace".equals(bet)) {
                BlockEntity nbe = new BlockEntity(bet, x, y, z);
                c.blockEntities.put(key, nbe);
                if (bet.equals("furnace")) furnaces.add(nbe);
            }
        }
        LightEngine.update(this, x, y, z, old, state);
        markChanged(c);
        if (lx == 0) markNeighbor(c.cx - 1, c.cz);
        if (lx == 15) markNeighbor(c.cx + 1, c.cz);
        if (lz == 0) markNeighbor(c.cx, c.cz - 1);
        if (lz == 15) markNeighbor(c.cx, c.cz + 1);
        if ((flags & NOTIFY) != 0) {
            neighborChanged(x, y, z);
            neighborChanged(x + 1, y, z);
            neighborChanged(x - 1, y, z);
            neighborChanged(x, y + 1, z);
            neighborChanged(x, y - 1, z);
            neighborChanged(x, y, z + 1);
            neighborChanged(x, y, z - 1);
        }
        return true;
    }

    private void markNeighbor(int cx, int cz) {
        Chunk n = chunkAt(cx, cz);
        if (n != null) markLightChanged(n);
    }

    public BlockEntity blockEntity(int x, int y, int z) {
        Chunk c = chunkAt(x >> 4, z >> 4);
        return c == null ? null : c.blockEntities.get(BlockEntity.localKey(x, y, z));
    }

    /** Break a block as if mined (drops, sound, particles). */
    public void destroyBlock(int x, int y, int z, boolean drop, ItemStack tool, Entity by) {
        int s = getBlock(x, y, z);
        if (s == Blocks.AIR) return;
        Block b = Blocks.block(s);
        if (b.shape == Block.SHAPE_BED) breakBedOther(x, y, z, s);
        if (drop && level.rule("doTileDrops")) {
            for (ItemStack st : Mining.drops(s, tool, rng)) dropItem(x + 0.5, y + 0.5, z + 0.5, st);
            int xp = Mining.xp(s, rng);
            if (xp > 0 && (tool == null || Mining.canHarvest(s, tool))) spawnXp(x + 0.5, y + 0.5, z + 0.5, xp);
        }
        playSound("block." + b.sound + ".break", x + 0.5, y + 0.5, z + 0.5, 1f, 0.8f);
        listener.particles(this, "block", x + 0.5, y + 0.5, z + 0.5, 12, s);
        int replace = Blocks.AIR;
        setBlock(x, y, z, replace);
    }

    private void breakBedOther(int x, int y, int z, int s) {
        String part = Blocks.value(s, "part"), facing = Blocks.value(s, "facing");
        int[] d = facingVec(facing);
        int ox = "foot".equals(part) ? x + d[0] : x - d[0], oz = "foot".equals(part) ? z + d[1] : z - d[1];
        if (Blocks.block(getBlock(ox, y, oz)).shape == Block.SHAPE_BED) setBlock(ox, y, oz, Blocks.AIR, SEND);
    }

    public static int[] facingVec(String f) {
        if (f == null) return new int[]{0, 1};
        switch (f) {
            case "north": return new int[]{0, -1};
            case "south": return new int[]{0, 1};
            case "west": return new int[]{-1, 0};
            default: return new int[]{1, 0};
        }
    }

    // ------------------------------------------------------------------ neighbor updates & scheduled ticks
    public void schedule(int x, int y, int z, int delay) {
        if (y < 0 || y > 255) return;
        long pos = packPos(x, y, z);
        if (!scheduledSet.add(pos)) return;
        scheduled.add(new long[]{level.gameTime + Math.max(1, delay), pos, schedSeq++});
    }

    public static long packPos(int x, int y, int z) { return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF); }

    public static int unpackX(long p) { return (int) (p >> 38); }

    public static int unpackY(long p) { return (int) (p << 52 >> 52); }

    public static int unpackZ(long p) { return (int) (p << 26 >> 38); }

    private void neighborChanged(int x, int y, int z) {
        if (y < 0 || y > 255) return;
        int s = getBlock(x, y, z);
        if (s == Blocks.AIR) return;
        Block b = Blocks.block(s);
        if (b.has("fluid")) {
            schedule(x, y, z, (int) b.extraNum("tick_rate", 5) * (dim.ultrawarm && Blocks.isLava(s) ? 1 : 1));
            return;
        }
        if (b.gravity) {
            schedule(x, y, z, 2);
            return;
        }
        if (s == Blocks.FIRE) {
            schedule(x, y, z, 30 + rng.nextInt(10));
        }
        if (s == Blocks.NETHER_PORTAL && !portalIntact(x, y, z, s)) {
            setBlock(x, y, z, Blocks.AIR);
            return;
        }
        if (!isSupported(x, y, z, s)) destroyBlock(x, y, z, true, null, null);
    }

    /** Placement / survival rules (support field in blocks.json). */
    public boolean isSupported(int x, int y, int z, int s) {
        Block b = Blocks.block(s);
        String sup = b.extraStr("support");
        int below = getBlock(x, y - 1, z);
        if (b.shape == Block.SHAPE_TORCH) return Blocks.solidCollision[below] && Blocks.block(below).shape != Block.SHAPE_TORCH;
        if (b.shape == Block.SHAPE_LADDER) {
            int[] d = facingVec(Blocks.value(s, "facing"));
            return Blocks.opaqueCube[getBlock(x - d[0], y, z - d[1])];
        }
        if (b == Blocks.block(Blocks.SNOW)) return Blocks.opaqueCube[below];
        if (s == Blocks.FIRE) return true;
        if (sup == null) return true;
        Block bb = Blocks.block(below);
        switch (sup) {
            case "soil":
                return below == Blocks.GRASS_BLOCK || below == Blocks.DIRT || below == Blocks.PODZOL || Blocks.is(below, Blocks.FARMLAND)
                        || below == Blocks.MYCELIUM || bb.id.equals("minecraft:coarse_dirt")
                        || ((b.id.contains("mushroom") || b.id.contains("fungus")) && Blocks.opaqueCube[below])
                        || (b == Blocks.block(Blocks.DEAD_BUSH) && (below == Blocks.SAND || below == Blocks.RED_SAND || below == Blocks.TERRACOTTA));
            case "farmland": return Blocks.is(below, Blocks.FARMLAND);
            case "sand":
                if (Blocks.is(below, Blocks.CACTUS)) return true;
                if (below != Blocks.SAND && below != Blocks.RED_SAND) return false;
                for (int d = 0; d < 4; d++) {
                    int n = getBlock(x + (d == 0 ? 1 : d == 1 ? -1 : 0), y, z + (d == 2 ? 1 : d == 3 ? -1 : 0));
                    if (Blocks.solidCollision[n] || Blocks.isLava(n)) return false;
                }
                return true;
            case "cane":
                if (Blocks.is(below, Blocks.SUGAR_CANE)) return true;
                if (!(below == Blocks.GRASS_BLOCK || below == Blocks.DIRT || below == Blocks.SAND || below == Blocks.RED_SAND
                        || below == Blocks.PODZOL || bb.id.equals("minecraft:coarse_dirt"))) return false;
                for (int d = 0; d < 4; d++) {
                    int n = getBlock(x + (d == 0 ? 1 : d == 1 ? -1 : 0), y - 1, z + (d == 2 ? 1 : d == 3 ? -1 : 0));
                    if (Blocks.isWater(n) || n == Blocks.ICE) return true;
                }
                return false;
            case "solid_below": return Blocks.solidCollision[below];
            default: return true;
        }
    }

    public void runScheduled() {
        int budget = 65536;
        while (!scheduled.isEmpty() && scheduled.peek()[0] <= level.gameTime && budget-- > 0) {
            long[] t = scheduled.poll();
            scheduledSet.remove(t[1]);
            int x = unpackX(t[1]), y = unpackY(t[1]), z = unpackZ(t[1]);
            if (!isLoadedBlock(x, z)) continue;
            int s = getBlock(x, y, z);
            Block b = Blocks.block(s);
            if (b.has("fluid")) Fluids.tick(this, x, y, z, s);
            else if (b.gravity) tryFall(x, y, z, s);
            else if (s == Blocks.FIRE) Fire.tick(this, x, y, z);
        }
    }

    private void tryFall(int x, int y, int z, int s) {
        int below = getBlock(x, y - 1, z);
        if (y > 0 && (below == Blocks.AIR || Blocks.fluid[below] || s == Blocks.FIRE || Blocks.replaceable[below])) {
            setBlock(x, y, z, Blocks.AIR, SEND | NOTIFY);
            FallingBlock fb = new FallingBlock(this, s);
            fb.setPos(x + 0.5, y, z + 0.5);
            addEntity(fb);
        }
    }

    // ------------------------------------------------------------------ portals (Section 5.6)
    public boolean portalIntact(int x, int y, int z, int s) {
        boolean ax = "x".equals(Blocks.value(s, "axis"));
        int[][] dirs = ax ? new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}} : new int[][]{{0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0}};
        for (int[] d : dirs) {
            int n = getBlock(x + d[0], y + d[1], z + d[2]);
            if (n != Blocks.OBSIDIAN && !Blocks.is(n, Blocks.NETHER_PORTAL)) return false;
        }
        return true;
    }

    /** Try to light a nether portal inside an obsidian frame containing (x,y,z). */
    public boolean tryCreatePortal(int x, int y, int z) {
        for (int axis = 0; axis < 2; axis++) {
            int dx = axis == 0 ? 1 : 0, dz = axis == 0 ? 0 : 1;
            // find bottom-left interior corner
            int by = y;
            while (by > y - 22 && getBlock(x, by - 1, z) == Blocks.AIR) by--;
            if (getBlock(x, by - 1, z) != Blocks.OBSIDIAN) continue;
            int lx = x, lz = z, n = 0;
            while (n < 22 && getBlock(lx - dx, by, lz - dz) == Blocks.AIR) { lx -= dx; lz -= dz; n++; }
            if (getBlock(lx - dx, by, lz - dz) != Blocks.OBSIDIAN) continue;
            int w = 0;
            while (w < 22 && getBlock(lx + dx * w, by, lz + dz * w) == Blocks.AIR) w++;
            if (w < 2 || w > 21 || getBlock(lx + dx * w, by, lz + dz * w) != Blocks.OBSIDIAN) continue;
            int h = 0;
            while (h < 22 && getBlock(lx, by + h, lz) == Blocks.AIR) h++;
            if (h < 3 || h > 21) continue;
            boolean ok = true;
            for (int i = 0; i < w && ok; i++) {
                if (getBlock(lx + dx * i, by - 1, lz + dz * i) != Blocks.OBSIDIAN) ok = false;
                if (getBlock(lx + dx * i, by + h, lz + dz * i) != Blocks.OBSIDIAN) ok = false;
                for (int j = 0; j < h && ok; j++) if (getBlock(lx + dx * i, by + j, lz + dz * i) != Blocks.AIR) ok = false;
            }
            for (int j = 0; j < h && ok; j++) {
                if (getBlock(lx - dx, by + j, lz - dz) != Blocks.OBSIDIAN) ok = false;
                if (getBlock(lx + dx * w, by + j, lz + dz * w) != Blocks.OBSIDIAN) ok = false;
            }
            if (!ok) continue;
            int ps = Blocks.with(Blocks.NETHER_PORTAL, "axis", axis == 0 ? "x" : "z");
            for (int i = 0; i < w; i++)
                for (int j = 0; j < h; j++) setBlock(lx + dx * i, by + j, lz + dz * i, ps, SEND);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ random ticks (Appendix BQ)
    private void randomTicks() {
        int speed = level.ruleInt("randomTickSpeed", 3);
        boolean rain = level.raining && dim.hasSky;
        for (Chunk c : chunks.values()) {
            if (!nearPlayer(c, Registries.tunei("simulation_distance"))) continue;
            int bx = c.cx << 4, bz = c.cz << 4;
            if (rain && rng.nextInt(16) == 0) precipitate(bx + rng.nextInt(16), bz + rng.nextInt(16));
            for (int sec = 0; sec < Chunk.SECTIONS; sec++) {
                if (c.sections[sec].isUniform() && c.sections[sec].get(0) == Blocks.AIR) continue;
                for (int i = 0; i < speed; i++) {
                    int r = rng.nextInt(4096);
                    int lx = r & 15, lz = (r >> 4) & 15, y = (sec << 4) | (r >> 8);
                    int s = c.get(lx, y, lz);
                    if (s == Blocks.AIR) continue;
                    Block b = Blocks.block(s);
                    String rt = b.extraStr("random_tick");
                    if (rt != null) RandomTicks.tick(this, rt, bx + lx, y, bz + lz, s);
                    else if (s == Blocks.ICE && getBlockLight(bx + lx, y + 1, bz + lz) > 11) setBlock(bx + lx, y, bz + lz, Blocks.WATER);
                    else if (b == Blocks.block(Blocks.SNOW) && getBlockLight(bx + lx, y, bz + lz) > 11) setBlock(bx + lx, y, bz + lz, Blocks.AIR);
                }
            }
        }
    }

    private void precipitate(int x, int z) {
        Biome bio = biomeAt(x, z);
        if (!bio.snowy() || "none".equals(bio.precipitation)) return;
        int y = heightAt(x, z);
        if (y <= 0 || y > 255) return;
        int top = getBlock(x, y - 1, z);
        if (Blocks.isWater(top) && Blocks.prop(top, "level") == 0) setBlock(x, y - 1, z, Blocks.ICE);
        else if (getBlock(x, y, z) == Blocks.AIR && Blocks.opaqueCube[top] && getBlockLight(x, y, z) < 10) setBlock(x, y, z, Blocks.SNOW);
    }

    public boolean nearPlayer(Chunk c, int dist) {
        for (Player p : players) {
            int pcx = (int) Math.floor(p.x) >> 4, pcz = (int) Math.floor(p.z) >> 4;
            if (Math.abs(c.cx - pcx) <= dist && Math.abs(c.cz - pcz) <= dist) return true;
        }
        return false;
    }

    public boolean isRainingAt(int x, int y, int z) {
        if (!level.raining || !dim.hasSky) return false;
        if (y < heightAt(x, z)) return false;
        Biome b = biomeAt(x, z);
        return !"none".equals(b.precipitation) && !b.snowy();
    }

    // ------------------------------------------------------------------ block entities
    private final List<BlockEntity> furnaces = new ArrayList<>();

    private void tickBlockEntities() {
        for (BlockEntity be : new ArrayList<>(furnaces)) {
            if (!isLoadedBlock(be.x, be.z)) continue;
            boolean changed = be.tickFurnace();
            Chunk c = chunkAt(be.x >> 4, be.z >> 4);
            if (c != null && (be.burnTime > 0 || changed)) c.dirty = true;
            if (changed) {
                int s = getBlock(be.x, be.y, be.z);
                if (Blocks.block(s).propIndex("lit") >= 0) setBlock(be.x, be.y, be.z, Blocks.with(s, "lit", be.burnTime > 0 ? "true" : "false"), SEND);
            }
        }
    }

    // ------------------------------------------------------------------ entities
    public void addEntity(Entity e) {
        e.world = this;
        addQueue.add(e);
        if (e instanceof Player) players.add((Player) e);
    }

    public void removePlayer(Player p) {
        players.remove(p);
        entities.remove(p);
        addQueue.remove(p);
    }

    public Entity entityById(int id) {
        for (Entity e : entities) if (e.id == id) return e;
        return null;
    }

    public List<Entity> entitiesIn(AABB box, Entity except) {
        List<Entity> out = new ArrayList<>();
        for (Entity e : entities) if (e != except && !e.removed && e.bbox().intersects(box)) out.add(e);
        return out;
    }

    public void dropItem(double x, double y, double z, ItemStack s) {
        if (ItemStack.isEmpty(s)) return;
        ItemEntity ie = new ItemEntity(this, s.copy());
        ie.setPos(x + (rng.nextDouble() - 0.5) * 0.5, y - 0.125, z + (rng.nextDouble() - 0.5) * 0.5);
        ie.vx = rng.nextDouble() * 0.2 - 0.1;
        ie.vy = 0.2;
        ie.vz = rng.nextDouble() * 0.2 - 0.1;
        addEntity(ie);
    }

    public void spawnXp(double x, double y, double z, int amount) {
        while (amount > 0) {
            int v = amount >= 17 ? 17 : amount >= 7 ? 7 : amount >= 3 ? 3 : 1;
            amount -= v;
            XpOrb o = new XpOrb(this, v);
            o.setPos(x, y, z);
            o.vx = (rng.nextDouble() - 0.5) * 0.2;
            o.vy = 0.2;
            o.vz = (rng.nextDouble() - 0.5) * 0.2;
            addEntity(o);
        }
    }

    private void tickEntities() {
        if (!addQueue.isEmpty()) {
            for (Entity e : addQueue) if (!entities.contains(e)) entities.add(e);
            addQueue.clear();
        }
        for (int i = 0; i < entities.size(); i++) {
            Entity e = entities.get(i);
            if (e.removed) continue;
            int cx = (int) Math.floor(e.x) >> 4, cz = (int) Math.floor(e.z) >> 4;
            if (!(e instanceof Player)) {
                Chunk c = chunkAt(cx, cz);
                if (c == null || !nearPlayer(c, Registries.tunei("simulation_distance") + 1)) continue; // frozen
            }
            try {
                e.tick();
            } catch (RuntimeException ex) {
                System.err.println("[entity] " + e.type.id + " tick failed: " + ex);
                ex.printStackTrace();
                e.remove();
            }
        }
        entities.removeIf(e -> e.removed && !(e instanceof Player));
        // entity push-out (Appendix AB.4 step 6)
        for (Entity a : entities) {
            if (!(a instanceof LivingEntity) || a.removed || ((LivingEntity) a).dead) continue;
            for (Entity b : entitiesIn(a.bbox(), a)) {
                if (!(b instanceof LivingEntity) || ((LivingEntity) b).dead) continue;
                double dx = b.x - a.x, dz = b.z - a.z, d = Math.max(Math.abs(dx), Math.abs(dz));
                if (d < 0.01) { dx = rng.nextDouble() - 0.5; d = 0.5; }
                double f = 0.05 / Math.max(d, 0.1);
                if (!(a instanceof Player)) { a.vx -= dx * f; a.vz -= dz * f; }
            }
        }
    }

    // ------------------------------------------------------------------ explosions (Section 7.6)
    public void explode(Entity source, double x, double y, double z, float power, boolean fire) {
        playSound("entity.generic.explode", x, y, z, 4f, (1f + (rng.nextFloat() - rng.nextFloat()) * 0.2f) * 0.7f);
        listener.particles(this, "explosion", x, y, z, (int) (power * 6), 0);
        Set<Long> hit = new LinkedHashSet<>();
        boolean grief = !(source instanceof Creeper) || level.rule("mobGriefing");
        if (grief) {
            for (int i = 0; i < 16; i++)
                for (int j = 0; j < 16; j++)
                    for (int k = 0; k < 16; k++) {
                        if (i != 0 && i != 15 && j != 0 && j != 15 && k != 0 && k != 15) continue;
                        double dx = i / 15.0 * 2 - 1, dy = j / 15.0 * 2 - 1, dz = k / 15.0 * 2 - 1;
                        double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        dx /= l; dy /= l; dz /= l;
                        double str = power * (0.7 + rng.nextDouble() * 0.6);
                        double px = x, py = y, pz = z;
                        while (str > 0) {
                            int bx = (int) Math.floor(px), by = (int) Math.floor(py), bz = (int) Math.floor(pz);
                            if (by < 0 || by > 255) break;
                            int s = getBlock(bx, by, bz);
                            if (s != Blocks.AIR) {
                                double res = Blocks.block(s).blastResistance;
                                if (Blocks.fluid[s]) res = 100;
                                str -= (res + 0.3) * 0.3;
                                if (str > 0 && !Blocks.fluid[s] && !Blocks.block(s).unbreakable()) hit.add(packPos(bx, by, bz));
                            }
                            px += dx * 0.3; py += dy * 0.3; pz += dz * 0.3;
                            str -= 0.225;
                        }
                    }
        }
        // entity damage
        double r = power * 2;
        for (Entity e : entitiesIn(new AABB(x - r, y - r, z - r, x + r, y + r, z + r), source)) {
            double dist = Math.sqrt(e.distSq(x, y, z)) / r;
            if (dist > 1) continue;
            double dx = e.x - x, dy = e.eyeY() - y, dz = e.z - z, l = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (l < 1e-4) continue;
            double exposure = exposure(x, y, z, e);
            double impact = (1 - dist) * exposure;
            float dmg = (float) ((impact * impact + impact) / 2 * 7 * r + 1);
            if (e instanceof PrimedTnt) continue;
            e.hurt(DamageSource.explosion(source), dmg);
            e.vx += dx / l * impact;
            e.vy += dy / l * impact;
            e.vz += dz / l * impact;
        }
        for (long p : hit) {
            int bx = unpackX(p), by = unpackY(p), bz = unpackZ(p);
            int s = getBlock(bx, by, bz);
            if (s == Blocks.AIR) continue;
            if (s == Blocks.TNT) {
                setBlock(bx, by, bz, Blocks.AIR);
                PrimedTnt t = new PrimedTnt(this, null);
                t.setPos(bx + 0.5, by, bz + 0.5);
                t.fuse = 10 + rng.nextInt(20);
                addEntity(t);
                continue;
            }
            if (rng.nextFloat() < 1f / power && level.rule("doTileDrops"))
                for (ItemStack st : Mining.drops(s, null, rng)) if (!Blocks.block(s).requiresTool) dropItem(bx + 0.5, by + 0.5, bz + 0.5, st);
            setBlock(bx, by, bz, Blocks.AIR);
        }
        if (fire) {
            for (long p : hit)
                if (rng.nextInt(3) == 0) {
                    int bx = unpackX(p), by = unpackY(p), bz = unpackZ(p);
                    if (getBlock(bx, by, bz) == Blocks.AIR && Blocks.opaqueCube[getBlock(bx, by - 1, bz)]) setBlock(bx, by, bz, Blocks.FIRE);
                }
        }
    }

    private double exposure(double x, double y, double z, Entity e) {
        AABB b = e.bbox();
        int total = 0, clear = 0;
        for (double fx = 0; fx <= 1; fx += 0.5)
            for (double fy = 0; fy <= 1; fy += 0.5)
                for (double fz = 0; fz <= 1; fz += 0.5) {
                    double tx = b.minX + (b.maxX - b.minX) * fx, ty = b.minY + (b.maxY - b.minY) * fy, tz = b.minZ + (b.maxZ - b.minZ) * fz;
                    if (!Raycast.blocked(this, x, y, z, tx, ty, tz)) clear++;
                    total++;
                }
        return clear / (double) total;
    }

    // ------------------------------------------------------------------ sound / effects
    @Override
    public void playSound(String eventId, double x, double y, double z, float volume, float pitch) {
        listener.sound(this, eventId, x, y, z, volume, pitch);
    }

    public void particles(String kind, double x, double y, double z, int count, int data) {
        listener.particles(this, kind, x, y, z, count, data);
    }

    // ------------------------------------------------------------------ main tick
    public void tick(boolean advanceTime) {
        long t0 = System.nanoTime();
        pollLoaded(8);
        long t1 = System.nanoTime();
        if (advanceTime) Weather.tick(this);
        runScheduled();
        randomTicks();
        tickBlockEntities();
        long t2 = System.nanoTime();
        tickEntities();
        long t3 = System.nanoTime();
        if (level.rule("doMobSpawning")) spawner.tick();
        long t4 = System.nanoTime();
        stageNanos[0] = t1 - t0;
        stageNanos[1] = t2 - t1;
        stageNanos[2] = t3 - t2;
        stageNanos[3] = t4 - t3;
        flushChanges();
    }

    /** Send changed chunks to listeners (one packet per chunk per tick). */
    public void flushChanges() {
        if (lightDirty.isEmpty()) return;
        for (long k : lightDirty) {
            Chunk c = chunks.get(k);
            if (c != null) listener.chunkChanged(this, c);
        }
        lightDirty.clear();
    }

    /** Deterministic seed-derived per-position hash (used by generators and tests). */
    public long posHash(int x, int y, int z) { return Hash.pos(level.seed, x, y, z); }
}
