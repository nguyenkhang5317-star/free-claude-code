package mc.core;

/** Stable world interface (Section 2.8) seen by content code; enables headless test mode. */
public interface IWorld extends IChunkAccess {

    boolean setBlock(int x, int y, int z, int state);

    void playSound(String eventId, double x, double y, double z, float volume, float pitch);
}
