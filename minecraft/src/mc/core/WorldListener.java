package mc.core;

/** Server-side sink for world events (block deltas, sounds, particles) that become packets to clients. */
public interface WorldListener {
    void chunkChanged(World w, Chunk c);

    void chunkUnloaded(World w, int cx, int cz);

    void sound(World w, String event, double x, double y, double z, float volume, float pitch);

    void particles(World w, String kind, double x, double y, double z, int count, int data);

    void message(String text);

    WorldListener NULL = new WorldListener() {
        public void chunkChanged(World w, Chunk c) { }
        public void chunkUnloaded(World w, int cx, int cz) { }
        public void sound(World w, String event, double x, double y, double z, float volume, float pitch) { }
        public void particles(World w, String kind, double x, double y, double z, int count, int data) { }
        public void message(String text) { }
    };
}
