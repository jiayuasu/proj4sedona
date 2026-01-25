package org.proj4sedona.core;

/**
 * Coordinate point representation.
 * Mirrors: lib/Point.js and lib/common/toPoint.js
 * 
 * Note: This class is mutable for performance in hot paths.
 * For thread-safe usage, create new instances or use copy().
 */
public class Point {

    public double x;  // longitude or easting
    public double y;  // latitude or northing
    public double z;  // altitude (optional, default 0)
    public double m;  // measure (optional, default NaN)

    /**
     * Create a point with x and y coordinates.
     */
    public Point(double x, double y) {
        this.x = x;
        this.y = y;
        this.z = 0;
        this.m = Double.NaN;
    }

    /**
     * Create a point with x, y, and z coordinates.
     */
    public Point(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.m = Double.NaN;
    }

    /**
     * Create a point with x, y, z, and m coordinates.
     */
    public Point(double x, double y, double z, double m) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.m = m;
    }

    /**
     * Create a point from an array.
     * Mirrors: lib/common/toPoint.js
     */
    public Point(double[] array) {
        if (array == null || array.length < 2) {
            throw new IllegalArgumentException("Array must have at least 2 elements");
        }
        this.x = array[0];
        this.y = array[1];
        this.z = array.length > 2 ? array[2] : 0;
        this.m = array.length > 3 ? array[3] : Double.NaN;
    }

    /**
     * Create a point from a comma-separated string.
     * Mirrors: lib/Point.js string parsing
     */
    public Point(String coords) {
        if (coords == null || coords.isEmpty()) {
            throw new IllegalArgumentException("Coordinates string cannot be null or empty");
        }
        String[] parts = coords.split(",");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Coordinates string must have at least 2 values");
        }
        this.x = Double.parseDouble(parts[0].trim());
        this.y = Double.parseDouble(parts[1].trim());
        this.z = parts.length > 2 ? Double.parseDouble(parts[2].trim()) : 0;
        this.m = parts.length > 3 ? Double.parseDouble(parts[3].trim()) : Double.NaN;
    }

    /**
     * Copy constructor.
     */
    public Point(Point other) {
        this.x = other.x;
        this.y = other.y;
        this.z = other.z;
        this.m = other.m;
    }

    /**
     * Create a copy of this point.
     */
    public Point copy() {
        return new Point(this);
    }

    /**
     * Convert point to array.
     * Mirrors the array format used in proj4js.
     */
    public double[] toArray() {
        if (Double.isNaN(m)) {
            if (z == 0) {
                return new double[]{x, y};
            }
            return new double[]{x, y, z};
        }
        return new double[]{x, y, z, m};
    }

    /**
     * Check if this point has a z coordinate (non-zero).
     */
    public boolean hasZ() {
        return z != 0;
    }

    /**
     * Check if this point has an m coordinate.
     */
    public boolean hasM() {
        return !Double.isNaN(m);
    }

    /**
     * Static factory method to create point from array.
     * Mirrors: lib/common/toPoint.js default export
     */
    public static Point toPoint(double[] array) {
        return new Point(array);
    }

    /**
     * Static factory method for convenience.
     */
    public static Point of(double x, double y) {
        return new Point(x, y);
    }

    /**
     * Static factory method for convenience.
     */
    public static Point of(double x, double y, double z) {
        return new Point(x, y, z);
    }

    @Override
    public String toString() {
        if (Double.isNaN(m)) {
            if (z == 0) {
                return "Point{x=" + x + ", y=" + y + "}";
            }
            return "Point{x=" + x + ", y=" + y + ", z=" + z + "}";
        }
        return "Point{x=" + x + ", y=" + y + ", z=" + z + ", m=" + m + "}";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Point point = (Point) obj;
        return Double.compare(point.x, x) == 0 &&
               Double.compare(point.y, y) == 0 &&
               Double.compare(point.z, z) == 0 &&
               (Double.isNaN(m) && Double.isNaN(point.m) || Double.compare(point.m, m) == 0);
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        temp = Double.doubleToLongBits(x);
        result = (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(y);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(z);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(m);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }
}
