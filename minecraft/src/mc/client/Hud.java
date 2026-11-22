package mc.client;

import mc.content.*;
import mc.net.S2C;

import java.util.Locale;

/** In-game overlay (Section 14.2): hotbar, vitals, XP, crosshair, chat, debug screen, first-person hand. */
public final class Hud {
    final GameClient c;
    int lastSelected = -1;
    long selectedAt;

    Hud(GameClient c) { this.c = c; }

    static final String[] HEART = {" kk   kk ", "krrk krrk", "krwrkrrrk", "krrrrrrrk", "krrrrrrrk", " krrrrrk ", "  krrrk  ", "   krk   ", "    k    "};
    static final String[] FOOD = {"     kkk ", "    kbbbk", "   kbbwbk", "  kbbbbbk", " kkbbbbk ", "kwk kbbk ", "kwwk kk  ", " kk      ", "         "};
    static final String[] ARMOR = {"kkkk kkkk", "kaaakaaak", "kaaaaaaak", " kaaaaak ", " kaaaaak ", " kaaaaak ", " kaaaaak ", " kkkkkkk ", "         "};
    static final String[] BUBBLE = {"  kkkkk  ", " kbbbbbk ", "kbwbbbbbk", "kbwbbbbbk", "kbbbbbbbk", "kbbbbbbbk", " kbbbbbk ", "  kkkkk  ", "         "};

    void icon(Gui g, String[] t, int x, int y, int fill, int highlight, int half, boolean empty) {
        for (int r = 0; r < t.length; r++)
            for (int col = 0; col < t[r].length(); col++) {
                char ch = t[r].charAt(col);
                if (ch == ' ') continue;
                int colr;
                if (ch == 'k') colr = 0xFF1A1A1A;
                else if (empty || (half == 1 && col > 4)) colr = 0xFF3A3A3A;
                else if (ch == 'w') colr = highlight;
                else colr = fill;
                g.rect(x + col, y + r, 1, 1, colr);
            }
    }

    void draw(Gui g, float partial) {
        S2C.PlayerState ps = c.ps;
        if (ps == null) return;
        int W = g.width, H = g.height;
        if (c.perspective == 0) hand(g, ps, partial);
        // crosshair
        if (c.perspective == 0 && c.screen == GameClient.Screen.NONE) {
            int cx = W / 2, cy = H / 2;
            g.rect(cx - 4, cy, 9, 1, 0xC0FFFFFF);
            g.rect(cx, cy - 4, 1, 9, 0xC0FFFFFF);
            if (ps.attackStrength() < 1 && ps.gameMode() != 3) {
                g.rect(cx - 8, cy + 9, 16, 2, 0xC0303030);
                g.rect(cx - 8, cy + 9, (int) (16 * ps.attackStrength()), 2, 0xE0FFFFFF);
            }
        }
        boolean survival = ps.gameMode() == 0 || ps.gameMode() == 2;
        int hx = W / 2 - 91, hy = H - 22;
        if (ps.gameMode() != 3) {
            // hotbar
            g.rect(hx, hy, 182, 22, 0x90000000);
            g.rect(hx, hy, 182, 1, 0xFF5A5A5A);
            for (int i = 0; i < 9; i++) {
                int x = hx + 1 + i * 20;
                g.rect(x + 1, hy + 2, 18, 18, 0x60404040);
                g.stack(ps.main()[i], x + 2, hy + 3);
            }
            int sx = hx - 1 + ps.selected() * 20;
            g.rect(sx, hy - 1, 24, 3, 0xFFE8E8E8);
            g.rect(sx, hy + 20, 24, 3, 0xFFE8E8E8);
            g.rect(sx, hy - 1, 3, 24, 0xFFE8E8E8);
            g.rect(sx + 21, hy - 1, 3, 24, 0xFFE8E8E8);
            if (ps.offhand() != null) {
                g.rect(hx - 29, hy, 22, 22, 0x90000000);
                g.stack(ps.offhand(), hx - 26, hy + 3);
            }
            if (ps.selected() != lastSelected) { lastSelected = ps.selected(); selectedAt = System.currentTimeMillis(); }
            long age = System.currentTimeMillis() - selectedAt;
            ItemStack held = ps.main()[ps.selected()];
            if (held != null && age < 2000) {
                int a = (int) Math.min(255, (2000 - age) / 2000.0 * 255 * 2);
                g.centered(held.item.name, W / 2, hy - (survival ? 46 : 18), (a << 24) | 0xFFFFFF);
            }
        }
        if (survival) {
            // xp bar
            int xy = H - 29;
            g.rect(hx, xy, 182, 5, 0xFF202020);
            g.rect(hx + 1, xy + 1, 180, 3, 0xFF3A3A3A);
            g.rect(hx + 1, xy + 1, (int) (180 * ps.xpProgress()), 3, 0xFF80FF20);
            if (ps.xpLevel() > 0) {
                String lv = String.valueOf(ps.xpLevel());
                int tx = W / 2 - g.textWidth(lv) / 2;
                g.text(lv, tx + 1, xy - 9, 0x000000, false);
                g.text(lv, tx - 1, xy - 9, 0x000000, false);
                g.text(lv, tx, xy - 8, 0x000000, false);
                g.text(lv, tx, xy - 10, 0x000000, false);
                g.text(lv, tx, xy - 9, 0x80FF20, false);
            }
            // hearts
            int heartsY = H - 39;
            float hp = ps.health();
            boolean blink = ps.hurtTime() > 0 && (ps.hurtTime() / 3) % 2 == 0;
            for (int i = 0; i < 10; i++) {
                int x = hx + i * 8;
                int y = heartsY + (hp <= 4 && (c.clientTicks + i * 3) % 6 < 2 ? -1 : 0);
                icon(g, HEART, x, y, 0, 0, 0, true);
                float v = hp - i * 2;
                int fill = blink ? 0xFFFFFFFF : 0xFFD81818;
                if (v >= 2) icon(g, HEART, x, y, fill, 0xFFFF9A9A, 0, false);
                else if (v >= 1) icon(g, HEART, x, y, fill, 0xFFFF9A9A, 1, false);
            }
            if (ps.absorption() > 0) for (int i = 0; i < Math.ceil(ps.absorption() / 2); i++) icon(g, HEART, hx + i * 8, heartsY - 10, 0xFFE8C020, 0xFFFFF0A0, 0, false);
            // armor
            if (ps.armor() > 0) {
                for (int i = 0; i < 10; i++) {
                    int v = ps.armor() - i * 2;
                    icon(g, ARMOR, hx + i * 8, heartsY - 10, 0xFFD0D0D0, 0xFFFFFFFF, v >= 2 ? 0 : v == 1 ? 1 : 0, v <= 0);
                }
            }
            // food
            int food = ps.food();
            for (int i = 0; i < 10; i++) {
                int x = hx + 182 - 9 - i * 8;
                int y = heartsY + (ps.saturation() <= 0 && (c.clientTicks + i * 5) % 20 < 2 ? -1 : 0);
                int v = food - i * 2;
                boolean hunger = false;
                for (String e : ps.effects()) if (e.startsWith("hunger")) hunger = true;
                int col = hunger ? 0xFF6A8A30 : 0xFFA8642A;
                icon(g, FOOD, x, y, col, 0xFFF0E8D0, v == 1 ? 1 : 0, v <= 0);
                if (v <= 0) icon(g, FOOD, x, y, 0, 0, 0, true);
            }
            // air
            if (ps.submerged() || ps.air() < ps.maxAir()) {
                int bubbles = (int) Math.ceil(ps.air() * 10.0 / ps.maxAir());
                for (int i = 0; i < bubbles; i++) icon(g, BUBBLE, hx + 182 - 9 - i * 8, heartsY - 10, 0xFF2A70E8, 0xFFFFFFFF, 0, false);
            }
        }
        // effects
        int ey = 4;
        for (String e : ps.effects()) { g.text(e, W - g.textWidth(e) - 4, ey, 0xFFFFFF); ey += 10; }
        // sleeping fade
        if (ps.sleeping()) {
            int a = (int) Math.min(200, ps.sleepTimer() * 2.5);
            g.rect(0, 0, W, H, a << 24 | 0x101020);
        }
        chat(g, c.screen == GameClient.Screen.CHAT);
        if (c.showDebug) {
            // F3 is drawn at a smaller scale so the left and right columns never collide
            int s0 = g.s, dW = g.width * s0, dH = g.height * s0;
            g.begin(g.g, dW, dH, Math.max(1, s0 / 2));
            debug(g, ps);
            g.begin(g.g, dW, dH, s0);
        }
    }

    void chat(Gui g, boolean open) {
        int H = g.height;
        long now = System.currentTimeMillis();
        int y = H - 40 - (open ? 12 : 0);
        int shown = 0;
        synchronized (c.chat) {
            for (int i = c.chat.size() - 1; i >= 0 && shown < (open ? 20 : 10); i--) {
                String[] m = c.chat.get(i);
                long age = now - Long.parseLong(m[1]);
                if (!open && age > 10000) break;
                int a = open ? 255 : (int) Math.max(0, Math.min(255, (10000 - age) / 1000.0 * 255));
                if (a <= 8) continue;
                g.rect(2, y - 1, 320, 10, ((a / 2) << 24));
                g.text(m[0], 4, y, (a << 24) | 0xFFFFFF);
                y -= 10;
                shown++;
            }
        }
    }

    void debug(Gui g, S2C.PlayerState ps) {
        S2C.WorldInfo info = c.info;
        Renderer r = c.renderer;
        int bx = (int) Math.floor(ps.x()), by = (int) Math.floor(ps.y()), bz = (int) Math.floor(ps.z());
        String[] dirs = {"south (Towards positive Z)", "west (Towards negative X)", "north (Towards negative Z)", "east (Towards positive X)"};
        int dir = Math.floorMod(Math.round(c.yaw / 90f), 4);
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) >> 20, max = rt.maxMemory() >> 20;
        String[] left = {
                "Minecraft " + GameClient.VERSION + " (Rebuild/vanilla)",
                String.format(Locale.ROOT, "%.0f fps  T: %d  render %.1f ms (proj %.1f, raster %.1f)", c.fps, c.settings.renderDistance,
                        (r.projNanos + r.rasterNanos) / 1e6, r.projNanos / 1e6, r.rasterNanos / 1e6),
                String.format(Locale.ROOT, "Integrated server @ %.1f ms ticks, %d chunks loaded, %d pending", info == null ? 0 : info.tickMs(),
                        info == null ? 0 : info.loadedChunks(), info == null ? 0 : info.pendingChunks()),
                String.format(Locale.ROOT, "C: %d sections, %d quads, %d polys, meshing %d", r.sectionsDrawn, r.quadsDrawn, r.polys, c.world.meshQueue.get()),
                String.format(Locale.ROOT, "E: %d/%d  P: %d", c.entities.size(), info == null ? 0 : info.entityCount(), c.particles.size()),
                c.world.dimension,
                "",
                String.format(Locale.ROOT, "XYZ: %.3f / %.5f / %.3f", ps.x(), ps.y(), ps.z()),
                String.format("Block: %d %d %d", bx, by, bz),
                String.format("Chunk: %d %d %d in %d %d %d", bx & 15, by & 15, bz & 15, bx >> 4, by >> 4, bz >> 4),
                String.format(Locale.ROOT, "Facing: %s (%.1f / %.1f)", dirs[dir], Renderer.lerpAngle(0, c.yaw, 1), c.pitch),
                "Biome: " + (info == null ? "?" : info.biome()),
                String.format("Light: %d (%d sky, %d block)", Math.max(c.world.getSkyLight(bx, by, bz), c.world.getBlockLight(bx, by, bz)),
                        c.world.getSkyLight(bx, by, bz), c.world.getBlockLight(bx, by, bz)),
                info == null ? "" : String.format("Local Difficulty: %d  Day %d  Time %d  Moon %d", info.difficulty(), info.dayTime() / 24000, info.dayTime() % 24000, info.moonPhase()),
                info == null ? "" : "Seed: " + info.seed(),
        };
        int y = 2;
        for (String s : left) {
            if (!s.isEmpty()) {
                g.rect(1, y - 1, g.textWidth(s) + 3, 10, 0x90505050);
                g.text(s, 2, y, 0xE0E0E0, false);
            }
            y += 10;
        }
        String[] right = {
                "Java: " + System.getProperty("java.version") + " " + (System.getProperty("sun.arch.data.model", "64")) + "bit",
                "Mem: " + (used * 100 / Math.max(1, max)) + "% " + used + "/" + max + "MB",
                "CPU: " + Runtime.getRuntime().availableProcessors() + "x",
                "Display: " + (g.width * g.s) + "x" + (g.height * g.s) + " (render " + c.renderer.raster.w + "x" + c.renderer.raster.h + ")",
                "Software rasteriser, " + c.renderer.raster.threads() + " threads",
                "",
                info == null ? "" : String.format(Locale.ROOT, "Server stages ms: chunks %.2f blocks %.2f entities %.2f spawn %.2f",
                        info.stageNanos()[0] / 1e6, info.stageNanos()[1] / 1e6, info.stageNanos()[2] / 1e6, info.stageNanos()[3] / 1e6),
        };
        y = 2;
        for (String s : right) {
            if (!s.isEmpty()) {
                int w = g.textWidth(s);
                g.rect(g.width - w - 4, y - 1, w + 3, 10, 0x90505050);
                g.text(s, g.width - w - 2, y, 0xE0E0E0, false);
            }
            y += 10;
        }
        if (c.hit != null) {
            String t = Blocks.stateName(c.world.getBlock(c.hit.x, c.hit.y, c.hit.z));
            String s = "Targeted Block: " + c.hit.x + ", " + c.hit.y + ", " + c.hit.z + "  " + t;
            int w = g.textWidth(s);
            g.rect(g.width - w - 4, y - 1, w + 3, 10, 0x90505050);
            g.text(s, g.width - w - 2, y, 0xE0E0E0, false);
        }
        // frame time chart
        int n = 120, base = g.height - 4;
        g.rect(1, base - 61, n + 2, 62, 0x90202020);
        for (int i = 0; i < n; i++) {
            long ft = c.frameTimes[Math.floorMod(c.frameIdx - n + i, c.frameTimes.length)];
            int h = (int) Math.min(60, ft / 1e6 * 60 / 50.0);
            int col = h < 20 ? 0xFF40E040 : h < 40 ? 0xFFE0E040 : 0xFFE04040;
            g.rect(2 + i, base - h, 1, h, col);
        }
        g.text("frame ms (60 = 50ms)", 4, base - 60, 0xE0E0E0, false);
    }

    /** First-person arm / held item, drawn as a 2D overlay with swing and bob. */
    void hand(Gui g, S2C.PlayerState ps, float partial) {
        if (ps.dead() || ps.sleeping() || ps.gameMode() == 3 || c.screen == GameClient.Screen.CONTAINER) return;
        ItemStack held = ps.main()[ps.selected()];
        double swing = ps.swing() > 0 ? 1 - (ps.swing() - partial) / 6.0 : 0;
        if (swing < 0) swing = 0;
        double sw = Math.sin(Math.sqrt(swing) * Math.PI);
        float limb = ps.limbSwing(), amt = Math.min(1, ps.limbAmount());
        double bobX = Math.sin(limb * 0.6662) * 4 * amt, bobY = Math.abs(Math.cos(limb * 0.6662)) * 4 * amt;
        int W = g.width, H = g.height;
        double eat = ps.useTicks() > 0 && held != null && held.item.isFood() ? Math.sin(ps.useTicks() * 1.4) * 3 : 0;
        if (held == null) {
            int x = (int) (W - 70 + bobX - sw * 30), y = (int) (H - 60 + bobY + sw * 10);
            int s = g.s;
            java.awt.geom.AffineTransform old = g.g.getTransform();
            g.g.rotate(Math.toRadians(-20 - sw * 30), (x + 20) * s, (y + 60) * s);
            int skin = 0xFFC89A7A;
            g.rect(x, y, 24, 70, skin);
            g.rect(x, y, 3, 70, 0xFFB08060);
            g.rect(x + 21, y, 3, 70, 0xFFA87858);
            g.g.setTransform(old);
        } else {
            boolean block = held.item.block != null && held.item.iconShape != null && held.item.iconShape.equals("block");
            int size = block ? 56 : 64;
            int x = (int) (W - size - 30 + bobX - sw * 40), y = (int) (H - size - 10 + bobY + sw * 18 + eat);
            if (ps.usingBow()) { x -= 60; y -= 20; }
            java.awt.geom.AffineTransform old = g.g.getTransform();
            int s = g.s;
            g.g.rotate(Math.toRadians(block ? -sw * 25 : -10 - sw * 50), (x + size / 2.0) * s, (y + size) * s);
            g.icon(held.item.index, x, y, size);
            g.g.setTransform(old);
        }
    }
}
