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
 * Tests for the Van der Grinten (vandg) and Bonne projections. Reference eastings/
 * northings are from proj4js 2.20.9 (WGS84 &rarr; CRS); definitions keep the same datum
 * on both sides, so the numbers exercise the projection math. Tolerance 0.01 m / 1e-7&deg;.
 */
class VanDerGrintenBonneTest {

    private static final double XY_EPSLN = 0.01;
    private static final double LL_EPSLN = 1e-7;
    private static final String WGS84 = "+proj=longlat +datum=WGS84 +no_defs";

    private static final String VANDG =
        "+proj=vandg +lon_0=0 +x_0=0 +y_0=0 +a=6371007 +units=m +no_defs";
    private static final String BONNE_E =
        "+proj=bonne +lat_1=40 +lon_0=0 +x_0=0 +y_0=0 +ellps=WGS84 +units=m +no_defs";
    private static final String BONNE_S =
        "+proj=bonne +lat_1=40 +lon_0=0 +x_0=0 +y_0=0 +a=6371007 +b=6371007 +units=m +no_defs";

    @BeforeEach
    void setUp() {
        ProjectionRegistry.reset();
        ProjectionRegistry.start();
    }

    @Test
    void testRegistry() {
        assertNotNull(ProjectionRegistry.get("vandg"));
        assertNotNull(ProjectionRegistry.get("Van_der_Grinten"));
        assertNotNull(ProjectionRegistry.get("bonne"));
    }

    @Test
    void testVanDerGrinten() {
        assertForward(VANDG, new double[][] {
            {20, 40, 2102451.3435, 4704549.9752},
            {-60, -30, -6487975.8526, -3496279.3225},
            {100, 55, 9919575.7830, 7425151.0583},
        });
        assertRoundTrip(VANDG, new double[][] {{20, 40}, {-60, -30}, {100, 55}});
    }

    @Test
    void testVanDerGrintenEquator() {
        // proj4js throws (non-finite) at the equator; this port returns the correct
        // x = R * dlon, y = 0.
        Converter conv = Proj4.proj4(WGS84, VANDG);
        Point xy = conv.forward(new Point(30, 0));
        assertEquals(6371007 * 30 * Math.PI / 180, xy.x, XY_EPSLN, "equator easting");
        assertEquals(0, xy.y, XY_EPSLN, "equator northing");
        Point ll = conv.inverse(new Point(xy.x, xy.y));
        assertEquals(30, ll.x, 1e-6);
        assertEquals(0, ll.y, 1e-6);
    }

    @Test
    void testBonneEllipsoid() {
        assertForward(BONNE_E, new double[][] {
            {5, 45, 394029.1105, 566425.3996},
            {10, 50, 715504.7995, 1150816.2352},
            {-3, 38, -263447.6992, -217599.8973},
        });
        assertRoundTrip(BONNE_E, new double[][] {{5, 45}, {10, 50}, {-3, 38}});
    }

    @Test
    void testBonneSphere() {
        assertForward(BONNE_S, new double[][] {
            {5, 45, 392929.3787, 566954.4064},
            {10, 50, 713299.9018, 1151324.7462},
            {-3, 38, -262819.1175, -217969.5670},
        });
        assertRoundTrip(BONNE_S, new double[][] {{5, 45}, {10, 50}, {-3, 38}});
    }

    @Test
    void testSerializationRoundTrip() {
        // BONNE_S (a sphere) is intentionally excluded: spherical CRSs don't yet
        // round-trip through the serializer (the +b/sphere flag is dropped) — tracked
        // separately in #78. It's not specific to these projections.
        for (String def : new String[] {VANDG, BONNE_E}) {
            Proj original = new Proj(def);
            double[] c = {6, 46};
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
