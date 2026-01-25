package org.proj4sedona.transform;

import org.proj4sedona.core.Point;

/**
 * Adjusts coordinates based on the axis order of a CRS.
 * Mirrors: lib/adjust_axis.js
 * 
 * Standard axis order is "enu" (Easting-Northing-Up).
 * Some CRS use different orders like "neu", "wsu", etc.
 */
public final class AdjustAxis {

    private AdjustAxis() {
        // Utility class
    }

    /**
     * Adjust axis order for a point.
     * 
     * @param axis The axis specification (e.g., "enu", "neu", "wsu")
     * @param denorm If true, converting from CRS to standard; if false, converting to CRS
     * @param point The input point
     * @param hasZ Whether the point has a meaningful z coordinate
     * @return A new point with adjusted coordinates, or null if axis is invalid
     */
    public static Point adjust(String axis, boolean denorm, Point point, boolean hasZ) {
        if (axis == null || axis.length() != 3) {
            return null;
        }

        double xin = point.x;
        double yin = point.y;
        double zin = point.z;

        double outX = 0, outY = 0, outZ = 0;
        boolean hasOutZ = false;

        for (int i = 0; i < 3; i++) {
            // Skip z if denormalizing and no z present
            if (denorm && i == 2 && !hasZ) {
                continue;
            }

            double v;
            char target;

            if (i == 0) {
                v = xin;
                char axisChar = axis.charAt(i);
                if (axisChar == 'e' || axisChar == 'w') {
                    target = 'x';
                } else {
                    target = 'y';
                }
            } else if (i == 1) {
                v = yin;
                char axisChar = axis.charAt(i);
                if (axisChar == 'n' || axisChar == 's') {
                    target = 'y';
                } else {
                    target = 'x';
                }
            } else {
                v = zin;
                target = 'z';
            }

            char axisChar = axis.charAt(i);
            switch (axisChar) {
                case 'e':
                    if (target == 'x') outX = v;
                    else outY = v;
                    break;
                case 'w':
                    if (target == 'x') outX = -v;
                    else outY = -v;
                    break;
                case 'n':
                    if (target == 'y') outY = v;
                    else outX = v;
                    break;
                case 's':
                    if (target == 'y') outY = -v;
                    else outX = -v;
                    break;
                case 'u':
                    if (hasZ) {
                        outZ = v;
                        hasOutZ = true;
                    }
                    break;
                case 'd':
                    if (hasZ) {
                        outZ = -v;
                        hasOutZ = true;
                    }
                    break;
                default:
                    // Unknown axis character
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
}
