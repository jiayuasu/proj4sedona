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
 * Tests for the General Oblique Transformation (ob_tran) meta-projection.
 *
 * <p>All cases are ported from proj4js {@code test/testData.js} (which include the
 * upstream ob_tran fixes through the current fork head) with the published reference
 * coordinates. Rotated-longlat outputs are degrees; inner-projection outputs are
 * meters. Tolerance 0.01 m / 1e-6&deg;.</p>
 */
class GeneralObliqueTransformationTest {

    private static final double XY_EPSLN = 0.01;
    private static final double DEG_EPSLN = 1e-6;
    private static final String WGS84 = "+proj=longlat +datum=WGS84 +no_defs";

    @BeforeEach
    void setUp() {
        ProjectionRegistry.reset();
        ProjectionRegistry.start();
    }

    @Test
    void testRegistry() {
        assertNotNull(ProjectionRegistry.get("ob_tran"));
        assertNotNull(ProjectionRegistry.get("General_Oblique_Transformation"));
        assertNotNull(ProjectionRegistry.get("General Oblique Transformation"));
    }

    @Test
    void testNewPoleWithInnerProjection() {
        // From proj4js testData: new-pole mode wrapping moll and eqearth.
        assertCase("+proj=ob_tran +o_proj=moll +o_lat_p=45 +o_lon_p=-90",
            -2, -1, -7421459.08469763, -5444548.62238893, XY_EPSLN);
        assertCase("+proj=ob_tran +o_proj=eqearth +o_lat_p=85 +o_lon_p=10",
            20, 11, 2841069.73392768, 808313.281062982, XY_EPSLN);
    }

    @Test
    void testRotatedPoleLonglat() {
        // Rotated-pole climate-grid style: o_proj=longlat outputs rotated lon/lat in degrees.
        assertCase("+proj=ob_tran +o_proj=longlat +o_lon_p=0 +o_lat_p=35",
            -105, 40, -60.8425058899586, 32.0797099050498, DEG_EPSLN);
        assertCase("+proj=ob_tran +o_proj=longlat +o_lon_p=0 +o_lat_p=35 +lon_0=-113 +R=6371229 +no_defs",
            -105, 40, 6.32623159167842, -14.6380639304457, DEG_EPSLN);
        assertCase("+proj=ob_tran +o_proj=longlat +o_lon_p=0 +o_lat_p=31.758312 +lon_0=-92.402969 +R=6371229 +no_defs",
            -105, 40, -10.0776833744144, -17.2983149595925, DEG_EPSLN);
        assertCase("+proj=ob_tran +o_proj=longlat +o_lon_p=0 +o_lat_p=60.31 +lon_0=327.63 +a=6371229 +no_defs",
            116, -32, 153.441735338947, -5.89482638862363, DEG_EPSLN);
    }

    @Test
    void testRotateAboutPoint() {
        assertCase("+proj=ob_tran +o_proj=moll +o_alpha=5 +o_lon_c=40 +o_lat_c=-10",
            10, 5, -154995.962452491, -8241537.7450648, XY_EPSLN);
    }

    @Test
    void testNewEquatorPoints() {
        assertCase("+proj=ob_tran +o_proj=moll +o_lon_1=-180 +o_lon_2=180 +o_lat_1=-3 +o_lat_2=3",
            10, 5, -938419.673847826, -8448989.10202702, XY_EPSLN);
        assertCase("+proj=ob_tran +o_proj=moll +o_lon_1=-11 +o_lon_2=6 +o_lat_1=-3 +o_lat_2=3 +x_0=10000 +y_0=50000",
            -90, 85, 3725830.59144467, -7713738.57893275, XY_EPSLN);
        assertCase("+proj=ob_tran +o_proj=moll +o_lon_1=-11 +o_lon_2=6 +o_lat_1=-3 +o_lat_2=3 +x_0=10000 +y_0=50000 +R=6400000",
            -90, 85, 3738567.72835796, -7740351.14880248, XY_EPSLN);
    }

    @Test
    void testRoundTrip() {
        String[] defs = {
            "+proj=ob_tran +o_proj=moll +o_lat_p=45 +o_lon_p=-90",
            "+proj=ob_tran +o_proj=longlat +o_lon_p=0 +o_lat_p=35 +lon_0=-113 +R=6371229 +no_defs",
            "+proj=ob_tran +o_proj=moll +o_alpha=5 +o_lon_c=40 +o_lat_c=-10",
            "+proj=ob_tran +o_proj=moll +o_lon_1=-11 +o_lon_2=6 +o_lat_1=-3 +o_lat_2=3 +x_0=10000 +y_0=50000",
        };
        for (String def : defs) {
            Converter conv = Proj4.proj4(WGS84, def);
            for (double[] c : new double[][] {{-2, -1}, {20, 11}, {-105, 40}}) {
                Point xy = conv.forward(new Point(c[0], c[1]));
                Point ll = conv.inverse(new Point(xy.x, xy.y));
                assertEquals(c[0], ll.x, DEG_EPSLN, "lng for " + c[0] + " via " + def);
                assertEquals(c[1], ll.y, DEG_EPSLN, "lat for " + c[1] + " via " + def);
            }
        }
    }

    @Test
    void testInvalidParameters() {
        assertThrows(IllegalArgumentException.class,
            () -> Proj4.proj4(WGS84, "+proj=ob_tran +o_lat_p=45 +o_lon_p=-90")
                .forward(new Point(0, 0)),
            "missing o_proj");
        assertThrows(IllegalArgumentException.class,
            () -> Proj4.proj4(WGS84, "+proj=ob_tran +o_proj=ob_tran +o_lat_p=45 +o_lon_p=-90")
                .forward(new Point(0, 0)),
            "o_proj=ob_tran");
        assertThrows(IllegalArgumentException.class,
            () -> Proj4.proj4(WGS84, "+proj=ob_tran +o_proj=moll")
                .forward(new Point(0, 0)),
            "no rotation parameter set");
    }

    @Test
    void testSerializationRoundTrip() {
        // Proj-string only (ob_tran has no standard WKT method).
        for (String def : new String[] {
                "+proj=ob_tran +o_proj=moll +o_lat_p=45 +o_lon_p=-90 +no_defs",
                "+proj=ob_tran +o_proj=moll +o_alpha=5 +o_lon_c=40 +o_lat_c=-10 +no_defs"}) {
            Proj original = new Proj(def);
            String serialized = CRSSerializer.toProjString(original);
            assertTrue(serialized.contains("+o_proj=moll"), "keeps o_proj: " + serialized);
            Proj reimported = new Proj(serialized);
            double[] c = {-2, -1};
            Point want = original.forward(new Point(c[0] * Math.PI / 180, c[1] * Math.PI / 180));
            Point got = reimported.forward(new Point(c[0] * Math.PI / 180, c[1] * Math.PI / 180));
            assertEquals(want.x, got.x, XY_EPSLN, "easting after re-import of " + serialized);
            assertEquals(want.y, got.y, XY_EPSLN, "northing after re-import of " + serialized);
        }
    }

    private void assertCase(String def, double lon, double lat, double ex, double ey, double tol) {
        Converter conv = Proj4.proj4(WGS84, def + (def.contains("+no_defs") ? "" : " +no_defs"));
        Point xy = conv.forward(new Point(lon, lat));
        assertEquals(ex, xy.x, tol, "x for " + def);
        assertEquals(ey, xy.y, tol, "y for " + def);
    }
}
