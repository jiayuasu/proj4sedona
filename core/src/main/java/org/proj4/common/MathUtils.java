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
        return (Math.abs(x) <= Values.PI) ? x : (x - (sign(x) * Values.TWO_PI));
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
     * Calculates the inverse meridian length using the proj4js algorithm.
     * @param arg meridian length
     * @param es squared eccentricity
     * @param en coefficients array
     * @return latitude
     */
    public static double invMlfn(double arg, double es, double[] en) {
        double k = 1 / (1 - es);
        double phi = arg;
        for (int i = 0; i < 20; i++) {
            double s = Math.sin(phi);
            double c = Math.cos(phi);
            double t = 1 - es * s * s;
            // Use the same mlfn method as the forward transformation
            t = (mlfn(phi, s, c, en) - arg) * (t * Math.sqrt(t)) * k;
            phi -= t;
            if (Math.abs(t) <= Values.EPSLN) {
                return phi;
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
        
        double sigma = s / (b * (1 + f * f / 4 * (1 - Math.cos(2 * sigma1))));
        
        for (int i = 0; i < 100; i++) {
            double sigma2 = sigma;
            double deltaSigma = 0;
            
            for (int j = 0; j < 20; j++) {
                double sinSigma = Math.sin(sigma);
                
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
    
    
    /**
     * Calculates the meridian length using the proj4js algorithm.
     * @param phi latitude
     * @param sphi sine of latitude
     * @param cphi cosine of latitude
     * @param en coefficients array
     * @return meridian length
     */
    public static double mlfn(double phi, double sphi, double cphi, double[] en) {
        cphi *= sphi;
        sphi *= sphi;
        return (en[0] * phi - cphi * (en[1] + sphi * (en[2] + sphi * (en[3] + sphi * en[4]))));
    }
    
    /**
     * Calculates the en coefficients for meridian length calculation.
     * @param es squared eccentricity
     * @return en coefficients array
     */
    public static double[] enfn(double es) {
        double[] en = new double[5];
        en[0] = 1 - es * (0.25 + es * (0.046875 + es * (0.01953125 + es * 0.01068115234375)));
        en[1] = es * (0.75 - es * (0.046875 + es * (0.01953125 + es * 0.01068115234375)));
        double t = es * es;
        en[2] = t * (0.46875 - es * (0.01302083333333333333 + es * 0.00712076822916666666));
        t *= es;
        en[3] = t * (0.36458333333333333333 - es * 0.00569661458333333333);
        en[4] = t * es * 0.3076171875;
        return en;
    }
    
    /**
     * Adjusts UTM zone based on longitude.
     * Ported from adjust_zone.js
     * @param zone the zone number (if undefined, will be calculated)
     * @param lon longitude in radians
     * @return adjusted zone number
     */
    public static int adjustZone(Integer zone, double lon) {
        if (zone == null) {
            zone = (int) Math.floor((adjustLon(lon) + Values.PI) * 30 / Values.PI) + 1;
            
            if (zone < 0) {
                return 0;
            } else if (zone > 60) {
                return 60;
            }
        }
        return zone;
    }
    
    /**
     * Calculates the Gauss-Krüger transverse Mercator projection.
     * Ported from gatg.js
     * @param pp coefficients array
     * @param B latitude in radians
     * @return result
     */
    public static double gatg(double[] pp, double B) {
        double cos_2B = 2 * Math.cos(2 * B);
        int i = pp.length - 1;
        double h1 = pp[i];
        double h2 = 0;
        double h = 0;
        
        while (--i >= 0) {
            h = -h2 + cos_2B * h1 + pp[i];
            h2 = h1;
            h1 = h;
        }
        
        return (B + h * Math.sin(2 * B));
    }
    
    /**
     * Calculates the radius of curvature in the prime vertical.
     * Ported from gN.js
     * @param a semi-major axis
     * @param e eccentricity
     * @param sinphi sine of latitude
     * @return radius of curvature
     */
    public static double gN(double a, double e, double sinphi) {
        double temp = e * sinphi;
        return a / Math.sqrt(1 - temp * temp);
    }
    
    /**
     * Calculates the fL function for transverse Mercator projections.
     * Ported from fL.js
     * @param x the x value
     * @param L the L value
     * @return result
     */
    public static double fL(double x, double L) {
        return 2 * Math.atan(x * Math.exp(L)) - Values.HALF_PI;
    }
    
    /**
     * Calculates the srat function for ellipsoid calculations.
     * Ported from srat.js
     * @param esinp e * sin(phi)
     * @param exp exponent
     * @return result
     */
    public static double srat(double esinp, double exp) {
        return Math.pow((1 - esinp) / (1 + esinp), exp);
    }
    
    /**
     * Calculates the radius of curvature in the meridian.
     * @param a semi-major axis
     * @param e eccentricity
     * @param sinphi sine of latitude
     * @return radius of curvature in the meridian
     */
    public static double gM(double a, double e, double sinphi) {
        double temp = e * sinphi;
        return a * (1 - e * e) / Math.pow(1 - temp * temp, 1.5);
    }
    
    /**
     * Calculates the convergence angle for transverse Mercator projections.
     * @param lon longitude in radians
     * @param lat latitude in radians
     * @param lon0 central meridian in radians
     * @param e eccentricity
     * @return convergence angle in radians
     */
    public static double convergence(double lon, double lat, double lon0, double e) {
        double dlon = lon - lon0;
        double sinlat = Math.sin(lat);
        double coslat = Math.cos(lat);
        double sin2lat = sinlat * sinlat;
        double e2 = e * e;
        
        double term1 = sinlat * dlon;
        double term2 = sinlat * coslat * coslat * dlon * dlon * dlon * (1 + 3 * e2 * sin2lat) / 6;
        
        return term1 + term2;
    }
    
    /**
     * Calculates the scale factor for transverse Mercator projections.
     * @param lat latitude in radians
     * @param e eccentricity
     * @param k0 central scale factor
     * @return scale factor
     */
    public static double scaleFactor(double lat, double e, double k0) {
        double sinlat = Math.sin(lat);
        double sin2lat = sinlat * sinlat;
        double e2 = e * e;
        
        return k0 * (1 + e2 * sin2lat / 2 + 5 * e2 * e2 * sin2lat * sin2lat / 24);
    }
    
    /**
     * Calculates the grid convergence for UTM projections.
     * @param lon longitude in radians
     * @param lat latitude in radians
     * @param zone UTM zone number
     * @return grid convergence in radians
     */
    public static double utmConvergence(double lon, double lat, int zone) {
        double lon0 = (zone - 1) * 6 - 180 + 3; // Central meridian
        lon0 = Math.toRadians(lon0);
        return convergence(lon, lat, lon0, 0.08181919084262157); // WGS84 eccentricity
    }
    
    /**
     * Calculates the distance between two points using the haversine formula.
     * @param lat1 first latitude in radians
     * @param lon1 first longitude in radians
     * @param lat2 second latitude in radians
     * @param lon2 second longitude in radians
     * @param radius Earth radius in meters
     * @return distance in meters
     */
    public static double haversineDistance(double lat1, double lon1, double lat2, double lon2, double radius) {
        double dlat = lat2 - lat1;
        double dlon = lon2 - lon1;
        
        double a = Math.sin(dlat / 2) * Math.sin(dlat / 2) +
                   Math.cos(lat1) * Math.cos(lat2) *
                   Math.sin(dlon / 2) * Math.sin(dlon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return radius * c;
    }
    
    /**
     * Calculates the bearing between two points.
     * @param lat1 first latitude in radians
     * @param lon1 first longitude in radians
     * @param lat2 second latitude in radians
     * @param lon2 second longitude in radians
     * @return bearing in radians
     */
    public static double bearing(double lat1, double lon1, double lat2, double lon2) {
        double dlon = lon2 - lon1;
        
        double y = Math.sin(dlon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dlon);
        
        return Math.atan2(y, x);
    }
    
    /**
     * Calculates the midpoint between two points on a sphere.
     * @param lat1 first latitude in radians
     * @param lon1 first longitude in radians
     * @param lat2 second latitude in radians
     * @param lon2 second longitude in radians
     * @return array [lat, lon] in radians
     */
    public static double[] midpoint(double lat1, double lon1, double lat2, double lon2) {
        double dlon = lon2 - lon1;
        
        double bx = Math.cos(lat2) * Math.cos(dlon);
        double by = Math.cos(lat2) * Math.sin(dlon);
        
        double lat3 = Math.atan2(Math.sin(lat1) + Math.sin(lat2),
                                Math.sqrt((Math.cos(lat1) + bx) * (Math.cos(lat1) + bx) + by * by));
        double lon3 = lon1 + Math.atan2(by, Math.cos(lat1) + bx);
        
        return new double[]{lat3, lon3};
    }
    
    /**
     * Arc sine function that handles values outside [-1, 1] range.
     * Ported from asinz.js
     * @param x the value
     * @return arc sine of x, clamped to valid range
     */
    public static double asinz(double x) {
        if (Math.abs(x) > 1.0) {
            x = x > 1.0 ? 1.0 : -1.0;
        }
        return Math.asin(x);
    }
    
    /**
     * Adjusts latitude to valid range [-PI/2, PI/2].
     * Ported from adjust_lat.js
     * @param x latitude in radians
     * @return adjusted latitude
     */
    public static double adjustLat(double x) {
        return (Math.abs(x) <= Values.HALF_PI) ? x : (x - (sign(x) * Values.PI));
    }
}
