package mc.core;

/** Read-only block/light access (Section 2.8). Implemented by the server World and the client's snapshot world. */
public interface IChunkAccess {
    int getBlock(int x, int y, int z);

    int getSkyLight(int x, int y, int z);

    int getBlockLight(int x, int y, int z);
}
