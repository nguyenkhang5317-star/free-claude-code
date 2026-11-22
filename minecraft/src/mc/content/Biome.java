package mc.content;

import java.util.*;

/** Biome definition (Section 4.2, Appendix BC/AM). */
public final class Biome {
    public final String id;
    public final int index;
    public final double temperature, humidity, treeDensity, flowerDensity, grassDensity, terrainAmp;
    public final int top, filler, underwater, grassColor, foliageColor;
    public final List<String> trees;
    public final Map<String, List<Object[]>> spawns; // category -> [EntityType, weight]
    public final String precipitation;
    public final Map<String, Object> raw;

    Biome(int index, String id, double temperature, double humidity, int top, int filler, int underwater, int grassColor,
          int foliageColor, List<String> trees, double treeDensity, double flowerDensity, double grassDensity, double terrainAmp,
          Map<String, List<Object[]>> spawns, String precipitation, Map<String, Object> raw) {
        this.index = index; this.id = id; this.temperature = temperature; this.humidity = humidity; this.top = top;
        this.filler = filler; this.underwater = underwater; this.grassColor = grassColor; this.foliageColor = foliageColor;
        this.trees = trees; this.treeDensity = treeDensity; this.flowerDensity = flowerDensity; this.grassDensity = grassDensity;
        this.terrainAmp = terrainAmp; this.spawns = spawns; this.precipitation = precipitation; this.raw = raw;
    }

    public boolean snowy() { return temperature < 0.15; }

    public double num(String k, double def) { Object o = raw.get(k); return o instanceof Number ? ((Number) o).doubleValue() : def; }

    public boolean flag(String k) { Object o = raw.get(k); return Boolean.TRUE.equals(o) || (o instanceof Number && ((Number) o).doubleValue() != 0); }

    @Override public String toString() { return id; }
}
