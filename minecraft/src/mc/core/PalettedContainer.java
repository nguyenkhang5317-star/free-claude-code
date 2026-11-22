package mc.core;

import mc.content.Blocks;

import java.util.Arrays;

/**
 * Palette-encoded 16x16x16 block storage (Section 2.3): a palette of the distinct states plus a B-bit index array,
 * B the smallest width in {4..13} that indexes the palette. A uniform section stores no index array at all. Above
 * 13 bits the container switches to the global palette (raw state IDs). Values never span two longs.
 */
public final class PalettedContainer {
    public static final int SIZE = 4096;
    private int[] palette;      // null in global mode
    private int paletteSize;
    private int bits;           // 0 when uniform
    private long[] data;        // null when uniform
    private boolean global;

    public PalettedContainer(int fill) {
        palette = new int[]{fill};
        paletteSize = 1;
        bits = 0;
    }

    public int bits() { return bits; }

    public int paletteSize() { return global ? -1 : paletteSize; }

    public boolean isUniform() { return data == null; }

    private int perLong() { return 64 / bits; }

    private int rawGet(int i) {
        int pl = perLong();
        return (int) ((data[i / pl] >>> ((i % pl) * bits)) & ((1L << bits) - 1));
    }

    private void rawSet(int i, int v) {
        int pl = perLong();
        int shift = (i % pl) * bits;
        long mask = ((1L << bits) - 1) << shift;
        int w = i / pl;
        data[w] = (data[w] & ~mask) | (((long) v << shift) & mask);
    }

    public int get(int i) {
        if (data == null) return palette[0];
        int v = rawGet(i);
        return global ? v : palette[v];
    }

    public int get(int x, int y, int z) { return get((y << 8) | (z << 4) | x); }

    public void set(int x, int y, int z, int state) { set((y << 8) | (z << 4) | x, state); }

    public void set(int i, int state) {
        if (data == null && palette[0] == state) return;
        if (global) { rawSet(i, state); return; }
        int idx = indexOf(state);
        if (idx < 0) {
            idx = paletteSize;
            if (paletteSize == palette.length) palette = Arrays.copyOf(palette, palette.length * 2);
            palette[paletteSize++] = state;
            int need = bitsFor(paletteSize);
            if (data == null || need > bits) grow(need);
        }
        if (global) rawSet(i, state);
        else rawSet(i, idx);
    }

    private int indexOf(int state) {
        for (int k = 0; k < paletteSize; k++) if (palette[k] == state) return k;
        return -1;
    }

    private static int bitsFor(int n) {
        int b = 32 - Integer.numberOfLeadingZeros(Math.max(1, n - 1));
        return Math.max(4, b);
    }

    private static int globalBits() { return Math.max(4, 32 - Integer.numberOfLeadingZeros(Math.max(1, Blocks.stateCount - 1))); }

    private void grow(int newBits) {
        int[] values = new int[SIZE];
        for (int k = 0; k < SIZE; k++) values[k] = get(k);
        boolean toGlobal = newBits > 13;
        if (toGlobal) {
            newBits = Math.max(globalBits(), 14);
            global = true;
        }
        bits = newBits;
        data = new long[(SIZE + perLong() - 1) / perLong()];
        for (int k = 0; k < SIZE; k++) rawSet(k, global ? values[k] : indexOf(values[k]));
        if (global) { palette = null; paletteSize = 0; }
    }

    /** Bulk load from flat state array (generation output); builds the minimal palette. */
    public static PalettedContainer fromArray(char[] states, int offset) {
        PalettedContainer c = new PalettedContainer(states[offset]);
        boolean uniform = true;
        for (int k = 1; k < SIZE; k++) if (states[offset + k] != states[offset]) { uniform = false; break; }
        if (uniform) return c;
        for (int k = 0; k < SIZE; k++) c.set(k, states[offset + k]);
        return c;
    }

    public void toArray(char[] out, int offset) {
        if (data == null) { Arrays.fill(out, offset, offset + SIZE, (char) palette[0]); return; }
        for (int k = 0; k < SIZE; k++) out[offset + k] = (char) get(k);
    }

    /** Re-pack to the minimal palette (after many edits). */
    public PalettedContainer compact() {
        char[] tmp = new char[SIZE];
        toArray(tmp, 0);
        return fromArray(tmp, 0);
    }

    // ---- serialization helpers ----
    public int[] paletteCopy() { return global ? null : Arrays.copyOf(palette, paletteSize); }

    public long[] dataCopy() { return data == null ? null : data.clone(); }

    public boolean isGlobal() { return global; }

    public static PalettedContainer restore(int[] palette, int bits, long[] data, boolean global) {
        PalettedContainer c = new PalettedContainer(global ? 0 : palette[0]);
        if (data == null) return c;
        c.global = global;
        c.palette = global ? null : palette.clone();
        c.paletteSize = global ? 0 : palette.length;
        c.bits = bits;
        c.data = data;
        return c;
    }
}
