package org.proj4sedona.projection;

import org.proj4sedona.common.ProjMath;
import org.proj4sedona.constants.Values;
import org.proj4sedona.core.Point;

/**
 * Mollweide projection implementation.
 * Mirrors: lib/projections/moll.js
 * 
 * <p>A pseudocylindrical equal-area projection. Commonly used for world maps
 * where area preservation is important.</p>
 */
public class Mollweide implements Projection {

    private static final String[] NAMES = {"Mollweide", "moll"};

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
        double lon = p.x;
        double lat = p.y;

        double deltaLon = ProjMath.adjustLon(lon - long0, over);
        double theta = lat;
        double con = Math.PI * Math.sin(lat);

        // Newton-Raphson iteration
        while (true) {
            double deltaTheta = -(theta + Math.sin(theta) - con) / (1 + Math.cos(theta));
            theta += deltaTheta;
            if (Math.abs(deltaTheta) < Values.EPSLN) {
                break;
            }
        }
        theta /= 2;

        // Handle poles
        if (Math.PI / 2 - Math.abs(lat) < Values.EPSLN) {
            deltaLon = 0;
        }

        double x = 0.900316316158 * a * deltaLon * Math.cos(theta) + x0;
        double y = 1.4142135623731 * a * Math.sin(theta) + y0;

        return new Point(x, y, p.z);
    }

    @Override
    public Point inverse(Point p) {
        double x = p.x - x0;
        double y = p.y - y0;

        double arg = y / (1.4142135623731 * a);
        if (Math.abs(arg) > 0.999999999999) {
            arg = 0.999999999999 * Math.signum(arg);
        }
        double theta = Math.asin(arg);

        double lon = ProjMath.adjustLon(long0 + x / (0.900316316158 * a * Math.cos(theta)), over);
        if (lon < -Math.PI) lon = -Math.PI;
        if (lon > Math.PI) lon = Math.PI;

        arg = (2 * theta + Math.sin(2 * theta)) / Math.PI;
        if (Math.abs(arg) > 1) {
            arg = Math.signum(arg);
        }
        double lat = Math.asin(arg);

        return new Point(lon, lat, p.z);
    }
}
