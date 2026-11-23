package mc.core;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Persistence boundary (Section 2.8). */
public interface ISaveStore {
    Chunk load(Dimension d, int cx, int cz) throws IOException;

    void save(Dimension d, Chunk c, List<Map<String, Object>> entities) throws IOException;

    void flush();

    void close();
}
