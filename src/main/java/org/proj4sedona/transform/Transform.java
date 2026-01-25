package org.proj4sedona.transform;

import org.proj4sedona.constants.Values;
import org.proj4sedona.core.DatumParams;
import org.proj4sedona.core.Point;
import org.proj4sedona.core.Proj;
import org.proj4sedona.datum.DatumTransform;
import org.proj4sedona.projection.ProjectionParams;

/**
 * Core transformation pipeline between two projections.
 * Mirrors: lib/transform.js
 */
public final class Transform {

    private static volatile Proj wgs84;

    private Transform() {}

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

    public static Point transform(Proj source, Proj dest, Point point, boolean enforceAxis) {
        Point p = point.copy();
        boolean hasZ = p.z != 0;

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
            p = new Point(p.x * Values.D2R, p.y * Values.D2R, p.z);
            p.m = point.m;
        } else {
            if (srcParams.toMeter != null && srcParams.toMeter != 0 && srcParams.toMeter != 1) {
                p = new Point(p.x * srcParams.toMeter, p.y * srcParams.toMeter, p.z);
                p.m = point.m;
            }
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
        p = DatumTransform.transform(srcParams.datum, destParams.datum, p);
        if (p == null) {
            return null;
        }

        // Adjust for destination prime meridian
        if (destParams.fromGreenwich != null && destParams.fromGreenwich != 0) {
            p = new Point(p.x - destParams.fromGreenwich, p.y, p.z);
            p.m = point.m;
        }

        // Transform geodetic to destination
        if ("longlat".equals(destParams.projName)) {
            p = new Point(p.x * Values.R2D, p.y * Values.R2D, p.z);
            p.m = point.m;
        } else {
            p = dest.forward(p);
            if (p == null) {
                return null;
            }
            if (destParams.toMeter != null && destParams.toMeter != 0 && destParams.toMeter != 1) {
                p = new Point(p.x / destParams.toMeter, p.y / destParams.toMeter, p.z);
            }
        }

        // Adjust for destination axis order
        if (enforceAxis && destParams.axis != null && !"enu".equals(destParams.axis)) {
            p = AdjustAxis.adjust(destParams.axis, true, p, hasZ);
        }

        if (p != null && !hasZ) {
            p.z = 0;
        }

        return p;
    }

    public static Point transform(Proj source, Proj dest, Point point) {
        return transform(source, dest, point, false);
    }
}
