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
 * Tests for the Gauss-Schreiber Transverse Mercator projection (gstmerc).
 *
 * <p>proj4js has no gstmerc case in {@code test/testData.js} and its published dist
 * does not bundle the projection, so the reference eastings/northings are from
 * pyproj/PROJ 9.5.1 (international-ellipsoid source on both sides, so the numbers
 * exercise the projection math alone). Tolerance 0.01 m / 1e-7&deg;.</p>
 */
class GaussSchreiberTransverseMercatorTest {

    private static final double XY_EPSLN = 0.01;
    private static final double LL_EPSLN = 1e-7;

    // Gauss Laborde Réunion
    private static final String GSTMERC =
        "+proj=gstmerc +lat_0=-21.116666667 +lon_0=55.53333333 +k_0=1 "
            + "+x_0=160000 +y_0=50000 +ellps=intl +units=m +no_defs";
    private static final String INTL_LL = "+proj=longlat +ellps=intl +no_defs";

    @BeforeEach
    void setUp() {
        ProjectionRegistry.reset();
        ProjectionRegistry.start();
    }

    @Test
    void testRegistry() {
        assertNotNull(ProjectionRegistry.get("gstmerc"));
        assertNotNull(ProjectionRegistry.get("gstmerg"), "historical proj4js alias");
        assertNotNull(ProjectionRegistry.get("Gauss_Schreiber_Transverse_Mercator"));
    }

    @Test
    void testKnownValues() {
        // ll (deg) -> easting, northing (m), per pyproj/PROJ 9.5.1
        double[][] cases = {
            {55.53333333, -21.116666667, 160000.000000, 50000.000000},
            {55.5, -21.1, 156536.491110, 51844.974871},
            {55.7, -21.3, 177294.269791, 29691.909304},
            {55.3, -20.9, 135723.069080, 73971.469994},
        };
        Converter conv = Proj4.proj4(INTL_LL, GSTMERC);
        for (double[] c : cases) {
            Point xy = conv.forward(new Point(c[0], c[1]));
            assertEquals(c[2], xy.x, XY_EPSLN, "easting for " + c[0] + "," + c[1]);
            assertEquals(c[3], xy.y, XY_EPSLN, "northing for " + c[0] + "," + c[1]);
        }
    }

    @Test
    void testRoundTrip() {
        Converter conv = Proj4.proj4(INTL_LL, GSTMERC);
        double[][] coords = {
            {55.53333333, -21.116666667}, {55.5, -21.1}, {55.7, -21.3}, {55.3, -20.9},
        };
        for (double[] c : coords) {
            Point xy = conv.forward(new Point(c[0], c[1]));
            Point ll = conv.inverse(new Point(xy.x, xy.y));
            assertEquals(c[0], ll.x, LL_EPSLN, "lng for " + c[0]);
            assertEquals(c[1], ll.y, LL_EPSLN, "lat for " + c[1]);
        }
    }

    @Test
    void testSerializationRoundTrip() {
        Proj original = new Proj(GSTMERC);
        double[] c = {55.5, -21.1};
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
