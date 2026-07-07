package org.datasyslab.proj4sedona.projection;

import org.datasyslab.proj4sedona.common.ProjMath;
import org.datasyslab.proj4sedona.core.Point;

/**
 * Miller Cylindrical projection (mill).
 * Mirrors: lib/projections/mill.js
 *
 * <p>A cylindrical compromise projection related to Mercator but showing the poles.</p>
 */
public class MillerCylindrical implements Projection {

    private static final String[] NAMES = {"Miller_Cylindrical", "mill"};

    private double a, long0, x0, y0;
    private Boolean over;

    @Override
    public String[] getNames() { return NAMES; }

    @Override
    public void init(ProjectionParams params) {
        this.a = params.a;
        this.long0 = params.getLong0();
        this.x0 = params.x0;
        this.y0 = params.y0;
        this.over = params.over;
    }

    @Override
    public Point forward(Point p) {
        double dlon = ProjMath.adjustLon(p.x - long0, over);
        double x = x0 + a * dlon;
        double y = y0 + a * Math.log(Math.tan((Math.PI / 4) + (p.y / 2.5))) * 1.25;
        return new Point(x, y, p.z);
    }

    @Override
    public Point inverse(Point p) {
        double x = p.x - x0;
        double y = p.y - y0;
        double lon = ProjMath.adjustLon(long0 + x / a, over);
        double lat = 2.5 * (Math.atan(Math.exp(0.8 * y / a)) - Math.PI / 4);
        return new Point(lon, lat, p.z);
    }
}
