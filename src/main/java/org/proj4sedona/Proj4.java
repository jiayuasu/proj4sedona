package org.proj4sedona;

import org.proj4sedona.core.Point;
import org.proj4sedona.core.Proj;
import org.proj4sedona.mgrs.MGRS;
import org.proj4sedona.transform.Converter;
import org.proj4sedona.transform.Transform;

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
 */
public final class Proj4 {

    // Default WGS84 projection for single-argument calls
    private static volatile Proj wgs84;

    private Proj4() {
        // Utility class
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
}
