package org.datasyslab.proj4sedona.core;

import org.datasyslab.proj4sedona.constants.Values;

/**
 * Derive ellipsoid and eccentricity constants from projection parameters.
 * Mirrors: lib/deriveConstants.js
 */
public final class DeriveConstants {

    private DeriveConstants() {
        // Utility class
    }

    /**
     * Result of sphere calculation.
     */
    public static class SphereResult {
        public final double a;       // Semi-major axis
        public final double b;       // Semi-minor axis
        public final double rf;      // Inverse flattening
        public final boolean sphere; // True if this is a sphere (a == b)

        public SphereResult(double a, double b, double rf, boolean sphere) {
            this.a = a;
            this.b = b;
            this.rf = rf;
            this.sphere = sphere;
        }
    }

    /**
     * Result of eccentricity calculation.
     */
    public static class EccentricityResult {
        public final double es;   // Eccentricity squared
        public final double e;    // Eccentricity
        public final double ep2;  // Second eccentricity squared

        public EccentricityResult(double es, double e, double ep2) {
            this.es = es;
            this.e = e;
            this.ep2 = ep2;
        }
    }

    /**
     * Derive sphere parameters from input values.
     * Mirrors: lib/deriveConstants.js sphere()
     *
     * @param a Semi-major axis (can be null)
     * @param b Semi-minor axis (can be null)
     * @param rf Inverse flattening (can be null)
     * @param ellps Ellipsoid name (can be null)
     * @param sphere Whether to force sphere (can be null)
     * @return SphereResult with derived values
     */
    public static SphereResult sphere(Double a, Double b, Double rf, String ellps, Boolean sphere) {
        double aVal;
        double bVal;
        Double rfVal;
        boolean isSphere = sphere != null && sphere;

        if (a == null) {
            // Look up ellipsoid by name
            Double[] ellipsoidParams = getEllipsoidParams(ellps);
            aVal = ellipsoidParams[0];
            bVal = ellipsoidParams[1] != null ? ellipsoidParams[1] : 0;
            rfVal = ellipsoidParams[2];
        } else {
            aVal = a;
            bVal = b != null ? b : 0;
            rfVal = rf;
        }

        // If rf is given but not b, calculate b
        if (rfVal != null && rfVal != 0 && bVal == 0) {
            bVal = (1.0 - 1.0 / rfVal) * aVal;
        }

        // Check if this is effectively a sphere
        // Only check rf == 0 if rf was explicitly provided as 0 (not null)
        boolean rfIsZero = (rfVal != null && rfVal == 0);
        if (rfIsZero || Math.abs(aVal - bVal) < Values.EPSLN) {
            isSphere = true;
            bVal = aVal;
        }

        return new SphereResult(aVal, bVal, rfVal != null ? rfVal : 0, isSphere);
    }

    /**
     * Calculate eccentricity values from ellipsoid parameters.
     * Mirrors: lib/deriveConstants.js eccentricity()
     *
     * @param a Semi-major axis
     * @param b Semi-minor axis
     * @param rf Inverse flattening (unused but kept for API compatibility)
     * @param useAuthalicRadius Whether to use authalic radius (R_A flag)
     * @return EccentricityResult with calculated values
     */
    public static EccentricityResult eccentricity(double a, double b, double rf, Boolean useAuthalicRadius) {
        double a2 = a * a;
        double b2 = b * b;
        double es = (a2 - b2) / a2;  // e^2
        double e = 0;

        if (useAuthalicRadius != null && useAuthalicRadius) {
            // Authalic sphere: adjust a and set es to 0
            double aNew = a * (1 - es * (Values.SIXTH + es * (Values.RA4 + es * Values.RA6)));
            double a2New = aNew * aNew;
            es = 0;
            // Recalculate ep2 with adjusted values
            double ep2 = (a2New - b2) / b2;
            return new EccentricityResult(es, e, ep2);
        } else {
            e = Math.sqrt(es);
        }

        double ep2 = (a2 - b2) / b2;  // Second eccentricity squared

        return new EccentricityResult(es, e, ep2);
    }

    /**
     * Get ellipsoid parameters by name.
     * Returns [a, b, rf] for the named ellipsoid, defaults to WGS84.
     */
    private static Double[] getEllipsoidParams(String ellps) {
        if (ellps == null || ellps.isEmpty()) {
            ellps = "wgs84";
        }
        return getEllipsoidValues(ellps.toLowerCase());
    }

    /**
     * Look up ellipsoid and return its parameters.
     */
    private static Double[] getEllipsoidValues(String key) {
        // Common ellipsoids - [a, b, rf]
        switch (key) {
            case "wgs84":
                return new Double[]{6378137.0, 6356752.314245179, 298.257223563};
            case "grs80":
                return new Double[]{6378137.0, 6356752.314140356, 298.257222101};
            case "clrk66":
                return new Double[]{6378206.4, 6356583.8, null};
            case "clrk80":
            case "clark80":
                return new Double[]{6378249.145, 6356514.966398753, 293.4663};
            case "bessel":
                return new Double[]{6377397.155, 6356078.962818189, 299.1528128};
            case "intl":
                return new Double[]{6378388.0, 6356911.946127947, 297.0};
            case "airy":
                return new Double[]{6377563.396, 6356256.91, null};
            case "mod_airy":
                return new Double[]{6377340.189, 6356034.446, null};
            case "krass":
                return new Double[]{6378245.0, 6356863.018773047, 298.3};
            case "sphere":
                return new Double[]{6370997.0, 6370997.0, null};
            default:
                // Default to WGS84
                return new Double[]{6378137.0, 6356752.314245179, 298.257223563};
        }
    }
}
