package mc.entity;

import mc.content.*;
import mc.core.Chunk;
import mc.core.World;
import mc.util.Json;

import java.util.*;

/**
 * Natural mob spawning (Section 8.4 / 16.4): per-category caps scaled by loaded chunk count (cap x chunks / 289),
 * pack spawning around a random column position, no spawns within 24 blocks of a player, monsters need light <= 7
 * (light_max in entities.json), animals need grass and light >= 9 and spawn mostly at chunk generation time.
 */
public final class Spawner {
    private final World w;
    private final Random rng;

    public Spawner(World w) {
        this.w = w;
        this.rng = new Random(w.level.seed * 31 + 7);
    }

    public int count(String category) {
        int n = 0;
        for (Entity e : w.entities) if (!e.removed && category.equals(e.type.category)) n++;
        return n;
    }

    public void tick() {
        if (w.players.isEmpty()) return;
        boolean monsters = w.level.difficulty > 0;
        boolean animals = w.level.gameTime % 400 == 0;
        List<Chunk> eligible = new ArrayList<>();
        for (Chunk c : w.loadedChunks()) if (w.nearPlayer(c, 8)) eligible.add(c);
        if (eligible.isEmpty()) return;
        double scale = eligible.size() / 289.0;
        if (monsters && count("monster") < Registries.cap("monster") * scale) spawnRound("monster", eligible);
        if (animals && count("creature") < Registries.cap("creature") * scale) spawnRound("creature", eligible);
    }

    private void spawnRound(String cat, List<Chunk> chunks) {
        for (Chunk c : chunks) {
            if (rng.nextInt(cat.equals("monster") ? 1 : 1) != 0) continue;
            int x = (c.cx << 4) + rng.nextInt(16), z = (c.cz << 4) + rng.nextInt(16);
            int top = c.height(x & 15, z & 15) + 1;
            int y = rng.nextInt(Math.max(1, top + 1));
            spawnPack(cat, x, y, z);
        }
    }

    private void spawnPack(String cat, int x, int y, int z) { spawnPack(cat, x, y, z, false); }

    private void spawnPack(String cat, int x, int y, int z, boolean worldgen) {
        if (Blocks.solidCollision[w.getBlock(x, y, z)]) return;
        Biome bio = w.biomeAt(x, z);
        List<Object[]> list = bio.spawns.get(cat);
        if (list == null || list.isEmpty()) return;
        int total = 0;
        for (Object[] o : list) total += ((Number) o[1]).intValue();
        if (total <= 0) return;
        int roll = rng.nextInt(total);
        EntityType type = null;
        for (Object[] o : list) {
            roll -= ((Number) o[1]).intValue();
            if (roll < 0) { type = (EntityType) o[0]; break; }
        }
        if (type == null) return;
        List<Object> grp = Json.arr(type.spawn.get("group"));
        int gmin = grp.size() > 0 ? ((Number) grp.get(0)).intValue() : 1, gmax = grp.size() > 1 ? ((Number) grp.get(1)).intValue() : 4;
        int n = gmin + rng.nextInt(Math.max(1, gmax - gmin + 1));
        int px = x, pz = z;
        for (int i = 0; i < n; i++) {
            px += rng.nextInt(6) - rng.nextInt(6);
            pz += rng.nextInt(6) - rng.nextInt(6);
            if (!canSpawnAt(type, px, y, pz, worldgen)) continue;
            Entity e = EntityFactory.create(w, type);
            if (e == null) return;
            e.setPos(px + 0.5, y, pz + 0.5);
            e.yaw = rng.nextFloat() * 360;
            if (Physics.collides(w, e.bbox())) continue;
            w.addEntity(e);
        }
    }

    public boolean canSpawnAt(EntityType t, int x, int y, int z, boolean ignorePlayers) {
        if (!w.isLoadedBlock(x, z) || y < 1 || y > 254) return false;
        double ex = Registries.tune("spawn_exclusion_radius");
        if (!ignorePlayers) for (Player p : w.players) if (p.distSq(x + 0.5, y, z + 0.5) < ex * ex) return false;
        int h = (int) Math.ceil(t.height);
        if (!Pathfinder.standable(w, x, y, z, h)) return false;
        int below = w.getBlock(x, y - 1, z);
        if (Blocks.fluid[below] || Blocks.fluid[w.getBlock(x, y, z)]) return false;
        if (below == Blocks.BEDROCK) return false;
        Map<String, Object> sp = t.spawn;
        if (sp.containsKey("light_max")) {
            int lm = Json.integer(sp, "light_max", 7);
            if (w.getBlockLight(x, y, z) > lm) return false;
            int sky = w.getSkyLight(x, y, z) - w.level.skyDarken();
            if (sky > lm) return false;
            if (w.level.raining && w.getSkyLight(x, y, z) > 0 && sky <= lm + 3) { /* thunder darkness ok */ }
        }
        if (sp.containsKey("light_min") && w.getRawBrightness(x, y, z) < Json.integer(sp, "light_min", 9)) return false;
        List<Object> ground = Json.arr(sp.get("ground"));
        if (!ground.isEmpty() && !ground.contains(Blocks.block(below).id)) return false;
        return true;
    }

    /** Initial animal population for freshly generated chunks (Section 8.4 chunk-generation spawning). */
    public void populate(Chunk c) {
        if (rng.nextFloat() >= 0.1f) return;
        int x = (c.cx << 4) + rng.nextInt(16), z = (c.cz << 4) + rng.nextInt(16);
        int y = c.height(x & 15, z & 15);
        spawnPack("creature", x, y, z, true);
    }
}
