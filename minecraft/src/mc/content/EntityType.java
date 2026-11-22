package mc.content;

import java.util.*;

/** Entity type definition (Sections 8-10 contract): dimensions, health, attributes, goals, drops, sounds, model. */
public final class EntityType {
    public static final class Part {
        public final String name;
        public final double ox, oy, oz, sx, sy, sz, px, py, pz;
        public final int color, color2;
        public final String pattern;
        public final Map<String, Object> face;

        Part(String name, double[] o, double[] s, double[] p, int color, int color2, String pattern, Map<String, Object> face) {
            this.name = name;
            ox = o[0]; oy = o[1]; oz = o[2]; sx = s[0]; sy = s[1]; sz = s[2]; px = p[0]; py = p[1]; pz = p[2];
            this.color = color; this.color2 = color2; this.pattern = pattern; this.face = face;
        }
    }

    public static final class Anim {
        public final String part;
        public final int axis; // 0=x 1=y 2=z
        public final double amp, phase, offset;

        Anim(String part, int axis, double amp, double phase, double offset) {
            this.part = part; this.axis = axis; this.amp = amp; this.phase = phase; this.offset = offset;
        }
    }

    public final String id, name;
    public final int index;
    public final double health, speed, width, height, eye;
    public final double[] damage;
    public final String category;
    public final int xpMin, xpMax;
    public final List<Map<String, Object>> drops;
    public final List<Object[]> goals; // [priority(Integer), name(String)]
    public final Map<String, String> sounds;
    public final List<Part> model;
    public final List<Anim> walk;
    public final Map<String, String> attachments;
    public final Map<String, Object> spawn;
    public final Map<String, Object> raw;

    EntityType(int index, String id, String name, double health, double speed, double width, double height, double eye,
               double[] damage, String category, int xpMin, int xpMax, List<Map<String, Object>> drops, List<Object[]> goals,
               Map<String, String> sounds, List<Part> model, List<Anim> walk, Map<String, String> attachments,
               Map<String, Object> spawn, Map<String, Object> raw) {
        this.index = index; this.id = id; this.name = name; this.health = health; this.speed = speed; this.width = width;
        this.height = height; this.eye = eye; this.damage = damage; this.category = category; this.xpMin = xpMin; this.xpMax = xpMax;
        this.drops = drops; this.goals = goals; this.sounds = sounds; this.model = model; this.walk = walk;
        this.attachments = attachments; this.spawn = spawn; this.raw = raw;
    }

    public String sound(String key) { return sounds.get(key); }

    public boolean flag(String key) { return Boolean.TRUE.equals(raw.get(key)); }

    public double num(String key, double def) { Object o = raw.get(key); return o instanceof Number ? ((Number) o).doubleValue() : def; }

    public String str(String key) { Object o = raw.get(key); return o == null ? null : o.toString(); }

    public boolean hasGoal(String name) {
        for (Object[] g : goals) if (((String) g[1]).equals(name) || ((String) g[1]).startsWith(name + ":")) return true;
        return false;
    }

    public String goalArg(String name) {
        for (Object[] g : goals) {
            String s = (String) g[1];
            if (s.startsWith(name + ":")) return s.substring(name.length() + 1);
        }
        return null;
    }

    public Part part(String name) {
        for (Part p : model) if (p.name.equals(name)) return p;
        return null;
    }

    @Override public String toString() { return id; }
}
