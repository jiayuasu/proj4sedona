package org.apache.sedona.proj.datum;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Client for downloading datum grids from the PROJ CDN.
 * This provides easy access to the official PROJ datum grids hosted at https://cdn.proj.org/
 */
public class ProjCdnClient {
    
    // Base URL for PROJ CDN
    private static final String CDN_BASE_URL = "https://cdn.proj.org/";
    
    // Cache for downloaded grids to avoid re-downloading
    private static final Map<String, GeoTiffReader.GeoTiffGrid> GRID_CACHE = new ConcurrentHashMap<>();
    
    /**
     * Downloads and loads a datum grid from the PROJ CDN.
     * @param gridName the name of the grid file (e.g., "ca_nrc_NA83SCRS.tif")
     * @return the loaded GeoTIFF grid
     * @throws IOException if downloading or parsing fails
     */
    public static GeoTiffReader.GeoTiffGrid downloadGrid(String gridName) throws IOException {
        if (gridName == null || gridName.isEmpty()) {
            throw new IllegalArgumentException("Grid name cannot be null or empty");
        }
        
        // Check cache first
        if (GRID_CACHE.containsKey(gridName)) {
            return GRID_CACHE.get(gridName);
        }
        
        // Construct URL
        String url = CDN_BASE_URL + gridName;
        
        // Download and load the grid
        GeoTiffReader.GeoTiffGrid grid = GeoTiffReader.loadFromUrl(gridName, url);
        
        // Cache the result
        GRID_CACHE.put(gridName, grid);
        
        return grid;
    }
    
    /**
     * Downloads and loads a datum grid from the PROJ CDN with a custom key.
     * @param key the key to associate with the loaded grid
     * @param gridName the name of the grid file (e.g., "ca_nrc_NA83SCRS.tif")
     * @return the loaded GeoTIFF grid
     * @throws IOException if downloading or parsing fails
     */
    public static GeoTiffReader.GeoTiffGrid downloadGrid(String key, String gridName) throws IOException {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }
        if (gridName == null || gridName.isEmpty()) {
            throw new IllegalArgumentException("Grid name cannot be null or empty");
        }
        
        // Check cache first
        if (GRID_CACHE.containsKey(key)) {
            return GRID_CACHE.get(key);
        }
        
        // Construct URL
        String url = CDN_BASE_URL + gridName;
        
        // Download and load the grid
        GeoTiffReader.GeoTiffGrid grid = GeoTiffReader.loadFromUrl(key, url);
        
        // Cache the result
        GRID_CACHE.put(key, grid);
        
        return grid;
    }
    
    /**
     * Downloads and loads a datum grid from a custom CDN URL.
     * @param key the key to associate with the loaded grid
     * @param url the full URL to the grid file
     * @return the loaded GeoTIFF grid
     * @throws IOException if downloading or parsing fails
     */
    public static GeoTiffReader.GeoTiffGrid downloadGridFromUrl(String key, String url) throws IOException {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("URL cannot be null or empty");
        }
        
        // Check cache first
        if (GRID_CACHE.containsKey(key)) {
            return GRID_CACHE.get(key);
        }
        
        // Download and load the grid
        GeoTiffReader.GeoTiffGrid grid = GeoTiffReader.loadFromUrl(key, url);
        
        // Cache the result
        GRID_CACHE.put(key, grid);
        
        return grid;
    }
    
    /**
     * Checks if a grid is available in the cache.
     * @param key the grid key
     * @return true if the grid is cached
     */
    public static boolean isGridCached(String key) {
        return GRID_CACHE.containsKey(key);
    }
    
    /**
     * Removes a grid from the cache.
     * @param key the grid key
     * @return the removed grid, or null if not found
     */
    public static GeoTiffReader.GeoTiffGrid removeFromCache(String key) {
        if (key == null) {
            return null;
        }
        return GRID_CACHE.remove(key);
    }
    
    /**
     * Clears the grid cache.
     */
    public static void clearCache() {
        GRID_CACHE.clear();
    }
    
    /**
     * Gets the number of grids in the cache.
     * @return the cache size
     */
    public static int getCacheSize() {
        return GRID_CACHE.size();
    }
    
    /**
     * Gets all cached grid keys.
     * @return array of cached grid keys
     */
    public static String[] getCachedGridKeys() {
        return GRID_CACHE.keySet().toArray(new String[0]);
    }
    
    /**
     * Constructs the full CDN URL for a given grid name.
     * @param gridName the grid file name
     * @return the full CDN URL
     */
    public static String getCdnUrl(String gridName) {
        if (gridName == null || gridName.isEmpty()) {
            throw new IllegalArgumentException("Grid name cannot be null or empty");
        }
        return CDN_BASE_URL + gridName;
    }
    
    /**
     * Downloads a grid file as an InputStream without parsing it.
     * This can be useful for custom processing or validation.
     * @param gridName the name of the grid file
     * @return InputStream containing the grid data
     * @throws IOException if downloading fails
     */
    public static InputStream downloadGridStream(String gridName) throws IOException {
        if (gridName == null || gridName.isEmpty()) {
            throw new IllegalArgumentException("Grid name cannot be null or empty");
        }
        String url = CDN_BASE_URL + gridName;
        return new URL(url).openStream();
    }
}
