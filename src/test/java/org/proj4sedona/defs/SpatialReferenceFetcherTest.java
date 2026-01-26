package org.proj4sedona.defs;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.proj4sedona.Proj4;
import org.proj4sedona.core.Point;
import org.proj4sedona.core.ProjectionDef;
import org.proj4sedona.transform.Converter;

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
        String projJson = SpatialReferenceFetcher.fetchProjJson("epsg", "2154");
        
        assertNotNull(projJson, "Should fetch PROJJSON for EPSG:2154");
        assertTrue(projJson.contains("RGF93") || projJson.contains("Lambert"), 
                "PROJJSON should contain projection name");
        assertTrue(projJson.startsWith("{"), "Should be valid JSON");
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testFetchProjJson_NonExistent() {
        // EPSG:999999 should not exist
        String projJson = SpatialReferenceFetcher.fetchProjJson("epsg", "999999");
        
        assertNull(projJson, "Should return null for non-existent EPSG code");
    }

    @Test
    void testNegativeCache() {
        // First call should add to negative cache
        String result1 = SpatialReferenceFetcher.fetchProjJson("epsg", "999999");
        assertNull(result1);
        
        // Verify it's in the negative cache
        assertTrue(SpatialReferenceFetcher.isInNotFoundCache("epsg", "999999"),
                "Non-existent code should be in negative cache");
        
        // Second call should return null immediately from cache
        String result2 = SpatialReferenceFetcher.fetchProjJson("epsg", "999999");
        assertNull(result2);
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
    void testDefsGet_DisableRemoteFetch() {
        // Disable remote fetching
        Defs.setRemoteFetchEnabled(false);
        assertFalse(Defs.isRemoteFetchEnabled());
        
        // Should return null for unknown code (no remote fetch)
        ProjectionDef def = Defs.get("EPSG:2154");
        assertNull(def, "Should not fetch remotely when disabled");
        
        // Re-enable
        Defs.setRemoteFetchEnabled(true);
        assertTrue(Defs.isRemoteFetchEnabled());
    }

    @Test
    void testDefsGet_NonEpsgPattern() {
        // Non-EPSG patterns should not trigger remote fetch
        ProjectionDef def = Defs.get("CUSTOM:12345");
        assertNull(def, "Non-EPSG codes should not trigger remote fetch");
    }

    @Test
    void testDefsGet_InvalidEpsgCode() {
        // Invalid EPSG code (non-numeric)
        ProjectionDef def = Defs.get("EPSG:abc");
        assertNull(def, "Invalid EPSG code should return null");
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
        
        // At least one should succeed (depends on what spatialreference.org has)
        // We're mainly testing that multiple fetches work
        assertTrue(def1 != null || def2 != null, 
                "At least one remote EPSG code should be fetchable");
    }

    // ==================== Case Sensitivity Tests ====================

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testEpsgPatternCaseInsensitive() {
        // Test lowercase "epsg:"
        ProjectionDef def1 = Defs.get("epsg:2154");
        assertNotNull(def1, "Should handle lowercase 'epsg:'");
        
        // Clear and test mixed case
        Defs.reset();
        ProjectionDef def2 = Defs.get("Epsg:2154");
        assertNotNull(def2, "Should handle mixed case 'Epsg:'");
    }
}
