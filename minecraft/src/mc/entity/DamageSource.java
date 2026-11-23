package mc.entity;

/** Damage source descriptor. */
public final class DamageSource {
    public final String type;
    public final Entity attacker;
    public final boolean bypassArmor, fire, explosion, projectile;

    public DamageSource(String type, Entity attacker, boolean bypassArmor, boolean fire, boolean explosion, boolean projectile) {
        this.type = type; this.attacker = attacker; this.bypassArmor = bypassArmor; this.fire = fire;
        this.explosion = explosion; this.projectile = projectile;
    }

    public static final DamageSource FALL = new DamageSource("fall", null, true, false, false, false);
    public static final DamageSource VOID = new DamageSource("outOfWorld", null, true, false, false, false);
    public static final DamageSource DROWN = new DamageSource("drown", null, true, false, false, false);
    public static final DamageSource STARVE = new DamageSource("starve", null, true, false, false, false);
    public static final DamageSource LAVA = new DamageSource("lava", null, false, true, false, false);
    public static final DamageSource IN_FIRE = new DamageSource("inFire", null, false, true, false, false);
    public static final DamageSource ON_FIRE = new DamageSource("onFire", null, true, true, false, false);
    public static final DamageSource CACTUS = new DamageSource("cactus", null, false, false, false, false);
    public static final DamageSource SUFFOCATE = new DamageSource("inWall", null, true, false, false, false);
    public static final DamageSource GENERIC = new DamageSource("generic", null, true, false, false, false);
    public static final DamageSource MAGIC = new DamageSource("magic", null, true, false, false, false);
    public static final DamageSource KILL = new DamageSource("kill", null, true, false, false, false);

    public static DamageSource mob(Entity e) { return new DamageSource("mob", e, false, false, false, false); }

    public static DamageSource player(Entity e) { return new DamageSource("player", e, false, false, false, false); }

    public static DamageSource arrow(Entity arrow, Entity shooter) { return new DamageSource("arrow", shooter, false, false, false, true); }

    public static DamageSource explosion(Entity src) { return new DamageSource("explosion", src, false, false, true, false); }

    public String deathMessage(String name) {
        switch (type) {
            case "fall": return name + " hit the ground too hard";
            case "outOfWorld": return name + " fell out of the world";
            case "drown": return name + " drowned";
            case "starve": return name + " starved to death";
            case "lava": return name + " tried to swim in lava";
            case "inFire": case "onFire": return name + " burned to death";
            case "cactus": return name + " was pricked to death";
            case "inWall": return name + " suffocated in a wall";
            case "explosion": return name + " blew up";
            case "arrow": return name + " was shot by " + (attacker == null ? "an arrow" : attacker.type.name);
            case "mob": return name + " was slain by " + (attacker == null ? "a mob" : attacker.type.name);
            case "player": return name + " was slain";
            case "kill": return name + " was killed";
            default: return name + " died";
        }
    }
}
