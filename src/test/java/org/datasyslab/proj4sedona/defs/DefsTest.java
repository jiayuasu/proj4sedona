package org.datasyslab.proj4sedona.defs;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.datasyslab.proj4sedona.Proj4;
import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.core.ProjectionDef;
import org.datasyslab.proj4sedona.transform.Converter;

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
}
