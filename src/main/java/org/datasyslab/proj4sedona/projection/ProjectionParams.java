package org.datasyslab.proj4sedona.projection;

import org.datasyslab.proj4sedona.core.DatumParams;

/**
 * Parameters passed to projection implementations during initialization.
 * Contains all ellipsoid, datum, and projection-specific parameters.
 * 
 * <p>This class is populated from {@link org.datasyslab.proj4sedona.core.ProjectionDef} after
 * deriving constants (ellipsoid parameters, eccentricity, etc.) in 
 * {@link org.datasyslab.proj4sedona.core.Proj}.</p>
 * 
 * <p>Parameter categories:</p>
 * <ul>
 *   <li><b>Ellipsoid parameters</b>: a, b, rf, sphere flag - derived from ellipsoid definition</li>
 *   <li><b>Eccentricity</b>: es, e, ep2 - calculated from ellipsoid</li>
 *   <li><b>Datum</b>: transformation parameters for datum shifts</li>
 *   <li><b>Projection parameters</b>: lat0, long0, lat1, lat2, etc. - from PROJ string</li>
 *   <li><b>Scale and offsets</b>: k0, x0, y0 - false easting/northing and scale factor</li>
 *   <li><b>Units</b>: toMeter conversion, axis order</li>
 *   <li><b>Flags</b>: rA (authalic), approx, over (longitude wrapping)</li>
 * </ul>
 * 
 * <p>All angular parameters (lat0, long0, etc.) are stored in <b>radians</b>.</p>
 */
public class ProjectionParams {

    // ==================== Ellipsoid Parameters (Derived) ====================
    
    /** Semi-major axis (equatorial radius) in meters */
    public double a;
    
    /** Semi-minor axis (polar radius) in meters */
    public double b;
    
    /** Inverse flattening: rf = a / (a - b) */
    public double rf;
    
    /** True if this is a perfect sphere (a == b) */
    public boolean sphere;

    // ==================== Eccentricity (Derived) ====================
    
    /** First eccentricity squared: es = (a² - b²) / a² */
    public double es;
    
    /** First eccentricity: e = sqrt(es) */
    public double e;
    
    /** Second eccentricity squared: ep2 = (a² - b²) / b² */
    public double ep2;

    // ==================== Datum ====================
    
    /** Datum transformation parameters (3-param, 7-param, or grid shift) */
    public DatumParams datum;

    // ==================== Projection Parameters ====================
    
    /** Projection name/type (e.g., "tmerc", "utm", "lcc", "merc") */
    public String projName;
    
    /** Latitude of origin in radians (+lat_0) */
    public Double lat0;
    
    /** First standard parallel in radians (+lat_1) - used by conic projections */
    public Double lat1;
    
    /** Second standard parallel in radians (+lat_2) - used by conic projections */
    public Double lat2;
    
    /** Latitude of true scale in radians (+lat_ts) - used by Mercator */
    public Double latTs;
    
    /** Central meridian (longitude of origin) in radians (+lon_0) */
    public Double long0;
    
    /** Additional longitude parameters for some projections */
    public Double long1;
    public Double long2;
    
    /** Azimuth angle in radians (+alpha) - used by oblique projections */
    public Double alpha;
    
    /** Longitude of center in radians (+longc) - used by oblique projections */
    public Double longc;
    
    /** Rectified grid angle (gamma) in radians */
    public Double rectifiedGridAngle;

    // ==================== Scale and Offsets ====================
    
    /** Scale factor at central meridian (+k_0 or +k), defaults to 1.0 */
    public double k0 = 1.0;
    
    /** False easting in projection units (+x_0), defaults to 0.0 */
    public double x0 = 0.0;
    
    /** False northing in projection units (+y_0), defaults to 0.0 */
    public double y0 = 0.0;

    // ==================== Units ====================
    
    /** Conversion factor to meters (+to_meter), null means meters */
    public Double toMeter;
    
    /** Unit name (e.g., "m", "ft", "us-ft") */
    public String units;
    
    /** Prime meridian offset from Greenwich in radians (+pm) */
    public Double fromGreenwich;
    
    /** Axis order string (+axis), defaults to "enu" (east-north-up) */
    public String axis = "enu";

    // ==================== UTM Specific ====================
    
    /** UTM zone number (1-60) */
    public Integer zone;
    
    /** True for southern hemisphere UTM (+south) */
    public Boolean utmSouth;

    // ==================== Flags ====================
    
    /** Use authalic radius (+R_A) - for equal-area calculations */
    public Boolean rA;
    
    /** Use approximate/fast algorithms (+approx) */
    public Boolean approx;
    
    /** Allow longitude values outside ±180° (+over) - prevents wrapping */
    public Boolean over;

    // ==================== Original Definition ====================
    
    /** Original SRS code or PROJ string */
    public String srsCode;
    
    /** Datum code from definition (e.g., "WGS84") */
    public String datumCode;

    // ==================== Accessor Methods ====================

    /**
     * Get central meridian (long0), defaulting to 0.
     * @return Central meridian in radians
     */
    public double getLong0() {
        return long0 != null ? long0 : 0.0;
    }

    /**
     * Get latitude of origin (lat0), defaulting to 0.
     * @return Latitude of origin in radians
     */
    public double getLat0() {
        return lat0 != null ? lat0 : 0.0;
    }

    /**
     * Get first standard parallel (lat1), defaulting to lat0 if not set.
     * Used by Lambert Conformal Conic 1SP where only one parallel is specified.
     * @return First standard parallel in radians
     */
    public double getLat1() {
        if (lat1 != null) return lat1;
        if (lat0 != null) return lat0;
        return 0.0;
    }

    /**
     * Get second standard parallel (lat2), defaulting to lat1 if not set.
     * @return Second standard parallel in radians
     */
    public double getLat2() {
        if (lat2 != null) return lat2;
        return getLat1();
    }

    /**
     * Get latitude of true scale (latTs), defaulting to lat0.
     * Used by Mercator projection for non-equatorial true scale.
     * @return Latitude of true scale in radians
     */
    public double getLatTs() {
        if (latTs != null) return latTs;
        if (lat0 != null) return lat0;
        return 0.0;
    }
}
