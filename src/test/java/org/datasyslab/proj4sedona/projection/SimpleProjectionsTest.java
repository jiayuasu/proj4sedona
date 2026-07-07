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
 * Tests for the Miller Cylindrical (mill), Gnomonic (gnom) and Orthographic (ortho)
 * projections. Reference eastings/northings are from proj4js 2.20.9 (WGS84 &rarr; CRS);
 * definitions keep the same datum on both sides, so the numbers exercise the projection
 * math. Tolerance 0.01 m / 1e-7&deg;.
 */
class SimpleProjectionsTest {

    private static final double XY_EPSLN = 0.01;
    private static final double LL_EPSLN = 1e-7;
    private static final String WGS84 = "+proj=longlat +datum=WGS84 +no_defs";

    private static final String MILL =
        "+proj=mill +lat_0=0 +lon_0=0 +x_0=0 +y_0=0 +R=6378137 +units=m +no_defs";
    private static final String GNOM =
        "+proj=gnom +lat_0=45 +lon_0=0 +x_0=0 +y_0=0 +ellps=WGS84 +units=m +no_defs";
    private static final String ORTHO =
        "+proj=ortho +lat_0=45 +lon_0=0 +x_0=0 +y_0=0 +a=6378137 +b=6378137 +units=m +no_defs";

    @BeforeEach
    void setUp() {
        ProjectionRegistry.reset();
        ProjectionRegistry.start();
    }

    @Test
    void testRegistry() {
        assertNotNull(ProjectionRegistry.get("mill"));
        assertNotNull(ProjectionRegistry.get("Miller_Cylindrical"));
        assertNotNull(ProjectionRegistry.get("gnom"));
        assertNotNull(ProjectionRegistry.get("ortho"));
    }

    @Test
    void testMiller() {
        assertForward(MILL, new double[][] {
            {10, 40, 1113194.90793, 4704138.28393},
            {-100, -30, -11131949.07933, -3441760.13671},
        });
        assertRoundTrip(MILL, new double[][] {{10, 40}, {-100, -30}, {45, 60}});
    }

    @Test
    void testGnomonic() {
        assertForward(GNOM, new double[][] {
            {5, 50, 359308.75202, 570078.10743},
            {-3, 44, -240323.99799, -106958.57123},
        });
        assertRoundTrip(GNOM, new double[][] {{5, 50}, {-3, 44}, {2, 47}});
    }

    @Test
    void testOrthographic() {
        assertForward(ORTHO, new double[][] {
            {5, 50, 357320.01913, 566922.79024},
            {10, 40, 848433.95315, -503403.89607},
        });
        assertRoundTrip(ORTHO, new double[][] {{5, 50}, {10, 40}, {-4, 48}});
    }

    @Test
    void testGnomonicScaleFactorRoundTrip() {
        // proj4js's gnom applies k0 only in the inverse; this port applies it in the
        // forward too, so a +k_0 CRS round-trips. Verify forward->inverse identity.
        Converter conv = Proj4.proj4(WGS84,
            "+proj=gnom +lat_0=45 +lon_0=0 +k_0=0.9 +x_0=0 +y_0=0 +ellps=WGS84 +units=m +no_defs");
        for (double[] c : new double[][] {{5, 50}, {-3, 44}}) {
            Point xy = conv.forward(new Point(c[0], c[1]));
            Point ll = conv.inverse(new Point(xy.x, xy.y));
            assertEquals(c[0], ll.x, LL_EPSLN, "lng for " + c[0]);
            assertEquals(c[1], ll.y, LL_EPSLN, "lat for " + c[1]);
        }
    }

    @Test
    void testOrthographicFarHemisphereIsNull() {
        // A point on the far side of the projection center is unprojectable (proj4js
        // likewise yields null here).
        Converter conv = Proj4.proj4(WGS84, ORTHO);
        assertNull(conv.forward(new Point(180, -45)));
    }

    @Test
    void testOrthographicOffsetRoundTrip() {
        // proj4js's ortho omits +x_0 in the forward path (asymmetric); this port applies
        // it, so an offset ortho CRS round-trips. Verify forward->inverse identity.
        Converter conv = Proj4.proj4(WGS84,
            "+proj=ortho +lat_0=45 +lon_0=0 +x_0=100000 +y_0=100000 +a=6378137 +b=6378137 +units=m +no_defs");
        Point xy = conv.forward(new Point(5, 50));
        Point ll = conv.inverse(new Point(xy.x, xy.y));
        assertEquals(5, ll.x, LL_EPSLN);
        assertEquals(50, ll.y, LL_EPSLN);
    }

    @Test
    void testSerializationRoundTrip() {
        for (String def : new String[] {MILL, GNOM, ORTHO}) {
            Proj original = new Proj(def);
            double[] c = {5, 48};
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

    private void assertForward(String def, double[][] cases) {
        Converter conv = Proj4.proj4(WGS84, def);
        for (double[] c : cases) {
            Point xy = conv.forward(new Point(c[0], c[1]));
            assertEquals(c[2], xy.x, XY_EPSLN, "easting for " + c[0] + "," + c[1]);
            assertEquals(c[3], xy.y, XY_EPSLN, "northing for " + c[0] + "," + c[1]);
        }
    }

    private void assertRoundTrip(String def, double[][] coords) {
        Converter conv = Proj4.proj4(WGS84, def);
        for (double[] c : coords) {
            Point xy = conv.forward(new Point(c[0], c[1]));
            Point ll = conv.inverse(new Point(xy.x, xy.y));
            assertEquals(c[0], ll.x, LL_EPSLN, "lng for " + c[0]);
            assertEquals(c[1], ll.y, LL_EPSLN, "lat for " + c[1]);
        }
    }
}
