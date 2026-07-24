package org.datasyslab.proj4sedona.transform;

import org.datasyslab.proj4sedona.core.Point;

/**
 * Adjusts coordinates based on the axis order of a CRS.
 * Mirrors: lib/adjust_axis.js (post proj4js c5bb8e1, "Fix enforceAxis for z and
 * arbitrary orders")
 *
 * <p>Standard axis order is "enu" (Easting-Northing-Up). Some CRS use different
 * orders like "neu", "wsu", or even orders that place the vertical axis first
 * (e.g. "uen"); the per-position switch below handles any permutation.</p>
 */
public final class AdjustAxis {

    private AdjustAxis() {
        // Utility class
    }

    /**
     * Convert a point in the CRS's axis order to ENU (east/north/up) order.
     *
     * @param axis The axis specification (e.g., "enu", "neu", "uen")
     * @param point The input point in CRS axis order
     * @param hasZ Whether the point has a meaningful z coordinate
     * @return A new point in ENU order, or null if axis is invalid
     */
    public static Point adjustAxisToEnu(String axis, Point point, boolean hasZ) {
        if (axis == null || axis.length() != 3) {
            return null;
        }

        double[] in = {point.x, point.y, point.z};
        double outX = 0, outY = 0, outZ = 0;
        boolean hasOutZ = false;

        for (int i = 0; i < 3; i++) {
            if (i == 2 && !hasZ) {
                continue;
            }
            double v = in[i];
            switch (axis.charAt(i)) {
                case 'e': outX = v; break;
                case 'w': outX = -v; break;
                case 'n': outY = v; break;
                case 's': outY = -v; break;
                case 'u': outZ = v; hasOutZ = true; break;
                case 'd': outZ = -v; hasOutZ = true; break;
                default:
                    return null;
            }
        }

        Point result = new Point(outX, outY);
        if (hasOutZ || hasZ) {
            result.z = outZ;
        }
        result.m = point.m;
        return result;
    }

    /**
     * Convert a point in ENU (east/north/up) order to the CRS's axis order.
     *
     * @param axis The axis specification (e.g., "enu", "neu", "uen")
     * @param point The input point in ENU order
     * @param hasZ Whether the point has a meaningful z coordinate
     * @return A new point in CRS axis order, or null if axis is invalid
     */
    public static Point adjustAxisFromEnu(String axis, Point point, boolean hasZ) {
        if (axis == null || axis.length() != 3) {
            return null;
        }

        double[] out = new double[3];
        boolean hasOutZ = false;

        for (int i = 0; i < 3; i++) {
            if (i == 2 && !hasZ) {
                continue;
            }
            double v;
            switch (axis.charAt(i)) {
                case 'e': v = point.x; break;
                case 'w': v = -point.x; break;
                case 'n': v = point.y; break;
                case 's': v = -point.y; break;
                case 'u': v = point.z; break;
                case 'd': v = -point.z; break;
                default:
                    return null;
            }
            out[i] = v;
            if (i == 2) {
                hasOutZ = true;
            }
        }

        Point result = new Point(out[0], out[1]);
        if (hasOutZ || hasZ) {
            result.z = out[2];
        }
        result.m = point.m;
        return result;
    }

    /**
     * Adjust axis order for a point.
     *
     * @deprecated Use {@link #adjustAxisToEnu} (source CRS to ENU) or
     *     {@link #adjustAxisFromEnu} (ENU to destination CRS) instead; this shim
     *     keeps the pre-c5bb8e1 signature working.
     */
    @Deprecated
    public static Point adjust(String axis, boolean denorm, Point point, boolean hasZ) {
        return denorm
            ? adjustAxisFromEnu(axis, point, hasZ)
            : adjustAxisToEnu(axis, point, hasZ);
    }
}
