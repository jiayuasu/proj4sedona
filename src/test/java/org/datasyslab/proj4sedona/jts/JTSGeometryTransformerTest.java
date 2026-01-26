package org.datasyslab.proj4sedona.jts;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.locationtech.jts.geom.*;
import org.datasyslab.proj4sedona.Proj4;
import org.datasyslab.proj4sedona.core.Proj;
import org.datasyslab.proj4sedona.transform.Converter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for JTS Geometry CRS transformations.
 * 
 * Tests coordinate transformations for all JTS geometry types:
 * - Point
 * - LineString
 * - LinearRing
 * - Polygon
 * - MultiPoint
 * - MultiLineString
 * - MultiPolygon
 * - GeometryCollection
 */
class JTSGeometryTransformerTest {

    private static final double COORD_TOLERANCE = 1e-6;     // For lon/lat degrees
    private static final double METER_TOLERANCE = 0.01;     // 1 cm tolerance for projected coords

    private GeometryFactory gf;
    private JTSGeometryTransformer wgs84ToWebMercator;
    private JTSGeometryTransformer wgs84ToUtm;

    // Common CRS definitions
    private static final String WGS84 = "+proj=longlat +datum=WGS84";
    private static final String WEB_MERCATOR = "+proj=merc +a=6378137 +b=6378137 +lat_ts=0 +lon_0=0 +x_0=0 +y_0=0 +k=1 +units=m +no_defs";
    private static final String UTM_ZONE_10N = "+proj=utm +zone=10 +datum=WGS84 +units=m +no_defs";
    private static final String UTM_ZONE_32N = "+proj=utm +zone=32 +datum=WGS84 +units=m +no_defs";

    @BeforeAll
    static void setupClass() {
        // Ensure projections are registered
        org.datasyslab.proj4sedona.projection.ProjectionRegistry.start();
    }

    @BeforeEach
    void setup() {
        gf = new GeometryFactory();
        wgs84ToWebMercator = new JTSGeometryTransformer(WGS84, WEB_MERCATOR);
        wgs84ToUtm = new JTSGeometryTransformer(WGS84, UTM_ZONE_10N);
    }

    // ==================== Point Tests ====================

    @Test
    @DisplayName("Transform Point: WGS84 (0,0) to Web Mercator (0,0)")
    void testTransformPointOrigin() {
        Point point = gf.createPoint(new Coordinate(0, 0));
        Point result = (Point) wgs84ToWebMercator.transform(point);

        assertNotNull(result);
        assertEquals(0, result.getX(), METER_TOLERANCE);
        assertEquals(0, result.getY(), METER_TOLERANCE);
    }

    @Test
    @DisplayName("Transform Point: San Francisco to Web Mercator")
    void testTransformPointSanFrancisco() {
        // San Francisco: lon=-122.4194, lat=37.7749
        Point sanFrancisco = gf.createPoint(new Coordinate(-122.4194, 37.7749));
        Point result = (Point) wgs84ToWebMercator.transform(sanFrancisco);

        assertNotNull(result);
        // Expected Web Mercator coords (approximately)
        // X should be negative (west of prime meridian)
        assertTrue(result.getX() < 0, "X should be negative for western longitude");
        // Y should be positive (north of equator)
        assertTrue(result.getY() > 0, "Y should be positive for northern latitude");

        // More precise checks based on known values
        assertEquals(-13627665.27, result.getX(), 10.0);  // ~10m tolerance
        assertEquals(4547675.35, result.getY(), 10.0);    // ~10m tolerance
    }

    @Test
    @DisplayName("Transform Point: Tokyo to Web Mercator")
    void testTransformPointTokyo() {
        // Tokyo: lon=139.6917, lat=35.6895
        Point tokyo = gf.createPoint(new Coordinate(139.6917, 35.6895));
        Point result = (Point) wgs84ToWebMercator.transform(tokyo);

        assertNotNull(result);
        assertTrue(result.getX() > 0, "X should be positive for eastern longitude");
        assertTrue(result.getY() > 0, "Y should be positive for northern latitude");
    }

    @Test
    @DisplayName("Transform Point: Sydney (southern hemisphere)")
    void testTransformPointSydney() {
        // Sydney: lon=151.2093, lat=-33.8688
        Point sydney = gf.createPoint(new Coordinate(151.2093, -33.8688));
        Point result = (Point) wgs84ToWebMercator.transform(sydney);

        assertNotNull(result);
        assertTrue(result.getX() > 0, "X should be positive for eastern longitude");
        assertTrue(result.getY() < 0, "Y should be negative for southern latitude");
    }

    @Test
    @DisplayName("Transform Point with Z coordinate")
    void testTransformPointWithZ() {
        Point point = gf.createPoint(new Coordinate(-122.4194, 37.7749, 100));
        Point result = (Point) wgs84ToWebMercator.transform(point);

        assertNotNull(result);
        // Z coordinate should be preserved
        assertEquals(100, result.getCoordinate().getZ(), COORD_TOLERANCE);
    }

    @Test
    @DisplayName("Transform empty Point")
    void testTransformEmptyPoint() {
        Point emptyPoint = gf.createPoint();
        Point result = (Point) wgs84ToWebMercator.transform(emptyPoint);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Transform null Point returns null")
    void testTransformNullPoint() {
        Point result = wgs84ToWebMercator.transformPoint(null);
        assertNull(result);
    }

    // ==================== LineString Tests ====================

    @Test
    @DisplayName("Transform LineString: simple path")
    void testTransformLineString() {
        Coordinate[] coords = new Coordinate[] {
            new Coordinate(-122.5, 37.7),
            new Coordinate(-122.4, 37.8),
            new Coordinate(-122.3, 37.9)
        };
        LineString line = gf.createLineString(coords);
        LineString result = (LineString) wgs84ToWebMercator.transform(line);

        assertNotNull(result);
        assertEquals(3, result.getNumPoints());

        // First point should be transformed correctly
        assertTrue(result.getCoordinateN(0).getX() < 0, "X should be negative");
        assertTrue(result.getCoordinateN(0).getY() > 0, "Y should be positive");
    }

    @Test
    @DisplayName("Transform LineString: California coast")
    void testTransformLineStringCoast() {
        // A line along the California coast
        Coordinate[] coords = new Coordinate[] {
            new Coordinate(-124.2, 41.8),   // Near Oregon border
            new Coordinate(-123.0, 38.5),   // Mendocino
            new Coordinate(-122.4, 37.8),   // San Francisco
            new Coordinate(-121.9, 36.6),   // Monterey
            new Coordinate(-117.2, 32.7)    // San Diego
        };
        LineString line = gf.createLineString(coords);
        LineString result = (LineString) wgs84ToWebMercator.transform(line);

        assertNotNull(result);
        assertEquals(5, result.getNumPoints());

        // All X coords should be negative (west coast)
        for (int i = 0; i < result.getNumPoints(); i++) {
            assertTrue(result.getCoordinateN(i).getX() < 0,
                "Point " + i + " X should be negative");
        }
    }

    @Test
    @DisplayName("Transform empty LineString")
    void testTransformEmptyLineString() {
        LineString emptyLine = gf.createLineString();
        LineString result = (LineString) wgs84ToWebMercator.transform(emptyLine);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== Polygon Tests ====================

    @Test
    @DisplayName("Transform Polygon: simple rectangle")
    void testTransformPolygonRectangle() {
        // Rectangle around San Francisco
        Coordinate[] coords = new Coordinate[] {
            new Coordinate(-122.5, 37.7),
            new Coordinate(-122.3, 37.7),
            new Coordinate(-122.3, 37.9),
            new Coordinate(-122.5, 37.9),
            new Coordinate(-122.5, 37.7)  // close the ring
        };
        Polygon polygon = gf.createPolygon(coords);
        Polygon result = (Polygon) wgs84ToWebMercator.transform(polygon);

        assertNotNull(result);
        assertTrue(result.isValid(), "Transformed polygon should be valid");
        assertEquals(5, result.getExteriorRing().getNumPoints());

        // Check area is positive (not inverted)
        assertTrue(result.getArea() > 0, "Polygon should have positive area");
    }

    @Test
    @DisplayName("Transform Polygon: with hole")
    void testTransformPolygonWithHole() {
        // Outer ring
        Coordinate[] outer = new Coordinate[] {
            new Coordinate(-122.5, 37.7),
            new Coordinate(-122.3, 37.7),
            new Coordinate(-122.3, 37.9),
            new Coordinate(-122.5, 37.9),
            new Coordinate(-122.5, 37.7)
        };

        // Inner ring (hole) - must be in opposite winding order
        Coordinate[] inner = new Coordinate[] {
            new Coordinate(-122.45, 37.75),
            new Coordinate(-122.45, 37.85),
            new Coordinate(-122.35, 37.85),
            new Coordinate(-122.35, 37.75),
            new Coordinate(-122.45, 37.75)
        };

        LinearRing shell = gf.createLinearRing(outer);
        LinearRing hole = gf.createLinearRing(inner);
        Polygon polygon = gf.createPolygon(shell, new LinearRing[]{hole});

        Polygon result = (Polygon) wgs84ToWebMercator.transform(polygon);

        assertNotNull(result);
        assertEquals(1, result.getNumInteriorRing(), "Should have 1 hole");
        assertEquals(5, result.getExteriorRing().getNumPoints());
        assertEquals(5, result.getInteriorRingN(0).getNumPoints());
    }

    @Test
    @DisplayName("Transform Polygon: triangle")
    void testTransformPolygonTriangle() {
        Coordinate[] coords = new Coordinate[] {
            new Coordinate(0, 0),
            new Coordinate(1, 0),
            new Coordinate(0.5, 1),
            new Coordinate(0, 0)
        };
        Polygon triangle = gf.createPolygon(coords);
        Polygon result = (Polygon) wgs84ToWebMercator.transform(triangle);

        assertNotNull(result);
        assertEquals(4, result.getExteriorRing().getNumPoints());
        assertTrue(result.getArea() > 0);
    }

    @Test
    @DisplayName("Transform empty Polygon")
    void testTransformEmptyPolygon() {
        Polygon emptyPolygon = gf.createPolygon();
        Polygon result = (Polygon) wgs84ToWebMercator.transform(emptyPolygon);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== MultiPoint Tests ====================

    @Test
    @DisplayName("Transform MultiPoint: US cities")
    void testTransformMultiPoint() {
        Point[] cities = new Point[] {
            gf.createPoint(new Coordinate(-122.4194, 37.7749)),  // San Francisco
            gf.createPoint(new Coordinate(-118.2437, 34.0522)),  // Los Angeles
            gf.createPoint(new Coordinate(-74.0060, 40.7128)),   // New York
            gf.createPoint(new Coordinate(-87.6298, 41.8781))    // Chicago
        };
        MultiPoint multiPoint = gf.createMultiPoint(cities);
        MultiPoint result = (MultiPoint) wgs84ToWebMercator.transform(multiPoint);

        assertNotNull(result);
        assertEquals(4, result.getNumGeometries());

        // All transformed points should have positive Y (northern hemisphere)
        for (int i = 0; i < result.getNumGeometries(); i++) {
            Point p = (Point) result.getGeometryN(i);
            assertTrue(p.getY() > 0, "City " + i + " should be in northern hemisphere");
        }
    }

    @Test
    @DisplayName("Transform empty MultiPoint")
    void testTransformEmptyMultiPoint() {
        MultiPoint empty = gf.createMultiPoint();
        MultiPoint result = (MultiPoint) wgs84ToWebMercator.transform(empty);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== MultiLineString Tests ====================

    @Test
    @DisplayName("Transform MultiLineString: road network")
    void testTransformMultiLineString() {
        LineString[] roads = new LineString[] {
            gf.createLineString(new Coordinate[] {
                new Coordinate(-122.5, 37.7),
                new Coordinate(-122.4, 37.8)
            }),
            gf.createLineString(new Coordinate[] {
                new Coordinate(-122.4, 37.8),
                new Coordinate(-122.3, 37.7)
            }),
            gf.createLineString(new Coordinate[] {
                new Coordinate(-122.4, 37.8),
                new Coordinate(-122.4, 37.9)
            })
        };
        MultiLineString network = gf.createMultiLineString(roads);
        MultiLineString result = (MultiLineString) wgs84ToWebMercator.transform(network);

        assertNotNull(result);
        assertEquals(3, result.getNumGeometries());

        for (int i = 0; i < result.getNumGeometries(); i++) {
            LineString line = (LineString) result.getGeometryN(i);
            assertEquals(2, line.getNumPoints());
        }
    }

    @Test
    @DisplayName("Transform empty MultiLineString")
    void testTransformEmptyMultiLineString() {
        MultiLineString empty = gf.createMultiLineString();
        MultiLineString result = (MultiLineString) wgs84ToWebMercator.transform(empty);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== MultiPolygon Tests ====================

    @Test
    @DisplayName("Transform MultiPolygon: buildings")
    void testTransformMultiPolygon() {
        Polygon[] buildings = new Polygon[] {
            gf.createPolygon(new Coordinate[] {
                new Coordinate(-122.4, 37.78),
                new Coordinate(-122.39, 37.78),
                new Coordinate(-122.39, 37.79),
                new Coordinate(-122.4, 37.79),
                new Coordinate(-122.4, 37.78)
            }),
            gf.createPolygon(new Coordinate[] {
                new Coordinate(-122.41, 37.78),
                new Coordinate(-122.405, 37.78),
                new Coordinate(-122.405, 37.785),
                new Coordinate(-122.41, 37.785),
                new Coordinate(-122.41, 37.78)
            })
        };
        MultiPolygon multiPoly = gf.createMultiPolygon(buildings);
        MultiPolygon result = (MultiPolygon) wgs84ToWebMercator.transform(multiPoly);

        assertNotNull(result);
        assertEquals(2, result.getNumGeometries());

        for (int i = 0; i < result.getNumGeometries(); i++) {
            Polygon poly = (Polygon) result.getGeometryN(i);
            assertTrue(poly.getArea() > 0, "Polygon " + i + " should have positive area");
        }
    }

    @Test
    @DisplayName("Transform empty MultiPolygon")
    void testTransformEmptyMultiPolygon() {
        MultiPolygon empty = gf.createMultiPolygon();
        MultiPolygon result = (MultiPolygon) wgs84ToWebMercator.transform(empty);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== GeometryCollection Tests ====================

    @Test
    @DisplayName("Transform GeometryCollection: mixed types")
    void testTransformGeometryCollection() {
        Geometry[] geoms = new Geometry[] {
            gf.createPoint(new Coordinate(-122.4, 37.8)),
            gf.createLineString(new Coordinate[] {
                new Coordinate(-122.5, 37.7),
                new Coordinate(-122.3, 37.9)
            }),
            gf.createPolygon(new Coordinate[] {
                new Coordinate(-122.42, 37.78),
                new Coordinate(-122.38, 37.78),
                new Coordinate(-122.38, 37.82),
                new Coordinate(-122.42, 37.82),
                new Coordinate(-122.42, 37.78)
            })
        };
        GeometryCollection collection = gf.createGeometryCollection(geoms);
        GeometryCollection result = (GeometryCollection) wgs84ToWebMercator.transform(collection);

        assertNotNull(result);
        assertEquals(3, result.getNumGeometries());

        assertTrue(result.getGeometryN(0) instanceof Point);
        assertTrue(result.getGeometryN(1) instanceof LineString);
        assertTrue(result.getGeometryN(2) instanceof Polygon);
    }

    @Test
    @DisplayName("Transform empty GeometryCollection")
    void testTransformEmptyGeometryCollection() {
        GeometryCollection empty = gf.createGeometryCollection();
        GeometryCollection result = (GeometryCollection) wgs84ToWebMercator.transform(empty);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== Round-trip Tests ====================

    @Test
    @DisplayName("Round-trip Point: WGS84 -> Web Mercator -> WGS84")
    void testRoundTripPoint() {
        Point original = gf.createPoint(new Coordinate(-122.4194, 37.7749));

        // Forward transformation
        Point projected = (Point) wgs84ToWebMercator.transform(original);

        // Inverse transformation
        JTSGeometryTransformer webMercatorToWgs84 = new JTSGeometryTransformer(WEB_MERCATOR, WGS84);
        Point restored = (Point) webMercatorToWgs84.transform(projected);

        assertNotNull(restored);
        assertEquals(original.getX(), restored.getX(), COORD_TOLERANCE);
        assertEquals(original.getY(), restored.getY(), COORD_TOLERANCE);
    }

    @Test
    @DisplayName("Round-trip Polygon: WGS84 -> Web Mercator -> WGS84")
    void testRoundTripPolygon() {
        Coordinate[] coords = new Coordinate[] {
            new Coordinate(-122.5, 37.7),
            new Coordinate(-122.3, 37.7),
            new Coordinate(-122.3, 37.9),
            new Coordinate(-122.5, 37.9),
            new Coordinate(-122.5, 37.7)
        };
        Polygon original = gf.createPolygon(coords);

        // Forward transformation
        Polygon projected = (Polygon) wgs84ToWebMercator.transform(original);

        // Inverse transformation
        JTSGeometryTransformer webMercatorToWgs84 = new JTSGeometryTransformer(WEB_MERCATOR, WGS84);
        Polygon restored = (Polygon) webMercatorToWgs84.transform(projected);

        assertNotNull(restored);
        assertEquals(original.getNumPoints(), restored.getNumPoints());

        // Check all coordinates match
        Coordinate[] origCoords = original.getCoordinates();
        Coordinate[] restoredCoords = restored.getCoordinates();
        for (int i = 0; i < origCoords.length; i++) {
            assertEquals(origCoords[i].getX(), restoredCoords[i].getX(), COORD_TOLERANCE,
                "X mismatch at index " + i);
            assertEquals(origCoords[i].getY(), restoredCoords[i].getY(), COORD_TOLERANCE,
                "Y mismatch at index " + i);
        }
    }

    @Test
    @DisplayName("Round-trip using inverse() method")
    void testRoundTripUsingInverse() {
        Point original = gf.createPoint(new Coordinate(-122.4194, 37.7749));

        // Forward
        Point projected = (Point) wgs84ToWebMercator.transform(original);

        // Inverse
        Point restored = (Point) wgs84ToWebMercator.inverse(projected);

        assertNotNull(restored);
        assertEquals(original.getX(), restored.getX(), COORD_TOLERANCE);
        assertEquals(original.getY(), restored.getY(), COORD_TOLERANCE);
    }

    // ==================== UTM Tests ====================

    @Test
    @DisplayName("Transform Point: WGS84 to UTM Zone 10N (San Francisco)")
    void testTransformToUtm() {
        // San Francisco is in UTM Zone 10N
        Point sf = gf.createPoint(new Coordinate(-122.4194, 37.7749));
        Point result = (Point) wgs84ToUtm.transform(sf);

        assertNotNull(result);
        // UTM Zone 10N for SF should give ~550000 easting, ~4180000 northing
        assertTrue(result.getX() > 500000 && result.getX() < 600000,
            "Easting should be around 550000");
        assertTrue(result.getY() > 4000000 && result.getY() < 5000000,
            "Northing should be around 4180000");
    }

    @Test
    @DisplayName("Transform Polygon: WGS84 to UTM Zone 32N (Munich)")
    void testTransformPolygonToUtm() {
        JTSGeometryTransformer wgs84ToUtm32 = new JTSGeometryTransformer(WGS84, UTM_ZONE_32N);

        // Munich area polygon
        Coordinate[] coords = new Coordinate[] {
            new Coordinate(11.5, 48.1),
            new Coordinate(11.6, 48.1),
            new Coordinate(11.6, 48.2),
            new Coordinate(11.5, 48.2),
            new Coordinate(11.5, 48.1)
        };
        Polygon munich = gf.createPolygon(coords);
        Polygon result = (Polygon) wgs84ToUtm32.transform(munich);

        assertNotNull(result);
        assertTrue(result.isValid());
        assertTrue(result.getArea() > 0);

        // All UTM northings should be around 5.3M meters for Munich
        for (Coordinate c : result.getCoordinates()) {
            assertTrue(c.getY() > 5000000 && c.getY() < 6000000,
                "Northing should be around 5.3M meters");
        }
    }

    // ==================== Static Method Tests ====================

    @Test
    @DisplayName("Static transform() method")
    void testStaticTransform() {
        Point point = gf.createPoint(new Coordinate(-122.4194, 37.7749));
        Geometry result = JTSGeometryTransformer.transform(WGS84, WEB_MERCATOR, point);

        assertNotNull(result);
        assertTrue(result instanceof Point);
        Point resultPoint = (Point) result;
        assertTrue(resultPoint.getX() < 0);
        assertTrue(resultPoint.getY() > 0);
    }

    @Test
    @DisplayName("Static transform() for Coordinate")
    void testStaticTransformCoordinate() {
        Coordinate coord = new Coordinate(-122.4194, 37.7749);
        Coordinate result = JTSGeometryTransformer.transform(WGS84, WEB_MERCATOR, coord);

        assertNotNull(result);
        assertTrue(result.getX() < 0);
        assertTrue(result.getY() > 0);
    }

    @Test
    @DisplayName("Static transform() for Coordinate array")
    void testStaticTransformCoordinateArray() {
        Coordinate[] coords = new Coordinate[] {
            new Coordinate(-122.5, 37.7),
            new Coordinate(-122.4, 37.8),
            new Coordinate(-122.3, 37.9)
        };
        Coordinate[] result = JTSGeometryTransformer.transform(WGS84, WEB_MERCATOR, coords);

        assertNotNull(result);
        assertEquals(3, result.length);
        for (Coordinate c : result) {
            assertTrue(c.getX() < 0);
            assertTrue(c.getY() > 0);
        }
    }

    // ==================== Cached Transformer Tests ====================

    @Test
    @DisplayName("Cached transformer works correctly")
    void testCachedTransformer() {
        JTSGeometryTransformer cached = JTSGeometryTransformer.cached(WGS84, WEB_MERCATOR);
        Point point = gf.createPoint(new Coordinate(-122.4194, 37.7749));
        Point result = (Point) cached.transform(point);

        assertNotNull(result);
        assertTrue(result.getX() < 0);
        assertTrue(result.getY() > 0);
    }

    // ==================== Constructor Tests ====================

    @Test
    @DisplayName("Constructor with Proj objects")
    void testConstructorWithProj() {
        Proj sourceProj = new Proj(WGS84);
        Proj targetProj = new Proj(WEB_MERCATOR);
        JTSGeometryTransformer transformer = new JTSGeometryTransformer(sourceProj, targetProj);

        Point point = gf.createPoint(new Coordinate(0, 0));
        Point result = (Point) transformer.transform(point);

        assertNotNull(result);
        assertEquals(0, result.getX(), METER_TOLERANCE);
        assertEquals(0, result.getY(), METER_TOLERANCE);
    }

    @Test
    @DisplayName("Constructor with Converter")
    void testConstructorWithConverter() {
        Converter converter = Proj4.proj4(WGS84, WEB_MERCATOR);
        JTSGeometryTransformer transformer = new JTSGeometryTransformer(converter);

        Point point = gf.createPoint(new Coordinate(0, 0));
        Point result = (Point) transformer.transform(point);

        assertNotNull(result);
        assertEquals(0, result.getX(), METER_TOLERANCE);
        assertEquals(0, result.getY(), METER_TOLERANCE);
    }

    @Test
    @DisplayName("Constructor with custom GeometryFactory")
    void testConstructorWithCustomGeometryFactory() {
        PrecisionModel pm = new PrecisionModel(1000); // 3 decimal places
        GeometryFactory customGf = new GeometryFactory(pm, 4326);
        JTSGeometryTransformer transformer = new JTSGeometryTransformer(WGS84, WEB_MERCATOR, customGf);

        Point point = gf.createPoint(new Coordinate(-122.4194, 37.7749));
        Point result = (Point) transformer.transform(point);

        assertNotNull(result);
        assertEquals(customGf, transformer.getGeometryFactory());
    }

    // ==================== EPSG Code Tests ====================

    @Test
    @DisplayName("Transform using EPSG:4326 to EPSG:3857")
    void testEpsgCodes() {
        JTSGeometryTransformer transformer = new JTSGeometryTransformer("EPSG:4326", "EPSG:3857");

        Point point = gf.createPoint(new Coordinate(0, 0));
        Point result = (Point) transformer.transform(point);

        assertNotNull(result);
        assertEquals(0, result.getX(), METER_TOLERANCE);
        assertEquals(0, result.getY(), METER_TOLERANCE);
    }

    // ==================== Edge Cases ====================

    @Test
    @DisplayName("Transform null geometry returns null")
    void testTransformNull() {
        Geometry result = wgs84ToWebMercator.transform(null);
        assertNull(result);
    }

    @Test
    @DisplayName("Transform at dateline")
    void testTransformAtDateline() {
        Point point = gf.createPoint(new Coordinate(180, 0));
        Point result = (Point) wgs84ToWebMercator.transform(point);

        assertNotNull(result);
        assertEquals(20037508.34, Math.abs(result.getX()), 1.0);
    }

    @Test
    @DisplayName("Transform near pole")
    void testTransformNearPole() {
        Point point = gf.createPoint(new Coordinate(0, 85));
        Point result = (Point) wgs84ToWebMercator.transform(point);

        assertNotNull(result);
        assertTrue(result.getY() > 15000000); // Very large Y value
    }

    // ==================== Real-World Scenarios ====================

    @Test
    @DisplayName("Scenario: Transform US state boundary")
    void testTransformStateBoundary() {
        // Simplified California boundary
        Coordinate[] california = new Coordinate[] {
            new Coordinate(-124.4, 42.0),   // NW corner
            new Coordinate(-120.0, 42.0),   // NE corner
            new Coordinate(-114.6, 35.0),   // SE corner
            new Coordinate(-114.6, 32.5),   // Southern AZ border
            new Coordinate(-117.1, 32.5),   // SW corner
            new Coordinate(-124.4, 40.0),   // Back up the coast
            new Coordinate(-124.4, 42.0)    // Close
        };
        Polygon state = gf.createPolygon(california);
        Polygon result = (Polygon) wgs84ToWebMercator.transform(state);

        assertNotNull(result);
        assertTrue(result.isValid());
        assertTrue(result.getArea() > 0);
    }

    @Test
    @DisplayName("Scenario: Transform city POI dataset")
    void testTransformCityPOIs() {
        // Multiple points of interest in San Francisco
        Point[] pois = new Point[] {
            gf.createPoint(new Coordinate(-122.4194, 37.7749)),  // City center
            gf.createPoint(new Coordinate(-122.4169, 37.8088)),  // Fisherman's Wharf
            gf.createPoint(new Coordinate(-122.4783, 37.8199)),  // Golden Gate
            gf.createPoint(new Coordinate(-122.3892, 37.6879)),  // SFO Airport
            gf.createPoint(new Coordinate(-122.4367, 37.7596))   // Mission District
        };
        MultiPoint dataset = gf.createMultiPoint(pois);
        MultiPoint result = (MultiPoint) wgs84ToWebMercator.transform(dataset);

        assertNotNull(result);
        assertEquals(5, result.getNumGeometries());
    }

    @Test
    @DisplayName("Scenario: Transform road network")
    void testTransformRoadNetwork() {
        // Simplified road segments
        LineString[] roads = new LineString[] {
            gf.createLineString(new Coordinate[] {
                new Coordinate(-122.4194, 37.7749),
                new Coordinate(-122.4, 37.79)
            }),
            gf.createLineString(new Coordinate[] {
                new Coordinate(-122.4, 37.79),
                new Coordinate(-122.38, 37.8)
            }),
            gf.createLineString(new Coordinate[] {
                new Coordinate(-122.4, 37.79),
                new Coordinate(-122.42, 37.8)
            })
        };
        MultiLineString network = gf.createMultiLineString(roads);
        MultiLineString result = (MultiLineString) wgs84ToWebMercator.transform(network);

        assertNotNull(result);
        assertEquals(3, result.getNumGeometries());

        // Verify total length is reasonable (not zero or infinite)
        assertTrue(result.getLength() > 0);
        assertTrue(result.getLength() < 1e10);
    }
}
