package org.datasyslab.proj4sedona.constants;

/**
 * Mathematical and geodetic constants used throughout the projection library.
 * Mirrors: lib/constants/values.js
 * 
 * <p>This class contains fundamental constants for coordinate transformations:</p>
 * <ul>
 *   <li>Datum type identifiers for transformation selection</li>
 *   <li>WGS84 ellipsoid parameters for grid shift operations</li>
 *   <li>Angular conversion factors (degrees ↔ radians)</li>
 *   <li>Mathematical constants (π fractions)</li>
 *   <li>Numerical tolerance values</li>
 * </ul>
 * 
 * <p>All angular values in this library use <b>radians</b> internally.
 * Use {@link #D2R} and {@link #R2D} for conversions.</p>
 */
public final class Values {

    private Values() {
        // Utility class - prevent instantiation
    }

    // ==================== Datum Type Identifiers ====================
    // Used to determine which transformation algorithm to apply
    
    /** 3-parameter datum shift: translation only (dx, dy, dz) */
    public static final int PJD_3PARAM = 1;
    
    /** 7-parameter datum shift: translation + rotation + scale (Helmert/Bursa-Wolf) */
    public static final int PJD_7PARAM = 2;
    
    /** Grid-based datum shift: uses NTv2 or similar grid files */
    public static final int PJD_GRIDSHIFT = 3;
    
    /** WGS84 datum or equivalent (no transformation needed) */
    public static final int PJD_WGS84 = 4;
    
    /** No datum defined (treat as WGS84) */
    public static final int PJD_NODATUM = 5;

    // ==================== WGS84 Ellipsoid Parameters ====================
    // Used in grid shift transformations and as reference values
    
    /** WGS84 semi-major axis (equatorial radius) in meters */
    public static final double SRS_WGS84_SEMIMAJOR = 6378137.0;
    
    /** WGS84 semi-minor axis (polar radius) in meters */
    public static final double SRS_WGS84_SEMIMINOR = 6356752.314;
    
    /** WGS84 eccentricity squared: e² = (a² - b²) / a² */
    public static final double SRS_WGS84_ESQUARED = 0.0066943799901413165;

    // ==================== Angular Conversions ====================
    
    /** Seconds of arc to radians: 1 arcsec = π/(180×3600) radians */
    public static final double SEC_TO_RAD = 4.84813681109535993589914102357e-6;
    
    /** Degrees to radians: multiply degrees by this to get radians */
    public static final double D2R = 0.01745329251994329577;   // π/180
    
    /** Radians to degrees: multiply radians by this to get degrees */
    public static final double R2D = 57.29577951308232088;     // 180/π

    // ==================== Mathematical Constants ====================
    
    /** Half π (90° in radians) */
    public static final double HALF_PI = Math.PI / 2;
    
    /** Quarter π (45° in radians) - "FORTPI" from PROJ */
    public static final double FORTPI = Math.PI / 4;
    
    /** Two π (360° in radians) */
    public static final double TWO_PI = Math.PI * 2;

    // ==================== Ellipsoid Series Constants ====================
    // Used in meridional distance calculations
    
    /** 1/6 - used in series expansions */
    public static final double SIXTH = 0.1666666666666666667;
    
    /** 17/360 - used in authalic latitude calculations */
    public static final double RA4 = 0.04722222222222222222;
    
    /** Used in authalic latitude calculations */
    public static final double RA6 = 0.02215608465608465608;

    // ==================== Numerical Tolerances ====================
    
    /** 
     * Epsilon for floating-point comparisons.
     * Used to detect convergence in iterative algorithms.
     */
    public static final double EPSLN = 1.0e-10;

    /**
     * Slightly greater than π (3.14159265359 vs Math.PI = 3.141592653589793).
     * 
     * <p>This value is used in longitude wrapping to prevent points that have
     * drifted slightly past ±180° due to floating-point error from incorrectly
     * wrapping to the opposite hemisphere. Without this tolerance, a point at
     * 180.0000000001° would wrap to -179.9999999999°.</p>
     */
    public static final double SPI = 3.14159265359;
}
