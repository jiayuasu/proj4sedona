package org.datasyslab.proj4sedona.projection;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.datasyslab.proj4sedona.Proj4;
import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.transform.Converter;

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
        assertNotNull(ProjectionRegistry.get("sterea"),
            "sterea should be registered as alias for Stereographic (issue #56)");
        assertNotNull(ProjectionRegistry.get("Polar_Stereographic"));
    }

    @Test
    void testStereaRoundTrip() {
        // Issue #56: +proj=sterea must be accepted since it is output by toProjString
        Converter conv = Proj4.proj4(
            "+proj=longlat +datum=WGS84",
            "+proj=sterea +lat_0=52.15616055555555 +lon_0=5.38763888888889 "
                + "+k=0.9999079 +x_0=155000 +y_0=463000 +ellps=bessel "
                + "+towgs84=565.417,50.3319,465.552,-0.398957,0.343988,-1.8774,4.0725 "
                + "+units=m +no_defs"
        );
        Point original = new Point(5.387638889, 52.156160556);
        Point projected = conv.forward(original);
        Point restored = conv.inverse(projected);
        assertEquals(original.x, restored.x, DEGREE_TOLERANCE);
        assertEquals(original.y, restored.y, DEGREE_TOLERANCE);
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
        assertEquals(
            "laea",
            ProjectionRegistry.resolveProjCode(
                "Lambert Azimuthal Equal Area (Spherical)"));
    }

    @Test
    void testSphericalLAEAMatchesProjAtBothPoles() {
        // pyproj 3.7.1 / PROJ 9.5.1, using EPSG:3408 and EPSG:3409
        // (sphere radius 6,371,228 metres).
        double[][] inputs = {
            {-45, 80}, {30, 75}, {-120, -80}, {40, -70}
        };
        double[][] northExpected = {
            {-785297.3883560896, -785297.3883560897},
            {831612.1306057743, -1440394.4623998066},
            {-10993297.990217209, 6346983.553933673},
            {8066257.80524404, -9612991.718190672}
        };
        double[][] southExpected = {
            {-8975990.22213199, 8975990.222131992},
            {6316721.261240939, 10940882.161719866},
            {-961788.9489063293, -555289.1085546761},
            {1422298.8844147713, 1695029.8052440411}
        };

        assertSphericalLaeaMatchesReference(90, inputs, northExpected);
        assertSphericalLaeaMatchesReference(-90, inputs, southExpected);
    }

    private static void assertSphericalLaeaMatchesReference(
            double latitudeOfOrigin,
            double[][] inputs,
            double[][] expected) {
        Converter converter = Proj4.proj4(
            "+proj=longlat +R=6371228 +no_defs",
            "+proj=laea +lat_0=" + latitudeOfOrigin
                + " +lon_0=0 +x_0=0 +y_0=0 +R=6371228 +units=m +no_defs");
        for (int i = 0; i < inputs.length; i++) {
            Point projected =
                converter.forward(new Point(inputs[i][0], inputs[i][1]));
            assertEquals(expected[i][0], projected.x, 1e-8);
            assertEquals(expected[i][1], projected.y, 1e-8);
        }
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
    void testSinusoidalFalseEastingNorthing() {
        // Regression: sinu forward must apply +x_0/+y_0 (it previously omitted them
        // while the inverse subtracted them, so offset CRSs did not round-trip).
        Converter noOff = Proj4.proj4("+proj=longlat +datum=WGS84",
            "+proj=sinu +lon_0=0 +x_0=0 +y_0=0 +ellps=WGS84 +units=m +no_defs");
        Converter withOff = Proj4.proj4("+proj=longlat +datum=WGS84",
            "+proj=sinu +lon_0=0 +x_0=1000000 +y_0=2000000 +ellps=WGS84 +units=m +no_defs");

        Point base = noOff.forward(new Point(20, 10));
        Point off = withOff.forward(new Point(20, 10));
        assertEquals(base.x + 1000000, off.x, 0.01, "x_0 applied in forward");
        assertEquals(base.y + 2000000, off.y, 0.01, "y_0 applied in forward");

        Point restored = withOff.inverse(new Point(off.x, off.y));
        assertEquals(20, restored.x, DEGREE_TOLERANCE);
        assertEquals(10, restored.y, DEGREE_TOLERANCE);
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
    void testLccExplicitEquatorialSecondParallelMatchesProj() {
        Converter converter = Proj4.proj4(
            "+proj=longlat +datum=WGS84",
            "+proj=lcc +lat_1=30 +lat_2=0 +lat_0=10 +lon_0=0 +ellps=WGS84"
        );
        Point projected = converter.forward(new Point(5, 20));
        assertEquals(507252.1405529205, projected.x, 1e-6);
        assertEquals(1076164.3771528224, projected.y, 1e-6);
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
