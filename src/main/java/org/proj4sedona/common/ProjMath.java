package org.proj4sedona.common;

import org.proj4sedona.constants.Values;

/**
 * Common mathematical functions for projections.
 * Mirrors: lib/common/*.js
 */
public final class ProjMath {

    private ProjMath() {
        // Utility class
    }

    /**
     * Sign function.
     * Mirrors: lib/common/sign.js
     */
    public static double sign(double x) {
        return x < 0 ? -1 : 1;
    }

    /**
     * Adjust longitude to be within -PI to PI.
     * Mirrors: lib/common/adjust_lon.js
     *
     * @param x Longitude in radians
     * @param skipAdjust If true, skip the adjustment (for over flag)
     * @return Adjusted longitude
     */
    public static double adjustLon(double x, Boolean skipAdjust) {
        if (skipAdjust != null && skipAdjust) {
            return x;
        }
        return (Math.abs(x) <= Values.SPI) ? x : (x - (sign(x) * Values.TWO_PI));
    }

    /**
     * Adjust longitude (default: do adjust).
     */
    public static double adjustLon(double x) {
        return adjustLon(x, false);
    }

    /**
     * Compute the constant small m which is the radius of
     * a parallel of latitude, phi, divided by the semimajor axis.
     * Mirrors: lib/common/msfnz.js
     *
     * @param eccent Eccentricity
     * @param sinphi Sin of latitude
     * @param cosphi Cos of latitude
     * @return m value
     */
    public static double msfnz(double eccent, double sinphi, double cosphi) {
        double con = eccent * sinphi;
        return cosphi / Math.sqrt(1 - con * con);
    }

    /**
     * Compute the constant small t for use in the forward computations.
     * Mirrors: lib/common/tsfnz.js
     *
     * @param eccent Eccentricity
     * @param phi Latitude in radians
     * @param sinphi Sin of latitude
     * @return t value
     */
    public static double tsfnz(double eccent, double phi, double sinphi) {
        double con = eccent * sinphi;
        double com = 0.5 * eccent;
        con = Math.pow(((1 - con) / (1 + con)), com);
        return Math.tan(0.5 * (Values.HALF_PI - phi)) / con;
    }

    /**
     * Compute the latitude angle, phi2, for the inverse of the Mercator projection.
     * Mirrors: lib/common/phi2z.js
     *
     * @param eccent Eccentricity
     * @param ts t value from inverse
     * @return Latitude in radians, or -9999 if no convergence
     */
    public static double phi2z(double eccent, double ts) {
        double eccnth = 0.5 * eccent;
        double con, dphi;
        double phi = Values.HALF_PI - 2 * Math.atan(ts);
        
        for (int i = 0; i <= 15; i++) {
            con = eccent * Math.sin(phi);
            dphi = Values.HALF_PI - 2 * Math.atan(ts * Math.pow(((1 - con) / (1 + con)), eccnth)) - phi;
            phi += dphi;
            if (Math.abs(dphi) <= 0.0000000001) {
                return phi;
            }
        }
        // No convergence
        return -9999;
    }

    /**
     * Adjust latitude to be within -PI/2 to PI/2.
     */
    public static double adjustLat(double x) {
        return (Math.abs(x) < Values.HALF_PI) ? x : (x - (sign(x) * Math.PI));
    }

    /**
     * Calculate q used in authalic calculations.
     */
    public static double qsfnz(double eccent, double sinphi) {
        if (eccent > 1.0e-7) {
            double con = eccent * sinphi;
            return (1 - eccent * eccent) * (sinphi / (1 - con * con) - 
                   (0.5 / eccent) * Math.log((1 - con) / (1 + con)));
        } else {
            return 2 * sinphi;
        }
    }

    /**
     * Calculate e0 used in meridional distance calculations.
     */
    public static double e0fn(double x) {
        return 1 - 0.25 * x * (1 + x / 16 * (3 + 1.25 * x));
    }

    /**
     * Calculate e1 used in meridional distance calculations.
     */
    public static double e1fn(double x) {
        return 0.375 * x * (1 + 0.25 * x * (1 + 0.46875 * x));
    }

    /**
     * Calculate e2 used in meridional distance calculations.
     */
    public static double e2fn(double x) {
        return 0.05859375 * x * x * (1 + 0.75 * x);
    }

    /**
     * Calculate e3 used in meridional distance calculations.
     */
    public static double e3fn(double x) {
        return x * x * x * (35 / 3072.0);
    }

    /**
     * Calculate meridional distance.
     */
    public static double mlfn(double e0, double e1, double e2, double e3, double phi) {
        return e0 * phi - e1 * Math.sin(2 * phi) + e2 * Math.sin(4 * phi) - e3 * Math.sin(6 * phi);
    }

    /**
     * Calculate the inverse of meridional distance.
     */
    public static double imlfn(double ml, double e0, double e1, double e2, double e3) {
        double phi = ml / e0;
        for (int i = 0; i < 15; i++) {
            double dphi = (ml - (e0 * phi - e1 * Math.sin(2 * phi) + e2 * Math.sin(4 * phi) - e3 * Math.sin(6 * phi))) /
                         (e0 - 2 * e1 * Math.cos(2 * phi) + 4 * e2 * Math.cos(4 * phi) - 6 * e3 * Math.cos(6 * phi));
            phi += dphi;
            if (Math.abs(dphi) <= 0.0000000001) {
                return phi;
            }
        }
        return phi;
    }

    /**
     * Hyperbolic sine.
     */
    public static double sinh(double x) {
        double r = Math.exp(x);
        return (r - 1 / r) / 2;
    }

    /**
     * Hyperbolic cosine.
     */
    public static double cosh(double x) {
        double r = Math.exp(x);
        return (r + 1 / r) / 2;
    }

    /**
     * Hyperbolic tangent.
     */
    public static double tanh(double x) {
        double r = Math.exp(x);
        return (r - 1 / r) / (r + 1 / r);
    }

    /**
     * Inverse hyperbolic sine.
     */
    public static double asinh(double x) {
        double s = (x >= 0 ? 1 : -1);
        return s * Math.log(Math.abs(x) + Math.sqrt(x * x + 1));
    }

    /**
     * Inverse hyperbolic tangent.
     */
    public static double atanh(double x) {
        return Math.log((1 + x) / (1 - x)) / 2;
    }

    /**
     * Arc sine with clamping.
     */
    public static double asinz(double x) {
        if (Math.abs(x) > 1) {
            x = (x > 1) ? 1 : -1;
        }
        return Math.asin(x);
    }
}
