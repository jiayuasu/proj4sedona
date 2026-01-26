package org.datasyslab.proj4sedona.datum;

import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.DatumParams;
import org.datasyslab.proj4sedona.core.Point;

/**
 * Utility functions for datum transformations.
 * Mirrors: lib/datumUtils.js
 * 
 * Contains functions for:
 * - Comparing datums for equality
 * - Converting between geodetic and geocentric coordinates
 * - Applying 3-parameter and 7-parameter Helmert transformations
 */
public final class DatumUtils {

    private DatumUtils() {
        // Utility class
    }

    /**
     * Compare two datums for equality.
     * Mirrors: lib/datumUtils.js compareDatums
     * 
     * @param source Source datum parameters
     * @param dest Destination datum parameters
     * @return true if datums are equivalent, false otherwise
     */
    public static boolean compareDatums(DatumParams source, DatumParams dest) {
        if (source.getDatumType() != dest.getDatumType()) {
            return false;
        }

        // Check ellipsoid parameters
        // The tolerance for es is to ensure that GRS80 and WGS84
        // are considered identical
        if (source.getA() != dest.getA() || 
            Math.abs(source.getEs() - dest.getEs()) > 0.000000000050) {
            return false;
        }

        // Check datum parameters based on type
        if (source.getDatumType() == Values.PJD_3PARAM) {
            double[] sp = source.getDatumParams();
            double[] dp = dest.getDatumParams();
            return sp != null && dp != null &&
                   sp[0] == dp[0] && sp[1] == dp[1] && sp[2] == dp[2];
        } else if (source.getDatumType() == Values.PJD_7PARAM) {
            double[] sp = source.getDatumParams();
            double[] dp = dest.getDatumParams();
            return sp != null && dp != null &&
                   sp[0] == dp[0] && sp[1] == dp[1] && sp[2] == dp[2] &&
                   sp[3] == dp[3] && sp[4] == dp[4] && sp[5] == dp[5] && sp[6] == dp[6];
        }

        // For WGS84 or NODATUM types, datums are equal
        return true;
    }

    /**
     * Convert geodetic coordinates (longitude, latitude, height) to geocentric (X, Y, Z).
     * Mirrors: lib/datumUtils.js geodeticToGeocentric
     * 
     * @param p Point with x=longitude, y=latitude (radians), z=height (meters)
     * @param es Eccentricity squared
     * @param a Semi-major axis
     * @return Point with x=X, y=Y, z=Z (geocentric coordinates in meters)
     */
    public static Point geodeticToGeocentric(Point p, double es, double a) {
        double longitude = p.x;
        double latitude = p.y;
        double height = p.z;

        // Clamp latitude to valid range
        // Don't blow up if Latitude is just a little out of the value
        // range as it may just be a rounding issue.
        if (latitude < -Values.HALF_PI && latitude > -1.001 * Values.HALF_PI) {
            latitude = -Values.HALF_PI;
        } else if (latitude > Values.HALF_PI && latitude < 1.001 * Values.HALF_PI) {
            latitude = Values.HALF_PI;
        } else if (latitude < -Values.HALF_PI) {
            // Latitude out of range
            return new Point(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, p.z);
        } else if (latitude > Values.HALF_PI) {
            // Latitude out of range
            return new Point(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, p.z);
        }

        // Normalize longitude
        if (longitude > Math.PI) {
            longitude -= (2 * Math.PI);
        }

        double sinLat = Math.sin(latitude);
        double cosLat = Math.cos(latitude);
        double sin2Lat = sinLat * sinLat;

        // Earth radius at location
        double rn = a / Math.sqrt(1.0 - es * sin2Lat);

        return new Point(
            (rn + height) * cosLat * Math.cos(longitude),
            (rn + height) * cosLat * Math.sin(longitude),
            ((rn * (1 - es)) + height) * sinLat
        );
    }

    /**
     * Convert geocentric coordinates (X, Y, Z) to geodetic (longitude, latitude, height).
     * Mirrors: lib/datumUtils.js geocentricToGeodetic
     * 
     * Uses iterative algorithm developed by "Institut for Erdmessung", 
     * University of Hannover, July 1988.
     * 
     * @param p Point with x=X, y=Y, z=Z (geocentric coordinates in meters)
     * @param es Eccentricity squared
     * @param a Semi-major axis
     * @param b Semi-minor axis
     * @return Point with x=longitude, y=latitude (radians), z=height (meters)
     */
    public static Point geocentricToGeodetic(Point p, double es, double a, double b) {
        // Accuracy constants
        double genau = 1e-12;
        double genau2 = genau * genau;
        int maxiter = 30;

        double x = p.x;
        double y = p.y;
        double z = p.z;

        // Distance from Z axis
        double pDist = Math.sqrt(x * x + y * y);
        // Distance from center
        double rr = Math.sqrt(x * x + y * y + z * z);

        double longitude;
        double latitude;
        double height;

        // Special cases for latitude and longitude
        if (pDist / a < genau) {
            // Special case if P=0 (X=0, Y=0)
            longitude = 0.0;

            // If (X,Y,Z)=(0,0,0) then Height becomes semi-minor axis
            // of ellipsoid (=center of mass), Latitude becomes PI/2
            if (rr / a < genau) {
                return new Point(p.x, p.y, p.z);
            }
        } else {
            // Ellipsoidal (geodetic) longitude
            // Interval: -PI < Longitude <= +PI
            longitude = Math.atan2(y, x);
        }

        // Iterative algorithm for latitude and height
        double ct = z / rr;
        double st = pDist / rr;
        double rx = 1.0 / Math.sqrt(1.0 - es * (2.0 - es) * st * st);
        double cphi0 = st * (1.0 - es) * rx;
        double sphi0 = ct * rx;

        double rn, cphi, sphi, sdphi;
        int iter = 0;

        // Loop to find sin(Latitude) resp. Latitude
        // until |sin(Latitude(iter)-Latitude(iter-1))| < genau
        do {
            iter++;
            rn = a / Math.sqrt(1.0 - es * sphi0 * sphi0);

            // Ellipsoidal (geodetic) height
            height = pDist * cphi0 + z * sphi0 - rn * (1.0 - es * sphi0 * sphi0);

            double rk = es * rn / (rn + height);
            rx = 1.0 / Math.sqrt(1.0 - rk * (2.0 - rk) * st * st);
            cphi = st * (1.0 - rk) * rx;
            sphi = ct * rx;
            sdphi = sphi * cphi0 - cphi * sphi0;
            cphi0 = cphi;
            sphi0 = sphi;
        } while (sdphi * sdphi > genau2 && iter < maxiter);

        // Ellipsoidal (geodetic) latitude
        latitude = Math.atan(sphi / Math.abs(cphi));

        return new Point(longitude, latitude, height);
    }

    /**
     * Apply 3-parameter or 7-parameter transformation to convert geocentric 
     * coordinates to WGS84.
     * Mirrors: lib/datumUtils.js geocentricToWgs84
     * 
     * @param p Point in geocentric coordinates
     * @param datumType Type of datum (PJD_3PARAM or PJD_7PARAM)
     * @param datumParams Transformation parameters
     * @return Point in WGS84 geocentric coordinates
     */
    public static Point geocentricToWgs84(Point p, int datumType, double[] datumParams) {
        if (datumType == Values.PJD_3PARAM) {
            // 3-parameter transformation (translation only)
            return new Point(
                p.x + datumParams[0],
                p.y + datumParams[1],
                p.z + datumParams[2]
            );
        } else if (datumType == Values.PJD_7PARAM) {
            // 7-parameter Helmert transformation (Bursa-Wolf)
            double dx = datumParams[0];
            double dy = datumParams[1];
            double dz = datumParams[2];
            double rx = datumParams[3];
            double ry = datumParams[4];
            double rz = datumParams[5];
            double m = datumParams[6];

            return new Point(
                m * (p.x - rz * p.y + ry * p.z) + dx,
                m * (rz * p.x + p.y - rx * p.z) + dy,
                m * (-ry * p.x + rx * p.y + p.z) + dz
            );
        }
        return p;
    }

    /**
     * Apply inverse 3-parameter or 7-parameter transformation to convert 
     * geocentric coordinates from WGS84.
     * Mirrors: lib/datumUtils.js geocentricFromWgs84
     * 
     * @param p Point in WGS84 geocentric coordinates
     * @param datumType Type of datum (PJD_3PARAM or PJD_7PARAM)
     * @param datumParams Transformation parameters
     * @return Point in target datum geocentric coordinates
     */
    public static Point geocentricFromWgs84(Point p, int datumType, double[] datumParams) {
        if (datumType == Values.PJD_3PARAM) {
            // Inverse 3-parameter transformation
            return new Point(
                p.x - datumParams[0],
                p.y - datumParams[1],
                p.z - datumParams[2]
            );
        } else if (datumType == Values.PJD_7PARAM) {
            // Inverse 7-parameter Helmert transformation
            double dx = datumParams[0];
            double dy = datumParams[1];
            double dz = datumParams[2];
            double rx = datumParams[3];
            double ry = datumParams[4];
            double rz = datumParams[5];
            double m = datumParams[6];

            double xTmp = (p.x - dx) / m;
            double yTmp = (p.y - dy) / m;
            double zTmp = (p.z - dz) / m;

            return new Point(
                xTmp + rz * yTmp - ry * zTmp,
                -rz * xTmp + yTmp + rx * zTmp,
                ry * xTmp - rx * yTmp + zTmp
            );
        }
        return p;
    }
}
