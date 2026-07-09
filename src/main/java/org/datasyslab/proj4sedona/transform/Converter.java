package org.datasyslab.proj4sedona.transform;

import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.core.Proj;

/**
 * A converter between two coordinate systems.
 * Mirrors: lib/core.js Converter interface
 * 
 * Provides forward and inverse transformation methods.
 */
public class Converter {

    private final Proj from;
    private final Proj to;

    /**
     * Create a converter from source to destination projection.
     * 
     * @param from Source projection
     * @param to Destination projection
     */
    public Converter(Proj from, Proj to) {
        this.from = from;
        this.to = to;
    }

    /**
     * Forward transformation: from source to destination.
     * 
     * @param point Input coordinates in source CRS
     * @return Output coordinates in destination CRS
     */
    public Point forward(Point point) {
        return forward(point, false);
    }

    /**
     * Forward transformation with axis enforcement.
     * 
     * @param point Input coordinates in source CRS
     * @param enforceAxis Whether to apply axis order corrections
     * @return Output coordinates in destination CRS
     */
    public Point forward(Point point, boolean enforceAxis) {
        Point result = Transform.transform(from, to, point, enforceAxis);
        // Preserve the input measure, like proj4js's transformer adapter copies
        // extra keys (m) back onto the output.
        if (result != null && !Double.isNaN(point.m)) {
            result.m = point.m;
        }
        return result;
    }

    /**
     * Forward transformation from array coordinates.
     * 
     * @param coords Array [x, y] or [x, y, z]
     * @return Transformed array
     */
    public double[] forward(double[] coords) {
        Point p = new Point(coords);
        Point result = forward(p);
        return adaptArray(coords, result);
    }

    /**
     * Inverse transformation: from destination to source.
     * 
     * @param point Input coordinates in destination CRS
     * @return Output coordinates in source CRS
     */
    public Point inverse(Point point) {
        return inverse(point, false);
    }

    /**
     * Inverse transformation with axis enforcement.
     * 
     * @param point Input coordinates in destination CRS
     * @param enforceAxis Whether to apply axis order corrections
     * @return Output coordinates in source CRS
     */
    public Point inverse(Point point, boolean enforceAxis) {
        Point result = Transform.transform(to, from, point, enforceAxis);
        if (result != null && !Double.isNaN(point.m)) {
            result.m = point.m;
        }
        return result;
    }

    /**
     * Inverse transformation from array coordinates.
     * 
     * @param coords Array [x, y] or [x, y, z]
     * @return Transformed array
     */
    public double[] inverse(double[] coords) {
        Point p = new Point(coords);
        Point result = inverse(p);
        return adaptArray(coords, result);
    }

    /**
     * Shape the array result like proj4js's transformer adapter (lib/core.js):
     * when either CRS is geocentric, a 3-component input keeps its third component
     * (the computed z), instead of Point.toArray() collapsing z == 0 to two
     * components. Also preserves the input measure (m) for 4-component input.
     */
    private double[] adaptArray(double[] coords, Point result) {
        if (result == null) {
            return new double[]{Double.NaN, Double.NaN};
        }
        if (coords.length > 2 && (isGeocent(from) || isGeocent(to))) {
            if (coords.length > 3) {
                return new double[]{result.x, result.y, result.z, coords[3]};
            }
            return new double[]{result.x, result.y, result.z};
        }
        return result.toArray();
    }

    private static boolean isGeocent(Proj proj) {
        String n = proj.getParams().projName;
        if (n == null) {
            return false;
        }
        n = n.toLowerCase(java.util.Locale.ROOT);
        return "geocent".equals(n) || "geocentric".equals(n);
    }

    /**
     * Get the source projection.
     */
    public Proj getFrom() {
        return from;
    }

    /**
     * Get the destination projection.
     */
    public Proj getTo() {
        return to;
    }
}
