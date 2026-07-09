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
 * Tests for Geocentric coordinates (geocent / EPSG:4978-style).
 *
 * <p>Reference X/Y/Z from pyproj/PROJ 9.5.1. Tolerance 1e-4 m (ECEF meters).</p>
 */
class GeocentricTest {

    private static final double M_EPSLN = 1e-4;
    private static final String WGS84 = "+proj=longlat +datum=WGS84 +no_defs";
    private static final String GEOCENT = "+proj=geocent +datum=WGS84 +units=m +no_defs";

    @BeforeEach
    void setUp() {
        ProjectionRegistry.reset();
        ProjectionRegistry.start();
    }

    @Test
    void testRegistry() {
        assertNotNull(ProjectionRegistry.get("geocent"));
        assertNotNull(ProjectionRegistry.get("Geocentric"));
        assertNotNull(ProjectionRegistry.get("geocentric"));
    }

    @Test
    void testKnownValues3d() {
        // (lon, lat, h) -> ECEF (X, Y, Z), per pyproj/PROJ 9.5.1
        double[][] cases = {
            {-7.56, 55.95, 0, 3548342.473034, -470928.890965, 5261327.157452},
            {2.35, 48.85, 100, 4201539.397559, 172423.833421, 4779673.699511},
            {139.69, 35.69, 0, -3954720.064214, 3355033.336380, 3700309.845473},
        };
        Converter conv = Proj4.proj4(WGS84, GEOCENT);
        for (double[] c : cases) {
            Point xyz = conv.forward(new Point(c[0], c[1], c[2]));
            assertEquals(c[3], xyz.x, M_EPSLN, "X for " + c[0] + "," + c[1]);
            assertEquals(c[4], xyz.y, M_EPSLN, "Y for " + c[0] + "," + c[1]);
            assertEquals(c[5], xyz.z, M_EPSLN, "Z for " + c[0] + "," + c[1]);
        }
    }

    @Test
    void testTwoDimensionalInputKeepsComputedZ() {
        // Mirrors proj4js test/test.js EPSG:4978 case: 2D input to a geocentric CRS
        // must return the computed Z, not reset it to 0 (proj4js 0ee1202, backported
        // ahead of this port — this test activates that guard).
        Converter conv = Proj4.proj4(WGS84, GEOCENT);
        Point xyz = conv.forward(new Point(-7.56, 55.95));
        assertEquals(3548342.473034, xyz.x, M_EPSLN, "X");
        assertEquals(-470928.890965, xyz.y, M_EPSLN, "Y");
        assertEquals(5261327.157452, xyz.z, M_EPSLN, "computed Z survives 2D input");
    }

    @Test
    void testRoundTrip() {
        Converter conv = Proj4.proj4(WGS84, GEOCENT);
        double[][] coords = {{-7.56, 55.95, 0}, {2.35, 48.85, 100}, {139.69, 35.69, 250}};
        for (double[] c : coords) {
            Point xyz = conv.forward(new Point(c[0], c[1], c[2]));
            Point ll = conv.inverse(new Point(xyz.x, xyz.y, xyz.z));
            assertEquals(c[0], ll.x, 1e-9, "lng for " + c[0]);
            assertEquals(c[1], ll.y, 1e-9, "lat for " + c[1]);
            assertEquals(c[2], ll.z, 1e-6, "height for " + c[2]);
        }
    }

    @Test
    void testNonMeterUnitsScaleAllAxes() {
        // A geocentric CRS carries its linear unit on all three axes. PROJ scales
        // X/Y/Z uniformly (reference below); proj4js scales only x/y, leaving z in
        // meters — this port follows PROJ. Reference from pyproj/PROJ 9.5.1.
        Converter conv = Proj4.proj4(WGS84, "+proj=geocent +datum=WGS84 +units=us-ft +no_defs");
        Point xyz = conv.forward(new Point(2.35, 48.85, 100));
        assertEquals(13784550.5068, xyz.x, 0.001, "X in US feet");
        assertEquals(565693.8601, xyz.y, 0.001, "Y in US feet");
        assertEquals(15681312.7958, xyz.z, 0.001, "Z in US feet (not meters)");

        Point ll = conv.inverse(new Point(xyz.x, xyz.y, xyz.z));
        assertEquals(2.35, ll.x, 1e-9);
        assertEquals(48.85, ll.y, 1e-9);
        assertEquals(100, ll.z, 1e-5, "height round-trips through the unit scaling");
    }

    @Test
    void testSerializationRoundTrip() {
        // Proj-string only: geocentric CRSs use GEOCCS/GeodeticCRS WKT structures the
        // serializer does not emit (it writes projected-CRS WKT).
        Proj original = new Proj(GEOCENT);
        String serialized = CRSSerializer.toProjString(original);
        assertTrue(serialized.contains("+proj=geocent"), "serialized: " + serialized);
        Proj reimported = new Proj(serialized);
        double lon = 2.35 * Math.PI / 180, lat = 48.85 * Math.PI / 180;
        Point want = original.forward(new Point(lon, lat, 100));
        Point got = reimported.forward(new Point(lon, lat, 100));
        assertEquals(want.x, got.x, M_EPSLN);
        assertEquals(want.y, got.y, M_EPSLN);
        assertEquals(want.z, got.z, M_EPSLN);
    }
}
