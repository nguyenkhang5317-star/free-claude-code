package mc.util;

/** Axis-aligned bounding box. */
public final class AABB {
    public double minX, minY, minZ, maxX, maxY, maxZ;

    public AABB(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
    }

    public AABB copy() { return new AABB(minX, minY, minZ, maxX, maxY, maxZ); }

    public AABB offset(double x, double y, double z) {
        return new AABB(minX + x, minY + y, minZ + z, maxX + x, maxY + y, maxZ + z);
    }

    public AABB expand(double x, double y, double z) {
        double a = minX, b = minY, c = minZ, d = maxX, e = maxY, f = maxZ;
        if (x < 0) a += x; else d += x;
        if (y < 0) b += y; else e += y;
        if (z < 0) c += z; else f += z;
        return new AABB(a, b, c, d, e, f);
    }

    public AABB grow(double g) { return new AABB(minX - g, minY - g, minZ - g, maxX + g, maxY + g, maxZ + g); }

    public boolean intersects(AABB o) {
        return o.maxX > minX && o.minX < maxX && o.maxY > minY && o.minY < maxY && o.maxZ > minZ && o.minZ < maxZ;
    }

    public double clipX(AABB o, double dx) {
        if (o.maxY <= minY || o.minY >= maxY || o.maxZ <= minZ || o.minZ >= maxZ) return dx;
        if (dx > 0 && o.maxX <= minX) { double m = minX - o.maxX; if (m < dx) dx = m; }
        else if (dx < 0 && o.minX >= maxX) { double m = maxX - o.minX; if (m > dx) dx = m; }
        return dx;
    }

    public double clipY(AABB o, double dy) {
        if (o.maxX <= minX || o.minX >= maxX || o.maxZ <= minZ || o.minZ >= maxZ) return dy;
        if (dy > 0 && o.maxY <= minY) { double m = minY - o.maxY; if (m < dy) dy = m; }
        else if (dy < 0 && o.minY >= maxY) { double m = maxY - o.minY; if (m > dy) dy = m; }
        return dy;
    }

    public double clipZ(AABB o, double dz) {
        if (o.maxX <= minX || o.minX >= maxX || o.maxY <= minY || o.minY >= maxY) return dz;
        if (dz > 0 && o.maxZ <= minZ) { double m = minZ - o.maxZ; if (m < dz) dz = m; }
        else if (dz < 0 && o.minZ >= maxZ) { double m = maxZ - o.minZ; if (m > dz) dz = m; }
        return dz;
    }

    /** Ray intersection; returns t of entry or -1. */
    public double rayHit(double ox, double oy, double oz, double dx, double dy, double dz, double maxT) {
        double t0 = 0, t1 = maxT;
        double[] mn = {minX, minY, minZ}, mx = {maxX, maxY, maxZ}, o = {ox, oy, oz}, d = {dx, dy, dz};
        for (int a = 0; a < 3; a++) {
            if (Math.abs(d[a]) < 1e-9) {
                if (o[a] < mn[a] || o[a] > mx[a]) return -1;
            } else {
                double ta = (mn[a] - o[a]) / d[a], tb = (mx[a] - o[a]) / d[a];
                if (ta > tb) { double t = ta; ta = tb; tb = t; }
                if (ta > t0) t0 = ta;
                if (tb < t1) t1 = tb;
                if (t0 > t1) return -1;
            }
        }
        return t0;
    }

    public double centerX() { return (minX + maxX) / 2; }
    public double centerZ() { return (minZ + maxZ) / 2; }
}
