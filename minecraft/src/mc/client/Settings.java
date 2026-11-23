package mc.client;

import java.nio.file.*;
import java.util.Properties;

/** User options persisted to options.txt in the game directory. */
public final class Settings {
    public int renderDistance = 6;
    public double fov = 70;
    public double brightness = 0.5;
    public double sensitivity = 0.5;
    public double volume = 0.8;
    public double musicVolume = 1.0;
    /** "auto" = original sounds from resource packs / the player's Minecraft installation when found; "builtin" = synthesised. */
    public String soundSource = "auto";
    public double renderScale = 0.5;
    public int guiScale = 0; // 0 = auto
    public boolean viewBobbing = true;
    public boolean showFps = false;
    public int difficulty = 2;
    private final Path file;

    public Settings(Path file) {
        this.file = file;
        try {
            if (Files.exists(file)) {
                Properties p = new Properties();
                try (var in = Files.newInputStream(file)) { p.load(in); }
                renderDistance = clampI(Integer.parseInt(p.getProperty("renderDistance", "6")), 2, 16);
                fov = clampD(Double.parseDouble(p.getProperty("fov", "70")), 30, 110);
                brightness = clampD(Double.parseDouble(p.getProperty("gamma", "0.5")), 0, 1);
                sensitivity = clampD(Double.parseDouble(p.getProperty("mouseSensitivity", "0.5")), 0, 1);
                volume = clampD(Double.parseDouble(p.getProperty("soundVolume", "0.8")), 0, 1);
                musicVolume = clampD(Double.parseDouble(p.getProperty("soundCategory_music", "1.0")), 0, 1);
                soundSource = "builtin".equals(p.getProperty("soundSource", "auto")) ? "builtin" : "auto";
                renderScale = clampD(Double.parseDouble(p.getProperty("renderScale", "0.5")), 0.25, 1);
                guiScale = clampI(Integer.parseInt(p.getProperty("guiScale", "0")), 0, 4);
                viewBobbing = Boolean.parseBoolean(p.getProperty("bobView", "true"));
            }
        } catch (Exception e) {
            System.err.println("[options] could not read " + file + ": " + e);
        }
    }

    static int clampI(int v, int a, int b) { return Math.max(a, Math.min(b, v)); }

    static double clampD(double v, double a, double b) { return Math.max(a, Math.min(b, v)); }

    public void save() {
        try {
            Properties p = new Properties();
            p.setProperty("renderDistance", "" + renderDistance);
            p.setProperty("fov", "" + fov);
            p.setProperty("gamma", "" + brightness);
            p.setProperty("mouseSensitivity", "" + sensitivity);
            p.setProperty("soundVolume", "" + volume);
            p.setProperty("renderScale", "" + renderScale);
            p.setProperty("soundCategory_music", "" + musicVolume);
            p.setProperty("soundSource", soundSource);
            p.setProperty("guiScale", "" + guiScale);
            p.setProperty("bobView", "" + viewBobbing);
            Files.createDirectories(file.getParent());
            try (var out = Files.newOutputStream(file)) { p.store(out, "Minecraft 1.16.5 rebuild options"); }
        } catch (Exception e) {
            System.err.println("[options] could not save: " + e);
        }
    }
}
