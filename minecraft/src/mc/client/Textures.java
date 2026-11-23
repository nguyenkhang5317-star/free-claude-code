package mc.client;

import mc.content.*;
import mc.util.Json;
import mc.util.SplitMix;

import java.awt.image.BufferedImage;
import java.util.*;

/**
 * Procedural 16x16 textures (Appendix F: flat fill + 2-step dither, 8-16 colours per texture, original art).
 * Every texture is generated from data/textures.json patterns; nothing is copied from Mojang assets.
 */
public final class Textures {
    public static final int S = 16;
    /** ARGB pixels per texture index. */
    public static int[][] pix = new int[0][];
    public static final Map<String, Integer> INDEX = new HashMap<>();
    public static final List<String> NAMES = new ArrayList<>();
    /** per texture: true if some pixels are fully transparent (cutout). */
    public static boolean[] cutout = new boolean[0];
    /** average colour per texture (for distant LOD, particles and the map). */
    public static int[] average = new int[0];
    public static int[][] itemIcon; // per item index, 16x16 ARGB
    public static int[][] destroy = new int[10][]; // destroy stage overlays
    public static int missing;

    private Textures() { }

    public static synchronized void load() {
        if (pix.length > 0) return;
        Registries.load();
        List<int[]> list = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> e : Registries.TEXTURES.entrySet()) {
            INDEX.put(e.getKey(), list.size());
            NAMES.add(e.getKey());
            list.add(generate(e.getKey(), e.getValue()));
        }
        missing = list.size();
        int[] m = new int[256];
        for (int i = 0; i < 256; i++) m[i] = (((i >> 4) / 8 + (i & 15) / 8) % 2 == 0) ? 0xFFF800F8 : 0xFF000000;
        list.add(m);
        pix = list.toArray(new int[0][]);
        cutout = new boolean[pix.length];
        average = new int[pix.length];
        for (int t = 0; t < pix.length; t++) {
            long r = 0, g = 0, b = 0, n = 0;
            for (int p : pix[t]) {
                if ((p >>> 24) < 128) { cutout[t] = true; continue; }
                r += (p >> 16) & 255; g += (p >> 8) & 255; b += p & 255; n++;
            }
            if (n == 0) n = 1;
            average[t] = 0xFF000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
        }
        for (int s = 0; s < 10; s++) destroy[s] = destroyStage(s);
        itemIcon = new int[Items.ALL.size()][];
        for (Item it : Items.ALL) itemIcon[it.index] = ItemIcons.make(it);
    }

    public static int id(String name) {
        Integer i = INDEX.get(name);
        return i == null ? missing : i;
    }

    public static BufferedImage image(int[] p) {
        BufferedImage im = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        im.setRGB(0, 0, S, S, p, 0, S);
        return im;
    }

    // ------------------------------------------------------------------ generation helpers
    static int hex(String s) { return 0xFF000000 | Integer.parseInt(s.substring(1), 16); }

    static int[] cols(Map<String, Object> d, String key) {
        if (d.get(key) == null) return null;
        List<Object> l = Json.arr(d.get(key));
        int[] c = new int[l.size()];
        for (int i = 0; i < c.length; i++) c[i] = hex((String) l.get(i));
        return c;
    }

    static int shade(int c, double f) {
        int r = (int) Math.min(255, ((c >> 16) & 255) * f), g = (int) Math.min(255, ((c >> 8) & 255) * f), b = (int) Math.min(255, (c & 255) * f);
        return (c & 0xFF000000) | r << 16 | g << 8 | b;
    }

    static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    /** Pick palette entry for value v in [0,1] with a 2-step ordered dither between neighbours. */
    static int pal(int[] c, double v, int x, int y) {
        v = Math.max(0, Math.min(0.9999, v)) * c.length;
        int i = (int) v;
        double f = v - i;
        boolean up = f > 0.5 + (((x + y) & 1) == 0 ? 0.15 : -0.15);
        return c[Math.min(c.length - 1, up ? i + 1 : i)];
    }

    /** Tileable value noise on a period-16 lattice of the given cell size. */
    static final class VNoise {
        final double[] g;
        final int cells;

        VNoise(SplitMix r, int cells) {
            this.cells = cells;
            g = new double[cells * cells];
            for (int i = 0; i < g.length; i++) g[i] = r.nextDouble();
        }

        double at(double x, double y) {
            double fx = x * cells / 16.0, fy = y * cells / 16.0;
            int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
            double tx = fx - x0, ty = fy - y0;
            tx = tx * tx * (3 - 2 * tx);
            ty = ty * ty * (3 - 2 * ty);
            double a = v(x0, y0), b = v(x0 + 1, y0), c = v(x0, y0 + 1), d = v(x0 + 1, y0 + 1);
            return (a + (b - a) * tx) * (1 - ty) + (c + (d - c) * tx) * ty;
        }

        double v(int x, int y) { return g[Math.floorMod(y, cells) * cells + Math.floorMod(x, cells)]; }
    }

    static int[] fill(int c) {
        int[] p = new int[256];
        Arrays.fill(p, c);
        return p;
    }

    static void set(int[] p, int x, int y, int c) { if (x >= 0 && y >= 0 && x < 16 && y < 16) p[y * 16 + x] = c; }

    // ------------------------------------------------------------------ patterns
    static int[] generate(String name, Map<String, Object> d) {
        String pat = Json.str(d, "pattern", "noise");
        int[] c = cols(d, "colors");
        int[] acc = cols(d, "accent");
        if (c == null || c.length == 0) c = new int[]{0xFFFF00FF};
        SplitMix r = new SplitMix(name.hashCode() * 0x9E3779B97F4A7C15L + 12345);
        int[] p = new int[256];
        int n = c.length;
        switch (pat) {
            case "noise": {
                VNoise v = new VNoise(r, 4);
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) p[y * 16 + x] = pal(c, v.at(x, y) * 0.45 + r.nextDouble() * 0.55, x, y);
                break;
            }
            case "flat": {
                for (int y = 0; y < 16; y++)
                    for (int x = 0; x < 16; x++) {
                        double q = r.nextDouble();
                        p[y * 16 + x] = c[q < 0.12 ? 0 : q > 0.9 ? Math.min(n - 1, 2) : Math.min(n - 1, 1)];
                    }
                // bevel
                for (int i = 0; i < 16; i++) { p[i] = c[n - 1]; p[i * 16] = c[n - 1]; p[15 * 16 + i] = c[0]; p[i * 16 + 15] = c[0]; }
                break;
            }
            case "stone": case "netherrack": case "bedrock": {
                VNoise v = new VNoise(r, pat.equals("bedrock") ? 8 : 4), v2 = new VNoise(r, 8);
                for (int y = 0; y < 16; y++)
                    for (int x = 0; x < 16; x++) {
                        double q = pat.equals("bedrock") ? v.at(x, y) * 0.5 + r.nextDouble() * 0.5
                                : pat.equals("netherrack") ? v.at(x * 0.5, y * 2) * 0.6 + r.nextDouble() * 0.4 : v.at(x, y) * 0.55 + v2.at(x, y) * 0.3 + r.nextDouble() * 0.15;
                        p[y * 16 + x] = pal(c, q, x, y);
                    }
                break;
            }
            case "cobble": case "mossy": cells(p, c, r, 7, true); if (acc != null) moss(p, acc, r, 0.35); break;
            case "glow": {
                cells(p, c, r, 6, false);
                for (int i = 0; i < 6; i++) { int x = r.nextInt(16), y = r.nextInt(16); set(p, x, y, c[n - 1]); set(p, x + 1, y, c[n - 2]); }
                break;
            }
            case "pebble": {
                VNoise v = new VNoise(r, 8);
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) p[y * 16 + x] = pal(c, v.at(x, y) * 0.5 + r.nextDouble() * 0.5, x, y);
                for (int i = 0; i < 9; i++) {
                    int x = r.nextInt(16), y = r.nextInt(16);
                    set(p, x, y, c[n - 1]); set(p, x + 1, y, c[n - 2]); set(p, x, y + 1, c[0]); set(p, x + 1, y + 1, c[0]);
                }
                break;
            }
            case "grass_side": {
                VNoise v = new VNoise(r, 4);
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) p[y * 16 + x] = pal(c, v.at(x, y) * 0.4 + r.nextDouble() * 0.6, x, y);
                int[] a = acc == null ? c : acc;
                for (int x = 0; x < 16; x++) {
                    int depth = 3 + r.nextInt(3) - (x % 5 == 0 ? 1 : 0);
                    for (int y = 0; y < depth; y++) p[y * 16 + x] = pal(a, r.nextDouble(), x, y);
                }
                break;
            }
            case "facets": {
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) p[y * 16 + x] = pal(c, (((x + y * 2) % 7) / 7.0) * 0.5 + r.nextDouble() * 0.5, x, y);
                if (name.contains("crying")) for (int i = 0; i < 10; i++) set(p, r.nextInt(16), r.nextInt(16), 0xFF8F2BE0);
                break;
            }
            case "stone_bricks": {
                VNoise v = new VNoise(r, 8);
                for (int y = 0; y < 16; y++)
                    for (int x = 0; x < 16; x++) {
                        int row = y / 8, xo = (x + (row == 1 ? 8 : 0)) % 16;
                        boolean mortar = y % 8 == 7 || xo % 16 == 15;
                        boolean light = y % 8 == 0 || xo == 0;
                        p[y * 16 + x] = mortar ? c[0] : light ? c[n - 1] : pal(c, 0.3 + v.at(x, y) * 0.4 + r.nextDouble() * 0.2, x, y);
                    }
                if (Json.bool(d, "cracked", false)) crack(p, c[0], r);
                if (acc != null) moss(p, acc, r, 0.3);
                break;
            }
            case "bricks": {
                int mort = acc != null ? acc[0] : c[0];
                for (int y = 0; y < 16; y++)
                    for (int x = 0; x < 16; x++) {
                        int row = y / 4, xo = (x + (row % 2 == 1 ? 4 : 0)) % 8;
                        boolean mortar = y % 4 == 3 || xo == 7;
                        p[y * 16 + x] = mortar ? (acc != null ? pal(acc, r.nextDouble(), x, y) : mort) : pal(c, 0.2 + r.nextDouble() * 0.6 + (y % 4 == 0 ? 0.2 : 0), x, y);
                    }
                break;
            }
            case "ice": {
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) p[y * 16 + x] = pal(c, 0.35 + r.nextDouble() * 0.25, x, y);
                for (int k = 0; k < 3; k++) { int o = r.nextInt(16); for (int i = 0; i < 6; i++) set(p, (o + i) % 16, (o * 3 + i) % 16, c[n - 1]); }
                if (!Json.bool(d, "opaque", true)) for (int i = 0; i < 256; i++) p[i] = (p[i] & 0xFFFFFF) | 0xC0000000;
                break;
            }
            case "log_side": {
                double[] col = new double[16];
                for (int x = 0; x < 16; x++) col[x] = r.nextDouble();
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) p[y * 16 + x] = pal(c, col[x] * 0.7 + r.nextDouble() * 0.3, x, y);
                break;
            }
            case "birch_side": {
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) p[y * 16 + x] = pal(c, 0.4 + r.nextDouble() * 0.6, x, y);
                for (int i = 0; i < 7; i++) {
                    int x = r.nextInt(16), y = r.nextInt(16), l = 2 + r.nextInt(3);
                    for (int k = 0; k < l; k++) set(p, (x + k) % 16, y, 0xFF3A3A30);
                }
                break;
            }
            case "rings": {
                for (int y = 0; y < 16; y++)
                    for (int x = 0; x < 16; x++) {
                        int dd = Math.max(Math.abs(2 * x - 15), Math.abs(2 * y - 15)) / 2;
                        p[y * 16 + x] = dd >= 7 ? shade(c[0], 0.8) : c[(dd % 2 == 0 ? n - 1 : n - 2) - (dd > 4 ? 1 : 0)];
                    }
                break;
            }
            case "planks": case "table_top": case "bookshelf": case "table_side": case "table_front": case "chest": case "ladder": {
                planks(p, c, r);
                if (pat.equals("table_top")) {
                    for (int i = 0; i < 16; i++) { set(p, i, 0, c[0]); set(p, i, 15, c[0]); set(p, 0, i, c[0]); set(p, 15, i, c[0]); }
                    for (int i = 3; i < 13; i++) { set(p, i, 3, c[0]); set(p, i, 12, c[0]); set(p, 3, i, c[0]); set(p, 12, i, c[0]); }
                } else if (pat.equals("table_side") || pat.equals("table_front")) {
                    for (int x = 0; x < 16; x++) { set(p, x, 0, c[0]); set(p, x, 1, c[n - 1]); }
                    // tools hanging
                    for (int y = 4; y < 12; y++) { set(p, 4, y, 0xFF6B511F); set(p, 11, y, 0xFF6B511F); }
                    for (int x = 2; x < 7; x++) set(p, x, 4, 0xFF9A9A9A);
                    if (pat.equals("table_front")) for (int x = 9; x < 14; x++) set(p, x, 5, 0xFF9A9A9A);
                    else for (int y = 4; y < 7; y++) set(p, 12, y, 0xFF9A9A9A);
                } else if (pat.equals("bookshelf")) {
                    int[] bc = {0xFF8E1E1E, 0xFF1E4A8E, 0xFF2E7A2E, 0xFF7A5A1E, 0xFF5A2E7A};
                    for (int sh = 0; sh < 2; sh++)
                        for (int x = 1; x < 15; x++) {
                            int col = bc[r.nextInt(bc.length)], top = sh * 8 + 2 + r.nextInt(2);
                            for (int y = top; y < sh * 8 + 7; y++) set(p, x, y, shade(col, y == top ? 1.2 : 1.0));
                        }
                } else if (pat.equals("chest")) {
                    for (int i = 0; i < 16; i++) { set(p, i, 0, c[0]); set(p, i, 15, c[0]); set(p, 0, i, c[0]); set(p, 15, i, c[0]); set(p, i, 5, c[0]); }
                    if (Json.bool(d, "latch", false) || name.contains("front")) for (int y = 3; y < 8; y++) for (int x = 7; x < 9; x++) set(p, x, y, y == 3 ? 0xFFE0E0E0 : 0xFFB0B0B0);
                } else {
                    int[] q = fill(0);
                    for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                        boolean rail = x == 2 || x == 3 || x == 12 || x == 13, rung = (y % 4 == 1) && x > 2 && x < 13;
                        if (rail || rung) q[y * 16 + x] = rail ? p[y * 16 + x] : shade(p[y * 16 + x], 0.9);
                    }
                    p = q;
                }
                break;
            }
            case "leaves": {
                VNoise v = new VNoise(r, 8);
                boolean holes = !Json.bool(d, "opaque", false);
                for (int y = 0; y < 16; y++)
                    for (int x = 0; x < 16; x++) {
                        double q = v.at(x, y) * 0.5 + r.nextDouble() * 0.5;
                        p[y * 16 + x] = holes && q < 0.18 ? 0 : pal(c, q, x, y);
                    }
                break;
            }
            case "furnace_side": case "furnace_front": {
                VNoise v = new VNoise(r, 4);
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) p[y * 16 + x] = pal(c, v.at(x, y) * 0.5 + r.nextDouble() * 0.5, x, y);
                for (int i = 0; i < 16; i++) { set(p, i, 0, c[n - 1]); set(p, i, 15, c[0]); }
                if (pat.equals("furnace_front")) {
                    boolean lit = Json.bool(d, "lit", false) || name.endsWith("_on");
                    for (int y = 3; y < 7; y++) for (int x = 4; x < 12; x++) set(p, x, y, c[0]);
                    for (int y = 9; y < 14; y++) for (int x = 4; x < 12; x++) set(p, x, y, lit ? (y > 11 ? 0xFFFFC23A : 0xFFE0600F) : 0xFF1A1A1A);
                    for (int x = 3; x < 13; x++) { set(p, x, 8, c[n - 1]); set(p, x, 14, c[n - 1]); }
                }
                break;
            }
            case "tnt_side": {
                for (int y = 0; y < 16; y++)
                    for (int x = 0; x < 16; x++) {
                        boolean band = y >= 5 && y <= 10;
                        p[y * 16 + x] = band ? c[2] : ((x % 4 == 0) ? c[0] : c[1]);
                    }
                // "TNT" glyph blocks
                int[][] glyph = {{1, 6}, {2, 6}, {3, 6}, {2, 7}, {2, 8}, {2, 9}, {5, 6}, {5, 7}, {5, 8}, {5, 9}, {6, 7}, {7, 8}, {8, 6}, {8, 7}, {8, 8}, {8, 9},
                        {10, 6}, {11, 6}, {12, 6}, {11, 7}, {11, 8}, {11, 9}};
                for (int[] g : glyph) set(p, g[0] + 1, g[1], c[3]);
                break;
            }
            case "tnt_top": {
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) p[y * 16 + x] = ((x / 4 + y / 4) % 2 == 0) ? c[0] : c[1];
                for (int y = 6; y < 10; y++) for (int x = 6; x < 10; x++) set(p, x, y, c[3]);
                break;
            }
            case "melon": case "pumpkin_face": {
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) p[y * 16 + x] = pal(c, (x % 4 == 0 ? 0.1 : 0.5) + r.nextDouble() * 0.4, x, y);
                if (pat.equals("pumpkin_face")) {
                    int face = name.contains("jack") ? 0xFFFFD24A : 0xFF2A1A0A;
                    for (int[] e : new int[][]{{3, 4}, {4, 4}, {3, 5}, {4, 5}, {11, 4}, {12, 4}, {11, 5}, {12, 5}, {7, 7}, {8, 7}}) set(p, e[0], e[1], face);
                    for (int x = 3; x < 13; x++) { set(p, x, 10, face); if (x % 3 != 0) set(p, x, 11, face); }
                }
                break;
            }
            case "sandstone": {
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) p[y * 16 + x] = pal(c, (y < 3 ? 0.8 : y > 12 ? 0.15 : 0.45) + r.nextDouble() * 0.2, x, y);
                break;
            }
            case "cactus_side": case "cactus_top": {
                for (int y = 0; y < 16; y++)
                    for (int x = 0; x < 16; x++) {
                        boolean edge = pat.equals("cactus_side") ? (x == 1 || x == 14) : (x == 1 || x == 14 || y == 1 || y == 14);
                        boolean out = x == 0 || x == 15 || (pat.equals("cactus_top") && (y == 0 || y == 15));
                        p[y * 16 + x] = out ? 0 : edge ? c[0] : pal(c, 0.35 + r.nextDouble() * 0.5, x, y);
                    }
                if (pat.equals("cactus_side")) for (int i = 0; i < 8; i++) set(p, 2 + r.nextInt(12), r.nextInt(16), 0xFFE8E8C0);
                break;
            }
            case "farmland": {
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) p[y * 16 + x] = pal(c, (y % 4 == 0 ? 0.0 : 0.4) + r.nextDouble() * 0.6, x, y);
                break;
            }
            case "water": case "lava": case "portal": {
                VNoise v = new VNoise(r, 4);
                for (int y = 0; y < 16; y++)
                    for (int x = 0; x < 16; x++) {
                        double q = pat.equals("portal") ? (Math.sin((x + y) * 0.8 + v.at(x, y) * 6) * 0.5 + 0.5) : v.at(x, y) * 0.7 + r.nextDouble() * 0.3;
                        int col = pal(c, q, x, y);
                        p[y * 16 + x] = pat.equals("water") ? (col & 0xFFFFFF) | 0xB0000000 : pat.equals("portal") ? (col & 0xFFFFFF) | 0xC0000000 : col;
                    }
                break;
            }
            case "glass": {
                int frame = c[0], hl = c.length > 1 ? c[1] : c[0];
                for (int y = 0; y < 16; y++)
                    for (int x = 0; x < 16; x++) {
                        boolean edge = x == 0 || y == 0 || x == 15 || y == 15;
                        boolean streak = (x - y == 4 || x - y == 5 || x - y == -6) && x > 1 && x < 14;
                        p[y * 16 + x] = edge ? frame : streak ? (hl & 0xFFFFFF) | 0xA0000000 : (c[c.length - 1] & 0xFFFFFF) | (name.contains("stained") ? 0x80000000 : 0x00000000);
                    }
                if (name.equals("spawner")) for (int y = 0; y < 16; y += 4) for (int x = 0; x < 16; x++) { set(p, x, y, 0xFF2A3A4A); set(p, y, x, 0xFF2A3A4A); }
                break;
            }
            case "bed_top": {
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) p[y * 16 + x] = y < 6 ? (x > 1 && x < 14 ? c[3] : c[1]) : pal(new int[]{c[0], c[1], c[2]}, r.nextDouble(), x, y);
                break;
            }
            case "bed_side": {
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) p[y * 16 + x] = y < 7 ? 0 : y < 12 ? c[(x + y) % 2] : (x < 3 || x > 12) ? c[2] : 0;
                break;
            }
            case "wool": {
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) p[y * 16 + x] = pal(c, 0.25 + ((x + y * 3) % 4) / 8.0 + r.nextDouble() * 0.25, x, y);
                break;
            }
            case "ore": {
                VNoise v = new VNoise(r, 4);
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) p[y * 16 + x] = pal(c, v.at(x, y) * 0.6 + r.nextDouble() * 0.4, x, y);
                int[] a = acc == null ? new int[]{0xFF000000} : acc;
                for (int k = 0; k < 5; k++) {
                    int x = 1 + r.nextInt(13), y = 1 + r.nextInt(13);
                    set(p, x, y, a[0]); set(p, x + 1, y, a[a.length - 1]); set(p, x, y + 1, a[Math.min(1, a.length - 1)]);
                    if (r.nextBoolean()) set(p, x + 1, y + 1, a[0]);
                }
                break;
            }
            case "metal": {
                for (int y = 0; y < 16; y++)
                    for (int x = 0; x < 16; x++) {
                        boolean edge = x == 0 || y == 0, dark = x == 15 || y == 15;
                        p[y * 16 + x] = edge ? c[n - 1] : dark ? c[0] : pal(c, 0.35 + (x + y) / 60.0 + r.nextDouble() * 0.15, x, y);
                    }
                break;
            }
            // ---- transparent sprites
            case "flower": {
                int stem = c[0], petal = c.length > 1 ? c[1] : 0xFFFF0000;
                for (int y = 8; y < 16; y++) set(p, 7, y, stem);
                set(p, 6, 12, stem); set(p, 5, 11, stem); set(p, 8, 13, stem); set(p, 9, 12, stem);
                for (int[] q : new int[][]{{7, 4}, {6, 5}, {8, 5}, {7, 6}, {6, 4}, {8, 6}, {6, 6}, {8, 4}, {7, 3}, {5, 5}, {9, 5}, {7, 7}}) set(p, q[0], q[1], petal);
                set(p, 7, 5, c.length > 2 ? c[2] : shade(petal, 0.6));
                break;
            }
            case "sapling": {
                int trunk = c[0], leaf = c.length > 1 ? c[1] : 0xFF4F8A2C;
                for (int y = 9; y < 16; y++) set(p, 7, y, trunk);
                for (int y = 2; y < 10; y++) for (int x = 3; x < 12; x++) if (Math.abs(x - 7) + Math.abs(y - 5) < 5 && r.nextDouble() < 0.8) set(p, x, y, shade(leaf, 0.8 + r.nextDouble() * 0.4));
                break;
            }
            case "tuft": case "fern": {
                for (int k = 0; k < 9; k++) {
                    int x = 1 + r.nextInt(14), h = 5 + r.nextInt(10);
                    double lean = (r.nextDouble() - 0.5) * 0.5;
                    for (int y = 0; y < h; y++) set(p, (int) Math.round(x + lean * y), 15 - y, pal(c, y / (double) h, x, y));
                    if (pat.equals("fern")) for (int y = 2; y < h; y += 2) { set(p, (int) Math.round(x + lean * y) - 1, 15 - y, c[0]); set(p, (int) Math.round(x + lean * y) + 1, 15 - y, c[0]); }
                }
                break;
            }
            case "dead_bush": {
                int[][] br = {{7, 15, 7, 8}, {7, 10, 4, 6}, {7, 11, 11, 5}, {5, 8, 3, 3}, {10, 7, 13, 2}, {7, 8, 8, 3}};
                for (int[] b : br) line(p, b[0], b[1], b[2], b[3], c[(b[1] + b[0]) % c.length]);
                break;
            }
            case "cane": {
                for (int k : new int[]{3, 8, 12})
                    for (int y = 0; y < 16; y++) { set(p, k, y, y % 5 == 0 ? c[0] : c[1]); set(p, k + 1, y, c[c.length - 1]); }
                set(p, 2, 4, c[1]); set(p, 1, 3, c[1]); set(p, 10, 9, c[1]); set(p, 11, 8, c[1]);
                break;
            }
            case "mushroom": {
                for (int y = 9; y < 15; y++) { set(p, 7, y, c[2]); set(p, 8, y, c[2]); }
                for (int y = 5; y < 10; y++) for (int x = 4; x < 12; x++) if (!(y == 5 && (x == 4 || x == 11))) set(p, x, y, c[0]);
                if (c[1] != c[0]) { set(p, 6, 6, c[1]); set(p, 9, 7, c[1]); set(p, 5, 8, c[1]); }
                break;
            }
            case "crop": {
                int age = Json.integer(d, "age", 7);
                int h = 3 + age * 12 / 7;
                for (int x : new int[]{2, 5, 8, 11, 14})
                    for (int y = 0; y < h; y++) set(p, x + (y > h / 2 ? (x % 2) : 0), 15 - y, age >= 7 && y > h - 4 ? 0xFFC8A83A : (y % 3 == 0 ? c[0] : c[c.length - 1]));
                break;
            }
            case "torch": {
                for (int y = 6; y < 16; y++) { set(p, 7, y, c[0]); set(p, 8, y, c[1]); }
                set(p, 7, 5, c[2]); set(p, 8, 5, c[2]); set(p, 7, 4, c[3]); set(p, 8, 4, c[2]);
                if (name.contains("soul")) { set(p, 7, 5, 0xFF5AD8E0); set(p, 8, 5, 0xFF5AD8E0); set(p, 7, 4, 0xFFC0F8FF); }
                break;
            }
            case "web": {
                for (int i = 0; i < 16; i++) { set(p, i, i, c[0]); set(p, 15 - i, i, c[0]); set(p, 7, i, c[1]); set(p, i, 7, c[1]); }
                for (int rad = 3; rad < 8; rad += 2) for (int a = 0; a < 32; a++) set(p, (int) (7.5 + Math.cos(a / 5.1) * rad), (int) (7.5 + Math.sin(a / 5.1) * rad), c[0]);
                break;
            }
            default: {
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) p[y * 16 + x] = pal(c, r.nextDouble(), x, y);
            }
        }
        return p;
    }

    static void line(int[] p, int x0, int y0, int x1, int y1, int c) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : i / (double) steps;
            set(p, (int) Math.round(x0 + (x1 - x0) * t), (int) Math.round(y0 + (y1 - y0) * t), c);
        }
    }

    static void cells(int[] p, int[] c, SplitMix r, int count, boolean mortar) {
        int[][] pts = new int[count][2];
        double[] shadeV = new double[count];
        for (int i = 0; i < count; i++) { pts[i][0] = r.nextInt(16); pts[i][1] = r.nextInt(16); shadeV[i] = 0.3 + r.nextDouble() * 0.6; }
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++) {
                double b1 = 1e9, b2 = 1e9;
                int bi = 0;
                for (int i = 0; i < count; i++)
                    for (int ox = -16; ox <= 16; ox += 16)
                        for (int oy = -16; oy <= 16; oy += 16) {
                            double dx = x - pts[i][0] - ox, dy = y - pts[i][1] - oy, dd = Math.sqrt(dx * dx + dy * dy);
                            if (dd < b1) { b2 = b1; b1 = dd; bi = i; } else if (dd < b2) b2 = dd;
                        }
                boolean edge = mortar && b2 - b1 < 1.1;
                p[y * 16 + x] = edge ? c[0] : pal(c, shadeV[bi] - b1 * 0.04 + r.nextDouble() * 0.15, x, y);
            }
    }

    static void moss(int[] p, int[] acc, SplitMix r, double amount) {
        VNoise v = new VNoise(r, 4);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) if (v.at(x, y) < amount) p[y * 16 + x] = pal(acc, r.nextDouble(), x, y);
    }

    static void crack(int[] p, int c, SplitMix r) {
        int x = 3 + r.nextInt(10), y = 0;
        while (y < 16) { set(p, x, y, c); y++; x += r.nextInt(3) - 1; }
    }

    static void planks(int[] p, int[] c, SplitMix r) {
        int n = c.length;
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++) {
                int board = y / 4;
                int seamX = (board * 7 + 3) % 16;
                boolean seam = y % 4 == 3 || x == seamX;
                p[y * 16 + x] = seam ? c[0] : pal(c, 0.35 + ((x * 3 + board * 5) % 7) / 20.0 + r.nextDouble() * 0.2, x, y);
            }
    }

    static int[] destroyStage(int s) {
        int[] p = new int[256];
        SplitMix r = new SplitMix(777);
        int cracks = 2 + s * 2;
        for (int k = 0; k < cracks; k++) {
            int x = 8, y = 8;
            int dx = r.nextInt(3) - 1, dy = r.nextInt(3) - 1;
            if (dx == 0 && dy == 0) dx = 1;
            int len = 2 + s;
            for (int i = 0; i < len; i++) {
                set(p, x, y, 0x90000000);
                x += dx + (r.nextInt(4) == 0 ? r.nextInt(3) - 1 : 0);
                y += dy + (r.nextInt(4) == 0 ? r.nextInt(3) - 1 : 0);
            }
        }
        return p;
    }
}
