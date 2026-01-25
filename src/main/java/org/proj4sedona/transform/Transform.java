package org.proj4sedona.transform;

import org.proj4sedona.constants.Values;
import org.proj4sedona.core.DatumParams;
import org.proj4sedona.core.Point;
import org.proj4sedona.core.Proj;
import org.proj4sedona.datum.DatumTransform;
import org.proj4sedona.projection.ProjectionParams;

/**
 * Core transformation pipeline between two coordinate reference systems.
 * Mirrors: lib/transform.js
 * 
 * <p>This class orchestrates the complete transformation process from a source
 * projection to a destination projection. The transformation pipeline consists
 * of the following steps:</p>
 * 
 * <ol>
 *   <li><b>Datum routing</b>: If source and destination use different datums
 *       (neither being WGS84), transform through WGS84 as an intermediate step</li>
 *   <li><b>Axis adjustment</b>: Handle non-standard axis orders (e.g., "neu" for northing-easting-up)</li>
 *   <li><b>Inverse projection</b>: Convert source coordinates to geographic (lon/lat in radians)</li>
 *   <li><b>Prime meridian adjustment</b>: Shift for non-Greenwich prime meridians</li>
 *   <li><b>Datum transformation</b>: Apply 3-param, 7-param, or grid shift transformation</li>
 *   <li><b>Forward projection</b>: Convert geographic coordinates to destination projection</li>
 *   <li><b>Unit conversion</b>: Apply any unit conversion factors</li>
 *   <li><b>Final axis adjustment</b>: Apply destination axis order</li>
 * </ol>
 * 
 * <p>Example usage:</p>
 * <pre>
 * Proj source = new Proj("+proj=longlat +datum=WGS84");
 * Proj dest = new Proj("+proj=utm +zone=32 +datum=WGS84");
 * Point p = new Point(9.0 * Values.D2R, 48.0 * Values.D2R);  // Munich in radians
 * Point result = Transform.transform(source, dest, p);
 * </pre>
 */
public final class Transform {

    /** Cached WGS84 projection for datum transformations through WGS84 */
    private static volatile Proj wgs84;

    private Transform() {
        // Utility class - prevent instantiation
    }

    /**
     * Get or create the cached WGS84 projection instance.
     * Uses double-checked locking for thread safety.
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
     * Check if transformation through WGS84 is needed for datum shift.
     * 
     * <p>This is required when both source and destination have datum shift
     * parameters but neither is WGS84. In this case, we must transform:
     * source → WGS84 → destination.</p>
     *
     * @param source Source projection parameters
     * @param dest Destination projection parameters
     * @return true if WGS84 intermediate transformation is needed
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

        // Check if destination is WGS84-equivalent
        boolean destIsWGS84 = "WGS84".equalsIgnoreCase(dest.datumCode) || 
                              (destDatum != null && destDatum.isWgs84());

        // Check if source is WGS84-equivalent
        boolean sourceIsWGS84 = "WGS84".equalsIgnoreCase(source.datumCode) ||
                                (srcDatum != null && srcDatum.isWgs84());

        // If source needs shift and dest is not WGS84
        if (sourceNeedsShift && !destIsWGS84) {
            return true;
        }

        // If dest needs shift and source is not WGS84
        if (destNeedsShift && !sourceIsWGS84) {
            return true;
        }

        return false;
    }

    /**
     * Transform a point from source projection to destination projection.
     * 
     * <p>This is the main transformation method that handles the complete
     * pipeline including datum shifts, axis adjustments, and projection
     * forward/inverse operations.</p>
     *
     * @param source The source Proj object
     * @param dest The destination Proj object
     * @param point The point to transform (coordinates in source CRS units)
     * @param enforceAxis If true, apply axis order adjustments for non-ENU systems
     * @return The transformed point in destination CRS units, or null if transformation fails
     */
    public static Point transform(Proj source, Proj dest, Point point, boolean enforceAxis) {
        // Create a copy to avoid modifying the input
        Point p = point.copy();
        boolean hasZ = p.z != 0;

        // Validate input coordinates
        CheckSanity.check(p);

        ProjectionParams srcParams = source.getParams();
        ProjectionParams destParams = dest.getParams();

        // Step 1: Handle datum shift through WGS84 if needed
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

        // Step 2: Adjust for source axis order (e.g., "neu" to "enu")
        if (enforceAxis && srcParams.axis != null && !"enu".equals(srcParams.axis)) {
            p = AdjustAxis.adjust(srcParams.axis, false, p, hasZ);
            if (p == null) {
                return null;
            }
        }

        // Step 3: Transform source coordinates to geodetic (lon/lat in radians)
        if ("longlat".equals(srcParams.projName)) {
            // Already in lon/lat, just convert degrees to radians
            p = new Point(p.x * Values.D2R, p.y * Values.D2R, p.z);
            p.m = point.m;
        } else {
            // Apply unit conversion if needed
            if (srcParams.toMeter != null && srcParams.toMeter != 0 && srcParams.toMeter != 1) {
                p = new Point(p.x * srcParams.toMeter, p.y * srcParams.toMeter, p.z);
                p.m = point.m;
            }
            // Inverse projection: projected → geodetic
            p = source.inverse(p);
            if (p == null) {
                return null;
            }
        }

        // Step 4: Adjust for source prime meridian offset
        if (srcParams.fromGreenwich != null && srcParams.fromGreenwich != 0) {
            p.x += srcParams.fromGreenwich;
        }

        // Step 5: Datum transformation (geodetic source datum → geodetic dest datum)
        p = DatumTransform.transform(srcParams.datum, destParams.datum, p);
        if (p == null) {
            return null;
        }

        // Step 6: Adjust for destination prime meridian offset
        if (destParams.fromGreenwich != null && destParams.fromGreenwich != 0) {
            p = new Point(p.x - destParams.fromGreenwich, p.y, p.z);
            p.m = point.m;
        }

        // Step 7: Transform geodetic to destination coordinates
        if ("longlat".equals(destParams.projName)) {
            // Convert radians to degrees
            p = new Point(p.x * Values.R2D, p.y * Values.R2D, p.z);
            p.m = point.m;
        } else {
            // Forward projection: geodetic → projected
            p = dest.forward(p);
            if (p == null) {
                return null;
            }
            // Apply inverse unit conversion if needed
            if (destParams.toMeter != null && destParams.toMeter != 0 && destParams.toMeter != 1) {
                p = new Point(p.x / destParams.toMeter, p.y / destParams.toMeter, p.z);
            }
        }

        // Step 8: Adjust for destination axis order (e.g., "enu" to "neu")
        if (enforceAxis && destParams.axis != null && !"enu".equals(destParams.axis)) {
            p = AdjustAxis.adjust(destParams.axis, true, p, hasZ);
        }

        // Reset z if it wasn't in the original input
        if (p != null && !hasZ) {
            p.z = 0;
        }

        return p;
    }

    /**
     * Transform a point without axis enforcement.
     * 
     * <p>Convenience overload that calls {@link #transform(Proj, Proj, Point, boolean)}
     * with enforceAxis=false.</p>
     *
     * @param source The source Proj object
     * @param dest The destination Proj object
     * @param point The point to transform
     * @return The transformed point, or null if transformation fails
     */
    public static Point transform(Proj source, Proj dest, Point point) {
        return transform(source, dest, point, false);
    }
}
