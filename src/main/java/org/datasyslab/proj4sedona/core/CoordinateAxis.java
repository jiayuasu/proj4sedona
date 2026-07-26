package org.datasyslab.proj4sedona.core;

import java.util.Objects;

/**
 * Coordinate-system axis metadata retained from WKT2 and PROJJSON definitions.
 *
 * <p>The compact PROJ {@code +axis=} value remains the representation used by
 * coordinate transforms. This value object keeps the richer standard-format
 * metadata needed to reproduce axis definitions that cannot be expressed by a
 * PROJ axis permutation, such as polar axes qualified by meridians.</p>
 */
public final class CoordinateAxis {

    private final String name;
    private final String abbreviation;
    private final String direction;
    private final Integer order;
    private final Unit unit;
    private final Meridian meridian;

    public CoordinateAxis(
            String name,
            String abbreviation,
            String direction,
            Integer order,
            Unit unit,
            Meridian meridian) {
        this.name = Objects.requireNonNull(name, "name");
        this.abbreviation = abbreviation;
        this.direction = Objects.requireNonNull(direction, "direction");
        this.order = order;
        this.unit = unit;
        this.meridian = meridian;
    }

    public String getName() {
        return name;
    }

    public String getAbbreviation() {
        return abbreviation;
    }

    public String getDirection() {
        return direction;
    }

    public Integer getOrder() {
        return order;
    }

    public Unit getUnit() {
        return unit;
    }

    public Meridian getMeridian() {
        return meridian;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CoordinateAxis)) {
            return false;
        }
        CoordinateAxis that = (CoordinateAxis) other;
        return Objects.equals(name, that.name)
            && Objects.equals(abbreviation, that.abbreviation)
            && Objects.equals(direction, that.direction)
            && Objects.equals(order, that.order)
            && Objects.equals(unit, that.unit)
            && Objects.equals(meridian, that.meridian);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, abbreviation, direction, order, unit, meridian);
    }

    @Override
    public String toString() {
        return "CoordinateAxis{"
            + "name='" + name + '\''
            + ", abbreviation='" + abbreviation + '\''
            + ", direction='" + direction + '\''
            + ", order=" + order
            + ", unit=" + unit
            + ", meridian=" + meridian
            + '}';
    }

    /** Unit metadata attached to an axis or axis meridian. */
    public static final class Unit {

        private final String type;
        private final String name;
        private final Double conversionFactor;

        public Unit(String type, String name, Double conversionFactor) {
            this.type = type;
            this.name = Objects.requireNonNull(name, "name");
            this.conversionFactor = conversionFactor;
        }

        public String getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        public Double getConversionFactor() {
            return conversionFactor;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Unit)) {
                return false;
            }
            Unit that = (Unit) other;
            return Objects.equals(type, that.type)
                && Objects.equals(name, that.name)
                && Objects.equals(conversionFactor, that.conversionFactor);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, name, conversionFactor);
        }

        @Override
        public String toString() {
            return "Unit{"
                + "type='" + type + '\''
                + ", name='" + name + '\''
                + ", conversionFactor=" + conversionFactor
                + '}';
        }
    }

    /** Meridian qualifying a polar axis direction. */
    public static final class Meridian {

        private final double longitude;
        private final Unit unit;

        public Meridian(double longitude, Unit unit) {
            this.longitude = longitude;
            this.unit = unit;
        }

        public double getLongitude() {
            return longitude;
        }

        public Unit getUnit() {
            return unit;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Meridian)) {
                return false;
            }
            Meridian that = (Meridian) other;
            return Double.compare(longitude, that.longitude) == 0
                && Objects.equals(unit, that.unit);
        }

        @Override
        public int hashCode() {
            return Objects.hash(longitude, unit);
        }

        @Override
        public String toString() {
            return "Meridian{"
                + "longitude=" + longitude
                + ", unit=" + unit
                + '}';
        }
    }
}
