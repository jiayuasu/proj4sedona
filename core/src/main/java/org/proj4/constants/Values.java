package org.proj4.constants;

/**
 * Mathematical constants used throughout the projection calculations.
 * These constants are equivalent to the values defined in the JavaScript version.
 */
public final class Values {
    
    // Mathematical constants
    public static final double PI = Math.PI;
    public static final double HALF_PI = PI / 2.0;
    public static final double FORTPI = PI / 4.0;
    public static final double TWO_PI = 2.0 * PI;
    public static final double SPI = 3.14159265359; // Slightly greater than PI for longitude wrapping
    
    // Degree to radian conversion
    public static final double D2R = PI / 180.0;
    public static final double R2D = 180.0 / PI;
    
    // Epsilon values for floating point comparisons
    public static final double EPSLN = 1.0e-10;
    public static final double TOL = 1.0e-14;
    
    // Datum transformation types
    public static final int PJD_3PARAM = 1;
    public static final int PJD_7PARAM = 2;
    public static final int PJD_GRIDSHIFT = 3;
    public static final int PJD_WGS84 = 4;
    public static final int PJD_NODATUM = 5;
    
    // Axis order constants
    public static final String AXIS_ENU = "enu";
    public static final String AXIS_NEU = "neu";
    public static final String AXIS_NUE = "nue";
    public static final String AXIS_WUN = "wun";
    public static final String AXIS_WNU = "wnu";
    public static final String AXIS_USW = "usw";
    public static final String AXIS_UWS = "uws";
    public static final String AXIS_SUE = "sue";
    public static final String AXIS_SEU = "seu";
    public static final String AXIS_ESU = "esu";
    public static final String AXIS_EUS = "eus";
    
    // Default values
    public static final double DEFAULT_K0 = 1.0;
    public static final double DEFAULT_LAT0 = 0.0;
    public static final double DEFAULT_LONG0 = 0.0;
    public static final double DEFAULT_X0 = 0.0;
    public static final double DEFAULT_Y0 = 0.0;
    
    // Earth radius (approximate)
    public static final double EARTH_RADIUS = 6378137.0;
    
    // Common ellipsoid parameters
    public static final double WGS84_A = 6378137.0;
    public static final double WGS84_B = 6356752.314245;
    public static final double WGS84_RF = 298.257223563;
    
    // Additional constants from JavaScript version
    public static final double SRS_WGS84_SEMIMAJOR = 6378137.0;
    public static final double SRS_WGS84_SEMIMINOR = 6356752.314;
    public static final double SRS_WGS84_ESQUARED = 0.0066943799901413165;
    public static final double SEC_TO_RAD = 4.84813681109535993589914102357e-6;
    public static final double SIXTH = 0.1666666666666666667;
    public static final double RA4 = 0.04722222222222222222;
    public static final double RA6 = 0.02215608465608465608;
    
    // Utility methods
    private Values() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Checks if a value is effectively zero within epsilon tolerance.
     * @param value the value to check
     * @return true if the value is effectively zero
     */
    public static boolean isZero(double value) {
        return Math.abs(value) < EPSLN;
    }
    
    /**
     * Checks if two values are effectively equal within epsilon tolerance.
     * @param a first value
     * @param b second value
     * @return true if the values are effectively equal
     */
    public static boolean equals(double a, double b) {
        return Math.abs(a - b) < EPSLN;
    }
    
    /**
     * Normalizes an angle to the range [-PI, PI].
     * @param angle the angle in radians
     * @return normalized angle
     */
    public static double normalizeAngle(double angle) {
        while (angle > PI) {
            angle -= TWO_PI;
        }
        while (angle < -PI) {
            angle += TWO_PI;
        }
        return angle;
    }
    
    /**
     * Normalizes a longitude to the range [-180, 180] degrees.
     * @param lon longitude in degrees
     * @return normalized longitude
     */
    public static double normalizeLongitude(double lon) {
        while (lon > 180.0) {
            lon -= 360.0;
        }
        while (lon < -180.0) {
            lon += 360.0;
        }
        return lon;
    }
    
    /**
     * Normalizes a latitude to the range [-90, 90] degrees.
     * @param lat latitude in degrees
     * @return normalized latitude
     */
    public static double normalizeLatitude(double lat) {
        if (lat > 90.0) {
            lat = 90.0;
        } else if (lat < -90.0) {
            lat = -90.0;
        }
        return lat;
    }
}
