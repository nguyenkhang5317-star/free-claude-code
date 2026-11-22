package mc.core;

import mc.content.*;

/**
 * Fluid flow (Section 5.4 / Appendix BQ). level 0 = source, 1..7 = flowing distance, 8..15 = falling.
 * Water drops 1 level per block (spread 7), lava 2 (spread 3) or 1 in ultrawarm dimensions.
 * Flow prefers the direction of the nearest drop within the slope-find distance.
 */
public final class Fluids {
    private Fluids() { }

    private static final int[] DX = {1, -1, 0, 0}, DZ = {0, 0, 1, -1};

    static int step(World w, int base) {
        Block b = Blocks.block(base);
        int spread = (int) b.extraNum("spread", 7);
        if (base == Blocks.LAVA && w.dim.ultrawarm) spread = 7;
        return spread >= 7 ? 1 : 2;
    }

    static int rate(World w, int base) {
        int r = (int) Blocks.block(base).extraNum("tick_rate", 5);
        if (base == Blocks.LAVA && w.dim.ultrawarm) r = 10;
        return r;
    }

    private static boolean same(int s, int base) { return Blocks.block(s).baseState == base; }

    /** Effective "distance" of a fluid state: source 0, flowing level, falling counts as 0 for horizontal feed. */
    private static int lvl(int s) { int l = Blocks.prop(s, "level"); return l >= 8 ? 0 : l; }

    private static boolean canReplace(int s, int base) {
        if (s == Blocks.AIR) return true;
        if (Blocks.fluid[s]) return same(s, base) && Blocks.prop(s, "level") != 0;
        Block b = Blocks.block(s);
        return !b.collision || b.shape == Block.SHAPE_CROSS || b.shape == Block.SHAPE_TORCH || b.shape == Block.SHAPE_CROP
                || (Blocks.replaceable[s] && b.shape != Block.SHAPE_CUBE);
    }

    public static void tick(World w, int x, int y, int z, int s) {
        int base = Blocks.block(s).baseState;
        boolean lava = base == Blocks.LAVA;
        int level = Blocks.prop(s, "level");
        if (lava && mixLava(w, x, y, z, level)) return;
        int st = step(w, base);
        if (level != 0) {
            // recompute from neighbors
            int newLevel;
            int above = w.getBlock(x, y + 1, z);
            if (same(above, base)) {
                newLevel = 8;
            } else {
                int best = 99, sources = 0;
                for (int d = 0; d < 4; d++) {
                    int n = w.getBlock(x + DX[d], y, z + DZ[d]);
                    if (!same(n, base)) continue;
                    int nl = Blocks.prop(n, "level");
                    if (nl == 0) sources++;
                    best = Math.min(best, lvl(n));
                }
                int below = w.getBlock(x, y - 1, z);
                if (!lava && sources >= 2 && (Blocks.solidCollision[below] || (same(below, base) && Blocks.prop(below, "level") == 0))) newLevel = 0;
                else newLevel = best + st;
                if (newLevel > 7) newLevel = -1;
            }
            if (newLevel != (level >= 8 ? 8 : level) || (level >= 8 && newLevel != 8)) {
                if (newLevel < 0) {
                    w.setBlock(x, y, z, Blocks.AIR);
                    return;
                }
                if (newLevel != level) {
                    w.setBlock(x, y, z, Blocks.with(base, "level", newLevel));
                    level = newLevel;
                }
            }
        }
        // flow down
        int below = w.getBlock(x, y - 1, z);
        if (y > 0 && canReplace(below, base)) {
            if (lava && Blocks.isWater(below)) {
                w.setBlock(x, y - 1, z, Blocks.STONE);
                w.playSound("block.fire.extinguish", x + 0.5, y, z + 0.5, 0.5f, 2.6f);
                return;
            }
            place(w, x, y - 1, z, base, 8);
            if (level != 0) return;
        } else if (same(below, base) && !(Blocks.prop(below, "level") == 0 && level == 0)) {
            if (level != 0) return;
        }
        int next = (level >= 8 ? 0 : level) + st;
        if (next > 7) return;
        // slope finding
        int maxDist = lava && !w.dim.ultrawarm ? 2 : 4;
        int bestDist = 1000;
        int[] dist = new int[4];
        for (int d = 0; d < 4; d++) {
            int nx = x + DX[d], nz = z + DZ[d];
            int n = w.getBlock(nx, y, nz);
            if (!canReplace(n, base) || (same(n, base) && Blocks.prop(n, "level") == 0)) { dist[d] = 1001; continue; }
            int bn = w.getBlock(nx, y - 1, nz);
            dist[d] = canReplace(bn, base) || same(bn, base) ? 0 : slope(w, nx, y, nz, base, d, 1, maxDist);
            bestDist = Math.min(bestDist, dist[d]);
        }
        for (int d = 0; d < 4; d++) {
            if (dist[d] > 1000 || dist[d] != bestDist) continue;
            int nx = x + DX[d], nz = z + DZ[d];
            int n = w.getBlock(nx, y, nz);
            if (same(n, base) && lvl(n) <= next) continue;
            if (!lava && Blocks.isLava(n)) {
                w.setBlock(nx, y, nz, Blocks.prop(n, "level") == 0 ? Blocks.OBSIDIAN : Blocks.COBBLESTONE);
                continue;
            }
            place(w, nx, y, nz, base, next);
        }
    }

    private static int slope(World w, int x, int y, int z, int base, int from, int depth, int max) {
        int best = 1000;
        for (int d = 0; d < 4; d++) {
            if ((d ^ 1) == from) continue;
            int nx = x + DX[d], nz = z + DZ[d];
            int n = w.getBlock(nx, y, nz);
            if (!canReplace(n, base) || (same(n, base) && Blocks.prop(n, "level") == 0)) continue;
            int bn = w.getBlock(nx, y - 1, nz);
            if (canReplace(bn, base) || same(bn, base)) return depth;
            if (depth < max) best = Math.min(best, slope(w, nx, y, nz, base, d, depth + 1, max));
        }
        return best;
    }

    private static void place(World w, int x, int y, int z, int base, int level) {
        int old = w.getBlock(x, y, z);
        if (old != Blocks.AIR && !Blocks.fluid[old]) {
            if (base == Blocks.LAVA) w.playSound("block.fire.extinguish", x + 0.5, y + 0.5, z + 0.5, 0.5f, 2.6f);
            else w.destroyBlock(x, y, z, true, null, null);
        }
        w.setBlock(x, y, z, Blocks.with(base, "level", level));
        w.schedule(x, y, z, rate(w, base));
    }

    /** Lava touching water hardens (Section 5.4). */
    private static boolean mixLava(World w, int x, int y, int z, int level) {
        boolean water = Blocks.isWater(w.getBlock(x, y + 1, z));
        for (int d = 0; d < 4 && !water; d++) water = Blocks.isWater(w.getBlock(x + DX[d], y, z + DZ[d]));
        if (!water) return false;
        w.setBlock(x, y, z, level == 0 ? Blocks.OBSIDIAN : Blocks.COBBLESTONE);
        w.playSound("block.fire.extinguish", x + 0.5, y + 0.5, z + 0.5, 0.5f, 2.6f);
        return true;
    }
}
