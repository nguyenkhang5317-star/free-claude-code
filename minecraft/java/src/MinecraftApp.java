import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * Minecraft 1.16.5 — Desktop App (Java Swing, KHONG phai web)
 * App native chay truc tiep bang Java, khong can browser/server.
 * Build: javac -d out src/MinecraftApp.java  |  Chay: java -cp out MinecraftApp
 * Controls: WASD di chuyen, Space nhay, chuot trai dao, chuot phai dat, 1-9 hotbar, E tui do, F an.
 */
public class MinecraftApp extends JFrame {
    static final int SEA = 28;
    static final Map<String, Color> COLORS = new HashMap<>();
    static {
        COLORS.put("minecraft:grass_block", new Color(95,159,53));
        COLORS.put("minecraft:dirt", new Color(134,96,67));
        COLORS.put("minecraft:stone", new Color(125,125,125));
        COLORS.put("minecraft:cobblestone", new Color(110,110,110));
        COLORS.put("minecraft:oak_log", new Color(107,82,50));
        COLORS.put("minecraft:oak_planks", new Color(168,136,82));
        COLORS.put("minecraft:sand", new Color(219,207,163));
        COLORS.put("minecraft:glass", new Color(200,230,240));
        COLORS.put("minecraft:oak_leaves", new Color(56,118,38));
        COLORS.put("minecraft:bricks", new Color(150,97,83));
        COLORS.put("minecraft:bedrock", new Color(85,85,85));
        COLORS.put("minecraft:water", new Color(52,95,218));
        COLORS.put("minecraft:gold_ore", new Color(252,238,75));
        COLORS.put("minecraft:diamond_ore", new Color(93,236,231));
        COLORS.put("minecraft:iron_ore", new Color(216,175,147));
        COLORS.put("minecraft:coal_ore", new Color(35,35,35));
        COLORS.put("minecraft:snow_block", new Color(240,251,251));
        COLORS.put("minecraft:gravel", new Color(136,126,126));
        COLORS.put("minecraft:torch", new Color(255,204,51));
        COLORS.put("minecraft:crafting_table", new Color(168,136,82));
        COLORS.put("minecraft:furnace", new Color(138,138,138));
        COLORS.put("minecraft:tnt", new Color(224,64,48));
        COLORS.put("minecraft:pumpkin", new Color(224,123,32));
        COLORS.put("minecraft:melon", new Color(122,199,79));
        COLORS.put("minecraft:white_wool", new Color(238,238,238));
        COLORS.put("minecraft:netherrack", new Color(112,58,58));
        COLORS.put("minecraft:end_stone", new Color(219,219,164));
        COLORS.put("minecraft:ice", new Color(160,216,239));
        COLORS.put("minecraft:obsidian", new Color(26,16,48));
        COLORS.put("minecraft:glowstone", new Color(255,204,102));
    }
    static Color blockColor(String id) { return COLORS.getOrDefault(id, Color.GRAY); }

    // ---- Seeded noise (deterministic, port tu spec §4) ----
    static int hashSeed(String s) {
        int h = 2166136261;
        for (char c : s.toCharArray()) { h ^= c; h *= 16777619; }
        return h;
    }
    static class Rng {
        int a;
        Rng(int seed) { a = seed; }
        double next() {
            a += 0x6D2B79F5;
            int t = a;
            t = (t ^ (t >>> 15)) * (t | 1);
            t ^= t + (int) ((t ^ (t >>> 7)) * (t | 61));
            return ((t ^ (t >>> 14)) & 0xFFFFFFFFL) / 4294967296.0;
        }
    }
    static class Noise {
        int[] p = new int[512];
        Noise(int seed) {
            Rng r = new Rng(seed);
            int[] perm = new int[256];
            for (int i = 0; i < 256; i++) perm[i] = i;
            for (int i = 255; i > 0; i--) { int j = (int) (r.next() * (i + 1)); int tmp = perm[i]; perm[i] = perm[j]; perm[j] = tmp; }
            for (int i = 0; i < 512; i++) p[i] = perm[i & 255];
        }
        double fade(double t) { return t*t*t*(t*(t*6-15)+10); }
        double grad(int h, double x, double y, double z) {
            switch (h & 7) {
                case 0: return x+y; case 1: return x-y; case 2: return -x+y; case 3: return -x-y;
                case 4: return x+z; case 5: return x-z; case 6: return -x+z; default: return -x-z;
            }
        }
        double noise(double x, double y, double z) {
            int X = (int) Math.floor(x) & 255, Y = (int) Math.floor(y) & 255, Z = (int) Math.floor(z) & 255;
            x -= Math.floor(x); y -= Math.floor(y); z -= Math.floor(z);
            double u = fade(x), v = fade(y), w = fade(z);
            int A = p[X] + Y, AA = p[A] + Z, AB = p[A+1] + Z, B = p[X+1] + Y, BA = p[B] + Z, BB = p[B+1] + Z;
            return lerp(lerp(lerp(grad(p[AA],x,y,z), grad(p[BA],x-1,y,z), u), lerp(grad(p[AB],x,y-1,z), grad(p[BB],x-1,y-1,z), u), v),
                         lerp(lerp(grad(p[AA+1],x,y,z-1), grad(p[BA+1],x-1,y,z-1), u), lerp(grad(p[AB+1],x,y-1,z-1), grad(p[BB+1],x-1,y-1,z-1), u), v), w);
        }
        double lerp(double a, double b, double t) { return a + t*(b-a); }
        double fbm(double x, double y, double z, int oct) {
            double a = 0, amp = 0.5, f = 1;
            for (int i = 0; i < oct; i++) { a += amp * noise(x*f, y*f, z*f); amp *= 0.5; f *= 2; }
            return a;
        }
    }

    // ---- World ----
    static class World {
        String seedStr, dim;
        Noise n1, n2, n3;
        Map<String, String> blocks = new HashMap<>(); // "x,y,z" -> id
        World(String seedStr, String dim) {
            this.seedStr = seedStr; this.dim = dim;
            int seed = hashSeed(seedStr);
            n1 = new Noise(seed); n2 = new Noise(seed ^ 0x9e3779b9); n3 = new Noise(seed ^ 0x85ebca6b);
            gen();
        }
        String key(int x, int y, int z) { return x + "," + y + "," + z; }
        int heightAt(int x, int z) {
            if (dim.equals("nether")) return 20 + (int) (n1.fbm(x*0.01, 0, z*0.01, 3) * 10);
            if (dim.equals("end")) return 30 + (int) (n1.fbm(x*0.02, 0, z*0.02, 2) * 4);
            double base = n1.fbm(x*0.008, 0, z*0.008, 4) * 22;
            double hills = n2.fbm(x*0.03, 0, z*0.03, 3) * 6;
            double m = Math.abs(n3.fbm(x*0.004, 0, z*0.004, 2)) * 18;
            return Math.max(4, (int) (SEA - 6 + base + hills + m));
        }
        void gen() {
            Rng rng = new Rng(hashSeed(seedStr));
            int R = 24;
            for (int x = -R; x < R; x++) for (int z = -R; z < R; z++) {
                int h = heightAt(x, z);
                for (int y = 0; y <= h; y++) {
                    String bid;
                    if (y == 0) bid = "minecraft:bedrock";
                    else if (y < h-3) bid = "minecraft:stone";
                    else if (y < h) bid = "minecraft:dirt";
                    else bid = "minecraft:grass_block";
                    if (bid.equals("minecraft:stone") && y < 40) {
                        double r = rng.next();
                        if (y < 12 && r < 0.008) bid = "minecraft:diamond_ore";
                        else if (y < 28 && r < 0.012) bid = "minecraft:iron_ore";
                        else if (r < 0.02) bid = "minecraft:coal_ore";
                    }
                    if (y > 2 && y < h-2 && !bid.equals("minecraft:bedrock") && n1.fbm(x*0.04, y*0.06, z*0.04, 2) > 0.62) continue;
                    blocks.put(key(x, y, z), bid);
                }
                if (h < SEA && dim.equals("overworld"))
                    for (int y = h+1; y <= SEA; y++) blocks.put(key(x, y, z), "minecraft:water");
                if (dim.equals("overworld") && h > SEA && rng.next() < 0.02) {
                    for (int dy = 1; dy <= 4; dy++) blocks.put(key(x, h+dy, z), "minecraft:oak_log");
                    for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 4; dy <= 5; dy++)
                        blocks.putIfAbsent(key(x+dx, h+dy, z+dz), "minecraft:oak_leaves");
                }
            }
        }
        String get(int x, int y, int z) { return blocks.get(key(x, y, z)); }
        void set(int x, int y, int z, String bid) { if (bid == null) blocks.remove(key(x,y,z)); else blocks.put(key(x,y,z), bid); }
        int ground(int x, int z) {
            for (int y = 60; y >= 0; y--) { String b = get(x, y, z); if (b != null && !b.equals("minecraft:water")) return y; }
            return 0;
        }
    }

    // ---- Game state ----
    World world;
    double px, pz, py, vy;
    boolean onGround;
    Set<String> keys = new HashSet<>();
    String[] inv = new String[9];
    int[] invCount = new int[9];
    int sel = 0, hp = 20, hunger = 20, day = 1, tick = 0;
    double time = 0.3;
    GamePanel panel;
    JLabel hud;

    public MinecraftApp() {
        setTitle("Minecraft 1.16.5 — Java Desktop App");
        setSize(960, 680); setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        showMenu();
        setVisible(true);
    }

    void showMenu() {
        getContentPane().removeAll();
        JPanel m = new JPanel(new GridLayout(0, 1, 8, 8));
        m.setBackground(new Color(90, 143, 60));
        m.setBorder(BorderFactory.createEmptyBorder(60, 120, 60, 120));
        JLabel t = new JLabel("MINECRAFT", SwingConstants.CENTER); t.setFont(new Font("Arial", Font.BOLD, 44)); t.setForeground(Color.WHITE);
        JLabel s = new JLabel("Java Edition 1.16.5 — Java Desktop App (khong phai web)", SwingConstants.CENTER); s.setForeground(Color.WHITE);
        JTextField seedF = new JTextField("12345"); seedF.setFont(new Font("Arial", Font.PLAIN, 16));
        JComboBox<String> dimC = new JComboBox<>(new String[]{"overworld", "nether", "end"});
        JButton play = new JButton("▶  PLAY"); play.setFont(new Font("Arial", Font.BOLD, 20)); play.setBackground(new Color(92,184,92)); play.setForeground(Color.WHITE);
        JLabel help = new JLabel("WASD di chuyen · Space nhay · Chuot trai dao · Chuot phai dat · 1-9 hotbar · E tui do · F an", SwingConstants.CENTER); help.setForeground(Color.WHITE);
        play.addActionListener(e -> startGame(seedF.getText(), (String) dimC.getSelectedItem()));
        m.add(t); m.add(s); m.add(new JLabel("Seed:")); m.add(seedF); m.add(new JLabel("World type:")); m.add(dimC); m.add(play); m.add(help);
        add(m); revalidate(); repaint();
    }

    void startGame(String seed, String dim) {
        world = new World(seed.isEmpty() ? "12345" : seed, dim);
        px = 0; pz = 0; py = world.ground(0, 0) + 1; vy = 0; hp = 20; hunger = 20;
        getContentPane().removeAll();
        panel = new GamePanel();
        hud = new JLabel("", SwingConstants.LEFT); hud.setBackground(Color.DARK_GRAY); hud.setForeground(Color.WHITE); hud.setOpaque(true);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(hud, BorderLayout.NORTH);
        JPanel hb = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 4)); hb.setBackground(Color.DARK_GRAY);
        for (int i = 0; i < 9; i++) {
            JLabel l = new JLabel(String.valueOf(i+1)); l.setPreferredSize(new Dimension(80, 36));
            l.setHorizontalAlignment(SwingConstants.CENTER); l.setOpaque(true); l.setBackground(Color.GRAY); l.setForeground(Color.WHITE);
            l.setName("slot" + i); hb.add(l);
        }
        bottom.add(hb, BorderLayout.SOUTH);
        add(panel, BorderLayout.CENTER); add(bottom, BorderLayout.SOUTH);
        addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                keys.add(e.getKeyChar() + "");
                if (e.getKeyCode() == KeyEvent.VK_SPACE) keys.add("space");
                if (e.getKeyCode() >= KeyEvent.VK_1 && e.getKeyCode() <= KeyEvent.VK_9) { sel = e.getKeyCode() - KeyEvent.VK_1; refreshHotbar(); }
                if (e.getKeyCode() == KeyEvent.VK_F) eat();
                if (e.getKeyCode() == KeyEvent.VK_E) showInv();
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) showMenu();
            }
            public void keyReleased(KeyEvent e) { keys.remove(e.getKeyChar() + ""); if (e.getKeyCode() == KeyEvent.VK_SPACE) keys.remove("space"); }
        });
        panel.setFocusable(true); panel.requestFocus();
        addItem("minecraft:oak_log", 16); addItem("minecraft:oak_planks", 16); addItem("minecraft:torch", 8);
        refreshHotbar();
        new javax.swing.Timer(50, e -> loop()).start(); // 20 TPS
    }

    void addItem(String id, int n) {
        for (int i = 0; i < 9; i++) if (id.equals(inv[i]) && invCount[i] < 64) { invCount[i] += n; return; }
        for (int i = 0; i < 9; i++) if (inv[i] == null) { inv[i] = id; invCount[i] = n; return; }
    }
    void refreshHotbar() {
        if (panel == null) return;
        Component hb = ((JPanel) getContentPane().getComponent(1)).getComponent(1);
        for (int i = 0; i < 9; i++) {
            JLabel l = (JLabel) ((JPanel) hb).getComponent(i);
            l.setText(inv[i] == null ? String.valueOf(i+1) : inv[i].replace("minecraft:", "") + " x" + invCount[i]);
            l.setBackground(i == sel ? Color.WHITE : Color.GRAY);
            l.setForeground(i == sel ? Color.BLACK : Color.WHITE);
        }
    }
    void eat() {
        if (inv[sel] != null && inv[sel].equals("minecraft:apple")) { hunger = Math.min(20, hunger + 4); if (--invCount[sel] <= 0) inv[sel] = null; refreshHotbar(); }
    }
    void showInv() {
        StringBuilder sb = new StringBuilder("<html>Inventory:<br>");
        for (int i = 0; i < 9; i++) sb.append(i+1).append(": ").append(inv[i] == null ? "empty" : inv[i] + " x" + invCount[i]).append("<br>");
        sb.append("<br>Craft: log -> planks (4)</html>");
        JOptionPane.showMessageDialog(this, sb.toString(), "Inventory", JOptionPane.INFORMATION_MESSAGE);
    }

    int[] iso(double x, double y, double z) {
        double dx = x - px, dz = z - pz;
        return new int[]{(int) (480 + (dx - dz) * 16), (int) (300 + (dx + dz) * 8 - (y - py) * 16)};
    }

    void loop() {
        tick++;
        double sp = 0.25;
        if (keys.contains("w")) pz -= sp;
        if (keys.contains("s")) pz += sp;
        if (keys.contains("a")) px -= sp;
        if (keys.contains("d")) px += sp;
        vy -= 0.08; py += vy;
        int g = world.ground((int) px, (int) pz) + 1;
        if (py <= g) { if (vy < -0.6) hp -= (int) (-vy * 4); py = g; vy = 0; onGround = true; } else onGround = false;
        if (keys.contains("space") && onGround) vy = 0.42;
        if (hp <= 0) { JOptionPane.showMessageDialog(this, "You died! Respawn...", "Minecraft", JOptionPane.INFORMATION_MESSAGE); hp = 20; py = world.ground((int)px,(int)pz)+1; }
        if (tick % 200 == 0 && hunger > 0) hunger--;
        if (hunger <= 0) hp--;
        time = (time + 0.0005) % 1;
        hud.setText("Seed: " + world.seedStr + "  Day " + day + "  " + String.format("%02d:00", (int)(time*24)) + "  HP " + hp + "/20  Hunger " + hunger + "/20  Pos " + (int)px + "," + (int)py + "," + (int)pz);
        panel.repaint();
    }

    class GamePanel extends JPanel {
        GamePanel() {
            addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    int[] best = null; double bd = 1e9;
                    for (String k : world.blocks.keySet()) {
                        String[] p = k.split(","); int x = Integer.parseInt(p[0]), y = Integer.parseInt(p[1]), z = Integer.parseInt(p[2]);
                        if (Math.abs(x - px) > 12 || Math.abs(z - pz) > 12) continue;
                        int[] s = iso(x, y, z); double d = (s[0]-e.getX())*(s[0]-e.getX()) + (s[1]-e.getY())*(s[1]-e.getY());
                        if (d < bd) { bd = d; best = new int[]{x, y, z}; }
                    }
                    if (best == null || bd > 2500) return;
                    if (e.getButton() == MouseEvent.BUTTON1) {
                        String bid = world.get(best[0], best[1], best[2]);
                        world.set(best[0], best[1], best[2], null);
                        if (bid != null) addItem(bid, 1);
                        refreshHotbar();
                    } else if (e.getButton() == MouseEvent.BUTTON3 && inv[sel] != null) {
                        if (world.get(best[0], best[1]+1, best[2]) == null) {
                            world.set(best[0], best[1]+1, best[2], inv[sel]);
                            if (--invCount[sel] <= 0) inv[sel] = null;
                            refreshHotbar();
                        }
                    }
                }
            });
        }
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            double dayF = Math.max(0, Math.sin(time * Math.PI * 2));
            g.setColor(dayF > 0.3 ? new Color(135,206,235) : new Color(26,26,58));
            g.fillRect(0, 0, getWidth(), getHeight());
            List<int[]> vis = new ArrayList<>();
            for (String k : world.blocks.keySet()) {
                String[] p = k.split(","); int x = Integer.parseInt(p[0]), y = Integer.parseInt(p[1]), z = Integer.parseInt(p[2]);
                if (Math.abs(x - px) > 14 || Math.abs(z - pz) > 14) continue;
                vis.add(new int[]{x + z, x, y, z});
            }
            vis.sort(Comparator.comparingInt(a -> a[0]));
            for (int[] v : vis) {
                int x = v[1], y = v[2], z = v[3];
                String bid = world.get(x, y, z); if (bid == null) continue;
                int[] s = iso(x, y, z); int sx = s[0], sy = s[1];
                Color c = blockColor(bid);
                g.setColor(c); g.setStroke(new BasicStroke(1));
                g.fillPolygon(new int[]{sx, sx+16, sx, sx-16}, new int[]{sy-8, sy, sy+8, sy}, 4);
                g.setColor(c.darker()); g.fillPolygon(new int[]{sx-16, sx, sx, sx-16}, new int[]{sy, sy+8, sy+24, sy+16}, 4);
                g.setColor(c.brighter()); g.fillPolygon(new int[]{sx+16, sx, sx, sx+16}, new int[]{sy, sy+8, sy+24, sy+16}, 4);
            }
            int[] pp = iso(px, py, pz);
            g.setColor(new Color(58,125,68)); g.fillRect(pp[0]-6, pp[1]-28, 12, 28);
            g.setColor(new Color(232,195,158)); g.fillRect(pp[0]-5, pp[1]-40, 10, 12);
            g.setColor(Color.WHITE); g.drawString("WASD di chuyen · Space nhay · Click trai dao · Click phai dat · E tui do", 10, 20);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(MinecraftApp::new);
    }
}
