package mc.core;

/** Dimension configuration (Section 5.6). */
public final class Dimension {
    public final String id;
    public final int index;
    public final boolean hasSky, hasCeiling, ultrawarm, bedsWork;
    public final double coordinateScale;
    public final int fogColor, logicalHeight;

    private Dimension(int index, String id, boolean hasSky, boolean hasCeiling, boolean ultrawarm, boolean bedsWork, double scale,
                      int fogColor, int logicalHeight) {
        this.index = index; this.id = id; this.hasSky = hasSky; this.hasCeiling = hasCeiling; this.ultrawarm = ultrawarm;
        this.bedsWork = bedsWork; this.coordinateScale = scale; this.fogColor = fogColor; this.logicalHeight = logicalHeight;
    }

    public static final Dimension OVERWORLD = new Dimension(0, "minecraft:overworld", true, false, false, true, 1.0, 0xC0D8FF, 256);
    public static final Dimension NETHER = new Dimension(1, "minecraft:the_nether", false, true, true, false, 8.0, 0x330808, 128);
    public static final Dimension END = new Dimension(2, "minecraft:the_end", false, false, false, false, 1.0, 0x181020, 256);
    public static final Dimension[] ALL = {OVERWORLD, NETHER, END};

    public static Dimension byId(String id) {
        if (id == null) return OVERWORLD;
        if (!id.contains(":")) id = "minecraft:" + id;
        for (Dimension d : ALL) if (d.id.equals(id)) return d;
        if (id.equals("minecraft:nether")) return NETHER;
        if (id.equals("minecraft:end")) return END;
        return null;
    }

    public String folder() { return index == 0 ? "region" : index == 1 ? "DIM-1/region" : "DIM1/region"; }
}
