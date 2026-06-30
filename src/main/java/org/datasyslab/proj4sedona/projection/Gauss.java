package org.datasyslab.proj4sedona.projection;

import org.datasyslab.proj4sedona.common.ProjMath;
import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.Point;

/**
 * Gauss conformal sphere mapping.
 * Mirrors: lib/projections/gauss.js
 *
 * <p>Not a standalone projection: it maps geodetic longitude/latitude on the
 * ellipsoid to longitude/latitude on a conformal sphere. Used as the base step
 * of the oblique stereographic alternative ({@link StereographicAlternative}).</p>
 */
final class Gauss {

    private static final int MAX_ITER = 20;

    final double rc;
    final double phic0;
    private final double c;
    private final double ratexp;
    private final double k;
    private final double e;

    Gauss(double lat0, double e, double es) {
        this.e = e;
        double sphi = Math.sin(lat0);
        double cphi = Math.cos(lat0);
        cphi *= cphi;
        this.rc = Math.sqrt(1 - es) / (1 - es * sphi * sphi);
        this.c = Math.sqrt(1 + es * cphi * cphi / (1 - es));
        this.phic0 = Math.asin(sphi / this.c);
        this.ratexp = 0.5 * this.c * e;
        this.k = Math.tan(0.5 * this.phic0 + Values.FORTPI)
                / (Math.pow(Math.tan(0.5 * lat0 + Values.FORTPI), this.c)
                    * ProjMath.srat(e * sphi, this.ratexp));
    }

    /** Forward: ellipsoidal lon/lat to conformal-sphere lon/lat (mutates p). */
    Point forward(Point p) {
        double lon = p.x;
        double lat = p.y;

        p.y = 2 * Math.atan(this.k * Math.pow(Math.tan(0.5 * lat + Values.FORTPI), this.c)
                * ProjMath.srat(this.e * Math.sin(lat), this.ratexp)) - Values.HALF_PI;
        p.x = this.c * lon;
        return p;
    }

    /** Inverse: conformal-sphere lon/lat back to ellipsoidal lon/lat (mutates p); null if it fails to converge. */
    Point inverse(Point p) {
        double DEL_TOL = 1e-14;
        double lon = p.x / this.c;
        double lat = p.y;
        double num = Math.pow(Math.tan(0.5 * lat + Values.FORTPI) / this.k, 1 / this.c);
        int i;
        for (i = MAX_ITER; i > 0; --i) {
            lat = 2 * Math.atan(num * ProjMath.srat(this.e * Math.sin(p.y), -0.5 * this.e)) - Values.HALF_PI;
            if (Math.abs(lat - p.y) < DEL_TOL) {
                break;
            }
            p.y = lat;
        }
        // convergence failed
        if (i == 0) {
            return null;
        }
        p.x = lon;
        p.y = lat;
        return p;
    }
}
