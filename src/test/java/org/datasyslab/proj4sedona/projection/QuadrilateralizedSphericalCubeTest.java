package org.datasyslab.proj4sedona.projection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.datasyslab.proj4sedona.Proj4;
import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.core.Proj;
import org.datasyslab.proj4sedona.parser.CRSSerializer;
import org.datasyslab.proj4sedona.transform.Converter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Quadrilateralized Spherical Cube projection (qsc).
 *
 * <p>The face cases (front/right/back/left/top/bottom, WGS84) and the Mars
 * ellipsoid cases are ported from proj4js {@code test/testData.js} with the same
 * reference coordinates. Tolerance 0.01 m / 1e-7&deg;.</p>
 */
class QuadrilateralizedSphericalCubeTest {

    private static final double XY_EPSLN = 0.01;
    private static final double LL_EPSLN = 1e-7;
    private static final String WGS84 = "+proj=longlat +datum=WGS84 +no_defs";

    @BeforeEach
    void setUp() {
        ProjectionRegistry.reset();
        ProjectionRegistry.start();
    }

    @Test
    void testRegistry() {
        assertNotNull(ProjectionRegistry.get("qsc"));
        assertNotNull(ProjectionRegistry.get("Quadrilateralized_Spherical_Cube"));
        assertNotNull(ProjectionRegistry.get("Quadrilateralized Spherical Cube"));
    }

    @Test
    void testAllSixFaces() {
        // Same test point (2, 1) through each cube face — from proj4js testData.
        Object[][] cases = {
            {"+proj=qsc +lat_0=0 +lon_0=0 +units=m +datum=WGS84",    304638.4508447283, 164123.8709293560},
            {"+proj=qsc +lat_0=0 +lon_0=90 +units=m +datum=WGS84",   -11576764.4717786349, 224687.8649776891},
            {"+proj=qsc +lat_0=0 +lon_0=180 +units=m +datum=WGS84",  -15631296.4526007362, 8421356.1168374438},
            {"+proj=qsc +lat_0=0 +lon_0=-90 +units=m +datum=WGS84",  11988027.5987015367, 232669.8736086514},
            {"+proj=qsc +lat_0=90 +lon_0=0 +units=m +datum=WGS84",   456180.4073964519, -11678366.5914389268},
            {"+proj=qsc +lat_0=-90 +lon_0=0 +units=m +datum=WGS84",  464158.3228444085, 11882603.8180405404},
        };
        for (Object[] c : cases) {
            Converter conv = Proj4.proj4(WGS84, (String) c[0] + " +no_defs");
            Point xy = conv.forward(new Point(2, 1));
            assertEquals((double) c[1], xy.x, XY_EPSLN, "easting for " + c[0]);
            assertEquals((double) c[2], xy.y, XY_EPSLN, "northing for " + c[0]);
        }
    }

    @Test
    void testMarsEllipsoid() {
        // Non-Earth ellipsoid (Mars), exercising the [LK12] ellipsoid<->sphere shift.
        Object[][] cases = {
            {"+proj=qsc +units=m +a=3396190 +b=3376200 +lat_0=0 +lon_0=0",  162139.9347801624, 86935.6184961362},
            {"+proj=qsc +units=m +a=3396190 +b=3376200 +lat_0=0 +lon_0=90", -6164327.7345527401, 119033.1141843863},
        };
        for (Object[] c : cases) {
            // Same-ellipsoid source: pure projection math.
            Converter conv = Proj4.proj4("+proj=longlat +a=3396190 +b=3376200 +no_defs",
                (String) c[0] + " +no_defs");
            Point xy = conv.forward(new Point(2, 1));
            assertEquals((double) c[1], xy.x, XY_EPSLN, "easting for " + c[0]);
            assertEquals((double) c[2], xy.y, XY_EPSLN, "northing for " + c[0]);
        }
    }

    @Test
    void testRoundTripAllFaces() {
        String[] defs = {
            "+proj=qsc +lat_0=0 +lon_0=0 +units=m +datum=WGS84 +no_defs",
            "+proj=qsc +lat_0=0 +lon_0=90 +units=m +datum=WGS84 +no_defs",
            "+proj=qsc +lat_0=0 +lon_0=180 +units=m +datum=WGS84 +no_defs",
            "+proj=qsc +lat_0=0 +lon_0=-90 +units=m +datum=WGS84 +no_defs",
            "+proj=qsc +lat_0=90 +lon_0=0 +units=m +datum=WGS84 +no_defs",
            "+proj=qsc +lat_0=-90 +lon_0=0 +units=m +datum=WGS84 +no_defs",
        };
        for (String def : defs) {
            Converter conv = Proj4.proj4(WGS84, def);
            for (double[] c : new double[][] {{2, 1}, {30, 20}, {-25, -15}}) {
                Point xy = conv.forward(new Point(c[0], c[1]));
                Point ll = conv.inverse(new Point(xy.x, xy.y));
                // The inverse does not wrap lam + lon_0 (faithful to proj4js, which
                // returns e.g. 335 for -25 via the back face); compare modulo 360.
                double lonDiff = Math.abs(ll.x - c[0]) % 360;
                lonDiff = Math.min(lonDiff, 360 - lonDiff);
                assertEquals(0, lonDiff, LL_EPSLN, "lng for " + c[0] + " via " + def);
                assertEquals(c[1], ll.y, LL_EPSLN, "lat for " + c[1] + " via " + def);
            }
        }
    }

    @Test
    void testSerializationRoundTrip() {
        Proj original = new Proj("+proj=qsc +lat_0=0 +lon_0=0 +units=m +datum=WGS84 +no_defs");
        double[] c = {2, 1};
        for (String serialized : new String[] {
                CRSSerializer.toProjString(original),
                CRSSerializer.toWkt1(original),
                CRSSerializer.toWkt2(original),
                CRSSerializer.toProjJson(original)}) {
            Proj reimported = new Proj(serialized);
            Point want = original.forward(new Point(c[0] * Math.PI / 180, c[1] * Math.PI / 180));
            Point got = reimported.forward(new Point(c[0] * Math.PI / 180, c[1] * Math.PI / 180));
            assertEquals(want.x, got.x, XY_EPSLN, "easting after re-import of " + serialized);
            assertEquals(want.y, got.y, XY_EPSLN, "northing after re-import of " + serialized);
        }
    }
}
