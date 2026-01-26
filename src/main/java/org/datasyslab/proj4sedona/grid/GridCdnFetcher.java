package org.datasyslab.proj4sedona.grid;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Fetches datum grid files from the PROJ CDN (https://cdn.proj.org/).
 * 
 * This class provides automatic downloading of NTv2 and GeoTIFF grid files
 * for datum transformations. Downloaded files can optionally be cached locally.
 * 
 * Usage:
 * <pre>
 * // Simple fetch
 * byte[] data = GridCdnFetcher.fetch("us_noaa_conus.tif");
 * 
 * // Fetch with caching
 * GridCdnFetcher.setCacheDirectory(Path.of("/path/to/cache"));
 * byte[] data = GridCdnFetcher.fetchWithCache("us_noaa_conus.tif");
 * 
 * // Async fetch
 * CompletableFuture&lt;byte[]&gt; future = GridCdnFetcher.fetchAsync("us_noaa_conus.tif");
 * </pre>
 */
public final class GridCdnFetcher {

    /** Default PROJ CDN URL */
    public static final String DEFAULT_CDN_URL = "https://cdn.proj.org/";

    /** Connection timeout in seconds */
    private static final int CONNECT_TIMEOUT_SECONDS = 30;

    /** Read timeout in seconds */
    private static final int READ_TIMEOUT_SECONDS = 120;

    /** Current CDN URL (can be customized) */
    private static String cdnUrl = DEFAULT_CDN_URL;

    /** Optional cache directory for downloaded grids */
    private static Path cacheDirectory = null;

    /** Whether auto-fetching is enabled */
    private static boolean autoFetchEnabled = false;

    /** Shared HTTP client */
    private static volatile HttpClient httpClient;

    private GridCdnFetcher() {
        // Utility class
    }

    /**
     * Get the HTTP client, creating it lazily.
     */
    private static HttpClient getHttpClient() {
        if (httpClient == null) {
            synchronized (GridCdnFetcher.class) {
                if (httpClient == null) {
                    httpClient = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .build();
                }
            }
        }
        return httpClient;
    }

    /**
     * Fetch a grid file from the CDN.
     * 
     * @param gridName The grid file name (e.g., "us_noaa_conus.tif")
     * @return The grid file data as a byte array
     * @throws IOException if the fetch fails
     */
    public static byte[] fetch(String gridName) throws IOException {
        String url = buildUrl(gridName);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
                .GET()
                .build();

        try {
            HttpResponse<byte[]> response = getHttpClient().send(request, 
                    HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 200) {
                return response.body();
            } else if (response.statusCode() == 404) {
                throw new IOException("Grid file not found on CDN: " + gridName);
            } else {
                throw new IOException("Failed to fetch grid file: " + gridName + 
                        " (HTTP " + response.statusCode() + ")");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Fetch interrupted for grid: " + gridName, e);
        }
    }

    /**
     * Fetch a grid file asynchronously.
     * 
     * @param gridName The grid file name
     * @return A CompletableFuture that will contain the grid data
     */
    public static CompletableFuture<byte[]> fetchAsync(String gridName) {
        String url = buildUrl(gridName);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
                .GET()
                .build();

        return getHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return response.body();
                    } else if (response.statusCode() == 404) {
                        throw new RuntimeException("Grid file not found on CDN: " + gridName);
                    } else {
                        throw new RuntimeException("Failed to fetch grid file: " + gridName +
                                " (HTTP " + response.statusCode() + ")");
                    }
                });
    }

    /**
     * Fetch a grid file with local caching.
     * If the file exists in the cache directory, it's loaded from there.
     * Otherwise, it's fetched from the CDN and saved to the cache.
     * 
     * @param gridName The grid file name
     * @return The grid file data
     * @throws IOException if the fetch fails
     */
    public static byte[] fetchWithCache(String gridName) throws IOException {
        // Try to load from cache first
        if (cacheDirectory != null) {
            Path cachedFile = cacheDirectory.resolve(gridName);
            if (Files.exists(cachedFile)) {
                return Files.readAllBytes(cachedFile);
            }
        }

        // Fetch from CDN
        byte[] data = fetch(gridName);

        // Save to cache if configured
        if (cacheDirectory != null && data != null) {
            try {
                Files.createDirectories(cacheDirectory);
                Path cachedFile = cacheDirectory.resolve(gridName);
                Files.write(cachedFile, data, 
                        StandardOpenOption.CREATE, 
                        StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                // Log but don't fail if caching fails
                System.err.println("Warning: Failed to cache grid file " + gridName + ": " + e.getMessage());
            }
        }

        return data;
    }

    /**
     * Fetch and load a grid file, registering it with the GridLoader.
     * Uses caching if a cache directory is configured.
     * 
     * @param gridName The grid file name (used as both the CDN filename and registry key)
     * @return The loaded GridData
     * @throws IOException if the fetch or load fails
     */
    public static GridData fetchAndLoad(String gridName) throws IOException {
        byte[] data = cacheDirectory != null ? fetchWithCache(gridName) : fetch(gridName);
        return GridLoader.load(gridName, data);
    }

    /**
     * Fetch and load a grid file asynchronously.
     * 
     * @param gridName The grid file name
     * @return A CompletableFuture that will contain the loaded GridData
     */
    public static CompletableFuture<GridData> fetchAndLoadAsync(String gridName) {
        return fetchAsync(gridName).thenApply(data -> {
            try {
                return GridLoader.load(gridName, data);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load grid: " + gridName, e);
            }
        });
    }

    /**
     * Build the full URL for a grid file.
     */
    private static String buildUrl(String gridName) {
        String base = cdnUrl.endsWith("/") ? cdnUrl : cdnUrl + "/";
        return base + gridName;
    }

    // ========== Configuration Methods ==========

    /**
     * Set the CDN URL.
     * 
     * @param url The CDN base URL (e.g., "https://cdn.proj.org/")
     */
    public static void setCdnUrl(String url) {
        cdnUrl = url;
    }

    /**
     * Get the current CDN URL.
     * 
     * @return The CDN URL
     */
    public static String getCdnUrl() {
        return cdnUrl;
    }

    /**
     * Reset the CDN URL to the default.
     */
    public static void resetCdnUrl() {
        cdnUrl = DEFAULT_CDN_URL;
    }

    /**
     * Set the cache directory for downloaded grid files.
     * 
     * @param directory The cache directory path, or null to disable caching
     */
    public static void setCacheDirectory(Path directory) {
        cacheDirectory = directory;
    }

    /**
     * Get the current cache directory.
     * 
     * @return The cache directory, or null if caching is disabled
     */
    public static Path getCacheDirectory() {
        return cacheDirectory;
    }

    /**
     * Enable or disable automatic fetching of grid files.
     * When enabled, GridLoader will automatically fetch missing grids from the CDN.
     * 
     * @param enabled true to enable auto-fetching
     */
    public static void setAutoFetchEnabled(boolean enabled) {
        autoFetchEnabled = enabled;
    }

    /**
     * Check if auto-fetching is enabled.
     * 
     * @return true if auto-fetching is enabled
     */
    public static boolean isAutoFetchEnabled() {
        return autoFetchEnabled;
    }

    /**
     * Clear the cache directory (delete all cached files).
     * 
     * @throws IOException if the cache cannot be cleared
     */
    public static void clearCache() throws IOException {
        if (cacheDirectory != null && Files.exists(cacheDirectory)) {
            Files.walk(cacheDirectory)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Ignore individual file deletion failures
                        }
                    });
        }
    }

    /**
     * Get the size of the cache in bytes.
     * 
     * @return The total size of cached files, or 0 if no cache
     * @throws IOException if the cache cannot be read
     */
    public static long getCacheSize() throws IOException {
        if (cacheDirectory == null || !Files.exists(cacheDirectory)) {
            return 0;
        }
        return Files.walk(cacheDirectory)
                .filter(Files::isRegularFile)
                .mapToLong(path -> {
                    try {
                        return Files.size(path);
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .sum();
    }
}
