package mc.entity;

import mc.content.Block;
import mc.content.Blocks;
import mc.core.World;
import mc.util.AABB;

import java.util.*;

/** Shared physics free functions (Section 2.5, Appendix AB.4): axis-by-axis collision against block boxes with
 * step-up, operating on any Entity body. */
public final class Physics {
    private Physics() {}

    /** Collision boxes of a block state at a position (unit cube unless the shape says otherwise). */
    public static void boxes(int state, int x, int y, int z, List<AABB> out) {
        if (!Blocks.solidCollision[state]) return;
        Block b = Blocks.block(state);
        switch (b.shape) {
            case Block.SHAPE_SLAB: out.add(new AABB(x, y, z, x + 1, y + 0.5, z + 1)); return;
            case Block.SHAPE_FARMLAND: out.add(new AABB(x, y, z, x + 1, y + 0.9375, z + 1)); return;
            case Block.SHAPE_CACTUS: out.add(new AABB(x + 0.0625, y, z + 0.0625, x + 0.9375, y + 0.9375, z + 0.9375)); return;
            case Block.SHAPE_BED: out.add(new AABB(x, y, z, x + 1, y + 0.5625, z + 1)); return;
            case Block.SHAPE_LADDER: {
                String f = Blocks.value(state, "facing");
                double t = 0.1875;
                if ("north".equals(f)) out.add(new AABB(x, y, z + 1 - t, x + 1, y + 1, z + 1));
                else if ("south".equals(f)) out.add(new AABB(x, y, z, x + 1, y + 1, z + t));
                else if ("west".equals(f)) out.add(new AABB(x + 1 - t, y, z, x + 1, y + 1, z + 1));
                else out.add(new AABB(x, y, z, x + t, y + 1, z + 1));
                return;
            }
            case Block.SHAPE_CROSS: case Block.SHAPE_TORCH: case Block.SHAPE_CROP: case Block.SHAPE_FLUID: case Block.SHAPE_PORTAL:
            case Block.SHAPE_NONE: case Block.SHAPE_SNOW:
                return;
            default:
                if (b.id.equals("minecraft:chest")) out.add(new AABB(x + 0.0625, y, z + 0.0625, x + 0.9375, y + 0.875, z + 0.9375));
                else out.add(new AABB(x, y, z, x + 1, y + 1, z + 1));
        }
    }

    public static List<AABB> collect(World w, AABB area) {
        List<AABB> l = new ArrayList<>();
        int x0 = (int) Math.floor(area.minX), x1 = (int) Math.floor(area.maxX);
        int y0 = (int) Math.floor(area.minY) - 1, y1 = (int) Math.floor(area.maxY);
        int z0 = (int) Math.floor(area.minZ), z1 = (int) Math.floor(area.maxZ);
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                boolean loaded = w.isLoaded(x >> 4, z >> 4);
                for (int y = y0; y <= y1; y++) {
                    if (!loaded) { l.add(new AABB(x, y, z, x + 1, y + 1, z + 1)); continue; } // unloaded = wall
                    if (y < 0 || y > 255) continue;
                    boxes(w.getBlock(x, y, z), x, y, z, l);
                }
            }
        return l;
    }

    public static boolean collides(World w, AABB box) {
        for (AABB b : collect(w, box)) if (b.intersects(box)) return true;
        return false;
    }

    /** Move with collisions resolved x, z, y (Appendix AB.4), with sneak-edge safety and 0.6 step-up. */
    public static void move(Entity e, double dx, double dy, double dz) {
        if (e.noClip) {
            e.x += dx; e.y += dy; e.z += dz;
            return;
        }
        if (e.inWeb) {
            dx *= 0.25; dy *= 0.05; dz *= 0.25;
            e.vx = e.vy = e.vz = 0;
        }
        // sneaking edge safety
        if (e.onGround && e.sneakSafe) {
            double step = 0.05;
            while (dx != 0 && !collides(e.world, e.bbox().offset(dx, -1.0, 0))) {
                if (Math.abs(dx) < step) dx = 0; else dx -= Math.signum(dx) * step;
            }
            while (dz != 0 && !collides(e.world, e.bbox().offset(0, -1.0, dz))) {
                if (Math.abs(dz) < step) dz = 0; else dz -= Math.signum(dz) * step;
            }
            while (dx != 0 && dz != 0 && !collides(e.world, e.bbox().offset(dx, -1.0, dz))) {
                if (Math.abs(dx) < step) dx = 0; else dx -= Math.signum(dx) * step;
                if (Math.abs(dz) < step) dz = 0; else dz -= Math.signum(dz) * step;
            }
        }
        double odx = dx, ody = dy, odz = dz;
        AABB box = e.bbox();
        List<AABB> boxes = collect(e.world, box.expand(dx, dy, dz));
        // Appendix AB.4: resolve x, then z, then y
        for (AABB b : boxes) dx = b.clipX(box, dx);
        box = box.offset(dx, 0, 0);
        for (AABB b : boxes) dz = b.clipZ(box, dz);
        box = box.offset(0, 0, dz);
        for (AABB b : boxes) dy = b.clipY(box, dy);
        box = box.offset(0, dy, 0);
        boolean grounded = e.onGround || (ody != dy && ody < 0);
        // step-up (max 0.6 blocks) when horizontally blocked
        if (e.stepHeight > 0 && grounded && (dx != odx || dz != odz)) {
            AABB start = e.bbox();
            double sx = odx, sy = e.stepHeight, sz = odz;
            List<AABB> sb = collect(e.world, start.expand(odx, e.stepHeight, odz));
            AABB b2 = start;
            for (AABB b : sb) sy = b.clipY(b2, sy);
            b2 = b2.offset(0, sy, 0);
            for (AABB b : sb) sx = b.clipX(b2, sx);
            b2 = b2.offset(sx, 0, 0);
            for (AABB b : sb) sz = b.clipZ(b2, sz);
            b2 = b2.offset(0, 0, sz);
            double down = -sy;
            for (AABB b : sb) down = b.clipY(b2, down);
            b2 = b2.offset(0, down, 0);
            if (sx * sx + sz * sz > dx * dx + dz * dz + 1e-7) {
                box = b2;
                dx = sx; dz = sz; dy = sy + down;
            }
        }
        e.x = (box.minX + box.maxX) / 2;
        e.y = box.minY;
        e.z = (box.minZ + box.maxZ) / 2;
        e.horizontalCollision = dx != odx || dz != odz;
        e.verticalCollision = ody != dy;
        e.onGround = ody != dy && ody < 0;
        if (dx != odx) e.vx = 0;
        if (dz != odz) e.vz = 0;
        if (ody != dy) e.vy = 0;
    }
}
