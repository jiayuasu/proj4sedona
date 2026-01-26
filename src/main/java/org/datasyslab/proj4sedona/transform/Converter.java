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
        return Transform.transform(from, to, point, false);
    }

    /**
     * Forward transformation with axis enforcement.
     * 
     * @param point Input coordinates in source CRS
     * @param enforceAxis Whether to apply axis order corrections
     * @return Output coordinates in destination CRS
     */
    public Point forward(Point point, boolean enforceAxis) {
        return Transform.transform(from, to, point, enforceAxis);
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
        if (result == null) {
            return new double[]{Double.NaN, Double.NaN};
        }
        return result.toArray();
    }

    /**
     * Inverse transformation: from destination to source.
     * 
     * @param point Input coordinates in destination CRS
     * @return Output coordinates in source CRS
     */
    public Point inverse(Point point) {
        return Transform.transform(to, from, point, false);
    }

    /**
     * Inverse transformation with axis enforcement.
     * 
     * @param point Input coordinates in destination CRS
     * @param enforceAxis Whether to apply axis order corrections
     * @return Output coordinates in source CRS
     */
    public Point inverse(Point point, boolean enforceAxis) {
        return Transform.transform(to, from, point, enforceAxis);
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
        if (result == null) {
            return new double[]{Double.NaN, Double.NaN};
        }
        return result.toArray();
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
