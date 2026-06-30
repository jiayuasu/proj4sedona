package org.datasyslab.proj4sedona.projection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.core.Proj;
import org.datasyslab.proj4sedona.transform.Transform;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the (American) Polyconic projection (poly).
 *
 * <p>The {@code testData*} cases below are ported from proj4js
 * ({@code test/testData.js}, SAD69 / Brazil Polyconic, EPSG:29101) and use the
 * same reference coordinates and tolerances. proj4js drives every case as
 * WGS84 &rarr; CRS (forward) and CRS &rarr; WGS84 (inverse); the tolerance is
 * {@code 10^-acc} (xy acc = -2 &rarr; 100 m, ll acc = 3 &rarr; 0.001&deg;).</p>
 */
class PolyconicTest {

    // proj4js WGS84 datum reference
    private static final Proj WGS84 = new Proj("+proj=longlat +datum=WGS84 +no_defs");

    // SAD69 / Brazil Polyconic (EPSG:29101), three WKT encodings from proj4js testData.js
    private static final String SAD69_WKT1_ESRI =
        "PROJCS[\"SAD69 / Brazil Polyconic\",GEOGCS[\"SAD69\",DATUM[\"D_South_American_1969\","
            + "SPHEROID[\"GRS_1967_SAD69\",6378160,298.25]],PRIMEM[\"Greenwich\",0],"
            + "UNIT[\"Degree\",0.017453292519943295]],PROJECTION[\"Polyconic\"],"
            + "PARAMETER[\"latitude_of_origin\",0],PARAMETER[\"central_meridian\",-54],"
            + "PARAMETER[\"false_easting\",5000000],PARAMETER[\"false_northing\",10000000],"
            + "UNIT[\"Meter\",1]]";
    private static final String SAD69_WKT1_EPSG =
        "PROJCS[\"SAD69 / Brazil Polyconic\",GEOGCS[\"SAD69\",DATUM[\"South_American_Datum_1969\","
            + "SPHEROID[\"GRS 1967 (SAD69)\",6378160,298.25,AUTHORITY[\"EPSG\",\"7050\"]],"
            + "AUTHORITY[\"EPSG\",\"6618\"]],PRIMEM[\"Greenwich\",0,AUTHORITY[\"EPSG\",\"8901\"]],"
            + "UNIT[\"degree\",0.01745329251994328,AUTHORITY[\"EPSG\",\"9122\"]],"
            + "AUTHORITY[\"EPSG\",\"4618\"]],UNIT[\"metre\",1,AUTHORITY[\"EPSG\",\"9001\"]],"
            + "PROJECTION[\"Polyconic\"],PARAMETER[\"latitude_of_origin\",0],"
            + "PARAMETER[\"central_meridian\",-54],PARAMETER[\"false_easting\",5000000],"
            + "PARAMETER[\"false_northing\",10000000],AUTHORITY[\"EPSG\",\"29101\"],"
            + "AXIS[\"X\",EAST],AXIS[\"Y\",NORTH]]";
    private static final String SAD69_WKT2 =
        "PROJCRS[\"SAD69 / Brazil Polyconic\",BASEGEOGCRS[\"SAD69\","
            + "DATUM[\"South American Datum 1969\","
            + "ELLIPSOID[\"GRS 1967 Modified\",6378160,298.25,LENGTHUNIT[\"metre\",1]]],"
            + "PRIMEM[\"Greenwich\",0,ANGLEUNIT[\"degree\",0.0174532925199433]],ID[\"EPSG\",4618]],"
            + "CONVERSION[\"Brazil Polyconic\",METHOD[\"American Polyconic\",ID[\"EPSG\",9818]],"
            + "PARAMETER[\"Latitude of natural origin\",0,"
            + "ANGLEUNIT[\"degree\",0.0174532925199433],ID[\"EPSG\",8801]],"
            + "PARAMETER[\"Longitude of natural origin\",-54,"
            + "ANGLEUNIT[\"degree\",0.0174532925199433],ID[\"EPSG\",8802]],"
            + "PARAMETER[\"False easting\",5000000,LENGTHUNIT[\"metre\",1],ID[\"EPSG\",8806]],"
            + "PARAMETER[\"False northing\",10000000,LENGTHUNIT[\"metre\",1],ID[\"EPSG\",8807]]],"
            + "CS[Cartesian,2],AXIS[\"easting (X)\",east,ORDER[1],LENGTHUNIT[\"metre\",1]],"
            + "AXIS[\"northing (Y)\",north,ORDER[2],LENGTHUNIT[\"metre\",1]],ID[\"EPSG\",29101]]";

    // proj4js reference point and tolerances for EPSG:29101 (acc xy=-2, ll=3)
    private static final double[] SAD69_LL = {-49.221772553812, -0.34551739237581};
    private static final double[] SAD69_XY = {5531902.134932, 9961660.779347};
    private static final double XY_EPSLN = Math.pow(10, 2);    // 100 m
    private static final double LL_EPSLN = Math.pow(10, -3);   // 0.001 deg

    @BeforeEach
    void setUp() {
        ProjectionRegistry.reset();
        ProjectionRegistry.start();
    }

    @Test
    void testRegistry() {
        assertNotNull(ProjectionRegistry.get("poly"));
        assertNotNull(ProjectionRegistry.get("Polyconic"));
        assertNotNull(ProjectionRegistry.get("American_Polyconic"));
    }

    // ===== Ported from proj4js test/testData.js (SAD69 / Brazil Polyconic) =====

    @Test
    void testDataSad69Wkt1Esri() {
        assertSad69(SAD69_WKT1_ESRI);
    }

    @Test
    void testDataSad69Wkt1Epsg() {
        assertSad69(SAD69_WKT1_EPSG);
    }

    @Test
    void testDataSad69Wkt2() {
        assertSad69(SAD69_WKT2);
    }

    /** Mirrors proj4js: forward = WGS84 -> CRS, inverse = CRS -> WGS84. */
    private void assertSad69(String code) {
        Proj proj = new Proj(code);

        Point xy = Transform.transform(WGS84, proj, new Point(SAD69_LL[0], SAD69_LL[1]));
        assertEquals(SAD69_XY[0], xy.x, XY_EPSLN, "x is close");
        assertEquals(SAD69_XY[1], xy.y, XY_EPSLN, "y is close");

        Point ll = Transform.transform(proj, WGS84, new Point(SAD69_XY[0], SAD69_XY[1]));
        assertEquals(SAD69_LL[0], ll.x, LL_EPSLN, "lng is close");
        assertEquals(SAD69_LL[1], ll.y, LL_EPSLN, "lat is close");
    }

    // ===== Issue #3088: EPSG:5880 SIRGAS 2000 / Brazil Polyconic =====
    // Reference eastings/northings produced with proj4js 2.20.9 (projection only;
    // SIRGAS 2000 and EPSG:5880 share the GRS80 datum, so no datum shift applies).

    private static final String EPSG_5880 =
        "+proj=poly +lat_0=0 +lon_0=-54 +x_0=5000000 +y_0=10000000 "
            + "+ellps=GRS80 +units=m +no_defs";
    private static final double METERS = 0.01; // 1 cm
    private static final double RADIANS = 1e-9;

    @Test
    void testEpsg5880ForwardKnownValues() {
        Proj poly = new Proj(EPSG_5880);

        double[][] cases = {
            {-54, -15, 5000000.000000, 8341010.410652},
            {-47.9, -15.8, 5653463.734227, 8243016.222029},
            {-60, -2, 4332488.696827, 9777630.779596},
            {-54, 0, 5000000.000000, 10000000.000000},
            {-43.2, -22.9, 6107064.064792, 7425917.997228},
        };

        for (double[] c : cases) {
            Point f = poly.forward(new Point(c[0] * Values.D2R, c[1] * Values.D2R));
            assertNotNull(f, "forward null for " + c[0] + "," + c[1]);
            assertEquals(c[2], f.x, METERS, "easting for " + c[0] + "," + c[1]);
            assertEquals(c[3], f.y, METERS, "northing for " + c[0] + "," + c[1]);
        }
    }

    @Test
    void testEpsg5880RoundTrip() {
        Proj poly = new Proj(EPSG_5880);

        double[][] coords = {
            {-54, -15}, {-47.9, -15.8}, {-60, -2}, {-54, 0}, {-43.2, -22.9}, {-38, -3.7},
        };

        for (double[] coord : coords) {
            double lon = coord[0] * Values.D2R;
            double lat = coord[1] * Values.D2R;

            Point forward = poly.forward(new Point(lon, lat));
            assertNotNull(forward, "forward null for " + coord[0] + "," + coord[1]);

            Point inverse = poly.inverse(forward.copy());
            assertNotNull(inverse, "inverse null for " + coord[0] + "," + coord[1]);
            assertEquals(lon, inverse.x, RADIANS, "lon for " + coord[0]);
            assertEquals(lat, inverse.y, RADIANS, "lat for " + coord[1]);
        }
    }

    @Test
    void testEquatorAtCentralMeridian() {
        Proj poly = new Proj(EPSG_5880);

        // On the central meridian at the equator, easting/northing collapse to the
        // false origin (x0, y0).
        Point f = poly.forward(new Point(-54 * Values.D2R, 0));
        assertEquals(5000000.0, f.x, METERS);
        assertEquals(10000000.0, f.y, METERS);
    }
}
