package org.datasyslab.proj4sedona.projection;

import org.datasyslab.proj4sedona.common.ProjMath;
import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.Point;

/**
 * Eckert VI projection (eck6).
 * Mirrors: lib/projections/eck6.js
 *
 * <p>A pseudocylindrical equal-area projection. proj4js implements it as the generalized
 * spherical sinusoidal family with {@code m = 1}, {@code n = 1 + pi/2} (and always forces
 * spherical computation); this class inlines that form so it does not depend on the
 * Sinusoidal implementation.</p>
 *
 * <p>Deviation from proj4js: its forward omits {@code +x_0/+y_0} while its inverse
 * subtracts them; this port applies them in both directions so offset CRSs round-trip
 * (see {@code forward}). Standard eck6 CRSs use {@code x_0=y_0=0}, so their output is
 * unchanged.</p>
 */
public class EckertVI implements Projection {

    private static final String[] NAMES = {"Eckert_VI", "eck6"};

    private static final int MAX_ITER = 20;
    private static final double M = 1.0;
    private static final double N = 2.570796326794896619231321691; // 1 + pi/2

    private double a, long0, x0, y0, cx, cy;
    private Boolean over;

    @Override
    public String[] getNames() { return NAMES; }

    @Override
    public void init(ProjectionParams params) {
        // Eckert VI always uses spherical computation.
        this.a = params.a;
        this.long0 = params.getLong0();
        this.x0 = params.x0;
        this.y0 = params.y0;
        this.over = params.over;

        this.cy = Math.sqrt((M + 1.0) / N);
        this.cx = cy / (M + 1.0);
    }

    @Override
    public Point forward(Point p) {
        double lon = ProjMath.adjustLon(p.x - long0, over);
        double lat = p.y;

        double k = N * Math.sin(lat);
        for (int i = MAX_ITER; i != 0; --i) {
            double v = (M * lat + Math.sin(lat) - k) / (M + Math.cos(lat));
            lat -= v;
            if (Math.abs(v) < Values.EPSLN) {
                break;
            }
        }
        double x = a * cx * lon * (M + Math.cos(lat));
        double y = a * cy * lat;

        // proj4js's eck6 forward (via the generalized sinu) omits x0/y0 while its inverse
        // subtracts them, so an offset CRS would not round-trip. Apply them here (the
        // inverse already reverses them). Standard eck6 CRSs use x_0=y_0=0, so their
        // output is unchanged.
        return new Point(x + x0, y + y0, p.z);
    }

    @Override
    public Point inverse(Point p) {
        double lon = (p.x - x0) / a;
        double lat = (p.y - y0) / a;

        lat /= cy;
        lon = lon / (cx * (M + Math.cos(lat)));
        lat = ProjMath.asinz((M * lat + Math.sin(lat)) / N);
        lon = ProjMath.adjustLon(lon + long0, over);
        lat = ProjMath.adjustLat(lat);

        return new Point(lon, lat, p.z);
    }
}
