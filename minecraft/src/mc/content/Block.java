package mc.content;

import java.util.*;

/** Immutable block definition loaded from data/blocks.json (Section 2.4 / 6.1). States are the cross product of
 * property values and receive sequential global IDs at load. */
public final class Block {
    public static final int LAYER_NONE = 0, LAYER_SOLID = 1, LAYER_CUTOUT = 2, LAYER_CUTOUT_MIPPED = 3, LAYER_TRANSLUCENT = 4;
    public static final int SHAPE_NONE = 0, SHAPE_CUBE = 1, SHAPE_CROSS = 2, SHAPE_TORCH = 3, SHAPE_SLAB = 4, SHAPE_SNOW = 5,
            SHAPE_FARMLAND = 6, SHAPE_CACTUS = 7, SHAPE_FLUID = 8, SHAPE_LADDER = 9, SHAPE_BED = 10, SHAPE_PORTAL = 11,
            SHAPE_CROP = 12;

    public final String id, name;
    public final int index;
    public final double hardness, blastResistance, slipperiness;
    public final String tool;
    public final int minTier;
    public final boolean requiresTool;
    public final int light, opacity, layer, shape;
    public final String sound;
    public final String texTop, texSide, texBottom, texFront;
    public final List<Map<String, Object>> drops; // null = drop self, empty = none
    public final int flameEncouragement, flammability;
    public final boolean gravity, collision, hasItem;
    public final String[] propNames;
    public final String[][] propValues;
    public final int[] propStrides;
    public final Map<String, Object> extra;
    public int baseState, stateCount;

    Block(int index, String id, String name, double hardness, double blast, String tool, int minTier, boolean requiresTool,
          int light, int opacity, int layer, int shape, String sound, String[] tex, List<Map<String, Object>> drops,
          int enc, int flam, boolean gravity, double slip, boolean collision, boolean hasItem,
          LinkedHashMap<String, String[]> props, Map<String, Object> extra) {
        this.index = index; this.id = id; this.name = name; this.hardness = hardness; this.blastResistance = blast;
        this.tool = tool; this.minTier = minTier; this.requiresTool = requiresTool; this.light = light; this.opacity = opacity;
        this.layer = layer; this.shape = shape; this.sound = sound;
        this.texTop = tex[0]; this.texSide = tex[1]; this.texBottom = tex[2]; this.texFront = tex[3];
        this.drops = drops; this.flameEncouragement = enc; this.flammability = flam; this.gravity = gravity;
        this.slipperiness = slip; this.collision = collision; this.hasItem = hasItem; this.extra = extra;
        int n = props.size();
        propNames = new String[n];
        propValues = new String[n][];
        propStrides = new int[n];
        int k = 0, stride = 1;
        for (Map.Entry<String, String[]> e : props.entrySet()) {
            propNames[k] = e.getKey();
            propValues[k] = e.getValue();
            propStrides[k] = stride;
            stride *= e.getValue().length;
            k++;
        }
        stateCount = stride;
    }

    public int propIndex(String name) {
        for (int i = 0; i < propNames.length; i++) if (propNames[i].equals(name)) return i;
        return -1;
    }

    public boolean has(String key) { return extra.containsKey(key); }

    public String extraStr(String key) { Object o = extra.get(key); return o == null ? null : o.toString(); }

    public double extraNum(String key, double def) { Object o = extra.get(key); return o instanceof Number ? ((Number) o).doubleValue() : def; }

    public boolean unbreakable() { return hardness < 0; }

    public boolean isOpaqueCube() { return shape == SHAPE_CUBE && layer == LAYER_SOLID && opacity >= 15; }

    @Override public String toString() { return id; }
}
