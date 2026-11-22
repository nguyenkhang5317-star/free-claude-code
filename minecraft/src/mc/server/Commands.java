package mc.server;

import mc.content.*;
import mc.core.*;
import mc.entity.*;

import java.util.Locale;

/** Chat and debug commands (Appendix AN requires /time, /weather and gamerules from Tier 1). */
public final class Commands {
    private Commands() { }

    public static void chat(IntegratedServer s, String text) {
        text = text.trim();
        if (text.isEmpty()) return;
        if (!text.startsWith("/")) { s.message("<" + s.player.name + "> " + text); return; }
        String[] a = text.substring(1).split("\\s+");
        try {
            String r = run(s, a);
            if (r != null) s.message(r);
        } catch (RuntimeException e) {
            s.message("Error: " + e.getMessage());
        }
    }

    private static String run(IntegratedServer s, String[] a) {
        Player p = s.player;
        Level l = s.level;
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "help":
                return "Commands: /time set|add, /weather clear|rain|thunder, /gamerule, /gamemode, /difficulty, /give, /tp, /kill, /seed, /summon, /spawnpoint, /setworldspawn, /effect, /xp, /clear";
            case "time": {
                if (a.length < 3) return "Usage: /time set <day|noon|night|midnight|ticks> or /time add <ticks>";
                long v;
                switch (a[2]) {
                    case "day": v = 1000; break;
                    case "noon": v = 6000; break;
                    case "night": v = 13000; break;
                    case "midnight": v = 18000; break;
                    default: v = Long.parseLong(a[2]);
                }
                if (a[1].equals("add")) l.dayTime += v;
                else l.dayTime = (l.dayTime / 24000) * 24000 + v;
                return "Set the time to " + l.timeOfDay();
            }
            case "weather": {
                if (a.length < 2) return "Usage: /weather clear|rain|thunder [seconds]";
                int dur = a.length > 2 ? Integer.parseInt(a[2]) * 20 : 6000;
                switch (a[1]) {
                    case "clear": l.raining = false; l.thundering = false; l.clearTime = dur; break;
                    case "rain": l.raining = true; l.thundering = false; l.rainTime = dur; l.clearTime = 0; break;
                    case "thunder": l.raining = true; l.thundering = true; l.rainTime = dur; l.clearTime = 0; break;
                    default: return "Unknown weather " + a[1];
                }
                return "Changed the weather to " + a[1];
            }
            case "gamerule": {
                if (a.length == 1) return "Gamerules: " + l.gamerules;
                if (!l.gamerules.containsKey(a[1])) return "Unknown gamerule " + a[1];
                if (a.length == 2) return a[1] + " = " + l.gamerules.get(a[1]);
                l.gamerules.put(a[1], a[2]);
                return "Gamerule " + a[1] + " is now set to: " + a[2];
            }
            case "gamemode": {
                if (a.length < 2) return "Usage: /gamemode <survival|creative|adventure|spectator>";
                int m;
                switch (a[1]) {
                    case "survival": case "s": case "0": m = 0; break;
                    case "creative": case "c": case "1": m = 1; break;
                    case "adventure": case "a": case "2": m = 2; break;
                    case "spectator": case "sp": case "3": m = 3; break;
                    default: return "Unknown game mode " + a[1];
                }
                p.setGameMode(m);
                return "Set own game mode to " + new String[]{"Survival", "Creative", "Adventure", "Spectator"}[m] + " Mode";
            }
            case "difficulty": {
                if (a.length < 2) return "The difficulty is " + l.difficulty;
                String[] names = {"peaceful", "easy", "normal", "hard"};
                for (int i = 0; i < 4; i++) if (names[i].equals(a[1]) || a[1].equals(String.valueOf(i))) { l.difficulty = i; return "The difficulty has been set to " + names[i]; }
                return "Unknown difficulty";
            }
            case "give": {
                if (a.length < 2) return "Usage: /give <item> [count]";
                String id = a[1].contains(":") ? a[1] : "minecraft:" + a[1];
                Item it = Items.get(id);
                if (it == null) return "Unknown item " + id;
                int n = a.length > 2 ? Integer.parseInt(a[2]) : 1;
                int left = n;
                while (left > 0) {
                    int c = Math.min(left, it.maxStack);
                    p.giveOrDrop(new ItemStack(it, c));
                    left -= c;
                }
                return "Gave " + n + " [" + it.name + "] to " + p.name;
            }
            case "tp": case "teleport": {
                if (a.length < 4) return "Usage: /tp x y z";
                double x = coord(a[1], p.x), y = coord(a[2], p.y), z = coord(a[3], p.z);
                p.setPos(x, y, z);
                p.vx = p.vy = p.vz = 0;
                p.fallDistance = 0;
                return String.format(Locale.ROOT, "Teleported %s to %.2f, %.2f, %.2f", p.name, x, y, z);
            }
            case "kill":
                p.hurt(DamageSource.KILL, Float.MAX_VALUE);
                if (!p.dead) p.die(DamageSource.KILL);
                return null;
            case "seed": return "Seed: [" + l.seed + "]";
            case "summon": {
                if (a.length < 2) return "Usage: /summon <entity>";
                EntityType t = EntityTypes.get(a[1].contains(":") ? a[1] : "minecraft:" + a[1]);
                if (t == null) return "Unknown entity " + a[1];
                Entity e = EntityFactory.create(p.world, t);
                if (e == null) return "Cannot summon " + t.id;
                LivingEntity.Vec v = p.look();
                e.setPos(p.x + v.x * 2, p.y, p.z + v.z * 2);
                p.world.addEntity(e);
                return "Summoned new " + t.name;
            }
            case "spawnpoint":
                p.setSpawn((int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z));
                return null;
            case "setworldspawn":
                l.spawnX = (int) Math.floor(p.x); l.spawnY = (int) Math.floor(p.y); l.spawnZ = (int) Math.floor(p.z);
                return "Set the world spawn point to " + l.spawnX + ", " + l.spawnY + ", " + l.spawnZ;
            case "effect": {
                if (a.length < 2) return "Usage: /effect <id> [seconds] [amplifier] | /effect clear";
                if (a[1].equals("clear")) { p.effects.clear(); return "Removed every effect"; }
                int sec = a.length > 2 ? Integer.parseInt(a[2]) : 30, amp = a.length > 3 ? Integer.parseInt(a[3]) : 0;
                p.addEffect(a[1].replace("minecraft:", ""), sec * 20, amp);
                return "Applied effect " + a[1];
            }
            case "xp": case "experience": {
                if (a.length < 2) return "Usage: /xp <amount>[L]";
                String v = a[1];
                if (v.endsWith("L") || v.endsWith("l")) p.xpLevel += Integer.parseInt(v.substring(0, v.length() - 1));
                else p.giveXp(Integer.parseInt(v));
                return "Gave experience to " + p.name;
            }
            case "clear":
                p.inventory.clear();
                return "Cleared the inventory of " + p.name;
            case "save-all":
                try { s.save(true); } catch (java.io.IOException e) { return "Save failed: " + e.getMessage(); }
                return "Saved the game";
            default:
                return "Unknown command. Type /help for help.";
        }
    }

    private static double coord(String s, double base) {
        if (s.startsWith("~")) return base + (s.length() > 1 ? Double.parseDouble(s.substring(1)) : 0);
        return Double.parseDouble(s);
    }
}
