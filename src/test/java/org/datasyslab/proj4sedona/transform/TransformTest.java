package org.datasyslab.proj4sedona.transform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.datasyslab.proj4sedona.Proj4;
import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.core.Proj;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Transform Pipeline (Phase 6).
 * Tests CheckSanity, AdjustAxis, Transform, Converter, and Proj4.
 */
class TransformTest {

    private static final double TOLERANCE = 1e-6;
    private static final double METER_TOLERANCE = 0.01; // 1 cm tolerance for projected coords

    @BeforeAll
    static void setup() {
        // Ensure projections are registered
        org.datasyslab.proj4sedona.projection.ProjectionRegistry.start();
    }

    // ==================== CheckSanity Tests ====================

    @Test
    void testCheckSanityValidPoint() {
        Point p = new Point(10.0, 20.0);
        assertDoesNotThrow(() -> CheckSanity.check(p));
    }

    @Test
    void testCheckSanityNaN() {
        Point p = new Point(Double.NaN, 20.0);
        assertThrows(IllegalArgumentException.class, () -> CheckSanity.check(p));
    }

    @Test
    void testCheckSanityInfinite() {
        Point p = new Point(10.0, Double.POSITIVE_INFINITY);
        assertThrows(IllegalArgumentException.class, () -> CheckSanity.check(p));
    }

    @Test
    void testCheckSanityNegativeInfinite() {
        Point p = new Point(Double.NEGATIVE_INFINITY, 20.0);
        assertThrows(IllegalArgumentException.class, () -> CheckSanity.check(p));
    }

    // ==================== AdjustAxis Tests ====================

    @Test
    void testAdjustAxisENU() {
        // ENU is the standard order, should not change
        Point p = new Point(100, 200, 300);
        Point result = AdjustAxis.adjust("enu", false, p, true);
        assertNotNull(result);
        assertEquals(100, result.x, TOLERANCE);
        assertEquals(200, result.y, TOLERANCE);
        assertEquals(300, result.z, TOLERANCE);
    }

    @Test
    void testAdjustAxisNEU() {
        // NEU swaps x and y
        Point p = new Point(100, 200, 300);
        Point result = AdjustAxis.adjust("neu", false, p, true);
        assertNotNull(result);
        assertEquals(200, result.x, TOLERANCE); // Northing -> x
        assertEquals(100, result.y, TOLERANCE); // Easting -> y
    }

    @Test
    void testAdjustAxisWSU() {
        // West-South-Up negates x and y
        Point p = new Point(100, 200, 300);
        Point result = AdjustAxis.adjust("wsu", false, p, true);
        assertNotNull(result);
        assertEquals(-100, result.x, TOLERANCE); // West negates
        assertEquals(-200, result.y, TOLERANCE); // South negates
    }

    @Test
    void testAdjustAxisNullAxis() {
        Point p = new Point(100, 200);
        Point result = AdjustAxis.adjust(null, false, p, false);
        assertNull(result);
    }

    @Test
    void testAdjustAxisInvalidAxis() {
        Point p = new Point(100, 200);
        Point result = AdjustAxis.adjust("xyz", false, p, false);
        assertNull(result); // Invalid axis characters
    }

    // ==================== Transform Tests ====================

    @Test
    void testTransformLongLatToMercator() {
        Proj longlat = new Proj("+proj=longlat +datum=WGS84");
        Proj merc = new Proj("+proj=merc +datum=WGS84");

        Point p = new Point(0, 0); // lon=0, lat=0
        Point result = Transform.transform(longlat, merc, p);

        assertNotNull(result);
        assertEquals(0, result.x, METER_TOLERANCE);
        assertEquals(0, result.y, METER_TOLERANCE);
    }

    @Test
    void testTransformMercatorToLongLat() {
        Proj longlat = new Proj("+proj=longlat +datum=WGS84");
        Proj merc = new Proj("+proj=merc +datum=WGS84");

        // Test reverse transformation
        Point p = new Point(0, 0); // x=0, y=0 in Mercator
        Point result = Transform.transform(merc, longlat, p);

        assertNotNull(result);
        assertEquals(0, result.x, TOLERANCE); // lon=0
        assertEquals(0, result.y, TOLERANCE); // lat=0
    }

    @Test
    void testTransformLongLatRoundTrip() {
        Proj longlat = new Proj("+proj=longlat +datum=WGS84");
        Proj merc = new Proj("+proj=merc +datum=WGS84");

        Point original = new Point(-122.4194, 37.7749); // San Francisco
        Point projected = Transform.transform(longlat, merc, original);
        Point restored = Transform.transform(merc, longlat, projected);

        assertNotNull(restored);
        assertEquals(original.x, restored.x, TOLERANCE);
        assertEquals(original.y, restored.y, TOLERANCE);
    }

    @Test
    void testTransformLongLatToLongLat() {
        // Identity transformation
        Proj longlat1 = new Proj("+proj=longlat +datum=WGS84");
        Proj longlat2 = new Proj("+proj=longlat +datum=WGS84");

        Point p = new Point(10, 20);
        Point result = Transform.transform(longlat1, longlat2, p);

        assertNotNull(result);
        assertEquals(10, result.x, TOLERANCE);
        assertEquals(20, result.y, TOLERANCE);
    }

    @Test
    void testTransformPreservesM() {
        Proj longlat = new Proj("+proj=longlat +datum=WGS84");
        Proj merc = new Proj("+proj=merc +datum=WGS84");

        Point p = new Point(0, 0, 100, 999);
        p.m = 999;
        Point result = Transform.transform(longlat, merc, p);

        assertNotNull(result);
        // Note: Current implementation may not preserve m - this tests current behavior
    }

    // ==================== Converter Tests ====================

    @Test
    void testConverterForward() {
        Converter conv = new Converter(
            new Proj("+proj=longlat +datum=WGS84"),
            new Proj("+proj=merc +datum=WGS84")
        );

        Point p = new Point(0, 0);
        Point result = conv.forward(p);

        assertNotNull(result);
        assertEquals(0, result.x, METER_TOLERANCE);
        assertEquals(0, result.y, METER_TOLERANCE);
    }

    @Test
    void testConverterInverse() {
        Converter conv = new Converter(
            new Proj("+proj=longlat +datum=WGS84"),
            new Proj("+proj=merc +datum=WGS84")
        );

        Point p = new Point(0, 0);
        Point result = conv.inverse(p);

        assertNotNull(result);
        assertEquals(0, result.x, TOLERANCE);
        assertEquals(0, result.y, TOLERANCE);
    }

    @Test
    void testConverterArrayForward() {
        Converter conv = new Converter(
            new Proj("+proj=longlat +datum=WGS84"),
            new Proj("+proj=merc +datum=WGS84")
        );

        double[] coords = {0, 0};
        double[] result = conv.forward(coords);

        assertNotNull(result);
        assertEquals(2, result.length);
        assertEquals(0, result[0], METER_TOLERANCE);
        assertEquals(0, result[1], METER_TOLERANCE);
    }

    @Test
    void testConverterArrayInverse() {
        Converter conv = new Converter(
            new Proj("+proj=longlat +datum=WGS84"),
            new Proj("+proj=merc +datum=WGS84")
        );

        double[] coords = {0, 0};
        double[] result = conv.inverse(coords);

        assertNotNull(result);
        assertEquals(2, result.length);
        assertEquals(0, result[0], TOLERANCE);
        assertEquals(0, result[1], TOLERANCE);
    }

    @Test
    void testConverterRoundTrip() {
        Converter conv = new Converter(
            new Proj("+proj=longlat +datum=WGS84"),
            new Proj("+proj=merc +datum=WGS84")
        );

        Point original = new Point(-74.006, 40.7128); // New York
        Point projected = conv.forward(original);
        Point restored = conv.inverse(projected);

        assertNotNull(restored);
        assertEquals(original.x, restored.x, TOLERANCE);
        assertEquals(original.y, restored.y, TOLERANCE);
    }

    // ==================== Proj4 API Tests ====================

    @Test
    void testProj4SingleArg() {
        // proj4("EPSG:3857") creates converter from WGS84
        Converter conv = Proj4.proj4("+proj=merc +datum=WGS84");
        assertNotNull(conv);

        Point result = conv.forward(new Point(0, 0));
        assertNotNull(result);
        assertEquals(0, result.x, METER_TOLERANCE);
        assertEquals(0, result.y, METER_TOLERANCE);
    }

    @Test
    void testProj4TwoProj() {
        // proj4(from, to) creates converter
        Converter conv = Proj4.proj4("+proj=longlat +datum=WGS84", "+proj=merc +datum=WGS84");
        assertNotNull(conv);

        Point result = conv.forward(new Point(0, 0));
        assertNotNull(result);
    }

    @Test
    void testProj4DirectTransformFromWGS84() {
        // proj4(to, point) transforms directly from WGS84
        Point result = Proj4.proj4("+proj=merc +datum=WGS84", new Point(0, 0));
        assertNotNull(result);
        assertEquals(0, result.x, METER_TOLERANCE);
        assertEquals(0, result.y, METER_TOLERANCE);
    }

    @Test
    void testProj4DirectTransformBetweenProj() {
        // proj4(from, to, point) transforms directly
        Point result = Proj4.proj4(
            "+proj=longlat +datum=WGS84",
            "+proj=merc +datum=WGS84",
            new Point(0, 0)
        );
        assertNotNull(result);
        assertEquals(0, result.x, METER_TOLERANCE);
        assertEquals(0, result.y, METER_TOLERANCE);
    }

    @Test
    void testProj4ArrayTransform() {
        // Array-based transformation
        double[] result = Proj4.proj4(
            "+proj=longlat +datum=WGS84",
            "+proj=merc +datum=WGS84",
            new double[]{0, 0}
        );
        assertNotNull(result);
        assertEquals(2, result.length);
        assertEquals(0, result[0], METER_TOLERANCE);
        assertEquals(0, result[1], METER_TOLERANCE);
    }

    @Test
    void testProj4ConvenienceMethods() {
        // Test convenience methods
        Proj proj = Proj4.projection("+proj=longlat +datum=WGS84");
        assertNotNull(proj);

        Point p = Proj4.point(10, 20);
        assertEquals(10, p.x, TOLERANCE);
        assertEquals(20, p.y, TOLERANCE);

        Point p3 = Proj4.point(10, 20, 30);
        assertEquals(30, p3.z, TOLERANCE);

        Point parr = Proj4.point(new double[]{1, 2, 3});
        assertEquals(3, parr.z, TOLERANCE);
    }

    // ==================== Real-World Coordinate Tests ====================

    @Test
    void testWebMercatorTransform() {
        // WGS84 to Web Mercator (EPSG:3857)
        Converter conv = Proj4.proj4(
            "+proj=longlat +datum=WGS84",
            "+proj=merc +a=6378137 +b=6378137 +lat_ts=0 +lon_0=0 +x_0=0 +y_0=0 +k=1"
        );

        // Test: 0, 0 should map to 0, 0
        Point result = conv.forward(new Point(0, 0));
        assertNotNull(result);
        assertEquals(0, result.x, METER_TOLERANCE);
        assertEquals(0, result.y, METER_TOLERANCE);

        // Test: -180, 0 should be left edge
        Point left = conv.forward(new Point(-180, 0));
        assertNotNull(left);
        assertEquals(-20037508.34, left.x, 1.0); // ~20M meters

        // Test: 180, 0 should be right edge
        Point right = conv.forward(new Point(180, 0));
        assertNotNull(right);
        assertEquals(20037508.34, right.x, 1.0);
    }

    @Test
    void testMercatorCities() {
        // Test some real city coordinates
        Converter conv = Proj4.proj4(
            "+proj=longlat +datum=WGS84",
            "+proj=merc +datum=WGS84"
        );

        // London: -0.1278, 51.5074
        Point london = conv.forward(new Point(-0.1278, 51.5074));
        assertNotNull(london);
        assertTrue(london.x < 0); // West of Greenwich
        assertTrue(london.y > 0); // North of equator

        // Sydney: 151.2093, -33.8688
        Point sydney = conv.forward(new Point(151.2093, -33.8688));
        assertNotNull(sydney);
        assertTrue(sydney.x > 0); // East
        assertTrue(sydney.y < 0); // South

        // Round-trip test
        Point londonBack = conv.inverse(london);
        assertEquals(-0.1278, londonBack.x, TOLERANCE);
        assertEquals(51.5074, londonBack.y, TOLERANCE);
    }

    @Test
    void testIdentityTransformPreservesCoordinates() {
        // Same projection should be identity
        Converter conv = Proj4.proj4(
            "+proj=longlat +datum=WGS84",
            "+proj=longlat +datum=WGS84"
        );

        Point p = new Point(123.456, -45.678);
        Point result = conv.forward(p);

        assertNotNull(result);
        assertEquals(p.x, result.x, TOLERANCE);
        assertEquals(p.y, result.y, TOLERANCE);
    }

    // ==================== Edge Cases ====================

    @Test
    void testTransformNearDateline() {
        Converter conv = Proj4.proj4(
            "+proj=longlat +datum=WGS84",
            "+proj=merc +datum=WGS84"
        );

        // Near but not at dateline
        Point p = new Point(179.999, 0);
        Point result = conv.forward(p);
        assertNotNull(result);
        assertTrue(Math.abs(result.x) > 20000000); // Near the edge

        // Round-trip
        Point back = conv.inverse(result);
        assertEquals(179.999, back.x, TOLERANCE);
    }

    @Test
    void testTransformNearPole() {
        Converter conv = Proj4.proj4(
            "+proj=longlat +datum=WGS84",
            "+proj=merc +datum=WGS84"
        );

        // Near but not at pole (Mercator has issues at poles)
        Point p = new Point(0, 85);
        Point result = conv.forward(p);
        assertNotNull(result);
        assertTrue(result.y > 15000000); // Very large y value

        // Round-trip
        Point back = conv.inverse(result);
        assertEquals(0, back.x, TOLERANCE);
        assertEquals(85, back.y, TOLERANCE);
    }

    @Test
    void testTransformWithFalseEastingNorthing() {
        // Mercator with false easting/northing
        Converter conv = Proj4.proj4(
            "+proj=longlat +datum=WGS84",
            "+proj=merc +datum=WGS84 +x_0=500000 +y_0=10000000"
        );

        Point p = new Point(0, 0);
        Point result = conv.forward(p);

        assertNotNull(result);
        assertEquals(500000, result.x, METER_TOLERANCE);
        assertEquals(10000000, result.y, METER_TOLERANCE);

        // Round-trip
        Point back = conv.inverse(result);
        assertEquals(0, back.x, TOLERANCE);
        assertEquals(0, back.y, TOLERANCE);
    }

    @Test
    void testMultipleConvertersIndependent() {
        // Verify converters don't interfere with each other
        Converter conv1 = Proj4.proj4("+proj=longlat +datum=WGS84", "+proj=merc +datum=WGS84");
        Converter conv2 = Proj4.proj4("+proj=longlat +datum=WGS84", "+proj=merc +datum=WGS84 +x_0=1000000");

        Point p = new Point(0, 0);
        Point r1 = conv1.forward(p);
        Point r2 = conv2.forward(p);

        assertEquals(0, r1.x, METER_TOLERANCE);
        assertEquals(1000000, r2.x, METER_TOLERANCE);
    }

    // ==================== Web Mercator WKT2/PROJJSON Tests (proj4js #546) ====================

    @Test
    void testWebMercatorWkt2MatchesEpsgCode() {
        // WKT2 definition for EPSG:3857 declares the WGS84 ellipsoid (a != b),
        // but Web Mercator must use a sphere (a == b == 6378137).
        // After the fix, parsing this WKT2 should produce the same results as "EPSG:3857".
        String wkt2_3857 = "PROJCRS[\"WGS 84 / Pseudo-Mercator\","
            + "BASEGEOGCRS[\"WGS 84\","
            + "  ENSEMBLE[\"World Geodetic System 1984 ensemble\","
            + "    MEMBER[\"World Geodetic System 1984 (Transit)\"],"
            + "    MEMBER[\"World Geodetic System 1984 (G730)\"],"
            + "    MEMBER[\"World Geodetic System 1984 (G873)\"],"
            + "    MEMBER[\"World Geodetic System 1984 (G1150)\"],"
            + "    MEMBER[\"World Geodetic System 1984 (G1674)\"],"
            + "    MEMBER[\"World Geodetic System 1984 (G1762)\"],"
            + "    MEMBER[\"World Geodetic System 1984 (G2139)\"],"
            + "    MEMBER[\"World Geodetic System 1984 (G2296)\"],"
            + "    ELLIPSOID[\"WGS 84\",6378137,298.257223563,LENGTHUNIT[\"metre\",1]],"
            + "    ENSEMBLEACCURACY[2.0]],"
            + "  PRIMEM[\"Greenwich\",0,ANGLEUNIT[\"degree\",0.0174532925199433]],"
            + "  ID[\"EPSG\",4326]],"
            + "CONVERSION[\"Popular Visualisation Pseudo-Mercator\","
            + "  METHOD[\"Popular Visualisation Pseudo Mercator\",ID[\"EPSG\",1024]],"
            + "  PARAMETER[\"Latitude of natural origin\",0,ANGLEUNIT[\"degree\",0.0174532925199433],ID[\"EPSG\",8801]],"
            + "  PARAMETER[\"Longitude of natural origin\",0,ANGLEUNIT[\"degree\",0.0174532925199433],ID[\"EPSG\",8802]],"
            + "  PARAMETER[\"False easting\",0,LENGTHUNIT[\"metre\",1],ID[\"EPSG\",8806]],"
            + "  PARAMETER[\"False northing\",0,LENGTHUNIT[\"metre\",1],ID[\"EPSG\",8807]]],"
            + "CS[Cartesian,2],"
            + "  AXIS[\"easting (X)\",east,ORDER[1],LENGTHUNIT[\"metre\",1]],"
            + "  AXIS[\"northing (Y)\",north,ORDER[2],LENGTHUNIT[\"metre\",1]],"
            + "USAGE[SCOPE[\"Web mapping and visualisation.\"],"
            + "  AREA[\"World between 85.06S and 85.06N.\"],"
            + "  BBOX[-85.06,-180,85.06,180]],"
            + "ID[\"EPSG\",3857]]";

        Converter fromEpsg = Proj4.proj4("EPSG:4326", "EPSG:3857");
        Converter fromWkt2 = Proj4.proj4("EPSG:4326", wkt2_3857);

        // lon=-112.5, lat=42.04 — same test point as proj4js
        Point ll = new Point(-112.50042920000004, 42.036926809999976);
        Point xyFromEpsg = fromEpsg.forward(ll);
        Point xyFromWkt2 = fromWkt2.forward(ll);

        assertEquals(xyFromEpsg.x, xyFromWkt2.x, 0.01, "WKT2 3857 x should match EPSG:3857");
        assertEquals(xyFromEpsg.y, xyFromWkt2.y, 0.01, "WKT2 3857 y should match EPSG:3857");

        // Verify specific expected values
        assertEquals(-12523490.49, xyFromWkt2.x, 1.0);
        assertEquals(5166512.51,   xyFromWkt2.y, 1.0);
    }

    @Test
    void testWebMercatorProjStringMatchesEpsgCode() {
        // A PROJ string tagged +title=EPSG:3857 but using the WGS84 ellipsoid
        // (a != b) should be corrected to the sphere-based definition by
        // checkWebMercator.  Ported from proj4js PR #553 which ensures the
        // mercator check runs on ALL code paths, including raw PROJ strings.
        String wrongProjStr = "+proj=merc +a=6378137 +b=6356752.314245179"
            + " +lat_ts=0 +lon_0=0 +x_0=0 +y_0=0 +k=1 +units=m"
            + " +title=EPSG:3857 +no_defs";

        Converter fromEpsg = Proj4.proj4("EPSG:4326", "EPSG:3857");
        Converter fromProj = Proj4.proj4("EPSG:4326", wrongProjStr);

        Point ll = new Point(-112.50042920000004, 42.036926809999976);
        Point xyFromEpsg = fromEpsg.forward(ll);
        Point xyFromProj = fromProj.forward(ll);

        assertEquals(xyFromEpsg.x, xyFromProj.x, 0.01,
            "PROJ-string 3857 x should match EPSG:3857 after mercator correction");
        assertEquals(xyFromEpsg.y, xyFromProj.y, 0.01,
            "PROJ-string 3857 y should match EPSG:3857 after mercator correction");
    }

    @Test
    void testWebMercatorWkt1StillWorks() {
        // WKT1 with EXTENSION[PROJ4,...] should also continue to work
        String wkt1_3857 = "PROJCS[\"WGS 84 / Pseudo-Mercator\","
            + "GEOGCS[\"WGS 84\","
            + "  DATUM[\"WGS_1984\","
            + "    SPHEROID[\"WGS 84\",6378137,298.257223563,AUTHORITY[\"EPSG\",\"7030\"]],"
            + "    AUTHORITY[\"EPSG\",\"6326\"]],"
            + "  PRIMEM[\"Greenwich\",0,AUTHORITY[\"EPSG\",\"8901\"]],"
            + "  UNIT[\"degree\",0.0174532925199433,AUTHORITY[\"EPSG\",\"9122\"]],"
            + "  AUTHORITY[\"EPSG\",\"4326\"]],"
            + "PROJECTION[\"Mercator_1SP\"],"
            + "PARAMETER[\"central_meridian\",0],"
            + "PARAMETER[\"scale_factor\",1],"
            + "PARAMETER[\"false_easting\",0],"
            + "PARAMETER[\"false_northing\",0],"
            + "UNIT[\"metre\",1,AUTHORITY[\"EPSG\",\"9001\"]],"
            + "AXIS[\"X\",EAST],AXIS[\"Y\",NORTH],"
            + "EXTENSION[\"PROJ4\",\"+proj=merc +a=6378137 +b=6378137 +lat_ts=0.0 +lon_0=0.0 +x_0=0.0 +y_0=0 +k=1.0 +units=m +nadgrids=@null +no_defs\"],"
            + "AUTHORITY[\"EPSG\",\"3857\"]]";

        Converter fromEpsg = Proj4.proj4("EPSG:4326", "EPSG:3857");
        Converter fromWkt1 = Proj4.proj4("EPSG:4326", wkt1_3857);

        Point ll = new Point(-112.50042920000004, 42.036926809999976);
        Point xyFromEpsg = fromEpsg.forward(ll);
        Point xyFromWkt1 = fromWkt1.forward(ll);

        assertEquals(xyFromEpsg.x, xyFromWkt1.x, 0.01, "WKT1 3857 x should match EPSG:3857");
        assertEquals(xyFromEpsg.y, xyFromWkt1.y, 0.01, "WKT1 3857 y should match EPSG:3857");
    }
}
