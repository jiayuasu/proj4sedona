package org.datasyslab.proj4sedona.projection;

import org.datasyslab.proj4sedona.common.ProjMath;
import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.Point;

/**
 * Equidistant Cylindrical (Plate Carrée / Equirectangular) projection.
 * Mirrors: lib/projections/eqc.js
 * 
 * <p>A simple cylindrical projection where meridians and parallels are
 * equally spaced straight lines. Also known as Plate Carrée when lat_ts=0.</p>
 */
public class EquidistantCylindrical implements Projection {

    private static final String[] NAMES = {
        "Equidistant_Cylindrical", "Plate_Carree", "eqc", "Equirectangular"
    };

    private double a, lat0, latTs, long0, x0, y0, rc;
    private Boolean over;

    @Override
    public String[] getNames() { return NAMES; }

    @Override
    public void init(ProjectionParams params) {
        this.a = params.a;
        this.lat0 = defaultToZero(params.lat0);
        this.latTs = resolveLatitudeOfTrueScale(params.latTs);
        this.long0 = defaultToZero(params.long0);
        this.x0 = defaultToZero(params.x0);
        this.y0 = defaultToZero(params.y0);
        this.over = params.over;
        this.rc = Math.cos(latTs);
    }

    /**
     * Resolve {@code +lat_ts} using JavaScript's {@code value || 0} semantics.
     * In particular, an omitted, zero, or NaN value selects Plate Carrée rather
     * than inheriting {@code +lat_0}.
     */
    public static double resolveLatitudeOfTrueScale(Double latTs) {
        return latTs == null ? 0.0 : defaultToZero(latTs);
    }

    private static double defaultToZero(Double value) {
        return value == null ? 0.0 : defaultToZero(value.doubleValue());
    }

    private static double defaultToZero(double value) {
        return value == 0.0 || Double.isNaN(value) ? 0.0 : value;
    }

    @Override
    public Point forward(Point p) {
        double lon = ProjMath.adjustLon(p.x - long0, over);
        double lat = ProjMath.adjustLat(p.y - lat0);
        double x = a * lon * rc + x0;
        double y = a * lat + y0;
        return new Point(x, y, p.z);
    }

    @Override
    public Point inverse(Point p) {
        double x = p.x - x0;
        double y = p.y - y0;
        double lon = ProjMath.adjustLon(x / (a * rc) + long0, over);
        double lat = ProjMath.adjustLat(lat0 + y / a);
        return new Point(lon, lat, p.z);
    }
}
