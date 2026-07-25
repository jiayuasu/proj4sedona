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
 * Tests for the Swiss Oblique Mercator (somerc) projection.
 *
 * <p>proj4js has no somerc-specific case in {@code test/testData.js} (it exercises
 * the Swiss grid through the Hotine omerc WKT, a different algorithm that differs
 * by ~9 cm). The reference eastings/northings below are therefore taken from
 * proj4js 2.20.9's own {@code +proj=somerc} output — the canonical PROJ definition
 * for EPSG:21781 / EPSG:2056 — driven WGS84 &rarr; CRS, matching how proj4js runs
 * its cases. Tolerance is 0.01 m (xy) / 1e-7&deg; (ll).</p>
 */
class SwissObliqueMercatorTest {

    private static final double XY_EPSLN = 0.01;     // 1 cm
    private static final double LL_EPSLN = 1e-7;      // deg

    // CH1903 / LV03 (EPSG:21781) and CH1903+ / LV95 (EPSG:2056)
    private static final String LV03 =
        "+proj=somerc +lat_0=46.95240555555556 +lon_0=7.439583333333333 +k_0=1 "
            + "+x_0=600000 +y_0=200000 +ellps=bessel "
            + "+towgs84=674.374,15.056,405.346,0,0,0,0 +units=m +no_defs";
    private static final String LV95 =
        "+proj=somerc +lat_0=46.95240555555556 +lon_0=7.439583333333333 +k_0=1 "
            + "+x_0=2600000 +y_0=1200000 +ellps=bessel "
            + "+towgs84=674.374,15.056,405.346,0,0,0,0 +units=m +no_defs";

    @BeforeEach
    void setUp() {
        ProjectionRegistry.reset();
        ProjectionRegistry.start();
    }

    @Test
    void testRegistry() {
        assertNotNull(ProjectionRegistry.get("somerc"));
    }

    @Test
    void testLv03KnownValues() {
        // ll (deg) -> easting, northing (m), per proj4js 2.20.9 +proj=somerc
        double[][] cases = {
            {7.439583333, 46.952405556, 600072.389677, 200147.055632},
            {8.55, 47.37, 683941.530345, 247167.393043},
            {6.14, 46.2, 499760.951660, 117336.097859},
        };
        assertForward(LV03, cases);
    }

    @Test
    void testLv95KnownValues() {
        double[][] cases = {
            {7.439583333, 46.952405556, 2600072.389677, 1200147.055632},
            {8.55, 47.37, 2683941.530345, 1247167.393043},
            {6.14, 46.2, 2499760.951660, 1117336.097859},
        };
        assertForward(LV95, cases);
    }

    @Test
    void testLv03RoundTrip() {
        assertProjectionRoundTrip(LV03);
    }

    @Test
    void testLv95RoundTrip() {
        // LV95 differs from LV03 only by false easting/northing; exercise x0/y0
        // handling in both directions here too.
        assertProjectionRoundTrip(LV95);
    }

    private void assertProjectionRoundTrip(String def) {
        Converter conv = Proj4.proj4("+proj=longlat +datum=WGS84 +no_defs", def);
        double[][] coords = {{7.439583333, 46.952405556}, {8.55, 47.37}, {6.14, 46.2}, {9.5, 47.5}};
        for (double[] c : coords) {
            Point xy = conv.forward(new Point(c[0], c[1]));
            Point ll = conv.inverse(new Point(xy.x, xy.y));
            assertEquals(c[0], ll.x, LL_EPSLN, "lng for " + c[0]);
            assertEquals(c[1], ll.y, LL_EPSLN, "lat for " + c[1]);
        }
    }

    @Test
    void testSerializationRoundTrip() {
        // Regression guard for the round-trip bug: proj4sedona's serializer emits the
        // method name "Swiss Oblique Mercator", which must re-import (previously failed
        // with "Unknown projection: Swiss Oblique Mercator"). Compare every executable
        // export against the original; WKT2 and PROJJSON preserve the Swiss TOWGS84
        // transformation in a BoundCRS.
        Proj original = new Proj(LV03);
        double[][] coords = {{7.439583333, 46.952405556}, {8.55, 47.37}, {6.14, 46.2}};
        for (String serialized : new String[] {
                CRSSerializer.toProjString(original),
                CRSSerializer.toWkt1(original),
                CRSSerializer.toWkt2(original),
                CRSSerializer.toProjJson(original)}) {
            Proj reimported = new Proj(serialized); // must not throw
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
}
