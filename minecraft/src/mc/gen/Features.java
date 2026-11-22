package mc.gen;

import mc.content.*;
import mc.core.World;

import java.util.Random;

/** Tree and small-plant features (Section 4.5). Writes go through a BlockSink so the same code runs inside the
 * pure chunk generator (clipped to one chunk) and in the live world (sapling growth). */
public final class Features {
    private Features() { }

    public interface BlockSink {
        int get(int x, int y, int z);

        void set(int x, int y, int z, int state);
    }

    /** Random abstraction shared by java.util.Random (world) and SplitMix (generation). */
    public interface Rng {
        int nextInt(int bound);

        double nextDouble();
    }

    public static Rng of(Random r) {
        return new Rng() {
            public int nextInt(int b) { return r.nextInt(b); }
            public double nextDouble() { return r.nextDouble(); }
        };
    }

    public static Rng of(mc.util.SplitMix r) {
        return new Rng() {
            public int nextInt(int b) { return r.nextInt(b); }
            public double nextDouble() { return r.nextDouble(); }
        };
    }

    private static int log(String wood) {
        Block b = Blocks.BY_ID.get("minecraft:" + wood + "_log");
        return b == null ? Blocks.OAK_LOG : b.baseState;
    }

    private static int leaves(String wood) {
        Block b = Blocks.BY_ID.get("minecraft:" + wood + "_leaves");
        return b == null ? Blocks.OAK_LEAVES : b.baseState;
    }

    private static boolean soft(int s) { return s == Blocks.AIR || Blocks.replaceable[s] || Blocks.block(s).id.endsWith("_leaves"); }

    private static void leaf(BlockSink w, int x, int y, int z, int st) {
        int s = w.get(x, y, z);
        if (s == Blocks.AIR || (Blocks.replaceable[s] && !Blocks.fluid[s])) w.set(x, y, z, st);
    }

    /** Place a tree with its base at (x,y,z) (the block above the soil). Returns false if there is no room. */
    public static boolean tree(BlockSink w, String wood, int x, int y, int z, Rng r, boolean checkSpace) {
        int lg = log(wood), lv = leaves(wood);
        switch (wood) {
            case "spruce": {
                int h = 6 + r.nextInt(4);
                if (checkSpace && !clear(w, x, y, z, h, 1)) return false;
                int top = y + h;
                int radius = 0;
                for (int yy = top; yy >= y + 2; yy--) {
                    for (int dx = -radius; dx <= radius; dx++)
                        for (int dz = -radius; dz <= radius; dz++)
                            if (Math.abs(dx) + Math.abs(dz) <= radius + (radius > 1 ? 1 : 0) && (dx != 0 || dz != 0 || yy >= top)) leaf(w, x + dx, yy, z + dz, lv);
                    radius = radius >= 2 ? (yy % 2 == 0 ? 1 : 2) : radius + 1;
                }
                leaf(w, x, top + 1, z, lv);
                for (int i = 0; i < h; i++) w.set(x, y + i, z, lg);
                return true;
            }
            case "acacia": {
                int h = 5 + r.nextInt(2);
                if (checkSpace && !clear(w, x, y, z, h, 1)) return false;
                int dir = r.nextInt(4);
                int ddx = dir == 0 ? 1 : dir == 1 ? -1 : 0, ddz = dir == 2 ? 1 : dir == 3 ? -1 : 0;
                int cx = x, cz = z;
                for (int i = 0; i < h; i++) {
                    if (i >= h - 2) { cx += ddx; cz += ddz; }
                    w.set(cx, y + i, cz, lg);
                }
                int ty = y + h;
                for (int dx = -3; dx <= 3; dx++)
                    for (int dz = -3; dz <= 3; dz++) if (Math.abs(dx) + Math.abs(dz) <= 4) leaf(w, cx + dx, ty - 1, cz + dz, lv);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) leaf(w, cx + dx, ty, cz + dz, lv);
                return true;
            }
            case "dark_oak": {
                int h = 6 + r.nextInt(3);
                if (checkSpace && !clear(w, x, y, z, h, 2)) return false;
                int ty = y + h;
                for (int yy = ty - 3; yy <= ty; yy++) {
                    int rad = yy == ty ? 2 : 3;
                    for (int dx = -rad; dx <= rad + 1; dx++)
                        for (int dz = -rad; dz <= rad + 1; dz++) {
                            if ((dx == -rad || dx == rad + 1) && (dz == -rad || dz == rad + 1)) continue;
                            leaf(w, x + dx, yy, z + dz, lv);
                        }
                }
                for (int i = 0; i < h; i++)
                    for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++) w.set(x + dx, y + i, z + dz, lg);
                return true;
            }
            case "jungle": {
                int h = 8 + r.nextInt(6);
                if (checkSpace && !clear(w, x, y, z, h, 1)) return false;
                int ty = y + h;
                blob(w, x, ty, z, lv, 3);
                for (int i = 0; i < h; i++) w.set(x, y + i, z, lg);
                return true;
            }
            default: { // oak, birch
                boolean birch = wood.equals("birch");
                int h = (birch ? 5 : 4) + r.nextInt(3);
                if (checkSpace && !clear(w, x, y, z, h, 1)) return false;
                int ty = y + h;
                for (int yy = ty - 3; yy <= ty; yy++) {
                    int rad = yy >= ty - 1 ? 1 : 2;
                    for (int dx = -rad; dx <= rad; dx++)
                        for (int dz = -rad; dz <= rad; dz++) {
                            if (Math.abs(dx) == rad && Math.abs(dz) == rad && (yy == ty || r.nextInt(2) == 0)) continue;
                            leaf(w, x + dx, yy, z + dz, lv);
                        }
                }
                for (int i = 0; i < h; i++) w.set(x, y + i, z, lg);
                return true;
            }
        }
    }

    private static void blob(BlockSink w, int x, int y, int z, int lv, int rad) {
        for (int dy = -2; dy <= 1; dy++) {
            int rr = dy == 1 ? rad - 1 : rad;
            for (int dx = -rr; dx <= rr; dx++)
                for (int dz = -rr; dz <= rr; dz++) if (dx * dx + dz * dz <= rr * rr + 1) leaf(w, x + dx, y + dy, z + dz, lv);
        }
    }

    private static boolean clear(BlockSink w, int x, int y, int z, int h, int size) {
        if (y + h + 2 > 255) return false;
        for (int i = 0; i < h; i++)
            for (int dx = 0; dx < size; dx++)
                for (int dz = 0; dz < size; dz++) if (!soft(w.get(x + dx, y + i, z + dz))) return false;
        return true;
    }

    /** Sapling growth in the live world. */
    public static boolean growTree(World world, String wood, int x, int y, int z, Random rng) {
        BlockSink sink = new BlockSink() {
            public int get(int a, int b, int c) { return world.getBlock(a, b, c); }
            public void set(int a, int b, int c, int s) { world.setBlock(a, b, c, s); }
        };
        int below = world.getBlock(x, y - 1, z);
        if (below == Blocks.GRASS_BLOCK) world.setBlock(x, y - 1, z, Blocks.DIRT);
        return tree(sink, wood, x, y, z, of(rng), true);
    }
}
