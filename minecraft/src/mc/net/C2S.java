package mc.net;

/** Client → server packets (Section 2.3: in-process packet queue; block edits are server-authoritative). */
public final class C2S {
    private C2S() { }

    public interface Packet { }

    public record Input(float forward, float strafe, boolean jump, boolean sneak, boolean sprint, float yaw, float pitch) implements Packet { }

    public record Dig(int x, int y, int z, int face) implements Packet { }

    public record StopDig() implements Packet { }

    public record UseOn(int x, int y, int z, int face, double hx, double hy, double hz) implements Packet { }

    public record UseItem() implements Packet { }

    public record ReleaseUse() implements Packet { }

    public record Attack(int entityId) implements Packet { }

    public record Interact(int entityId) implements Packet { }

    public record Select(int slot) implements Packet { }

    public record Drop(boolean all) implements Packet { }

    public record SwapHands() implements Packet { }

    public record Click(int windowId, int slot, int button, boolean shift) implements Packet { }

    public record CloseMenu() implements Packet { }

    public record Chat(String text) implements Packet { }

    public record Respawn() implements Packet { }

    public record PickBlock(int x, int y, int z) implements Packet { }

    public record SetFlying(boolean flying) implements Packet { }

    public record CreativeGive(String itemId, int count) implements Packet { }

    public record SetViewDistance(int chunks) implements Packet { }

    public record OpenInventory() implements Packet { }

    public record SetPaused(boolean paused) implements Packet { }
}
