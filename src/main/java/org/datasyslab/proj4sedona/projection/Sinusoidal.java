package org.datasyslab.proj4sedona.projection;

import org.datasyslab.proj4sedona.common.ProjMath;
import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.Point;

/**
 * Sinusoidal (Sanson-Flamsteed) projection implementation.
 * Mirrors: lib/projections/sinu.js
 * 
 * <p>A pseudocylindrical equal-area projection. Commonly used for world maps
 * and maps of Africa and South America.</p>
 */
public class Sinusoidal implements Projection {

    private static final String[] NAMES = {"Sinusoidal", "sinu"};

    private double a, es, long0, x0, y0;
    private double[] en;
    private boolean sphere;
    private Boolean over;

    @Override
    public String[] getNames() { return NAMES; }

    @Override
    public void init(ProjectionParams params) {
        this.a = params.a;
        this.es = params.es;
        this.long0 = params.getLong0();
        this.x0 = params.x0;
        this.y0 = params.y0;
        this.sphere = params.sphere;
        this.over = params.over;

        if (!sphere) {
            en = ProjMath.pjEnfn(es);
        }
    }

    @Override
    public Point forward(Point p) {
        double lon = ProjMath.adjustLon(p.x - long0, over);
        double lat = p.y;
        double x, y;

        if (sphere) {
            x = a * lon * Math.cos(lat);
            y = a * lat;
        } else {
            double s = Math.sin(lat);
            double c = Math.cos(lat);
            y = a * ProjMath.pjMlfn(lat, s, c, en);
            x = a * lon * c / Math.sqrt(1 - es * s * s);
        }

        return new Point(x, y, p.z);
    }

    @Override
    public Point inverse(Point p) {
        double x = p.x - x0;
        double lon = x / a;
        double y = p.y - y0;
        double lat = y / a;

        if (sphere) {
            lon = lon / Math.cos(lat);
            lon = ProjMath.adjustLon(lon + long0, over);
            lat = ProjMath.adjustLat(lat);
        } else {
            lat = ProjMath.pjInvMlfn(y / a, es, en);
            double s = Math.abs(lat);
            if (s < Values.HALF_PI) {
                s = Math.sin(lat);
                double temp = long0 + x * Math.sqrt(1 - es * s * s) / (a * Math.cos(lat));
                lon = ProjMath.adjustLon(temp, over);
            } else if (s - Values.EPSLN < Values.HALF_PI) {
                lon = long0;
            }
        }

        return new Point(lon, lat, p.z);
    }
}
