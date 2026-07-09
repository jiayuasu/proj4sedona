package org.datasyslab.proj4sedona.projection;

import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.datum.DatumUtils;

/**
 * Geocentric coordinates (geocent).
 * Mirrors: lib/projections/geocent.js
 *
 * <p>Not a map projection in the usual sense: converts geodetic longitude/latitude/
 * height to Earth-centered, Earth-fixed (ECEF) X/Y/Z in meters (e.g. EPSG:4978).
 * Delegates to the geodetic&harr;geocentric conversions shared with the datum
 * pipeline. The transform pipeline keeps the computed Z even for 2D input
 * (proj4js 0ee1202, backported ahead of this port).</p>
 */
public class Geocentric implements Projection {

    private static final String[] NAMES = {"Geocentric", "geocentric", "geocent", "Geocent"};

    private double a, b, es;

    @Override
    public String[] getNames() { return NAMES; }

    @Override
    public void init(ProjectionParams params) {
        this.a = params.a;
        this.b = params.b;
        this.es = params.es;
    }

    @Override
    public Point forward(Point p) {
        return DatumUtils.geodeticToGeocentric(p, es, a);
    }

    @Override
    public Point inverse(Point p) {
        return DatumUtils.geocentricToGeodetic(p, es, a, b);
    }
}
