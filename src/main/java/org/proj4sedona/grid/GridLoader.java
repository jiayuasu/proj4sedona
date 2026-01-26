package org.proj4sedona.grid;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Main entry point for loading NAD grid files.
 * Mirrors: lib/nadgrid.js default export
 */
public final class GridLoader {
    private static final byte[] TIFF_MAGIC_LE = {0x49, 0x49};
    private static final byte[] TIFF_MAGIC_BE = {0x4D, 0x4D};

    private GridLoader() {}

    public static GridData load(String key, byte[] data) throws IOException {
        if (data == null || data.length < 4) {
            throw new IllegalArgumentException("Invalid grid data");
        }
        GridData grid = isTiff(data) ? GeoTiffGridReader.read(data) : NTV2GridReader.read(data);
        NadgridRegistry.put(key, grid);
        return grid;
    }

    public static GridData loadFile(String key, Path path) throws IOException {
        return load(key, Files.readAllBytes(path));
    }

    public static GridData loadFile(String key, String path) throws IOException {
        return loadFile(key, Path.of(path));
    }

    public static List<NadgridInfo> getNadgrids(String nadgrids) {
        if (nadgrids == null || nadgrids.isEmpty()) return null;
        String[] parts = nadgrids.split(",");
        List<NadgridInfo> result = new ArrayList<>();
        for (String part : parts) {
            NadgridInfo info = parseNadgridString(part.trim());
            if (info != null) result.add(info);
        }
        return result.isEmpty() ? null : result;
    }

    private static NadgridInfo parseNadgridString(String value) {
        if (value.isEmpty()) return null;
        boolean optional = value.charAt(0) == '@';
        if (optional) value = value.substring(1);
        if ("null".equals(value)) return new NadgridInfo("null", !optional, null, true);
        return new NadgridInfo(value, !optional, NadgridRegistry.get(value), false);
    }

    private static boolean isTiff(byte[] data) {
        return data.length >= 2 && 
               ((data[0] == TIFF_MAGIC_LE[0] && data[1] == TIFF_MAGIC_LE[1]) ||
                (data[0] == TIFF_MAGIC_BE[0] && data[1] == TIFF_MAGIC_BE[1]));
    }

    public static GridData get(String key) { return NadgridRegistry.get(key); }
    public static boolean has(String key) { return NadgridRegistry.has(key); }
    public static GridData remove(String key) { return NadgridRegistry.remove(key); }
    public static void clear() { NadgridRegistry.clear(); }
}
