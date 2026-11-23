package mc.client;

import mc.content.Biome;
import mc.content.Blocks;
import mc.net.S2C;

import java.util.*;

/**
 * Client-side ambient audio, ported from vanilla 1.16.5's client sound handlers (all timings in client ticks):
 * <ul>
 *   <li>MusicTicker + Minecraft#getBackgroundMusicSelector (menu / game / creative / under water / nether biome / end).</li>
 *   <li>BiomeSoundHandler: biome ambient loops with 40-tick fades, additions (chance 0.0111 per tick) and the
 *       "mood" darkness counter that triggers {@code ambient.cave} (or the nether biome mood).</li>
 *   <li>UnderwaterAmbientSoundHandler: enter / exit, the underwater loop and its rare additions.</li>
 *   <li>WorldRenderer#tickRain: {@code weather.rain} / {@code weather.rain.above} near the camera every few ticks.</li>
 * </ul>
 * Music, loops and additions only exist in resource packs / the player's Minecraft assets; with built-in sounds the
 * mood sound and a synthetic rain bed are used instead.
 */
final class Ambience {
    final GameClient c;
    final Random rnd = new Random();

    Ambience(GameClient c) { this.c = c; }

    // ------------------------------------------------------------------ music (MusicTicker)
    record Selector(String event, int minDelay, int maxDelay, boolean replace) { }

    static final Selector MENU = new Selector("music.menu", 20, 600, true);
    static final Selector GAME = new Selector("music.game", 12000, 24000, false);
    static final Selector CREATIVE = new Selector("music.creative", 12000, 24000, false);
    static final Selector UNDER_WATER = new Selector("music.under_water", 12000, 24000, false);
    static final Selector END = new Selector("music.end", 6000, 24000, true);

    String currentMusic;
    int timeUntilNextMusic = 100;

    Selector selector() {
        S2C.PlayerState ps = c.ps;
        ClientWorld w = c.world;
        if (c.server == null || ps == null || w == null) return MENU;
        if (w.dimension.equals("minecraft:the_end")) return END;
        Biome b = w.biomeAt((int) Math.floor(ps.x()), (int) Math.floor(ps.z()));
        String bid = b == null ? "" : b.id;
        boolean waterBiome = bid.contains("ocean") || bid.contains("river");
        if (UNDER_WATER.event.equals(currentMusic) || (ps.submerged() && waterBiome)) return UNDER_WATER;
        if (w.dimension.equals("minecraft:the_nether")) {
            String s = "music.nether." + bid.substring(bid.indexOf(':') + 1);
            return new Selector(c.audio.packHas(s) ? s : "music.nether.nether_wastes", 12000, 24000, false);
        }
        if (ps.gameMode() == 1) return CREATIVE;
        return GAME;
    }

    void tickMusic() {
        Audio.IAudioBus a = c.audio;
        if (!a.usingPack()) { currentMusic = null; return; }
        Selector sel = selector();
        if (currentMusic != null) {
            if (!sel.event.equals(currentMusic) && sel.replace) {
                a.stopMusic();
                currentMusic = null;
                timeUntilNextMusic = rnd.nextInt(sel.minDelay / 2 + 1);
            }
            if (currentMusic != null && !a.musicPlaying()) {
                currentMusic = null;
                timeUntilNextMusic = Math.min(timeUntilNextMusic, sel.minDelay + rnd.nextInt(sel.maxDelay - sel.minDelay + 1));
            }
        }
        timeUntilNextMusic = Math.min(timeUntilNextMusic, sel.maxDelay);
        if (currentMusic == null && timeUntilNextMusic-- <= 0) {
            if (a.playMusic(sel.event)) {
                currentMusic = sel.event;
                timeUntilNextMusic = Integer.MAX_VALUE;
            } else {
                timeUntilNextMusic = sel.minDelay + rnd.nextInt(sel.maxDelay - sel.minDelay + 1);
            }
        }
    }

    // ------------------------------------------------------------------ biome loops / mood / additions
    static final class FadingLoop {
        final Audio.Loop loop;
        int fade, dir = 1;

        FadingLoop(Audio.Loop l) { loop = l; }

        boolean tick() {
            fade = Math.max(0, Math.min(40, fade + dir));
            loop.setVolume(fade / 40f);
            if (fade == 0 && dir < 0) { loop.stop(); return false; }
            return loop.alive();
        }
    }

    final Map<String, FadingLoop> biomeLoops = new HashMap<>();
    String currentBiome;
    float darkness;
    FadingLoop underwaterLoop;
    boolean wasUnderwater;
    int rainSoundTime;
    float reverbEnv;

    void reset() {
        for (FadingLoop f : biomeLoops.values()) f.loop.stop();
        biomeLoops.clear();
        if (underwaterLoop != null) underwaterLoop.loop.stop();
        underwaterLoop = null;
        currentBiome = null;
        wasUnderwater = false;
        darkness = 0;
    }

    static String biomeKey(Biome b) {
        return b == null ? "plains" : b.id.substring(b.id.indexOf(':') + 1);
    }

    static boolean netherBiome(String k) {
        return k.equals("nether_wastes") || k.equals("crimson_forest") || k.equals("warped_forest") || k.equals("soul_sand_valley")
                || k.equals("basalt_deltas");
    }

    void tick(boolean paused) {
        S2C.PlayerState ps = c.ps;
        ClientWorld w = c.world;
        Audio.IAudioBus a = c.audio;
        if (ps == null || w == null) return;
        int px = (int) Math.floor(ps.x()), pz = (int) Math.floor(ps.z());
        double eyeY = ps.y() + ps.eyeHeight();
        int ey = (int) Math.floor(eyeY);

        // built-in sounds only: estimate enclosure for the reverb send
        int sky = w.getSkyLight(px, ey, pz);
        float target = w.hasSky ? (15 - sky) / 15f : 0.8f;
        if (ey > 62 && w.hasSky) target *= 0.5f;
        reverbEnv += (target - reverbEnv) * 0.05f;
        a.setEnvironment(reverbEnv, ps.submerged() ? 1 : 0);
        if (paused) return;

        boolean pack = a.usingPack();
        Biome biome = w.biomeAt(px, pz);
        String bk = biomeKey(biome);
        boolean nether = w.dimension.equals("minecraft:the_nether");

        // --- biome ambient loop (nether biomes in 1.16.5)
        if (!bk.equals(currentBiome)) {
            currentBiome = bk;
            for (FadingLoop f : biomeLoops.values()) f.dir = -1;
            String loopId = "ambient." + bk + ".loop";
            if (pack && netherBiome(bk) && a.packHas(loopId)) {
                FadingLoop f = biomeLoops.get(bk);
                if (f == null) {
                    Audio.Loop l = a.loop(loopId, 0);
                    if (l != null) biomeLoops.put(bk, f = new FadingLoop(l));
                }
                if (f != null) f.dir = 1;
            }
        }
        biomeLoops.values().removeIf(f -> !f.tick());

        // --- additions
        if (pack && netherBiome(bk) && rnd.nextDouble() < 0.0111) a.playUi("ambient." + bk + ".additions", 1f, 1f);

        // --- mood (darkness counter): ambient.cave in the overworld, the biome mood in the nether
        String moodId = nether && netherBiome(bk) ? "ambient." + bk + ".mood" : "ambient.cave";
        if (!w.dimension.equals("minecraft:the_end") && !ps.dead()) {
            int ext = 8, size = ext * 2 + 1;
            int bx = px + rnd.nextInt(size) - ext, by = ey + rnd.nextInt(size) - ext, bz = pz + rnd.nextInt(size) - ext;
            if (w.isLoaded(bx, bz) && by >= 0 && by < 256) {
                int s = w.hasSky ? w.getSkyLight(bx, by, bz) : 0;
                if (s > 0) darkness -= s / 15f * 0.001f;
                else darkness -= (w.getBlockLight(bx, by, bz) - 1) / 6000f;
                if (darkness >= 1f) {
                    double dx = bx + 0.5 - ps.x(), dy = by + 0.5 - eyeY, dz = bz + 0.5 - ps.z();
                    double d = Math.max(1e-3, Math.sqrt(dx * dx + dy * dy + dz * dz)), k = (d + 2.0) / d;
                    a.play(moodId, ps.x() + dx * k, eyeY + dy * k, ps.z() + dz * k, 1f, 1f);
                    darkness = 0;
                } else darkness = Math.max(darkness, 0);
            }
        }

        // --- underwater enter / exit / loop / additions
        boolean under = ps.submerged();
        if (under != wasUnderwater) {
            wasUnderwater = under;
            if (pack) {
                a.playUi(under ? "ambient.underwater.enter" : "ambient.underwater.exit", 1f, 1f);
                if (under && underwaterLoop == null) {
                    Audio.Loop l = a.loop("ambient.underwater.loop", 0);
                    if (l != null) underwaterLoop = new FadingLoop(l);
                }
            }
        }
        if (underwaterLoop != null) {
            underwaterLoop.dir = under ? 1 : -2;
            if (!underwaterLoop.tick()) underwaterLoop = null;
        }
        if (pack && under) {
            float f = rnd.nextFloat();
            if (f < 1.0E-4f) a.playUi("ambient.underwater.loop.additions.ultra_rare", 1f, 1f);
            else if (f < 0.001f) a.playUi("ambient.underwater.loop.additions.rare", 1f, 1f);
            else if (f < 0.01f) a.playUi("ambient.underwater.loop.additions", 1f, 1f);
        }

        // --- random display ticks (vanilla ClientWorld#animateTick): fire, lit furnaces, portals, lava, flowing water
        animateTick(w, px, ey, pz, pack);

        // --- rain sounds (pack mode; the built-in mixer renders a rain bed instead)
        float rain = c.rainSmooth;
        if (pack && rain > 0 && w.hasSky && w.dimension.equals("minecraft:overworld")) tickRain(w, rain);
    }

    void animateTick(ClientWorld w, int px, int py, int pz, boolean pack) {
        for (int j = 0; j < 667; j++) {
            displayTick(w, px, py, pz, 16, pack);
            displayTick(w, px, py, pz, 32, pack);
        }
    }

    void displayTick(ClientWorld w, int px, int py, int pz, int r, boolean pack) {
        int x = px + rnd.nextInt(r) - rnd.nextInt(r), y = py + rnd.nextInt(r) - rnd.nextInt(r), z = pz + rnd.nextInt(r) - rnd.nextInt(r);
        if (y < 0 || y > 255) return;
        int st = w.getBlock(x, y, z);
        if (st == Blocks.AIR) return;
        int base = Blocks.block(st).baseState;
        Audio.IAudioBus a = c.audio;
        if (base == Blocks.FIRE) {
            if (rnd.nextInt(24) == 0) a.play("block.fire.ambient", x + 0.5, y + 0.5, z + 0.5, 1f + rnd.nextFloat(), rnd.nextFloat() * 0.7f + 0.3f);
        } else if (base == Blocks.FURNACE) {
            if ("true".equals(Blocks.value(st, "lit")) && rnd.nextDouble() < 0.1)
                a.play("block.furnace.fire_crackle", x + 0.5, y, z + 0.5, 1f, 1f);
        } else if (base == Blocks.NETHER_PORTAL) {
            if (rnd.nextInt(100) == 0) a.play("block.portal.ambient", x + 0.5, y + 0.5, z + 0.5, 0.5f, rnd.nextFloat() * 0.4f + 0.8f);
        } else if (base == Blocks.LAVA) {
            if (Blocks.prop(st, "level") == 0 && w.getBlock(x, y + 1, z) == Blocks.AIR && rnd.nextInt(100) == 0)
                a.play("block.lava.pop", x + rnd.nextDouble(), y + 1, z + rnd.nextDouble(), 0.2f + rnd.nextFloat() * 0.2f, 0.9f + rnd.nextFloat() * 0.15f);
            if (rnd.nextInt(200) == 0)
                a.play("block.lava.ambient", x + 0.5, y + 0.5, z + 0.5, 0.2f + rnd.nextFloat() * 0.2f, 0.9f + rnd.nextFloat() * 0.15f);
        } else if (base == Blocks.WATER && pack) {
            if (Blocks.prop(st, "level") != 0 && rnd.nextInt(64) == 0)
                a.play("block.water.ambient", x + 0.5, y + 0.5, z + 0.5, rnd.nextFloat() * 0.25f + 0.75f, rnd.nextFloat() + 0.5f);
        }
    }

    void tickRain(ClientWorld w, float strength) {
        int cx = (int) Math.floor(c.camX), cy = (int) Math.floor(c.camY), cz = (int) Math.floor(c.camZ);
        Random r = new Random((long) c.clientTicks * 312987231L);
        int n = (int) (100f * strength * strength);
        int[] hit = null;
        for (int k = 0; k < n; k++) {
            int x = cx + r.nextInt(21) - 10, z = cz + r.nextInt(21) - 10;
            int top = motionTop(w, x, z, cy);
            if (top == Integer.MIN_VALUE) continue;
            Biome b = w.biomeAt(x, z);
            if (top > 0 && top <= cy + 10 && top >= cy - 10 && b != null && "rain".equals(b.precipitation) && b.temperature >= 0.15)
                hit = new int[]{x, top, z};
        }
        if (hit != null && r.nextInt(3) < rainSoundTime++) {
            rainSoundTime = 0;
            int camTop = motionTop(w, cx, cz, cy);
            if (hit[1] > cy + 1 && (camTop == Integer.MIN_VALUE || camTop + 1 > cy))
                c.audio.play("weather.rain.above", hit[0] + 0.5, hit[1] + 0.5, hit[2] + 0.5, 0.1f, 0.5f);
            else c.audio.play("weather.rain", hit[0] + 0.5, hit[1] + 0.5, hit[2] + 0.5, 0.2f, 1.0f);
        }
    }

    /**
     * Y of the highest motion-blocking block in a column (vanilla heightmap MOTION_BLOCKING minus one), searched only in
     * the band that matters; MIN_VALUE if the column is covered far above (sky light below 15) or not loaded.
     */
    static int motionTop(ClientWorld w, int x, int z, int cy) {
        if (!w.isLoaded(x, z)) return Integer.MIN_VALUE;
        int startY = Math.min(255, cy + 11);
        if (startY < 255 && w.getSkyLight(x, startY, z) < 15) return cy + 11; // something higher up blocks the sky
        for (int y = startY; y >= Math.max(0, cy - 11); y--) {
            int b = w.getBlock(x, y, z);
            if (b != Blocks.AIR && blocksMotion(b)) return y;
        }
        return Integer.MIN_VALUE;
    }

    static boolean blocksMotion(int state) {
        mc.content.Block b = Blocks.block(state);
        return b.collision || Blocks.isWater(state) || Blocks.isLava(state);
    }
}
