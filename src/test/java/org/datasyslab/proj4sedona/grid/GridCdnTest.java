package org.datasyslab.proj4sedona.grid;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PROJ CDN grid fetching functionality.
 * 
 * Note: These tests require network access to cdn.proj.org
 */
public class GridCdnTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        NadgridRegistry.clear();
        GridCdnFetcher.setAutoFetchEnabled(false);
        GridCdnFetcher.setCacheDirectory(null);
        GridCdnFetcher.resetCdnUrl();
    }

    @AfterEach
    void tearDown() {
        NadgridRegistry.clear();
        GridCdnFetcher.setAutoFetchEnabled(false);
        GridCdnFetcher.setCacheDirectory(null);
        GridCdnFetcher.resetCdnUrl();
    }

    @Test
    void testFetchFromCdn() throws IOException {
        // Fetch a small grid file from the CDN
        byte[] data = GridCdnFetcher.fetch("us_noaa_conus.tif");
        
        assertNotNull(data);
        assertTrue(data.length > 1000, "Grid file should have substantial content");
        
        // Verify it's a valid TIFF (magic bytes)
        assertEquals(0x49, data[0] & 0xFF); // 'I' - little-endian TIFF
        assertEquals(0x49, data[1] & 0xFF);
        
        System.out.println("Downloaded us_noaa_conus.tif: " + data.length + " bytes");
    }

    @Test
    void testFetchAndLoad() throws IOException {
        // Fetch and load a grid
        GridData grid = GridCdnFetcher.fetchAndLoad("us_noaa_conus.tif");
        
        assertNotNull(grid);
        assertFalse(grid.getSubgrids().isEmpty());
        
        // Verify it's registered
        assertTrue(GridLoader.has("us_noaa_conus.tif"));
        
        System.out.println("Loaded grid with " + grid.getSubgrids().size() + " subgrids");
    }

    @Test
    void testFetchAsyncFromCdn() throws ExecutionException, InterruptedException, TimeoutException {
        // Fetch asynchronously
        CompletableFuture<byte[]> future = GridCdnFetcher.fetchAsync("us_noaa_conus.tif");
        
        byte[] data = future.get(60, TimeUnit.SECONDS);
        
        assertNotNull(data);
        assertTrue(data.length > 1000);
        
        System.out.println("Async downloaded: " + data.length + " bytes");
    }

    @Test
    void testFetchWithCache() throws IOException {
        // Set up cache directory
        GridCdnFetcher.setCacheDirectory(tempDir);
        
        // First fetch - should download
        long startTime = System.currentTimeMillis();
        byte[] data1 = GridCdnFetcher.fetchWithCache("us_noaa_conus.tif");
        long downloadTime = System.currentTimeMillis() - startTime;
        
        assertNotNull(data1);
        
        // Verify file is cached
        Path cachedFile = tempDir.resolve("us_noaa_conus.tif");
        assertTrue(Files.exists(cachedFile));
        assertEquals(data1.length, Files.size(cachedFile));
        
        // Second fetch - should load from cache (much faster)
        startTime = System.currentTimeMillis();
        byte[] data2 = GridCdnFetcher.fetchWithCache("us_noaa_conus.tif");
        long cacheTime = System.currentTimeMillis() - startTime;
        
        assertArrayEquals(data1, data2);
        assertTrue(cacheTime < downloadTime, 
                "Cache read should be faster than download");
        
        System.out.println("Download time: " + downloadTime + "ms, Cache read: " + cacheTime + "ms");
    }

    @Test
    void testAutoFetchEnabled() throws IOException {
        // Enable auto-fetch
        GridLoader.setAutoFetch(true);
        GridLoader.setCacheDirectory(tempDir);
        
        // Grid should not be loaded initially
        assertFalse(GridLoader.has("us_noaa_conus.tif"));
        
        // Request grids - should auto-fetch
        List<NadgridInfo> grids = GridLoader.getNadgrids("@us_noaa_conus.tif,null");
        
        assertNotNull(grids);
        assertEquals(2, grids.size());
        
        // First grid should now be loaded
        NadgridInfo conusInfo = grids.get(0);
        assertEquals("us_noaa_conus.tif", conusInfo.getName());
        assertNotNull(conusInfo.getGrid(), "Grid should be auto-fetched");
        
        // Verify it's now in the registry
        assertTrue(GridLoader.has("us_noaa_conus.tif"));
        
        System.out.println("Auto-fetched grid successfully");
    }

    @Test
    void testAutoFetchDisabled() {
        // Auto-fetch is disabled by default
        assertFalse(GridLoader.isAutoFetchEnabled());
        
        // Grid should not be loaded
        assertFalse(GridLoader.has("us_noaa_conus.tif"));
        
        // Request grids - should NOT auto-fetch
        List<NadgridInfo> grids = GridLoader.getNadgrids("@us_noaa_conus.tif,null");
        
        assertNotNull(grids);
        assertEquals(2, grids.size());
        
        // Grid should still not be fetched
        NadgridInfo conusInfo = grids.get(0);
        assertNull(conusInfo.getGrid(), "Grid should not be fetched when auto-fetch is disabled");
    }

    @Test
    void testFetchNonExistentGrid() {
        // Try to fetch a non-existent grid
        assertThrows(IOException.class, () -> {
            GridCdnFetcher.fetch("non_existent_grid_12345.tif");
        });
    }

    @Test
    void testCacheSize() throws IOException {
        GridCdnFetcher.setCacheDirectory(tempDir);
        
        // Initially empty
        assertEquals(0, GridCdnFetcher.getCacheSize());
        
        // Fetch a grid
        GridCdnFetcher.fetchWithCache("us_noaa_conus.tif");
        
        // Now cache has content
        long size = GridCdnFetcher.getCacheSize();
        assertTrue(size > 0);
        
        System.out.println("Cache size: " + size + " bytes");
    }

    @Test
    void testClearCache() throws IOException {
        GridCdnFetcher.setCacheDirectory(tempDir);
        
        // Fetch a grid to populate cache
        GridCdnFetcher.fetchWithCache("us_noaa_conus.tif");
        assertTrue(GridCdnFetcher.getCacheSize() > 0);
        
        // Clear cache
        GridCdnFetcher.clearCache();
        assertEquals(0, GridCdnFetcher.getCacheSize());
    }

    @Test
    void testCustomCdnUrl() {
        // Set custom URL
        GridLoader.setCdnUrl("https://example.com/grids/");
        assertEquals("https://example.com/grids/", GridCdnFetcher.getCdnUrl());
        
        // Reset to default
        GridCdnFetcher.resetCdnUrl();
        assertEquals(GridCdnFetcher.DEFAULT_CDN_URL, GridCdnFetcher.getCdnUrl());
    }

    @Test
    void testFetchLargerGrid() throws IOException {
        // Test with a larger grid file (Canada)
        GridData grid = GridCdnFetcher.fetchAndLoad("ca_nrc_ntv2_0.tif");
        
        assertNotNull(grid);
        assertFalse(grid.getSubgrids().isEmpty());
        
        System.out.println("Canada grid: " + grid.getSubgrids().size() + " subgrids");
    }

    @Test
    void testDirectFetchFromLoader() throws IOException {
        // Use the GridLoader convenience method
        GridData grid = GridLoader.fetchFromCdn("us_noaa_conus.tif");
        
        assertNotNull(grid);
        assertTrue(GridLoader.has("us_noaa_conus.tif"));
    }
}
