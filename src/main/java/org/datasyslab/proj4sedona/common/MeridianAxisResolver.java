package org.datasyslab.proj4sedona.common;

import java.util.List;
import java.util.Locale;
import org.datasyslab.proj4sedona.constants.Units;
import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.CoordinateAxis;
import org.datasyslab.proj4sedona.projection.ProjectionParams;

/**
 * Validates meridian-qualified polar axes and resolves their coordinate roles.
 *
 * <p>A compact PROJ axis string cannot distinguish two horizontal axes that
 * both point north or both point south. The retained WKT2/PROJJSON meridians do:
 * relative to the projection's central meridian, they identify which native
 * coordinate is easting and which is northing. This class is the shared policy
 * used by both serialization and optional axis enforcement.</p>
 */
public final class MeridianAxisResolver {

    private MeridianAxisResolver() {
        // Utility class.
    }

    /**
     * Whether the CRS needs its retained meridian metadata resolved.
     *
     * <p>Ordinary coordinate-axis metadata alone does not trigger this path.
     * This keeps conventional polar CRSs, whose axes are representable as a
     * normal PROJ permutation, on the ordinary axis-handling path.</p>
     */
    public static boolean requiresResolution(ProjectionParams params) {
        String axis = effectiveAxis(params);
        return hasMeridianMetadata(params)
            || "nnu".equals(axis)
            || "ssu".equals(axis);
    }

    /** Return whether at least one retained horizontal axis has a meridian. */
    public static boolean hasMeridianMetadata(ProjectionParams params) {
        if (params.coordinateAxes == null) {
            return false;
        }
        for (CoordinateAxis axis : params.coordinateAxes) {
            if (axis != null && axis.getMeridian() != null) {
                return true;
            }
        }
        return false;
    }

    /** Resolve using the compact axis value stored on the projection. */
    public static Resolution resolve(ProjectionParams params) {
        return resolve(params, effectiveAxis(params));
    }

    /**
     * Validate and resolve a duplicate-direction polar axis definition.
     *
     * @param params projection parameters containing retained axis metadata
     * @param axisOrder compact PROJ axis string to verify against the metadata
     * @return a valid conventional permutation, or a descriptive validation error
     */
    public static Resolution resolve(
            ProjectionParams params, String axisOrder) {
        String normalizedAxis = axisOrder == null
            ? "enu" : axisOrder.toLowerCase(Locale.ROOT);
        if (!"nnu".equals(normalizedAxis) && !"ssu".equals(normalizedAxis)) {
            return Resolution.invalid(
                "Meridian-qualified axes require two matching north or south directions");
        }
        List<CoordinateAxis> axes = params.coordinateAxes;
        if (axes == null || axes.size() != 2) {
            return Resolution.invalid(
                "Axis " + normalizedAxis
                    + " requires two retained meridian-qualified horizontal axes");
        }
        if (params.coordinateSystemType == null
                || !"Cartesian".equalsIgnoreCase(params.coordinateSystemType)) {
            return Resolution.invalid(
                "Meridian-qualified projected axes require a Cartesian coordinate system");
        }
        if (!isPolarLatitude(params.lat0)) {
            return Resolution.invalid(
                "Meridian-qualified horizontal axes require a polar latitude of origin");
        }

        boolean northPole = params.lat0 > 0.0;
        String expectedDirection = northPole ? "south" : "north";
        boolean anyOrder = false;
        boolean allOrder = true;
        Role firstRole = null;
        Role secondRole = null;
        double expectedLinearFactor;
        try {
            expectedLinearFactor = projectedUnitToMeter(params);
        } catch (IllegalArgumentException error) {
            return Resolution.invalid(error.getMessage());
        }

        for (int i = 0; i < axes.size(); i++) {
            CoordinateAxis coordinateAxis = axes.get(i);
            if (coordinateAxis == null || coordinateAxis.getDirection() == null
                    || !expectedDirection.equalsIgnoreCase(
                        coordinateAxis.getDirection())) {
                return Resolution.invalid(
                    "Polar axis directions must both be " + expectedDirection
                        + " at the " + (northPole ? "north" : "south") + " pole");
            }

            char parsedDirection =
                mapStandardAxisDirection(coordinateAxis.getDirection());
            if (parsedDirection != normalizedAxis.charAt(i)) {
                return Resolution.invalid(
                    "Retained coordinate-axis order does not match axis "
                        + normalizedAxis);
            }

            Integer order = coordinateAxis.getOrder();
            anyOrder |= order != null;
            allOrder &= order != null;
            if (order != null && order != i + 1) {
                return Resolution.invalid(
                    "WKT axis ORDER must match its position in the coordinate system");
            }

            CoordinateAxis.Unit linearUnit = coordinateAxis.getUnit();
            String linearUnitError = validateCoordinateAxisUnit(
                linearUnit, "LinearUnit", "horizontal axis");
            if (linearUnitError != null) {
                return Resolution.invalid(linearUnitError);
            }
            double linearFactor = linearUnit.getConversionFactor();
            if (!sameUnitFactor(linearFactor, expectedLinearFactor)) {
                return Resolution.invalid(
                    "Horizontal-axis units must match the projected CRS unit");
            }

            CoordinateAxis.Meridian meridian = coordinateAxis.getMeridian();
            if (meridian == null || !Double.isFinite(meridian.getLongitude())) {
                return Resolution.invalid(
                    "Each duplicate polar axis requires a finite meridian");
            }
            String angularUnitError = validateCoordinateAxisUnit(
                meridian.getUnit(), "AngularUnit", "axis meridian");
            if (angularUnitError != null) {
                return Resolution.invalid(angularUnitError);
            }

            Role role = role(params, coordinateAxis);
            if (role == Role.UNKNOWN) {
                return Resolution.invalid(
                    "Axis meridians must identify one easting and one northing axis");
            }
            if (i == 0) {
                firstRole = role;
            } else {
                secondRole = role;
            }
        }

        if (anyOrder != allOrder) {
            return Resolution.invalid(
                "WKT axis ORDER must be present on every axis or none");
        }
        if (firstRole == secondRole) {
            return Resolution.invalid(
                "Polar axes must identify one easting and one northing axis");
        }

        char first = firstRole == Role.EASTING ? 'e' : 'n';
        char second = secondRole == Role.EASTING ? 'e' : 'n';
        return Resolution.valid(
            new String(new char[]{first, second, normalizedAxis.charAt(2)}));
    }

    /**
     * Determine an individual horizontal axis role from its meridian geometry.
     *
     * <p>The role is positive easting/northing; the duplicate north/south
     * direction tokens describe polar geometry and are not sign inversions.</p>
     */
    public static Role role(
            ProjectionParams params, CoordinateAxis axis) {
        if (axis == null || axis.getMeridian() == null
                || axis.getMeridian().getUnit() == null
                || axis.getMeridian().getUnit().getConversionFactor() == null
                || !isPolarLatitude(params.lat0)) {
            return Role.UNKNOWN;
        }
        CoordinateAxis.Meridian meridian = axis.getMeridian();
        double actual = meridian.getLongitude()
            * meridian.getUnit().getConversionFactor();
        if (!Double.isFinite(actual)) {
            return Role.UNKNOWN;
        }
        double central = params.long0 != null ? params.long0 : 0.0;
        double easting = central + Values.HALF_PI;
        double northing = central + (params.lat0 > 0.0 ? Math.PI : 0.0);
        boolean matchesEasting = sameLongitude(actual, easting);
        boolean matchesNorthing = sameLongitude(actual, northing);
        if (matchesEasting && !matchesNorthing) {
            return Role.EASTING;
        }
        if (matchesNorthing && !matchesEasting) {
            return Role.NORTHING;
        }
        return Role.UNKNOWN;
    }

    /** Whether a latitude in radians denotes either pole within library tolerance. */
    public static boolean isPolarLatitude(Double latitude) {
        return latitude != null
            && Double.isFinite(latitude)
            && Math.abs(Math.abs(latitude) - Values.HALF_PI) <= Values.EPSLN;
    }

    /**
     * Resolve the projected linear-unit factor using the same precedence as CRS
     * serialization and meridian-axis validation.
     */
    public static double projectedUnitToMeter(ProjectionParams params) {
        double toMeter;
        if (params.units != null) {
            Double registered = Units.getToMeter(params.units);
            if (registered != null) {
                toMeter = registered;
            } else {
                toMeter = params.toMeter != null ? params.toMeter : 1.0;
            }
        } else {
            toMeter = params.toMeter != null ? params.toMeter : 1.0;
        }
        if (!Double.isFinite(toMeter) || toMeter <= 0.0) {
            throw new IllegalArgumentException(
                "Linear unit conversion factor must be finite and greater than zero: "
                    + toMeter);
        }
        return toMeter;
    }

    private static String effectiveAxis(ProjectionParams params) {
        return params.axis != null
            ? params.axis.toLowerCase(Locale.ROOT) : "enu";
    }

    private static String validateCoordinateAxisUnit(
            CoordinateAxis.Unit unit, String expectedType, String label) {
        if (unit == null || unit.getConversionFactor() == null
                || !Double.isFinite(unit.getConversionFactor())
                || unit.getConversionFactor() <= 0.0) {
            return "The " + label + " requires a positive finite unit factor";
        }
        if (unit.getType() != null
                && !expectedType.equalsIgnoreCase(unit.getType())) {
            return "The " + label + " must use a " + expectedType;
        }
        if (unit.getName() == null || unit.getName().trim().isEmpty()) {
            return "The " + label + " requires a unit name";
        }
        return null;
    }

    private static boolean sameUnitFactor(double left, double right) {
        return Math.abs(left - right)
            <= 1e-12 * Math.max(Math.max(Math.abs(left), Math.abs(right)), 1.0);
    }

    private static boolean sameLongitude(double left, double right) {
        return Math.abs(Math.IEEEremainder(left - right, 2.0 * Math.PI))
            <= Values.EPSLN;
    }

    private static char mapStandardAxisDirection(String direction) {
        if ("north".equalsIgnoreCase(direction)) {
            return 'n';
        }
        if ("south".equalsIgnoreCase(direction)) {
            return 's';
        }
        if ("east".equalsIgnoreCase(direction)) {
            return 'e';
        }
        if ("west".equalsIgnoreCase(direction)) {
            return 'w';
        }
        return '\0';
    }

    /** Geometric horizontal-coordinate role. */
    public enum Role {
        EASTING,
        NORTHING,
        UNKNOWN
    }

    /** Validated conventional axis permutation or a validation error. */
    public static final class Resolution {

        private final String conventionalAxis;
        private final String error;

        private Resolution(String conventionalAxis, String error) {
            this.conventionalAxis = conventionalAxis;
            this.error = error;
        }

        private static Resolution valid(String conventionalAxis) {
            return new Resolution(conventionalAxis, null);
        }

        private static Resolution invalid(String error) {
            return new Resolution(null, error);
        }

        public boolean isValid() {
            return error == null;
        }

        /**
         * Return an ordinary PROJ permutation such as {@code enu} or {@code neu}.
         */
        public String getConventionalAxis() {
            return conventionalAxis;
        }

        public String getError() {
            return error;
        }
    }
}
