package mc.core;

import mc.content.*;
import mc.gen.Features;

/** Random-tick behaviors referenced by blocks.json "random_tick" (Appendix BQ). */
public final class RandomTicks {
    private RandomTicks() { }

    public static void tick(World w, String kind, int x, int y, int z, int s) {
        switch (kind) {
            case "grass_spread": grass(w, x, y, z); break;
            case "leaf_decay": leaves(w, x, y, z); break;
            case "sapling_grow": sapling(w, x, y, z, s); break;
            case "crop_grow": crop(w, x, y, z, s); break;
            case "farmland": farmland(w, x, y, z, s); break;
            case "cactus_grow": case "cane_grow": column(w, x, y, z, s); break;
            default: break;
        }
    }

    private static void grass(World w, int x, int y, int z) {
        int above = w.getBlock(x, y + 1, z);
        int light = w.getRawBrightness(x, y + 1, z);
        if (Blocks.opacity[above] >= 15 || (light < 4 && Blocks.opacity[above] > 2)) {
            w.setBlock(x, y, z, Blocks.DIRT);
            return;
        }
        if (light < 9) return;
        for (int i = 0; i < 4; i++) {
            int nx = x + w.rng.nextInt(3) - 1, ny = y + w.rng.nextInt(5) - 3, nz = z + w.rng.nextInt(3) - 1;
            if (w.getBlock(nx, ny, nz) != Blocks.DIRT) continue;
            int a = w.getBlock(nx, ny + 1, nz);
            if (Blocks.opacity[a] <= 2 && !Blocks.fluid[a] && w.getRawBrightness(nx, ny + 1, nz) >= 4) w.setBlock(nx, ny, nz, Blocks.GRASS_BLOCK);
        }
    }

    private static boolean isLog(int s) {
        Block b = Blocks.block(s);
        return b.id.endsWith("_log") || b.id.endsWith("_stem");
    }

    /** Leaves without a log within 6 blocks (taxicab, through leaves) decay. */
    private static void leaves(World w, int x, int y, int z) {
        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
        java.util.HashSet<Long> seen = new java.util.HashSet<>();
        q.add(new int[]{x, y, z, 0});
        seen.add(World.packPos(x, y, z));
        int[][] n6 = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int[] d : n6) {
                int nx = c[0] + d[0], ny = c[1] + d[1], nz = c[2] + d[2];
                int s = w.getBlock(nx, ny, nz);
                if (isLog(s)) return;
                if (c[3] + 1 >= 6 || !Blocks.block(s).id.endsWith("_leaves")) continue;
                if (seen.add(World.packPos(nx, ny, nz))) q.add(new int[]{nx, ny, nz, c[3] + 1});
            }
        }
        if (!w.isLoadedBlock(x + 7, z + 7) || !w.isLoadedBlock(x - 7, z - 7)) return;
        w.destroyBlock(x, y, z, true, null, null);
    }

    private static void sapling(World w, int x, int y, int z, int s) {
        if (w.getRawBrightness(x, y + 1, z) < 9 || w.rng.nextInt(7) != 0) return;
        String wood = Blocks.block(s).id.replace("minecraft:", "").replace("_sapling", "");
        w.setBlock(x, y, z, Blocks.AIR, World.SEND);
        if (!Features.growTree(w, wood, x, y, z, w.rng)) w.setBlock(x, y, z, s, World.SEND);
    }

    private static void crop(World w, int x, int y, int z, int s) {
        int age = Blocks.prop(s, "age");
        if (age >= 7 || w.getRawBrightness(x, y + 1, z) < 9) return;
        int below = w.getBlock(x, y - 1, z);
        float f = Blocks.is(below, Blocks.FARMLAND) && Blocks.prop(below, "moisture") > 0 ? 3f : 1f;
        if (w.rng.nextInt((int) (25f / f) + 1) == 0) w.setBlock(x, y, z, Blocks.with(s, "age", age + 1));
    }

    private static void farmland(World w, int x, int y, int z, int s) {
        boolean water = false;
        for (int dx = -4; dx <= 4 && !water; dx++)
            for (int dz = -4; dz <= 4 && !water; dz++)
                for (int dy = 0; dy <= 1 && !water; dy++) if (Blocks.isWater(w.getBlock(x + dx, y + dy, z + dz))) water = true;
        int m = Blocks.prop(s, "moisture");
        if (water || w.isRainingAt(x, y + 1, z)) {
            if (m < 7) w.setBlock(x, y, z, Blocks.with(s, "moisture", 7));
        } else if (m > 0) {
            w.setBlock(x, y, z, Blocks.with(s, "moisture", m - 1));
        } else {
            int above = w.getBlock(x, y + 1, z);
            if (Blocks.block(above).shape != Block.SHAPE_CROP) w.setBlock(x, y, z, Blocks.DIRT);
        }
    }

    private static void column(World w, int x, int y, int z, int s) {
        if (w.getBlock(x, y + 1, z) != Blocks.AIR) return;
        int h = 1;
        while (h < 3 && Blocks.block(w.getBlock(x, y - h, z)) == Blocks.block(s)) h++;
        if (h >= 3 || w.rng.nextInt(16) != 0) return;
        if (w.isSupported(x, y + 1, z, s)) w.setBlock(x, y + 1, z, s);
    }
}
