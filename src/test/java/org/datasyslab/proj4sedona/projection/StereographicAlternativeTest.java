package org.datasyslab.proj4sedona.projection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.datasyslab.proj4sedona.Proj4;
import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.transform.Converter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Oblique Stereographic Alternative / Double Stereographic projection (sterea).
 *
 * <p>This is the EPSG method 9809 ("Oblique Stereographic") algorithm, which goes
 * through the Gauss conformal sphere — distinct from the Snyder {@link Stereographic}.
 * The two reference cases are ported from proj4js {@code test/testData.js}
 * (EPSG:2036 New Brunswick Stereographic, EPSG:28992 Amersfoort / RD New); proj4js
 * drives them WGS84 &rarr; CRS (forward) and CRS &rarr; WGS84 (inverse). Tolerance
 * is {@code 10^-acc} (default xy acc = 2 &rarr; 0.01 m, ll acc = 6 &rarr; 1e-6&deg;).</p>
 *
 * <p>These are <em>known-value</em> checks, not round-trips: until this projection
 * was split out, {@code sterea} was aliased to the Snyder stereographic, which
 * round-trips cleanly but yields coordinates off by centimetres and more — so only
 * an absolute comparison against proj4js catches the regression.</p>
 */
class StereographicAlternativeTest {

    private static final double XY_EPSLN = Math.pow(10, -2); // 0.01 m
    private static final double LL_EPSLN = Math.pow(10, -6); // 1e-6 deg

    @BeforeEach
    void setUp() {
        ProjectionRegistry.reset();
        ProjectionRegistry.start();
    }

    @Test
    void testRegistry() {
        assertNotNull(ProjectionRegistry.get("sterea"));
        assertNotNull(ProjectionRegistry.get("Oblique_Stereographic"));
        assertNotNull(ProjectionRegistry.get("Double_Stereographic"));
        assertNotNull(ProjectionRegistry.get("Stereographic_North_Pole"));
        // Snyder stereographic names stay on Stereographic
        assertNotNull(ProjectionRegistry.get("stere"));
        assertNotNull(ProjectionRegistry.get("Stereographic_South_Pole"));
    }

    @Test
    void testEpsg28992RdNew() {
        // Amersfoort / RD New (Netherlands). proj4js: ll=[5.2, 52.25] -> xy=[142216.10, 473567.13]
        assertForwardInverse(
            "+proj=sterea +lat_0=52.15616055555555 +lon_0=5.38763888888889 +k=0.9999079 "
                + "+x_0=155000 +y_0=463000 +ellps=bessel "
                + "+towgs84=565.417,50.3319,465.552,-0.398957,0.343988,-1.8774,4.0725 "
                + "+units=m +no_defs",
            5.2, 52.25, 142216.10, 473567.13);
    }

    @Test
    void testEpsg2036NewBrunswick() {
        // NAD83(CSRS98) / New Brunswick Stereographic.
        // proj4js: ll=[-66.415, 46.34] -> xy=[2506543.370459, 7482219.546176]
        assertForwardInverse(
            "+proj=sterea +lat_0=46.5 +lon_0=-66.5 +k=0.999912 +x_0=2500000 +y_0=7500000 "
                + "+ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs",
            -66.415, 46.34, 2506543.370459, 7482219.546176);
    }

    /** Mirrors the proj4js harness: forward = WGS84 -> CRS, inverse = CRS -> WGS84. */
    private void assertForwardInverse(String def, double lon, double lat, double east, double north) {
        Converter toCrs = Proj4.proj4("+proj=longlat +datum=WGS84 +no_defs", def);

        Point xy = toCrs.forward(new Point(lon, lat));
        assertEquals(east, xy.x, XY_EPSLN, "easting");
        assertEquals(north, xy.y, XY_EPSLN, "northing");

        Point ll = toCrs.inverse(new Point(east, north));
        assertEquals(lon, ll.x, LL_EPSLN, "lng");
        assertEquals(lat, ll.y, LL_EPSLN, "lat");
    }
}
