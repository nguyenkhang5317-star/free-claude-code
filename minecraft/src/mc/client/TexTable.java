package mc.client;

import java.util.ArrayList;
import java.util.List;

/** Unified 16x16 texture table used by the rasteriser: block textures, item icons, destroy stages, entity skins, misc. */
public final class TexTable {
    private TexTable() { }

    private static final List<int[]> list = new ArrayList<>();
    public static int ITEM_BASE, DESTROY_BASE, WHITE, SUN, MOON, XP, PARTICLE, CLOUD, STAR;
    private static int[][] array;

    public static synchronized void init() {
        if (!list.isEmpty()) return;
        Textures.load();
        for (int[] p : Textures.pix) list.add(p);
        ITEM_BASE = list.size();
        for (int[] p : Textures.itemIcon) list.add(p);
        DESTROY_BASE = list.size();
        for (int[] p : Textures.destroy) list.add(p);
        WHITE = add(Textures.fill(0xFFFFFFFF));
        int[] sun = new int[256], moon = new int[256], xp = new int[256], cloud = Textures.fill(0xE0FFFFFF), star = Textures.fill(0xFFFFFFFF);
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++) {
                double d = Math.max(Math.abs(x - 7.5), Math.abs(y - 7.5));
                sun[y * 16 + x] = d < 5 ? 0xFFFFF8C0 : d < 7 ? 0xA0FFE070 : 0;
                moon[y * 16 + x] = d < 5 ? (((x * 7 + y * 3) % 5 == 0) ? 0xFFB8B8C8 : 0xFFE0E0EA) : 0;
                double r = Math.hypot(x - 7.5, y - 7.5);
                xp[y * 16 + x] = r < 4 ? 0xFFF0FF60 : r < 6 ? 0xFF80E020 : 0;
            }
        SUN = add(sun); MOON = add(moon); XP = add(xp); CLOUD = add(cloud); STAR = add(star);
        PARTICLE = WHITE;
        EntityModels.init();
    }

    public static synchronized int add(int[] p) {
        list.add(p);
        array = null;
        return list.size() - 1;
    }

    public static synchronized int[][] array() {
        if (array == null) array = list.toArray(new int[0][]);
        return array;
    }
}
