package org.proj4sedona.projection;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.proj4sedona.Proj4;
import org.proj4sedona.core.Point;
import org.proj4sedona.transform.Converter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for all projection implementations (Phases 9, 10, 11).
 */
class AllProjectionsTest {

    private static final double DEGREE_TOLERANCE = 1e-6;
    private static final double METER_TOLERANCE = 0.1;

    @BeforeAll
    static void setup() {
        ProjectionRegistry.start();
    }

    // ==================== Conic Projections (Phase 9) ====================

    @Test
    void testLCCRoundTrip() {
        Converter conv = Proj4.proj4(
            "+proj=longlat +datum=WGS84",
            "+proj=lcc +lat_1=33 +lat_2=45 +lat_0=39 +lon_0=-96 +x_0=0 +y_0=0 +datum=WGS84"
        );
        Point original = new Point(-100, 40);
        Point projected = conv.forward(original);
        Point restored = conv.inverse(projected);
        assertEquals(original.x, restored.x, DEGREE_TOLERANCE);
        assertEquals(original.y, restored.y, DEGREE_TOLERANCE);
    }

    @Test
    void testLCCRegisteredNames() {
        assertNotNull(ProjectionRegistry.get("lcc"));
        assertNotNull(ProjectionRegistry.get("Lambert_Conformal_Conic"));
        assertNotNull(ProjectionRegistry.get("Lambert_Conformal_Conic_2SP"));
    }

    @Test
    void testAlbersRoundTrip() {
        Converter conv = Proj4.proj4(
            "+proj=longlat +datum=WGS84",
            "+proj=aea +lat_1=29.5 +lat_2=45.5 +lat_0=23 +lon_0=-96 +x_0=0 +y_0=0 +datum=WGS84"
        );
        Point original = new Point(-100, 40);
        Point projected = conv.forward(original);
        Point restored = conv.inverse(projected);
        assertEquals(original.x, restored.x, DEGREE_TOLERANCE);
        assertEquals(original.y, restored.y, DEGREE_TOLERANCE);
    }

    @Test
    void testAlbersRegisteredNames() {
        assertNotNull(ProjectionRegistry.get("aea"));
        assertNotNull(ProjectionRegistry.get("Albers"));
        assertNotNull(ProjectionRegistry.get("Albers_Equal_Area"));
    }

    @Test
    void testEQDCRoundTrip() {
        Converter conv = Proj4.proj4(
            "+proj=longlat +datum=WGS84",
            "+proj=eqdc +lat_1=33 +lat_2=45 +lat_0=39 +lon_0=-96 +x_0=0 +y_0=0 +datum=WGS84"
        );
        Point original = new Point(-100, 40);
        Point projected = conv.forward(original);
        Point restored = conv.inverse(projected);
        assertEquals(original.x, restored.x, DEGREE_TOLERANCE);
        assertEquals(original.y, restored.y, DEGREE_TOLERANCE);
    }

    // ==================== Azimuthal Projections (Phase 10) ====================

    @Test
    void testStereographicPolarRoundTrip() {
        Converter conv = Proj4.proj4(
            "+proj=longlat +datum=WGS84",
            "+proj=stere +lat_0=90 +lat_ts=70 +lon_0=0 +k=1 +x_0=0 +y_0=0 +datum=WGS84"
        );
        Point original = new Point(10, 80);
        Point projected = conv.forward(original);
        Point restored = conv.inverse(projected);
        assertEquals(original.x, restored.x, DEGREE_TOLERANCE);
        assertEquals(original.y, restored.y, DEGREE_TOLERANCE);
    }

    @Test
    void testStereographicRegisteredNames() {
        assertNotNull(ProjectionRegistry.get("stere"));
        assertNotNull(ProjectionRegistry.get("Polar_Stereographic"));
    }

    @Test
    void testLAEARoundTrip() {
        Converter conv = Proj4.proj4(
            "+proj=longlat +datum=WGS84",
            "+proj=laea +lat_0=52 +lon_0=10 +x_0=4321000 +y_0=3210000 +datum=WGS84"
        );
        Point original = new Point(10, 52);
        Point projected = conv.forward(original);
        Point restored = conv.inverse(projected);
        assertEquals(original.x, restored.x, DEGREE_TOLERANCE);
        assertEquals(original.y, restored.y, DEGREE_TOLERANCE);
    }

    @Test
    void testLAEARegisteredNames() {
        assertNotNull(ProjectionRegistry.get("laea"));
        assertNotNull(ProjectionRegistry.get("Lambert_Azimuthal_Equal_Area"));
    }

    @Test
    void testAEQDRoundTrip() {
        Converter conv = Proj4.proj4(
            "+proj=longlat +datum=WGS84",
            "+proj=aeqd +lat_0=52 +lon_0=10 +x_0=0 +y_0=0 +datum=WGS84"
        );
        Point original = new Point(11, 53);
        Point projected = conv.forward(original);
        Point restored = conv.inverse(projected);
        assertEquals(original.x, restored.x, DEGREE_TOLERANCE);
        assertEquals(original.y, restored.y, DEGREE_TOLERANCE);
    }

    @Test
    void testAEQDRegisteredNames() {
        assertNotNull(ProjectionRegistry.get("aeqd"));
        assertNotNull(ProjectionRegistry.get("Azimuthal_Equidistant"));
    }

    // ==================== Pseudocylindrical Projections (Phase 11) ====================

    @Test
    void testSinusoidalRoundTrip() {
        Converter conv = Proj4.proj4(
            "+proj=longlat +datum=WGS84",
            "+proj=sinu +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84"
        );
        Point original = new Point(10, 45);
        Point projected = conv.forward(original);
        Point restored = conv.inverse(projected);
        assertEquals(original.x, restored.x, DEGREE_TOLERANCE);
        assertEquals(original.y, restored.y, DEGREE_TOLERANCE);
    }

    @Test
    void testSinusoidalRegisteredNames() {
        assertNotNull(ProjectionRegistry.get("sinu"));
        assertNotNull(ProjectionRegistry.get("Sinusoidal"));
    }

    @Test
    void testMollweideRoundTrip() {
        Converter conv = Proj4.proj4(
            "+proj=longlat +datum=WGS84",
            "+proj=moll +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84"
        );
        Point original = new Point(10, 45);
        Point projected = conv.forward(original);
        Point restored = conv.inverse(projected);
        assertEquals(original.x, restored.x, DEGREE_TOLERANCE);
        assertEquals(original.y, restored.y, DEGREE_TOLERANCE);
    }

    @Test
    void testMollweideRegisteredNames() {
        assertNotNull(ProjectionRegistry.get("moll"));
        assertNotNull(ProjectionRegistry.get("Mollweide"));
    }

    @Test
    void testRobinsonRoundTrip() {
        Converter conv = Proj4.proj4(
            "+proj=longlat +datum=WGS84",
            "+proj=robin +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84"
        );
        Point original = new Point(10, 45);
        Point projected = conv.forward(original);
        Point restored = conv.inverse(projected);
        assertEquals(original.x, restored.x, DEGREE_TOLERANCE);
        assertEquals(original.y, restored.y, DEGREE_TOLERANCE);
    }

    @Test
    void testRobinsonRegisteredNames() {
        assertNotNull(ProjectionRegistry.get("robin"));
        assertNotNull(ProjectionRegistry.get("Robinson"));
    }

    @Test
    void testEQCRoundTrip() {
        Converter conv = Proj4.proj4(
            "+proj=longlat +datum=WGS84",
            "+proj=eqc +lat_ts=0 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84"
        );
        Point original = new Point(10, 45);
        Point projected = conv.forward(original);
        Point restored = conv.inverse(projected);
        assertEquals(original.x, restored.x, DEGREE_TOLERANCE);
        assertEquals(original.y, restored.y, DEGREE_TOLERANCE);
    }

    @Test
    void testCEARoundTrip() {
        Converter conv = Proj4.proj4(
            "+proj=longlat +datum=WGS84",
            "+proj=cea +lat_ts=0 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84"
        );
        Point original = new Point(10, 45);
        Point projected = conv.forward(original);
        Point restored = conv.inverse(projected);
        assertEquals(original.x, restored.x, DEGREE_TOLERANCE);
        assertEquals(original.y, restored.y, DEGREE_TOLERANCE);
    }

    // ==================== Edge Cases ====================

    @Test
    void testLCCAtEquator() {
        Converter conv = Proj4.proj4(
            "+proj=longlat +datum=WGS84",
            "+proj=lcc +lat_1=20 +lat_2=60 +lat_0=40 +lon_0=0 +datum=WGS84"
        );
        Point original = new Point(0, 0);
        Point projected = conv.forward(original);
        assertTrue(Double.isFinite(projected.x));
        assertTrue(Double.isFinite(projected.y));
    }

    @Test
    void testLAEAAtPole() {
        Converter conv = Proj4.proj4(
            "+proj=longlat +datum=WGS84",
            "+proj=laea +lat_0=90 +lon_0=0 +datum=WGS84"
        );
        Point original = new Point(0, 85);
        Point projected = conv.forward(original);
        Point restored = conv.inverse(projected);
        assertEquals(original.x, restored.x, DEGREE_TOLERANCE);
        assertEquals(original.y, restored.y, DEGREE_TOLERANCE);
    }

    @Test
    void testMollweideAtPole() {
        Converter conv = Proj4.proj4(
            "+proj=longlat +datum=WGS84",
            "+proj=moll +lon_0=0 +datum=WGS84"
        );
        Point original = new Point(0, 89);
        Point projected = conv.forward(original);
        Point restored = conv.inverse(projected);
        assertEquals(original.x, restored.x, DEGREE_TOLERANCE);
        assertEquals(original.y, restored.y, DEGREE_TOLERANCE);
    }

    @Test
    void testRobinsonAtDateLine() {
        Converter conv = Proj4.proj4(
            "+proj=longlat +datum=WGS84",
            "+proj=robin +lon_0=0 +datum=WGS84"
        );
        Point original = new Point(179, 45);
        Point projected = conv.forward(original);
        Point restored = conv.inverse(projected);
        assertEquals(original.x, restored.x, DEGREE_TOLERANCE);
        assertEquals(original.y, restored.y, DEGREE_TOLERANCE);
    }

    @Test
    void testMultipleProjectionsInSequence() {
        // WGS84 -> LCC -> UTM -> WGS84
        Converter toLcc = Proj4.proj4(
            "+proj=longlat +datum=WGS84",
            "+proj=lcc +lat_1=33 +lat_2=45 +lat_0=39 +lon_0=-96 +datum=WGS84"
        );
        Converter lccToUtm = Proj4.proj4(
            "+proj=lcc +lat_1=33 +lat_2=45 +lat_0=39 +lon_0=-96 +datum=WGS84",
            "+proj=utm +zone=14 +datum=WGS84"
        );
        Converter utmToWgs = Proj4.proj4(
            "+proj=utm +zone=14 +datum=WGS84",
            "+proj=longlat +datum=WGS84"
        );

        Point original = new Point(-100, 40);
        Point lcc = toLcc.forward(original);
        Point utm = lccToUtm.forward(lcc);
        Point restored = utmToWgs.forward(utm);

        assertEquals(original.x, restored.x, DEGREE_TOLERANCE * 10);
        assertEquals(original.y, restored.y, DEGREE_TOLERANCE * 10);
    }
}
