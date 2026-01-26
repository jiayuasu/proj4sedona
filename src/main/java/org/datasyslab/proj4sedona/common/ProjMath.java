package org.datasyslab.proj4sedona.common;

import org.datasyslab.proj4sedona.constants.Values;

/**
 * Common mathematical functions for projections.
 * Mirrors: lib/common/*.js
 * 
 * This class provides various mathematical utilities used throughout the projection
 * library, including longitude/latitude adjustments, ellipsoid calculations,
 * meridional distance functions, and hyperbolic functions.
 */
public final class ProjMath {

    private ProjMath() {
        // Utility class - prevent instantiation
    }

    // ==================== Basic Math Functions ====================

    /**
     * Sign function.
     * Mirrors: lib/common/sign.js
     *
     * @param x Input value
     * @return -1 if x is negative, 1 otherwise
     */
    public static double sign(double x) {
        return x < 0 ? -1 : 1;
    }

    // ==================== Longitude/Latitude Adjustments ====================

    /**
     * Adjust longitude to be within -PI to PI.
     * Mirrors: lib/common/adjust_lon.js
     *
     * @param x Longitude in radians
     * @param skipAdjust If true, skip the adjustment (for +over flag)
     * @return Adjusted longitude in radians
     */
    public static double adjustLon(double x, Boolean skipAdjust) {
        if (skipAdjust != null && skipAdjust) {
            return x;
        }
        return (Math.abs(x) <= Values.SPI) ? x : (x - (sign(x) * Values.TWO_PI));
    }

    /**
     * Adjust longitude to be within -PI to PI.
     * 
     * @param x Longitude in radians
     * @return Adjusted longitude in radians
     */
    public static double adjustLon(double x) {
        return adjustLon(x, false);
    }

    /**
     * Adjust latitude to be within -PI/2 to PI/2.
     *
     * @param x Latitude in radians
     * @return Adjusted latitude in radians
     */
    public static double adjustLat(double x) {
        return (Math.abs(x) < Values.HALF_PI) ? x : (x - (sign(x) * Math.PI));
    }

    // ==================== Ellipsoid Calculations ====================

    /**
     * Compute the constant small m which is the radius of a parallel of latitude,
     * phi, divided by the semimajor axis.
     * Mirrors: lib/common/msfnz.js
     *
     * @param eccent Eccentricity of the ellipsoid
     * @param sinphi Sine of the latitude
     * @param cosphi Cosine of the latitude
     * @return The m value
     */
    public static double msfnz(double eccent, double sinphi, double cosphi) {
        double con = eccent * sinphi;
        return cosphi / Math.sqrt(1 - con * con);
    }

    /**
     * Compute the constant small t for use in the forward computations.
     * Mirrors: lib/common/tsfnz.js
     *
     * @param eccent Eccentricity of the ellipsoid
     * @param phi Latitude in radians
     * @param sinphi Sine of the latitude
     * @return The t value
     */
    public static double tsfnz(double eccent, double phi, double sinphi) {
        double con = eccent * sinphi;
        double com = 0.5 * eccent;
        con = Math.pow(((1 - con) / (1 + con)), com);
        return Math.tan(0.5 * (Values.HALF_PI - phi)) / con;
    }

    /**
     * Compute the latitude angle, phi2, for the inverse of various projections.
     * Mirrors: lib/common/phi2z.js
     *
     * @param eccent Eccentricity of the ellipsoid
     * @param ts The t value from inverse computation
     * @return Latitude in radians, or -9999 if no convergence after 15 iterations
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
        // No convergence - return sentinel value
        return -9999;
    }

    /**
     * Calculate q used in authalic (equal-area) calculations.
     * Mirrors: lib/common/qsfnz.js
     *
     * @param eccent Eccentricity of the ellipsoid
     * @param sinphi Sine of the latitude
     * @return The q value
     */
    public static double qsfnz(double eccent, double sinphi) {
        if (eccent > 1.0e-7) {
            double con = eccent * sinphi;
            return (1 - eccent * eccent) * (sinphi / (1 - con * con) - 
                   (0.5 / eccent) * Math.log((1 - con) / (1 + con)));
        }
        return 2 * sinphi;
    }

    // ==================== Meridional Distance Functions ====================
    // These functions calculate the meridional distance from the equator to a 
    // given latitude on an ellipsoid.

    /**
     * Calculate e0 coefficient for meridional distance calculations.
     * Mirrors: lib/common/e0fn.js
     *
     * @param x Eccentricity squared (es)
     * @return e0 coefficient
     */
    public static double e0fn(double x) {
        return 1 - 0.25 * x * (1 + x / 16 * (3 + 1.25 * x));
    }

    /**
     * Calculate e1 coefficient for meridional distance calculations.
     * Mirrors: lib/common/e1fn.js
     *
     * @param x Eccentricity squared (es)
     * @return e1 coefficient
     */
    public static double e1fn(double x) {
        return 0.375 * x * (1 + 0.25 * x * (1 + 0.46875 * x));
    }

    /**
     * Calculate e2 coefficient for meridional distance calculations.
     * Mirrors: lib/common/e2fn.js
     *
     * @param x Eccentricity squared (es)
     * @return e2 coefficient
     */
    public static double e2fn(double x) {
        return 0.05859375 * x * x * (1 + 0.75 * x);
    }

    /**
     * Calculate e3 coefficient for meridional distance calculations.
     * Mirrors: lib/common/e3fn.js
     *
     * @param x Eccentricity squared (es)
     * @return e3 coefficient
     */
    public static double e3fn(double x) {
        return x * x * x * (35 / 3072.0);
    }

    /**
     * Calculate meridional distance from the equator to a given latitude.
     * Mirrors: lib/common/mlfn.js
     *
     * @param e0 e0 coefficient
     * @param e1 e1 coefficient
     * @param e2 e2 coefficient
     * @param e3 e3 coefficient
     * @param phi Latitude in radians
     * @return Meridional distance
     */
    public static double mlfn(double e0, double e1, double e2, double e3, double phi) {
        return e0 * phi - e1 * Math.sin(2 * phi) + e2 * Math.sin(4 * phi) - e3 * Math.sin(6 * phi);
    }

    /**
     * Calculate the inverse of meridional distance (latitude from distance).
     * Uses Newton-Raphson iteration.
     *
     * @param ml Meridional distance
     * @param e0 e0 coefficient
     * @param e1 e1 coefficient
     * @param e2 e2 coefficient
     * @param e3 e3 coefficient
     * @return Latitude in radians
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

    // ==================== Hyperbolic Functions ====================
    // These delegate to Java's native Math implementations for JIT optimization.

    /**
     * Hyperbolic sine.
     * Mirrors: lib/common/sinh.js
     * 
     * @param x Input value
     * @return sinh(x)
     */
    public static double sinh(double x) {
        return Math.sinh(x);
    }

    /**
     * Hyperbolic cosine.
     * Mirrors: lib/common/cosh.js
     *
     * @param x Input value
     * @return cosh(x)
     */
    public static double cosh(double x) {
        return Math.cosh(x);
    }

    /**
     * Hyperbolic tangent.
     * Mirrors: lib/common/tanh.js
     *
     * @param x Input value
     * @return tanh(x)
     */
    public static double tanh(double x) {
        return Math.tanh(x);
    }

    /**
     * Inverse hyperbolic sine.
     * Mirrors: lib/common/asinh.js
     *
     * @param x Input value
     * @return asinh(x)
     */
    public static double asinh(double x) {
        double s = (x >= 0 ? 1 : -1);
        return s * Math.log(Math.abs(x) + Math.sqrt(x * x + 1));
    }

    /**
     * Inverse hyperbolic tangent.
     *
     * @param x Input value (must be in range -1 to 1)
     * @return atanh(x)
     */
    public static double atanh(double x) {
        return Math.log((1 + x) / (1 - x)) / 2;
    }

    /**
     * Arc sine with clamping to avoid NaN for values slightly outside [-1, 1].
     * Mirrors: lib/common/asinz.js
     *
     * @param x Input value
     * @return asin(x), clamped to valid range
     */
    public static double asinz(double x) {
        if (Math.abs(x) > 1) {
            x = (x > 1) ? 1 : -1;
        }
        return Math.asin(x);
    }

    // ==================== Extended Meridional Distance Functions ====================
    // Used by Transverse Mercator projections. Based on PROJ's pj_mlfn algorithm.
    // Mirrors: lib/common/pj_enfn.js, pj_mlfn.js, pj_inv_mlfn.js

    // Coefficients for meridional distance polynomial
    private static final double C00 = 1;
    private static final double C02 = 0.25;
    private static final double C04 = 0.046875;
    private static final double C06 = 0.01953125;
    private static final double C08 = 0.01068115234375;
    private static final double C22 = 0.75;
    private static final double C44 = 0.46875;
    private static final double C46 = 0.01302083333333333333;
    private static final double C48 = 0.00712076822916666666;
    private static final double C66 = 0.36458333333333333333;
    private static final double C68 = 0.00569661458333333333;
    private static final double C88 = 0.3076171875;

    /**
     * Calculate meridional distance coefficients for the given eccentricity squared.
     * Mirrors: lib/common/pj_enfn.js
     *
     * @param es Eccentricity squared
     * @return Array of 5 coefficients [en0, en1, en2, en3, en4]
     */
    public static double[] pjEnfn(double es) {
        double[] en = new double[5];
        en[0] = C00 - es * (C02 + es * (C04 + es * (C06 + es * C08)));
        en[1] = es * (C22 - es * (C04 + es * (C06 + es * C08)));
        double t = es * es;
        en[2] = t * (C44 - es * (C46 + es * C48));
        t *= es;
        en[3] = t * (C66 - es * C68);
        en[4] = t * es * C88;
        return en;
    }

    /**
     * Calculate meridional distance from latitude using precomputed coefficients.
     * Mirrors: lib/common/pj_mlfn.js
     *
     * @param phi Latitude in radians
     * @param sphi sin(phi)
     * @param cphi cos(phi)
     * @param en Coefficients from pjEnfn()
     * @return Meridional distance
     */
    public static double pjMlfn(double phi, double sphi, double cphi, double[] en) {
        cphi *= sphi;
        sphi *= sphi;
        return en[0] * phi - cphi * (en[1] + sphi * (en[2] + sphi * (en[3] + sphi * en[4])));
    }

    /**
     * Inverse of pjMlfn - calculate latitude from meridional distance.
     * Uses Newton-Raphson iteration. Rarely requires more than 2 iterations.
     * Mirrors: lib/common/pj_inv_mlfn.js
     *
     * @param arg Meridional distance
     * @param es Eccentricity squared
     * @param en Coefficients from pjEnfn()
     * @return Latitude in radians
     */
    public static double pjInvMlfn(double arg, double es, double[] en) {
        double k = 1.0 / (1.0 - es);
        double phi = arg;
        for (int i = 20; i > 0; i--) {
            double s = Math.sin(phi);
            double t = 1.0 - es * s * s;
            t = (pjMlfn(phi, s, Math.cos(phi), en) - arg) * (t * Math.sqrt(t)) * k;
            phi -= t;
            if (Math.abs(t) < Values.EPSLN) {
                return phi;
            }
        }
        return phi;
    }

    // ==================== Extended Transverse Mercator Functions ====================
    // Used by the accurate etmerc projection implementation.
    // Mirrors: lib/common/gatg.js, clens.js, clens_cmplx.js

    /**
     * Gauss to geodetic latitude transformation using Clenshaw summation.
     * Mirrors: lib/common/gatg.js
     *
     * @param pp Polynomial coefficients
     * @param B Input angle (conformal latitude)
     * @return Transformed angle (geodetic latitude)
     */
    public static double gatg(double[] pp, double B) {
        double cos2B = 2 * Math.cos(2 * B);
        int i = pp.length - 1;
        double h1 = pp[i];
        double h2 = 0;
        double h = h1;
        while (--i >= 0) {
            h = -h2 + cos2B * h1 + pp[i];
            h2 = h1;
            h1 = h;
        }
        return B + h * Math.sin(2 * B);
    }

    /**
     * Clenshaw summation for real argument.
     * Mirrors: lib/common/clens.js
     *
     * @param pp Polynomial coefficients
     * @param argR Real argument
     * @return Summation result
     */
    public static double clens(double[] pp, double argR) {
        double r = 2 * Math.cos(argR);
        int i = pp.length - 1;
        double hr1 = pp[i];
        double hr2 = 0;
        double hr = hr1;
        while (--i >= 0) {
            hr = -hr2 + r * hr1 + pp[i];
            hr2 = hr1;
            hr1 = hr;
        }
        return Math.sin(argR) * hr;
    }

    /**
     * Clenshaw summation for complex argument.
     * Used for accurate Transverse Mercator calculations.
     * Mirrors: lib/common/clens_cmplx.js
     *
     * @param pp Polynomial coefficients
     * @param argR Real part of argument
     * @param argI Imaginary part of argument
     * @return Array [real_result, imaginary_result]
     */
    public static double[] clensCmplx(double[] pp, double argR, double argI) {
        double sinArgR = Math.sin(argR);
        double cosArgR = Math.cos(argR);
        double sinhArgI = Math.sinh(argI);
        double coshArgI = Math.cosh(argI);
        double r = 2 * cosArgR * coshArgI;
        double im = -2 * sinArgR * sinhArgI;
        int j = pp.length - 1;
        double hr = pp[j];
        double hi1 = 0;
        double hr1 = 0;
        double hi = 0;
        double hr2, hi2;
        while (--j >= 0) {
            hr2 = hr1;
            hi2 = hi1;
            hr1 = hr;
            hi1 = hi;
            hr = -hr2 + r * hr1 - im * hi1 + pp[j];
            hi = -hi2 + im * hr1 + r * hi1;
        }
        r = sinArgR * coshArgI;
        im = cosArgR * sinhArgI;
        return new double[]{r * hr - im * hi, r * hi + im * hr};
    }

    /**
     * Inverse hyperbolic sine with improved accuracy for small values.
     * Mirrors: lib/common/asinhy.js
     *
     * @param x Input value
     * @return asinh(x) with high accuracy
     */
    public static double asinhy(double x) {
        double y = Math.abs(x);
        y = log1py(y * (1 + y / (Math.hypot(1, y) + 1)));
        return x < 0 ? -y : y;
    }

    /**
     * Accurate computation of log(1 + x) for small x.
     * Avoids precision loss when x is close to zero.
     * Mirrors: lib/common/log1py.js
     *
     * @param x Input value
     * @return log(1 + x)
     */
    public static double log1py(double x) {
        double y = 1 + x;
        double z = y - 1;
        return z == 0 ? x : x * Math.log(y) / z;
    }

    // ==================== UTM Zone Calculation ====================

    /**
     * Adjust or calculate UTM zone number.
     * If zone is provided, returns it unchanged.
     * If zone is null, calculates zone from longitude.
     * Mirrors: lib/common/adjust_zone.js
     *
     * @param zone Optional zone number (1-60), or null to auto-calculate
     * @param lon Longitude in radians (used if zone is null)
     * @return UTM zone number (1-60), or 0 if invalid
     */
    public static int adjustZone(Integer zone, double lon) {
        if (zone == null) {
            int z = (int) Math.floor((adjustLon(lon) + Math.PI) * 30 / Math.PI) + 1;
            if (z < 0) return 0;
            if (z > 60) return 60;
            return z;
        }
        return zone;
    }
}
