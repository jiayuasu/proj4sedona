package org.proj4sedona.transform;

import org.proj4sedona.constants.Values;
import org.proj4sedona.core.DatumParams;
import org.proj4sedona.core.Point;
import org.proj4sedona.core.Proj;
import org.proj4sedona.projection.ProjectionParams;

/**
 * Core transformation pipeline between two projections.
 * Mirrors: lib/transform.js
 * 
 * The transformation follows these steps:
 * 1. Validate input coordinates
 * 2. Handle axis order if non-standard
 * 3. Convert to geodetic (lon/lat in radians)
 * 4. Apply datum transformation if needed
 * 5. Convert to target projection
 * 6. Handle axis order for target if non-standard
 */
public final class Transform {

    // WGS84 projection for datum shift intermediary
    private static volatile Proj wgs84;

    private Transform() {
        // Utility class
    }

    /**
     * Get or create the WGS84 projection.
     */
    private static Proj getWgs84() {
        if (wgs84 == null) {
            synchronized (Transform.class) {
                if (wgs84 == null) {
                    wgs84 = new Proj("+proj=longlat +datum=WGS84");
                }
            }
        }
        return wgs84;
    }

    /**
     * Check if datum shift through WGS84 is needed.
     * Mirrors: lib/transform.js checkNotWGS
     */
    private static boolean checkNotWGS(ProjectionParams source, ProjectionParams dest) {
        DatumParams srcDatum = source.datum;
        DatumParams destDatum = dest.datum;

        boolean sourceNeedsShift = srcDatum != null && 
            (srcDatum.getDatumType() == Values.PJD_3PARAM || 
             srcDatum.getDatumType() == Values.PJD_7PARAM || 
             srcDatum.getDatumType() == Values.PJD_GRIDSHIFT);

        boolean destNeedsShift = destDatum != null && 
            (destDatum.getDatumType() == Values.PJD_3PARAM || 
             destDatum.getDatumType() == Values.PJD_7PARAM || 
             destDatum.getDatumType() == Values.PJD_GRIDSHIFT);

        // If source needs shift and dest is not WGS84
        if (sourceNeedsShift && !"WGS84".equalsIgnoreCase(dest.srsCode)) {
            return true;
        }

        // If dest needs shift and source is not WGS84
        if (destNeedsShift && !"WGS84".equalsIgnoreCase(source.srsCode)) {
            return true;
        }

        return false;
    }

    /**
     * Transform a point from source to destination projection.
     * 
     * @param source Source projection
     * @param dest Destination projection
     * @param point Input point (will be cloned, not modified)
     * @param enforceAxis Whether to enforce axis order adjustments
     * @return Transformed point, or null if transformation failed
     */
    public static Point transform(Proj source, Proj dest, Point point, boolean enforceAxis) {
        // Clone the point to avoid modifying input
        Point p = point.copy();
        boolean hasZ = p.z != 0;

        // Validate coordinates
        CheckSanity.check(p);

        ProjectionParams srcParams = source.getParams();
        ProjectionParams destParams = dest.getParams();

        // Handle datum shift through WGS84 if needed
        if (srcParams.datum != null && destParams.datum != null && 
            checkNotWGS(srcParams, destParams)) {
            Proj wgs84Proj = getWgs84();
            p = transform(source, wgs84Proj, p, enforceAxis);
            if (p == null) {
                return null;
            }
            source = wgs84Proj;
            srcParams = source.getParams();
        }

        // Adjust for source axis order
        if (enforceAxis && srcParams.axis != null && !"enu".equals(srcParams.axis)) {
            p = AdjustAxis.adjust(srcParams.axis, false, p, hasZ);
            if (p == null) {
                return null;
            }
        }

        // Transform source to geodetic (lon/lat radians)
        if ("longlat".equals(srcParams.projName)) {
            // Already in degrees, convert to radians
            p = new Point(
                p.x * Values.D2R,
                p.y * Values.D2R,
                p.z
            );
            p.m = point.m;
        } else {
            // Apply unit conversion if needed
            if (srcParams.toMeter != null && srcParams.toMeter != 0 && srcParams.toMeter != 1) {
                p = new Point(
                    p.x * srcParams.toMeter,
                    p.y * srcParams.toMeter,
                    p.z
                );
                p.m = point.m;
            }

            // Inverse projection: projected -> geodetic
            p = source.inverse(p);
            if (p == null) {
                return null;
            }
        }

        // Adjust for source prime meridian
        if (srcParams.fromGreenwich != null && srcParams.fromGreenwich != 0) {
            p.x += srcParams.fromGreenwich;
        }

        // Datum transformation
        p = datumTransform(srcParams.datum, destParams.datum, p);
        if (p == null) {
            return null;
        }

        // Adjust for destination prime meridian
        if (destParams.fromGreenwich != null && destParams.fromGreenwich != 0) {
            p = new Point(
                p.x - destParams.fromGreenwich,
                p.y,
                p.z
            );
            p.m = point.m;
        }

        // Transform geodetic to destination
        if ("longlat".equals(destParams.projName)) {
            // Convert radians to degrees
            p = new Point(
                p.x * Values.R2D,
                p.y * Values.R2D,
                p.z
            );
            p.m = point.m;
        } else {
            // Forward projection: geodetic -> projected
            p = dest.forward(p);
            if (p == null) {
                return null;
            }

            // Apply unit conversion if needed
            if (destParams.toMeter != null && destParams.toMeter != 0 && destParams.toMeter != 1) {
                p = new Point(
                    p.x / destParams.toMeter,
                    p.y / destParams.toMeter,
                    p.z
                );
            }
        }

        // Adjust for destination axis order
        if (enforceAxis && destParams.axis != null && !"enu".equals(destParams.axis)) {
            p = AdjustAxis.adjust(destParams.axis, true, p, hasZ);
        }

        // Remove z if input didn't have it
        if (p != null && !hasZ) {
            p.z = 0;
        }

        return p;
    }

    /**
     * Transform a point from source to destination projection (without axis enforcement).
     */
    public static Point transform(Proj source, Proj dest, Point point) {
        return transform(source, dest, point, false);
    }

    /**
     * Datum transformation between two datums.
     * This is a placeholder that will be expanded in Phase 7.
     * Currently handles WGS84 <-> WGS84 and same datum cases.
     * 
     * Mirrors: lib/datum_transform.js
     */
    private static Point datumTransform(DatumParams source, DatumParams dest, Point point) {
        // If either datum is null or NODATUM, pass through
        if (source == null || dest == null) {
            return point;
        }

        if (source.isNoDatum() || dest.isNoDatum()) {
            return point;
        }

        // If both are WGS84 (or equivalent), no transform needed
        if (source.isWgs84() && dest.isWgs84()) {
            return point;
        }

        // TODO: Implement full datum transform in Phase 7
        // For now, we pass through - this is correct for same-datum transformations
        // but will give slightly incorrect results for different datums
        // 
        // Full implementation will include:
        // - 3-parameter Molodensky transformation
        // - 7-parameter Helmert transformation  
        // - Grid shift (NAD27->NAD83, etc.)

        // Check if datums are the same (compare ellipsoid params)
        if (Math.abs(source.getA() - dest.getA()) < 1e-10 &&
            Math.abs(source.getB() - dest.getB()) < 1e-10) {
            return point;
        }

        // Different datums - would need transformation
        // For now, return the point as-is (this is a known limitation)
        return point;
    }
}
