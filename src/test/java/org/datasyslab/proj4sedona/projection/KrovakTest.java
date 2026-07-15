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
 * Tests for the Krovak projection (krovak).
 *
 * <p>Krovak is always defined on the Bessel 1841 ellipsoid; the reference eastings/
 * northings are from proj4js 2.20.9 for the S-JTSK / Krovak definition, transformed
 * from Bessel longitude/latitude (same ellipsoid on both sides, no datum shift, so the
 * numbers exercise the projection math). Output is south-west oriented (negative), as in
 * proj4js — the czech/north-orientation flag is never applied. Tolerance 0.01 m / 1e-7&deg;.</p>
 */
class KrovakTest {

    private static final double XY_EPSLN = 0.01;
    private static final double LL_EPSLN = 1e-7;

    // EPSG:5514 / S-JTSK / Krovak, expressed on Greenwich.
    private static final String KROVAK =
        "+proj=krovak +lat_0=49.5 +lon_0=24.83333333333333 +alpha=30.28813972222222 "
            + "+k=0.9999 +x_0=0 +y_0=0 +ellps=bessel +pm=greenwich +units=m +no_defs";
    private static final String BESSEL_LL = "+proj=longlat +ellps=bessel +no_defs";

    @BeforeEach
    void setUp() {
        ProjectionRegistry.reset();
        ProjectionRegistry.start();
    }

    @Test
    void testRegistry() {
        assertNotNull(ProjectionRegistry.get("krovak"));
        assertNotNull(ProjectionRegistry.get("Krovak"));
        assertNotNull(ProjectionRegistry.get("Krovak (North Orientated)"));
    }

    @Test
    void testKnownValues() {
        // ll (Bessel deg) -> easting, northing (m), per proj4js 2.20.9
        double[][] cases = {
            {14.42, 50.08, -743101.013895, -1043898.660356},
            {16.6, 49.2, -598786.141107, -1160206.215911},
            {17.1, 48.15, -574341.827532, -1280152.023098},
        };
        Converter conv = Proj4.proj4(BESSEL_LL, KROVAK);
        for (double[] c : cases) {
            Point xy = conv.forward(new Point(c[0], c[1]));
            assertEquals(c[2], xy.x, XY_EPSLN, "easting for " + c[0] + "," + c[1]);
            assertEquals(c[3], xy.y, XY_EPSLN, "northing for " + c[0] + "," + c[1]);
        }
    }

    @Test
    void testRoundTrip() {
        Converter conv = Proj4.proj4(BESSEL_LL, KROVAK);
        double[][] coords = {{14.42, 50.08}, {16.6, 49.2}, {17.1, 48.15}, {18.9, 49.0}};
        for (double[] c : coords) {
            Point xy = conv.forward(new Point(c[0], c[1]));
            Point ll = conv.inverse(new Point(xy.x, xy.y));
            assertEquals(c[0], ll.x, LL_EPSLN, "lng for " + c[0]);
            assertEquals(c[1], ll.y, LL_EPSLN, "lat for " + c[1]);
        }
    }

    @Test
    void testFalseEastingNorthingApplied() {
        // proj4js's krovak ignores +x_0/+y_0; this port applies them (matching PROJ and
        // the codebase convention). A 500000 offset must shift the zero-offset result.
        Converter conv = Proj4.proj4(BESSEL_LL, KROVAK
            .replace("+x_0=0 +y_0=0", "+x_0=500000 +y_0=500000"));
        Point xy = conv.forward(new Point(14.42, 50.08));
        assertEquals(-743101.013895 + 500000, xy.x, XY_EPSLN, "easting with offset");
        assertEquals(-1043898.660356 + 500000, xy.y, XY_EPSLN, "northing with offset");
        Point ll = conv.inverse(new Point(xy.x, xy.y));
        assertEquals(14.42, ll.x, LL_EPSLN);
        assertEquals(50.08, ll.y, LL_EPSLN);
    }

    @Test
    void testDefaultScaleFactor() {
        // With +k omitted, Krovak's 0.9999 scale factor must be used (as PROJ does), not the
        // generic 1.0 default. (proj4js gets this wrong and projects with 1.0; the expected
        // values below are PROJ's, matching an explicit +k=0.9999.)
        Converter conv = Proj4.proj4(BESSEL_LL,
            "+proj=krovak +lat_0=49.5 +lon_0=24.83333333333333 +ellps=bessel "
                + "+pm=greenwich +units=m +no_defs");
        Point xy = conv.forward(new Point(14.42, 50.08));
        assertEquals(-743101.013895, xy.x, XY_EPSLN, "easting with default k0");
        assertEquals(-1043898.660356, xy.y, XY_EPSLN, "northing with default k0");
    }

    @Test
    void testSerializationRoundTrip() {
        Proj original = new Proj(KROVAK);
        double[][] coords = {{14.42, 50.08}, {16.6, 49.2}};
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
}
