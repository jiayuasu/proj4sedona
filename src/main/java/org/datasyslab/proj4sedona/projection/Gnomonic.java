package org.datasyslab.proj4sedona.projection;

import org.datasyslab.proj4sedona.common.ProjMath;
import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.Point;

/**
 * Gnomonic projection (gnom).
 * Mirrors: lib/projections/gnom.js
 *
 * <p>Azimuthal projection from the center of the sphere; every great circle maps to a
 * straight line. Points on the far hemisphere are projected toward infinity on a bearing,
 * as in proj4js.</p>
 */
public class Gnomonic implements Projection {

    // "Gnomonic" alias (beyond proj4js's ["gnom"]) lets a CRS round-trip through the
    // serializer, which emits the "Gnomonic" method name.
    private static final String[] NAMES = {"gnom", "Gnomonic"};

    private double a, k0, lat0, long0, x0, y0;
    private double sinP14, cosP14, infinityDist, rc;
    private Boolean over;

    @Override
    public String[] getNames() { return NAMES; }

    @Override
    public void init(ProjectionParams params) {
        this.a = params.a;
        this.k0 = params.getK0OrDefault(1.0);
        this.lat0 = params.getLat0();
        this.long0 = params.getLong0();
        this.x0 = params.x0;
        this.y0 = params.y0;
        this.over = params.over;

        this.sinP14 = Math.sin(lat0);
        this.cosP14 = Math.cos(lat0);
        this.infinityDist = 1000 * a;
        this.rc = 1;
    }

    @Override
    public Point forward(Point p) {
        double lat = p.y;
        double dlon = ProjMath.adjustLon(p.x - long0, over);
        double sinphi = Math.sin(lat);
        double cosphi = Math.cos(lat);
        double coslon = Math.cos(dlon);
        double g = sinP14 * sinphi + cosP14 * cosphi * coslon;
        double x, y;

        if (g > 0 || Math.abs(g) <= Values.EPSLN) {
            // proj4js uses ksp=1 (ignores k0) in forward while its inverse divides by k0,
            // so a +k_0 CRS would not round-trip. Apply k0 here to match the inverse.
            x = x0 + a * k0 * cosphi * Math.sin(dlon) / g;
            y = y0 + a * k0 * (cosP14 * sinphi - sinP14 * cosphi * coslon) / g;
        } else {
            // Opposing hemisphere: project toward infinity on the equivalent bearing.
            x = x0 + infinityDist * cosphi * Math.sin(dlon);
            y = y0 + infinityDist * (cosP14 * sinphi - sinP14 * cosphi * coslon);
        }
        return new Point(x, y, p.z);
    }

    @Override
    public Point inverse(Point p) {
        double x = (p.x - x0) / a / k0;
        double y = (p.y - y0) / a / k0;
        double lon, lat;

        double rh = Math.sqrt(x * x + y * y);
        if (rh != 0) {
            double c = Math.atan2(rh, rc);
            double sinc = Math.sin(c);
            double cosc = Math.cos(c);
            lat = ProjMath.asinz(cosc * sinP14 + (y * sinc * cosP14) / rh);
            lon = Math.atan2(x * sinc, rh * cosP14 * cosc - y * sinP14 * sinc);
            lon = ProjMath.adjustLon(long0 + lon, over);
        } else {
            // Projection center. proj4js reads an unset phic0 here (NaN); use lat0.
            lat = lat0;
            lon = long0;
        }
        return new Point(lon, lat, p.z);
    }
}
