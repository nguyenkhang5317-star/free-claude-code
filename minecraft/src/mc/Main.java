package mc;

import mc.client.GameClient;
import mc.content.Registries;
import mc.server.IntegratedServer;

import java.nio.file.*;

/**
 * Entry point. Usage:
 *   java -jar Minecraft.jar                 start the desktop game
 *   java -jar Minecraft.jar --headless SEED  generate + tick a world without a window (null sinks), print stage timers
 * System properties: -Dmc.data=DIR (data pack folder, default ./data next to the jar), -Dmc.dir=DIR (game directory).
 */
public final class Main {
    public static void main(String[] args) throws Exception {
        // Display pipeline: Direct3D + an active-rendered Canvas flickers black on many Windows GPUs/drivers, so the
        // stable GDI path is the default (opt back in with -Dmc.gpu=true). Never let AWT erase the canvas background.
        if (!Boolean.getBoolean("mc.gpu")) {
            System.setProperty("sun.java2d.d3d", "false");
            System.setProperty("sun.java2d.opengl", "false");
            System.setProperty("sun.java2d.noddraw", "true");
        }
        System.setProperty("sun.awt.noerasebackground", "true");
        if (System.getProperty("mc.data") == null) {
            Path d = locateData();
            if (d != null) System.setProperty("mc.data", d.toString());
        }
        Path gameDir = Paths.get(System.getProperty("mc.dir", defaultGameDir()));
        Files.createDirectories(gameDir);
        if (args.length > 1 && args[0].equals("--make-icon")) {
            makeIcon(Paths.get(args[1]));
            return;
        }
        if (args.length > 0 && args[0].equals("--headless")) {
            headless(gameDir, args.length > 1 ? Long.parseLong(args[1]) : 12345L, args.length > 2 ? Integer.parseInt(args[2]) : 200);
            return;
        }
        new GameClient(gameDir).start();
    }

    static String defaultGameDir() {
        String appData = System.getenv("APPDATA");
        if (appData != null) return Paths.get(appData, ".minecraft-rebuild").toString();
        return Paths.get(System.getProperty("user.home"), ".minecraft-rebuild").toString();
    }

    static Path locateData() {
        try {
            Path jar = Paths.get(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path dir = Files.isDirectory(jar) ? jar : jar.getParent();
            for (int i = 0; i < 4 && dir != null; i++, dir = dir.getParent()) {
                if (Files.isRegularFile(dir.resolve("data/blocks.json"))) return dir.resolve("data");
                if (Files.isRegularFile(dir.resolve("app/data/blocks.json"))) return dir.resolve("app/data");
            }
        } catch (Exception ignored) { }
        Path cwd = Paths.get("data");
        return Files.isRegularFile(cwd.resolve("blocks.json")) ? cwd : null;
    }

    /** Writes a multi-size Windows .ico (PNG-compressed entries) of the grass block item icon; used by the build. */
    static void makeIcon(Path out) throws Exception {
        System.setProperty("java.awt.headless", "true");
        Registries.load();
        mc.client.Textures.load();
        int[] px = mc.client.Textures.itemIcon[mc.content.Items.get("minecraft:grass_block").index];
        int[] sizes = {16, 32, 48, 256};
        java.util.List<byte[]> pngs = new java.util.ArrayList<>();
        for (int sz : sizes) {
            java.awt.image.BufferedImage im = new java.awt.image.BufferedImage(sz, sz, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < sz; y++) for (int x = 0; x < sz; x++) im.setRGB(x, y, px[(y * 16 / sz) * 16 + x * 16 / sz]);
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(im, "png", bo);
            pngs.add(bo.toByteArray());
        }
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(6 + 16 * sizes.length + pngs.stream().mapToInt(a -> a.length).sum()).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) 0).putShort((short) 1).putShort((short) sizes.length);
        int off = 6 + 16 * sizes.length;
        for (int i = 0; i < sizes.length; i++) {
            b.put((byte) (sizes[i] & 255)).put((byte) (sizes[i] & 255)).put((byte) 0).put((byte) 0);
            b.putShort((short) 1).putShort((short) 32).putInt(pngs.get(i).length).putInt(off);
            off += pngs.get(i).length;
        }
        for (byte[] p : pngs) b.put(p);
        Files.write(out, b.array());
        System.out.println("icon written: " + out);
    }

    static void headless(Path gameDir, long seed, int ticks) throws Exception {
        Registries.load();
        Path saves = gameDir.resolve("saves");
        Path dir = saves.resolve("headless-" + seed);
        IntegratedServer s = IntegratedServer.create(dir, "Headless " + seed, seed, 0, true);
        long t0 = System.nanoTime();
        for (int i = 0; i < ticks; i++) { s.tick(); s.outbound.clear(); }
        double ms = (System.nanoTime() - t0) / 1e6;
        System.out.printf("headless: seed=%d ticks=%d %.1f ms (%.2f ms/tick) chunks=%d%n", seed, ticks, ms, ms / ticks, s.world(mc.core.Dimension.OVERWORLD).loadedChunks().size());
        s.save(true);
        s.shutdown();
    }
}
