package mc.client;

import mc.content.*;
import mc.core.Raycast;
import mc.net.C2S;
import mc.net.S2C;
import mc.server.IntegratedServer;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Desktop client: window, 20 TPS client tick + uncapped interpolated rendering, input, screens, and the in-process
 * connection to the {@link IntegratedServer} running on its own thread (Section 2.1 architecture).
 */
public final class GameClient {
    public static final String VERSION = "1.16.5";
    public static final String TITLE = "Minecraft " + VERSION + " (Rebuild)";

    // ---- environment
    public final Path gameDir, savesDir;
    public final Settings settings;
    public Audio.IAudioBus audio;
    JFrame frame;
    Canvas canvas;
    volatile boolean running = true;
    Robot robot;
    final Gui gui = new Gui();
    final Hud hud;
    final Screens screens;
    Renderer renderer;
    BufferedImage worldImage;

    // ---- session
    public IntegratedServer server;
    Thread serverThread;
    volatile boolean serverRunning;
    public final ClientWorld world = new ClientWorld();
    public final Particles particles = new Particles();
    public S2C.PlayerState ps, prevPs;
    public S2C.WorldInfo info, prevInfo;
    public List<S2C.EntityFrame> entities = new ArrayList<>();
    public Map<Integer, S2C.EntityFrame> prevEntities = new HashMap<>();
    public S2C.MenuState menu;
    long stateNanos, infoNanos;
    public float yaw, pitch;
    public double plX, plY, plZ, camX, camY, camZ;
    public float camYaw, camPitch;
    public Raycast.Hit hit;
    public int hitEntity = -1;
    public int perspective;
    public boolean hideGui, showDebug;
    public double clientTicks;
    public final List<String[]> chat = new ArrayList<>(); // [text, timeMillis]
    public String loadingText = "";
    public float loadingProgress;

    // ---- screens
    public enum Screen { TITLE, WORLDS, CREATE, LOADING, OPTIONS, NONE, PAUSE, DEATH, CONTAINER, CHAT }

    public Screen screen = Screen.TITLE;
    public Screen optionsReturn = Screen.TITLE;

    // ---- input
    final boolean[] keys = new boolean[1024];
    final ConcurrentLinkedQueue<Object[]> events = new ConcurrentLinkedQueue<>();
    volatile int mouseX, mouseY;
    boolean lmb, rmb;
    boolean captured;
    int lastSpaceTick = -100, lastForwardTick = -100;
    boolean wasForward, sprintToggle;
    int useCooldown;
    double accMouseDX, accMouseDY;
    boolean ignoreNextMove;
    boolean swallowChar; // the key that opened chat also produces a keyTyped char; drop it

    // ---- timing
    public double fps;
    long frameCount, fpsTimer;
    public final long[] frameTimes = new long[240];
    int frameIdx;

    public GameClient(Path gameDir) {
        this.gameDir = gameDir;
        this.savesDir = gameDir.resolve("saves");
        this.settings = new Settings(gameDir.resolve("options.txt"));
        this.hud = new Hud(this);
        this.screens = new Screens(this);
    }

    // ------------------------------------------------------------------ startup
    public void start() {
        Registries.load();
        TexTable.init();
        audio = Audio.Synth.open();
        audio.setVolume((float) settings.volume);
        gui.audio = audio;
        frame = new JFrame(TITLE);
        canvas = new Canvas();
        canvas.setPreferredSize(new Dimension(1280, 720));
        canvas.setBackground(Color.BLACK);
        canvas.setFocusTraversalKeysEnabled(false);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { running = false; }

            @Override public void windowDeactivated(WindowEvent e) { events.add(new Object[]{"focuslost"}); }
        });
        frame.add(canvas);
        frame.pack();
        frame.setLocationRelativeTo(null);
        try {
            BufferedImage icon = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
            Graphics2D ig = icon.createGraphics();
            ig.drawImage(Textures.image(Textures.pix[Textures.id("grass_block_side")]), 0, 0, 32, 32, null);
            ig.dispose();
            frame.setIconImage(icon);
        } catch (Exception ignored) { }
        frame.setVisible(true);
        installInput();
        try { robot = new Robot(); } catch (Exception e) { robot = null; }
        canvas.createBufferStrategy(2);
        canvas.requestFocus();
        Thread loop = new Thread(this::loop, "Render thread");
        loop.start();
    }

    void installInput() {
        canvas.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                int k = e.getKeyCode();
                if (k < keys.length) keys[k] = true;
                events.add(new Object[]{"key", k, e.isShiftDown(), e.isControlDown()});
            }

            @Override public void keyReleased(KeyEvent e) {
                int k = e.getKeyCode();
                if (k < keys.length) keys[k] = false;
                events.add(new Object[]{"keyup", k});
            }

            @Override public void keyTyped(KeyEvent e) { events.add(new Object[]{"char", e.getKeyChar()}); }
        });
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { canvas.requestFocus(); events.add(new Object[]{"down", e.getButton(), e.isShiftDown()}); }

            @Override public void mouseReleased(MouseEvent e) { events.add(new Object[]{"up", e.getButton()}); }

            @Override public void mouseMoved(MouseEvent e) { moved(e); }

            @Override public void mouseDragged(MouseEvent e) { moved(e); }

            @Override public void mouseWheelMoved(MouseWheelEvent e) { events.add(new Object[]{"wheel", e.getWheelRotation()}); }
        };
        canvas.addMouseListener(ma);
        canvas.addMouseMotionListener(ma);
        canvas.addMouseWheelListener(ma);
    }

    void moved(MouseEvent e) {
        int cx = canvas.getWidth() / 2, cy = canvas.getHeight() / 2;
        if (captured && robot != null) {
            if (e.getX() == cx && e.getY() == cy) return;
            synchronized (this) {
                accMouseDX += e.getX() - cx;
                accMouseDY += e.getY() - cy;
            }
            Point p = canvas.getLocationOnScreen();
            robot.mouseMove(p.x + cx, p.y + cy);
        } else if (captured) {
            synchronized (this) {
                accMouseDX += e.getX() - mouseX;
                accMouseDY += e.getY() - mouseY;
            }
        }
        mouseX = e.getX();
        mouseY = e.getY();
    }

    void setCaptured(boolean c) {
        if (c == captured) return;
        captured = c;
        SwingUtilities.invokeLater(() -> {
            if (c) {
                BufferedImage blank = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                canvas.setCursor(Toolkit.getDefaultToolkit().createCustomCursor(blank, new Point(0, 0), "blank"));
                if (robot != null) {
                    try {
                        Point p = canvas.getLocationOnScreen();
                        robot.mouseMove(p.x + canvas.getWidth() / 2, p.y + canvas.getHeight() / 2);
                    } catch (Exception ignored) { }
                }
            } else {
                canvas.setCursor(Cursor.getDefaultCursor());
            }
        });
        synchronized (this) { accMouseDX = accMouseDY = 0; }
    }

    // ------------------------------------------------------------------ main loop
    void loop() {
        long last = System.nanoTime();
        double acc = 0;
        fpsTimer = System.currentTimeMillis();
        try {
            while (running) {
                long now = System.nanoTime();
                double dt = (now - last) / 1e6;
                last = now;
                acc += Math.min(dt, 250);
                handleEvents();
                pollPackets();
                int ticks = 0;
                while (acc >= 50 && ticks < 3) {
                    clientTick();
                    acc -= 50;
                    ticks++;
                }
                if (acc > 150) acc = 0;
                updateLook();
                renderFrame();
                frameTimes[frameIdx++ % frameTimes.length] = System.nanoTime() - now;
                frameCount++;
                long ms = System.currentTimeMillis();
                if (ms - fpsTimer >= 1000) {
                    fps = frameCount * 1000.0 / (ms - fpsTimer);
                    frameCount = 0;
                    fpsTimer = ms;
                }
                long spent = (System.nanoTime() - now) / 1_000_000;
                if (spent < 6) Thread.sleep(6 - spent); // ~160 fps cap, leaves CPU for server/meshers
            }
        } catch (Throwable t) {
            t.printStackTrace();
            JOptionPane.showMessageDialog(frame, "The game crashed:\n" + t, TITLE, JOptionPane.ERROR_MESSAGE);
        }
        quitWorld();
        settings.save();
        audio.close();
        if (renderer != null) renderer.shutdown();
        world.shutdown();
        frame.dispose();
        System.exit(0);
    }

    boolean inGame() { return server != null && screen != Screen.LOADING && screen != Screen.TITLE && screen != Screen.WORLDS && screen != Screen.CREATE && !(screen == Screen.OPTIONS && optionsReturn == Screen.TITLE); }

    void send(C2S.Packet p) { if (server != null) server.inbound.add(p); }

    // ------------------------------------------------------------------ input events
    void handleEvents() {
        gui.clicked = false;
        gui.rightClicked = false;
        gui.typed = 0;
        gui.backspace = false;
        gui.enter = false;
        Object[] e;
        while ((e = events.poll()) != null) {
            String kind = (String) e[0];
            switch (kind) {
                case "focuslost":
                    if (screen == Screen.NONE) openPause();
                    lmb = rmb = false;
                    Arrays.fill(keys, false);
                    break;
                case "char": {
                    char c = (Character) e[1];
                    if (swallowChar) { swallowChar = false; break; }
                    if (c == '\b') gui.backspace = true;
                    else if (c == '\n' || c == '\r') gui.enter = true;
                    else if (c >= 32 && c != 127) gui.typed = c;
                    break;
                }
                case "key": keyPressed((Integer) e[1], (Boolean) e[2], (Boolean) e[3]); break;
                case "keyup": break;
                case "down": {
                    int b = (Integer) e[1];
                    gui.shiftDown = (Boolean) e[2];
                    if (screen == Screen.NONE) {
                        if (!captured) { setCaptured(true); break; }
                        if (b == MouseEvent.BUTTON1) { lmb = true; leftPress(); }
                        if (b == MouseEvent.BUTTON3) { rmb = true; rightPress(); }
                        if (b == MouseEvent.BUTTON2) pick();
                    } else {
                        if (b == MouseEvent.BUTTON1) { gui.clicked = true; gui.mouseDown = true; }
                        if (b == MouseEvent.BUTTON3) gui.rightClicked = true;
                    }
                    break;
                }
                case "up": {
                    int b = (Integer) e[1];
                    if (b == MouseEvent.BUTTON1) { lmb = false; gui.mouseDown = false; send(new C2S.StopDig()); }
                    if (b == MouseEvent.BUTTON3) { rmb = false; send(new C2S.ReleaseUse()); }
                    break;
                }
                case "wheel": {
                    int r = (Integer) e[1];
                    if (screen == Screen.NONE && ps != null) send(new C2S.Select(Math.floorMod(ps.selected() + r, 9)));
                    else screens.scroll(r);
                    break;
                }
                default: break;
            }
        }
    }

    void keyPressed(int k, boolean shift, boolean ctrl) {
        if (k == KeyEvent.VK_F11) { toggleFullscreen(); return; }
        if (k == KeyEvent.VK_F2) { screenshot(); return; }
        if (screen == Screen.CHAT) {
            if (k == KeyEvent.VK_ESCAPE) closeScreen();
            else if (k == KeyEvent.VK_ENTER) { screens.submitChat(); closeScreen(); }
            else if (k == KeyEvent.VK_UP) screens.chatHistory(-1);
            else if (k == KeyEvent.VK_DOWN) screens.chatHistory(1);
            return;
        }
        if (screen == Screen.CONTAINER) {
            if (k == KeyEvent.VK_ESCAPE || k == KeyEvent.VK_E) { send(new C2S.CloseMenu()); closeScreen(); }
            else if (k >= KeyEvent.VK_1 && k <= KeyEvent.VK_9) screens.hotbarSwap(k - KeyEvent.VK_1);
            else if (k == KeyEvent.VK_Q) screens.dropHovered(ctrl);
            return;
        }
        if (screen != Screen.NONE) {
            if (k == KeyEvent.VK_ESCAPE) screens.back();
            if (k == KeyEvent.VK_ENTER) gui.enter = true;
            return;
        }
        // in game
        switch (k) {
            case KeyEvent.VK_ESCAPE: openPause(); break;
            case KeyEvent.VK_E: send(new C2S.OpenInventory()); screen = Screen.CONTAINER; menu = null; screens.openContainer(0); setCaptured(false); break;
            case KeyEvent.VK_T: swallowChar = true; screens.openChat(""); break;
            case KeyEvent.VK_SLASH: swallowChar = true; screens.openChat("/"); break;
            case KeyEvent.VK_Q: send(new C2S.Drop(ctrl)); break;
            case KeyEvent.VK_F: send(new C2S.SwapHands()); break;
            case KeyEvent.VK_F1: hideGui = !hideGui; break;
            case KeyEvent.VK_F3: showDebug = !showDebug; break;
            case KeyEvent.VK_F5: perspective = (perspective + 1) % 3; break;
            case KeyEvent.VK_SPACE:
                if (ps != null && (ps.gameMode() == 1 || ps.gameMode() == 3)) {
                    if (clientTicks - lastSpaceTick < 7) { send(new C2S.SetFlying(!ps.flying())); lastSpaceTick = -100; }
                    else lastSpaceTick = (int) clientTicks;
                }
                break;
            default:
                if (k >= KeyEvent.VK_1 && k <= KeyEvent.VK_9) send(new C2S.Select(k - KeyEvent.VK_1));
        }
    }

    void openPause() {
        screen = Screen.PAUSE;
        setCaptured(false);
        lmb = rmb = false;
        send(new C2S.StopDig());
        send(new C2S.SetPaused(true));
    }

    public void closeScreen() {
        if (screen == Screen.PAUSE) send(new C2S.SetPaused(false));
        screen = Screen.NONE;
        gui.focus = null;
        setCaptured(true);
    }

    void leftPress() {
        if (ps == null || ps.dead()) return;
        if (hitEntity >= 0) send(new C2S.Attack(hitEntity));
        else if (hit != null) send(new C2S.Dig(hit.x, hit.y, hit.z, hit.face));
        else send(new C2S.StopDig());
    }

    void rightPress() {
        if (ps == null || ps.dead()) return;
        useCooldown = 4;
        if (hitEntity >= 0) send(new C2S.Interact(hitEntity));
        else if (hit != null) send(new C2S.UseOn(hit.x, hit.y, hit.z, hit.face, hit.hx, hit.hy, hit.hz));
        else send(new C2S.UseItem());
    }

    void pick() { if (hit != null) send(new C2S.PickBlock(hit.x, hit.y, hit.z)); }

    void toggleFullscreen() {
        SwingUtilities.invokeLater(() -> {
            GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            boolean full = gd.getFullScreenWindow() == frame;
            frame.dispose();
            frame.setUndecorated(!full);
            if (full) { gd.setFullScreenWindow(null); frame.setVisible(true); frame.pack(); frame.setLocationRelativeTo(null); }
            else { frame.setVisible(true); gd.setFullScreenWindow(frame); }
            canvas.requestFocus();
            canvas.createBufferStrategy(2);
        });
    }

    void screenshot() {
        try {
            if (worldImage == null) return;
            Path dir = gameDir.resolve("screenshots");
            Files.createDirectories(dir);
            Path f = dir.resolve(new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date()) + ".png");
            BufferedImage copy = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = copy.createGraphics();
            g.drawImage(worldImage, 0, 0, copy.getWidth(), copy.getHeight(), null);
            g.dispose();
            ImageIO.write(copy, "png", f.toFile());
            addChat("Saved screenshot as " + f.getFileName());
        } catch (Exception e) {
            addChat("Couldn't save screenshot: " + e.getMessage());
        }
    }

    public void addChat(String s) {
        synchronized (chat) {
            chat.add(new String[]{s, String.valueOf(System.currentTimeMillis())});
            while (chat.size() > 100) chat.remove(0);
        }
    }

    // ------------------------------------------------------------------ server connection
    void pollPackets() {
        if (server == null) return;
        S2C.Packet p;
        int budget = 4000;
        while (budget-- > 0 && (p = server.outbound.poll()) != null) {
            if (p instanceof S2C.ChunkData) world.accept((S2C.ChunkData) p);
            else if (p instanceof S2C.ChunkUnload) world.unload(((S2C.ChunkUnload) p).cx(), ((S2C.ChunkUnload) p).cz());
            else if (p instanceof S2C.Entities) {
                Map<Integer, S2C.EntityFrame> prev = new HashMap<>();
                for (S2C.EntityFrame f : entities) prev.put(f.id(), f);
                prevEntities = prev;
                entities = ((S2C.Entities) p).frames();
            } else if (p instanceof S2C.PlayerState) {
                S2C.PlayerState n = (S2C.PlayerState) p;
                prevPs = ps == null ? n : ps;
                ps = n;
                stateNanos = System.nanoTime();
                if (n.dead() && screen != Screen.DEATH && screen != Screen.LOADING) { screen = Screen.DEATH; setCaptured(false); lmb = rmb = false; }
                if (!n.dead() && screen == Screen.DEATH) closeScreen();
            } else if (p instanceof S2C.WorldInfo) {
                prevInfo = info == null ? (S2C.WorldInfo) p : info;
                info = (S2C.WorldInfo) p;
                infoNanos = System.nanoTime();
            } else if (p instanceof S2C.Sound) {
                S2C.Sound s = (S2C.Sound) p;
                audio.play(s.id(), s.x(), s.y(), s.z(), s.volume(), s.pitch());
            } else if (p instanceof S2C.Particles) {
                S2C.Particles s = (S2C.Particles) p;
                particles.spawn(world, s.kind(), s.x(), s.y(), s.z(), s.count(), s.data());
            } else if (p instanceof S2C.Message) addChat(((S2C.Message) p).text());
            else if (p instanceof S2C.MenuState) {
                S2C.MenuState m = (S2C.MenuState) p;
                menu = m;
                if (screen == Screen.NONE && m.windowId() != 0) { screen = Screen.CONTAINER; screens.openContainer(m.windowId()); setCaptured(false); lmb = rmb = false; send(new C2S.StopDig()); }
            } else if (p instanceof S2C.MenuClosed) {
                if (screen == Screen.CONTAINER && menu != null && menu.windowId() != 0) closeScreen();
                menu = null;
            } else if (p instanceof S2C.Dimension) {
                S2C.Dimension d = (S2C.Dimension) p;
                world.clear();
                world.dimension = d.id();
                world.hasSky = d.hasSky();
                world.fogColor = d.fogColor() & 0xFFFFFF;
                entities = new ArrayList<>();
                prevEntities = new HashMap<>();
            }
        }
    }

    public float partial() {
        if (ps == null) return 1;
        return (float) Math.max(0, Math.min(1, (System.nanoTime() - stateNanos) / 50e6));
    }

    public double dayTime(float partial) {
        if (info == null) return 6000;
        double a = prevInfo.dayTime(), b = info.dayTime();
        if (b < a || b - a > 100) return b;
        return a + (b - a) * Math.max(0, Math.min(1, (System.nanoTime() - infoNanos) / 50e6));
    }

    float rainSmooth;

    public float rainLevel(float partial) { return rainSmooth; }

    // ------------------------------------------------------------------ world lifecycle
    public void createWorld(String name, String seedText, int gameMode) {
        long seed;
        seedText = seedText.trim();
        if (seedText.isEmpty()) seed = new Random().nextLong();
        else {
            try { seed = Long.parseLong(seedText); } catch (NumberFormatException e) { seed = seedText.hashCode(); }
        }
        String folder = name.replaceAll("[^A-Za-z0-9 _-]", "_").trim();
        if (folder.isEmpty()) folder = "World";
        Path dir = savesDir.resolve(folder);
        int n = 1;
        while (Files.exists(dir)) dir = savesDir.resolve(folder + " (" + (n++) + ")");
        final Path d = dir;
        final long sd = seed;
        startLoading("Generating world");
        new Thread(() -> {
            try {
                Files.createDirectories(d);
                loadingText = "Preparing spawn area";
                IntegratedServer s = IntegratedServer.create(d, name, sd, gameMode, false);
                beginSession(s);
            } catch (Throwable t) {
                t.printStackTrace();
                loadingFailed(t);
            }
        }, "World loader").start();
    }

    public void openWorld(Path dir) {
        startLoading("Loading world");
        new Thread(() -> {
            try {
                IntegratedServer s = IntegratedServer.open(dir, false);
                beginSession(s);
            } catch (Throwable t) {
                t.printStackTrace();
                loadingFailed(t);
            }
        }, "World loader").start();
    }

    void startLoading(String text) {
        loadingText = text;
        loadingProgress = 0;
        screen = Screen.LOADING;
    }

    void loadingFailed(Throwable t) {
        screens.error = "Failed to load world: " + t;
        screen = Screen.WORLDS;
    }

    void beginSession(IntegratedServer s) {
        world.clear();
        entities = new ArrayList<>();
        prevEntities = new HashMap<>();
        ps = prevPs = null;
        info = prevInfo = null;
        menu = null;
        chat.clear();
        s.viewDistance = settings.renderDistance;
        server = s;
        yaw = s.player.yaw;
        pitch = s.player.pitch;
        s.resendAll();
        serverRunning = true;
        serverThread = new Thread(() -> serverLoop(s), "Server thread");
        serverThread.setPriority(Thread.NORM_PRIORITY + 1);
        serverThread.start();
        loadingText = "Building terrain";
    }

    void serverLoop(IntegratedServer s) {
        long next = System.nanoTime();
        while (serverRunning) {
            long now = System.nanoTime();
            if (now < next) {
                try { Thread.sleep(Math.max(1, (next - now) / 1_000_000)); } catch (InterruptedException e) { break; }
                continue;
            }
            int catchUp = 0;
            while (System.nanoTime() >= next && catchUp < 2) { // at most 2 catch-up ticks (Section 2.1)
                try {
                    synchronized (s) { s.tick(); }
                } catch (Throwable t) {
                    t.printStackTrace();
                    addChat("Server tick error: " + t);
                }
                next += 50_000_000L;
                catchUp++;
            }
            if (System.nanoTime() - next > 500_000_000L) next = System.nanoTime(); // drop backlog
        }
    }

    public void quitWorld() {
        IntegratedServer s = server;
        if (s == null) return;
        serverRunning = false;
        try { if (serverThread != null) serverThread.join(3000); } catch (InterruptedException ignored) { }
        try {
            synchronized (s) { s.save(true); }
        } catch (Exception e) {
            System.err.println("[save] " + e);
        }
        s.shutdown();
        server = null;
        world.clear();
        ps = null;
        menu = null;
        setCaptured(false);
    }

    // ------------------------------------------------------------------ client tick (20 Hz)
    void clientTick() {
        clientTicks++;
        float targetRain = info != null && info.raining() ? 1 : 0;
        rainSmooth += (targetRain - rainSmooth) * 0.02f;
        if (server == null) return;
        if (screen == Screen.LOADING) {
            int need = 0, have = 0;
            if (ps != null) {
                int pcx = (int) Math.floor(ps.x()) >> 4, pcz = (int) Math.floor(ps.z()) >> 4;
                for (int dx = -2; dx <= 2; dx++)
                    for (int dz = -2; dz <= 2; dz++) {
                        need++;
                        ClientWorld.CChunk c = world.chunk(pcx + dx, pcz + dz);
                        if (c != null) {
                            boolean ok = true;
                            for (int sct = 0; sct < 16; sct++) if ((c.nonEmptyMask & (1 << sct)) != 0 && c.meshes[sct] == null) ok = false;
                            if (ok) have++;
                        }
                    }
            }
            loadingProgress = need == 0 ? 0 : have / (float) need;
            if (ps != null && have >= need - 4) { screen = Screen.NONE; setCaptured(true); }
            world.updateMeshes(ps == null ? 0 : ps.x(), ps == null ? 0 : ps.z(), 3);
            return;
        }
        particles.tick(world);
        audio.setRain(world.hasSky && ps != null ? rainSmooth * Math.min(1, world.getSkyLight((int) Math.floor(ps.x()), (int) Math.floor(ps.y() + 1), (int) Math.floor(ps.z())) / 15f) : 0);
        if (screen == Screen.PAUSE) return;
        // movement input
        boolean play = screen == Screen.NONE && ps != null && !ps.dead();
        float fwd = 0, str = 0;
        boolean jump = false, sneak = false, sprint = false;
        if (play) {
            if (keys[KeyEvent.VK_W]) fwd += 1;
            if (keys[KeyEvent.VK_S]) fwd -= 1;
            if (keys[KeyEvent.VK_A]) str += 1;
            if (keys[KeyEvent.VK_D]) str -= 1;
            jump = keys[KeyEvent.VK_SPACE];
            sneak = keys[KeyEvent.VK_SHIFT];
            boolean fwdNow = keys[KeyEvent.VK_W];
            if (fwdNow && !wasForward) {
                if (clientTicks - lastForwardTick < 7) sprintToggle = true;
                lastForwardTick = (int) clientTicks;
            }
            if (!fwdNow) sprintToggle = false;
            wasForward = fwdNow;
            sprint = (keys[KeyEvent.VK_CONTROL] || sprintToggle) && fwd > 0;
        }
        send(new C2S.Input(fwd, str, jump, sneak, sprint, yaw, pitch));
        if (play) {
            if (lmb) {
                if (hitEntity < 0 && hit != null) send(new C2S.Dig(hit.x, hit.y, hit.z, hit.face));
            }
            if (rmb && --useCooldown <= 0) {
                useCooldown = 4;
                if (hit != null && hitEntity < 0) send(new C2S.UseOn(hit.x, hit.y, hit.z, hit.face, hit.hx, hit.hy, hit.hz));
            }
        }
        if (server != null && server.viewDistance != settings.renderDistance) send(new C2S.SetViewDistance(settings.renderDistance));
    }

    void updateLook() {
        double dx, dy;
        synchronized (this) {
            dx = accMouseDX;
            dy = accMouseDY;
            accMouseDX = accMouseDY = 0;
        }
        if (screen == Screen.NONE && captured && ps != null && !ps.dead() && !ps.sleeping()) {
            double sens = settings.sensitivity * 0.6 + 0.2;
            double k = sens * sens * sens * 8 * 0.15;
            yaw += dx * k;
            pitch += dy * k;
            pitch = Math.max(-90, Math.min(90, pitch));
        }
    }

    // ------------------------------------------------------------------ frame
    void renderFrame() {
        BufferStrategy bs = canvas.getBufferStrategy();
        if (bs == null) return;
        int W = Math.max(1, canvas.getWidth()), H = Math.max(1, canvas.getHeight());
        Graphics2D g;
        try { g = (Graphics2D) bs.getDrawGraphics(); } catch (IllegalStateException e) { return; }
        Point mp = canvas.getMousePosition();
        if (mp != null) { mouseX = mp.x; mouseY = mp.y; }
        drawFrame(g, W, H);
        g.dispose();
        try { bs.show(); } catch (IllegalStateException ignored) { }
        Toolkit.getDefaultToolkit().sync();
    }

    /** Draws one complete frame (world + HUD + screens) into any Graphics2D; used by the window and by offscreen tests. */
    public void drawFrame(Graphics2D g, int W, int H) {
        int scale = Gui.autoScale(W, H, settings.guiScale);
        gui.mx = mouseX / scale;
        gui.my = mouseY / scale;
        gui.shiftDown = keys[KeyEvent.VK_SHIFT];
        boolean world3d = server != null && ps != null && screen != Screen.LOADING;
        if (world3d) {
            float partial = partial();
            computeCamera(partial);
            int rw = Math.max(64, (int) (W * settings.renderScale)), rh = Math.max(36, (int) (H * settings.renderScale));
            if (renderer == null) renderer = new Renderer(rw, rh);
            if (worldImage == null || worldImage.getWidth() != rw || worldImage.getHeight() != rh) {
                worldImage = new BufferedImage(rw, rh, BufferedImage.TYPE_INT_RGB);
                renderer.raster.color = ((DataBufferInt) worldImage.getRaster().getDataBuffer()).getData();
                renderer.raster.w = rw;
                renderer.raster.h = rh;
                renderer.raster.depth = new float[rw * rh];
            }
            world.updateMeshes(camX, camZ, settings.renderDistance);
            pickTarget();
            audio.listener(camX, camY, camZ, camYaw);
            renderer.render(this, partial);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(worldImage, 0, 0, W, H, null);
        } else {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, W, H);
        }
        gui.begin(g, W, H, scale);
        if (world3d && !hideGui) hud.draw(gui, partial());
        screens.draw(gui);
        gui.drawTooltip();
    }

    void computeCamera(float partial) {
        S2C.PlayerState a = prevPs, b = ps;
        plX = a.x() + (b.x() - a.x()) * partial;
        plY = a.y() + (b.y() - a.y()) * partial;
        plZ = a.z() + (b.z() - a.z()) * partial;
        double eye = b.eyeHeight();
        if (b.sleeping()) eye = 0.3;
        camX = plX;
        camY = plY + eye;
        camZ = plZ;
        camYaw = yaw;
        camPitch = pitch;
        if (settings.viewBobbing && perspective == 0 && b.onGround() && !b.flying()) {
            float limb = a.limbSwing() + (b.limbSwing() - a.limbSwing()) * partial;
            float amt = Math.min(1, a.limbAmount() + (b.limbAmount() - a.limbAmount()) * partial);
            double bob = Math.sin(limb * 0.6662) * 0.05 * amt;
            camY += Math.abs(Math.cos(limb * 0.6662)) * 0.06 * amt;
            camX += -Math.cos(Math.toRadians(yaw)) * bob;
            camZ += -Math.sin(Math.toRadians(yaw)) * bob;
        }
        if (b.hurtTime() > 0 && perspective == 0) camPitch += 0; // tilt handled in post
        if (perspective != 0) {
            double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
            double lx = -Math.sin(yr) * Math.cos(pr), ly = -Math.sin(pr), lz = Math.cos(yr) * Math.cos(pr);
            double dir = perspective == 1 ? -1 : 1;
            double dist = 4;
            Raycast.Hit h = Raycast.cast(world, camX, camY, camZ, lx * dir, ly * dir, lz * dir, 4.2);
            if (h != null) dist = Math.max(0.5, h.dist - 0.3);
            camX += lx * dir * dist;
            camY += ly * dir * dist;
            camZ += lz * dir * dist;
            if (perspective == 2) { camYaw = yaw + 180; camPitch = -pitch; }
        }
    }

    void pickTarget() {
        hit = null;
        hitEntity = -1;
        if (ps == null || ps.dead() || ps.gameMode() == 3) return;
        double ex = plX, ey = plY + ps.eyeHeight(), ez = plZ;
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        double lx = -Math.sin(yr) * Math.cos(pr), ly = -Math.sin(pr), lz = Math.cos(yr) * Math.cos(pr);
        double reach = ps.gameMode() == 1 ? 5 : 4.5;
        Raycast.Hit h = Raycast.cast(world, ex, ey, ez, lx, ly, lz, reach);
        double best = h == null ? reach : h.dist;
        float partial = partial();
        for (S2C.EntityFrame f : entities) {
            EntityType t = f.type() >= 0 && f.type() < EntityTypes.ALL.size() ? EntityTypes.ALL.get(f.type()) : null;
            if (t == null || f.dead() || t == EntityTypes.ITEM || t == EntityTypes.XP_ORB || t == EntityTypes.ARROW) continue;
            S2C.EntityFrame p = prevEntities.getOrDefault(f.id(), f);
            double x = p.x() + (f.x() - p.x()) * partial, y = p.y() + (f.y() - p.y()) * partial, z = p.z() + (f.z() - p.z()) * partial;
            double hw = f.width() / 2 + 0.1;
            mc.util.AABB bb = new mc.util.AABB(x - hw, y - 0.1, z - hw, x + hw, y + f.height() + 0.1, z + hw);
            double t2 = bb.rayHit(ex, ey, ez, lx, ly, lz, reach);
            if (t2 >= 0 && t2 < best) { best = t2; hitEntity = f.id(); }
        }
        if (hitEntity < 0) hit = h;
    }

    // ------------------------------------------------------------------ worlds list
    public List<Object[]> listWorlds() {
        List<Object[]> out = new ArrayList<>();
        try {
            if (!Files.isDirectory(savesDir)) return out;
            try (var ds = Files.newDirectoryStream(savesDir)) {
                for (Path p : ds) {
                    if (!Files.isDirectory(p)) continue;
                    try {
                        mc.core.RegionStore rs = new mc.core.RegionStore(p);
                        Map<String, Object> m = rs.readJson("level.json");
                        rs.close();
                        if (m == null) continue;
                        Map<String, Object> l = mc.util.Json.obj(m.get("level"));
                        out.add(new Object[]{p, mc.util.Json.str(l, "name", p.getFileName().toString()), mc.util.Json.str(l, "game_mode", "survival"),
                                Files.getLastModifiedTime(p).toMillis()});
                    } catch (Exception e) {
                        out.add(new Object[]{p, p.getFileName() + " (unreadable)", "?", 0L});
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[worlds] " + e);
        }
        out.sort((a, b) -> Long.compare((Long) b[3], (Long) a[3]));
        return out;
    }
}
