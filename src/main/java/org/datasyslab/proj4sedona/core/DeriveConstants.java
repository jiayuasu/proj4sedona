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
            // PROJ treats +a without +b/+rf as spherical shorthand. Current
            // proj4js happens to carry b=undefined/es=NaN, which selects the same
            // spherical traditional-TM path but leaves other consumers with
            // unusable axes. Prefer the executable, serializable PROJ semantics.
            if (b == null && rf == null) {
                bVal = aVal;
                isSphere = true;
            } else {
                bVal = b != null ? b : 0;
            }
            rfVal = rf;
        }

        // If rf is given but not b, calculate b
        if (rfVal != null && rfVal != 0 && bVal == 0) {
            bVal = (1.0 - 1.0 / rfVal) * aVal;
        }

        // If b is given but not rf, calculate rf (e.g., Airy defined by a,b)
        if ((rfVal == null || rfVal == 0) && bVal > 0 && Math.abs(aVal - bVal) > Values.EPSLN) {
            rfVal = aVal / (aVal - bVal);
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
     * Look up ellipsoid and return its parameters as [a, b, rf].
     *
     * <p>Resolves against the full Ellipsoid registry, as proj4js's
     * deriveConstants.sphere does with its complete ellipsoid table. A hardcoded
     * 10-entry switch previously shadowed the registry here, silently defaulting
     * the other 34 registered codes to WGS84 (issue #105) — +ellps=clrk80ign
     * parsed 112 m off in the semi-major axis. Unknown codes still default to
     * WGS84, matching proj4js.</p>
     */
    private static Double[] getEllipsoidValues(String key) {
        org.datasyslab.proj4sedona.constants.Ellipsoid ellipsoid =
            org.datasyslab.proj4sedona.constants.Ellipsoid.get(key);
        if (ellipsoid == null) {
            ellipsoid = org.datasyslab.proj4sedona.constants.Ellipsoid.WGS84;
        }
        // A sphere entry (a == b) derives rf = a / (a - b) = Infinity in the
        // registry; the sphere() caller expects null there, as for any
        // b-defined ellipsoid whose rf is not meaningful.
        double rf = ellipsoid.getRf();
        return new Double[]{
            ellipsoid.getA(),
            ellipsoid.getB(),
            rf > 0 && !Double.isInfinite(rf) ? rf : null
        };
    }
}
