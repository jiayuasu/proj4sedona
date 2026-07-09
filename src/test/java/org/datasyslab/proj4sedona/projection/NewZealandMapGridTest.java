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
 * Tests for the New Zealand Map Grid projection (nzmg).
 *
 * <p>Reference eastings/northings are from pyproj/PROJ 9.5.1 and cross-checked
 * identical in proj4js 2.20.9 (international-ellipsoid source, pure projection
 * math). Forward tolerance 0.01 m; the inverse uses 1 refinement iteration
 * (meter accuracy, matching proj4js incl. upstream 8c632d0), so round-trips are
 * asserted at 1e-4&deg; (~10 m).</p>
 */
class NewZealandMapGridTest {

    private static final double XY_EPSLN = 0.01;
    private static final double RT_EPSLN = 1e-4; // inverse is meter-accurate by design

    // EPSG:27200 (NZGD49 / New Zealand Map Grid), projection parameters only
    private static final String NZMG =
        "+proj=nzmg +lat_0=-41 +lon_0=173 +x_0=2510000 +y_0=6023150 "
            + "+ellps=intl +units=m +no_defs";
    private static final String INTL_LL = "+proj=longlat +ellps=intl +no_defs";

    @BeforeEach
    void setUp() {
        ProjectionRegistry.reset();
        ProjectionRegistry.start();
    }

    @Test
    void testRegistry() {
        assertNotNull(ProjectionRegistry.get("nzmg"));
        assertNotNull(ProjectionRegistry.get("New_Zealand_Map_Grid"));
    }

    @Test
    void testKnownValues() {
        // ll (deg) -> easting, northing (m), per pyproj/PROJ 9.5.1 (== proj4js)
        double[][] cases = {
            {173, -41, 2510000.000000, 6023150.000000},           // origin -> false origin
            {174.7645, -36.8509, 2667767.479835, 6482111.835745}, // Auckland
            {170.5036, -45.8742, 2316055.320203, 5478727.745716}, // Dunedin
            {172.6362, -43.5321, 2480614.526882, 5741827.025505}, // Christchurch
        };
        Converter conv = Proj4.proj4(INTL_LL, NZMG);
        for (double[] c : cases) {
            Point xy = conv.forward(new Point(c[0], c[1]));
            assertEquals(c[2], xy.x, XY_EPSLN, "easting for " + c[0] + "," + c[1]);
            assertEquals(c[3], xy.y, XY_EPSLN, "northing for " + c[0] + "," + c[1]);
        }
    }

    @Test
    void testRoundTrip() {
        Converter conv = Proj4.proj4(INTL_LL, NZMG);
        double[][] coords = {
            {173, -41}, {174.7645, -36.8509}, {170.5036, -45.8742}, {172.6362, -43.5321},
        };
        for (double[] c : coords) {
            Point xy = conv.forward(new Point(c[0], c[1]));
            Point ll = conv.inverse(new Point(xy.x, xy.y));
            assertEquals(c[0], ll.x, RT_EPSLN, "lng for " + c[0]);
            assertEquals(c[1], ll.y, RT_EPSLN, "lat for " + c[1]);
        }
    }

    @Test
    void testSerializationRoundTrip() {
        Proj original = new Proj(NZMG);
        double[] c = {174.7645, -36.8509};
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
