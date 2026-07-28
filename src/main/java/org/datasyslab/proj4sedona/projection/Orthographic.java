package org.datasyslab.proj4sedona.projection;

import org.datasyslab.proj4sedona.common.ProjMath;
import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.Point;

/**
 * Orthographic projection (ortho).
 * Mirrors: lib/projections/ortho.js
 *
 * <p>Azimuthal projection giving the view of the globe from infinite distance; only the
 * near hemisphere is visible. Points on the far hemisphere are unprojectable (null).</p>
 */
public class Orthographic implements Projection {

    // "Orthographic" alias (beyond proj4js's ["ortho"]) lets a CRS round-trip through
    // the serializer, which emits the "Orthographic" method name.
    private static final String[] NAMES = {"ortho", "Orthographic"};

    private double a, lat0, long0, x0, y0;
    private double sinP14, cosP14;
    private Boolean over;

    @Override
    public String[] getNames() { return NAMES; }

    @Override
    public void init(ProjectionParams params) {
        this.a = params.a;
        this.lat0 = params.getLat0();
        this.long0 = params.getLong0();
        this.x0 = params.x0;
        this.y0 = params.y0;
        this.over = params.over;

        this.sinP14 = Math.sin(lat0);
        this.cosP14 = Math.cos(lat0);
    }

    @Override
    public Point forward(Point p) {
        double lat = p.y;
        double dlon = ProjMath.adjustLon(p.x - long0, over);
        double sinphi = Math.sin(lat);
        double cosphi = Math.cos(lat);
        double coslon = Math.cos(dlon);
        double g = sinP14 * sinphi + cosP14 * cosphi * coslon;

        if (g > 0 || Math.abs(g) <= Values.EPSLN) {
            // Current proj4js applies false easting here as well as subtracting it
            // in the inverse (4572f6a), matching PROJ and this port's original behavior.
            double x = x0 + a * cosphi * Math.sin(dlon);
            double y = y0 + a * (cosP14 * sinphi - sinP14 * cosphi * coslon);
            return new Point(x, y, p.z);
        }
        // Far hemisphere: unprojectable.
        return null;
    }

    @Override
    public Point inverse(Point p) {
        double x = p.x - x0;
        double y = p.y - y0;
        double rh = Math.sqrt(x * x + y * y);
        double z = ProjMath.asinz(rh / a);
        double sinz = Math.sin(z);
        double cosz = Math.cos(z);

        double lon = long0;
        if (Math.abs(rh) <= Values.EPSLN) {
            return new Point(lon, lat0, p.z);
        }
        double lat = ProjMath.asinz(cosz * sinP14 + (y * sinz * cosP14) / rh);
        double con = Math.abs(lat0) - Values.HALF_PI;
        if (Math.abs(con) <= Values.EPSLN) {
            if (lat0 >= 0) {
                lon = ProjMath.adjustLon(long0 + Math.atan2(x, -y), over);
            } else {
                lon = ProjMath.adjustLon(long0 - Math.atan2(-x, y), over);
            }
            return new Point(lon, lat, p.z);
        }
        lon = ProjMath.adjustLon(long0 + Math.atan2(x * sinz, rh * cosP14 * cosz - y * sinP14 * sinz), over);
        return new Point(lon, lat, p.z);
    }
}
