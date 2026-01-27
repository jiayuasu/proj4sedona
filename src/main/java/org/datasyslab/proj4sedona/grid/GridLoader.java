package org.datasyslab.proj4sedona.grid;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
 * - Automatic loading from local file paths in +nadgrids
 * - Automatic loading from HTTP/HTTPS URLs in +nadgrids
 * - Automatic fetching from PROJ CDN (when enabled)
 * - Local caching of downloaded grids
 * 
 * Usage:
 * <pre>
 * // Manual loading
 * GridLoader.loadFile("conus", Path.of("/path/to/us_noaa_conus.tif"));
 * 
 * // Local file path in +nadgrids (auto-loaded)
 * new Proj("+proj=longlat +nadgrids=/path/to/grid.gsb");
 * new Proj("+proj=longlat +nadgrids=./relative/path/grid.tif");
 * 
 * // URL in +nadgrids (auto-downloaded)
 * new Proj("+proj=longlat +nadgrids=https://cdn.proj.org/uk_os_OSTN15_NTv2_OSGBtoETRS.tif");
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
     * Supports:
     * - Registry lookup (pre-loaded grids)
     * - HTTP/HTTPS URLs (auto-downloaded)
     * - Local file paths (absolute or relative)
     * - CDN auto-fetch (if enabled)
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

        // If not found and looks like a URL, try to download
        if (grid == null && isUrl(value)) {
            grid = tryLoadFromUrl(value, !optional);
        }

        // If not found and looks like a file path, try to load from local file
        if (grid == null && isFilePath(value)) {
            grid = tryLoadFromFile(value, !optional);
        }

        // If still not found and auto-fetch is enabled, try to fetch from CDN
        if (grid == null && GridCdnFetcher.isAutoFetchEnabled()) {
            grid = tryAutoFetch(value, !optional);
        }

        // If grid is mandatory but not found, throw an exception
        if (grid == null && !optional) {
            throw new RuntimeException("Required grid not found: " + value);
        }

        return new NadgridInfo(value, !optional, grid, false);
    }

    /**
     * Check if a value looks like an HTTP/HTTPS URL.
     */
    private static boolean isUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    /**
     * Check if a value looks like a file path.
     * Matches absolute paths, relative paths, and paths with directory separators.
     */
    private static boolean isFilePath(String value) {
        return value.startsWith("/") ||           // Unix absolute
               value.startsWith("./") ||          // Relative current dir
               value.startsWith("../") ||         // Relative parent dir
               value.contains(File.separator) ||  // Contains OS path separator
               (value.length() > 2 && value.charAt(1) == ':');  // Windows drive letter (e.g., C:\)
    }

    /**
     * Attempt to load a grid from a local file path.
     * 
     * @param filePath The file path
     * @param mandatory Whether the grid is mandatory
     * @return The loaded GridData, or null if load failed
     */
    private static GridData tryLoadFromFile(String filePath, boolean mandatory) {
        try {
            Path path = Path.of(filePath);
            if (Files.exists(path)) {
                // Debug: Loading grid from local file
                return loadFile(filePath, path);
            }
        } catch (IOException e) {
            // Grid loading failed
        }
        return null;
    }

    /**
     * Attempt to load a grid from an HTTP/HTTPS URL.
     * 
     * @param url The URL to download from
     * @param mandatory Whether the grid is mandatory
     * @return The loaded GridData, or null if download/load failed
     */
    private static GridData tryLoadFromUrl(String url, boolean mandatory) {
        try {
            // Debug: Downloading grid from URL
            
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(2))
                    .GET()
                    .build();
            
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            
            if (response.statusCode() == 200) {
                byte[] data = response.body();
                // Use the URL as the key (or extract filename from URL)
                String key = extractFilenameFromUrl(url);
                GridData grid = load(key, data);
                // Debug: Downloaded and loaded grid
                return grid;
            }
        } catch (Exception e) {
            // URL download failed
        }
        return null;
    }

    /**
     * Extract the filename from a URL.
     * E.g., "https://cdn.proj.org/uk_os_OSTN15.tif" -> "uk_os_OSTN15.tif"
     */
    private static String extractFilenameFromUrl(String url) {
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < url.length() - 1) {
            String filename = url.substring(lastSlash + 1);
            // Remove query parameters if any
            int queryStart = filename.indexOf('?');
            if (queryStart > 0) {
                filename = filename.substring(0, queryStart);
            }
            return filename;
        }
        return url;  // Fallback to full URL as key
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
            // Debug: Auto-fetching grid from CDN
            return GridCdnFetcher.fetchAndLoad(gridName);
        } catch (IOException e) {
            // CDN fetch failed
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
