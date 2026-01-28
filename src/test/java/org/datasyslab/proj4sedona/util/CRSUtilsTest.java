package org.datasyslab.proj4sedona.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CRSUtils utility class.
 */
class CRSUtilsTest {

    @Test
    void testNormalizeEpsgCode() {
        assertEquals("EPSG:4326", CRSUtils.normalizeAuthorityCode("epsg:4326"));
        assertEquals("EPSG:4326", CRSUtils.normalizeAuthorityCode("EPSG:4326"));
        assertEquals("EPSG:4326", CRSUtils.normalizeAuthorityCode("Epsg:4326"));
        assertEquals("EPSG:3857", CRSUtils.normalizeAuthorityCode("ePsG:3857"));
    }

    @Test
    void testNormalizeOtherAuthorities() {
        assertEquals("ESRI:102001", CRSUtils.normalizeAuthorityCode("esri:102001"));
        assertEquals("IAU_2015:49900", CRSUtils.normalizeAuthorityCode("iau_2015:49900"));
        assertEquals("IGNF:LAMB93", CRSUtils.normalizeAuthorityCode("ignf:LAMB93"));
        assertEquals("OGC:CRS84", CRSUtils.normalizeAuthorityCode("ogc:CRS84"));
    }

    @Test
    void testPreservesCodeCase() {
        // Code part should be preserved as-is
        assertEquals("TEST:AbC123", CRSUtils.normalizeAuthorityCode("test:AbC123"));
        assertEquals("EPSG:4326", CRSUtils.normalizeAuthorityCode("epsg:4326"));
        assertEquals("IGNF:LAMB93", CRSUtils.normalizeAuthorityCode("ignf:LAMB93"));
    }

    @Test
    void testRejectsProjStrings() {
        String proj = "+proj=longlat +datum=WGS84";
        assertEquals(proj, CRSUtils.normalizeAuthorityCode(proj));
        
        String projLong = "+proj=merc +lon_0=0 +k=1 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
        assertEquals(projLong, CRSUtils.normalizeAuthorityCode(projLong));
    }

    @Test
    void testRejectsProjJson() {
        String json = "{\"type\":\"GeographicCRS\"}";
        assertEquals(json, CRSUtils.normalizeAuthorityCode(json));
        
        String jsonLong = "{\"type\":\"GeographicCRS\",\"name\":\"WGS 84\"}";
        assertEquals(jsonLong, CRSUtils.normalizeAuthorityCode(jsonLong));
    }

    @Test
    void testRejectsLongStrings() {
        String wkt = "GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\"]]";
        assertEquals(wkt, CRSUtils.normalizeAuthorityCode(wkt));
        
        // Exactly at the limit (25 chars)
        String atLimit = "AUTHORITY:1234567890123456";  // 26 chars - should be rejected
        assertEquals(atLimit, CRSUtils.normalizeAuthorityCode(atLimit));
    }

    @Test
    void testRejectsNoColon() {
        assertEquals("WGS84", CRSUtils.normalizeAuthorityCode("WGS84"));
        assertEquals("GOOGLE", CRSUtils.normalizeAuthorityCode("GOOGLE"));
        assertEquals("NAD83", CRSUtils.normalizeAuthorityCode("NAD83"));
    }

    @Test
    void testRejectsMultipleColons() {
        String urn = "urn:ogc:def:crs:EPSG::4326";
        assertEquals(urn, CRSUtils.normalizeAuthorityCode(urn));
        
        String custom = "A:B:C";
        assertEquals(custom, CRSUtils.normalizeAuthorityCode(custom));
    }

    @Test
    void testRejectsColonAtEdges() {
        // Colon at start
        assertEquals(":4326", CRSUtils.normalizeAuthorityCode(":4326"));
        
        // Colon at end
        assertEquals("EPSG:", CRSUtils.normalizeAuthorityCode("EPSG:"));
    }

    @Test
    void testHandlesNullAndEmpty() {
        assertNull(CRSUtils.normalizeAuthorityCode(null));
        assertEquals("", CRSUtils.normalizeAuthorityCode(""));
    }

    @Test
    void testValidAuthorityCodePatterns() {
        // Various valid patterns
        assertEquals("A:1", CRSUtils.normalizeAuthorityCode("a:1"));
        assertEquals("AB:12", CRSUtils.normalizeAuthorityCode("ab:12"));
        assertEquals("A1:B2", CRSUtils.normalizeAuthorityCode("a1:B2"));
        assertEquals("A_B:123", CRSUtils.normalizeAuthorityCode("a_b:123"));
    }
}
