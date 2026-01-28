package org.datasyslab.proj4sedona.defs;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.datasyslab.proj4sedona.Proj4;
import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.core.ProjectionDef;
import org.datasyslab.proj4sedona.transform.Converter;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SpatialReferenceFetcher and Defs remote lookup functionality.
 * 
 * <p>These tests require network access to spatialreference.org.
 * Some tests may be slow due to network latency.</p>
 */
class SpatialReferenceFetcherTest {

    @BeforeEach
    void setUp() {
        // Reset all state before each test
        Defs.reset();
        SpatialReferenceFetcher.reset();
    }

    @AfterEach
    void tearDown() {
        // Clean up after each test
        Defs.reset();
        SpatialReferenceFetcher.reset();
    }

    // ==================== SpatialReferenceFetcher Direct Tests ====================

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testFetchProjJson_EPSG2154() {
        // EPSG:2154 - RGF93 / Lambert-93 (French national projection)
        SpatialReferenceFetcher.FetchResult result = SpatialReferenceFetcher.fetchProjJson("epsg", "2154");
        
        assertTrue(result.isSuccess(), "Should fetch PROJJSON for EPSG:2154");
        assertNotNull(result.getProjJson());
        assertTrue(result.getProjJson().contains("RGF93") || result.getProjJson().contains("Lambert"), 
                "PROJJSON should contain projection name");
        assertTrue(result.getProjJson().startsWith("{"), "Should be valid JSON");
        assertEquals(1, result.getAttemptCount(), "Should succeed on first attempt");
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testFetchProjJson_NonExistent() {
        // EPSG:999999 should not exist
        SpatialReferenceFetcher.FetchResult result = SpatialReferenceFetcher.fetchProjJson("epsg", "999999");
        
        assertTrue(result.isNotFound(), "Should return NOT_FOUND for non-existent EPSG code");
        assertNull(result.getProjJson());
    }

    @Test
    void testNegativeCache() {
        // First call should add to negative cache
        SpatialReferenceFetcher.FetchResult result1 = SpatialReferenceFetcher.fetchProjJson("epsg", "999999");
        assertTrue(result1.isNotFound());
        
        // Verify it's in the negative cache
        assertTrue(SpatialReferenceFetcher.isInNotFoundCache("epsg", "999999"),
                "Non-existent code should be in negative cache");
        
        // Second call should return from cache immediately (0 attempts)
        SpatialReferenceFetcher.FetchResult result2 = SpatialReferenceFetcher.fetchProjJson("epsg", "999999");
        assertTrue(result2.isNotFound());
        assertEquals(0, result2.getAttemptCount(), "Should return from cache with 0 attempts");
    }

    @Test
    void testClearNotFoundCache() {
        // Add to negative cache
        SpatialReferenceFetcher.fetchProjJson("epsg", "999999");
        assertTrue(SpatialReferenceFetcher.isInNotFoundCache("epsg", "999999"));
        
        // Clear cache
        SpatialReferenceFetcher.clearNotFoundCache();
        
        // Should no longer be in cache
        assertFalse(SpatialReferenceFetcher.isInNotFoundCache("epsg", "999999"));
        assertEquals(0, SpatialReferenceFetcher.getNotFoundCacheSize());
    }

    @Test
    void testBaseUrlConfiguration() {
        // Get default
        assertEquals(SpatialReferenceFetcher.DEFAULT_BASE_URL, 
                SpatialReferenceFetcher.getBaseUrl());
        
        // Set custom
        SpatialReferenceFetcher.setBaseUrl("https://custom.example.org/");
        assertEquals("https://custom.example.org/", SpatialReferenceFetcher.getBaseUrl());
        
        // Reset
        SpatialReferenceFetcher.resetBaseUrl();
        assertEquals(SpatialReferenceFetcher.DEFAULT_BASE_URL, 
                SpatialReferenceFetcher.getBaseUrl());
    }

    @Test
    void testTimeoutConfiguration() {
        // Verify defaults
        assertEquals(SpatialReferenceFetcher.DEFAULT_CONNECT_TIMEOUT_SECONDS, 
                SpatialReferenceFetcher.getConnectTimeout());
        assertEquals(SpatialReferenceFetcher.DEFAULT_READ_TIMEOUT_SECONDS, 
                SpatialReferenceFetcher.getReadTimeout());
        
        // Set custom timeouts
        SpatialReferenceFetcher.setConnectTimeout(5);
        SpatialReferenceFetcher.setReadTimeout(15);
        
        assertEquals(5, SpatialReferenceFetcher.getConnectTimeout());
        assertEquals(15, SpatialReferenceFetcher.getReadTimeout());
        
        // Reset restores defaults
        SpatialReferenceFetcher.reset();
        assertEquals(SpatialReferenceFetcher.DEFAULT_CONNECT_TIMEOUT_SECONDS, 
                SpatialReferenceFetcher.getConnectTimeout());
    }

    @Test
    void testRetryConfiguration() {
        // Verify defaults
        assertEquals(SpatialReferenceFetcher.DEFAULT_MAX_RETRIES, 
                SpatialReferenceFetcher.getMaxRetries());
        assertEquals(SpatialReferenceFetcher.DEFAULT_INITIAL_BACKOFF_MS, 
                SpatialReferenceFetcher.getInitialBackoffMs());
        
        // Set custom
        SpatialReferenceFetcher.setMaxRetries(5);
        SpatialReferenceFetcher.setInitialBackoffMs(100);
        
        assertEquals(5, SpatialReferenceFetcher.getMaxRetries());
        assertEquals(100, SpatialReferenceFetcher.getInitialBackoffMs());
        
        // Reset restores defaults
        SpatialReferenceFetcher.reset();
        assertEquals(SpatialReferenceFetcher.DEFAULT_MAX_RETRIES, 
                SpatialReferenceFetcher.getMaxRetries());
    }

    // ==================== Network Failure Tests ====================

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testFetchWithUnreachableServer_RetriesAndFails() {
        // Configure for fast failure
        SpatialReferenceFetcher.setBaseUrl("http://localhost:59999/");  // Unreachable port
        SpatialReferenceFetcher.setConnectTimeout(1);  // 1 second timeout
        SpatialReferenceFetcher.setReadTimeout(1);
        SpatialReferenceFetcher.setMaxRetries(3);
        SpatialReferenceFetcher.setInitialBackoffMs(100);  // Fast backoff for testing
        SpatialReferenceFetcher.resetAttemptCounter();
        
        long startTime = System.currentTimeMillis();
        SpatialReferenceFetcher.FetchResult result = SpatialReferenceFetcher.fetchProjJson("epsg", "4326");
        long elapsed = System.currentTimeMillis() - startTime;
        
        assertTrue(result.isNetworkError(), "Should return NETWORK_ERROR for unreachable server");
        assertNotNull(result.getLastException(), "Should have a last exception");
        assertEquals(3, result.getAttemptCount(), "Should have attempted 3 times");
        
        // Verify total attempts counter
        assertEquals(3, SpatialReferenceFetcher.getTotalAttemptCount(), 
            "Total attempt counter should reflect 3 attempts");
        
        // Verify it took some time (due to retries and backoff)
        // With 100ms initial backoff, expect at least ~200ms (100 + 200 for 2 backoffs)
        assertTrue(elapsed >= 200, "Should have taken time for retries, took " + elapsed + "ms");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testFetchRetryCount_VerifyExactlyThreeAttempts() {
        // Configure for fast failure with exactly 3 retries
        SpatialReferenceFetcher.setBaseUrl("http://localhost:59998/");  // Different unreachable port
        SpatialReferenceFetcher.setConnectTimeout(1);
        SpatialReferenceFetcher.setReadTimeout(1);
        SpatialReferenceFetcher.setMaxRetries(3);
        SpatialReferenceFetcher.setInitialBackoffMs(50);
        SpatialReferenceFetcher.resetAttemptCounter();
        
        // Make multiple fetch calls to verify counter increments correctly
        SpatialReferenceFetcher.FetchResult result1 = SpatialReferenceFetcher.fetchProjJson("epsg", "1234");
        assertEquals(3, result1.getAttemptCount(), "First call should make 3 attempts");
        assertEquals(3, SpatialReferenceFetcher.getTotalAttemptCount());
        
        // Second call should also make 3 attempts
        SpatialReferenceFetcher.FetchResult result2 = SpatialReferenceFetcher.fetchProjJson("epsg", "5678");
        assertEquals(3, result2.getAttemptCount(), "Second call should also make 3 attempts");
        assertEquals(6, SpatialReferenceFetcher.getTotalAttemptCount(), "Total should be 6 attempts");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testFetchWithCustomRetryCount() {
        // Configure for 5 retries
        SpatialReferenceFetcher.setBaseUrl("http://localhost:59997/");
        SpatialReferenceFetcher.setConnectTimeout(1);
        SpatialReferenceFetcher.setReadTimeout(1);
        SpatialReferenceFetcher.setMaxRetries(5);
        SpatialReferenceFetcher.setInitialBackoffMs(10);
        SpatialReferenceFetcher.resetAttemptCounter();
        
        SpatialReferenceFetcher.FetchResult result = SpatialReferenceFetcher.fetchProjJson("epsg", "4326");
        
        assertTrue(result.isNetworkError());
        assertEquals(5, result.getAttemptCount(), "Should have attempted 5 times");
        assertEquals(5, SpatialReferenceFetcher.getTotalAttemptCount());
    }

    @Test
    void testAttemptCounterReset() {
        SpatialReferenceFetcher.setBaseUrl("http://localhost:59996/");
        SpatialReferenceFetcher.setConnectTimeout(1);
        SpatialReferenceFetcher.setMaxRetries(2);
        SpatialReferenceFetcher.setInitialBackoffMs(10);
        
        // Make a call
        SpatialReferenceFetcher.fetchProjJson("epsg", "1111");
        assertTrue(SpatialReferenceFetcher.getTotalAttemptCount() > 0);
        
        // Reset counter
        SpatialReferenceFetcher.resetAttemptCounter();
        assertEquals(0, SpatialReferenceFetcher.getTotalAttemptCount());
        
        // Full reset also resets counter
        SpatialReferenceFetcher.fetchProjJson("epsg", "2222");
        assertTrue(SpatialReferenceFetcher.getTotalAttemptCount() > 0);
        SpatialReferenceFetcher.reset();
        assertEquals(0, SpatialReferenceFetcher.getTotalAttemptCount());
    }

    // ==================== Defs Remote Lookup Tests ====================

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testDefsGet_RemoteFetch_EPSG2154() {
        // EPSG:2154 is not in the default globals, should be fetched remotely
        ProjectionDef def = Defs.get("EPSG:2154");
        
        assertNotNull(def, "Should fetch EPSG:2154 from spatialreference.org");
        assertEquals("EPSG:2154", def.getSrsCode());
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testDefsGet_CachesRemoteFetch() {
        // First fetch
        ProjectionDef def1 = Defs.get("EPSG:2154");
        assertNotNull(def1);
        
        // Second fetch should return cached value (same object)
        ProjectionDef def2 = Defs.get("EPSG:2154");
        assertSame(def1, def2, "Second call should return cached ProjectionDef");
    }

    @Test
    void testDefsGet_LocalRegistryFirst() {
        // Add a custom definition for an EPSG code
        Defs.set("EPSG:99999", "+proj=longlat +datum=WGS84");
        
        // Should return local definition, not try remote
        ProjectionDef def = Defs.get("EPSG:99999");
        assertNotNull(def);
        assertEquals("longlat", def.getProjName());
    }

    @Test
    void testDefsGet_DisableRemoteFetch_ReturnsNull() {
        // Disable remote fetching
        Defs.setRemoteFetchEnabled(false);
        assertFalse(Defs.isRemoteFetchEnabled());
        
        // Should return null for unknown code (no remote fetch, no exception)
        ProjectionDef def = Defs.get("EPSG:2154");
        assertNull(def, "Should return null when remote fetch is disabled");
        
        // Re-enable
        Defs.setRemoteFetchEnabled(true);
        assertTrue(Defs.isRemoteFetchEnabled());
    }

    @Test
    void testDefsGet_NonAuthorityPattern_ReturnsNull() {
        // Non-authority patterns should return null, not trigger remote fetch
        ProjectionDef def = Defs.get("CUSTOMNAME");
        assertNull(def, "Non-authority pattern should return null");
    }

    @Test
    void testDefsGet_ThrowsOnNotFound() {
        // Invalid EPSG code should throw CRSFetchException with NOT_FOUND reason
        CRSFetchException ex = assertThrows(CRSFetchException.class, () -> {
            Defs.get("EPSG:999999999");
        });
        
        assertEquals("EPSG:999999999", ex.getCrsCode());
        assertEquals(CRSFetchException.Reason.NOT_FOUND, ex.getReason());
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testDefsGet_ThrowsOnNetworkError() {
        // Configure for unreachable server
        SpatialReferenceFetcher.setBaseUrl("http://localhost:59995/");
        SpatialReferenceFetcher.setConnectTimeout(1);
        SpatialReferenceFetcher.setReadTimeout(1);
        SpatialReferenceFetcher.setMaxRetries(2);
        SpatialReferenceFetcher.setInitialBackoffMs(50);
        
        // Should throw CRSFetchException with NETWORK_ERROR reason
        CRSFetchException ex = assertThrows(CRSFetchException.class, () -> {
            Defs.get("ESRI:102001");  // Use ESRI to avoid hitting built-in cache
        });
        
        assertEquals("ESRI:102001", ex.getCrsCode());
        assertEquals(CRSFetchException.Reason.NETWORK_ERROR, ex.getReason());
        assertTrue(ex.getMessage().contains("Failed to fetch"));
        assertNotNull(ex.getCause(), "Should have underlying cause");
    }

    @Test
    void testCRSFetchException_ContainsAllDetails() {
        CRSFetchException ex = assertThrows(CRSFetchException.class, () -> {
            Defs.get("EPSG:999999999");
        });
        
        // Verify all details are accessible
        assertNotNull(ex.getCrsCode());
        assertNotNull(ex.getReason());
        assertNotNull(ex.getMessage());
        
        // toString should include all info
        String str = ex.toString();
        assertTrue(str.contains("EPSG:999999999"));
        assertTrue(str.contains("NOT_FOUND"));
    }

    // ==================== Integration Tests ====================

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testProj4WithRemoteEPSG() {
        // Use a remotely-fetched EPSG code in a transformation
        // EPSG:2154 (RGF93 / Lambert-93) is commonly used in France
        Converter conv = Proj4.proj4("EPSG:4326", "EPSG:2154");
        
        // Paris coordinates (lon=2.35, lat=48.85)
        Point paris = new Point(2.35, 48.85);
        Point projected = conv.forward(paris);
        
        assertTrue(Double.isFinite(projected.x), "Easting should be finite");
        assertTrue(Double.isFinite(projected.y), "Northing should be finite");
        
        // Paris should be roughly at (650000, 6860000) in Lambert-93
        assertTrue(projected.x > 600000 && projected.x < 700000, 
                "Easting should be around 650000");
        assertTrue(projected.y > 6800000 && projected.y < 6900000, 
                "Northing should be around 6860000");
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testRoundTripWithRemoteEPSG() {
        Converter conv = Proj4.proj4("EPSG:4326", "EPSG:2154");
        
        Point original = new Point(2.35, 48.85);  // Paris
        Point projected = conv.forward(original);
        Point restored = conv.inverse(projected);
        
        assertEquals(original.x, restored.x, 1e-6, "Longitude should round-trip");
        assertEquals(original.y, restored.y, 1e-6, "Latitude should round-trip");
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testMultipleRemoteEPSGCodes() {
        // Fetch multiple EPSG codes that are not in globals
        // EPSG:32188 - NAD83 / MTM zone 8 (Canada)
        ProjectionDef def1 = Defs.get("EPSG:32188");
        
        // EPSG:28992 - Amersfoort / RD New (Netherlands)
        ProjectionDef def2 = Defs.get("EPSG:28992");
        
        // Both should succeed
        assertNotNull(def1, "EPSG:32188 should be fetchable");
        assertNotNull(def2, "EPSG:28992 should be fetchable");
    }

    @Test
    void testTransformationThrowsWhenCRSNotFound() {
        // Verify that creating a transformation with a non-existent CRS throws
        CRSFetchException ex = assertThrows(CRSFetchException.class, () -> {
            Proj4.proj4("EPSG:4326", "ESRI:999999999");
        });
        
        assertEquals("ESRI:999999999", ex.getCrsCode());
        assertEquals(CRSFetchException.Reason.NOT_FOUND, ex.getReason());
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testTransformationThrowsOnNetworkError() {
        // Configure for unreachable server
        SpatialReferenceFetcher.setBaseUrl("http://localhost:59994/");
        SpatialReferenceFetcher.setConnectTimeout(1);
        SpatialReferenceFetcher.setReadTimeout(1);
        SpatialReferenceFetcher.setMaxRetries(2);
        SpatialReferenceFetcher.setInitialBackoffMs(50);
        
        // Verify that creating a transformation with network issues throws
        CRSFetchException ex = assertThrows(CRSFetchException.class, () -> {
            Proj4.proj4("EPSG:4326", "ESRI:102001");
        });
        
        assertEquals(CRSFetchException.Reason.NETWORK_ERROR, ex.getReason());
    }

    // ==================== Case Sensitivity Tests ====================

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testEpsgPatternCaseInsensitive() {
        // Test lowercase "epsg:"
        ProjectionDef def1 = Defs.get("epsg:2154");
        assertNotNull(def1, "Should handle lowercase 'epsg:'");
        
        // Second fetch with uppercase should return same cached object
        ProjectionDef def2 = Defs.get("EPSG:2154");
        assertSame(def1, def2, "Should return same cached definition regardless of case");
    }
}
