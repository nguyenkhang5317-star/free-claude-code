package mc.entity;

import mc.content.*;
import mc.core.World;

import java.util.*;

/** Grid A* for ground mobs (Section 8.3): 4-neighbour moves, step up 1, drop up to 3, node budget 512. */
public final class Pathfinder {
    private Pathfinder() { }

    public static final class Path {
        public final List<int[]> nodes;
        public int index;

        Path(List<int[]> n) { nodes = n; }

        public boolean done() { return index >= nodes.size(); }

        public int[] current() { return nodes.get(Math.min(index, nodes.size() - 1)); }

        public int[] end() { return nodes.get(nodes.size() - 1); }
    }

    private static boolean passable(World w, int x, int y, int z) {
        int s = w.getBlock(x, y, z);
        if (Blocks.solidCollision[s]) {
            Block b = Blocks.block(s);
            return b.shape == Block.SHAPE_SNOW || b.shape == Block.SHAPE_LADDER;
        }
        if (Blocks.isLava(s) || s == Blocks.FIRE || s == Blocks.CACTUS || s == Blocks.COBWEB) return false;
        return true;
    }

    /** Can a mob of the given height stand with feet at (x,y,z)? */
    public static boolean standable(World w, int x, int y, int z, int h) {
        if (y < 1 || y > 254) return false;
        for (int i = 0; i < h; i++) if (!passable(w, x, y + i, z)) return false;
        int below = w.getBlock(x, y - 1, z);
        Block bb = Blocks.block(below);
        if (Blocks.isWater(w.getBlock(x, y, z))) return true; // swimming
        if (bb.shape == Block.SHAPE_TORCH || below == Blocks.CACTUS || below == Blocks.FIRE || Blocks.isLava(below)) return false;
        return Blocks.solidCollision[below] && bb.shape != Block.SHAPE_SNOW || Blocks.isWater(below);
    }

    public static Path find(World w, int sx, int sy, int sz, int tx, int ty, int tz, int height, double reach) {
        int budget = Registries.tunei("pathfinder_node_budget");
        if (!w.isLoadedBlock(tx, tz)) return null;
        PriorityQueue<long[]> open = new PriorityQueue<>(Comparator.comparingLong(a -> a[0]));
        Map<Long, Long> parent = new HashMap<>();
        Map<Long, Integer> g = new HashMap<>();
        long start = mc.core.World.packPos(sx, sy, sz);
        g.put(start, 0);
        parent.put(start, start);
        open.add(new long[]{h(sx, sy, sz, tx, ty, tz), start});
        long best = start;
        int bestH = h(sx, sy, sz, tx, ty, tz);
        int expanded = 0;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!open.isEmpty() && expanded < budget) {
            long[] cur = open.poll();
            long p = cur[1];
            int x = mc.core.World.unpackX(p), y = mc.core.World.unpackY(p), z = mc.core.World.unpackZ(p);
            int hh = h(x, y, z, tx, ty, tz);
            if (hh < bestH) { bestH = hh; best = p; }
            double dx = x - tx, dy = y - ty, dz = z - tz;
            if (dx * dx + dy * dy + dz * dz <= reach * reach) { best = p; break; }
            expanded++;
            int gc = g.get(p);
            for (int[] d : dirs) {
                int nx = x + d[0], nz = z + d[1];
                if (!w.isLoadedBlock(nx, nz)) continue;
                int ny = Integer.MIN_VALUE;
                if (standable(w, nx, y, nz, height)) ny = y;
                else if (standable(w, nx, y + 1, nz, height) && passable(w, x, y + height, z)) ny = y + 1;
                else {
                    for (int drop = 1; drop <= 3; drop++) {
                        if (!passable(w, nx, y - drop + height, nz) && drop == 1) break;
                        if (standable(w, nx, y - drop, nz, height)) { ny = y - drop; break; }
                        if (!passable(w, nx, y - drop, nz)) break;
                    }
                }
                if (ny == Integer.MIN_VALUE) continue;
                long np = mc.core.World.packPos(nx, ny, nz);
                int cost = gc + 10 + (ny != y ? 4 : 0) + (Blocks.isWater(w.getBlock(nx, ny, nz)) ? 8 : 0);
                Integer old = g.get(np);
                if (old != null && old <= cost) continue;
                g.put(np, cost);
                parent.put(np, p);
                open.add(new long[]{cost + h(nx, ny, nz, tx, ty, tz), np});
            }
        }
        if (best == start) return null;
        LinkedList<int[]> nodes = new LinkedList<>();
        long p = best;
        while (p != start) {
            nodes.addFirst(new int[]{mc.core.World.unpackX(p), mc.core.World.unpackY(p), mc.core.World.unpackZ(p)});
            p = parent.get(p);
        }
        return new Path(new ArrayList<>(nodes));
    }

    private static int h(int x, int y, int z, int tx, int ty, int tz) {
        return (Math.abs(x - tx) + Math.abs(y - ty) + Math.abs(z - tz)) * 10;
    }
}
