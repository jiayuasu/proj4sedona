package org.datasyslab.proj4sedona.projection;

import org.datasyslab.proj4sedona.core.Point;

/**
 * Interface for map projections.
 * Each projection implementation provides forward and inverse transformations.
 * 
 * Mirrors the projection object structure in proj4js.
 */
public interface Projection {

    /**
     * Get the projection names (aliases).
     * The first name is the primary name.
     */
    String[] getNames();

    /**
     * Initialize the projection with the given parameters.
     * Called after all parameters are set but before any transformations.
     * 
     * @param params The projection parameters
     */
    void init(ProjectionParams params);

    /**
     * Forward projection: geodetic (lon/lat in radians) to projected (x/y in meters).
     * 
     * @param p Point with x=longitude, y=latitude (both in radians)
     * @return Point with x=easting, y=northing (in projection units, typically meters)
     */
    Point forward(Point p);

    /**
     * Inverse projection: projected (x/y in meters) to geodetic (lon/lat in radians).
     * 
     * @param p Point with x=easting, y=northing (in projection units)
     * @return Point with x=longitude, y=latitude (both in radians)
     */
    Point inverse(Point p);
}
