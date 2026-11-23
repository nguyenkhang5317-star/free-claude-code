package mc.client;

import mc.content.*;

import java.util.HashMap;
import java.util.Map;

/** Original 16x16 item icons drawn from small templates and primitives, plus mini isometric cubes for block items. */
public final class ItemIcons {
    private ItemIcons() { }

    static final int HANDLE = 0xFF6B4A22, HANDLE_L = 0xFF9A7440, OUTLINE = 0xFF1C1C1C;
    static final Map<String, String[]> T = new HashMap<>();

    static {
        T.put("sword", new String[]{
                "................", ".............ll.", "............lml.", "...........lmd..", "..........lmd...", ".........lmd....",
                "........lmd.....", ".......lmd......", "..d...lmd.......", "...d.lmd........", "....dhd.........", ".....hd.........",
                "....h.dd........", "...h............", "..H.............", "................"});
        T.put("pickaxe", new String[]{
                "................", ".....mmmmm......", "....mlllllm.....", ".....mmmmhlm....", "........h.lm....", ".......h...lm...",
                "......h....lm...", ".....h......m...", "....h...........", "...h............", "..h.............", ".H..............",
                "................", "................", "................", "................"});
        T.put("axe", new String[]{
                "................", "......mm........", ".....mllm.......", "....mllmmh......", "....mlm.h.......", ".....m.h........",
                "......h.........", ".....h..........", "....h...........", "...h............", "..h.............", ".H..............",
                "................", "................", "................", "................"});
        T.put("shovel", new String[]{
                "................", "..........mmm...", ".........mlllm..", ".........mllm...", "........hmmm....", ".......h........",
                "......h.........", ".....h..........", "....h...........", "...h............", "..h.............", ".H..............",
                "................", "................", "................", "................"});
        T.put("hoe", new String[]{
                "................", ".......mmmmm....", "......mllllh....", "..........h.....", ".........h......", "........h.......",
                ".......h........", "......h.........", ".....h..........", "....h...........", "...h............", "..H.............",
                "................", "................", "................", "................"});
        T.put("helmet", new String[]{
                "................", "................", "................", "....mmmmmmmm....", "...mllllllllm...", "...mlmmmmmmlm...",
                "...mm......mm...", "...mm......mm...", "...dd......dd...", "................", "................", "................",
                "................", "................", "................", "................"});
        T.put("chestplate", new String[]{
                "................", "..mmm......mmm..", "..mlmm....mmlm..", "..mllmmmmmmllm..", "..mmllllllllmm..", "....mllllllm....",
                "....mlmmmmlm....", "....mllllllm....", "....mlmmmmlm....", "....mllllllm....", "....mddddddm....", "................",
                "................", "................", "................", "................"});
        T.put("leggings", new String[]{
                "................", "....mmmmmmmm....", "....mllllllm....", "....mlmmmmlm....", "....mlm..mlm....", "....mlm..mlm....",
                "....mlm..mlm....", "....mlm..mlm....", "....mlm..mlm....", "....mlm..mlm....", "....ddd..ddd....", "................",
                "................", "................", "................", "................"});
        T.put("boots", new String[]{
                "................", "................", "................", "................", "................", "...mmm....mmm...",
                "...mlm....mlm...", "...mlm....mlm...", "...mlm....mlm...", "..mllm....mllm..", "..dddd....dddd..", "................",
                "................", "................", "................", "................"});
        T.put("bucket", new String[]{
                "................", "................", "....dddddddd....", "...d........d...", "...dmmmmmmmmd...", "...mlsssssslm...",
                "...mlsssssslm...", "....mlllllmm....", "....mlllllmm....", "....mllllllm....", ".....mllllm.....", ".....mmmmmm.....",
                "................", "................", "................", "................"});
        T.put("bow", new String[]{
                "................", "........mmm....s", "......mm......s.", ".....m.......s..", "....m.......s...", "...m.......s....",
                "...m......s.....", "..m......s......", "..m.....s.......", "..m....s........", "..m...s.........", "...m.s..........",
                "...ms...........", "....s...........", "...s............", "................"});
        T.put("arrow", new String[]{
                "................", "............lll.", ".............ll.", "............h.l.", "...........h....", "..........h.....",
                ".........h......", "........h.......", ".......h........", "......h.........", ".....h..........", "..ff.h..........",
                "..fff...........", "...ff...........", "................", "................"});
        T.put("shears", new String[]{
                "................", "................", "...........ll...", "..........lml...", ".........lml....", "....ll..lml.....",
                "...lmmllml......", "....llmml.......", ".....dmd........", "....dd.dd.......", "...d.....d......", "...d.....d......",
                "....ddddd.......", "................", "................", "................"});
        T.put("flint_and_steel", new String[]{
                "................", "................", "..llllll........", "..l....l........", "..l....l........", "...l..l.........",
                "....ll..........", "................", ".........ss.....", "........ssss....", ".......sssss....", "......sssss.....",
                ".......sss......", "................", "................", "................"});
        T.put("book", new String[]{
                "................", "................", "...ddmmmmmmmm...", "...dlmmmmmmmmm..", "...dlmmmmmmmmm..", "...dlmmssssmmm..",
                "...dlmmmmmmmmm..", "...dlmmmmmmmmm..", "...dlmmmmmmmmm..", "...dlmmmmmmmmm..", "...dlmmmmmmmmm..", "...dsssssssssss.",
                "....ddddddddddd.", "................", "................", "................"});
        T.put("paper", new String[]{
                "................", "................", "...llllllllll...", "...lmmmmmmmmml..", "...lmssssssmml..", "...lmmmmmmmmml..",
                "...lmssssssmml..", "...lmmmmmmmmml..", "...lmsssssmmml..", "...lmmmmmmmmml..", "...lmmmmmmmmml..", "...llllllllll...",
                "................", "................", "................", "................"});
    }

    public static int[] make(Item it) {
        int[] p;
        if (it.block != null && (it.iconShape == null || it.iconShape.equals("block"))) {
            p = blockIcon(it.block);
        } else {
            p = new int[256];
            int c0 = it.iconColors != null && it.iconColors.length > 0 ? Textures.hex(it.iconColors[0]) : 0xFFB0B0B0;
            int c1 = it.iconColors != null && it.iconColors.length > 1 ? Textures.hex(it.iconColors[1]) : Textures.shade(c0, 0.7);
            draw(p, it.iconShape == null ? "lump" : it.iconShape, c0, c1, it.id);
            outline(p);
        }
        return p;
    }

    static void tpl(int[] p, String[] rows, int m, int s) {
        int l = Textures.shade(m, 1.3), d = Textures.shade(m, 0.6);
        for (int y = 0; y < 16 && y < rows.length; y++)
            for (int x = 0; x < 16 && x < rows[y].length(); x++) {
                int c;
                switch (rows[y].charAt(x)) {
                    case 'm': c = m; break;
                    case 'l': c = l; break;
                    case 'd': c = d; break;
                    case 'h': c = HANDLE; break;
                    case 'H': c = HANDLE_L; break;
                    case 's': c = s; break;
                    case 'f': c = 0xFFE8E8E8; break;
                    default: continue;
                }
                p[y * 16 + x] = c;
            }
    }

    static void disc(int[] p, double cx, double cy, double rx, double ry, int c, boolean shaded) {
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++) {
                double dx = (x + 0.5 - cx) / rx, dy = (y + 0.5 - cy) / ry, dd = dx * dx + dy * dy;
                if (dd <= 1) p[y * 16 + x] = shaded ? Textures.shade(c, 1.25 - 0.35 * (dx + dy + 1) / 2 - dd * 0.15) : c;
            }
    }

    static void draw(int[] p, String shape, int c0, int c1, String id) {
        String[] t = T.get(shape);
        if (t != null) {
            int s = c1;
            if (shape.equals("bucket")) s = id.contains("water") ? 0xFF3358D0 : id.contains("lava") ? 0xFFF58A1E : id.contains("milk") ? 0xFFF4F4F4 : c0;
            if (shape.equals("bow") || shape.equals("arrow")) { s = 0xFFDDDDDD; c0 = shape.equals("bow") ? HANDLE_L : 0xFF9A9A9A; }
            if (shape.equals("shears")) c0 = 0xFFCCCCCC;
            if (shape.equals("flint_and_steel")) { c0 = 0xFF9A9A9A; s = 0xFF3A3A3A; }
            if (shape.equals("book")) { c0 = 0xFF8E4A2A; s = 0xFFF0F0E0; }
            if (shape.equals("paper")) { c0 = 0xFFF2F2F2; s = 0xFFBBBBBB; }
            tpl(p, t, c0, s);
            return;
        }
        switch (shape) {
            case "ball": disc(p, 8, 8, 5, 5, c0, true); break;
            case "dust": {
                mc.util.SplitMix r = new mc.util.SplitMix(id.hashCode());
                for (int i = 0; i < 40; i++) {
                    double a = r.nextDouble() * 6.283, rad = Math.sqrt(r.nextDouble()) * 5;
                    int x = (int) (8 + Math.cos(a) * rad), y = (int) (9 + Math.sin(a) * rad * 0.7);
                    p[y * 16 + x] = r.nextBoolean() ? c0 : Textures.shade(c0, 1.3);
                }
                break;
            }
            case "lump": {
                mc.util.SplitMix r = new mc.util.SplitMix(id.hashCode());
                disc(p, 8, 8.5, 5, 4.5, c0, true);
                for (int i = 0; i < 5; i++) p[(4 + r.nextInt(9)) * 16 + 4 + r.nextInt(9)] = Textures.shade(c0, 1.5);
                break;
            }
            case "nugget": disc(p, 8, 9, 3, 2.5, c0, true); break;
            case "gem": {
                for (int y = 2; y < 14; y++) {
                    int w = y < 6 ? (y - 2) * 2 + 2 : (14 - y);
                    for (int x = 8 - w / 2 - 1; x <= 8 + w / 2; x++) if (x >= 0 && x < 16) p[y * 16 + x] = Textures.shade(c0, y < 6 ? 1.3 : x < 8 ? 1.0 : 0.75);
                }
                break;
            }
            case "ingot": {
                for (int y = 5; y < 11; y++)
                    for (int x = 2 + (10 - y) / 2; x < 14 - (y - 5) / 2; x++) p[y * 16 + x] = Textures.shade(c0, y == 5 ? 1.35 : y > 8 ? 0.8 : 1.0);
                break;
            }
            case "egg": case "egg2": {
                disc(p, 8, 8.5, 4.5, 6, c0, true);
                if (shape.equals("egg2")) {
                    mc.util.SplitMix r = new mc.util.SplitMix(id.hashCode());
                    for (int i = 0; i < 9; i++) {
                        int x = 5 + r.nextInt(6), y = 4 + r.nextInt(9);
                        if (p[y * 16 + x] != 0) { p[y * 16 + x] = c1; if (x < 15 && p[y * 16 + x + 1] != 0) p[y * 16 + x + 1] = c1; }
                    }
                }
                break;
            }
            case "meat": case "fish": case "potato": case "bread": {
                double rx = shape.equals("bread") ? 6 : 5.5, ry = shape.equals("bread") ? 3.5 : 4;
                disc(p, 8, 8, rx, ry, c0, true);
                if (shape.equals("meat")) for (int x = 4; x < 12; x++) p[(7 + (x % 3 == 0 ? 1 : 0)) * 16 + x] = c1;
                if (shape.equals("fish")) { for (int y = 5; y < 11; y++) for (int x = 13; x < 16; x++) if (Math.abs(y - 8) <= x - 12) p[y * 16 + x] = c1; p[7 * 16 + 4] = 0xFF101010; }
                if (shape.equals("bread")) for (int x = 4; x < 13; x += 3) p[6 * 16 + x] = c1;
                break;
            }
            case "drumstick": {
                disc(p, 6.5, 6.5, 4.5, 4.5, c0, true);
                Textures.line(p, 9, 9, 13, 13, 0xFFF0EADC);
                p[13 * 16 + 14] = 0xFFF0EADC; p[14 * 16 + 13] = 0xFFF0EADC;
                break;
            }
            case "apple": {
                disc(p, 8, 9, 5, 5, c0, true);
                p[3 * 16 + 8] = HANDLE; p[2 * 16 + 8] = HANDLE; p[3 * 16 + 9] = 0xFF4F8A2C; p[2 * 16 + 10] = 0xFF4F8A2C;
                break;
            }
            case "carrot": {
                for (int i = 0; i < 9; i++) {
                    int x = 3 + i, y = 12 - i;
                    for (int w = 0; w <= (i / 3); w++) { int px = x - w, py = y - w; if (px >= 0 && py >= 0) p[py * 16 + px] = Textures.shade(c0, 1 - w * 0.15); }
                }
                p[2 * 16 + 12] = 0xFF4F8A2C; p[1 * 16 + 13] = 0xFF4F8A2C; p[2 * 16 + 14] = 0xFF4F8A2C; p[3 * 16 + 13] = 0xFF4F8A2C;
                break;
            }
            case "slice": {
                for (int y = 4; y < 13; y++) for (int x = 3; x < 3 + (y - 3); x++) p[y * 16 + x] = y == 12 ? c1 : (x + y) % 5 == 0 ? 0xFF1A1A1A : c0;
                break;
            }
            case "bowl": {
                for (int y = 8; y < 13; y++) for (int x = 2 + (y - 8); x < 14 - (y - 8); x++) p[y * 16 + x] = Textures.shade(c0, y == 8 ? 1.3 : 1);
                if (id.contains("stew")) for (int x = 3; x < 13; x++) p[8 * 16 + x] = 0xFFB07A4A;
                break;
            }
            case "stick": Textures.line(p, 3, 12, 12, 3, c0); Textures.line(p, 4, 12, 12, 4, Textures.shade(c0, 0.7)); break;
            case "bone": Textures.line(p, 4, 11, 11, 4, c0); p[3 * 16 + 11] = c0; p[4 * 16 + 12] = c0; p[11 * 16 + 3] = c0; p[12 * 16 + 4] = c0; break;
            case "string":
                for (int x = 2; x < 14; x++) p[(int) (8 + Math.sin(x * 0.9) * 3) * 16 + x] = c0;
                break;
            case "feather":
                Textures.line(p, 3, 13, 12, 3, 0xFFCCCCCC);
                for (int i = 0; i < 8; i++) { int x = 4 + i, y = 12 - i; if (x + 1 < 16) p[y * 16 + x + 1] = c0; if (y + 1 < 16 && x + 2 < 16) p[(y + 1) * 16 + x + 2] = c0; if (y - 1 >= 0 && x - 1 >= 0) p[(y - 1) * 16 + x - 1] = c0; }
                break;
            case "wheat":
                for (int k : new int[]{5, 8, 11}) for (int y = 3; y < 14; y++) p[y * 16 + k + (y < 7 ? (y % 2) : 0)] = y < 7 ? c0 : Textures.shade(c0, 0.8);
                break;
            case "seeds": {
                int[][] pts = {{5, 6}, {9, 5}, {7, 9}, {11, 9}, {4, 11}, {9, 12}};
                for (int[] q : pts) { p[q[1] * 16 + q[0]] = c0; p[q[1] * 16 + q[0] + 1] = Textures.shade(c0, 0.7); }
                break;
            }
            default: disc(p, 8, 8, 5, 5, c0, true);
        }
    }

    /** Dark 1px outline around opaque pixels for readability against the slot background. */
    static void outline(int[] p) {
        int[] q = p.clone();
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++) {
                if ((q[y * 16 + x] >>> 24) != 0) continue;
                boolean adj = (x > 0 && (q[y * 16 + x - 1] >>> 24) != 0) || (x < 15 && (q[y * 16 + x + 1] >>> 24) != 0)
                        || (y > 0 && (q[(y - 1) * 16 + x] >>> 24) != 0) || (y < 15 && (q[(y + 1) * 16 + x] >>> 24) != 0);
                if (adj) p[y * 16 + x] = 0x90000000 | (OUTLINE & 0xFFFFFF);
            }
    }

    /** Isometric cube (or flat sprite for non-cube shapes) rendered from the block's own textures. */
    static int[] blockIcon(Block b) {
        int top = Textures.id(b.texTop), side = Textures.id(b.texSide), front = Textures.id(b.texFront);
        int tint = BlockColors.itemTint(b);
        if (b.shape != Block.SHAPE_CUBE && b.shape != Block.SHAPE_SLAB && b.shape != Block.SHAPE_CACTUS && b.shape != Block.SHAPE_FARMLAND
                && b.shape != Block.SHAPE_SNOW) {
            int[] src = Textures.pix[b.shape == Block.SHAPE_BED ? top : side];
            int[] p = new int[256];
            for (int i = 0; i < 256; i++) p[i] = BlockColors.mul(src[i], tint);
            return p;
        }
        double h = b.shape == Block.SHAPE_SLAB ? 0.5 : b.shape == Block.SHAPE_SNOW ? 0.125 : 1;
        int[] p = new int[256];
        // render with inverse mapping per destination pixel; cube spans x 1..15
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++) {
                double px = x + 0.5, py = y + 0.5;
                // top face: diamond centred (8,4.5) half-width 7, half-height 3.5, lowered by (1-h)*8
                double ty = py - (1 - h) * 8;
                double u = ((px - 8) / 7 + (ty - 4.5) / 3.5) / 2 + 0.5, v = ((ty - 4.5) / 3.5 - (px - 8) / 7) / 2 + 0.5;
                if (u >= 0 && u < 1 && v >= 0 && v < 1) {
                    p[y * 16 + x] = BlockColors.mul(Textures.pix[top][(int) (v * 16) * 16 + (int) (u * 16)], tint, b.texTop);
                    continue;
                }
                // left face (front/south)
                if (px >= 1 && px < 8) {
                    double fx = (px - 1) / 7;
                    double yTop = 4.5 + fx * 3.5 + (1 - h) * 8, yb = yTop + 8 * h;
                    if (py >= yTop && py < yb) {
                        double fv = (py - yTop) / 8 + (1 - h);
                        int c = Textures.pix[front][Math.min(15, (int) (fv * 16)) * 16 + Math.min(15, (int) (fx * 16))];
                        p[y * 16 + x] = Textures.shade(BlockColors.mul(c, tint, b.texFront), 0.82);
                        continue;
                    }
                }
                if (px >= 8 && px < 15) {
                    double fx = (px - 8) / 7;
                    double yTop = 8 - fx * 3.5 + (1 - h) * 8, yb = yTop + 8 * h;
                    if (py >= yTop && py < yb) {
                        double fv = (py - yTop) / 8 + (1 - h);
                        int c = Textures.pix[side][Math.min(15, (int) (fv * 16)) * 16 + Math.min(15, (int) (fx * 16))];
                        p[y * 16 + x] = Textures.shade(BlockColors.mul(c, tint, b.texSide), 0.62);
                    }
                }
            }
        return p;
    }
}
