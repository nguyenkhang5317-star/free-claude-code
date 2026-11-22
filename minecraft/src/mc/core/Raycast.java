package mc.core;

import mc.content.*;
import mc.entity.Physics;
import mc.util.AABB;

import java.util.ArrayList;
import java.util.List;

/** Voxel DDA ray cast against block outline shapes (Section 3.4 block targeting). */
public final class Raycast {
    private Raycast() { }

    public static final class Hit {
        public final int x, y, z, face; // face: 0 down 1 up 2 north 3 south 4 west 5 east
        public final double hx, hy, hz, dist;

        Hit(int x, int y, int z, int face, double hx, double hy, double hz, double dist) {
            this.x = x; this.y = y; this.z = z; this.face = face; this.hx = hx; this.hy = hy; this.hz = hz; this.dist = dist;
        }

        public int adjX() { return x + (face == 4 ? -1 : face == 5 ? 1 : 0); }

        public int adjY() { return y + (face == 0 ? -1 : face == 1 ? 1 : 0); }

        public int adjZ() { return z + (face == 2 ? -1 : face == 3 ? 1 : 0); }
    }

    /** Selection boxes: fluids and air are not targetable; plants use a reduced box. */
    public static void outline(int s, int x, int y, int z, List<AABB> out) {
        Block b = Blocks.block(s);
        if (s == Blocks.AIR || Blocks.fluid[s] || b.shape == Block.SHAPE_NONE) return;
        switch (b.shape) {
            case Block.SHAPE_CROSS: case Block.SHAPE_CROP:
                out.add(new AABB(x + 0.15, y, z + 0.15, x + 0.85, y + (b.shape == Block.SHAPE_CROP ? 0.25 + 0.1 * Blocks.prop(s, "age") : 0.8), z + 0.85));
                return;
            case Block.SHAPE_TORCH: out.add(new AABB(x + 0.4, y, z + 0.4, x + 0.6, y + 0.6, z + 0.6)); return;
            case Block.SHAPE_PORTAL: return;
            default:
                int n = out.size();
                Physics.boxes(s, x, y, z, out);
                if (out.size() == n) out.add(new AABB(x, y, z, x + 1, y + 1, z + 1));
        }
    }

    public static Hit cast(IChunkAccess w, double ox, double oy, double oz, double dx, double dy, double dz, double max) {
        int x = (int) Math.floor(ox), y = (int) Math.floor(oy), z = (int) Math.floor(oz);
        int sx = dx > 0 ? 1 : -1, sy = dy > 0 ? 1 : -1, sz = dz > 0 ? 1 : -1;
        double tdx = dx == 0 ? 1e30 : Math.abs(1 / dx), tdy = dy == 0 ? 1e30 : Math.abs(1 / dy), tdz = dz == 0 ? 1e30 : Math.abs(1 / dz);
        double tx = dx == 0 ? 1e30 : (dx > 0 ? (x + 1 - ox) : (ox - x)) * tdx;
        double ty = dy == 0 ? 1e30 : (dy > 0 ? (y + 1 - oy) : (oy - y)) * tdy;
        double tz = dz == 0 ? 1e30 : (dz > 0 ? (z + 1 - oz) : (oz - z)) * tdz;
        List<AABB> boxes = new ArrayList<>();
        for (int i = 0; i < 256; i++) {
            int s = w.getBlock(x, y, z);
            if (s != Blocks.AIR) {
                boxes.clear();
                outline(s, x, y, z, boxes);
                double best = 1e30;
                int bf = -1;
                for (AABB b : boxes) {
                    double t = b.rayHit(ox, oy, oz, dx, dy, dz, max);
                    if (t >= 0 && t < best) { best = t; bf = faceOf(b, ox + dx * t, oy + dy * t, oz + dz * t); }
                }
                if (bf >= 0 && best <= max) return new Hit(x, y, z, bf, ox + dx * best, oy + dy * best, oz + dz * best, best);
            }
            double t = Math.min(tx, Math.min(ty, tz));
            if (t > max) return null;
            if (tx <= ty && tx <= tz) { x += sx; tx += tdx; }
            else if (ty <= tz) { y += sy; ty += tdy; }
            else { z += sz; tz += tdz; }
        }
        return null;
    }

    private static int faceOf(AABB b, double hx, double hy, double hz) {
        double e = 1e-5;
        if (Math.abs(hy - b.minY) < e) return 0;
        if (Math.abs(hy - b.maxY) < e) return 1;
        if (Math.abs(hz - b.minZ) < e) return 2;
        if (Math.abs(hz - b.maxZ) < e) return 3;
        if (Math.abs(hx - b.minX) < e) return 4;
        if (Math.abs(hx - b.maxX) < e) return 5;
        return 1;
    }

    /** Line of sight test against opaque full blocks (explosions, mob sight). */
    public static boolean blocked(World w, double ax, double ay, double az, double bx, double by, double bz) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6) return false;
        int steps = (int) Math.ceil(len * 4);
        int lx = Integer.MIN_VALUE, ly = 0, lz = 0;
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            int x = (int) Math.floor(ax + dx * t), y = (int) Math.floor(ay + dy * t), z = (int) Math.floor(az + dz * t);
            if (x == lx && y == ly && z == lz) continue;
            lx = x; ly = y; lz = z;
            int s = w.getBlock(x, y, z);
            if (Blocks.opaqueCube[s]) return true;
        }
        return false;
    }
}
