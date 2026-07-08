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
 * Tests for the Eckert VI projection (eck6). Reference eastings/northings are from
 * proj4js 2.20.9. Eckert VI is always spherical. Tolerance 0.01 m / 1e-7&deg;.
 */
class EckertVITest {

    private static final double XY_EPSLN = 0.01;
    private static final double LL_EPSLN = 1e-7;
    private static final String WGS84 = "+proj=longlat +datum=WGS84 +no_defs";

    private static final String ECK6 =
        "+proj=eck6 +lon_0=0 +x_0=0 +y_0=0 +a=6371007 +b=6371007 +units=m +no_defs";

    @BeforeEach
    void setUp() {
        ProjectionRegistry.reset();
        ProjectionRegistry.start();
    }

    @Test
    void testRegistry() {
        assertNotNull(ProjectionRegistry.get("eck6"));
        assertNotNull(ProjectionRegistry.get("Eckert_VI"));
    }

    @Test
    void testKnownValues() {
        assertForward(ECK6, new double[][] {
            {20, 40, 1604864.0148, 4951028.1113},
            {-100, -30, -8757097.6890, -3747398.7363},
            {45, 60, 2858468.8330, 7142151.5716},
        });
    }

    @Test
    void testRoundTrip() {
        assertRoundTrip(ECK6, new double[][] {{20, 40}, {-100, -30}, {45, 60}, {0, 0}});
    }

    @Test
    void testForcesSphereForEllipsoid() {
        // Eckert VI always computes spherically; an ellipsoidal def uses a as the radius.
        Converter ell = Proj4.proj4(WGS84,
            "+proj=eck6 +lon_0=0 +x_0=0 +y_0=0 +ellps=WGS84 +units=m +no_defs");
        Converter sph = Proj4.proj4(WGS84,
            "+proj=eck6 +lon_0=0 +x_0=0 +y_0=0 +a=6378137 +b=6378137 +units=m +no_defs");
        Point e = ell.forward(new Point(20, 40));
        Point s = sph.forward(new Point(20, 40));
        assertEquals(s.x, e.x, XY_EPSLN, "ellipsoid def must project like the sphere of radius a");
        assertEquals(s.y, e.y, XY_EPSLN);
    }

    @Test
    void testSerializationRoundTrip() {
        Proj original = new Proj(ECK6);
        double[] c = {30, 25};
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
