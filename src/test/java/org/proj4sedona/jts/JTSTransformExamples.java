package org.proj4sedona.jts;

import org.locationtech.jts.geom.*;
import org.proj4sedona.Proj4;
import org.proj4sedona.transform.Converter;

/**
 * Example usage of JTSGeometryTransformer for CRS transformations.
 * 
 * <p>This class demonstrates various ways to transform JTS geometries
 * between different coordinate reference systems using proj4sedona.</p>
 * 
 * <h2>Common CRS Definitions</h2>
 * <ul>
 *   <li><b>WGS84 (EPSG:4326)</b>: Geographic coordinates (lon/lat in degrees)</li>
 *   <li><b>Web Mercator (EPSG:3857)</b>: Used by Google Maps, OpenStreetMap</li>
 *   <li><b>UTM Zones</b>: Universal Transverse Mercator (meters)</li>
 * </ul>
 */
public class JTSTransformExamples {

    /**
     * Example 1: Transform a single Point from WGS84 to Web Mercator.
     * 
     * <p>This is the most common transformation for web mapping applications.</p>
     */
    public static void example1_TransformPoint() {
        System.out.println("=== Example 1: Transform Point ===");

        // Create geometry factory and a point
        GeometryFactory gf = new GeometryFactory();
        Point sanFrancisco = gf.createPoint(new Coordinate(-122.4194, 37.7749));

        // Create transformer (WGS84 to Web Mercator)
        JTSGeometryTransformer transformer = new JTSGeometryTransformer(
            "EPSG:4326",   // WGS84
            "EPSG:3857"    // Web Mercator
        );

        // Transform the point
        Point transformed = (Point) transformer.transform(sanFrancisco);

        System.out.println("Original (WGS84): " + sanFrancisco);
        System.out.println("Transformed (Web Mercator): " + transformed);
        System.out.println("X (meters): " + transformed.getX());
        System.out.println("Y (meters): " + transformed.getY());
        System.out.println();
    }

    /**
     * Example 2: Transform a Polygon from WGS84 to Web Mercator.
     * 
     * <p>Useful for transforming building footprints, boundaries, etc.</p>
     */
    public static void example2_TransformPolygon() {
        System.out.println("=== Example 2: Transform Polygon ===");

        GeometryFactory gf = new GeometryFactory();

        // Create a polygon (rectangle around San Francisco)
        Coordinate[] coords = new Coordinate[] {
            new Coordinate(-122.5, 37.7),
            new Coordinate(-122.3, 37.7),
            new Coordinate(-122.3, 37.9),
            new Coordinate(-122.5, 37.9),
            new Coordinate(-122.5, 37.7)  // close the ring
        };
        Polygon polygon = gf.createPolygon(coords);

        // Transform using static method (convenient for one-off transformations)
        Polygon transformed = (Polygon) JTSGeometryTransformer.transform(
            "EPSG:4326",
            "EPSG:3857",
            polygon
        );

        System.out.println("Original polygon area (deg²): " + polygon.getArea());
        System.out.println("Transformed polygon area (m²): " + transformed.getArea());
        System.out.println("Is valid: " + transformed.isValid());
        System.out.println();
    }

    /**
     * Example 3: Transform a LineString (e.g., a road or river).
     */
    public static void example3_TransformLineString() {
        System.out.println("=== Example 3: Transform LineString ===");

        GeometryFactory gf = new GeometryFactory();

        // Create a line representing a path
        Coordinate[] pathCoords = new Coordinate[] {
            new Coordinate(-122.4194, 37.7749),  // San Francisco
            new Coordinate(-121.8863, 37.3382),  // San Jose
            new Coordinate(-118.2437, 34.0522)   // Los Angeles
        };
        LineString path = gf.createLineString(pathCoords);

        // Using PROJ string syntax
        JTSGeometryTransformer transformer = new JTSGeometryTransformer(
            "+proj=longlat +datum=WGS84",
            "+proj=merc +a=6378137 +b=6378137 +lat_ts=0 +lon_0=0 +x_0=0 +y_0=0 +k=1 +units=m"
        );

        LineString transformed = (LineString) transformer.transform(path);

        System.out.println("Original length (degrees): " + path.getLength());
        System.out.println("Transformed length (meters): " + transformed.getLength());
        System.out.println("Number of points: " + transformed.getNumPoints());
        System.out.println();
    }

    /**
     * Example 4: Transform to UTM (Universal Transverse Mercator).
     * 
     * <p>UTM is commonly used for local/regional coordinate systems
     * where distances need to be in meters.</p>
     */
    public static void example4_TransformToUTM() {
        System.out.println("=== Example 4: Transform to UTM ===");

        GeometryFactory gf = new GeometryFactory();

        // San Francisco is in UTM Zone 10N
        Point sf = gf.createPoint(new Coordinate(-122.4194, 37.7749));

        JTSGeometryTransformer transformer = new JTSGeometryTransformer(
            "EPSG:4326",
            "+proj=utm +zone=10 +datum=WGS84 +units=m"
        );

        Point utmPoint = (Point) transformer.transform(sf);

        System.out.println("WGS84: lon=" + sf.getX() + ", lat=" + sf.getY());
        System.out.println("UTM Zone 10N: easting=" + utmPoint.getX() + ", northing=" + utmPoint.getY());
        System.out.println();
    }

    /**
     * Example 5: Batch transformation for multiple geometries.
     * 
     * <p>When transforming many geometries, create a single transformer
     * instance and reuse it for better performance.</p>
     */
    public static void example5_BatchTransformation() {
        System.out.println("=== Example 5: Batch Transformation ===");

        GeometryFactory gf = new GeometryFactory();

        // Create multiple city points
        Point[] cities = new Point[] {
            gf.createPoint(new Coordinate(-122.4194, 37.7749)),  // San Francisco
            gf.createPoint(new Coordinate(-118.2437, 34.0522)),  // Los Angeles
            gf.createPoint(new Coordinate(-74.0060, 40.7128)),   // New York
            gf.createPoint(new Coordinate(-87.6298, 41.8781)),   // Chicago
            gf.createPoint(new Coordinate(-95.3698, 29.7604))    // Houston
        };

        // Create a cached transformer for better performance
        JTSGeometryTransformer transformer = JTSGeometryTransformer.cached(
            "EPSG:4326",
            "EPSG:3857"
        );

        System.out.println("Transforming " + cities.length + " cities:");
        String[] cityNames = {"San Francisco", "Los Angeles", "New York", "Chicago", "Houston"};

        for (int i = 0; i < cities.length; i++) {
            Point transformed = (Point) transformer.transform(cities[i]);
            System.out.printf("  %s: (%.2f, %.2f) meters%n",
                cityNames[i], transformed.getX(), transformed.getY());
        }
        System.out.println();
    }

    /**
     * Example 6: Round-trip transformation (forward and inverse).
     * 
     * <p>Demonstrates that transformations can be reversed to get back
     * the original coordinates (within floating-point precision).</p>
     */
    public static void example6_RoundTripTransformation() {
        System.out.println("=== Example 6: Round-Trip Transformation ===");

        GeometryFactory gf = new GeometryFactory();
        Point original = gf.createPoint(new Coordinate(-122.4194, 37.7749));

        // Forward transformation
        JTSGeometryTransformer toWebMercator = new JTSGeometryTransformer("EPSG:4326", "EPSG:3857");
        Point projected = (Point) toWebMercator.transform(original);

        // Inverse transformation
        JTSGeometryTransformer toWGS84 = new JTSGeometryTransformer("EPSG:3857", "EPSG:4326");
        Point restored = (Point) toWGS84.transform(projected);

        // Or use the inverse() method directly
        Point restoredAlt = (Point) toWebMercator.inverse(projected);

        System.out.println("Original: " + original);
        System.out.println("Projected: " + projected);
        System.out.println("Restored: " + restored);
        System.out.println("Restored (alt): " + restoredAlt);

        double error = Math.sqrt(
            Math.pow(original.getX() - restored.getX(), 2) +
            Math.pow(original.getY() - restored.getY(), 2)
        );
        System.out.println("Round-trip error (degrees): " + error);
        System.out.println();
    }

    /**
     * Example 7: Transform a Polygon with holes.
     */
    public static void example7_PolygonWithHoles() {
        System.out.println("=== Example 7: Polygon with Holes ===");

        GeometryFactory gf = new GeometryFactory();

        // Outer ring (San Francisco area)
        Coordinate[] outer = new Coordinate[] {
            new Coordinate(-122.5, 37.7),
            new Coordinate(-122.3, 37.7),
            new Coordinate(-122.3, 37.9),
            new Coordinate(-122.5, 37.9),
            new Coordinate(-122.5, 37.7)
        };

        // Inner ring (hole - e.g., a park or lake)
        Coordinate[] inner = new Coordinate[] {
            new Coordinate(-122.45, 37.75),
            new Coordinate(-122.45, 37.85),
            new Coordinate(-122.35, 37.85),
            new Coordinate(-122.35, 37.75),
            new Coordinate(-122.45, 37.75)
        };

        LinearRing shell = gf.createLinearRing(outer);
        LinearRing hole = gf.createLinearRing(inner);
        Polygon polygonWithHole = gf.createPolygon(shell, new LinearRing[]{hole});

        JTSGeometryTransformer transformer = new JTSGeometryTransformer("EPSG:4326", "EPSG:3857");
        Polygon transformed = (Polygon) transformer.transform(polygonWithHole);

        System.out.println("Original polygon:");
        System.out.println("  Exterior ring points: " + polygonWithHole.getExteriorRing().getNumPoints());
        System.out.println("  Number of holes: " + polygonWithHole.getNumInteriorRing());

        System.out.println("Transformed polygon:");
        System.out.println("  Exterior ring points: " + transformed.getExteriorRing().getNumPoints());
        System.out.println("  Number of holes: " + transformed.getNumInteriorRing());
        System.out.println("  Is valid: " + transformed.isValid());
        System.out.println();
    }

    /**
     * Example 8: Transform a GeometryCollection (mixed geometry types).
     */
    public static void example8_GeometryCollection() {
        System.out.println("=== Example 8: GeometryCollection ===");

        GeometryFactory gf = new GeometryFactory();

        // Create a collection with different geometry types
        Geometry[] geoms = new Geometry[] {
            gf.createPoint(new Coordinate(-122.4, 37.8)),  // Point
            gf.createLineString(new Coordinate[] {        // LineString
                new Coordinate(-122.5, 37.7),
                new Coordinate(-122.3, 37.9)
            }),
            gf.createPolygon(new Coordinate[] {           // Polygon
                new Coordinate(-122.42, 37.78),
                new Coordinate(-122.38, 37.78),
                new Coordinate(-122.38, 37.82),
                new Coordinate(-122.42, 37.82),
                new Coordinate(-122.42, 37.78)
            })
        };
        GeometryCollection collection = gf.createGeometryCollection(geoms);

        JTSGeometryTransformer transformer = new JTSGeometryTransformer("EPSG:4326", "EPSG:3857");
        GeometryCollection transformed = (GeometryCollection) transformer.transform(collection);

        System.out.println("Collection contains:");
        for (int i = 0; i < transformed.getNumGeometries(); i++) {
            Geometry g = transformed.getGeometryN(i);
            System.out.println("  " + g.getGeometryType());
        }
        System.out.println();
    }

    /**
     * Example 9: Using with Proj4 directly for coordinate-level control.
     */
    public static void example9_UsingProj4Directly() {
        System.out.println("=== Example 9: Using Proj4 Directly ===");

        // For finer control, you can use Proj4 directly to transform coordinates
        // and then build JTS geometries from the results

        // Transform a single coordinate
        double[] wgs84Coord = {-122.4194, 37.7749};
        double[] webMercatorCoord = Proj4.proj4("EPSG:4326", "EPSG:3857", wgs84Coord);

        System.out.println("WGS84: [" + wgs84Coord[0] + ", " + wgs84Coord[1] + "]");
        System.out.println("Web Mercator: [" + webMercatorCoord[0] + ", " + webMercatorCoord[1] + "]");

        // Transform batch coordinates using flat array
        double[] flatCoords = {-122.5, 37.7, -122.3, 37.9};
        double[] transformedFlat = Proj4.transformFlat("EPSG:4326", "EPSG:3857", flatCoords);

        System.out.println("Batch transformation:");
        for (int i = 0; i < transformedFlat.length; i += 2) {
            System.out.printf("  Point %d: (%.2f, %.2f)%n",
                i/2, transformedFlat[i], transformedFlat[i+1]);
        }
        System.out.println();
    }

    /**
     * Example 10: Performance considerations.
     */
    public static void example10_Performance() {
        System.out.println("=== Example 10: Performance Tips ===");
        System.out.println();
        System.out.println("1. REUSE TRANSFORMERS: Create a JTSGeometryTransformer once and reuse it:");
        System.out.println("   JTSGeometryTransformer t = JTSGeometryTransformer.cached(src, dst);");
        System.out.println("   for (Geometry g : geometries) { t.transform(g); }");
        System.out.println();
        System.out.println("2. PRELOAD PROJECTIONS: At startup, preload common projections:");
        System.out.println("   Proj4.preloadProjections(\"EPSG:4326\", \"EPSG:3857\", \"EPSG:32610\");");
        System.out.println();
        System.out.println("3. BATCH COORDINATES: For large datasets, use flat array transformation:");
        System.out.println("   double[] result = Proj4.transformFlat(src, dst, flatCoords);");
        System.out.println();
        System.out.println("4. CACHE SIZE: Monitor cache with Proj4.getCacheSize()");
        System.out.println("   Clear if needed: Proj4.clearCache()");
        System.out.println();
    }

    /**
     * Run all examples.
     */
    public static void main(String[] args) {
        // Initialize projection registry
        org.proj4sedona.projection.ProjectionRegistry.start();

        System.out.println("JTS Geometry Transformation Examples");
        System.out.println("=====================================\n");

        example1_TransformPoint();
        example2_TransformPolygon();
        example3_TransformLineString();
        example4_TransformToUTM();
        example5_BatchTransformation();
        example6_RoundTripTransformation();
        example7_PolygonWithHoles();
        example8_GeometryCollection();
        example9_UsingProj4Directly();
        example10_Performance();

        System.out.println("All examples completed successfully!");
    }
}
