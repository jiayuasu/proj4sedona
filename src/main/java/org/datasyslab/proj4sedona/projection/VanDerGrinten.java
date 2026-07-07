package org.datasyslab.proj4sedona.projection;

import org.datasyslab.proj4sedona.common.ProjMath;
import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.Point;

/**
 * Van der Grinten (I) projection (vandg).
 * Mirrors: lib/projections/vandg.js
 *
 * <p>Projects the world into a circle. Computed on a sphere of radius {@code a}.</p>
 *
 * <p>proj4js's vandg forward leaves the equator/meridian/pole special cases unreturned,
 * so the general formula overwrites them and divides by zero at the equator (it throws
 * on the resulting non-finite value). This port returns those special cases, matching
 * PROJ, so the equator and poles project correctly.</p>
 */
public class VanDerGrinten implements Projection {

    private static final String[] NAMES = {
        "Van_der_Grinten_I", "VanDerGrinten", "Van_der_Grinten", "vandg"
    };

    private double R, long0, x0, y0;
    private Boolean over;

    @Override
    public String[] getNames() { return NAMES; }

    @Override
    public void init(ProjectionParams params) {
        this.R = params.a;
        this.long0 = params.getLong0();
        this.x0 = params.x0;
        this.y0 = params.y0;
        this.over = params.over;
    }

    @Override
    public Point forward(Point p) {
        double lat = p.y;
        double dlon = ProjMath.adjustLon(p.x - long0, over);

        if (Math.abs(lat) <= Values.EPSLN) {
            return new Point(x0 + R * dlon, y0, p.z);
        }
        double theta = ProjMath.asinz(2 * Math.abs(lat / Math.PI));
        if (Math.abs(dlon) <= Values.EPSLN || Math.abs(Math.abs(lat) - Values.HALF_PI) <= Values.EPSLN) {
            double y = (lat >= 0)
                ? y0 + Math.PI * R * Math.tan(0.5 * theta)
                : y0 - Math.PI * R * Math.tan(0.5 * theta);
            return new Point(x0, y, p.z);
        }

        double al = 0.5 * Math.abs((Math.PI / dlon) - (dlon / Math.PI));
        double asq = al * al;
        double sinth = Math.sin(theta);
        double costh = Math.cos(theta);

        double g = costh / (sinth + costh - 1);
        double gsq = g * g;
        double m = g * (2 / sinth - 1);
        double msq = m * m;
        double con = Math.PI * R
            * (al * (g - msq) + Math.sqrt(asq * (g - msq) * (g - msq) - (msq + asq) * (gsq - msq)))
            / (msq + asq);
        if (dlon < 0) {
            con = -con;
        }
        double x = x0 + con;

        double q = asq + g;
        con = Math.PI * R * (m * q - al * Math.sqrt((msq + asq) * (asq + 1) - q * q)) / (msq + asq);
        double y = (lat >= 0) ? y0 + con : y0 - con;

        return new Point(x, y, p.z);
    }

    @Override
    public Point inverse(Point p) {
        double con = Math.PI * R;
        double xx = (p.x - x0) / con;
        double yy = (p.y - y0) / con;

        // Projection center: the cubic solve below is 0/0 (NaN) at the origin, as in
        // proj4js. Return the center (long0, 0) directly so it round-trips.
        if (Math.abs(xx) <= Values.EPSLN && Math.abs(yy) <= Values.EPSLN) {
            return new Point(long0, 0, p.z);
        }

        double xys = xx * xx + yy * yy;
        double c1 = -Math.abs(yy) * (1 + xys);
        double c2 = c1 - 2 * yy * yy + xx * xx;
        double c3 = -2 * c1 + 1 + 2 * yy * yy + xys * xys;
        double d = yy * yy / c3 + (2 * c2 * c2 * c2 / c3 / c3 / c3 - 9 * c1 * c2 / c3 / c3) / 27;
        double a1 = (c1 - c2 * c2 / 3 / c3) / c3;
        double m1 = 2 * Math.sqrt(-a1 / 3);
        con = ((3 * d) / a1) / m1;
        if (Math.abs(con) > 1) {
            con = (con >= 0) ? 1 : -1;
        }
        double th1 = Math.acos(con) / 3;
        double lat;
        if (p.y - y0 >= 0) {
            lat = (-m1 * Math.cos(th1 + Math.PI / 3) - c2 / 3 / c3) * Math.PI;
        } else {
            lat = -(-m1 * Math.cos(th1 + Math.PI / 3) - c2 / 3 / c3) * Math.PI;
        }

        double lon;
        if (Math.abs(xx) < Values.EPSLN) {
            lon = long0;
        } else {
            lon = ProjMath.adjustLon(
                long0 + Math.PI * (xys - 1 + Math.sqrt(1 + 2 * (xx * xx - yy * yy) + xys * xys)) / 2 / xx,
                over);
        }

        return new Point(lon, lat, p.z);
    }
}
