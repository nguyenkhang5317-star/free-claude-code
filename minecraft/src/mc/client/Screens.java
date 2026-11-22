package mc.client;

import mc.content.*;
import mc.core.Menu;
import mc.net.C2S;
import mc.net.S2C;

import java.nio.file.*;
import java.util.*;

/** Menus and container screens (Section 14): title, world list, create world, options, pause, death, chat, inventories. */
public final class Screens {
    final GameClient c;
    public String error;
    List<Object[]> worlds;
    int selectedWorld = -1;
    long lastClickTime;
    int lastClickIndex = -1;
    boolean confirmDelete;
    String newName = "New World", newSeed = "";
    int newMode = 0;
    String chatText = "";
    final List<String> sent = new ArrayList<>();
    int historyPos;
    int scroll, creativeScroll;
    int containerWindow;
    int hoveredSlot = -1;
    static final String[] SPLASHES = {"Also try Terraria!", "Software rendered!", "100% original art!", "Now in Java!", "Deterministic worlds!", "20 ticks per second!",
            "Punch the tree!", "Made from scratch!", "Creeper? Aw man.", "Seeded with SplitMix64!"};
    final String splash = SPLASHES[new Random().nextInt(SPLASHES.length)];

    Screens(GameClient c) { this.c = c; }

    void draw(Gui g) {
        switch (c.screen) {
            case TITLE: title(g); break;
            case WORLDS: worlds(g); break;
            case CREATE: create(g); break;
            case LOADING: loading(g); break;
            case OPTIONS: options(g); break;
            case PAUSE: pause(g); break;
            case DEATH: death(g); break;
            case CHAT: chat(g); break;
            case CONTAINER: container(g); break;
            default: break;
        }
    }

    void back() {
        switch (c.screen) {
            case WORLDS: c.screen = GameClient.Screen.TITLE; break;
            case CREATE: c.screen = GameClient.Screen.WORLDS; break;
            case OPTIONS: c.settings.save(); c.screen = c.optionsReturn; break;
            case PAUSE: c.closeScreen(); break;
            default: break;
        }
    }

    void scroll(int r) {
        if (c.screen == GameClient.Screen.CONTAINER) creativeScroll = Math.max(0, creativeScroll + r);
        else scroll = Math.max(0, scroll + r);
    }

    // ------------------------------------------------------------------ title
    void title(Gui g) {
        g.dirtBackground();
        int W = g.width;
        // blocky logo made from the stone texture
        String logo = "MINECRAFT";
        int lx = W / 2 - 137, ly = 30;
        java.awt.Font f = new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 44 * g.s);
        g.g.setFont(f);
        int tw = g.g.getFontMetrics().stringWidth(logo);
        int px = W * g.s / 2 - tw / 2, py = (ly + 40) * g.s;
        g.g.setColor(new java.awt.Color(0x202020));
        g.g.drawString(logo, px + 3 * g.s, py + 3 * g.s);
        java.awt.image.BufferedImage stone = g.tex(Textures.id("stone"));
        java.awt.TexturePaint tp = new java.awt.TexturePaint(stone, new java.awt.Rectangle(0, 0, 16 * g.s * 2, 16 * g.s * 2));
        g.g.setPaint(tp);
        g.g.drawString(logo, px, py);
        g.centered("Java Edition " + GameClient.VERSION + " - original rebuild", W / 2, ly + 52, 0xC0C0C0);
        // splash: rotated -20 degrees around its own centre at the logo's right edge, pulsing
        java.awt.geom.AffineTransform old = g.g.getTransform();
        g.g.translate((W / 2 + tw / g.s / 2 - 20) * g.s, (ly + 30) * g.s);
        g.g.rotate(Math.toRadians(-20));
        double pulse = 1 + Math.abs(Math.sin(System.currentTimeMillis() / 250.0)) * 0.08;
        g.g.scale(pulse, pulse);
        g.text(splash, -g.textWidth(splash) / 2, -4, 0xFFFF00);
        g.g.setTransform(old);
        int bx = W / 2 - 100, by = g.height / 4 + 48;
        if (g.button("Singleplayer", bx, by, 200, 20, true)) { worlds = null; error = null; c.screen = GameClient.Screen.WORLDS; }
        if (g.button("Options...", bx, by + 24, 98, 20, true)) { c.optionsReturn = GameClient.Screen.TITLE; c.screen = GameClient.Screen.OPTIONS; }
        if (g.button("Quit Game", bx + 102, by + 24, 98, 20, true)) c.running = false;
        g.text("Copyright: original rebuild, not affiliated with Mojang", 2, g.height - 10, 0xFFFFFF);
        g.text("Minecraft " + GameClient.VERSION, g.width - g.textWidth("Minecraft 1.16.5") - 2, g.height - 10, 0xFFFFFF);
    }

    // ------------------------------------------------------------------ world list
    void worlds(Gui g) {
        g.dirtBackground();
        if (worlds == null) { worlds = c.listWorlds(); selectedWorld = worlds.isEmpty() ? -1 : 0; confirmDelete = false; }
        int W = g.width, H = g.height;
        g.centered("Select World", W / 2, 12, 0xFFFFFF);
        int top = 32, bottom = H - 60, rowH = 36;
        g.rect(0, top - 2, W, bottom - top + 4, 0x80000000);
        int visible = Math.max(1, (bottom - top) / rowH);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, worlds.size() - visible)));
        for (int i = scroll; i < worlds.size() && i < scroll + visible; i++) {
            Object[] w = worlds.get(i);
            int y = top + (i - scroll) * rowH;
            int x = W / 2 - 110;
            boolean sel = i == selectedWorld;
            if (sel) { g.rect(x - 2, y - 2, 224, rowH - 2, 0xFF808080); g.rect(x - 1, y - 1, 222, rowH - 4, 0xFF000000); }
            g.text((String) w[1], x + 36, y + 2, 0xFFFFFF);
            g.text(((Path) w[0]).getFileName() + "  " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date((Long) w[3])), x + 36, y + 12, 0x808080);
            g.text(capital((String) w[2]) + " Mode", x + 36, y + 22, 0x808080);
            g.icon(Items.get("minecraft:grass_block").index, x + 2, y + 1, 30);
            if (g.clicked && g.over(x - 2, y - 2, 224, rowH - 2)) {
                g.clicked = false;
                long now = System.currentTimeMillis();
                if (lastClickIndex == i && now - lastClickTime < 350) { c.openWorld((Path) w[0]); return; }
                selectedWorld = i; lastClickIndex = i; lastClickTime = now; confirmDelete = false;
            }
        }
        if (worlds.isEmpty()) g.centered("No worlds yet - create one!", W / 2, top + 20, 0xA0A0A0);
        if (error != null) g.centered(error, W / 2, bottom - 12, 0xFF5555);
        int by = H - 52;
        boolean has = selectedWorld >= 0 && selectedWorld < worlds.size();
        if (g.button("Play Selected World", W / 2 - 154, by, 150, 20, has) || (g.enter && has)) c.openWorld((Path) worlds.get(selectedWorld)[0]);
        if (g.button("Create New World", W / 2 + 4, by, 150, 20, true)) { newName = "New World"; newSeed = ""; newMode = 0; c.gui.focus = "name"; c.screen = GameClient.Screen.CREATE; }
        if (g.button(confirmDelete ? "Click again to delete!" : "Delete", W / 2 - 154, by + 24, 150, 20, has)) {
            if (!confirmDelete) confirmDelete = true;
            else { deleteDir((Path) worlds.get(selectedWorld)[0]); worlds = null; }
        }
        if (g.button("Cancel", W / 2 + 4, by + 24, 150, 20, true)) c.screen = GameClient.Screen.TITLE;
    }

    static String capital(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    static void deleteDir(Path p) {
        try (var st = Files.walk(p)) {
            st.sorted(Comparator.reverseOrder()).forEach(q -> { try { Files.delete(q); } catch (Exception ignored) { } });
        } catch (Exception e) {
            System.err.println("[worlds] delete failed: " + e);
        }
    }

    void create(Gui g) {
        g.dirtBackground();
        int W = g.width, x = W / 2 - 100;
        g.centered("Create New World", W / 2, 16, 0xFFFFFF);
        g.text("World Name", x, 44, 0xA0A0A0);
        newName = g.textField("name", newName, x, 56, 200, 20, 32);
        g.text("Seed for the World Generator", x, 84, 0xA0A0A0);
        newSeed = g.textField("seed", newSeed, x, 96, 200, 20, 32);
        g.text("Leave blank for a random seed", x, 120, 0x808080);
        String[] modes = {"Survival", "Creative"};
        if (g.button("Game Mode: " + modes[newMode], x, 136, 200, 20, true)) newMode = 1 - newMode;
        g.text(newMode == 0 ? "Search for resources, craft, gain levels, health and hunger" : "Unlimited resources, free flying, destroy blocks instantly", W / 2 - g.textWidth(newMode == 0
                ? "Search for resources, craft, gain levels, health and hunger" : "Unlimited resources, free flying, destroy blocks instantly") / 2, 160, 0xA0A0A0);
        int by = g.height - 28;
        if (g.button("Create New World", W / 2 - 154, by, 150, 20, !newName.trim().isEmpty()) || (g.enter && !newName.trim().isEmpty())) c.createWorld(newName.trim(), newSeed, newMode);
        if (g.button("Cancel", W / 2 + 4, by, 150, 20, true)) c.screen = GameClient.Screen.WORLDS;
    }

    void loading(Gui g) {
        g.dirtBackground();
        int W = g.width, H = g.height;
        g.centered(c.loadingText, W / 2, H / 2 - 20, 0xFFFFFF);
        int bw = 200;
        g.rect(W / 2 - bw / 2, H / 2, bw, 4, 0xFF404040);
        g.rect(W / 2 - bw / 2, H / 2, (int) (bw * c.loadingProgress), 4, 0xFF80FF80);
        g.centered((int) (c.loadingProgress * 100) + "%", W / 2, H / 2 + 10, 0xA0A0A0);
    }

    // ------------------------------------------------------------------ options
    void options(Gui g) {
        if (c.server != null && c.ps != null) g.darken();
        else g.dirtBackground();
        Settings s = c.settings;
        int W = g.width, x = W / 2 - 155, y = g.height / 6;
        g.centered("Options", W / 2, 15, 0xFFFFFF);
        s.fov = 30 + Math.round(g.slider("FOV: " + (int) s.fov, x, y, 150, 20, (s.fov - 30) / 80) * 80);
        s.renderDistance = 2 + (int) Math.round(g.slider("Render Distance: " + s.renderDistance + " chunks", x + 160, y, 150, 20, (s.renderDistance - 2) / 14.0) * 14);
        s.brightness = g.slider("Brightness: " + (s.brightness < 0.01 ? "Moody" : s.brightness > 0.99 ? "Bright" : "+" + (int) (s.brightness * 100) + "%"), x, y + 24, 150, 20, s.brightness);
        s.sensitivity = g.slider("Sensitivity: " + (int) (s.sensitivity * 200) + "%", x + 160, y + 24, 150, 20, s.sensitivity);
        double v = g.slider("Master Volume: " + (int) (s.volume * 100) + "%", x, y + 48, 150, 20, s.volume);
        if (v != s.volume) { s.volume = v; c.audio.setVolume((float) v); }
        s.renderScale = 0.25 + Math.round(g.slider("Render Scale: " + (int) (s.renderScale * 100) + "%", x + 160, y + 48, 150, 20, (s.renderScale - 0.25) / 0.75) * 15) / 20.0;
        if (g.button("GUI Scale: " + (s.guiScale == 0 ? "Auto" : String.valueOf(s.guiScale)), x, y + 72, 150, 20, true)) s.guiScale = (s.guiScale + 1) % 5;
        if (g.button("View Bobbing: " + (s.viewBobbing ? "ON" : "OFF"), x + 160, y + 72, 150, 20, true)) s.viewBobbing = !s.viewBobbing;
        if (c.server != null) {
            String[] diff = {"Peaceful", "Easy", "Normal", "Hard"};
            int d = c.server.level.difficulty;
            if (g.button("Difficulty: " + diff[d], x, y + 96, 150, 20, true)) c.server.inbound.add(new C2S.Chat("/difficulty " + ((d + 1) % 4)));
        }
        g.text("Controls: WASD move, Space jump (double-tap: fly in creative), Shift sneak, Ctrl sprint", x, y + 124, 0xA0A0A0);
        g.text("LMB break/attack, RMB use/place, MMB pick, 1-9/wheel hotbar, E inventory, Q drop", x, y + 134, 0xA0A0A0);
        g.text("F swap hands, T chat, / command, F1 hide HUD, F2 screenshot, F3 debug, F5 camera", x, y + 144, 0xA0A0A0);
        if (g.button("Done", W / 2 - 100, g.height - 30, 200, 20, true)) back();
    }

    // ------------------------------------------------------------------ in-game screens
    void pause(Gui g) {
        g.darken();
        int W = g.width, y = g.height / 4 + 8;
        g.centered("Game Menu", W / 2, y - 24, 0xFFFFFF);
        if (g.button("Back to Game", W / 2 - 100, y, 200, 20, true)) c.closeScreen();
        if (g.button("Options...", W / 2 - 100, y + 24, 200, 20, true)) { c.optionsReturn = GameClient.Screen.PAUSE; c.screen = GameClient.Screen.OPTIONS; }
        if (g.button("Save and Quit to Title", W / 2 - 100, y + 48, 200, 20, true)) {
            c.screen = GameClient.Screen.LOADING;
            c.loadingText = "Saving world";
            c.quitWorld();
            worlds = null;
            c.screen = GameClient.Screen.TITLE;
        }
        if (c.server != null) g.centered("World: " + c.server.level.name + "   Seed: " + c.server.level.seed, W / 2, y + 80, 0xA0A0A0);
    }

    void death(Gui g) {
        g.rect(0, 0, g.width, g.height, 0x80500000);
        int W = g.width;
        java.awt.geom.AffineTransform old = g.g.getTransform();
        g.g.scale(2, 2);
        g.centered("You died!", W / 4, 30, 0xFFFFFF);
        g.g.setTransform(old);
        S2C.PlayerState ps = c.ps;
        if (ps != null && ps.deathMessage() != null) g.centered(ps.deathMessage(), W / 2, 90, 0xFFFFFF);
        g.centered("Score: " + (ps == null ? 0 : ps.xpLevel()), W / 2, 104, 0xFFFF55);
        int y = g.height / 4 + 72;
        if (g.button("Respawn", W / 2 - 100, y, 200, 20, true)) c.send(new C2S.Respawn());
        if (g.button("Title Screen", W / 2 - 100, y + 24, 200, 20, true)) {
            c.send(new C2S.Respawn());
            c.quitWorld();
            worlds = null;
            c.screen = GameClient.Screen.TITLE;
        }
    }

    void openChat(String prefix) {
        chatText = prefix;
        historyPos = sent.size();
        c.screen = GameClient.Screen.CHAT;
        c.gui.focus = "chat";
        c.setCaptured(false);
    }

    void chat(Gui g) {
        chatText = g.textField("chat", chatText, 2, g.height - 14, g.width - 4, 12, 256);
        g.focus = "chat";
    }

    void submitChat() {
        String t = chatText.trim();
        if (!t.isEmpty()) {
            sent.add(t);
            c.send(new C2S.Chat(t));
        }
        chatText = "";
    }

    void chatHistory(int d) {
        if (sent.isEmpty()) return;
        historyPos = Math.max(0, Math.min(sent.size(), historyPos + d));
        chatText = historyPos < sent.size() ? sent.get(historyPos) : "";
    }

    // ------------------------------------------------------------------ containers
    void openContainer(int windowId) { containerWindow = windowId; hoveredSlot = -1; }

    /** Slot positions for a menu kind in snapshot order (mirrors mc.core.Menu). */
    static List<int[]> layout(int kind) {
        List<int[]> l = new ArrayList<>();
        switch (kind) {
            case Menu.INVENTORY:
                l.add(new int[]{154, 28});
                for (int r = 0; r < 2; r++) for (int col = 0; col < 2; col++) l.add(new int[]{98 + col * 18, 18 + r * 18});
                for (int i = 0; i < 4; i++) l.add(new int[]{8, 8 + i * 18});
                break;
            case Menu.CRAFTING:
                l.add(new int[]{124, 35});
                for (int r = 0; r < 3; r++) for (int col = 0; col < 3; col++) l.add(new int[]{30 + col * 18, 17 + r * 18});
                break;
            case Menu.FURNACE:
                l.add(new int[]{56, 17}); l.add(new int[]{56, 53}); l.add(new int[]{116, 35});
                break;
            case Menu.CHEST:
                for (int r = 0; r < 3; r++) for (int col = 0; col < 9; col++) l.add(new int[]{8 + col * 18, 18 + r * 18});
                break;
            default: break;
        }
        for (int r = 0; r < 3; r++) for (int col = 0; col < 9; col++) l.add(new int[]{8 + col * 18, 84 + r * 18});
        for (int col = 0; col < 9; col++) l.add(new int[]{8 + col * 18, 142});
        if (kind == Menu.INVENTORY) l.add(new int[]{77, 62});
        return l;
    }

    void container(Gui g) {
        g.darken();
        S2C.MenuState m = c.menu;
        if (m == null) {
            g.centered("...", g.width / 2, g.height / 2, 0xFFFFFF);
            return;
        }
        boolean creative = c.ps != null && c.ps.gameMode() == 1 && m.windowId() == 0;
        int pw = 176, ph = 166;
        int x0 = g.width / 2 - pw / 2, y0 = g.height / 2 - ph / 2 + (creative ? 52 : 0);
        if (creative) creativePalette(g, x0, y0 - 108);
        g.panel(x0, y0, pw, ph);
        List<int[]> lay = layout(m.kind());
        ItemStack[] slots = m.slots();
        hoveredSlot = -1;
        for (int i = 0; i < lay.size() && i < slots.length; i++) {
            int sx = x0 + lay.get(i)[0], sy = y0 + lay.get(i)[1];
            boolean big = (m.kind() == Menu.CRAFTING || m.kind() == Menu.INVENTORY) && i == 0 || (m.kind() == Menu.FURNACE && i == 2);
            if (big) { g.rect(sx - 5, sy - 5, 26, 26, 0xFF8B8B8B); g.rect(sx - 5, sy - 5, 25, 1, 0xFF373737); g.rect(sx - 5, sy - 5, 1, 25, 0xFF373737); }
            else g.slotBg(sx, sy);
            if (m.kind() == Menu.INVENTORY && i >= 5 && i < 9 && slots[i] == null) {
                String[] lbl = {"H", "C", "L", "B"};
                g.text(lbl[i - 5], sx + 5, sy + 4, 0x707070, false);
            }
            g.stack(slots[i], sx, sy);
            if (g.over(sx - 1, sy - 1, 18, 18)) {
                hoveredSlot = i;
                g.rect(sx, sy, 16, 16, 0x80FFFFFF);
                if (slots[i] != null && m.carried() == null) g.tooltip(slots[i].item.name + (slots[i].item.durability > 0 ? "  (" + (slots[i].item.durability - slots[i].damage) + "/" + slots[i].item.durability + ")" : ""));
            }
        }
        // decorations
        switch (m.kind()) {
            case Menu.INVENTORY:
                g.text("Crafting", x0 + 97, y0 + 6, 0x404040, false);
                g.rect(x0 + 26, y0 + 8, 49, 70, 0xFF000000);
                paperdoll(g, x0 + 26, y0 + 8);
                arrow(g, x0 + 135, y0 + 28, 1);
                break;
            case Menu.CRAFTING:
                g.text("Crafting", x0 + 28, y0 + 5, 0x404040, false);
                arrow(g, x0 + 90, y0 + 35, 1);
                break;
            case Menu.FURNACE: {
                g.text("Furnace", x0 + pw / 2 - g.textWidth("Furnace") / 2, y0 + 5, 0x404040, false);
                double cook = m.cookTotal() > 0 ? m.cook() / (double) m.cookTotal() : 0;
                arrow(g, x0 + 79, y0 + 34, cook);
                double burn = m.burnTotal() > 0 ? m.burn() / (double) m.burnTotal() : 0;
                int fh = (int) Math.ceil(13 * burn);
                g.rect(x0 + 57, y0 + 37, 13, 13, 0xFF8B8B8B);
                if (fh > 0) {
                    g.rect(x0 + 59, y0 + 37 + 13 - fh, 9, fh, 0xFFFF8A1E);
                    g.rect(x0 + 61, y0 + 37 + 13 - Math.max(1, fh - 3), 5, Math.max(1, fh - 3), 0xFFFFD040);
                }
                break;
            }
            case Menu.CHEST: g.text("Chest", x0 + 8, y0 + 6, 0x404040, false); break;
            default: break;
        }
        if (m.kind() != Menu.INVENTORY) g.text("Inventory", x0 + 8, y0 + 72, 0x404040, false);
        // clicks
        if ((g.clicked || g.rightClicked) && hoveredSlot >= 0) {
            c.send(new C2S.Click(m.windowId(), hoveredSlot, g.rightClicked ? 1 : 0, g.shiftDown));
            g.clicked = false;
            g.rightClicked = false;
        } else if ((g.clicked || g.rightClicked) && !g.over(x0, y0, pw, ph) && m.carried() != null && !(creative && g.over(x0, y0 - 108, pw, 104))) {
            c.send(new C2S.Click(m.windowId(), -999, g.rightClicked ? 1 : 0, false)); // drop outside
            g.clicked = false;
            g.rightClicked = false;
        }
        // carried stack follows the mouse
        if (m.carried() != null) g.stack(m.carried(), g.mx - 8, g.my - 8);
    }

    void paperdoll(Gui g, int x, int y) {
        // simple front view of the player model colours
        int cx = x + 24, top = y + 8;
        g.rect(cx - 8, top, 16, 16, 0xFFC89A7A);
        g.rect(cx - 5, top + 7, 2, 2, 0xFF3A2A8A); g.rect(cx + 3, top + 7, 2, 2, 0xFF3A2A8A);
        g.rect(cx - 8, top, 16, 4, 0xFF4A3020);
        g.rect(cx - 8, top + 16, 16, 24, 0xFF2AA8C8);
        g.rect(cx - 16, top + 16, 8, 24, 0xFFC89A7A); g.rect(cx + 8, top + 16, 8, 24, 0xFFC89A7A);
        g.rect(cx - 8, top + 40, 8, 20, 0xFF3A3AAA); g.rect(cx, top + 40, 8, 20, 0xFF3A3AAA);
    }

    void arrow(Gui g, int x, int y, double progress) {
        for (int i = 0; i < 22; i++) {
            int hgt = i < 15 ? 4 : (22 - i) * 2 + 1;
            int yy = y + 8 - hgt / 2;
            int col = i < progress * 22 ? 0xFFFFFFFF : 0xFF8B8B8B;
            g.rect(x + i, yy, 1, hgt, col);
        }
    }

    void creativePalette(Gui g, int x0, int y0) {
        int pw = 176, ph = 104;
        g.panel(x0, y0, pw, ph);
        g.text("Creative Items  (scroll, shift = stack)", x0 + 8, y0 + 5, 0x404040, false);
        List<Item> items = Items.ALL;
        int cols = 9, rows = 5;
        int maxScroll = Math.max(0, (items.size() + cols - 1) / cols - rows);
        creativeScroll = Math.min(creativeScroll, maxScroll);
        for (int r = 0; r < rows; r++)
            for (int col = 0; col < cols; col++) {
                int i = (creativeScroll + r) * cols + col;
                int sx = x0 + 8 + col * 18, sy = y0 + 16 + r * 18;
                g.slotBg(sx, sy);
                if (i >= items.size()) continue;
                Item it = items.get(i);
                g.icon(it.index, sx, sy, 16);
                if (g.over(sx - 1, sy - 1, 18, 18)) {
                    g.rect(sx, sy, 16, 16, 0x80FFFFFF);
                    g.tooltip(it.name);
                    if (g.clicked) {
                        g.clicked = false;
                        if (c.menu != null && c.menu.carried() != null) c.send(new C2S.Click(0, -999, 0, false));
                        else c.send(new C2S.CreativeGive(it.id, g.shiftDown ? it.maxStack : 1));
                    }
                }
            }
        // scrollbar
        g.rect(x0 + pw - 10, y0 + 16, 6, rows * 18 - 2, 0xFF505050);
        int kh = 12, ky = maxScroll == 0 ? 0 : (int) ((rows * 18 - 2 - kh) * creativeScroll / (double) maxScroll);
        g.rect(x0 + pw - 10, y0 + 16 + ky, 6, kh, 0xFFC0C0C0);
    }

    void hotbarSwap(int n) {
        if (c.menu == null || hoveredSlot < 0) return;
        // emulate vanilla number-key swap: pick up, put into hotbar slot, put back
        int hot = layout(c.menu.kind()).size() - (c.menu.kind() == Menu.INVENTORY ? 10 : 9) + n;
        if (hot == hoveredSlot) return;
        c.send(new C2S.Click(c.menu.windowId(), hoveredSlot, 0, false));
        c.send(new C2S.Click(c.menu.windowId(), hot, 0, false));
        c.send(new C2S.Click(c.menu.windowId(), hoveredSlot, 0, false));
    }

    void dropHovered(boolean all) {
        if (c.menu == null || hoveredSlot < 0 || c.menu.carried() != null) return;
        c.send(new C2S.Click(c.menu.windowId(), hoveredSlot, 0, false));
        c.send(new C2S.Click(c.menu.windowId(), -999, all ? 0 : 1, false));
        if (!all) c.send(new C2S.Click(c.menu.windowId(), hoveredSlot, 0, false));
    }
}
