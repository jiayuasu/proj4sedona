package org.datasyslab.proj4sedona.projection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.datasyslab.proj4sedona.Proj4;
import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.transform.Converter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Hotine Oblique Mercator (omerc) projection.
 *
 * <p>The four cases below are ported from proj4js {@code test/testData.js} with the
 * same reference coordinates, driven WGS84 &rarr; CRS (forward) and CRS &rarr; WGS84
 * (inverse) as proj4js runs them. They cover the variants: Type A ({@code +no_uoff},
 * alpha+gamma), Type B (alpha only), and the gamma-only form. Tolerance is
 * {@code 10^-acc} (xy 0.01 m, ll 1e-6&deg;; the Alaska entry's reference is rounded to
 * 2 decimals so it uses 0.1 m).</p>
 */
class ObliqueMercatorTest {

    private static final double XY_EPSLN = 0.01;
    private static final double LL_EPSLN = 1e-6;

    @BeforeEach
    void setUp() {
        ProjectionRegistry.reset();
        ProjectionRegistry.start();
    }

    @Test
    void testRegistry() {
        assertNotNull(ProjectionRegistry.get("omerc"));
        assertNotNull(ProjectionRegistry.get("Hotine_Oblique_Mercator"));
        assertNotNull(ProjectionRegistry.get("Hotine_Oblique_Mercator_Azimuth_Center"));
        assertNotNull(ProjectionRegistry.get("Oblique_Mercator"));
    }

    @Test
    void testMalaysiaTypeANoUoff() {
        // RSO-style, variant A (+no_uoff), alpha + gamma.
        check("+proj=omerc +lat_0=4 +lonc=102.25 +alpha=323.0257964666666 +k=0.99984 "
                + "+x_0=804671 +y_0=0 +no_uoff +gamma=323.1301023611111 +ellps=GRS80 +units=m +no_defs",
            101.70979078430528, 3.06268465621428, 412597.532715, 338944.957259, XY_EPSLN);
    }

    @Test
    void testAlaskaTypeANoUoff() {
        // Alaska zone 1, variant A (+no_uoff). Reference rounded to 2 decimals -> 0.1 m.
        check("+proj=omerc +lat_0=57 +lonc=-133.6666666666667 +alpha=323.1301023611111 +k=0.9999 "
                + "+x_0=5000000 +y_0=-5000000 +no_uoff +gamma=323.1301023611111 +ellps=GRS80 "
                + "+towgs84=0,0,0,0,0,0,0 +units=m +no_defs",
            -128.115000029, 44.8150000066, 1264314.74, -763162.04, 0.1);
    }

    @Test
    void testJapanTypeB() {
        // Variant B: alpha only (offset applied).
        check("+proj=omerc +lat_0=37.4769061 +lonc=141.0039618 +alpha=202.22 +k=1 "
                + "+x_0=138 +y_0=77.65 +ellps=WGS84 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs",
            141.003611, 37.476802, 168.2438, 64.1736, XY_EPSLN);
    }

    @Test
    void testGammaOnly() {
        // gamma only (no alpha): exercises the alpha_c = 0 / alp = true branch.
        check("+proj=omerc +gamma=-1.574854 +lonc=144.6934349669362 +lat_0=-37.82121510921045 "
                + "+x_0=2000 +y_0=2000 +k_0=1",
            144.7074447, -37.8195261, 3227.90322952057, 2221.20579300822, XY_EPSLN);
    }

    private void check(String def, double lon, double lat, double east, double north, double xyTol) {
        Converter conv = Proj4.proj4("+proj=longlat +datum=WGS84 +no_defs", def);

        Point xy = conv.forward(new Point(lon, lat));
        assertEquals(east, xy.x, xyTol, "easting");
        assertEquals(north, xy.y, xyTol, "northing");

        Point ll = conv.inverse(new Point(east, north));
        assertEquals(lon, ll.x, LL_EPSLN, "lng");
        assertEquals(lat, ll.y, LL_EPSLN, "lat");
    }
}
