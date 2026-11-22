package mc.core;

import mc.content.Registries;
import mc.entity.Player;

/** Day cycle and weather state machine (Appendix AN). */
public final class Weather {
    private Weather() { }

    public static void tick(World w) {
        Level l = w.level;
        if (w.dim != Dimension.OVERWORLD) return;
        l.gameTime++;
        if (l.rule("doDaylightCycle")) l.dayTime++;
        if (!l.rule("doWeatherCycle")) return;
        if (l.clearTime > 0) {
            l.clearTime--;
            l.raining = false;
            l.thundering = false;
            return;
        }
        if (--l.rainTime <= 0) {
            if (l.raining) {
                l.raining = false;
                l.rainTime = rand(w, "clear_min_ticks", "clear_max_ticks");
            } else {
                l.raining = true;
                l.rainTime = rand(w, "rain_min_ticks", "rain_max_ticks");
                l.thundering = w.rng.nextDouble() < Registries.tune("thunder_chance");
            }
        }
        if (!l.raining) l.thundering = false;
        if (l.thundering && w.rng.nextInt(100000 / 20) == 0 && !w.players.isEmpty()) {
            Player p = w.players.get(w.rng.nextInt(w.players.size()));
            w.playSound("entity.lightning_bolt.thunder", p.x + w.rng.nextInt(64) - 32, p.y + 20, p.z + w.rng.nextInt(64) - 32, 10000f,
                    0.8f + w.rng.nextFloat() * 0.2f);
        }
    }

    private static int rand(World w, String a, String b) {
        int lo = Registries.tunei(a), hi = Registries.tunei(b);
        return lo + w.rng.nextInt(Math.max(1, hi - lo));
    }

    /** Can players sleep now (Appendix AN sleep window or thunderstorm)? */
    public static boolean canSleep(Level l) {
        int t = l.timeOfDay();
        return l.thundering || (t >= Registries.tunei("sleep_start") && t <= Registries.tunei("sleep_end"));
    }
}
