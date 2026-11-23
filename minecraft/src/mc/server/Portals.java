package mc.server;

import mc.content.*;
import mc.core.*;
import mc.entity.*;

/** Nether portal travel (Section 5.4/5.6): coordinates scale by 8, search for an existing portal within 16 blocks of
 * the scaled position, otherwise build a 4x5 obsidian frame with a small platform. */
public final class Portals {
    private Portals() { }

    public static void travel(IntegratedServer s, Player p) {
        World from = p.world;
        Dimension td = from.dim == Dimension.NETHER ? Dimension.OVERWORLD : Dimension.NETHER;
        World to = s.world(td);
        double scale = from.dim.coordinateScale / td.coordinateScale;
        int tx = (int) Math.floor(p.x * scale), tz = (int) Math.floor(p.z * scale);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) to.loadNow((tx >> 4) + dx, (tz >> 4) + dz);
        int[] found = findPortal(to, tx, tz);
        if (found == null) found = buildPortal(to, tx, tz);
        from.removePlayer(p);
        p.world = to;
        p.setPos(found[0] + 0.5, found[1], found[2] + 0.5);
        p.vx = p.vy = p.vz = 0;
        p.portalCooldown = 300;
        p.fallDistance = 0;
        to.addEntity(p);
        s.resendAll();
        from.playSound("block.glass.break", p.x, p.y, p.z, 0.5f, 1f);
    }

    private static int[] findPortal(World w, int x, int z) {
        int top = w.dim.hasCeiling ? 126 : 255;
        for (int r = 0; r <= 16; r++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    for (int y = 1; y < top; y++) {
                        if (Blocks.is(w.getBlock(x + dx, y, z + dz), Blocks.NETHER_PORTAL) && !Blocks.is(w.getBlock(x + dx, y - 1, z + dz), Blocks.NETHER_PORTAL))
                            return new int[]{x + dx, y, z + dz};
                    }
                }
        return null;
    }

    private static int[] buildPortal(World w, int x, int z) {
        int top = w.dim.hasCeiling ? 120 : 250;
        int bestY = -1;
        // look for a floor with 5 blocks of headroom
        for (int y = top; y > 8 && bestY < 0; y--) {
            boolean ok = Blocks.solidCollision[w.getBlock(x, y - 1, z)] || Blocks.isLava(w.getBlock(x, y - 1, z));
            for (int k = 0; k < 5 && ok; k++) if (Blocks.solidCollision[w.getBlock(x, y + k, z)] || Blocks.isLava(w.getBlock(x, y + k, z))) ok = false;
            if (ok) bestY = y;
        }
        if (bestY < 0) bestY = w.dim.hasCeiling ? 70 : 80;
        int y = bestY;
        // platform and clearance (axis x: frame spans x-1..x+2)
        for (int dx = -2; dx <= 3; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                w.setBlock(x + dx, y - 1, z + dz, Blocks.OBSIDIAN, World.SEND);
                for (int k = 0; k < 5; k++) w.setBlock(x + dx, y + k, z + dz, Blocks.AIR, World.SEND);
            }
        for (int dx = -1; dx <= 2; dx++) {
            w.setBlock(x + dx, y - 1, z, Blocks.OBSIDIAN, World.SEND);
            w.setBlock(x + dx, y + 3, z, Blocks.OBSIDIAN, World.SEND);
        }
        for (int k = 0; k < 3; k++) {
            w.setBlock(x - 1, y + k, z, Blocks.OBSIDIAN, World.SEND);
            w.setBlock(x + 2, y + k, z, Blocks.OBSIDIAN, World.SEND);
        }
        int ps = Blocks.with(Blocks.NETHER_PORTAL, "axis", "x");
        for (int dx = 0; dx <= 1; dx++) for (int k = 0; k < 3; k++) w.setBlock(x + dx, y + k, z, ps, World.SEND);
        return new int[]{x, y, z};
    }
}
