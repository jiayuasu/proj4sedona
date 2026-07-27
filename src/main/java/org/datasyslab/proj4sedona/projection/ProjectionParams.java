package org.datasyslab.proj4sedona.projection;

import java.util.Collections;
import java.util.List;
import org.datasyslab.proj4sedona.core.CoordinateAxis;
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

    /**
     * Resolve a projection-local scale default using JavaScript numeric truthiness.
     * Current proj4js treats zero and NaN as absent when applying these defaults.
     */
    public double getK0OrDefault(double defaultValue) {
        return k0 == 0.0 || Double.isNaN(k0) ? defaultValue : k0;
    }

    /** Whether the source definition explicitly supplied +k_0/+k (including 1.0). */
    public boolean k0Specified;
    
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

    /** Coordinate-system subtype retained from WKT2/PROJJSON (for example Cartesian). */
    public String coordinateSystemType;

    /**
     * Detailed WKT2/PROJJSON coordinate-axis metadata, used for faithful
     * serialization and duplicate-direction polar axis enforcement.
     */
    public List<CoordinateAxis> coordinateAxes = Collections.emptyList();

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

    /** Oblique Mercator without origin offset (+no_uoff / +no_off, variant A) */
    public Boolean noUoff;

    /** Oblique Mercator without rectification rotation (+no_rot) */
    public Boolean noRot;

    /** Center of the longitude wrapping range in radians (+lon_wrap) */
    public Double longWrap;

    /** Satellite/view height in meters (+h) - Geostationary, Tilted Perspective */
    public Double h;

    /** Full original PROJ string, when the CRS was parsed from one (else null) */
    public String projStr;

    /** Inner projection name (+o_proj) - General Oblique Transformation */
    public String oProj;

    /** New pole latitude in radians (+o_lat_p) - ob_tran */
    public Double oLatP;
    /** New pole longitude in radians (+o_lon_p) - ob_tran */
    public Double oLonP;
    /** Rotation angle in radians (+o_alpha) - ob_tran */
    public Double oAlpha;
    /** Rotation center longitude in radians (+o_lon_c) - ob_tran */
    public Double oLonC;
    /** Rotation center latitude in radians (+o_lat_c) - ob_tran */
    public Double oLatC;
    /** First new-equator point longitude in radians (+o_lon_1) - ob_tran */
    public Double oLon1;
    /** First new-equator point latitude in radians (+o_lat_1) - ob_tran */
    public Double oLat1;
    /** Second new-equator point longitude in radians (+o_lon_2) - ob_tran */
    public Double oLon2;
    /** Second new-equator point latitude in radians (+o_lat_2) - ob_tran */
    public Double oLat2;

    /** Camera tilt from nadir in radians (+tilt) - Tilted Perspective */
    public Double tilt;

    /** Camera azimuth from north in radians (+azi) - Tilted Perspective */
    public Double azi;

    /** Sweep axis "x" or "y" (+sweep) - used by the Geostationary projection */
    public String sweep;

    // ==================== Original Definition ====================
    
    /** Original SRS code or PROJ string */
    public String srsCode;
    
    /** Datum code from definition (e.g., "WGS84") */
    public String datumCode;

    /**
     * The ellipsoid as stated by the definition: the +ellps= code, or the WKT/PROJJSON
     * ellipsoid name. May be the "wgs84" placeholder Proj assigns when no ellipsoid
     * was given, so consumers must validate it against a/b before trusting it.
     */
    public String ellps;

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
     * An explicit zero is preserved. This intentionally follows PROJ rather than
     * proj4js's truthy {@code lat2 || lat1} fallback, which loses an equatorial
     * second standard parallel.
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
