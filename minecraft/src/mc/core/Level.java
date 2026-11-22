package mc.core;

import mc.util.Json;

import java.util.*;

/** Level metadata (Section 2.6): seed, spawn, time, weather, gamerules, difficulty, data version. */
public final class Level {
    public static final String VERSION = "1.16.5";
    public static final int DATA_VERSION = 2586;

    public String name = "New World";
    public long seed;
    public long gameTime;     // total ticks
    public long dayTime;      // 0..∞ (mod 24000 = time of day)
    public boolean raining, thundering;
    public int rainTime = 24000, thunderTime = 48000, clearTime;
    public int spawnX, spawnY = 64, spawnZ;
    public int difficulty = 2; // 0 peaceful 1 easy 2 normal 3 hard
    public String defaultGameMode = "survival";
    public boolean spawnFound;
    public final Map<String, String> gamerules = new LinkedHashMap<>();

    public Level() {
        gamerules.put("doDaylightCycle", "true");
        gamerules.put("doWeatherCycle", "true");
        gamerules.put("keepInventory", "false");
        gamerules.put("doMobSpawning", "true");
        gamerules.put("doMobLoot", "true");
        gamerules.put("doTileDrops", "true");
        gamerules.put("mobGriefing", "true");
        gamerules.put("doFireTick", "true");
        gamerules.put("doInsomnia", "true");
        gamerules.put("doImmediateRespawn", "false");
        gamerules.put("showCoordinates", "true");
        gamerules.put("reducedDebugInfo", "false");
        gamerules.put("naturalRegeneration", "true");
        gamerules.put("fallDamage", "true");
        gamerules.put("drowningDamage", "true");
        gamerules.put("randomTickSpeed", "3");
        gamerules.put("playersSleepingPercentage", "100");
    }

    public boolean rule(String k) { return "true".equals(gamerules.get(k)); }

    public int ruleInt(String k, int def) {
        try { return Integer.parseInt(gamerules.getOrDefault(k, "" + def)); } catch (NumberFormatException e) { return def; }
    }

    public int timeOfDay() { return (int) (dayTime % 24000L); }

    public long day() { return dayTime / 24000L; }

    /** Celestial angle (Appendix AN.1): a = t/24000 - 0.25 wrapped to [0,1), smoothed like the reference curve. */
    public static double celestialAngle(long time) {
        double a = ((time % 24000L) / 24000.0) - 0.25;
        if (a < 0) a += 1;
        if (a >= 1) a -= 1;
        double b = 1.0 - (Math.cos(a * Math.PI) + 1.0) / 2.0;
        return a + (b - a) / 3.0;
    }

    /** Sky light subtraction 0..11 (darkness at night, rain, thunder). */
    public int skyDarken() {
        double a = celestialAngle(dayTime);
        double f = 1.0 - (Math.cos(a * Math.PI * 2) * 2.0 + 0.5);
        f = Math.max(0, Math.min(1, f));
        f = 1.0 - f;
        if (raining) f *= 1.0 - 5.0 / 16.0;
        if (thundering) f *= 1.0 - 5.0 / 16.0;
        return (int) ((1.0 - f) * 11.0);
    }

    public int moonPhase() { return (int) (day() % 8); }

    public Map<String, Object> save() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("version", VERSION);
        m.put("data_version", DATA_VERSION);
        m.put("seed", Long.toString(seed));
        m.put("game_time", gameTime);
        m.put("day_time", dayTime);
        m.put("raining", raining);
        m.put("thundering", thundering);
        m.put("rain_time", rainTime);
        m.put("thunder_time", thunderTime);
        m.put("clear_time", clearTime);
        m.put("spawn", Arrays.asList(spawnX, spawnY, spawnZ));
        m.put("spawn_found", spawnFound);
        m.put("difficulty", difficulty);
        m.put("game_mode", defaultGameMode);
        m.put("gamerules", new LinkedHashMap<>(gamerules));
        return m;
    }

    public static Level load(Map<String, Object> m) {
        Level l = new Level();
        l.name = Json.str(m, "name", "World");
        l.seed = Long.parseLong(Json.str(m, "seed", "0"));
        l.gameTime = (long) Json.num(m, "game_time", 0);
        l.dayTime = (long) Json.num(m, "day_time", 0);
        l.raining = Json.bool(m, "raining", false);
        l.thundering = Json.bool(m, "thundering", false);
        l.rainTime = Json.integer(m, "rain_time", 24000);
        l.thunderTime = Json.integer(m, "thunder_time", 48000);
        l.clearTime = Json.integer(m, "clear_time", 0);
        List<Object> sp = Json.arr(m.get("spawn"));
        if (sp.size() == 3) {
            l.spawnX = ((Number) sp.get(0)).intValue();
            l.spawnY = ((Number) sp.get(1)).intValue();
            l.spawnZ = ((Number) sp.get(2)).intValue();
        }
        l.spawnFound = Json.bool(m, "spawn_found", false);
        l.difficulty = Json.integer(m, "difficulty", 2);
        l.defaultGameMode = Json.str(m, "game_mode", "survival");
        for (Map.Entry<String, Object> e : Json.obj(m.get("gamerules")).entrySet()) l.gamerules.put(e.getKey(), String.valueOf(e.getValue()));
        return l;
    }
}
