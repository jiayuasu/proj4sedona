package org.proj4sedona.transform;

import org.proj4sedona.core.Point;

/**
 * Validates that coordinate values are finite (not NaN or Infinite).
 * Mirrors: lib/checkSanity.js
 */
public final class CheckSanity {

    private CheckSanity() {
        // Utility class
    }

    /**
     * Check that point coordinates are finite numbers.
     * 
     * @param point The point to validate
     * @throws IllegalArgumentException if coordinates are not finite
     */
    public static void check(Point point) {
        checkCoord(point.x, "x");
        checkCoord(point.y, "y");
    }

    /**
     * Check that a coordinate value is finite.
     */
    private static void checkCoord(double num, String name) {
        if (!Double.isFinite(num)) {
            throw new IllegalArgumentException(
                "Coordinate '" + name + "' must be a finite number, got: " + num
            );
        }
    }
}
