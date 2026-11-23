package mc.client;

import mc.content.*;

/**
 * Biome tinting. Textures are authored in plains colours; a tint is a per-channel factor relative to the plains
 * reference packed as RGB where 128 means 1.0 (so {@link #IDENTITY} leaves a texel unchanged).
 */
public final class BlockColors {
    public static final int IDENTITY = 0xFF808080;
    public static final int MODE_NONE = 0, MODE_GRASS_TOP = 1, MODE_GRASS = 2, MODE_FOLIAGE = 3;
    static final int REF_GRASS = 0x91BD59, REF_FOLIAGE = 0x77AB2F;
    private static int[] modeByBlock;

    private BlockColors() { }

    public static int mode(Block b) {
        if (modeByBlock == null) {
            int[] m = new int[Blocks.ALL.size()];
            for (Block bl : Blocks.ALL) {
                String t = bl.extraStr("tint");
                m[bl.index] = t == null ? MODE_NONE : t.equals("grass_top") ? MODE_GRASS_TOP : t.equals("grass") ? MODE_GRASS : t.equals("foliage") ? MODE_FOLIAGE : MODE_NONE;
            }
            modeByBlock = m;
        }
        return modeByBlock[b.index];
    }

    static int factor(int color, int ref) {
        int r = Math.min(255, ((color >> 16) & 255) * 128 / Math.max(1, (ref >> 16) & 255));
        int g = Math.min(255, ((color >> 8) & 255) * 128 / Math.max(1, (ref >> 8) & 255));
        int b = Math.min(255, (color & 255) * 128 / Math.max(1, ref & 255));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** Tint for a face of block b in biome bi; face 1 = top. */
    public static int tint(Block b, int face, Biome bi) {
        int m = mode(b);
        if (m == MODE_NONE || bi == null) return IDENTITY;
        if (m == MODE_GRASS_TOP && face != 1) return IDENTITY;
        if (b.id.contains("spruce_leaves")) return factor(0x619961, REF_FOLIAGE);
        if (b.id.contains("birch_leaves")) return factor(0x80A755, REF_FOLIAGE);
        return m == MODE_FOLIAGE ? factor(bi.foliageColor, REF_FOLIAGE) : factor(bi.grassColor, REF_GRASS);
    }

    public static int itemTint(Block b) {
        if (b.id.contains("spruce_leaves")) return factor(0x619961, REF_FOLIAGE);
        if (b.id.contains("birch_leaves")) return factor(0x80A755, REF_FOLIAGE);
        return IDENTITY;
    }

    public static int mul(int c, int t) {
        if (t == IDENTITY) return c;
        int r = Math.min(255, ((c >> 16) & 255) * ((t >> 16) & 255) >> 7);
        int g = Math.min(255, ((c >> 8) & 255) * ((t >> 8) & 255) >> 7);
        int b = Math.min(255, (c & 255) * (t & 255) >> 7);
        return (c & 0xFF000000) | r << 16 | g << 8 | b;
    }

    static int mul(int c, int t, String tex) { return mul(c, t); }
}
