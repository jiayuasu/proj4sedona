package org.datasyslab.proj4sedona.datum;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.datasyslab.proj4sedona.Proj4;
import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.DatumParams;
import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.core.Proj;
import org.datasyslab.proj4sedona.transform.Converter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Datum Transformations (Phase 7).
 * Tests DatumUtils and DatumTransform functionality.
 */
class DatumTest {

    private static final double TOLERANCE = 1e-9;
    private static final double METER_TOLERANCE = 0.01;   // 1 cm for projected
    private static final double DEGREE_TOLERANCE = 1e-7;  // ~1 cm at equator

    @BeforeAll
    static void setup() {
        org.datasyslab.proj4sedona.projection.ProjectionRegistry.start();
    }

    // ==================== DatumUtils Tests ====================

    @Test
    void testCompareDatumsIdentical() {
        DatumParams d1 = new DatumParams(
            Values.SRS_WGS84_SEMIMAJOR, Values.SRS_WGS84_SEMIMINOR,
            Values.SRS_WGS84_ESQUARED, 0.006739496742276434
        );
        DatumParams d2 = new DatumParams(
            Values.SRS_WGS84_SEMIMAJOR, Values.SRS_WGS84_SEMIMINOR,
            Values.SRS_WGS84_ESQUARED, 0.006739496742276434
        );

        assertTrue(DatumUtils.compareDatums(d1, d2));
    }

    @Test
    void testCompareDatumsDifferentA() {
        DatumParams d1 = new DatumParams(6378137.0, 6356752.314, 0.0066943799901413165, 0.0);
        DatumParams d2 = new DatumParams(6378388.0, 6356911.946, 0.0067226700223333, 0.0);

        assertFalse(DatumUtils.compareDatums(d1, d2));
    }

    @Test
    void testCompareDatumsGRS80vsWGS84() {
        // GRS80 and WGS84 should be considered identical (within tolerance)
        DatumParams wgs84 = new DatumParams(6378137.0, 6356752.314, 0.00669437999014132, 0.0);
        DatumParams grs80 = new DatumParams(6378137.0, 6356752.3141, 0.00669438002290079, 0.0);

        // The difference in es is ~3.3e-11, which is less than 5e-11 tolerance
        assertTrue(DatumUtils.compareDatums(wgs84, grs80));
    }

    @Test
    void testGeodeticToGeocentricOrigin() {
        Point p = new Point(0, 0, 0);
        Point result = DatumUtils.geodeticToGeocentric(p, Values.SRS_WGS84_ESQUARED, Values.SRS_WGS84_SEMIMAJOR);

        assertNotNull(result);
        assertEquals(Values.SRS_WGS84_SEMIMAJOR, result.x, METER_TOLERANCE);
        assertEquals(0, result.y, METER_TOLERANCE);
        assertEquals(0, result.z, METER_TOLERANCE);
    }

    @Test
    void testGeodeticToGeocentricNorthPole() {
        Point p = new Point(0, Values.HALF_PI, 0);
        Point result = DatumUtils.geodeticToGeocentric(p, Values.SRS_WGS84_ESQUARED, Values.SRS_WGS84_SEMIMAJOR);

        assertNotNull(result);
        assertEquals(0, result.x, METER_TOLERANCE);
        assertEquals(0, result.y, METER_TOLERANCE);
        assertTrue(result.z > 6350000);
    }

    @Test
    void testGeodeticToGeocentricSouthPole() {
        Point p = new Point(0, -Values.HALF_PI, 0);
        Point result = DatumUtils.geodeticToGeocentric(p, Values.SRS_WGS84_ESQUARED, Values.SRS_WGS84_SEMIMAJOR);

        assertNotNull(result);
        assertEquals(0, result.x, METER_TOLERANCE);
        assertEquals(0, result.y, METER_TOLERANCE);
        assertTrue(result.z < -6350000);
    }

    @Test
    void testGeodeticToGeocentricWithHeight() {
        Point p = new Point(0, 0, 1000);
        Point result = DatumUtils.geodeticToGeocentric(p, Values.SRS_WGS84_ESQUARED, Values.SRS_WGS84_SEMIMAJOR);

        assertNotNull(result);
        assertEquals(Values.SRS_WGS84_SEMIMAJOR + 1000, result.x, METER_TOLERANCE);
    }

    @Test
    void testGeocentricToGeodeticOrigin() {
        Point p = new Point(Values.SRS_WGS84_SEMIMAJOR, 0, 0);
        Point result = DatumUtils.geocentricToGeodetic(p, Values.SRS_WGS84_ESQUARED, 
            Values.SRS_WGS84_SEMIMAJOR, Values.SRS_WGS84_SEMIMINOR);

        assertNotNull(result);
        assertEquals(0, result.x, TOLERANCE);
        assertEquals(0, result.y, TOLERANCE);
    }

    @Test
    void testGeocentricToGeodeticRoundTrip() {
        Point original = new Point(Math.toRadians(10), Math.toRadians(45), 100);
        
        Point geocentric = DatumUtils.geodeticToGeocentric(original, 
            Values.SRS_WGS84_ESQUARED, Values.SRS_WGS84_SEMIMAJOR);
        
        Point restored = DatumUtils.geocentricToGeodetic(geocentric, 
            Values.SRS_WGS84_ESQUARED, Values.SRS_WGS84_SEMIMAJOR, Values.SRS_WGS84_SEMIMINOR);

        assertNotNull(restored);
        assertEquals(original.x, restored.x, TOLERANCE);
        assertEquals(original.y, restored.y, TOLERANCE);
        assertEquals(original.z, restored.z, METER_TOLERANCE);
    }

    @Test
    void testGeocentricToWgs843Param() {
        Point p = new Point(1000000, 2000000, 3000000);
        double[] params = {100, 200, 300};

        Point result = DatumUtils.geocentricToWgs84(p, Values.PJD_3PARAM, params);

        assertNotNull(result);
        assertEquals(1000100, result.x, TOLERANCE);
        assertEquals(2000200, result.y, TOLERANCE);
        assertEquals(3000300, result.z, TOLERANCE);
    }

    @Test
    void testGeocentricFromWgs843Param() {
        Point p = new Point(1000100, 2000200, 3000300);
        double[] params = {100, 200, 300};

        Point result = DatumUtils.geocentricFromWgs84(p, Values.PJD_3PARAM, params);

        assertNotNull(result);
        assertEquals(1000000, result.x, TOLERANCE);
        assertEquals(2000000, result.y, TOLERANCE);
        assertEquals(3000000, result.z, TOLERANCE);
    }

    @Test
    void testGeocentricToWgs847Param() {
        Point p = new Point(1000000, 2000000, 3000000);
        double[] params = {0, 0, 0, 0, 0, 0, 1.0};

        Point result = DatumUtils.geocentricToWgs84(p, Values.PJD_7PARAM, params);

        assertNotNull(result);
        assertEquals(1000000, result.x, TOLERANCE);
        assertEquals(2000000, result.y, TOLERANCE);
        assertEquals(3000000, result.z, TOLERANCE);
    }

    @Test
    void testGeocentricToWgs847ParamWithTranslation() {
        Point p = new Point(1000000, 2000000, 3000000);
        double[] params = {100, 200, 300, 0, 0, 0, 1.0};

        Point result = DatumUtils.geocentricToWgs84(p, Values.PJD_7PARAM, params);

        assertNotNull(result);
        assertEquals(1000100, result.x, TOLERANCE);
        assertEquals(2000200, result.y, TOLERANCE);
        assertEquals(3000300, result.z, TOLERANCE);
    }

    @Test
    void testGeocentricRoundTrip7Param() {
        Point original = new Point(4000000, 1000000, 5000000);
        double[] params = {100, -50, 75, 0.00001, -0.00002, 0.00001, 1.000001};

        Point toWgs84 = DatumUtils.geocentricToWgs84(original, Values.PJD_7PARAM, params);
        Point back = DatumUtils.geocentricFromWgs84(toWgs84, Values.PJD_7PARAM, params);

        assertNotNull(back);
        assertEquals(original.x, back.x, 0.01);
        assertEquals(original.y, back.y, 0.01);
        assertEquals(original.z, back.z, 0.01);
    }

    // ==================== DatumTransform Tests ====================

    @Test
    void testDatumTransformSameDatum() {
        DatumParams d1 = new DatumParams(
            Values.SRS_WGS84_SEMIMAJOR, Values.SRS_WGS84_SEMIMINOR,
            Values.SRS_WGS84_ESQUARED, 0.006739496742276434
        );
        DatumParams d2 = new DatumParams(
            Values.SRS_WGS84_SEMIMAJOR, Values.SRS_WGS84_SEMIMINOR,
            Values.SRS_WGS84_ESQUARED, 0.006739496742276434
        );

        Point p = new Point(Math.toRadians(10), Math.toRadians(45), 0);
        Point result = DatumTransform.transform(d1, d2, p);

        assertNotNull(result);
        assertEquals(p.x, result.x, TOLERANCE);
        assertEquals(p.y, result.y, TOLERANCE);
    }

    @Test
    void testDatumTransformNoDatum() {
        DatumParams d1 = new DatumParams("none", null, 6378137, 6356752.314, 
            Values.SRS_WGS84_ESQUARED, 0, null);
        DatumParams d2 = new DatumParams(
            Values.SRS_WGS84_SEMIMAJOR, Values.SRS_WGS84_SEMIMINOR,
            Values.SRS_WGS84_ESQUARED, 0.006739496742276434
        );

        Point p = new Point(Math.toRadians(10), Math.toRadians(45), 0);
        Point result = DatumTransform.transform(d1, d2, p);

        assertNotNull(result);
        assertEquals(p.x, result.x, TOLERANCE);
        assertEquals(p.y, result.y, TOLERANCE);
    }

    @Test
    void testDatumTransformNullDatum() {
        Point p = new Point(Math.toRadians(10), Math.toRadians(45), 0);
        Point result = DatumTransform.transform(null, null, p);

        assertNotNull(result);
        assertEquals(p.x, result.x, TOLERANCE);
        assertEquals(p.y, result.y, TOLERANCE);
    }

    @Test
    void testDatumTransform3Param() {
        DatumParams source = new DatumParams(
            "test", new double[]{-87, -98, -121},
            6378388.0, 6356911.946,
            0.00672267, 0.00676817,
            null
        );

        DatumParams dest = new DatumParams(
            Values.SRS_WGS84_SEMIMAJOR, Values.SRS_WGS84_SEMIMINOR,
            Values.SRS_WGS84_ESQUARED, 0.006739496742276434
        );

        Point p = new Point(Math.toRadians(2.337229), Math.toRadians(48.856614), 0);

        Point result = DatumTransform.transform(source, dest, p);

        assertNotNull(result);
        assertNotEquals(p.x, result.x, 1e-6);
    }

    // ==================== Integration Tests with Proj4 ====================

    @Test
    void testWGS84ToWGS84Identity() {
        Converter conv = Proj4.proj4(
            "+proj=longlat +datum=WGS84",
            "+proj=longlat +datum=WGS84"
        );

        Point p = new Point(-122.4194, 37.7749);
        Point result = conv.forward(p);

        assertNotNull(result);
        assertEquals(p.x, result.x, DEGREE_TOLERANCE);
        assertEquals(p.y, result.y, DEGREE_TOLERANCE);
    }

    @Test
    void testWGS84ToMercatorAndBack() {
        Converter conv = Proj4.proj4(
            "+proj=longlat +datum=WGS84",
            "+proj=merc +datum=WGS84"
        );

        Point original = new Point(-74.006, 40.7128);
        Point projected = conv.forward(original);
        Point restored = conv.inverse(projected);

        assertNotNull(restored);
        assertEquals(original.x, restored.x, DEGREE_TOLERANCE);
        assertEquals(original.y, restored.y, DEGREE_TOLERANCE);
    }

    @Test
    void testDifferentEllipsoidsViaTransform() {
        Converter conv = Proj4.proj4(
            "+proj=longlat +ellps=WGS84",
            "+proj=longlat +ellps=GRS80"
        );

        Point p = new Point(10, 50);
        Point result = conv.forward(p);

        assertNotNull(result);
        assertEquals(p.x, result.x, DEGREE_TOLERANCE);
        assertEquals(p.y, result.y, DEGREE_TOLERANCE);
    }

    @Test
    void testExplicitTowgs84Params() {
        Converter conv = Proj4.proj4(
            "+proj=longlat +ellps=intl +towgs84=-87,-98,-121",
            "+proj=longlat +datum=WGS84"
        );

        Point p = new Point(2.337229, 48.856614);
        Point result = conv.forward(p);

        assertNotNull(result);
        assertTrue(Math.abs(result.x - p.x) < 0.01);
        assertTrue(Math.abs(result.y - p.y) < 0.01);
    }

    // ==================== Edge Cases ====================

    @Test
    void testGeodeticToGeocentricLatitudeOutOfRange() {
        Point p = new Point(0, 2 * Values.HALF_PI, 0);
        Point result = DatumUtils.geodeticToGeocentric(p, Values.SRS_WGS84_ESQUARED, Values.SRS_WGS84_SEMIMAJOR);

        assertNotNull(result);
        assertTrue(Double.isInfinite(result.x) || Double.isInfinite(result.y));
    }

    @Test
    void testGeodeticToGeocentricSlightlyOutOfRange() {
        Point p = new Point(0, Values.HALF_PI * 1.0005, 0);
        Point result = DatumUtils.geodeticToGeocentric(p, Values.SRS_WGS84_ESQUARED, Values.SRS_WGS84_SEMIMAJOR);

        assertNotNull(result);
        assertEquals(0, result.x, METER_TOLERANCE);
        assertEquals(0, result.y, METER_TOLERANCE);
        assertTrue(result.z > 6350000);
    }

    @Test
    void testGeocentricToGeodeticAtCenter() {
        Point p = new Point(0, 0, 0);
        Point result = DatumUtils.geocentricToGeodetic(p, Values.SRS_WGS84_ESQUARED,
            Values.SRS_WGS84_SEMIMAJOR, Values.SRS_WGS84_SEMIMINOR);

        assertNotNull(result);
    }

    @Test
    void testDatumTransformPreservesZ() {
        DatumParams d1 = new DatumParams(
            Values.SRS_WGS84_SEMIMAJOR, Values.SRS_WGS84_SEMIMINOR,
            Values.SRS_WGS84_ESQUARED, 0.006739496742276434
        );

        Point p = new Point(Math.toRadians(10), Math.toRadians(45), 1000);
        Point result = DatumTransform.transform(d1, d1, p);

        assertNotNull(result);
        assertEquals(1000, result.z, METER_TOLERANCE);
    }

    @Test
    void testMultipleDatumTransforms() {
        Converter conv1 = Proj4.proj4(
            "+proj=longlat +datum=WGS84",
            "+proj=merc +datum=WGS84"
        );
        Converter conv2 = Proj4.proj4(
            "+proj=merc +datum=WGS84",
            "+proj=longlat +datum=WGS84"
        );

        Point original = new Point(-122.4, 37.8);
        Point projected = conv1.forward(original);
        Point back = conv2.forward(projected);

        assertEquals(original.x, back.x, DEGREE_TOLERANCE);
        assertEquals(original.y, back.y, DEGREE_TOLERANCE);
    }
}
