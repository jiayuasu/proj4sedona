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
 * Tests for the Equal Earth (eqearth) and Geostationary (geos) projections. Reference
 * eastings/northings are from proj4js 2.20.9 (WGS84 &rarr; CRS); definitions keep the
 * same datum on both sides, so the numbers exercise the projection math. Tolerance
 * 0.01 m / 1e-7&deg;.
 */
class EqualEarthGeosTest {

    private static final double XY_EPSLN = 0.01;
    private static final double LL_EPSLN = 1e-7;
    private static final String WGS84 = "+proj=longlat +datum=WGS84 +no_defs";

    private static final String EQEARTH_E =
        "+proj=eqearth +lon_0=0 +x_0=0 +y_0=0 +ellps=WGS84 +units=m +no_defs";
    private static final String EQEARTH_S =
        "+proj=eqearth +lon_0=0 +x_0=0 +y_0=0 +a=6371007 +b=6371007 +units=m +no_defs";
    private static final String GEOS_X =
        "+proj=geos +sweep=x +lon_0=-75 +h=35786023 +a=6378137.0 +b=6356752.314 +units=m +no_defs";
    private static final String GEOS_Y =
        "+proj=geos +sweep=y +lon_0=0 +h=35785831 +a=6378137 +b=6356752.31414 +units=m +no_defs";

    @BeforeEach
    void setUp() {
        ProjectionRegistry.reset();
        ProjectionRegistry.start();
    }

    @Test
    void testRegistry() {
        assertNotNull(ProjectionRegistry.get("eqearth"));
        assertNotNull(ProjectionRegistry.get("Equal_Earth"));
        assertNotNull(ProjectionRegistry.get("geos"));
        assertNotNull(ProjectionRegistry.get("Geostationary_Satellite"));
    }

    @Test
    void testEqualEarthEllipsoid() {
        assertForward(EQEARTH_E, new double[][] {
            {20, 40, 1699499.6614, 4921020.0618},
            {-100, -30, -8965339.9386, -3764325.4269},
        });
        assertRoundTrip(EQEARTH_E, new double[][] {{20, 40}, {-100, -30}, {5, 60}});
    }

    @Test
    void testEqualEarthSphere() {
        assertForward(EQEARTH_S, new double[][] {
            {20, 40, 1698159.2845, 4935117.0615},
            {-100, -30, -8960827.2770, -3777593.7991},
        });
        assertRoundTrip(EQEARTH_S, new double[][] {{20, 40}, {-100, -30}, {5, 60}});
    }

    @Test
    void testGeostationaryXSweep() {
        // proj4js testData published point.
        assertForward(GEOS_X, new double[][] {
            {-95, 25, -1920508.77, 2605680.03},
            {-60, 10, 1610055.5137, 1090452.2824},
        });
        assertRoundTrip(GEOS_X, new double[][] {{-95, 25}, {-60, 10}, {-75, 0}});
    }

    @Test
    void testGeostationaryYSweep() {
        assertForward(GEOS_Y, new double[][] {
            {10, -5, 1099312.2595, -550025.7331},
        });
        assertRoundTrip(GEOS_Y, new double[][] {{10, -5}, {0, 0}, {-8, 12}});
    }

    @Test
    void testGeostationaryFalseEastingNorthing() {
        // Intentional divergence from proj4js (which ignores +x_0/+y_0 for geos): this
        // port applies them, matching PROJ and the codebase convention. A 1000/2000
        // offset must shift the zero-offset result and round-trip.
        Converter base = Proj4.proj4(WGS84, GEOS_Y);
        Converter off = Proj4.proj4(WGS84, GEOS_Y.replace("+lon_0=0", "+lon_0=0 +x_0=1000 +y_0=2000"));
        Point b = base.forward(new Point(10, -5));
        Point o = off.forward(new Point(10, -5));
        assertEquals(b.x + 1000, o.x, XY_EPSLN, "x_0 applied in forward");
        assertEquals(b.y + 2000, o.y, XY_EPSLN, "y_0 applied in forward");
        Point ll = off.inverse(new Point(o.x, o.y));
        assertEquals(10, ll.x, LL_EPSLN);
        assertEquals(-5, ll.y, LL_EPSLN);
    }

    @Test
    void testGeostationaryFarSideIsNull() {
        // A point on the far side of the globe from the sub-satellite point is not visible.
        Converter conv = Proj4.proj4(WGS84, GEOS_Y);
        assertNull(conv.forward(new Point(180, 0)));
    }

    @Test
    void testGeostationarySphereFarSideIsNull() {
        // The spherical branch must also reject far-side points (proj4js only guards the
        // ellipsoidal path).
        Converter conv = Proj4.proj4(WGS84,
            "+proj=geos +lon_0=0 +h=35785831 +a=6378137 +b=6378137 +units=m +no_defs");
        assertNull(conv.forward(new Point(180, 0)));
        assertNotNull(conv.forward(new Point(5, 5)), "near-side point must still project");
    }

    @Test
    void testSerializationRoundTrip() {
        // eqearth: all four formats. geos: proj string only (WKT/PROJJSON serialization
        // of +h/+sweep is not implemented; proj string is the canonical geos form).
        assertProjMathRoundTrip(EQEARTH_E, new String[] {"proj", "wkt1", "wkt2", "projjson"});
        assertProjMathRoundTrip(GEOS_X, new String[] {"proj"});
    }

    private void assertProjMathRoundTrip(String def, String[] formats) {
        Proj original = new Proj(def);
        double[] c = {-40, 15};
        for (String fmt : formats) {
            String serialized;
            switch (fmt) {
                case "wkt1": serialized = CRSSerializer.toWkt1(original); break;
                case "wkt2": serialized = CRSSerializer.toWkt2(original); break;
                case "projjson": serialized = CRSSerializer.toProjJson(original); break;
                default: serialized = CRSSerializer.toProjString(original);
            }
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
