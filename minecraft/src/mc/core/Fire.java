package mc.core;

import mc.content.*;

/** Fire spread and burnout (Section 5.5): uses flammable = [encouragement, flammability] from blocks.json. */
public final class Fire {
    private Fire() { }

    private static final int[][] N6 = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    public static void tick(World w, int x, int y, int z) {
        if (!w.level.rule("doFireTick")) return;
        int below = w.getBlock(x, y - 1, z);
        boolean infinite = below == Blocks.NETHERRACK || below == Blocks.SOUL_SAND || below == Blocks.SOUL_SOIL;
        if (!infinite && w.isRainingAt(x, y, z) && w.rng.nextFloat() < 0.6f) {
            w.setBlock(x, y, z, Blocks.AIR);
            w.playSound("block.fire.extinguish", x + 0.5, y + 0.5, z + 0.5, 0.5f, 2.6f);
            return;
        }
        boolean fuel = false;
        for (int[] d : N6) if (Blocks.block(w.getBlock(x + d[0], y + d[1], z + d[2])).flameEncouragement > 0) fuel = true;
        if (!infinite) {
            if (!fuel && !Blocks.opaqueCube[below]) { w.setBlock(x, y, z, Blocks.AIR); return; }
            if (!fuel && w.rng.nextInt(4) == 0) { w.setBlock(x, y, z, Blocks.AIR); return; }
            if (w.rng.nextInt(12) == 0) { w.setBlock(x, y, z, Blocks.AIR); return; }
        }
        // burn neighbors
        for (int[] d : N6) {
            int nx = x + d[0], ny = y + d[1], nz = z + d[2];
            int s = w.getBlock(nx, ny, nz);
            int flam = Blocks.block(s).flammability;
            if (flam > 0 && w.rng.nextInt(d[1] != 0 ? 250 : 300) < flam) {
                if (s == Blocks.TNT) {
                    w.setBlock(nx, ny, nz, Blocks.AIR);
                    mc.entity.PrimedTnt t = new mc.entity.PrimedTnt(w, null);
                    t.setPos(nx + 0.5, ny, nz + 0.5);
                    w.addEntity(t);
                } else {
                    w.setBlock(nx, ny, nz, w.rng.nextInt(2) == 0 ? Blocks.FIRE : Blocks.AIR, World.SEND | World.NOTIFY | World.NO_DROP_BE);
                }
            }
        }
        // spread to air next to flammable blocks
        for (int i = 0; i < 2; i++) {
            int nx = x + w.rng.nextInt(3) - 1, ny = y + w.rng.nextInt(4) - 1, nz = z + w.rng.nextInt(3) - 1;
            if (w.getBlock(nx, ny, nz) != Blocks.AIR) continue;
            int enc = 0;
            for (int[] d : N6) enc = Math.max(enc, Blocks.block(w.getBlock(nx + d[0], ny + d[1], nz + d[2])).flameEncouragement);
            if (enc > 0 && w.rng.nextInt(100) < enc) w.setBlock(nx, ny, nz, Blocks.FIRE);
        }
        w.schedule(x, y, z, 30 + w.rng.nextInt(10));
    }
}
