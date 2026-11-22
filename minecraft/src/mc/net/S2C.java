package mc.net;

import mc.content.ItemStack;

import java.util.List;

/** Server → client packets. Chunk data are immutable snapshots, so the client never touches server state. */
public final class S2C {
    private S2C() { }

    public interface Packet { }

    public record ChunkData(int cx, int cz, char[] blocks, byte[] light, byte[] biomes) implements Packet { }

    public record ChunkUnload(int cx, int cz) implements Packet { }

    public record Dimension(String id, boolean hasSky, int fogColor) implements Packet { }

    public record Sound(String id, double x, double y, double z, float volume, float pitch) implements Packet { }

    public record Particles(String kind, double x, double y, double z, int count, int data) implements Packet { }

    public record Message(String text) implements Packet { }

    public record EntityFrame(int id, int type, double x, double y, double z, float yaw, float headYaw, float pitch, float limbSwing,
                              float limbAmount, int hurtTime, int deathTime, boolean dead, int data, float data2, int swing, boolean onFire,
                              double width, double height) { }

    public record Entities(List<EntityFrame> frames) implements Packet { }

    public record PlayerState(int entityId, double x, double y, double z, float health, float maxHealth, float absorption, int food,
                              float saturation, int air, int maxAir, int armor, int xpLevel, float xpProgress, int gameMode, boolean flying,
                              boolean dead, String deathMessage, ItemStack[] main, ItemStack[] armorSlots, ItemStack offhand, int selected,
                              boolean digging, int digX, int digY, int digZ, float digProgress, boolean sleeping, int sleepTimer,
                              float attackStrength, int hurtTime, boolean onFire, boolean submerged, boolean inLava, boolean sneaking,
                              boolean sprinting, boolean onGround, int useTicks, boolean usingBow, String[] effects, double eyeHeight,
                              float yaw, float pitch, float limbSwing, float limbAmount, int swing, boolean inPortal) implements Packet { }

    public record MenuState(int windowId, int kind, String title, ItemStack[] slots, ItemStack carried, int burn, int burnTotal, int cook,
                            int cookTotal) implements Packet { }

    public record MenuClosed() implements Packet { }

    public record WorldInfo(long gameTime, long dayTime, boolean raining, boolean thundering, int skyDarken, int moonPhase,
                            String biome, int loadedChunks, int pendingChunks, int entityCount, long[] stageNanos, double tickMs,
                            long seed, int difficulty) implements Packet { }
}
