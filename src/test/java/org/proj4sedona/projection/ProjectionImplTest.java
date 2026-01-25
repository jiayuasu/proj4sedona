package org.proj4sedona.projection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.proj4sedona.common.ProjMath;
import org.proj4sedona.constants.Values;
import org.proj4sedona.core.Point;
import org.proj4sedona.core.Proj;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Phase 5: First Projections (LongLat, Mercator).
 */
class ProjectionImplTest {

    private static final double DELTA = 1e-6;

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
