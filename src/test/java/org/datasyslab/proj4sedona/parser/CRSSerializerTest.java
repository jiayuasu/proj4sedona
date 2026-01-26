package org.datasyslab.proj4sedona.parser;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.datasyslab.proj4sedona.core.Proj;
import org.datasyslab.proj4sedona.projection.ProjectionRegistry;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CRSSerializer - exporting CRS definitions to various formats.
 */
class CRSSerializerTest {

    @BeforeAll
    static void setup() {
        ProjectionRegistry.start();
    }

    // ==================== PROJ String Export Tests ====================

    @Test
    @DisplayName("toProjString: WGS84 Geographic")
    void testToProjStringWgs84() {
        Proj proj = new Proj("EPSG:4326");
        String result = CRSSerializer.toProjString(proj);

        assertNotNull(result);
        assertTrue(result.contains("+proj=longlat"));
        assertTrue(result.contains("+datum=WGS84") || result.contains("+ellps=WGS84"));
        assertTrue(result.contains("+no_defs"));
    }

    @Test
    @DisplayName("toProjString: UTM Zone 10N")
    void testToProjStringUtm() {
        Proj proj = new Proj("EPSG:32610");
        String result = CRSSerializer.toProjString(proj);

        assertNotNull(result);
        assertTrue(result.contains("+proj=utm") || result.contains("+proj=tmerc"));
        assertTrue(result.contains("+zone=10") || result.contains("+lon_0=-123"));
    }

    @Test
    @DisplayName("toProjString: Web Mercator")
    void testToProjStringWebMercator() {
        Proj proj = new Proj("EPSG:3857");
        String result = CRSSerializer.toProjString(proj);

        assertNotNull(result);
        assertTrue(result.contains("+proj=merc"));
        assertTrue(result.contains("+a=6378137"));
    }

    @Test
    @DisplayName("toProjString: Custom LCC projection")
    void testToProjStringLcc() {
        String lcc = "+proj=lcc +lat_1=33 +lat_2=45 +lat_0=39 +lon_0=-96 " +
                "+x_0=0 +y_0=0 +datum=NAD83 +units=m +no_defs";
        Proj proj = new Proj(lcc);
        String result = CRSSerializer.toProjString(proj);

        assertNotNull(result);
        assertTrue(result.contains("+proj=lcc"));
        assertTrue(result.contains("+lat_1="));
        assertTrue(result.contains("+lat_2="));
        assertTrue(result.contains("+lon_0="));
    }

    @Test
    @DisplayName("toProjString: Projection with scale factor")
    void testToProjStringWithScaleFactor() {
        String tmerc = "+proj=tmerc +lat_0=0 +lon_0=9 +k=0.9996 +x_0=500000 +y_0=0 +datum=WGS84";
        Proj proj = new Proj(tmerc);
        String result = CRSSerializer.toProjString(proj);

        assertNotNull(result);
        assertTrue(result.contains("+k_0=0.9996") || result.contains("+k=0.9996"));
        assertTrue(result.contains("+x_0=500000"));
    }

    @Test
    @DisplayName("toProjString: null input returns null")
    void testToProjStringNull() {
        assertNull(CRSSerializer.toProjString((Proj) null));
    }

    // ==================== WKT1 Export Tests ====================

    @Test
    @DisplayName("toWkt1: Geographic CRS")
    void testToWkt1Geographic() {
        Proj proj = new Proj("EPSG:4326");
        String result = CRSSerializer.toWkt1(proj);

        assertNotNull(result);
        assertTrue(result.startsWith("GEOGCS["));
        assertTrue(result.contains("DATUM["));
        assertTrue(result.contains("SPHEROID["));
        assertTrue(result.contains("PRIMEM["));
        assertTrue(result.contains("UNIT[\"degree\""));
    }

    @Test
    @DisplayName("toWkt1: Projected CRS (UTM)")
    void testToWkt1Projected() {
        Proj proj = new Proj("EPSG:32610");
        String result = CRSSerializer.toWkt1(proj);

        assertNotNull(result);
        assertTrue(result.startsWith("PROJCS["));
        assertTrue(result.contains("GEOGCS["));
        assertTrue(result.contains("PROJECTION["));
        assertTrue(result.contains("PARAMETER["));
        assertTrue(result.contains("UNIT["));
    }

    @Test
    @DisplayName("toWkt1: Contains proper structure")
    void testToWkt1Structure() {
        Proj proj = new Proj("+proj=tmerc +lat_0=0 +lon_0=9 +k=0.9996 +x_0=500000 +y_0=0 +datum=WGS84");
        String result = CRSSerializer.toWkt1(proj);

        // Check for proper nesting
        assertTrue(result.contains("PARAMETER[\"latitude_of_origin\""));
        assertTrue(result.contains("PARAMETER[\"central_meridian\""));
        assertTrue(result.contains("PARAMETER[\"scale_factor\""));
        assertTrue(result.contains("PARAMETER[\"false_easting\""));
    }

    @Test
    @DisplayName("toWkt1: LCC with standard parallels")
    void testToWkt1Lcc() {
        String lcc = "+proj=lcc +lat_1=33 +lat_2=45 +lat_0=39 +lon_0=-96 +datum=NAD83";
        Proj proj = new Proj(lcc);
        String result = CRSSerializer.toWkt1(proj);

        assertTrue(result.contains("PARAMETER[\"standard_parallel_1\""));
        assertTrue(result.contains("PARAMETER[\"standard_parallel_2\""));
    }

    @Test
    @DisplayName("toWkt1: null input returns null")
    void testToWkt1Null() {
        assertNull(CRSSerializer.toWkt1((Proj) null));
    }

    // ==================== WKT2 Export Tests ====================

    @Test
    @DisplayName("toWkt2: Geographic CRS")
    void testToWkt2Geographic() {
        Proj proj = new Proj("EPSG:4326");
        String result = CRSSerializer.toWkt2(proj);

        assertNotNull(result);
        assertTrue(result.startsWith("GEOGCRS["));
        assertTrue(result.contains("DATUM["));
        assertTrue(result.contains("ELLIPSOID["));
        assertTrue(result.contains("PRIMEM["));
        assertTrue(result.contains("CS[ellipsoidal"));
        assertTrue(result.contains("AXIS["));
        assertTrue(result.contains("ANGLEUNIT["));
    }

    @Test
    @DisplayName("toWkt2: Projected CRS")
    void testToWkt2Projected() {
        Proj proj = new Proj("EPSG:32610");
        String result = CRSSerializer.toWkt2(proj);

        assertNotNull(result);
        assertTrue(result.startsWith("PROJCRS["));
        assertTrue(result.contains("BASEGEOGCRS["));
        assertTrue(result.contains("CONVERSION["));
        assertTrue(result.contains("METHOD["));
        assertTrue(result.contains("PARAMETER["));
        assertTrue(result.contains("CS[Cartesian"));
        assertTrue(result.contains("LENGTHUNIT["));
    }

    @Test
    @DisplayName("toWkt2: Contains unit specifications")
    void testToWkt2Units() {
        Proj proj = new Proj("+proj=tmerc +lat_0=0 +lon_0=9 +k=0.9996 +x_0=500000 +y_0=0 +datum=WGS84");
        String result = CRSSerializer.toWkt2(proj);

        assertTrue(result.contains("ANGLEUNIT[\"degree\""));
        assertTrue(result.contains("LENGTHUNIT[\"metre\""));
    }

    @Test
    @DisplayName("toWkt2: null input returns null")
    void testToWkt2Null() {
        assertNull(CRSSerializer.toWkt2((Proj) null));
    }

    // ==================== PROJJSON Export Tests ====================

    @Test
    @DisplayName("toProjJson: Geographic CRS")
    void testToProjJsonGeographic() {
        Proj proj = new Proj("EPSG:4326");
        String result = CRSSerializer.toProjJson(proj);

        assertNotNull(result);
        assertTrue(result.contains("\"type\": \"GeographicCRS\""));
        assertTrue(result.contains("\"datum\""));
        assertTrue(result.contains("\"ellipsoid\""));
        assertTrue(result.contains("\"semi_major_axis\""));
        assertTrue(result.contains("\"coordinate_system\""));
    }

    @Test
    @DisplayName("toProjJson: Projected CRS")
    void testToProjJsonProjected() {
        Proj proj = new Proj("EPSG:32610");
        String result = CRSSerializer.toProjJson(proj);

        assertNotNull(result);
        assertTrue(result.contains("\"type\": \"ProjectedCRS\""));
        assertTrue(result.contains("\"base_crs\""));
        assertTrue(result.contains("\"conversion\""));
        assertTrue(result.contains("\"method\""));
        assertTrue(result.contains("\"parameters\""));
    }

    @Test
    @DisplayName("toProjJson: Contains proper ellipsoid")
    void testToProjJsonEllipsoid() {
        Proj proj = new Proj("EPSG:4326");
        String result = CRSSerializer.toProjJson(proj);

        assertTrue(result.contains("\"semi_major_axis\": 6378137"));
        assertTrue(result.contains("\"inverse_flattening\""));
    }

    @Test
    @DisplayName("toProjJson: Compact format")
    void testToProjJsonCompact() {
        Proj proj = new Proj("EPSG:4326");
        String result = CRSSerializer.toProjJson(proj, false);

        assertNotNull(result);
        assertFalse(result.contains("\n")); // No newlines in compact format
        assertTrue(result.contains("\"type\":\"GeographicCRS\""));
    }

    @Test
    @DisplayName("toProjJson: Can be parsed back")
    void testToProjJsonRoundTrip() {
        Proj original = new Proj("EPSG:4326");
        String json = CRSSerializer.toProjJson(original);

        // Parse the JSON back
        Proj parsed = new Proj(json);

        assertNotNull(parsed);
        assertEquals("longlat", parsed.getParams().projName);
        assertEquals(original.getA(), parsed.getA(), 0.1);
    }

    @Test
    @DisplayName("toProjJson: null input returns null")
    void testToProjJsonNull() {
        assertNull(CRSSerializer.toProjJson((Proj) null));
    }

    // ==================== EPSG Code Lookup Tests ====================

    @Test
    @DisplayName("toEpsgCode: WGS84")
    void testToEpsgCodeWgs84() {
        Proj proj = new Proj("+proj=longlat +datum=WGS84 +no_defs");
        String result = CRSSerializer.toEpsgCode(proj);

        assertEquals("EPSG:4326", result);
    }

    @Test
    @DisplayName("toEpsgCode: Web Mercator")
    void testToEpsgCodeWebMercator() {
        Proj proj = new Proj("+proj=merc +a=6378137 +b=6378137 +lat_ts=0 +lon_0=0 +x_0=0 +y_0=0 +k=1 +units=m");
        String result = CRSSerializer.toEpsgCode(proj);

        assertEquals("EPSG:3857", result);
    }

    @Test
    @DisplayName("toEpsgCode: UTM Zone 10N")
    void testToEpsgCodeUtm10N() {
        Proj proj = new Proj("+proj=utm +zone=10 +datum=WGS84 +units=m");
        String result = CRSSerializer.toEpsgCode(proj);

        assertEquals("EPSG:32610", result);
    }

    @Test
    @DisplayName("toEpsgCode: UTM Zone 33S")
    void testToEpsgCodeUtm33S() {
        Proj proj = new Proj("+proj=utm +zone=33 +south +datum=WGS84 +units=m");
        String result = CRSSerializer.toEpsgCode(proj);

        assertEquals("EPSG:32733", result);
    }

    @Test
    @DisplayName("toEpsgCode: Already has EPSG code")
    void testToEpsgCodeAlreadyHasCode() {
        Proj proj = new Proj("EPSG:4326");
        String result = CRSSerializer.toEpsgCode(proj);

        assertEquals("EPSG:4326", result);
    }

    @Test
    @DisplayName("toEpsgCode: Custom projection returns null")
    void testToEpsgCodeCustomProjection() {
        // Custom projection that doesn't match any EPSG
        Proj proj = new Proj("+proj=merc +lon_0=100 +k=0.5 +x_0=12345 +y_0=67890 +datum=WGS84");
        String result = CRSSerializer.toEpsgCode(proj);

        assertNull(result);
    }

    @Test
    @DisplayName("toEpsgCode: null input returns null")
    void testToEpsgCodeNull() {
        assertNull(CRSSerializer.toEpsgCode((Proj) null));
    }

    // ==================== Round-trip Tests ====================

    @Test
    @DisplayName("Round-trip: PROJ -> WKT1 -> PROJ")
    void testRoundTripProjWkt1() {
        String original = "+proj=tmerc +lat_0=0 +lon_0=9 +k=0.9996 +x_0=500000 +y_0=0 +datum=WGS84";
        Proj proj1 = new Proj(original);
        
        String wkt1 = CRSSerializer.toWkt1(proj1);
        Proj proj2 = new Proj(wkt1);
        
        // WKT uses full method names, so projName will be different
        // Compare ellipsoid and scale factor instead
        assertEquals(proj1.getA(), proj2.getA(), 0.1);
        assertEquals(proj1.getParams().k0, proj2.getParams().k0, 1e-6);
    }

    @Test
    @DisplayName("Round-trip: PROJ -> PROJJSON -> PROJ")
    void testRoundTripProjJson() {
        String original = "+proj=lcc +lat_1=33 +lat_2=45 +lat_0=39 +lon_0=-96 +datum=WGS84";
        Proj proj1 = new Proj(original);
        
        String json = CRSSerializer.toProjJson(proj1);
        Proj proj2 = new Proj(json);
        
        // PROJJSON uses full method names, so compare ellipsoid instead
        assertEquals(proj1.getA(), proj2.getA(), 0.1);
    }

    @Test
    @DisplayName("Round-trip: EPSG -> all formats -> verify")
    void testRoundTripAllFormats() {
        Proj original = new Proj("EPSG:32610");
        
        // Export to all formats
        String projStr = CRSSerializer.toProjString(original);
        String wkt1 = CRSSerializer.toWkt1(original);
        String wkt2 = CRSSerializer.toWkt2(original);
        String json = CRSSerializer.toProjJson(original);
        String epsg = CRSSerializer.toEpsgCode(original);
        
        // All should be non-null
        assertNotNull(projStr);
        assertNotNull(wkt1);
        assertNotNull(wkt2);
        assertNotNull(json);
        assertNotNull(epsg);
        
        // Parse back from each format and verify ellipsoid
        Proj fromProj = new Proj(projStr);
        Proj fromWkt1 = new Proj(wkt1);
        Proj fromJson = new Proj(json);
        
        // All should have same ellipsoid
        assertEquals(original.getA(), fromProj.getA(), 0.1);
        assertEquals(original.getA(), fromWkt1.getA(), 0.1);
        assertEquals(original.getA(), fromJson.getA(), 0.1);
    }

    // ==================== Edge Cases ====================

    @Test
    @DisplayName("Export: Projection with all parameters")
    void testExportFullProjection() {
        // Use a projection that's supported (LCC instead of omerc)
        String full = "+proj=lcc +lat_0=4 +lon_0=115 +lat_1=33 +lat_2=45 " +
                "+k=0.99984 +x_0=590476.87 +y_0=442857.65 +ellps=GRS80 +units=m";
        Proj proj = new Proj(full);
        
        String projStr = CRSSerializer.toProjString(proj);
        String wkt1 = CRSSerializer.toWkt1(proj);
        String json = CRSSerializer.toProjJson(proj);
        
        assertNotNull(projStr);
        assertNotNull(wkt1);
        assertNotNull(json);
    }

    @Test
    @DisplayName("Export: Spherical projection")
    void testExportSpherical() {
        // Use the sphere ellipsoid which is registered
        String sphere = "+proj=merc +ellps=sphere +lat_ts=0 +lon_0=0 +x_0=0 +y_0=0 +k=1 +units=m";
        Proj proj = new Proj(sphere);
        
        String projStr = CRSSerializer.toProjString(proj);
        // Should contain ellps=sphere or the a value
        assertTrue(projStr.contains("+ellps=sphere") || projStr.contains("+a=6370997"));
        
        String json = CRSSerializer.toProjJson(proj);
        assertTrue(json.contains("6370997"));
    }

    @Test
    @DisplayName("Export: Non-meter units")
    void testExportNonMeterUnits() {
        String usFeet = "+proj=lcc +lat_1=33 +lat_2=45 +lat_0=39 +lon_0=-96 +datum=NAD83 +units=us-ft";
        Proj proj = new Proj(usFeet);
        
        String projStr = CRSSerializer.toProjString(proj);
        assertTrue(projStr.contains("+units=us-ft"));
        
        String wkt1 = CRSSerializer.toWkt1(proj);
        assertTrue(wkt1.contains("US survey foot") || wkt1.contains("us-ft"));
    }
}
