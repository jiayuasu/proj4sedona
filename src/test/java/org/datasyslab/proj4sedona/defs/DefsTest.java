package org.datasyslab.proj4sedona.defs;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.datasyslab.proj4sedona.Proj4;
import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.core.ProjectionDef;
import org.datasyslab.proj4sedona.transform.Converter;
import org.datasyslab.proj4sedona.defs.SpatialReferenceFetcher;
import org.datasyslab.proj4sedona.defs.CRSFetchException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Defs registry (Phase 12).
 */
class DefsTest {

    @BeforeEach
    void setUp() {
        Defs.reset();
    }

    @AfterEach
    void tearDown() {
        Defs.reset();
    }

    // ==================== Basic Registry Operations ====================

    @Test
    void testSetAndGetProjString() {
        Defs.set("TEST:1", "+proj=longlat +datum=WGS84");
        
        ProjectionDef def = Defs.get("TEST:1");
        assertNotNull(def);
        assertEquals("longlat", def.getProjName());
    }

    @Test
    void testSetAndGetProjectionDef() {
        ProjectionDef def = new ProjectionDef();
        def.setProjName("merc");
        
        Defs.set("TEST:2", def);
        
        ProjectionDef retrieved = Defs.get("TEST:2");
        assertNotNull(retrieved);
        assertEquals("merc", retrieved.getProjName());
    }

    @Test
    void testHas() {
        assertFalse(Defs.has("DOES_NOT_EXIST"));
        
        Defs.set("EXISTS", "+proj=longlat");
        assertTrue(Defs.has("EXISTS"));
    }

    @Test
    void testRemove() {
        Defs.set("TO_REMOVE", "+proj=longlat");
        assertTrue(Defs.has("TO_REMOVE"));
        
        ProjectionDef removed = Defs.remove("TO_REMOVE");
        assertNotNull(removed);
        assertFalse(Defs.has("TO_REMOVE"));
    }

    @Test
    void testSetNullRemoves() {
        Defs.set("TO_REMOVE", "+proj=longlat");
        assertTrue(Defs.has("TO_REMOVE"));
        
        Defs.set("TO_REMOVE", (ProjectionDef) null);
        assertFalse(Defs.has("TO_REMOVE"));
    }

    @Test
    void testAlias() {
        Defs.set("ORIGINAL", "+proj=merc +datum=WGS84");
        Defs.alias("ALIAS", "ORIGINAL");
        
        ProjectionDef original = Defs.get("ORIGINAL");
        ProjectionDef aliased = Defs.get("ALIAS");
        
        assertSame(original, aliased);
    }

    @Test
    void testAliasNonExistentThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            Defs.alias("ALIAS", "DOES_NOT_EXIST");
        });
    }

    @Test
    void testSizeAndReset() {
        assertEquals(0, Defs.size());
        
        Defs.set("A", "+proj=longlat");
        Defs.set("B", "+proj=merc");
        assertEquals(2, Defs.size());
        
        Defs.reset();
        assertEquals(0, Defs.size());
    }

    // ==================== Global Definitions ====================

    @Test
    void testGlobalsInitialization() {
        assertFalse(Defs.isGlobalsInitialized());
        
        Defs.globals();
        
        assertTrue(Defs.isGlobalsInitialized());
    }

    @Test
    void testGlobalsIdempotent() {
        Defs.globals();
        int sizeAfterFirst = Defs.size();
        
        Defs.globals();  // Second call
        
        assertEquals(sizeAfterFirst, Defs.size());
    }

    @Test
    void testWGS84Defined() {
        Defs.globals();
        
        ProjectionDef wgs84 = Defs.get("EPSG:4326");
        assertNotNull(wgs84);
        assertEquals("longlat", wgs84.getProjName());
    }

    @Test
    void testWGS84Alias() {
        Defs.globals();
        
        ProjectionDef byCode = Defs.get("EPSG:4326");
        ProjectionDef byAlias = Defs.get("WGS84");
        
        assertSame(byCode, byAlias);
    }

    @Test
    void testWebMercatorDefined() {
        Defs.globals();
        
        ProjectionDef webMerc = Defs.get("EPSG:3857");
        assertNotNull(webMerc);
        assertEquals("merc", webMerc.getProjName());
    }

    @Test
    void testWebMercatorAliases() {
        Defs.globals();
        
        ProjectionDef epsg3857 = Defs.get("EPSG:3857");
        
        assertSame(epsg3857, Defs.get("EPSG:3785"));
        assertSame(epsg3857, Defs.get("GOOGLE"));
        assertSame(epsg3857, Defs.get("EPSG:900913"));
        assertSame(epsg3857, Defs.get("EPSG:102113"));
    }

    @Test
    void testNAD83Defined() {
        Defs.globals();
        
        ProjectionDef nad83 = Defs.get("EPSG:4269");
        assertNotNull(nad83);
        assertEquals("longlat", nad83.getProjName());
    }

    @Test
    void testUTMZonesDefined() {
        Defs.globals();
        
        // Test a few UTM zones
        ProjectionDef utmZ32N = Defs.get("EPSG:32632");
        assertNotNull(utmZ32N);
        assertEquals("utm", utmZ32N.getProjName());
        assertEquals(Integer.valueOf(32), utmZ32N.getZone());
        
        ProjectionDef utmZ1S = Defs.get("EPSG:32701");
        assertNotNull(utmZ1S);
        assertEquals("utm", utmZ1S.getProjName());
        assertEquals(Integer.valueOf(1), utmZ1S.getZone());
        assertTrue(utmZ1S.getUtmSouth());
    }

    @Test
    void testUPSDefined() {
        Defs.globals();
        
        ProjectionDef upsNorth = Defs.get("EPSG:5041");
        assertNotNull(upsNorth);
        assertEquals("stere", upsNorth.getProjName());
        
        ProjectionDef upsSouth = Defs.get("EPSG:5042");
        assertNotNull(upsSouth);
        assertEquals("stere", upsSouth.getProjName());
    }

    // ==================== Integration with Proj4 ====================

    @Test
    void testProj4WithEPSGCodes() {
        Defs.globals();
        
        Converter conv = Proj4.proj4("EPSG:4326", "EPSG:3857");
        
        Point wgs84 = new Point(0, 0);
        Point webMerc = conv.forward(wgs84);
        
        assertEquals(0, webMerc.x, 1e-6);
        assertEquals(0, webMerc.y, 1e-6);
    }

    @Test
    void testProj4WithAlias() {
        // Globals should auto-initialize
        Converter conv = Proj4.proj4("WGS84", "GOOGLE");
        
        Point result = conv.forward(new Point(10, 45));
        assertTrue(Double.isFinite(result.x));
        assertTrue(Double.isFinite(result.y));
    }

    @Test
    void testProj4UTMZone() {
        Converter conv = Proj4.proj4("EPSG:4326", "EPSG:32632");
        
        // Berlin, Germany (lon=13.4, lat=52.5) should be in UTM zone 32
        Point wgs84 = new Point(13.4, 52.5);
        Point utm = conv.forward(wgs84);
        
        // Expected ~395000 E, ~5818000 N
        assertTrue(Double.isFinite(utm.x), "Easting is finite");
        assertTrue(Double.isFinite(utm.y), "Northing is finite");
    }

    @Test
    void testRoundTripEPSGCodes() {
        Converter conv = Proj4.proj4("EPSG:4326", "EPSG:3857");
        
        Point original = new Point(-122.4194, 37.7749);  // San Francisco
        Point projected = conv.forward(original);
        Point restored = conv.inverse(projected);
        
        assertEquals(original.x, restored.x, 1e-6);
        assertEquals(original.y, restored.y, 1e-6);
    }

    @Test
    void testCustomDefinition() {
        // Register a custom definition
        Defs.set("CUSTOM:1", "+proj=lcc +lat_1=33 +lat_2=45 +lat_0=39 +lon_0=-96 +x_0=0 +y_0=0 +datum=WGS84 +units=m");
        
        Converter conv = Proj4.proj4("EPSG:4326", "CUSTOM:1");
        
        Point original = new Point(-100, 40);
        Point projected = conv.forward(original);
        Point restored = conv.inverse(projected);
        
        assertEquals(original.x, restored.x, 1e-6);
        assertEquals(original.y, restored.y, 1e-6);
    }

    // ==================== Case Sensitivity Tests ====================

    @Test
    void testGetCaseInsensitive_BuiltIn() {
        Defs.globals();
        
        // Disable remote fetch to ensure we're testing built-in definitions
        Defs.setRemoteFetchEnabled(false);
        
        try {
            // These should all return the same built-in definition
            ProjectionDef upper = Defs.get("EPSG:4326");
            ProjectionDef lower = Defs.get("epsg:4326");
            ProjectionDef mixed = Defs.get("Epsg:4326");
            
            assertNotNull(upper, "EPSG:4326 should be found");
            assertNotNull(lower, "epsg:4326 should be found (case-insensitive)");
            assertNotNull(mixed, "Epsg:4326 should be found (case-insensitive)");
            
            // They should be the exact same object (from built-in registry)
            assertSame(upper, lower, "Should return same definition regardless of case");
            assertSame(upper, mixed, "Should return same definition regardless of case");
        } finally {
            Defs.setRemoteFetchEnabled(true);
        }
    }

    @Test
    void testHasCaseInsensitive() {
        Defs.globals();
        
        assertTrue(Defs.has("EPSG:4326"), "Should find EPSG:4326");
        assertTrue(Defs.has("epsg:4326"), "Should find epsg:4326 (case-insensitive)");
        assertTrue(Defs.has("Epsg:4326"), "Should find Epsg:4326 (case-insensitive)");
        assertTrue(Defs.has("EPSG:3857"), "Should find EPSG:3857");
        assertTrue(Defs.has("epsg:3857"), "Should find epsg:3857 (case-insensitive)");
    }

    @Test
    void testAliasCaseInsensitive() {
        Defs.globals();
        
        // Create alias using lowercase source
        Defs.alias("MY_WGS84", "epsg:4326");
        
        ProjectionDef original = Defs.get("EPSG:4326");
        ProjectionDef aliased = Defs.get("MY_WGS84");
        
        assertSame(original, aliased, "Alias should point to same definition");
    }

    @Test
    void testRemoveCaseInsensitive() {
        Defs.set("TEST:123", "+proj=longlat +datum=WGS84");
        
        assertTrue(Defs.has("TEST:123"));
        assertTrue(Defs.has("test:123"));
        
        // Remove using lowercase
        ProjectionDef removed = Defs.remove("test:123");
        assertNotNull(removed);
        
        assertFalse(Defs.has("TEST:123"));
        assertFalse(Defs.has("test:123"));
    }

    @Test
    void testNormalizationDoesNotAffectProjStrings() {
        // PROJ strings should not be normalized (they don't match authority:code pattern)
        Defs.set("PROJ_TEST", "+proj=longlat +datum=WGS84");
        
        // Looking up by name should work
        ProjectionDef def = Defs.get("PROJ_TEST");
        assertNotNull(def);
    }

    @Test
    void testNormalizationPreservesCode() {
        // Register with uppercase authority
        Defs.set("TEST:AbC123", "+proj=longlat +datum=WGS84");
        
        // Lookup with lowercase authority should work
        ProjectionDef def = Defs.get("test:AbC123");
        assertNotNull(def, "Should find with lowercase authority");
        
        // The code part should be preserved as-is
        assertEquals("TEST:AbC123", def.getSrsCode());
    }

    @Test
    void testProj4WithLowercaseEpsgCodes() {
        // This tests the main use case: Flink tests using lowercase "epsg:4326"
        Converter conv = Proj4.proj4("epsg:4326", "epsg:3857");
        
        Point wgs84 = new Point(0, 0);
        Point webMerc = conv.forward(wgs84);
        
        assertEquals(0, webMerc.x, 1e-6);
        assertEquals(0, webMerc.y, 1e-6);
    }

    // ==================== Remote Fetch Tests (Non-EPSG Authorities) ====================
    // These tests require network access to spatialreference.org

    @Test
    void testRemoteFetch_ESRI_102001_CanadaAlbers() {
        // ESRI:102001 = Canada Albers Equal Area Conic
        // Should be fetched from spatialreference.org
        Defs.globals();
        SpatialReferenceFetcher.clearNotFoundCache();
        
        ProjectionDef def = Defs.get("ESRI:102001");
        
        assertNotNull(def, "ESRI:102001 should be fetched from spatialreference.org");
        // PROJJSON returns full name "Albers Equal Area", check it contains expected keywords
        String projName = def.getProjName().toLowerCase();
        assertTrue(projName.contains("aea") || projName.contains("albers"), 
            "Should be Albers Equal Area projection, got: " + def.getProjName());
        assertEquals("ESRI:102001", def.getSrsCode());
    }

    @Test
    void testRemoteFetch_ESRI_CaseInsensitive() {
        // Test case-insensitivity for ESRI authority codes
        Defs.globals();
        SpatialReferenceFetcher.clearNotFoundCache();
        
        // Fetch with uppercase
        ProjectionDef upper = Defs.get("ESRI:102001");
        assertNotNull(upper, "ESRI:102001 should be found");
        
        // Fetch with lowercase - should use cached definition
        ProjectionDef lower = Defs.get("esri:102001");
        assertNotNull(lower, "esri:102001 should be found (case-insensitive)");
        
        // Fetch with mixed case
        ProjectionDef mixed = Defs.get("Esri:102001");
        assertNotNull(mixed, "Esri:102001 should be found (case-insensitive)");
        
        // All should be the same cached object
        assertSame(upper, lower, "Should return same cached definition regardless of case");
        assertSame(upper, mixed, "Should return same cached definition regardless of case");
    }

    @Test
    void testRemoteFetch_ESRI_102008_NorthAmericaAlbers() {
        // ESRI:102008 = North America Albers Equal Area Conic
        Defs.globals();
        SpatialReferenceFetcher.clearNotFoundCache();
        
        ProjectionDef def = Defs.get("ESRI:102008");
        
        assertNotNull(def, "ESRI:102008 should be fetched from spatialreference.org");
        // PROJJSON returns full name "Albers Equal Area", check it contains expected keywords
        String projName = def.getProjName().toLowerCase();
        assertTrue(projName.contains("aea") || projName.contains("albers"), 
            "Should be Albers Equal Area projection, got: " + def.getProjName());
    }

    @Test
    void testRemoteFetch_IAU2015_49900_Mars() {
        // IAU_2015:49900 = Mars (2015) - Sphere / Ocentric
        Defs.globals();
        SpatialReferenceFetcher.clearNotFoundCache();
        
        ProjectionDef def = Defs.get("IAU_2015:49900");
        
        assertNotNull(def, "IAU_2015:49900 should be fetched from spatialreference.org");
        assertEquals("longlat", def.getProjName(), "Mars geographic CRS should be longlat");
    }

    @Test
    void testRemoteFetch_IAU2015_CaseInsensitive() {
        // Test case-insensitivity for IAU_2015 authority codes
        Defs.globals();
        SpatialReferenceFetcher.clearNotFoundCache();
        
        // Fetch with uppercase
        ProjectionDef upper = Defs.get("IAU_2015:49900");
        assertNotNull(upper, "IAU_2015:49900 should be found");
        
        // Fetch with lowercase
        ProjectionDef lower = Defs.get("iau_2015:49900");
        assertNotNull(lower, "iau_2015:49900 should be found (case-insensitive)");
        
        // All should be the same cached object
        assertSame(upper, lower, "Should return same cached definition regardless of case");
    }

    @Test
    void testRemoteFetch_Transform_ESRI_ToWGS84() {
        // Test transformation using remotely fetched ESRI definition
        Defs.globals();
        SpatialReferenceFetcher.clearNotFoundCache();
        
        // Transform from WGS84 to ESRI:102001 (Canada Albers) and back
        Converter conv = Proj4.proj4("EPSG:4326", "ESRI:102001");
        
        // Ottawa, Canada (approximately in the center of the projection)
        Point wgs84 = new Point(-75.7, 45.4);
        Point albers = conv.forward(wgs84);
        
        // Verify we got valid projected coordinates
        assertTrue(Double.isFinite(albers.x), "Easting should be finite");
        assertTrue(Double.isFinite(albers.y), "Northing should be finite");
        
        // Round-trip test
        Point restored = conv.inverse(albers);
        assertEquals(wgs84.x, restored.x, 1e-6, "Longitude should round-trip");
        assertEquals(wgs84.y, restored.y, 1e-6, "Latitude should round-trip");
    }

    @Test
    void testRemoteFetch_Transform_LowercaseEsri() {
        // Test transformation using lowercase "esri:102001"
        Defs.globals();
        SpatialReferenceFetcher.clearNotFoundCache();
        
        // This is the key use case: user specifies lowercase authority
        Converter conv = Proj4.proj4("epsg:4326", "esri:102001");
        
        Point wgs84 = new Point(-100, 50);
        Point albers = conv.forward(wgs84);
        
        assertTrue(Double.isFinite(albers.x), "Should transform successfully with lowercase authority");
        assertTrue(Double.isFinite(albers.y), "Should transform successfully with lowercase authority");
    }

    @Test
    void testRemoteFetch_NonExistentCode_ThrowsException() {
        // Test that non-existent codes throw CRSFetchException
        Defs.globals();
        SpatialReferenceFetcher.clearNotFoundCache();
        
        CRSFetchException ex = assertThrows(CRSFetchException.class, () -> {
            Defs.get("ESRI:999999999");
        });
        
        assertEquals("ESRI:999999999", ex.getCrsCode());
        assertEquals(CRSFetchException.Reason.NOT_FOUND, ex.getReason());
        assertTrue(ex.getMessage().contains("not found"));
        
        // Verify it's in the negative cache
        assertTrue(SpatialReferenceFetcher.isInNotFoundCache("esri", "999999999"),
            "Non-existent code should be in negative cache");
    }

    @Test
    void testRemoteFetch_NonExistentAuthority_ThrowsException() {
        // Test that non-existent authorities throw CRSFetchException
        Defs.globals();
        SpatialReferenceFetcher.clearNotFoundCache();
        
        CRSFetchException ex = assertThrows(CRSFetchException.class, () -> {
            Defs.get("BOGUS:12345");
        });
        
        assertEquals("BOGUS:12345", ex.getCrsCode());
        assertEquals(CRSFetchException.Reason.NOT_FOUND, ex.getReason());
    }

    @Test
    void testRemoteFetchDisabled_NonBuiltIn_ReturnsNull() {
        // When remote fetch is disabled, non-built-in codes should return null (no exception)
        Defs.globals();
        Defs.setRemoteFetchEnabled(false);
        
        try {
            // ESRI:102001 is not a built-in, so should return null
            ProjectionDef def = Defs.get("ESRI:102001");
            assertNull(def, "Should return null when remote fetch is disabled");
        } finally {
            Defs.setRemoteFetchEnabled(true);
        }
    }
}
