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
 * Tests for the Tilted Perspective projection (tpers).
 *
 * <p>Reference eastings/northings are from pyproj/PROJ 9.5.1 and cross-checked
 * against proj4js 2.20.9 (identical). All definitions are spherical (tpers forces
 * spherical computation). Tolerance 0.01 m / 1e-7&deg;.</p>
 */
class TiltedPerspectiveTest {

    private static final double XY_EPSLN = 0.01;
    private static final double LL_EPSLN = 1e-7;
    private static final String WGS84 = "+proj=longlat +datum=WGS84 +no_defs";

    private static final String OBLIQ =
        "+proj=tpers +lat_0=40 +lon_0=-100 +h=5500000 +tilt=30 +azi=20 "
            + "+a=6378137 +b=6378137 +units=m +no_defs";
    private static final String EQUIT =
        "+proj=tpers +lat_0=0 +lon_0=0 +h=35785831 +tilt=0 +azi=0 "
            + "+a=6378137 +b=6378137 +units=m +no_defs";
    private static final String NPOLE =
        "+proj=tpers +lat_0=90 +lon_0=0 +h=3000000 +tilt=15 +azi=45 "
            + "+a=6378137 +b=6378137 +units=m +no_defs";

    @BeforeEach
    void setUp() {
        ProjectionRegistry.reset();
        ProjectionRegistry.start();
    }

    @Test
    void testRegistry() {
        assertNotNull(ProjectionRegistry.get("tpers"));
        assertNotNull(ProjectionRegistry.get("Tilted_Perspective"));
    }

    @Test
    void testObliqueKnownValues() {
        assertForward(OBLIQ, new double[][] {
            {-100, 40, 0, 0},
            {-95, 42, 295919.997906, 400765.399163},
            {-105, 35, -258351.885935, -820633.131131},
        });
        // The exact projection center is excluded: proj4js's inverse rh<EPSLN branch
        // returns lat=0 there (correct only for the equatorial mode) — a faithful
        // upstream quirk (verified identical in proj4js 2.20.9).
        assertRoundTrip(OBLIQ, new double[][] {{-95, 42}, {-105, 35}});
    }

    @Test
    void testEquatorialKnownValues() {
        assertForward(EQUIT, new double[][] {
            {0, 0, 0, 0},
            {10, -5, 1099625.353484, -554021.089355},
            {-8, 12, -863439.658780, 1318715.784975},
        });
        assertRoundTrip(EQUIT, new double[][] {{0, 0}, {10, -5}, {-8, 12}});
    }

    @Test
    void testNorthPolarKnownValues() {
        assertForward(NPOLE, new double[][] {
            {0, 80, 813796.372602, -842504.000259},
            {45, 75, 1539273.363745, 0},
            {-30, 85, 149849.471259, -578973.897378},
        });
        assertRoundTrip(NPOLE, new double[][] {{0, 80}, {45, 75}, {-30, 85}});
    }

    @Test
    void testDefaults() {
        // proj4js defaults: h = 100 km (Kármán line), tilt = 0, azi = 0. A bare
        // +proj=tpers must initialize (no exception) and round-trip near the origin.
        Converter conv = Proj4.proj4(WGS84, "+proj=tpers +a=6378137 +b=6378137 +units=m +no_defs");
        Point xy = conv.forward(new Point(0.05, 0.05));
        Point ll = conv.inverse(new Point(xy.x, xy.y));
        assertEquals(0.05, ll.x, LL_EPSLN);
        assertEquals(0.05, ll.y, LL_EPSLN);
    }

    @Test
    void testFarSideIsNull() {
        // Points beyond the camera's horizon are unprojectable. PROJ returns inf;
        // proj4js returns finite garbage — this port returns null (like ortho/geos).
        Converter obliq = Proj4.proj4(WGS84, OBLIQ);
        assertNull(obliq.forward(new Point(80, -40)), "antipodal-ish point not visible");
        Converter npole = Proj4.proj4(WGS84, NPOLE);
        assertNull(npole.forward(new Point(0, -60)), "southern point not visible from north polar view");
        assertNotNull(obliq.forward(new Point(-95, 42)), "near-side point still projects");
    }

    @Test
    void testFalseEastingNorthing() {
        // proj4js ignores +x_0/+y_0 for tpers; this port applies them (PROJ does too).
        Converter base = Proj4.proj4(WGS84, OBLIQ);
        Converter off = Proj4.proj4(WGS84,
            OBLIQ.replace("+a=", "+x_0=100000 +y_0=200000 +a="));
        Point b = base.forward(new Point(-95, 42));
        Point o = off.forward(new Point(-95, 42));
        assertEquals(b.x + 100000, o.x, XY_EPSLN, "x_0 applied (PROJ reference: 395919.9979)");
        assertEquals(b.y + 200000, o.y, XY_EPSLN, "y_0 applied (PROJ reference: 600765.3992)");
        Point ll = off.inverse(new Point(o.x, o.y));
        assertEquals(-95, ll.x, LL_EPSLN);
        assertEquals(42, ll.y, LL_EPSLN);
    }

    @Test
    void testSerializationRoundTrip() {
        // tpers is proj-string-only in practice (no standard WKT method); verify
        // +h/+tilt/+azi survive toProjString and re-import identically.
        Proj original = new Proj(OBLIQ);
        String serialized = CRSSerializer.toProjString(original);
        // Values may carry D2R/R2D float noise (e.g. 29.999999999999996); presence
        // checks here, exact behavior verified functionally below.
        assertTrue(serialized.contains("+tilt="), "serialized keeps +tilt: " + serialized);
        assertTrue(serialized.contains("+azi="), "serialized keeps +azi: " + serialized);
        assertTrue(serialized.contains("+h="), "serialized keeps +h: " + serialized);

        Proj reimported = new Proj(serialized);
        double[] c = {-95, 42};
        Point want = original.forward(new Point(c[0] * Math.PI / 180, c[1] * Math.PI / 180));
        Point got = reimported.forward(new Point(c[0] * Math.PI / 180, c[1] * Math.PI / 180));
        assertEquals(want.x, got.x, XY_EPSLN, "easting after re-import");
        assertEquals(want.y, got.y, XY_EPSLN, "northing after re-import");
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
