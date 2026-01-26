package org.proj4sedona.grid;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Main entry point for loading NAD grid files.
 * Mirrors: lib/nadgrid.js default export
 * 
 * Supports loading NTv2 (.gsb) and GeoTIFF (.tif) grid files for datum
 * transformations. Grid files can be obtained from https://cdn.proj.org/
 * 
 * Features:
 * - Manual loading from files or byte arrays
 * - Automatic fetching from PROJ CDN (when enabled)
 * - Local caching of downloaded grids
 * 
 * Usage:
 * <pre>
 * // Manual loading
 * GridLoader.loadFile("conus", Path.of("/path/to/us_noaa_conus.tif"));
 * 
 * // Enable auto-fetching from CDN
 * GridLoader.setAutoFetch(true);
 * GridLoader.setCacheDirectory(Path.of("/path/to/cache"));
 * 
 * // Now grids are fetched automatically when needed
 * List&lt;NadgridInfo&gt; grids = GridLoader.getNadgrids("@us_noaa_conus.tif,null");
 * </pre>
 */
public final class GridLoader {

    /** TIFF magic bytes (little-endian) */
    private static final byte[] TIFF_MAGIC_LE = {0x49, 0x49};

    /** TIFF magic bytes (big-endian) */
    private static final byte[] TIFF_MAGIC_BE = {0x4D, 0x4D};

    private GridLoader() {
        // Utility class
    }

    /**
     * Load a grid from a byte array.
     * The format (NTv2 or GeoTIFF) is auto-detected.
     * 
     * @param key The key to associate with the grid
     * @param data The grid file data
     * @return The loaded GridData
     * @throws IOException if the data cannot be parsed
     */
    public static GridData load(String key, byte[] data) throws IOException {
        if (data == null || data.length < 4) {
            throw new IllegalArgumentException("Invalid grid data");
        }

        GridData grid;
        if (isTiff(data)) {
            grid = GeoTiffGridReader.read(data);
        } else {
            grid = NTV2GridReader.read(data);
        }

        NadgridRegistry.put(key, grid);
        return grid;
    }

    /**
     * Load a grid from a file path.
     * 
     * @param key The key to associate with the grid
     * @param path The path to the grid file
     * @return The loaded GridData
     * @throws IOException if the file cannot be read
     */
    public static GridData loadFile(String key, Path path) throws IOException {
        byte[] data = Files.readAllBytes(path);
        return load(key, data);
    }

    /**
     * Load a grid from a file path string.
     * 
     * @param key The key to associate with the grid
     * @param path The path to the grid file
     * @return The loaded GridData
     * @throws IOException if the file cannot be read
     */
    public static GridData loadFile(String key, String path) throws IOException {
        return loadFile(key, Path.of(path));
    }

    /**
     * Parse a nadgrids string and return information about each grid.
     * If auto-fetch is enabled, missing grids will be downloaded from the CDN.
     * 
     * The nadgrids string is a comma-separated list of grid names.
     * Names prefixed with '@' are optional (non-mandatory).
     * The special name "null" indicates no grid shift should be applied.
     * 
     * @param nadgrids The nadgrids string (e.g., "@us_noaa_conus.tif,null")
     * @return List of NadgridInfo objects
     */
    public static List<NadgridInfo> getNadgrids(String nadgrids) {
        if (nadgrids == null || nadgrids.isEmpty()) {
            return null;
        }

        String[] parts = nadgrids.split(",");
        List<NadgridInfo> result = new ArrayList<>();

        for (String part : parts) {
            NadgridInfo info = parseNadgridString(part.trim());
            if (info != null) {
                result.add(info);
            }
        }

        return result.isEmpty() ? null : result;
    }

    /**
     * Parse a single nadgrid string.
     * If auto-fetch is enabled and the grid is not loaded, attempts to fetch it.
     */
    private static NadgridInfo parseNadgridString(String value) {
        if (value.isEmpty()) {
            return null;
        }

        boolean optional = value.charAt(0) == '@';
        if (optional) {
            value = value.substring(1);
        }

        if ("null".equals(value)) {
            return new NadgridInfo("null", !optional, null, true);
        }

        // Try to get from registry first
        GridData grid = NadgridRegistry.get(value);

        // If not found and auto-fetch is enabled, try to fetch from CDN
        if (grid == null && GridCdnFetcher.isAutoFetchEnabled()) {
            grid = tryAutoFetch(value, !optional);
        }

        return new NadgridInfo(value, !optional, grid, false);
    }

    /**
     * Attempt to auto-fetch a grid from the CDN.
     * 
     * @param gridName The grid name
     * @param mandatory Whether the grid is mandatory
     * @return The loaded GridData, or null if fetch failed
     */
    private static GridData tryAutoFetch(String gridName, boolean mandatory) {
        try {
            System.out.println("Auto-fetching grid from CDN: " + gridName);
            return GridCdnFetcher.fetchAndLoad(gridName);
        } catch (IOException e) {
            if (mandatory) {
                System.err.println("Warning: Failed to fetch mandatory grid '" + gridName + 
                        "' from CDN: " + e.getMessage());
            }
            return null;
        }
    }

    /**
     * Check if data is a TIFF file based on magic bytes.
     */
    private static boolean isTiff(byte[] data) {
        if (data.length < 2) {
            return false;
        }
        return (data[0] == TIFF_MAGIC_LE[0] && data[1] == TIFF_MAGIC_LE[1]) ||
               (data[0] == TIFF_MAGIC_BE[0] && data[1] == TIFF_MAGIC_BE[1]);
    }

    // ========== Registry Access Methods ==========

    /**
     * Get a loaded grid by key.
     * 
     * @param key The grid key
     * @return The grid, or null if not loaded
     */
    public static GridData get(String key) {
        return NadgridRegistry.get(key);
    }

    /**
     * Check if a grid is loaded.
     * 
     * @param key The grid key
     * @return true if the grid is loaded
     */
    public static boolean has(String key) {
        return NadgridRegistry.has(key);
    }

    /**
     * Remove a loaded grid.
     * 
     * @param key The grid key
     * @return The removed grid, or null if not found
     */
    public static GridData remove(String key) {
        return NadgridRegistry.remove(key);
    }

    /**
     * Clear all loaded grids.
     */
    public static void clear() {
        NadgridRegistry.clear();
    }

    // ========== CDN Configuration Shortcuts ==========

    /**
     * Enable or disable automatic fetching from the PROJ CDN.
     * When enabled, missing grids will be downloaded automatically.
     * 
     * @param enabled true to enable auto-fetching
     */
    public static void setAutoFetch(boolean enabled) {
        GridCdnFetcher.setAutoFetchEnabled(enabled);
    }

    /**
     * Check if auto-fetching is enabled.
     * 
     * @return true if auto-fetching is enabled
     */
    public static boolean isAutoFetchEnabled() {
        return GridCdnFetcher.isAutoFetchEnabled();
    }

    /**
     * Set the cache directory for downloaded grids.
     * When set, downloaded grids are saved locally for future use.
     * 
     * @param directory The cache directory, or null to disable caching
     */
    public static void setCacheDirectory(Path directory) {
        GridCdnFetcher.setCacheDirectory(directory);
    }

    /**
     * Get the current cache directory.
     * 
     * @return The cache directory, or null if caching is disabled
     */
    public static Path getCacheDirectory() {
        return GridCdnFetcher.getCacheDirectory();
    }

    /**
     * Set a custom CDN URL.
     * 
     * @param url The CDN base URL
     */
    public static void setCdnUrl(String url) {
        GridCdnFetcher.setCdnUrl(url);
    }

    /**
     * Fetch a specific grid from the CDN without auto-fetch being enabled.
     * Useful for pre-loading grids.
     * 
     * @param gridName The grid file name (e.g., "us_noaa_conus.tif")
     * @return The loaded GridData
     * @throws IOException if the fetch fails
     */
    public static GridData fetchFromCdn(String gridName) throws IOException {
        return GridCdnFetcher.fetchAndLoad(gridName);
    }
}
