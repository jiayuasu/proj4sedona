package org.proj4.common;

import org.proj4.constants.Values;

/**
 * Mathematical utility functions for coordinate transformations.
 * These functions are ported from the JavaScript common utilities.
 */
public final class MathUtils {
    
    private MathUtils() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Adjusts longitude to valid range [-PI, PI].
     * Ported from adjust_lon.js
     * @param x longitude in radians
     * @return adjusted longitude
     */
    public static double adjustLon(double x) {
        return (Math.abs(x) <= Values.SPI) ? x : (x - (sign(x) * Values.TWO_PI));
    }
    
    /**
     * Returns the sign of a number.
     * Ported from sign.js
     * @param x the number
     * @return -1 if negative, 1 if positive or zero
     */
    public static double sign(double x) {
        return x < 0 ? -1 : 1;
    }
    
    /**
     * Calculates the meridian scale factor.
     * Ported from msfnz.js
     * @param eccent eccentricity
     * @param sinphi sine of latitude
     * @param cosphi cosine of latitude
     * @return meridian scale factor
     */
    public static double msfnz(double eccent, double sinphi, double cosphi) {
        double con = eccent * sinphi;
        return cosphi / Math.sqrt(1 - con * con);
    }
    
    /**
     * Calculates the conformal latitude.
     * Ported from tsfnz.js
     * @param eccent eccentricity
     * @param phi latitude in radians
     * @param sinphi sine of latitude
     * @return conformal latitude
     */
    public static double tsfnz(double eccent, double phi, double sinphi) {
        double con = eccent * sinphi;
        double com = 0.5 * eccent;
        con = Math.pow(((1 - con) / (1 + con)), com);
        return Math.tan(0.5 * (Values.HALF_PI - phi)) / con;
    }
    
    /**
     * Converts conformal latitude to geodetic latitude.
     * Ported from phi2z.js
     * @param eccent eccentricity
     * @param ts conformal latitude
     * @return geodetic latitude in radians, or -9999 if no convergence
     */
    public static double phi2z(double eccent, double ts) {
        double eccnth = 0.5 * eccent;
        double con, dphi;
        double phi = Values.HALF_PI - 2 * Math.atan(ts);
        
        for (int i = 0; i <= 15; i++) {
            con = eccent * Math.sin(phi);
            dphi = Values.HALF_PI - 2 * Math.atan(ts * (Math.pow(((1 - con) / (1 + con)), eccnth))) - phi;
            phi += dphi;
            if (Math.abs(dphi) <= 0.0000000001) {
                return phi;
            }
        }
        // No convergence
        return -9999;
    }
    
    /**
     * Calculates the inverse hyperbolic sine.
     * @param x the value
     * @return inverse hyperbolic sine
     */
    public static double asinh(double x) {
        return Math.log(x + Math.sqrt(x * x + 1));
    }
    
    /**
     * Calculates the inverse hyperbolic cosine.
     * @param x the value
     * @return inverse hyperbolic cosine
     */
    public static double acosh(double x) {
        return Math.log(x + Math.sqrt(x * x - 1));
    }
    
    /**
     * Calculates the inverse hyperbolic tangent.
     * @param x the value
     * @return inverse hyperbolic tangent
     */
    public static double atanh(double x) {
        return 0.5 * Math.log((1 + x) / (1 - x));
    }
    
    /**
     * Calculates the hyperbolic sine.
     * @param x the value
     * @return hyperbolic sine
     */
    public static double sinh(double x) {
        return (Math.exp(x) - Math.exp(-x)) / 2;
    }
    
    /**
     * Calculates the hyperbolic cosine.
     * @param x the value
     * @return hyperbolic cosine
     */
    public static double cosh(double x) {
        return (Math.exp(x) + Math.exp(-x)) / 2;
    }
    
    /**
     * Calculates the hyperbolic tangent.
     * @param x the value
     * @return hyperbolic tangent
     */
    public static double tanh(double x) {
        return sinh(x) / cosh(x);
    }
    
    /**
     * Calculates the hypotenuse of a right triangle.
     * @param x first side
     * @param y second side
     * @return hypotenuse
     */
    public static double hypot(double x, double y) {
        return Math.sqrt(x * x + y * y);
    }
    
    /**
     * Calculates log(1 + y) - x for better numerical stability.
     * @param x the value
     * @param y the value to add
     * @return log(1 + y) - x
     */
    public static double log1py(double x, double y) {
        return Math.log(1 + y) - x;
    }
    
    /**
     * Calculates the Clenshaw summation for complex numbers.
     * @param n number of terms
     * @param x argument
     * @param a coefficients
     * @return result
     */
    public static double clens(double n, double x, double[] a) {
        double u0 = 0, u1 = 0, u2 = 0;
        for (int i = (int) n; i >= 0; i--) {
            u2 = u1;
            u1 = u0;
            u0 = 2 * x * u1 - u2 + a[i];
        }
        return (u0 - u2) / 2;
    }
    
    /**
     * Calculates the Clenshaw summation for complex numbers (complex version).
     * @param n number of terms
     * @param x argument
     * @param a coefficients
     * @return result as array [real, imaginary]
     */
    public static double[] clensC(double n, double x, double[] a) {
        double u0r = 0, u1r = 0, u2r = 0;
        double u0i = 0, u1i = 0, u2i = 0;
        
        for (int i = (int) n; i >= 0; i--) {
            u2r = u1r;
            u1r = u0r;
            u0r = 2 * x * u1r - u2r + a[i];
            
            u2i = u1i;
            u1i = u0i;
            u0i = 2 * x * u1i - u2i;
        }
        
        return new double[]{(u0r - u2r) / 2, (u0i - u2i) / 2};
    }
    
    /**
     * Calculates the meridian length.
     * @param e eccentricity
     * @param c0 first coefficient
     * @param c1 second coefficient
     * @param c2 third coefficient
     * @param c3 fourth coefficient
     * @param phi latitude
     * @return meridian length
     */
    public static double mlfn(double e, double c0, double c1, double c2, double c3, double phi) {
        return c0 * phi - c1 * Math.sin(2 * phi) + c2 * Math.sin(4 * phi) - c3 * Math.sin(6 * phi);
    }
    
    /**
     * Calculates the inverse meridian length.
     * @param e eccentricity
     * @param c0 first coefficient
     * @param c1 second coefficient
     * @param c2 third coefficient
     * @param c3 fourth coefficient
     * @param ml meridian length
     * @return latitude
     */
    public static double invMlfn(double e, double c0, double c1, double c2, double c3, double ml) {
        double phi = ml / c0;
        for (int i = 0; i < 15; i++) {
            double dphi = (ml - mlfn(e, c0, c1, c2, c3, phi)) / c0;
            phi += dphi;
            if (Math.abs(dphi) <= 1e-10) {
                break;
            }
        }
        return phi;
    }
    
    /**
     * Calculates the latitude from isometric latitude.
     * @param e eccentricity
     * @param ts isometric latitude
     * @return latitude
     */
    public static double latiso(double e, double ts) {
        return phi2z(e, Math.exp(ts));
    }
    
    /**
     * Calculates the inverse latitude from isometric latitude.
     * @param e eccentricity
     * @param phi latitude
     * @return isometric latitude
     */
    public static double invLatiso(double e, double phi) {
        return Math.log(tsfnz(e, phi, Math.sin(phi)));
    }
    
    /**
     * Calculates the q function for equal area projections.
     * @param e eccentricity
     * @param sinphi sine of latitude
     * @return q value
     */
    public static double qsfnz(double e, double sinphi) {
        double con = e * sinphi;
        return (1 - e * e) * (sinphi / (1 - con * con) - (1 / (2 * e)) * Math.log((1 - con) / (1 + con)));
    }
    
    /**
     * Calculates the q function for equal area projections with cosphi.
     * @param e eccentricity
     * @param sinphi sine of latitude
     * @param cosphi cosine of latitude
     * @return q value
     */
    public static double qsfnz(double e, double sinphi, double cosphi) {
        return qsfnz(e, sinphi);
    }
    
    /**
     * Calculates the inverse q function.
     * @param e eccentricity
     * @param q q value
     * @return sine of latitude
     */
    public static double iqsfnz(double e, double q) {
        double con = 1 - e * e;
        double com = 1 / (2 * e);
        double phi = Math.asin(0.5 * q / con);
        for (int i = 0; i < 15; i++) {
            double sinphi = Math.sin(phi);
            double dphi = (q - qsfnz(e, sinphi)) / (con * Math.cos(phi) / (1 - e * e * sinphi * sinphi));
            phi += dphi;
            if (Math.abs(dphi) <= 1e-10) {
                break;
            }
        }
        return Math.sin(phi);
    }
    
    /**
     * Calculates the Vincenty direct formula.
     * @param a semi-major axis
     * @param e eccentricity
     * @param lat1 first latitude
     * @param lon1 first longitude
     * @param az azimuth
     * @param s distance
     * @return array [lat2, lon2, az2]
     */
    public static double[] vincenty(double a, double e, double lat1, double lon1, double az, double s) {
        double f = 1 - Math.sqrt(1 - e * e);
        double b = a * (1 - f);
        
        double u1 = Math.atan((1 - f) * Math.tan(lat1));
        double sigma1 = Math.atan2(Math.tan(u1), Math.cos(az));
        double alpha = Math.asin(Math.cos(u1) * Math.sin(az));
        
        double u2 = u1;
        double sigma = s / (b * (1 + f * f / 4 * (1 - Math.cos(2 * sigma1))));
        
        for (int i = 0; i < 100; i++) {
            double sigma2 = sigma;
            double deltaSigma = 0;
            
            for (int j = 0; j < 20; j++) {
                double cos2SigmaM = Math.cos(2 * sigma1 + sigma);
                double sinSigma = Math.sin(sigma);
                double cosSigma = Math.cos(sigma);
                
                double deltaSigmaNew = (b * (1 + f * f / 4 * (1 - Math.cos(2 * sigma1))) * sigma - s) / 
                                     (b * (1 + f * f / 4 * (1 - Math.cos(2 * sigma1))) + 
                                      b * f * f / 4 * Math.sin(2 * sigma1) * sinSigma);
                
                if (Math.abs(deltaSigmaNew - deltaSigma) < 1e-12) {
                    break;
                }
                deltaSigma = deltaSigmaNew;
                sigma += deltaSigma;
            }
            
            if (Math.abs(sigma - sigma2) < 1e-12) {
                break;
            }
        }
        
        double lat2 = Math.atan2(Math.sin(u1) * Math.cos(sigma) + Math.cos(u1) * Math.sin(sigma) * Math.cos(az),
                                (1 - f) * Math.sqrt(Math.sin(alpha) * Math.sin(alpha) + 
                                Math.pow(Math.sin(u1) * Math.sin(sigma) - Math.cos(u1) * Math.cos(sigma) * Math.cos(az), 2)));
        
        double lambda = Math.atan2(Math.sin(sigma) * Math.sin(az),
                                  Math.cos(u1) * Math.cos(sigma) - Math.sin(u1) * Math.sin(sigma) * Math.cos(az));
        
        double c = f / 16 * (1 - Math.cos(2 * sigma1)) * (4 + f * (4 - 3 * (1 - Math.cos(2 * sigma1))));
        double lon2 = lon1 + lambda - (1 - c) * f * Math.sin(alpha) * 
                     (sigma + c * Math.sin(sigma) * (Math.cos(2 * sigma1 + sigma) + 
                      c * Math.cos(sigma) * (-1 + 2 * Math.pow(Math.cos(2 * sigma1 + sigma), 2))));
        
        double az2 = Math.atan2(Math.sin(alpha), -Math.sin(u1) * Math.sin(sigma) + Math.cos(u1) * Math.cos(sigma) * Math.cos(az));
        
        return new double[]{lat2, lon2, az2};
    }
    
    /**
     * Calculates the e0 coefficient for ellipsoid calculations.
     * @param es squared eccentricity
     * @return e0 coefficient
     */
    public static double e0fn(double es) {
        return 1 - 0.25 * es * (1 + es / 16 * (3 + 1.25 * es));
    }
    
    /**
     * Calculates the e1 coefficient for ellipsoid calculations.
     * @param es squared eccentricity
     * @return e1 coefficient
     */
    public static double e1fn(double es) {
        return 0.375 * es * (1 + 0.25 * es * (1 + 0.46875 * es));
    }
    
    /**
     * Calculates the e2 coefficient for ellipsoid calculations.
     * @param es squared eccentricity
     * @return e2 coefficient
     */
    public static double e2fn(double es) {
        return 0.05859375 * es * es * (1 + 0.75 * es);
    }
    
    /**
     * Calculates the e3 coefficient for ellipsoid calculations.
     * @param es squared eccentricity
     * @return e3 coefficient
     */
    public static double e3fn(double es) {
        return es * es * es * (35.0 / 3072.0);
    }
    
    /**
     * Calculates the meridian length using coefficients.
     * @param e0 first coefficient
     * @param e1 second coefficient
     * @param e2 third coefficient
     * @param e3 fourth coefficient
     * @param phi latitude
     * @return meridian length
     */
    public static double mlfn(double e0, double e1, double e2, double e3, double phi) {
        return e0 * phi - e1 * Math.sin(2 * phi) + e2 * Math.sin(4 * phi) - e3 * Math.sin(6 * phi);
    }
}
