package org.proj4.optimization;

import org.proj4.core.Point;
import org.proj4.core.Projection;
import org.proj4.cache.ProjectionCache;
import java.util.List;
import java.util.ArrayList;

/**
 * Optimized batch transformer for processing multiple points at once.
 * This reduces overhead by reusing projection objects and minimizing method calls.
 */
public class BatchTransformer {
    private final Projection fromProj;
    private final Projection toProj;
    private final boolean enforceAxis;
    
    /**
     * Creates a batch transformer for the given projections.
     * @param fromProjString source projection string
     * @param toProjString destination projection string
     * @param enforceAxis whether to enforce axis order
     */
    public BatchTransformer(String fromProjString, String toProjString, boolean enforceAxis) {
        this.fromProj = ProjectionCache.getProjection(fromProjString);
        this.toProj = ProjectionCache.getProjection(toProjString);
        this.enforceAxis = enforceAxis;
    }
    
    /**
     * Creates a batch transformer for the given projections.
     * @param fromProj source projection
     * @param toProj destination projection
     * @param enforceAxis whether to enforce axis order
     */
    public BatchTransformer(Projection fromProj, Projection toProj, boolean enforceAxis) {
        this.fromProj = fromProj;
        this.toProj = toProj;
        this.enforceAxis = enforceAxis;
    }
    
    /**
     * Transforms a single point.
     * @param point the point to transform
     * @return transformed point
     */
    public Point transform(Point point) {
        return org.proj4.Proj4Sedona.transform(fromProj, toProj, point, enforceAxis);
    }
    
    /**
     * Transforms multiple points in batch.
     * @param points the points to transform
     * @return list of transformed points
     */
    public List<Point> transformBatch(List<Point> points) {
        List<Point> results = new ArrayList<>(points.size());
        for (Point point : points) {
            Point transformed = transform(point);
            results.add(transformed);
        }
        return results;
    }
    
    /**
     * Transforms multiple points in batch with null filtering.
     * @param points the points to transform
     * @return list of transformed points (nulls filtered out)
     */
    public List<Point> transformBatchFiltered(List<Point> points) {
        List<Point> results = new ArrayList<>(points.size());
        for (Point point : points) {
            if (point != null) {
                Point transformed = transform(point);
                if (transformed != null) {
                    results.add(transformed);
                }
            }
        }
        return results;
    }
    
    /**
     * Transforms arrays of coordinates in batch.
     * @param xCoords array of x coordinates
     * @param yCoords array of y coordinates
     * @return array of transformed points
     */
    public Point[] transformArrays(double[] xCoords, double[] yCoords) {
        if (xCoords.length != yCoords.length) {
            throw new IllegalArgumentException("X and Y coordinate arrays must have the same length");
        }
        
        Point[] results = new Point[xCoords.length];
        for (int i = 0; i < xCoords.length; i++) {
            Point point = new Point(xCoords[i], yCoords[i]);
            results[i] = transform(point);
        }
        return results;
    }
    
    /**
     * Gets the source projection.
     * @return the source projection
     */
    public Projection getFromProjection() {
        return fromProj;
    }
    
    /**
     * Gets the destination projection.
     * @return the destination projection
     */
    public Projection getToProjection() {
        return toProj;
    }
}
