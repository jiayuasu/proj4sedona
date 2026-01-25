package org.proj4sedona.core;

import org.junit.jupiter.api.Test;
import org.proj4sedona.constants.Values;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for core data structures.
 */
class CoreTest {

    private static final double DELTA = 1e-10;

    // ========== Point Tests ==========

    @Test
    void testPointXY() {
        Point p = new Point(1.5, 2.5);
        assertEquals(1.5, p.x, DELTA);
        assertEquals(2.5, p.y, DELTA);
        assertEquals(0, p.z, DELTA);
        assertTrue(Double.isNaN(p.m));
    }

    @Test
    void testPointXYZ() {
        Point p = new Point(1.5, 2.5, 3.5);
        assertEquals(1.5, p.x, DELTA);
        assertEquals(2.5, p.y, DELTA);
        assertEquals(3.5, p.z, DELTA);
        assertTrue(Double.isNaN(p.m));
    }

    @Test
    void testPointXYZM() {
        Point p = new Point(1.5, 2.5, 3.5, 4.5);
        assertEquals(1.5, p.x, DELTA);
        assertEquals(2.5, p.y, DELTA);
        assertEquals(3.5, p.z, DELTA);
        assertEquals(4.5, p.m, DELTA);
    }

    @Test
    void testPointFromArray() {
        // Mirrors lib/common/toPoint.js behavior
        Point p2 = new Point(new double[]{1.0, 2.0});
        assertEquals(1.0, p2.x, DELTA);
        assertEquals(2.0, p2.y, DELTA);
        assertEquals(0, p2.z, DELTA);

        Point p3 = new Point(new double[]{1.0, 2.0, 3.0});
        assertEquals(3.0, p3.z, DELTA);

        Point p4 = new Point(new double[]{1.0, 2.0, 3.0, 4.0});
        assertEquals(4.0, p4.m, DELTA);
    }

    @Test
    void testPointFromString() {
        // Mirrors lib/Point.js string parsing
        Point p = new Point("1.5, 2.5");
        assertEquals(1.5, p.x, DELTA);
        assertEquals(2.5, p.y, DELTA);

        Point p3 = new Point("1.0,2.0,3.0");
        assertEquals(3.0, p3.z, DELTA);
    }

    @Test
    void testPointCopy() {
        Point original = new Point(1.0, 2.0, 3.0, 4.0);
        Point copy = original.copy();
        
        assertEquals(original.x, copy.x, DELTA);
        assertEquals(original.y, copy.y, DELTA);
        assertEquals(original.z, copy.z, DELTA);
        assertEquals(original.m, copy.m, DELTA);
        
        // Modify copy, original should be unchanged
        copy.x = 99.0;
        assertEquals(1.0, original.x, DELTA);
    }

    @Test
    void testPointToArray() {
        Point p2 = new Point(1.0, 2.0);
        double[] arr2 = p2.toArray();
        assertEquals(2, arr2.length);
        assertEquals(1.0, arr2[0], DELTA);
        assertEquals(2.0, arr2[1], DELTA);

        Point p3 = new Point(1.0, 2.0, 3.0);
        double[] arr3 = p3.toArray();
        assertEquals(3, arr3.length);
        assertEquals(3.0, arr3[2], DELTA);

        Point p4 = new Point(1.0, 2.0, 3.0, 4.0);
        double[] arr4 = p4.toArray();
        assertEquals(4, arr4.length);
        assertEquals(4.0, arr4[3], DELTA);
    }

    @Test
    void testPointHasZ() {
        Point p = new Point(1.0, 2.0);
        assertFalse(p.hasZ());
        
        Point p3 = new Point(1.0, 2.0, 3.0);
        assertTrue(p3.hasZ());
    }

    @Test
    void testPointHasM() {
        Point p = new Point(1.0, 2.0);
        assertFalse(p.hasM());
        
        Point pm = new Point(1.0, 2.0, 0, 4.0);
        assertTrue(pm.hasM());
    }

    @Test
    void testPointStaticFactories() {
        Point p1 = Point.of(1.0, 2.0);
        assertEquals(1.0, p1.x, DELTA);
        
        Point p2 = Point.toPoint(new double[]{3.0, 4.0});
        assertEquals(3.0, p2.x, DELTA);
    }

    @Test
    void testPointEquals() {
        Point p1 = new Point(1.0, 2.0, 3.0);
        Point p2 = new Point(1.0, 2.0, 3.0);
        Point p3 = new Point(1.0, 2.0, 4.0);
        
        assertEquals(p1, p2);
        assertNotEquals(p1, p3);
    }

    // ========== ProjectionDef Tests ==========

    @Test
    void testProjectionDefDefaults() {
        ProjectionDef def = new ProjectionDef();
        
        // Verify defaults match proj4js
        assertEquals(1.0, def.getK0(), DELTA);
        assertEquals(0.0, def.getX0(), DELTA);
        assertEquals(0.0, def.getY0(), DELTA);
        assertEquals("enu", def.getAxis());
    }

    @Test
    void testProjectionDefSetters() {
        ProjectionDef def = new ProjectionDef();
        
        def.setProjName("merc");
        def.setEllps("WGS84");
        def.setLat0(0.5);
        def.setLong0(1.0);
        def.setK0(0.9996);
        def.setX0(500000.0);
        def.setY0(0.0);
        
        assertEquals("merc", def.getProjName());
        assertEquals("WGS84", def.getEllps());
        assertEquals(0.5, def.getLat0(), DELTA);
        assertEquals(1.0, def.getLong0(), DELTA);
        assertEquals(0.9996, def.getK0(), DELTA);
        assertEquals(500000.0, def.getX0(), DELTA);
    }

    @Test
    void testProjectionDefDatumParams() {
        ProjectionDef def = new ProjectionDef();
        
        def.setDatumCode("potsdam");
        def.setDatumParams(new double[]{598.1, 73.7, 418.2, 0.202, 0.045, -2.455, 6.7});
        
        assertEquals("potsdam", def.getDatumCode());
        assertEquals(7, def.getDatumParams().length);
        assertEquals(598.1, def.getDatumParams()[0], DELTA);
    }

    @Test
    void testProjectionDefUtm() {
        ProjectionDef def = new ProjectionDef();
        
        def.setProjName("utm");
        def.setZone(32);
        def.setUtmSouth(false);
        
        assertEquals("utm", def.getProjName());
        assertEquals(32, def.getZone());
        assertFalse(def.getUtmSouth());
    }

    // ========== DatumParams Tests ==========

    @Test
    void testDatumParamsWgs84() {
        // WGS84 with no transformation needed
        DatumParams datum = new DatumParams(
            "wgs84",
            new double[]{0, 0, 0},
            Values.SRS_WGS84_SEMIMAJOR,
            Values.SRS_WGS84_SEMIMINOR,
            Values.SRS_WGS84_ESQUARED,
            0.006739496742276434,  // ep2 for WGS84
            null
        );
        
        // With all zeros, should remain PJD_WGS84
        assertTrue(datum.isWgs84());
        assertFalse(datum.is3Param());
    }

    @Test
    void testDatumParams3Param() {
        // 3-parameter datum (translation only)
        DatumParams datum = new DatumParams(
            "nad83",
            new double[]{-8.0, 160.0, 176.0},  // Example translation
            6378137,
            6356752.314,
            0.00669438,
            0.00673950,
            null
        );
        
        assertTrue(datum.is3Param());
        assertFalse(datum.is7Param());
        assertEquals(3, datum.getDatumParams().length);
    }

    @Test
    void testDatumParams7Param() {
        // 7-parameter datum (Helmert transformation)
        // Mirrors lib/datum.js conversion of rotation params
        DatumParams datum = new DatumParams(
            "potsdam",
            new double[]{598.1, 73.7, 418.2, 0.202, 0.045, -2.455, 6.7},
            6377397.155,  // Bessel a
            6356078.963,  // Bessel b
            0.006674372,
            0.006719219,
            null
        );
        
        assertTrue(datum.is7Param());
        assertFalse(datum.is3Param());
        
        // Verify rotation params are converted to radians
        // Original: 0.202 arc-seconds -> 0.202 * SEC_TO_RAD
        double expectedRx = 0.202 * Values.SEC_TO_RAD;
        assertEquals(expectedRx, datum.getDatumParams()[3], DELTA);
        
        // Verify scale is converted from ppm to multiplier
        // Original: 6.7 ppm -> (6.7 / 1000000) + 1
        double expectedScale = (6.7 / 1000000.0) + 1.0;
        assertEquals(expectedScale, datum.getDatumParams()[6], DELTA);
    }

    @Test
    void testDatumParamsNoDatum() {
        DatumParams datum = new DatumParams(
            "none",
            null,
            6378137,
            6356752.314,
            0.00669438,
            0.00673950,
            null
        );
        
        assertTrue(datum.isNoDatum());
        assertFalse(datum.isWgs84());
    }

    @Test
    void testDatumParamsSimpleConstructor() {
        DatumParams datum = new DatumParams(
            6378137,
            6356752.314,
            0.00669438,
            0.00673950
        );
        
        assertTrue(datum.isWgs84());
        assertEquals(6378137, datum.getA(), DELTA);
        assertEquals(6356752.314, datum.getB(), DELTA);
    }

    @Test
    void testDatumParamsToString() {
        DatumParams datum = new DatumParams(
            "test",
            new double[]{1, 2, 3},
            6378137,
            6356752.314,
            0.00669438,
            0.00673950,
            null
        );
        
        String str = datum.toString();
        assertTrue(str.contains("3PARAM"));
        assertTrue(str.contains("6378137"));
    }
}
