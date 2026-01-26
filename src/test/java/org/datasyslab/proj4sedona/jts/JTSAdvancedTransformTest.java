package org.datasyslab.proj4sedona.jts;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.locationtech.jts.geom.*;
import org.datasyslab.proj4sedona.grid.GridLoader;
import org.datasyslab.proj4sedona.projection.ProjectionRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Advanced JTS transformation tests using various CRS definition formats:
 * - PROJ strings (not just EPSG codes)
 * - PROJJSON strings
 * - Grid files for datum transformations
 */
class JTSAdvancedTransformTest {

    private static final double COORD_TOLERANCE = 1e-6;
    private static final double METER_TOLERANCE = 0.1;
    private static final String TEST_GRIDS_DIR = "src/test/resources/grids";

    private GeometryFactory gf;

    @BeforeAll
    static void setupClass() {
        ProjectionRegistry.start();
    }

    @BeforeEach
    void setup() {
        gf = new GeometryFactory();
    }

    // ==================== PROJ String Tests ====================

    @Test
    @DisplayName("PROJ String: Lambert Conformal Conic with all parameters")
    void testProjStringLambertConformalConic() {
        // NAD83 / California zone 6 - LCC with two standard parallels
        String lccProj = "+proj=lcc +lat_1=33.88333333333333 +lat_2=32.78333333333333 " +
                "+lat_0=32.16666666666666 +lon_0=-116.25 +x_0=2000000 +y_0=500000 " +
                "+datum=NAD83 +units=m +no_defs";

        JTSGeometryTransformer transformer = new JTSGeometryTransformer(
            "+proj=longlat +datum=NAD83",
            lccProj
        );

        // San Diego area point
        Point sanDiego = gf.createPoint(new Coordinate(-117.1611, 32.7157));
        Point result = (Point) transformer.transform(sanDiego);

        assertNotNull(result);
        // Verify we get reasonable easting/northing values
        assertTrue(result.getX() > 1500000 && result.getX() < 2500000,
            "Easting should be around 2M meters");
        assertTrue(result.getY() > 0 && result.getY() < 1000000,
            "Northing should be around 500k meters");

        // Round-trip
        Point restored = (Point) transformer.inverse(result);
        assertEquals(sanDiego.getX(), restored.getX(), COORD_TOLERANCE);
        assertEquals(sanDiego.getY(), restored.getY(), COORD_TOLERANCE);
    }

    @Test
    @DisplayName("PROJ String: Transverse Mercator with custom scale factor")
    void testProjStringTransverseMercator() {
        // Custom TM projection
        String tmProj = "+proj=tmerc +lat_0=0 +lon_0=9 +k=0.9996 +x_0=500000 +y_0=0 " +
                "+datum=WGS84 +units=m +no_defs";

        JTSGeometryTransformer transformer = new JTSGeometryTransformer(
            "+proj=longlat +datum=WGS84",
            tmProj
        );

        // Munich area
        Point munich = gf.createPoint(new Coordinate(11.5820, 48.1351));
        Point result = (Point) transformer.transform(munich);

        assertNotNull(result);
        assertTrue(result.getX() > 0);
        assertTrue(result.getY() > 0);

        // Round-trip
        Point restored = (Point) transformer.inverse(result);
        assertEquals(munich.getX(), restored.getX(), COORD_TOLERANCE);
        assertEquals(munich.getY(), restored.getY(), COORD_TOLERANCE);
    }

    @Test
    @DisplayName("PROJ String: Albers Equal Area Conic")
    void testProjStringAlbersEqualArea() {
        // CONUS Albers Equal Area
        String albersProj = "+proj=aea +lat_1=29.5 +lat_2=45.5 +lat_0=37.5 +lon_0=-96 " +
                "+x_0=0 +y_0=0 +datum=NAD83 +units=m +no_defs";

        JTSGeometryTransformer transformer = new JTSGeometryTransformer(
            "+proj=longlat +datum=NAD83",
            albersProj
        );

        // Kansas City (near center of CONUS)
        Point kansasCity = gf.createPoint(new Coordinate(-94.5786, 39.0997));
        Point result = (Point) transformer.transform(kansasCity);

        assertNotNull(result);

        // Round-trip
        Point restored = (Point) transformer.inverse(result);
        assertEquals(kansasCity.getX(), restored.getX(), COORD_TOLERANCE);
        assertEquals(kansasCity.getY(), restored.getY(), COORD_TOLERANCE);
    }

    @Test
    @DisplayName("PROJ String: State Plane with US Survey Feet")
    void testProjStringStatePlaneUsSurveyFeet() {
        // California State Plane Zone 6 in US Survey Feet
        String statePlaneProj = "+proj=lcc +lat_1=33.88333333333333 +lat_2=32.78333333333333 " +
                "+lat_0=32.16666666666666 +lon_0=-116.25 +x_0=2000000.0001016 +y_0=500000.0001016 " +
                "+datum=NAD83 +units=us-ft +no_defs";

        JTSGeometryTransformer transformer = new JTSGeometryTransformer(
            "+proj=longlat +datum=NAD83",
            statePlaneProj
        );

        Point sanDiego = gf.createPoint(new Coordinate(-117.1611, 32.7157));
        Point result = (Point) transformer.transform(sanDiego);

        assertNotNull(result);
        // US Survey Feet values should be larger than meters
        assertTrue(result.getX() > 5000000, "X in US Survey Feet should be > 5M");
    }

    @Test
    @DisplayName("PROJ String: Mercator with spherical Earth")
    void testProjStringMercatorSpherical() {
        // Web Mercator style with spherical Earth (a=b)
        String sphereMerc = "+proj=merc +a=6378137 +b=6378137 +lat_ts=0 +lon_0=0 " +
                "+x_0=0 +y_0=0 +k=1 +units=m +no_defs";

        JTSGeometryTransformer transformer = new JTSGeometryTransformer(
            "+proj=longlat +datum=WGS84",
            sphereMerc
        );

        // Test at equator/prime meridian
        Point origin = gf.createPoint(new Coordinate(0, 0));
        Point result = (Point) transformer.transform(origin);

        assertEquals(0, result.getX(), METER_TOLERANCE);
        assertEquals(0, result.getY(), METER_TOLERANCE);

        // Test at 180 degrees
        Point dateline = gf.createPoint(new Coordinate(180, 0));
        Point datelineResult = (Point) transformer.transform(dateline);
        assertEquals(20037508.34, Math.abs(datelineResult.getX()), 1.0);
    }

    @Test
    @DisplayName("PROJ String: Datum transformation with +towgs84")
    void testProjStringDatumTransform() {
        // ED50 to WGS84 with 7-parameter Helmert transformation
        String ed50 = "+proj=longlat +ellps=intl +towgs84=-87,-98,-121,0,0,0,0 +no_defs";
        String wgs84 = "+proj=longlat +datum=WGS84 +no_defs";

        JTSGeometryTransformer transformer = new JTSGeometryTransformer(ed50, wgs84);

        // Point in Europe (Paris area)
        Point paris = gf.createPoint(new Coordinate(2.3522, 48.8566));
        Point result = (Point) transformer.transform(paris);

        assertNotNull(result);
        // The transformation should shift the coordinates slightly
        // ED50 is offset from WGS84 by roughly 100-200 meters
        assertNotEquals(paris.getX(), result.getX(), "Longitude should shift");
        assertNotEquals(paris.getY(), result.getY(), "Latitude should shift");

        // But the shift should be small (< 0.01 degrees)
        assertTrue(Math.abs(paris.getX() - result.getX()) < 0.01);
        assertTrue(Math.abs(paris.getY() - result.getY()) < 0.01);
    }

    @Test
    @DisplayName("PROJ String: UTM zone with explicit parameters")
    void testProjStringUtmExplicit() {
        // UTM Zone 10N with explicit parameters (not using +proj=utm)
        String utmExplicit = "+proj=tmerc +lat_0=0 +lon_0=-123 +k=0.9996 " +
                "+x_0=500000 +y_0=0 +datum=WGS84 +units=m +no_defs";

        JTSGeometryTransformer transformer = new JTSGeometryTransformer(
            "+proj=longlat +datum=WGS84",
            utmExplicit
        );

        // San Francisco
        Point sf = gf.createPoint(new Coordinate(-122.4194, 37.7749));
        Point result = (Point) transformer.transform(sf);

        assertNotNull(result);
        // Should get typical UTM coordinates
        assertTrue(result.getX() > 400000 && result.getX() < 600000);
        assertTrue(result.getY() > 4000000 && result.getY() < 5000000);
    }

    @Test
    @DisplayName("PROJ String: Transform polygon with complex projection")
    void testProjStringPolygonTransform() {
        String albersProj = "+proj=aea +lat_1=29.5 +lat_2=45.5 +lat_0=37.5 +lon_0=-96 " +
                "+x_0=0 +y_0=0 +datum=NAD83 +units=m +no_defs";

        JTSGeometryTransformer transformer = new JTSGeometryTransformer(
            "+proj=longlat +datum=NAD83",
            albersProj
        );

        // Colorado boundary (simplified)
        Coordinate[] colorado = new Coordinate[] {
            new Coordinate(-109.05, 41.0),   // NW
            new Coordinate(-102.05, 41.0),   // NE
            new Coordinate(-102.05, 37.0),   // SE
            new Coordinate(-109.05, 37.0),   // SW
            new Coordinate(-109.05, 41.0)    // close
        };
        Polygon polygon = gf.createPolygon(colorado);
        Polygon result = (Polygon) transformer.transform(polygon);

        assertNotNull(result);
        assertTrue(result.isValid());
        assertTrue(result.getArea() > 0);

        // Round-trip
        Polygon restored = (Polygon) transformer.inverse(result);
        assertEquals(polygon.getNumPoints(), restored.getNumPoints());
    }

    // ==================== PROJJSON Tests ====================

    @Test
    @DisplayName("PROJJSON: Geographic CRS transformation")
    void testProjJsonGeographicCRS() {
        String wgs84Json = "{" +
            "\"type\": \"GeographicCRS\"," +
            "\"name\": \"WGS 84\"," +
            "\"datum\": {" +
                "\"type\": \"GeodeticReferenceFrame\"," +
                "\"name\": \"World Geodetic System 1984\"," +
                "\"ellipsoid\": {" +
                    "\"name\": \"WGS 84\"," +
                    "\"semi_major_axis\": 6378137," +
                    "\"inverse_flattening\": 298.257223563" +
                "}" +
            "}" +
        "}";

        // WGS84 (PROJJSON) to Web Mercator (PROJ string)
        JTSGeometryTransformer transformer = new JTSGeometryTransformer(
            wgs84Json,
            "+proj=merc +a=6378137 +b=6378137 +lat_ts=0 +lon_0=0 +x_0=0 +y_0=0 +k=1 +units=m"
        );

        Point point = gf.createPoint(new Coordinate(-122.4194, 37.7749));
        Point result = (Point) transformer.transform(point);

        assertNotNull(result);
        assertTrue(result.getX() < 0, "X should be negative for western longitude");
        assertTrue(result.getY() > 0, "Y should be positive for northern latitude");
    }

    @Test
    @DisplayName("PROJJSON: Projected CRS transformation")
    void testProjJsonProjectedCRS() {
        String utmJson = "{" +
            "\"type\": \"ProjectedCRS\"," +
            "\"name\": \"WGS 84 / UTM zone 10N\"," +
            "\"base_crs\": {" +
                "\"type\": \"GeographicCRS\"," +
                "\"datum\": {" +
                    "\"ellipsoid\": {" +
                        "\"semi_major_axis\": 6378137," +
                        "\"inverse_flattening\": 298.257223563" +
                    "}" +
                "}" +
            "}," +
            "\"conversion\": {" +
                "\"method\": {\"name\": \"Transverse Mercator\"}," +
                "\"parameters\": [" +
                    "{\"name\": \"Latitude of natural origin\", \"value\": 0, \"unit\": {\"conversion_factor\": 0.0174532925199433}}," +
                    "{\"name\": \"Longitude of natural origin\", \"value\": -123, \"unit\": {\"conversion_factor\": 0.0174532925199433}}," +
                    "{\"name\": \"Scale factor at natural origin\", \"value\": 0.9996}," +
                    "{\"name\": \"False easting\", \"value\": 500000}," +
                    "{\"name\": \"False northing\", \"value\": 0}" +
                "]" +
            "}" +
        "}";

        // WGS84 (PROJ) to UTM (PROJJSON)
        JTSGeometryTransformer transformer = new JTSGeometryTransformer(
            "+proj=longlat +datum=WGS84",
            utmJson
        );

        Point sf = gf.createPoint(new Coordinate(-122.4194, 37.7749));
        Point result = (Point) transformer.transform(sf);

        assertNotNull(result);
        // UTM Zone 10N for San Francisco
        assertTrue(result.getX() > 500000 && result.getX() < 600000,
            "Easting should be around 550k");
        assertTrue(result.getY() > 4000000 && result.getY() < 5000000,
            "Northing should be around 4.18M");
    }

    @Test
    @DisplayName("PROJJSON: Both source and target as PROJJSON")
    void testProjJsonBothEnds() {
        String wgs84Json = "{" +
            "\"type\": \"GeographicCRS\"," +
            "\"name\": \"WGS 84\"," +
            "\"datum\": {" +
                "\"ellipsoid\": {" +
                    "\"semi_major_axis\": 6378137," +
                    "\"inverse_flattening\": 298.257223563" +
                "}" +
            "}" +
        "}";

        String mercJson = "{" +
            "\"type\": \"ProjectedCRS\"," +
            "\"name\": \"WGS 84 / Pseudo-Mercator\"," +
            "\"base_crs\": {" +
                "\"type\": \"GeographicCRS\"," +
                "\"datum\": {" +
                    "\"ellipsoid\": {" +
                        "\"semi_major_axis\": 6378137," +
                        "\"inverse_flattening\": 298.257223563" +
                    "}" +
                "}" +
            "}," +
            "\"conversion\": {" +
                "\"method\": {\"name\": \"Mercator\"}," +
                "\"parameters\": [" +
                    "{\"name\": \"Latitude of natural origin\", \"value\": 0}," +
                    "{\"name\": \"Longitude of natural origin\", \"value\": 0}," +
                    "{\"name\": \"Scale factor at natural origin\", \"value\": 1}," +
                    "{\"name\": \"False easting\", \"value\": 0}," +
                    "{\"name\": \"False northing\", \"value\": 0}" +
                "]" +
            "}" +
        "}";

        JTSGeometryTransformer transformer = new JTSGeometryTransformer(wgs84Json, mercJson);

        Point origin = gf.createPoint(new Coordinate(0, 0));
        Point result = (Point) transformer.transform(origin);

        assertNotNull(result);
        assertEquals(0, result.getX(), METER_TOLERANCE);
        assertEquals(0, result.getY(), METER_TOLERANCE);
    }

    @Test
    @DisplayName("PROJJSON: Round-trip transformation")
    void testProjJsonRoundTrip() {
        String geogJson = "{" +
            "\"type\": \"GeographicCRS\"," +
            "\"datum\": {" +
                "\"ellipsoid\": {" +
                    "\"semi_major_axis\": 6378137," +
                    "\"inverse_flattening\": 298.257223563" +
                "}" +
            "}" +
        "}";

        JTSGeometryTransformer transformer = new JTSGeometryTransformer(
            geogJson,
            "+proj=merc +datum=WGS84"
        );

        Point original = gf.createPoint(new Coordinate(-74.006, 40.7128));
        Point projected = (Point) transformer.transform(original);
        Point restored = (Point) transformer.inverse(projected);

        assertEquals(original.getX(), restored.getX(), COORD_TOLERANCE);
        assertEquals(original.getY(), restored.getY(), COORD_TOLERANCE);
    }

    // ==================== Grid File Tests ====================

    @Test
    @DisplayName("Grid: NAD27 to NAD83 using CONUS grid (if available)")
    void testGridNad27ToNad83() throws IOException {
        Path gridPath = Path.of(TEST_GRIDS_DIR, "us_noaa_conus.tif");
        if (!Files.exists(gridPath)) {
            System.out.println("Skipping NAD grid test - grid file not found: " + gridPath);
            return;
        }

        // Load the grid
        GridLoader.loadFile("conus", gridPath);

        // NAD27 with grid shift to NAD83
        String nad27 = "+proj=longlat +datum=NAD27 +nadgrids=@conus +no_defs";
        String nad83 = "+proj=longlat +datum=NAD83 +no_defs";

        JTSGeometryTransformer transformer = new JTSGeometryTransformer(nad27, nad83);

        // Washington DC area
        Point dc = gf.createPoint(new Coordinate(-77.0369, 38.9072));
        Point result = (Point) transformer.transform(dc);

        assertNotNull(result);
        // NAD27 to NAD83 shift is typically small (< 100 meters / ~0.001 degrees)
        assertTrue(Math.abs(dc.getX() - result.getX()) < 0.01,
            "Longitude shift should be small");
        assertTrue(Math.abs(dc.getY() - result.getY()) < 0.01,
            "Latitude shift should be small");

        System.out.printf("NAD27->NAD83 shift at DC: dLon=%.6f, dLat=%.6f degrees%n",
            result.getX() - dc.getX(), result.getY() - dc.getY());
    }

    @Test
    @DisplayName("Grid: Canada NTv2 transformation (if available)")
    void testGridCanadaNtv2() throws IOException {
        Path gridPath = Path.of(TEST_GRIDS_DIR, "ca_nrc_ntv2_0.tif");
        if (!Files.exists(gridPath)) {
            System.out.println("Skipping Canada grid test - grid file not found: " + gridPath);
            return;
        }

        // Load the grid
        GridLoader.loadFile("ntv2_0", gridPath);

        // NAD27 Canada to NAD83
        String nad27Canada = "+proj=longlat +ellps=clrk66 +nadgrids=@ntv2_0 +no_defs";
        String nad83 = "+proj=longlat +datum=NAD83 +no_defs";

        JTSGeometryTransformer transformer = new JTSGeometryTransformer(nad27Canada, nad83);

        // Toronto area
        Point toronto = gf.createPoint(new Coordinate(-79.3832, 43.6532));
        Point result = (Point) transformer.transform(toronto);

        assertNotNull(result);
        // Grid shift should be small
        assertTrue(Math.abs(toronto.getX() - result.getX()) < 0.01);
        assertTrue(Math.abs(toronto.getY() - result.getY()) < 0.01);

        System.out.printf("NAD27->NAD83 Canada shift at Toronto: dLon=%.6f, dLat=%.6f degrees%n",
            result.getX() - toronto.getX(), result.getY() - toronto.getY());
    }

    @Test
    @DisplayName("Grid: Transform polygon through grid shift")
    void testGridPolygonTransform() throws IOException {
        Path gridPath = Path.of(TEST_GRIDS_DIR, "us_noaa_conus.tif");
        if (!Files.exists(gridPath)) {
            System.out.println("Skipping polygon grid test - grid file not found");
            return;
        }

        GridLoader.loadFile("conus", gridPath);

        String nad27 = "+proj=longlat +datum=NAD27 +nadgrids=@conus +no_defs";
        String nad83 = "+proj=longlat +datum=NAD83 +no_defs";

        JTSGeometryTransformer transformer = new JTSGeometryTransformer(nad27, nad83);

        // Small polygon in continental US
        Coordinate[] coords = new Coordinate[] {
            new Coordinate(-77.1, 38.8),
            new Coordinate(-76.9, 38.8),
            new Coordinate(-76.9, 39.0),
            new Coordinate(-77.1, 39.0),
            new Coordinate(-77.1, 38.8)
        };
        Polygon polygon = gf.createPolygon(coords);
        Polygon result = (Polygon) transformer.transform(polygon);

        assertNotNull(result);
        assertTrue(result.isValid());
        assertEquals(polygon.getNumPoints(), result.getNumPoints());
    }

    // ==================== Mixed Format Tests ====================

    @Test
    @DisplayName("Mixed: EPSG to PROJ string")
    void testMixedEpsgToProj() {
        JTSGeometryTransformer transformer = new JTSGeometryTransformer(
            "EPSG:4326",
            "+proj=merc +a=6378137 +b=6378137 +lat_ts=0 +lon_0=0 +x_0=0 +y_0=0 +k=1 +units=m"
        );

        Point point = gf.createPoint(new Coordinate(0, 0));
        Point result = (Point) transformer.transform(point);

        assertEquals(0, result.getX(), METER_TOLERANCE);
        assertEquals(0, result.getY(), METER_TOLERANCE);
    }

    @Test
    @DisplayName("Mixed: PROJ string to EPSG")
    void testMixedProjToEpsg() {
        JTSGeometryTransformer transformer = new JTSGeometryTransformer(
            "+proj=longlat +datum=WGS84",
            "EPSG:3857"
        );

        Point point = gf.createPoint(new Coordinate(0, 0));
        Point result = (Point) transformer.transform(point);

        assertEquals(0, result.getX(), METER_TOLERANCE);
        assertEquals(0, result.getY(), METER_TOLERANCE);
    }

    @Test
    @DisplayName("Mixed: WKT to PROJJSON")
    void testMixedWktToProjJson() {
        String wkt = "GEOGCS[\"WGS 84\"," +
                "DATUM[\"WGS_1984\"," +
                "SPHEROID[\"WGS 84\",6378137,298.257223563]]," +
                "PRIMEM[\"Greenwich\",0]," +
                "UNIT[\"degree\",0.0174532925199433]]";

        String projJson = "{" +
            "\"type\": \"ProjectedCRS\"," +
            "\"base_crs\": {" +
                "\"type\": \"GeographicCRS\"," +
                "\"datum\": {\"ellipsoid\": {\"semi_major_axis\": 6378137, \"inverse_flattening\": 298.257223563}}" +
            "}," +
            "\"conversion\": {" +
                "\"method\": {\"name\": \"Mercator\"}," +
                "\"parameters\": []" +
            "}" +
        "}";

        JTSGeometryTransformer transformer = new JTSGeometryTransformer(wkt, projJson);

        Point point = gf.createPoint(new Coordinate(10, 20));
        Point result = (Point) transformer.transform(point);

        assertNotNull(result);
        assertTrue(result.getX() > 0);
        assertTrue(result.getY() > 0);
    }

    // ==================== Performance Sanity Tests ====================

    @Test
    @DisplayName("Performance: Batch transformation with PROJ strings")
    void testBatchTransformPerformance() {
        JTSGeometryTransformer transformer = new JTSGeometryTransformer(
            "+proj=longlat +datum=WGS84",
            "+proj=utm +zone=10 +datum=WGS84"
        );

        // Create 1000 points
        Point[] points = new Point[1000];
        for (int i = 0; i < 1000; i++) {
            double lon = -125 + (i % 10) * 0.5;
            double lat = 35 + (i / 10) * 0.05;
            points[i] = gf.createPoint(new Coordinate(lon, lat));
        }

        long start = System.nanoTime();
        for (Point point : points) {
            transformer.transform(point);
        }
        long elapsed = System.nanoTime() - start;

        double msPerPoint = elapsed / 1_000_000.0 / 1000;
        System.out.printf("Batch transform: %.4f ms/point (1000 points)%n", msPerPoint);

        // Should be reasonably fast (< 1ms per point)
        assertTrue(msPerPoint < 1.0, "Transformation should be < 1ms per point");
    }
}
