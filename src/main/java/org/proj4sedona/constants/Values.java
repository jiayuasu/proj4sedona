package org.proj4sedona.constants;

/**
 * Mathematical and geodetic constants.
 * Mirrors: lib/constants/values.js
 */
public final class Values {

    private Values() {
        // Utility class
    }

    // Datum types
    public static final int PJD_3PARAM = 1;
    public static final int PJD_7PARAM = 2;
    public static final int PJD_GRIDSHIFT = 3;
    public static final int PJD_WGS84 = 4;     // WGS84 or equivalent
    public static final int PJD_NODATUM = 5;   // WGS84 or equivalent

    // WGS84 ellipsoid parameters (only used in grid shift transforms)
    public static final double SRS_WGS84_SEMIMAJOR = 6378137.0;
    public static final double SRS_WGS84_SEMIMINOR = 6356752.314;
    public static final double SRS_WGS84_ESQUARED = 0.0066943799901413165;

    // Angular conversions
    public static final double SEC_TO_RAD = 4.84813681109535993589914102357e-6;
    public static final double D2R = 0.01745329251994329577;   // Degrees to radians
    public static final double R2D = 57.29577951308232088;     // Radians to degrees

    // Mathematical constants
    public static final double HALF_PI = Math.PI / 2;
    public static final double FORTPI = Math.PI / 4;
    public static final double TWO_PI = Math.PI * 2;

    // Ellipsoid constants
    public static final double SIXTH = 0.1666666666666666667;  // 1/6
    public static final double RA4 = 0.04722222222222222222;   // 17/360
    public static final double RA6 = 0.02215608465608465608;

    // Epsilon for floating point comparisons
    public static final double EPSLN = 1.0e-10;

    // SPI is slightly greater than Math.PI, so values that exceed the -180..180
    // degree range by a tiny amount don't get wrapped. This prevents points that
    // have drifted from their original location along the 180th meridian (due to
    // floating point error) from changing their sign.
    public static final double SPI = 3.14159265359;
}
