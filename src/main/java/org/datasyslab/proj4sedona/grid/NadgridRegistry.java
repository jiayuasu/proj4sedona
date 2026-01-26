package org.datasyslab.proj4sedona.grid;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global registry for loaded NAD grids.
 */
public final class NadgridRegistry {
    private static final Map<String, GridData> loadedGrids = new ConcurrentHashMap<>();

    private NadgridRegistry() {}

    public static void put(String key, GridData grid) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Grid key cannot be null or empty");
        }
        if (grid == null) {
            loadedGrids.remove(key);
        } else {
            loadedGrids.put(key, grid);
        }
    }

    public static GridData get(String key) {
        return loadedGrids.get(key);
    }

    public static boolean has(String key) {
        return loadedGrids.containsKey(key);
    }

    public static GridData remove(String key) {
        return loadedGrids.remove(key);
    }

    public static void clear() {
        loadedGrids.clear();
    }

    public static int size() {
        return loadedGrids.size();
    }
}
