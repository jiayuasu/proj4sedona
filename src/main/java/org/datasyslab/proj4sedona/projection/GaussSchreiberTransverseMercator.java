package org.datasyslab.proj4sedona.projection;

import org.datasyslab.proj4sedona.common.ProjMath;
import org.datasyslab.proj4sedona.core.Point;

/**
 * Gauss-Schreiber Transverse Mercator projection (gstmerc).
 * Mirrors: lib/projections/gstmerc.js
 *
 * <p>The double-projection transverse Mercator used by IGN for some French
 * territories, e.g. Gauss Laborde Réunion. Ellipsoid latitude is mapped to a
 * conformal sphere via the isometric latitude, then the spherical transverse
 * Mercator is applied.</p>
 *
 * <p>The "gstmerg" alias is a historical typo kept by proj4js; the
 * "Gauss_Schreiber_Transverse_Mercator" alias matches the serializer's WKT
 * method name so serialized CRSs re-import.</p>
 */
public class GaussSchreiberTransverseMercator implements Projection {

    private static final String[] NAMES = {
        "gstmerg", "gstmerc", "Gauss_Schreiber_Transverse_Mercator"
    };

    private double e, lc, rs, cp, n2, xs, ys;

    @Override
    public String[] getNames() { return NAMES; }

    @Override
    public void init(ProjectionParams params) {
        // array of: a, b, lon0, lat0, k0, x0, y0
        double temp = params.b / params.a;
        this.e = Math.sqrt(1 - temp * temp);
        this.lc = params.getLong0();
        double lat0 = params.getLat0();
        this.rs = Math.sqrt(1 + e * e * Math.pow(Math.cos(lat0), 4) / (1 - e * e));
        double sinz = Math.sin(lat0);
        double pc = Math.asin(sinz / rs);
        double sinzpc = Math.sin(pc);
        this.cp = ProjMath.latiso(0, pc, sinzpc) - rs * ProjMath.latiso(e, lat0, sinz);
        this.n2 = params.k0 * params.a * Math.sqrt(1 - e * e) / (1 - e * e * sinz * sinz);
        this.xs = params.x0;
        this.ys = params.y0 - n2 * pc;
    }

    @Override
    public Point forward(Point p) {
        double lon = p.x;
        double lat = p.y;

        double L = rs * (lon - lc);
        double Ls = cp + rs * ProjMath.latiso(e, lat, Math.sin(lat));
        double lat1 = Math.asin(Math.sin(L) / Math.cosh(Ls));
        double Ls1 = ProjMath.latiso(0, lat1, Math.sin(lat1));
        double x = xs + n2 * Ls1;
        double y = ys + n2 * Math.atan(Math.sinh(Ls) / Math.cos(L));
        return new Point(x, y, p.z);
    }

    @Override
    public Point inverse(Point p) {
        double x = p.x;
        double y = p.y;

        double L = Math.atan(Math.sinh((x - xs) / n2) / Math.cos((y - ys) / n2));
        double lat1 = Math.asin(Math.sin((y - ys) / n2) / Math.cosh((x - xs) / n2));
        double LC = ProjMath.latiso(0, lat1, Math.sin(lat1));
        double lon = lc + L / rs;
        double lat = ProjMath.invlatiso(e, (LC - cp) / rs);
        return new Point(lon, lat, p.z);
    }
}
