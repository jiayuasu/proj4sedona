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
 * Tests for the Cassini-Soldner projection (cass).
 *
 * <p>Reference eastings/northings are from proj4js 2.20.9 (WGS84 &rarr; CRS). Both the
 * ellipsoidal and spherical branches are covered; the definitions keep the same datum
 * on both sides so the numbers exercise the projection math alone. Tolerance is 0.01 m
 * (xy) / 1e-7&deg; (ll).</p>
 */
class CassiniSoldnerTest {

    private static final double XY_EPSLN = 0.01;
    private static final double LL_EPSLN = 1e-7;

    // Tobago-grid location on the Clarke 1866 ellipsoid (metric), and an authalic sphere.
    private static final String ELLIPSOID =
        "+proj=cass +lat_0=11.25217861111111 +lon_0=-60.68600888888889 "
            + "+x_0=187500 +y_0=180000 +ellps=clrk66 +units=m +no_defs";
    private static final String SPHERE =
        "+proj=cass +lat_0=0 +lon_0=0 +x_0=0 +y_0=0 +a=6370997 +b=6370997 +units=m +no_defs";

    @BeforeEach
    void setUp() {
        ProjectionRegistry.reset();
        ProjectionRegistry.start();
    }

    @Test
    void testRegistry() {
        assertNotNull(ProjectionRegistry.get("cass"));
        assertNotNull(ProjectionRegistry.get("Cassini"));
        assertNotNull(ProjectionRegistry.get("Cassini_Soldner"));
    }

    @Test
    void testEllipsoidKnownValues() {
        assertForward(ELLIPSOID, new double[][] {
            {-60.7, 11.25, 185972.229819, 179759.060307},
            {-60.5, 11.4, 207800.805377, 196357.114598},
            {-60.9, 11.1, 164121.067912, 163176.020714},
        });
    }

    @Test
    void testSphereKnownValues() {
        assertForward(SPHERE, new double[][] {
            {10, 5, 1107674.200397, 564506.689554},
            {-3, 52, -205316.910011, 5786371.601283},
            {1, -20, 104488.382218, -2224209.386881},
        });
    }

    @Test
    void testEllipsoidRoundTrip() {
        assertRoundTrip(ELLIPSOID, new double[][] {{-60.7, 11.25}, {-60.5, 11.4}, {-60.9, 11.1}});
    }

    @Test
    void testSphereRoundTrip() {
        assertRoundTrip(SPHERE, new double[][] {{10, 5}, {-3, 52}, {1, -20}});
    }

    @Test
    void testSerializationRoundTrip() {
        // cass uses only standard parameters; verify it round-trips through every format.
        Proj original = new Proj(ELLIPSOID);
        double[][] coords = {{-60.7, 11.25}, {-60.5, 11.4}};
        for (String serialized : new String[] {
                CRSSerializer.toProjString(original),
                CRSSerializer.toWkt1(original),
                CRSSerializer.toWkt2(original),
                CRSSerializer.toProjJson(original)}) {
            Proj reimported = new Proj(serialized);
            for (double[] c : coords) {
                Point want = original.forward(new Point(c[0] * Math.PI / 180, c[1] * Math.PI / 180));
                Point got = reimported.forward(new Point(c[0] * Math.PI / 180, c[1] * Math.PI / 180));
                assertEquals(want.x, got.x, XY_EPSLN, "easting after re-import of " + serialized);
                assertEquals(want.y, got.y, XY_EPSLN, "northing after re-import of " + serialized);
            }
        }
    }

    private void assertForward(String def, double[][] cases) {
        Converter conv = Proj4.proj4("+proj=longlat +datum=WGS84 +no_defs", def);
        for (double[] c : cases) {
            Point xy = conv.forward(new Point(c[0], c[1]));
            assertEquals(c[2], xy.x, XY_EPSLN, "easting for " + c[0] + "," + c[1]);
            assertEquals(c[3], xy.y, XY_EPSLN, "northing for " + c[0] + "," + c[1]);
        }
    }

    private void assertRoundTrip(String def, double[][] coords) {
        Converter conv = Proj4.proj4("+proj=longlat +datum=WGS84 +no_defs", def);
        for (double[] c : coords) {
            Point xy = conv.forward(new Point(c[0], c[1]));
            Point ll = conv.inverse(new Point(xy.x, xy.y));
            assertEquals(c[0], ll.x, LL_EPSLN, "lng for " + c[0]);
            assertEquals(c[1], ll.y, LL_EPSLN, "lat for " + c[1]);
        }
    }
}
