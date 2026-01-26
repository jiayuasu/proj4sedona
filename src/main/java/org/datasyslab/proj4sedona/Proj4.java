package org.datasyslab.proj4sedona;

import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.core.Proj;
import org.datasyslab.proj4sedona.mgrs.MGRS;
import org.datasyslab.proj4sedona.transform.Converter;
import org.datasyslab.proj4sedona.transform.Transform;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Main entry point for coordinate transformations.
 * Mirrors: lib/core.js proj4 function
 * 
 * This class provides a fluent API for transforming coordinates between
 * coordinate reference systems (CRS).
 * 
 * Usage patterns (mirroring proj4js):
 * 
 * 1. Create a converter:
 *    Converter conv = Proj4.proj4("EPSG:4326", "EPSG:3857");
 *    Point result = conv.forward(new Point(lon, lat));
 * 
 * 2. Direct transformation (from WGS84):
 *    Point result = Proj4.proj4("EPSG:3857", new Point(lon, lat));
 * 
 * 3. Direct transformation between two CRS:
 *    Point result = Proj4.proj4("+proj=longlat", "+proj=merc", new Point(lon, lat));
 * 
 * 4. Batch transformation for high throughput:
 *    double[][] results = Proj4.transformBatch("EPSG:4326", "EPSG:3857", coords);
 * 
 * 5. Cached projection for repeated use:
 *    Proj proj = Proj4.getCachedProj("+proj=utm +zone=18");
 * 
 * @since 1.0.0
 */
public final class Proj4 {

    /** Cache for initialized projections (thread-safe) */
    private static final ConcurrentHashMap<String, Proj> projCache = new ConcurrentHashMap<>();

    /** Default WGS84 projection for single-argument calls */
    private static volatile Proj wgs84;

    private Proj4() {
        // Utility class
    }

    // ========== Projection Cache ==========

    /**
     * Get a cached projection instance, creating it if necessary.
     * This is useful for repeated transformations to avoid re-parsing
     * the projection definition each time.
     * 
     * @param srsCode The SRS code (PROJ string, WKT, etc.)
     * @return The cached Proj instance
     * @since 1.0.0
     */
    public static Proj getCachedProj(String srsCode) {
        return projCache.computeIfAbsent(srsCode, Proj::new);
    }

    /**
     * Clear the projection cache.
     * Useful for testing or when memory is constrained.
     * 
     * @since 1.0.0
     */
    public static void clearCache() {
        projCache.clear();
        wgs84 = null;
    }

    /**
     * Get the current size of the projection cache.
     * 
     * @return Number of cached projections
     * @since 1.0.0
     */
    public static int getCacheSize() {
        return projCache.size();
    }

    /**
     * Preload commonly used projections into the cache.
     * Call this at application startup to avoid initialization latency
     * during the first transformation requests.
     * 
     * <p>Preloads the following projections:</p>
     * <ul>
     *   <li>WGS84 (EPSG:4326) - Geographic coordinates</li>
     *   <li>Web Mercator (EPSG:3857) - Web maps</li>
     *   <li>UTM zones 10-19N - Continental US coverage</li>
     * </ul>
     * 
     * @since 1.0.0
     */
    public static void preloadCommonProjections() {
        // Geographic
        getCachedProj("+proj=longlat +datum=WGS84");
        getCachedProj("EPSG:4326");
        
        // Web Mercator
        getCachedProj("+proj=merc +a=6378137 +b=6378137 +lat_ts=0 +lon_0=0 +x_0=0 +y_0=0 +k=1 +units=m +no_defs");
        getCachedProj("EPSG:3857");
        
        // Common UTM zones for continental US
        for (int zone = 10; zone <= 19; zone++) {
            getCachedProj("+proj=utm +zone=" + zone + " +datum=WGS84");
        }
    }

    /**
     * Preload specific projections into the cache.
     * Use this to warm the cache with projections specific to your application.
     * 
     * @param srsCodes Array of SRS codes to preload
     * @since 1.0.0
     */
    public static void preloadProjections(String... srsCodes) {
        for (String srsCode : srsCodes) {
            getCachedProj(srsCode);
        }
    }


    /**
     * Get or create the WGS84 projection.
     */
    private static Proj getWgs84() {
        if (wgs84 == null) {
            synchronized (Proj4.class) {
                if (wgs84 == null) {
                    wgs84 = new Proj("+proj=longlat +datum=WGS84");
                }
            }
        }
        return wgs84;
    }

    /**
     * Create a converter from WGS84 to the specified projection.
     * 
     * @param toProj Destination projection definition
     * @return Converter from WGS84 to the destination
     */
    public static Converter proj4(String toProj) {
        Proj to = new Proj(toProj);
        return new Converter(getWgs84(), to);
    }

    /**
     * Create a converter between two projections.
     * 
     * @param fromProj Source projection definition
     * @param toProj Destination projection definition
     * @return Converter between the two projections
     */
    public static Converter proj4(String fromProj, String toProj) {
        Proj from = new Proj(fromProj);
        Proj to = new Proj(toProj);
        return new Converter(from, to);
    }

    /**
     * Direct transformation from WGS84 to the specified projection.
     * 
     * @param toProj Destination projection definition
     * @param coord Input coordinates (in WGS84: lon, lat)
     * @return Transformed coordinates
     */
    public static Point proj4(String toProj, Point coord) {
        Proj to = new Proj(toProj);
        return Transform.transform(getWgs84(), to, coord);
    }

    /**
     * Direct transformation from WGS84 to the specified projection.
     * 
     * @param toProj Destination projection definition
     * @param coords Input coordinates array [lon, lat] (in WGS84)
     * @return Transformed coordinates array
     */
    public static double[] proj4(String toProj, double[] coords) {
        Point result = proj4(toProj, new Point(coords));
        if (result == null) {
            return new double[]{Double.NaN, Double.NaN};
        }
        return result.toArray();
    }

    /**
     * Direct transformation between two projections.
     * 
     * @param fromProj Source projection definition
     * @param toProj Destination projection definition
     * @param coord Input coordinates in source CRS
     * @return Transformed coordinates in destination CRS
     */
    public static Point proj4(String fromProj, String toProj, Point coord) {
        Proj from = new Proj(fromProj);
        Proj to = new Proj(toProj);
        return Transform.transform(from, to, coord);
    }

    /**
     * Direct transformation between two projections.
     * 
     * @param fromProj Source projection definition
     * @param toProj Destination projection definition
     * @param coords Input coordinates array in source CRS
     * @return Transformed coordinates array in destination CRS
     */
    public static double[] proj4(String fromProj, String toProj, double[] coords) {
        Point result = proj4(fromProj, toProj, new Point(coords));
        if (result == null) {
            return new double[]{Double.NaN, Double.NaN};
        }
        return result.toArray();
    }

    /**
     * Create a converter with Proj objects.
     * 
     * @param from Source projection
     * @param to Destination projection
     * @return Converter between the two projections
     */
    public static Converter converter(Proj from, Proj to) {
        return new Converter(from, to);
    }

    /**
     * Transform coordinates using Proj objects.
     * 
     * @param from Source projection
     * @param to Destination projection  
     * @param coord Input coordinates
     * @return Transformed coordinates
     */
    public static Point transform(Proj from, Proj to, Point coord) {
        return Transform.transform(from, to, coord);
    }

    /**
     * Create a projection from definition string.
     * Convenience method to access Proj constructor.
     * 
     * @param definition Projection definition (PROJ string, WKT, etc.)
     * @return The projection object
     */
    public static Proj projection(String definition) {
        return new Proj(definition);
    }

    /**
     * Create a point from coordinates.
     * Convenience method to access Point constructor.
     * 
     * @param x X coordinate (longitude or easting)
     * @param y Y coordinate (latitude or northing)
     * @return The point object
     */
    public static Point point(double x, double y) {
        return new Point(x, y);
    }

    /**
     * Create a point from coordinates.
     * 
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate (altitude)
     * @return The point object
     */
    public static Point point(double x, double y, double z) {
        return new Point(x, y, z);
    }

    /**
     * Create a point from an array.
     * 
     * @param coords Coordinate array [x, y] or [x, y, z] or [x, y, z, m]
     * @return The point object
     */
    public static Point point(double[] coords) {
        return new Point(coords);
    }

    // ========== MGRS Methods ==========

    /**
     * Convert longitude/latitude to MGRS string.
     * 
     * @param lonLat Array with [longitude, latitude] in degrees (WGS84)
     * @param accuracy Accuracy in digits (1-5): 5=1m, 4=10m, 3=100m, 2=1km, 1=10km
     * @return MGRS string for the given location
     */
    public static String toMGRS(double[] lonLat, int accuracy) {
        return MGRS.forward(lonLat, accuracy);
    }

    /**
     * Convert longitude/latitude to MGRS string with 1m accuracy.
     * 
     * @param lonLat Array with [longitude, latitude] in degrees (WGS84)
     * @return MGRS string for the given location
     */
    public static String toMGRS(double[] lonLat) {
        return MGRS.forward(lonLat, 5);
    }

    /**
     * Convert longitude/latitude to MGRS string.
     * 
     * @param lon Longitude in degrees (WGS84)
     * @param lat Latitude in degrees (WGS84)
     * @param accuracy Accuracy in digits (1-5)
     * @return MGRS string for the given location
     */
    public static String toMGRS(double lon, double lat, int accuracy) {
        return MGRS.forward(new double[]{lon, lat}, accuracy);
    }

    /**
     * Convert longitude/latitude to MGRS string with 1m accuracy.
     * 
     * @param lon Longitude in degrees (WGS84)
     * @param lat Latitude in degrees (WGS84)
     * @return MGRS string for the given location
     */
    public static String toMGRS(double lon, double lat) {
        return MGRS.forward(new double[]{lon, lat}, 5);
    }

    /**
     * Convert MGRS string to longitude/latitude.
     * 
     * @param mgrs MGRS string
     * @return Array with [longitude, latitude] in degrees (WGS84)
     */
    public static double[] fromMGRS(String mgrs) {
        return MGRS.toPoint(mgrs);
    }

    /**
     * Convert MGRS string to Point.
     * 
     * @param mgrs MGRS string
     * @return Point with longitude and latitude in degrees (WGS84)
     */
    public static Point mgrsToPoint(String mgrs) {
        double[] coords = MGRS.toPoint(mgrs);
        return new Point(coords[0], coords[1]);
    }

    /**
     * Get bounding box for an MGRS reference.
     * 
     * @param mgrs MGRS string
     * @return Array with [left, bottom, right, top] in degrees (WGS84)
     */
    public static double[] mgrsInverse(String mgrs) {
        return MGRS.inverse(mgrs);
    }

    // ========== Batch Transformation Methods ==========
    //
    // WHEN TO USE BATCH VS SINGLE TRANSFORMS:
    //
    // Use SINGLE transforms (proj4() methods) when:
    // - Transforming individual points interactively (e.g., user clicks)
    // - Working with Point objects and need the result as a Point
    // - Transforming < 10 coordinates at a time
    // - Code readability is more important than raw performance
    //
    // Use BATCH transforms (transformBatch/transformFlat) when:
    // - Processing large datasets (100+ coordinates)
    // - Reading coordinates from files or databases
    // - Performance is critical (batch is ~30% faster due to reduced overhead)
    // - Working with coordinate arrays from GIS libraries
    //
    // Use FLAT transforms when:
    // - Coordinates are already in flat array format [x1,y1,x2,y2,...]
    // - Minimizing memory allocation is important
    // - Integrating with native code or GPU processing
    //

    /**
     * Transform an array of coordinate pairs efficiently.
     * 
     * <p><b>When to use:</b> Use this method when you have many coordinates
     * to transform (100+) and they're already organized as [x,y] pairs.
     * For small numbers of points (&lt;10), use {@link #proj4(String, String, Point)}
     * for cleaner code.</p>
     * 
     * <p><b>Performance:</b> ~30% faster than calling proj4() in a loop due to
     * projection caching and reduced object allocation.</p>
     * 
     * @param fromProj Source projection definition
     * @param toProj Destination projection definition
     * @param coords Array of [x, y] coordinate pairs
     * @return Array of transformed [x, y] coordinate pairs
     * @see #transformFlat(String, String, double[]) for flat array input
     * @see #proj4(String, String, Point) for single point transformation
     * @since 1.0.0
     */
    public static double[][] transformBatch(String fromProj, String toProj, double[][] coords) {
        if (coords == null || coords.length == 0) {
            return new double[0][0];
        }

        Proj from = getCachedProj(fromProj);
        Proj to = getCachedProj(toProj);
        Converter converter = new Converter(from, to);

        double[][] results = new double[coords.length][];
        Point temp = new Point(0, 0);

        for (int i = 0; i < coords.length; i++) {
            if (coords[i] == null || coords[i].length < 2) {
                results[i] = new double[]{Double.NaN, Double.NaN};
                continue;
            }

            temp.x = coords[i][0];
            temp.y = coords[i][1];
            temp.z = coords[i].length > 2 ? coords[i][2] : 0;

            Point result = converter.forward(temp);
            if (result != null) {
                results[i] = result.toArray();
            } else {
                results[i] = new double[]{Double.NaN, Double.NaN};
            }
        }

        return results;
    }

    /**
     * Transform a flat array of coordinates efficiently.
     * Coordinates are packed as [x1, y1, x2, y2, ...].
     * 
     * <p><b>When to use:</b> Use this method when coordinates are already in
     * flat array format (common in GIS libraries, binary formats, or GPU buffers).
     * This is the most memory-efficient batch method as it avoids creating
     * intermediate arrays.</p>
     * 
     * <p><b>Performance:</b> Fastest batch method due to minimal allocation.
     * Ideal for processing large datasets from binary files or native code.</p>
     * 
     * @param fromProj Source projection definition
     * @param toProj Destination projection definition
     * @param coords Flat array of coordinates [x1, y1, x2, y2, ...]
     * @return Flat array of transformed coordinates
     * @see #transformBatch(String, String, double[][]) for array of pairs
     * @see #transformFlat3D(String, String, double[]) for 3D coordinates
     * @since 1.0.0
     */
    public static double[] transformFlat(String fromProj, String toProj, double[] coords) {
        if (coords == null || coords.length < 2) {
            return new double[0];
        }

        Proj from = getCachedProj(fromProj);
        Proj to = getCachedProj(toProj);
        Converter converter = new Converter(from, to);

        int numPoints = coords.length / 2;
        double[] results = new double[numPoints * 2];
        Point temp = new Point(0, 0);

        for (int i = 0; i < numPoints; i++) {
            temp.x = coords[i * 2];
            temp.y = coords[i * 2 + 1];

            Point result = converter.forward(temp);
            if (result != null) {
                results[i * 2] = result.x;
                results[i * 2 + 1] = result.y;
            } else {
                results[i * 2] = Double.NaN;
                results[i * 2 + 1] = Double.NaN;
            }
        }

        return results;
    }

    /**
     * Transform a flat array of 3D coordinates efficiently.
     * Coordinates are packed as [x1, y1, z1, x2, y2, z2, ...].
     * Uses cached projections for better performance.
     * 
     * @param fromProj Source projection definition
     * @param toProj Destination projection definition
     * @param coords Flat array of 3D coordinates
     * @return Flat array of transformed 3D coordinates
     * @since 1.0.0
     */
    public static double[] transformFlat3D(String fromProj, String toProj, double[] coords) {
        if (coords == null || coords.length < 3) {
            return new double[0];
        }

        Proj from = getCachedProj(fromProj);
        Proj to = getCachedProj(toProj);
        Converter converter = new Converter(from, to);

        int numPoints = coords.length / 3;
        double[] results = new double[numPoints * 3];
        Point temp = new Point(0, 0, 0);

        for (int i = 0; i < numPoints; i++) {
            temp.x = coords[i * 3];
            temp.y = coords[i * 3 + 1];
            temp.z = coords[i * 3 + 2];

            Point result = converter.forward(temp);
            if (result != null) {
                results[i * 3] = result.x;
                results[i * 3 + 1] = result.y;
                results[i * 3 + 2] = result.z;
            } else {
                results[i * 3] = Double.NaN;
                results[i * 3 + 1] = Double.NaN;
                results[i * 3 + 2] = Double.NaN;
            }
        }

        return results;
    }

    /**
     * Create a high-performance converter for repeated transformations.
     * Uses cached projections internally.
     * 
     * @param fromProj Source projection definition
     * @param toProj Destination projection definition
     * @return A converter that can be reused
     * @since 1.0.0
     */
    public static Converter cachedConverter(String fromProj, String toProj) {
        Proj from = getCachedProj(fromProj);
        Proj to = getCachedProj(toProj);
        return new Converter(from, to);
    }
}
