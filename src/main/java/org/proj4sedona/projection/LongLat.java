package org.proj4sedona.projection;

import org.proj4sedona.core.Point;

/**
 * Long/Lat identity projection.
 * Mirrors: lib/projections/longlat.js
 * 
 * This is an identity transformation - coordinates pass through unchanged.
 * Used for geographic coordinate systems where data is in degrees or radians.
 */
public class LongLat implements Projection {

    public static final String[] NAMES = {"longlat", "identity"};

    @Override
    public String[] getNames() {
        return NAMES;
    }

    @Override
    public void init(ProjectionParams params) {
        // No-op for longlat
    }

    @Override
    public Point forward(Point p) {
        // Identity transformation
        return p;
    }

    @Override
    public Point inverse(Point p) {
        // Identity transformation
        return p;
    }
}
