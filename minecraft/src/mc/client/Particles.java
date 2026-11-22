package mc.client;

import mc.content.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Client-only cosmetic particles (block break debris, smoke, explosion, crit, hearts, happy villager, rain splashes). */
public final class Particles {
    static final class P {
        double x, y, z, vx, vy, vz;
        int age, life, tex, tint;
        float size, u0, v0, us;
        boolean gravity, collide, emissive;
    }

    final List<P> list = new ArrayList<>();
    final Random rnd = new Random();
    static final int MAX = 2000;

    public void spawn(ClientWorld w, String kind, double x, double y, double z, int count, int data) {
        for (int i = 0; i < count && list.size() < MAX; i++) {
            P p = new P();
            p.x = x; p.y = y; p.z = z;
            p.tint = BlockColors.IDENTITY;
            p.size = 0.1f;
            p.us = 16;
            switch (kind) {
                case "block": {
                    int st = data;
                    p.tex = Mesher.faceTex(st, Blocks.block(st), 2);
                    if (Blocks.block(st).shape != mc.content.Block.SHAPE_CUBE) p.tex = Textures.id(Blocks.block(st).texSide);
                    p.x += (rnd.nextDouble() - 0.5) * 0.8; p.y += (rnd.nextDouble() - 0.5) * 0.8; p.z += (rnd.nextDouble() - 0.5) * 0.8;
                    p.vx = (rnd.nextDouble() - 0.5) * 0.2; p.vy = rnd.nextDouble() * 0.2; p.vz = (rnd.nextDouble() - 0.5) * 0.2;
                    p.u0 = rnd.nextInt(12); p.v0 = rnd.nextInt(12); p.us = 4;
                    p.size = 0.08f + rnd.nextFloat() * 0.06f;
                    p.life = 20 + rnd.nextInt(20); p.gravity = true; p.collide = true;
                    break;
                }
                case "explosion": {
                    p.tex = TexTable.WHITE; p.tint = 0xFF707070;
                    double a = rnd.nextDouble() * Math.PI * 2, r = rnd.nextDouble() * 3;
                    p.x += Math.cos(a) * r; p.z += Math.sin(a) * r; p.y += rnd.nextDouble() * 2;
                    p.vy = 0.05; p.size = 0.6f + rnd.nextFloat(); p.life = 10 + rnd.nextInt(10);
                    break;
                }
                case "smoke":
                    p.tex = TexTable.WHITE; p.tint = 0xFF404040; p.vy = 0.04; p.size = 0.15f; p.life = 16;
                    p.vx = (rnd.nextDouble() - 0.5) * 0.02; p.vz = (rnd.nextDouble() - 0.5) * 0.02;
                    break;
                case "crit":
                    p.tex = TexTable.WHITE; p.tint = 0xFFE0E0FF; p.emissive = true;
                    p.vx = (rnd.nextDouble() - 0.5) * 0.5; p.vy = rnd.nextDouble() * 0.4; p.vz = (rnd.nextDouble() - 0.5) * 0.5;
                    p.size = 0.07f; p.life = 12; p.gravity = true;
                    break;
                case "heart":
                    p.tex = TexTable.WHITE; p.tint = 0xFFFF2040; p.emissive = true; p.vy = 0.03; p.size = 0.2f; p.life = 20;
                    break;
                case "happy":
                    p.tex = TexTable.WHITE; p.tint = 0xFF40FF60; p.emissive = true;
                    p.x += (rnd.nextDouble() - 0.5); p.y += (rnd.nextDouble() - 0.5); p.z += (rnd.nextDouble() - 0.5);
                    p.size = 0.08f; p.life = 20;
                    break;
                case "splash":
                    p.tex = Textures.id("water"); p.us = 2; p.size = 0.06f; p.life = 6; p.gravity = true;
                    p.vx = (rnd.nextDouble() - 0.5) * 0.1; p.vy = 0.1; p.vz = (rnd.nextDouble() - 0.5) * 0.1;
                    break;
                default:
                    p.tex = TexTable.WHITE; p.size = 0.1f; p.life = 10;
            }
            list.add(p);
        }
    }

    public void tick(ClientWorld w) {
        for (int i = list.size() - 1; i >= 0; i--) {
            P p = list.get(i);
            if (++p.age >= p.life) { list.set(i, list.get(list.size() - 1)); list.remove(list.size() - 1); continue; }
            if (p.gravity) p.vy -= 0.04;
            double nx = p.x + p.vx, ny = p.y + p.vy, nz = p.z + p.vz;
            if (p.collide && Blocks.solidCollision[w.getBlock((int) Math.floor(nx), (int) Math.floor(ny), (int) Math.floor(nz))]) {
                p.vx *= 0.3; p.vz *= 0.3; p.vy = 0;
                ny = p.y;
            }
            p.x = nx; p.y = ny; p.z = nz;
            p.vx *= 0.98; p.vy *= 0.98; p.vz *= 0.98;
        }
    }

    public int size() { return list.size(); }
}
