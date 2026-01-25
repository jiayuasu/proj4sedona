package org.proj4sedona.projection;

import org.proj4sedona.core.DatumParams;

/**
 * Parameters passed to projection implementations during initialization.
 * Contains all ellipsoid, datum, and projection-specific parameters.
 * 
 * This class is populated from ProjectionDef after deriving constants.
 */
public class ProjectionParams {

    // Ellipsoid parameters (derived)
    public double a;            // Semi-major axis
    public double b;            // Semi-minor axis
    public double rf;           // Inverse flattening
    public boolean sphere;      // True if this is a sphere

    // Eccentricity (derived)
    public double es;           // Eccentricity squared
    public double e;            // Eccentricity
    public double ep2;          // Second eccentricity squared

    // Datum
    public DatumParams datum;

    // Projection parameters (from definition)
    public String projName;
    public Double lat0;         // Latitude of origin (radians)
    public Double lat1;         // First standard parallel (radians)
    public Double lat2;         // Second standard parallel (radians)
    public Double latTs;        // Latitude of true scale (radians)
    public Double long0;        // Central meridian (radians)
    public Double long1;
    public Double long2;
    public Double alpha;        // Azimuth (radians)
    public Double longc;        // Longitude of center (radians)
    public Double rectifiedGridAngle;  // Gamma (radians)

    // Scale and offsets
    public double k0 = 1.0;     // Scale factor
    public double x0 = 0.0;     // False easting
    public double y0 = 0.0;     // False northing

    // Units
    public Double toMeter;      // Conversion to meters
    public String units;
    public Double fromGreenwich; // Prime meridian offset (radians)
    public String axis = "enu"; // Axis order

    // UTM specific
    public Integer zone;
    public Boolean utmSouth;

    // Flags
    public Boolean rA;          // Use authalic radius
    public Boolean approx;      // Use approximate algorithms
    public Boolean over;        // Allow longitude wrapping

    // Original definition
    public String srsCode;
    public String datumCode;

    /**
     * Get long0 with default of 0.
     */
    public double getLong0() {
        return long0 != null ? long0 : 0.0;
    }

    /**
     * Get lat0 with default of 0.
     */
    public double getLat0() {
        return lat0 != null ? lat0 : 0.0;
    }

    /**
     * Get lat1, defaulting to lat0 if not set.
     * Used by Lambert Conformal Conic 1SP.
     */
    public double getLat1() {
        if (lat1 != null) return lat1;
        if (lat0 != null) return lat0;
        return 0.0;
    }

    /**
     * Get lat2, defaulting to lat1 if not set.
     */
    public double getLat2() {
        if (lat2 != null) return lat2;
        return getLat1();
    }

    /**
     * Get latTs (latitude of true scale), defaulting to lat0.
     */
    public double getLatTs() {
        if (latTs != null) return latTs;
        if (lat0 != null) return lat0;
        return 0.0;
    }
}
