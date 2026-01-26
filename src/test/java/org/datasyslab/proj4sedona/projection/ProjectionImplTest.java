package org.datasyslab.proj4sedona.projection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.datasyslab.proj4sedona.common.ProjMath;
import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.core.Proj;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Phase 5: First Projections (LongLat, Mercator).
 */
class ProjectionImplTest {

    private static final double DELTA = 1e-6;
    private static final double DELTA_METERS = 0.01; // 1cm accuracy for meters

    @BeforeEach
    void setUp() {
        ProjectionRegistry.reset();
        ProjectionRegistry.start();
    }

    // ========== ProjMath Tests ==========

    @Test
    void testSign() {
        assertEquals(-1, ProjMath.sign(-5), DELTA);
        assertEquals(1, ProjMath.sign(5), DELTA);
        assertEquals(1, ProjMath.sign(0), DELTA);
    }

    @Test
    void testAdjustLon() {
        // Within range - no change
        assertEquals(1.0, ProjMath.adjustLon(1.0), DELTA);
        
        // Greater than PI - should wrap
        double lon = Math.PI + 0.5;
        double adjusted = ProjMath.adjustLon(lon);
        assertTrue(Math.abs(adjusted) <= Math.PI);
        
        // Skip adjust when over=true
        assertEquals(lon, ProjMath.adjustLon(lon, true), DELTA);
    }

    @Test
    void testMsfnz() {
        // Test with WGS84 eccentricity at equator
        double e = 0.0818191908426215;
        double result = ProjMath.msfnz(e, 0, 1);
        assertEquals(1.0, result, DELTA);
        
        // Test at 45 degrees
        double sinphi = Math.sin(Math.PI / 4);
        double cosphi = Math.cos(Math.PI / 4);
        result = ProjMath.msfnz(e, sinphi, cosphi);
        assertTrue(result > 0 && result < 1);
    }

    @Test
    void testTsfnz() {
        double e = 0.0818191908426215;
        double phi = Math.PI / 4; // 45 degrees
        double sinphi = Math.sin(phi);
        double result = ProjMath.tsfnz(e, phi, sinphi);
        assertTrue(result > 0);
    }

    @Test
    void testPhi2z() {
        double e = 0.0818191908426215;
        double ts = 0.5;
        double phi = ProjMath.phi2z(e, ts);
        assertNotEquals(-9999, phi);
        assertTrue(Math.abs(phi) < Values.HALF_PI);
    }

    @Test
    void testPhi2zNoConvergence() {
        // Test with extreme values that should cause no convergence
        // Very large eccentricity and ts that would cause iteration to fail
        double result = ProjMath.phi2z(0.99, 1e100);
        // Should either converge or return -9999
        assertTrue(result == -9999 || Math.abs(result) <= Values.HALF_PI);
    }

    @Test
    void testHyperbolicFunctions() {
        // Test that our functions match Java's native implementations
        double x = 1.5;
        assertEquals(Math.sinh(x), ProjMath.sinh(x), DELTA);
        assertEquals(Math.cosh(x), ProjMath.cosh(x), DELTA);
        assertEquals(Math.tanh(x), ProjMath.tanh(x), DELTA);
    }

    @Test
    void testAsinz() {
        // Normal case
        assertEquals(Math.asin(0.5), ProjMath.asinz(0.5), DELTA);
        
        // Edge case: value slightly > 1 should be clamped
        assertEquals(Math.PI / 2, ProjMath.asinz(1.0001), DELTA);
        
        // Edge case: value slightly < -1 should be clamped
        assertEquals(-Math.PI / 2, ProjMath.asinz(-1.0001), DELTA);
    }

    // ========== LongLat Projection Tests ==========

    @Test
    void testLongLatIdentity() {
        LongLat proj = new LongLat();
        ProjectionParams params = new ProjectionParams();
        proj.init(params);

        Point p = new Point(1.5, 0.5);
        
        Point forward = proj.forward(p.copy());
        assertEquals(1.5, forward.x, DELTA);
        assertEquals(0.5, forward.y, DELTA);
        
        Point inverse = proj.inverse(forward.copy());
        assertEquals(1.5, inverse.x, DELTA);
        assertEquals(0.5, inverse.y, DELTA);
    }

    @Test
    void testLongLatRegistry() {
        assertNotNull(ProjectionRegistry.get("longlat"));
        assertNotNull(ProjectionRegistry.get("identity"));
        assertNotNull(ProjectionRegistry.get("LONGLAT"));
    }

    // ========== Mercator Projection Tests ==========

    @Test
    void testMercatorForwardInverse() {
        Mercator proj = new Mercator();
        ProjectionParams params = createWgs84Params();
        proj.init(params);

        // Test point: London (approx 0 lon, 51.5 lat)
        double lon = 0;
        double lat = 51.5 * Values.D2R;
        Point p = new Point(lon, lat);

        Point forward = proj.forward(p.copy());
        assertNotNull(forward);
        assertEquals(0, forward.x, 1.0); // Should be near 0 easting
        assertTrue(forward.y > 0); // Should be positive northing

        Point inverse = proj.inverse(forward.copy());
        assertNotNull(inverse);
        assertEquals(lon, inverse.x, DELTA);
        assertEquals(lat, inverse.y, DELTA);
    }

    @Test
    void testMercatorEquator() {
        Mercator proj = new Mercator();
        ProjectionParams params = createWgs84Params();
        proj.init(params);

        // Test at equator
        Point p = new Point(0, 0);
        Point forward = proj.forward(p.copy());
        assertNotNull(forward);
        assertEquals(0, forward.x, DELTA);
        assertEquals(0, forward.y, DELTA);

        Point inverse = proj.inverse(forward.copy());
        assertEquals(0, inverse.x, DELTA);
        assertEquals(0, inverse.y, DELTA);
    }

    @Test
    void testMercatorPoleReturnsNull() {
        Mercator proj = new Mercator();
        ProjectionParams params = createWgs84Params();
        proj.init(params);

        // At the pole - should return null
        Point p = new Point(0, Values.HALF_PI);
        Point forward = proj.forward(p.copy());
        assertNull(forward);
    }

    @Test
    void testMercatorNearPole() {
        Mercator proj = new Mercator();
        ProjectionParams params = createWgs84Params();
        proj.init(params);

        // Near the pole (89.9 degrees) - should work
        double lat = 89.9 * Values.D2R;
        Point p = new Point(0, lat);
        Point forward = proj.forward(p.copy());
        assertNotNull(forward);
        assertTrue(forward.y > 0);

        Point inverse = proj.inverse(forward.copy());
        assertNotNull(inverse);
        assertEquals(0, inverse.x, DELTA);
        assertEquals(lat, inverse.y, DELTA);
    }

    @Test
    void testMercatorWithFalseEasting() {
        Mercator proj = new Mercator();
        ProjectionParams params = createWgs84Params();
        params.x0 = 500000; // False easting
        proj.init(params);

        Point p = new Point(0, 0);
        Point forward = proj.forward(p.copy());
        assertNotNull(forward);
        assertEquals(500000, forward.x, DELTA);
        assertEquals(0, forward.y, DELTA);
    }

    @Test
    void testMercatorSpherical() {
        Mercator proj = new Mercator();
        ProjectionParams params = createSphericalParams();
        proj.init(params);

        Point p = new Point(0.1, 0.5);
        Point forward = proj.forward(p.copy());
        assertNotNull(forward);

        Point inverse = proj.inverse(forward.copy());
        assertNotNull(inverse);
        assertEquals(0.1, inverse.x, DELTA);
        assertEquals(0.5, inverse.y, DELTA);
    }

    @Test
    void testMercatorRegistry() {
        assertNotNull(ProjectionRegistry.get("merc"));
        assertNotNull(ProjectionRegistry.get("Mercator"));
        assertNotNull(ProjectionRegistry.get("mercator_1sp"));
    }

    // ========== Negative Coordinates Tests (Southern/Western Hemispheres) ==========

    @Test
    void testMercatorSouthernHemisphere() {
        Mercator proj = new Mercator();
        ProjectionParams params = createWgs84Params();
        proj.init(params);

        // Sydney, Australia (approx 151E, 34S)
        double lon = 151 * Values.D2R;
        double lat = -34 * Values.D2R;
        Point p = new Point(lon, lat);

        Point forward = proj.forward(p.copy());
        assertNotNull(forward);
        assertTrue(forward.x > 0);  // Eastern hemisphere
        assertTrue(forward.y < 0);  // Southern hemisphere

        Point inverse = proj.inverse(forward.copy());
        assertNotNull(inverse);
        assertEquals(lon, inverse.x, DELTA);
        assertEquals(lat, inverse.y, DELTA);
    }

    @Test
    void testMercatorWesternHemisphere() {
        Mercator proj = new Mercator();
        ProjectionParams params = createWgs84Params();
        proj.init(params);

        // New York (approx 74W, 41N)
        double lon = -74 * Values.D2R;
        double lat = 41 * Values.D2R;
        Point p = new Point(lon, lat);

        Point forward = proj.forward(p.copy());
        assertNotNull(forward);
        assertTrue(forward.x < 0);  // Western hemisphere
        assertTrue(forward.y > 0);  // Northern hemisphere

        Point inverse = proj.inverse(forward.copy());
        assertNotNull(inverse);
        assertEquals(lon, inverse.x, DELTA);
        assertEquals(lat, inverse.y, DELTA);
    }

    @Test
    void testMercatorSouthWestQuadrant() {
        Mercator proj = new Mercator();
        ProjectionParams params = createWgs84Params();
        proj.init(params);

        // Buenos Aires, Argentina (approx 58W, 34S)
        double lon = -58 * Values.D2R;
        double lat = -34 * Values.D2R;
        Point p = new Point(lon, lat);

        Point forward = proj.forward(p.copy());
        assertNotNull(forward);
        assertTrue(forward.x < 0);  // Western
        assertTrue(forward.y < 0);  // Southern

        Point inverse = proj.inverse(forward.copy());
        assertNotNull(inverse);
        assertEquals(lon, inverse.x, DELTA);
        assertEquals(lat, inverse.y, DELTA);
    }

    // ========== Large Coordinate Stress Tests ==========

    @Test
    void testMercatorLargeCoordinates() {
        Mercator proj = new Mercator();
        ProjectionParams params = createWgs84Params();
        proj.init(params);

        // Test at extreme but valid latitude (85 degrees - typical Web Mercator limit)
        double lat = 85 * Values.D2R;
        Point p = new Point(0, lat);

        Point forward = proj.forward(p.copy());
        assertNotNull(forward);
        assertTrue(Double.isFinite(forward.y));

        Point inverse = proj.inverse(forward.copy());
        assertNotNull(inverse);
        assertEquals(lat, inverse.y, DELTA);
    }

    @Test
    void testMercatorDatelineCrossing() {
        Mercator proj = new Mercator();
        ProjectionParams params = createWgs84Params();
        proj.init(params);

        // Test at 180 degrees longitude
        double lon = Math.PI;  // 180 degrees
        double lat = 45 * Values.D2R;
        Point p = new Point(lon, lat);

        Point forward = proj.forward(p.copy());
        assertNotNull(forward);

        Point inverse = proj.inverse(forward.copy());
        assertNotNull(inverse);
        // Longitude might wrap, so check absolute difference
        double lonDiff = Math.abs(lon - inverse.x);
        assertTrue(lonDiff < DELTA || Math.abs(lonDiff - 2 * Math.PI) < DELTA);
        assertEquals(lat, inverse.y, DELTA);
    }

    // ========== Web Mercator / EPSG:3857 Accuracy Tests ==========

    @Test
    void testWebMercatorKnownValues() {
        // Web Mercator (EPSG:3857) uses a sphere with a = 6378137
        String projStr = "+proj=merc +a=6378137 +b=6378137 +lat_ts=0 +lon_0=0 +x_0=0 +y_0=0 +k=1 +units=m";
        Proj proj = new Proj(projStr);

        // Test with known EPSG:3857 values
        // (0, 0) -> (0, 0)
        Point p1 = proj.forward(new Point(0, 0));
        assertEquals(0, p1.x, DELTA_METERS);
        assertEquals(0, p1.y, DELTA_METERS);

        // (10, 0) degrees -> (1113194.9, 0) approximately
        Point p2 = proj.forward(new Point(10 * Values.D2R, 0));
        assertEquals(1113194.9, p2.x, 1.0); // 1 meter tolerance

        // (0, 45) degrees -> (0, 5621521.5) approximately
        Point p3 = proj.forward(new Point(0, 45 * Values.D2R));
        assertEquals(0, p3.x, DELTA_METERS);
        assertEquals(5621521.5, p3.y, 100.0); // 100 meter tolerance for this approximation
    }

    @Test
    void testWebMercatorRoundTrip() {
        String projStr = "+proj=merc +a=6378137 +b=6378137 +lat_ts=0 +lon_0=0 +x_0=0 +y_0=0 +k=1 +units=m";
        Proj proj = new Proj(projStr);

        // Test multiple points across the globe
        double[][] testCoords = {
            {0, 0},           // Null Island
            {-122.4, 37.8},   // San Francisco
            {139.7, 35.7},    // Tokyo
            {-43.2, -22.9},   // Rio de Janeiro
            {18.4, -33.9},    // Cape Town
        };

        for (double[] coord : testCoords) {
            double lon = coord[0] * Values.D2R;
            double lat = coord[1] * Values.D2R;

            Point forward = proj.forward(new Point(lon, lat));
            assertNotNull(forward, "Forward failed for " + coord[0] + ", " + coord[1]);

            Point inverse = proj.inverse(forward.copy());
            assertNotNull(inverse, "Inverse failed for " + coord[0] + ", " + coord[1]);

            assertEquals(lon, inverse.x, DELTA, "Lon mismatch for " + coord[0]);
            assertEquals(lat, inverse.y, DELTA, "Lat mismatch for " + coord[1]);
        }
    }

    // ========== Integration Tests with Proj ==========

    @Test
    void testProjLonglat() {
        Proj proj = new Proj("+proj=longlat +datum=WGS84");
        
        Point p = new Point(1.0, 0.5);
        Point forward = proj.forward(p.copy());
        assertEquals(1.0, forward.x, DELTA);
        assertEquals(0.5, forward.y, DELTA);
    }

    @Test
    void testProjMercator() {
        Proj proj = new Proj("+proj=merc +datum=WGS84");
        
        double lon = 0;
        double lat = 45 * Values.D2R;
        Point p = new Point(lon, lat);
        
        Point forward = proj.forward(p.copy());
        assertNotNull(forward);
        
        Point inverse = proj.inverse(forward.copy());
        assertNotNull(inverse);
        assertEquals(lon, inverse.x, DELTA);
        assertEquals(lat, inverse.y, DELTA);
    }

    @Test
    void testProjMercatorWithParams() {
        Proj proj = new Proj("+proj=merc +lon_0=-96 +x_0=0 +y_0=0 +datum=WGS84");
        
        // Point at central meridian
        double lon = -96 * Values.D2R;
        double lat = 30 * Values.D2R;
        Point p = new Point(lon, lat);
        
        Point forward = proj.forward(p.copy());
        assertNotNull(forward);
        assertEquals(0, forward.x, 1.0); // Should be near 0 at central meridian
        
        Point inverse = proj.inverse(forward.copy());
        assertEquals(lon, inverse.x, DELTA);
        assertEquals(lat, inverse.y, DELTA);
    }

    @Test
    void testEpsg3857StyleProjection() {
        // Web Mercator / EPSG:3857 style
        String projStr = "+proj=merc +a=6378137 +b=6378137 +lat_ts=0 +lon_0=0 +x_0=0 +y_0=0 +k=1 +units=m";
        Proj proj = new Proj(projStr);
        
        // Test with a known point (0, 0)
        Point p = new Point(0, 0);
        Point forward = proj.forward(p.copy());
        assertNotNull(forward);
        assertEquals(0, forward.x, DELTA);
        assertEquals(0, forward.y, DELTA);
    }

    // ========== Helper Methods ==========

    private ProjectionParams createWgs84Params() {
        ProjectionParams params = new ProjectionParams();
        params.a = 6378137.0;
        params.b = 6356752.314245179;
        params.es = 0.006694379990141316;
        params.e = Math.sqrt(params.es);
        params.ep2 = 0.006739496742276434;
        params.k0 = 1.0;
        params.x0 = 0;
        params.y0 = 0;
        params.long0 = 0.0;
        params.sphere = false;
        return params;
    }

    private ProjectionParams createSphericalParams() {
        ProjectionParams params = new ProjectionParams();
        params.a = 6370997.0;
        params.b = 6370997.0;
        params.es = 0;
        params.e = 0;
        params.ep2 = 0;
        params.k0 = 1.0;
        params.x0 = 0;
        params.y0 = 0;
        params.long0 = 0.0;
        params.sphere = true;
        return params;
    }
}
