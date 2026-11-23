package mc.entity;

import mc.content.*;
import mc.core.World;

import java.util.Map;

/** Creates entities by type id (spawning, spawn eggs, loading from disk). */
public final class EntityFactory {
    private EntityFactory() { }

    public static Entity create(World w, EntityType t) {
        switch (t.id) {
            case "minecraft:zombie": return new Zombie(w, t);
            case "minecraft:skeleton": return new Skeleton(w, t);
            case "minecraft:creeper": return new Creeper(w, t);
            case "minecraft:spider": return new Spider(w, t);
            case "minecraft:cow": case "minecraft:pig": case "minecraft:sheep": case "minecraft:chicken": return new Animal(w, t);
            case "minecraft:item": return new ItemEntity(w, null);
            case "minecraft:experience_orb": return new XpOrb(w, 1);
            case "minecraft:arrow": return new Arrow(w, null);
            case "minecraft:tnt": return new PrimedTnt(w, null);
            case "minecraft:falling_block": return new FallingBlock(w, Blocks.SAND);
            default:
                if ("monster".equals(t.category)) return new Monster(w, t);
                if ("creature".equals(t.category)) return new Animal(w, t);
                return null;
        }
    }

    public static Entity load(World w, Map<String, Object> m) {
        EntityType t = EntityTypes.get(String.valueOf(m.get("type")));
        if (t == null || t == EntityTypes.PLAYER) return null;
        Entity e = create(w, t);
        if (e == null) return null;
        try {
            e.load(m);
        } catch (RuntimeException ex) {
            System.err.println("[save] bad entity record " + t.id + ": " + ex);
            return null;
        }
        return e;
    }
}
