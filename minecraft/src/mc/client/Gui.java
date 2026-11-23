package mc.client;

import mc.content.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/** Immediate-mode GUI toolkit in "GUI pixels" (scaled by {@link #s}), drawn with Java2D on the window buffer. */
public final class Gui {
    public Graphics2D g;
    public int s = 2;            // gui scale
    public int width, height;    // in gui pixels
    public int mx, my;           // mouse in gui pixels
    public boolean clicked, rightClicked, mouseDown;
    public boolean shiftDown;
    public String focus;         // focused text field id
    public char typed;           // last typed char this frame (0 = none)
    public boolean backspace, enter;
    public String hoverTip;
    final Map<Integer, BufferedImage> iconCache = new HashMap<>();
    final Map<Integer, BufferedImage> texCache = new HashMap<>();
    public Audio.IAudioBus audio;
    Font font, fontSmall;
    int fontScale;

    public void begin(Graphics2D g, int winW, int winH, int scale) {
        this.g = g;
        this.s = Math.max(1, scale);
        this.width = winW / s;
        this.height = winH / s;
        if (font == null || fontScale != s) {
            fontScale = s;
            font = new Font(Font.MONOSPACED, Font.BOLD, 9 * s);
            fontSmall = new Font(Font.MONOSPACED, Font.BOLD, 7 * s);
        }
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        hoverTip = null;
    }

    public static int autoScale(int w, int h, int pref) {
        int sc = 1;
        while (sc < 4 && w / (sc + 1) >= 320 && h / (sc + 1) >= 240) sc++;
        return pref > 0 ? Math.min(pref, sc) : sc;
    }

    // ------------------------------------------------------------------ primitives
    public void rect(int x, int y, int w, int h, int argb) {
        g.setColor(new Color(argb, true));
        g.fillRect(x * s, y * s, w * s, h * s);
    }

    public void rectF(double x, double y, double w, double h, int argb) {
        g.setColor(new Color(argb, true));
        g.fillRect((int) Math.round(x * s), (int) Math.round(y * s), (int) Math.round(w * s), (int) Math.round(h * s));
    }

    public int textWidth(String t) {
        g.setFont(font);
        return (int) Math.ceil(g.getFontMetrics().stringWidth(t) / (double) s);
    }

    public void text(String t, int x, int y, int rgb) { text(t, x, y, rgb, true); }

    public void text(String t, int x, int y, int rgb, boolean shadow) {
        g.setFont(font);
        int base = (y + 8) * s;
        if (shadow) {
            g.setColor(new Color(shade(rgb), false));
            g.drawString(t, x * s + s, base + s);
        }
        g.setColor(new Color(rgb, (rgb >>> 24) != 0));
        g.drawString(t, x * s, base);
    }

    public void centered(String t, int cx, int y, int rgb) { text(t, cx - textWidth(t) / 2, y, rgb); }

    static int shade(int rgb) { return (((rgb >> 16) & 255) / 4) << 16 | (((rgb >> 8) & 255) / 4) << 8 | ((rgb & 255) / 4); }

    public boolean over(int x, int y, int w, int h) { return mx >= x && my >= y && mx < x + w && my < y + h; }

    /** Bevelled panel like the container backgrounds. */
    public void panel(int x, int y, int w, int h) {
        rect(x, y, w, h, 0xFFC6C6C6);
        rect(x, y, w, 1, 0xFFFFFFFF);
        rect(x, y, 1, h, 0xFFFFFFFF);
        rect(x, y + h - 1, w, 1, 0xFF555555);
        rect(x + w - 1, y, 1, h, 0xFF555555);
        rect(x - 1, y + 1, 1, h - 2, 0xFF000000);
        rect(x + w, y + 1, 1, h - 2, 0xFF000000);
        rect(x + 1, y - 1, w - 2, 1, 0xFF000000);
        rect(x + 1, y + h, w - 2, 1, 0xFF000000);
    }

    public void slotBg(int x, int y) {
        rect(x - 1, y - 1, 18, 18, 0xFF8B8B8B);
        rect(x - 1, y - 1, 17, 1, 0xFF373737);
        rect(x - 1, y - 1, 1, 17, 0xFF373737);
        rect(x, y + 16, 17, 1, 0xFFFFFFFF);
        rect(x + 16, y, 1, 17, 0xFFFFFFFF);
    }

    public void darken() { rect(0, 0, width, height, 0xA0101010); }

    /** Dirt-style options background (procedural dirt texture, darkened). */
    public void dirtBackground() {
        BufferedImage dirt = tex(Textures.id("dirt"));
        int ts = 32 * s / 2;
        for (int y = 0; y < height * s; y += ts) for (int x = 0; x < width * s; x += ts) g.drawImage(dirt, x, y, ts, ts, null);
        rect(0, 0, width, height, 0xB0000000);
    }

    BufferedImage tex(int id) {
        return texCache.computeIfAbsent(id, k -> {
            int[] p = TexTable.array()[k];
            BufferedImage im = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            im.setRGB(0, 0, 16, 16, p, 0, 16);
            return im;
        });
    }

    public void icon(int itemIndex, int x, int y, int size) {
        BufferedImage im = iconCache.computeIfAbsent(itemIndex, k -> {
            BufferedImage b = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            b.setRGB(0, 0, 16, 16, Textures.itemIcon[k], 0, 16);
            return b;
        });
        g.drawImage(im, x * s, y * s, size * s, size * s, null);
    }

    public void iconPx(int itemIndex, int px, int py, int sizePx) {
        BufferedImage im = iconCache.computeIfAbsent(itemIndex, k -> {
            BufferedImage b = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            b.setRGB(0, 0, 16, 16, Textures.itemIcon[k], 0, 16);
            return b;
        });
        g.drawImage(im, px, py, sizePx, sizePx, null);
    }

    /** Item stack with count and durability bar. */
    public void stack(ItemStack st, int x, int y) {
        if (st == null || st.count <= 0) return;
        icon(st.item.index, x, y, 16);
        if (st.item.durability > 0 && st.damage > 0) {
            double f = 1 - st.damage / (double) st.item.durability;
            rect(x + 2, y + 13, 13, 2, 0xFF000000);
            int c = Color.HSBtoRGB((float) (f / 3), 1, 1);
            rect(x + 2, y + 13, (int) Math.round(f * 13), 1, c);
        }
        if (st.count > 1) {
            String c = String.valueOf(st.count);
            text(c, x + 17 - textWidth(c), y + 9 - 1, 0xFFFFFF);
        }
    }

    // ------------------------------------------------------------------ widgets
    public boolean button(String label, int x, int y, int w, int h, boolean enabled) {
        boolean hover = enabled && over(x, y, w, h);
        int base = !enabled ? 0xFF2C2C2C : hover ? 0xFF7E88BF : 0xFF6F6F6F;
        rect(x, y, w, h, 0xFF000000);
        rect(x + 1, y + 1, w - 2, h - 2, base);
        rect(x + 1, y + 1, w - 2, 1, enabled ? (hover ? 0xFFBCC4F0 : 0xFFAAAAAA) : 0xFF444444);
        rect(x + 1, y + h - 3, w - 2, 2, enabled ? (hover ? 0xFF5A649A : 0xFF4E4E4E) : 0xFF222222);
        centered(label, x + w / 2, y + (h - 8) / 2 - 1, !enabled ? 0xA0A0A0 : hover ? 0xFFFFA0 : 0xE0E0E0);
        if (hover && clicked) {
            clicked = false;
            if (audio != null) audio.playUi("ui.button.click", 0.25f, 1f);
            return true;
        }
        return false;
    }

    /** Slider returning the new value in [0,1]. */
    public double slider(String label, int x, int y, int w, int h, double v) {
        rect(x, y, w, h, 0xFF000000);
        rect(x + 1, y + 1, w - 2, h - 2, 0xFF3A3A3A);
        int kx = x + (int) Math.round(v * (w - 8));
        boolean hover = over(x, y, w, h);
        rect(kx, y, 8, h, 0xFF000000);
        rect(kx + 1, y + 1, 6, h - 2, hover ? 0xFFA0A8E0 : 0xFF9A9A9A);
        centered(label, x + w / 2, y + (h - 8) / 2 - 1, 0xE0E0E0);
        if (mouseDown && hover) v = Math.max(0, Math.min(1, (mx - x - 4) / (double) (w - 8)));
        return v;
    }

    public String textField(String id, String value, int x, int y, int w, int h, int max) {
        boolean f = id.equals(focus);
        rect(x, y, w, h, f ? 0xFFFFFFFF : 0xFFA0A0A0);
        rect(x + 1, y + 1, w - 2, h - 2, 0xFF000000);
        String shown = value;
        while (textWidth(shown) > w - 8 && shown.length() > 0) shown = shown.substring(1);
        boolean caret = f && (System.currentTimeMillis() / 500) % 2 == 0;
        text(shown + (caret ? "_" : ""), x + 4, y + (h - 8) / 2 - 1, 0xE0E0E0);
        if (clicked && over(x, y, w, h)) { focus = id; clicked = false; }
        if (f) {
            if (backspace && value.length() > 0) value = value.substring(0, value.length() - 1);
            if (typed >= 32 && typed != 127 && value.length() < max) value += typed;
        }
        return value;
    }

    public void tooltip(String t) { hoverTip = t; }

    public void drawTooltip() {
        if (hoverTip == null) return;
        int w = textWidth(hoverTip) + 6;
        int x = Math.min(mx + 10, width - w - 2), y = Math.max(2, my - 14);
        rect(x, y, w, 13, 0xF0100010);
        rect(x, y, w, 1, 0xFF5000A0);
        rect(x, y + 12, w, 1, 0xFF28007F);
        text(hoverTip, x + 3, y + 2, 0xFFFFFF);
    }
}
