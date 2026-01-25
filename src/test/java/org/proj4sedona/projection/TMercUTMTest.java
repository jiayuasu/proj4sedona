package org.proj4sedona.projection;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.proj4sedona.Proj4;
import org.proj4sedona.common.ProjMath;
import org.proj4sedona.core.Point;
import org.proj4sedona.core.Proj;
import org.proj4sedona.transform.Converter;

import static org.junit.jupiter.api.Assertions.*;

class TMercUTMTest {

    private static final double TOLERANCE = 1e-6;
    private static final double DEGREE_TOLERANCE = 1e-7;

    @BeforeAll
    static void setup() {
        ProjectionRegistry.start();
    }

    @Test
    void testPjEnfn() {
        double es = 0.0066943799901413165;
        double[] en = ProjMath.pjEnfn(es);
        assertEquals(5, en.length);
        assertTrue(en[0] > 0.99);
    }

    @Test
    void testPjMlfnRoundTrip() {
        double es = 0.0066943799901413165;
        double[] en = ProjMath.pjEnfn(es);
        double lat = Math.toRadians(45);
        double ml = ProjMath.pjMlfn(lat, Math.sin(lat), Math.cos(lat), en);
        double latBack = ProjMath.pjInvMlfn(ml, es, en);
        assertEquals(lat, latBack, TOLERANCE);
    }

    @Test
    void testGatg() {
        double[] pp = {0.1, 0.01, 0.001, 0.0001, 0.00001, 0.000001};
        double result = ProjMath.gatg(pp, 0.5);
        assertTrue(Double.isFinite(result));
    }

    @Test
    void testClensCmplx() {
        double[] pp = {0.1, 0.02, 0.003, 0.0004, 0.00005, 0.000006};
        double[] result = ProjMath.clensCmplx(pp, Math.PI / 4, 0.1);
        assertEquals(2, result.length);
        assertTrue(Double.isFinite(result[0]));
        assertTrue(Double.isFinite(result[1]));
    }

    @Test
    void testAdjustZone() {
        assertEquals(32, ProjMath.adjustZone(32, 0));
        assertEquals(33, ProjMath.adjustZone(null, Math.toRadians(15)));
    }

    @Test
    void testTMercOrigin() {
        Proj tmerc = new Proj("+proj=tmerc +lat_0=0 +lon_0=0 +k=1 +x_0=0 +y_0=0 +datum=WGS84");
        Point p = tmerc.forward(new Point(0, 0));
        assertEquals(0, p.x, 0.01);
        assertEquals(0, p.y, 0.01);
    }

    @Test
    void testTMercRoundTrip() {
        Converter conv = Proj4.proj4(
            "+proj=longlat +datum=WGS84",
            "+proj=tmerc +lat_0=0 +lon_0=9 +k=0.9996 +x_0=500000 +y_0=0 +datum=WGS84"
        );
        Point original = new Point(9.0, 48.0);
        Point projected = conv.forward(original);
        Point restored = conv.inverse(projected);
        assertEquals(original.x, restored.x, DEGREE_TOLERANCE);
        assertEquals(original.y, restored.y, DEGREE_TOLERANCE);
    }

    @Test
    void testUTMZone32RoundTrip() {
        Converter conv = Proj4.proj4(
            "+proj=longlat +datum=WGS84",
            "+proj=utm +zone=32 +datum=WGS84"
        );
        // Munich: 11.576E, 48.137N
        Point munich = new Point(11.576, 48.137);
        Point projected = conv.forward(munich);
        
        // Check reasonable values (Munich is east of zone 32 center at 9E)
        assertTrue(projected.x > 600000 && projected.x < 800000, "Easting: " + projected.x);
        assertTrue(projected.y > 5300000 && projected.y < 5400000, "Northing: " + projected.y);
        
        // Round-trip
        Point restored = conv.inverse(projected);
        assertEquals(munich.x, restored.x, DEGREE_TOLERANCE);
        assertEquals(munich.y, restored.y, DEGREE_TOLERANCE);
    }

    @Test
    void testUTMZone10RoundTrip() {
        Converter conv = Proj4.proj4(
            "+proj=longlat +datum=WGS84",
            "+proj=utm +zone=10 +datum=WGS84"
        );
        // San Francisco: -122.4194, 37.7749
        Point sf = new Point(-122.4194, 37.7749);
        Point projected = conv.forward(sf);
        assertTrue(projected.x > 500000 && projected.x < 600000);
        assertTrue(projected.y > 4100000 && projected.y < 4200000);
        Point restored = conv.inverse(projected);
        assertEquals(sf.x, restored.x, DEGREE_TOLERANCE);
        assertEquals(sf.y, restored.y, DEGREE_TOLERANCE);
    }

    @Test
    void testUTMSouthernHemisphere() {
        Converter conv = Proj4.proj4(
            "+proj=longlat +datum=WGS84",
            "+proj=utm +zone=56 +south +datum=WGS84"
        );
        // Sydney: 151.2093E, -33.8688S
        Point sydney = new Point(151.2093, -33.8688);
        Point projected = conv.forward(sydney);
        assertTrue(projected.y > 6000000, "Southern hemisphere should have large northing");
        Point restored = conv.inverse(projected);
        assertEquals(sydney.x, restored.x, DEGREE_TOLERANCE);
        assertEquals(sydney.y, restored.y, DEGREE_TOLERANCE);
    }

    @Test
    void testUTMZone33RoundTrip() {
        Converter conv = Proj4.proj4(
            "+proj=longlat +datum=WGS84",
            "+proj=utm +zone=33 +datum=WGS84"
        );
        // Berlin: 13.405E, 52.52N
        Point berlin = new Point(13.405, 52.52);
        Point projected = conv.forward(berlin);
        
        // Berlin is west of zone 33 center (15E), so easting < 500000
        assertTrue(projected.x > 350000 && projected.x < 450000, "Easting: " + projected.x);
        assertTrue(projected.y > 5800000 && projected.y < 5850000, "Northing: " + projected.y);
        
        // Round-trip
        Point restored = conv.inverse(projected);
        assertEquals(berlin.x, restored.x, DEGREE_TOLERANCE);
        assertEquals(berlin.y, restored.y, DEGREE_TOLERANCE);
    }

    @Test
    void testTMercNearPole() {
        Converter conv = Proj4.proj4(
            "+proj=longlat +datum=WGS84",
            "+proj=tmerc +lat_0=0 +lon_0=0 +k=1 +x_0=0 +y_0=0 +datum=WGS84"
        );
        Point nearPole = new Point(0, 89);
        Point projected = conv.forward(nearPole);
        assertTrue(Double.isFinite(projected.x));
        assertTrue(Double.isFinite(projected.y));
    }

    @Test
    void testUTMMultipleZonesRoundTrip() {
        double[][] testPoints = {{-75, 40}, {0, 51}, {105, 35}};
        for (double[] coords : testPoints) {
            double lon = coords[0], lat = coords[1];
            int zone = (int) Math.floor((lon + 180) / 6) + 1;
            if (zone > 60) zone = 60;
            Converter conv = Proj4.proj4(
                "+proj=longlat +datum=WGS84",
                "+proj=utm +zone=" + zone + " +datum=WGS84"
            );
            Point original = new Point(lon, lat);
            Point projected = conv.forward(original);
            Point restored = conv.inverse(projected);
            assertEquals(lon, restored.x, DEGREE_TOLERANCE * 10, "Zone " + zone);
            assertEquals(lat, restored.y, DEGREE_TOLERANCE * 10);
        }
    }

    @Test
    void testETMercRegisteredNames() {
        assertNotNull(ProjectionRegistry.get("tmerc"));
        assertNotNull(ProjectionRegistry.get("etmerc"));
        assertNotNull(ProjectionRegistry.get("Transverse_Mercator"));
    }

    @Test
    void testUTMRegisteredNames() {
        assertNotNull(ProjectionRegistry.get("utm"));
    }
}
