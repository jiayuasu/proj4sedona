package org.datasyslab.proj4sedona.projection;

import org.datasyslab.proj4sedona.common.ProjMath;
import org.datasyslab.proj4sedona.core.Point;

/**
 * Oblique Stereographic Alternative (a.k.a. Double Stereographic) projection.
 * Mirrors: lib/projections/sterea.js
 *
 * <p>Distinct from the (Snyder) {@link Stereographic} projection: it maps the
 * ellipsoid to a conformal sphere via {@link Gauss} first, then applies the
 * spherical stereographic. This is the algorithm used by EPSG method 9809
 * ("Oblique Stereographic"), e.g. EPSG:28992 (Amersfoort / RD New) and
 * EPSG:2036 (New Brunswick Stereographic).</p>
 */
public class StereographicAlternative implements Projection {

    // Note: proj4js's sterea.js also lists "Stereographic_North_Pole", but that is
    // a proj4js bug — the ESRI polar-north name is the Snyder polar stereographic
    // and must honor +lat_ts, which this algorithm ignores. It is intentionally
    // kept on Stereographic (matching PROJ/pyproj and proj4sedona's prior behavior).
    private static final String[] NAMES = {
        "Oblique_Stereographic", "sterea",
        "Oblique Stereographic Alternative", "Double_Stereographic"
    };

    private double a, e, es, lat0, long0, k0, x0, y0;
    private double sinc0, cosc0, R2;
    private Boolean over;
    private Gauss gauss;

    @Override
    public String[] getNames() { return NAMES; }

    @Override
    public void init(ProjectionParams params) {
        this.a = params.a;
        this.es = params.es;
        this.e = Math.sqrt(es);
        this.lat0 = params.getLat0();
        this.long0 = params.getLong0();
        this.k0 = params.getK0OrDefault(1.0);
        this.x0 = params.x0;
        this.y0 = params.y0;
        this.over = params.over;

        this.gauss = new Gauss(lat0, e, es);
        if (gauss.rc == 0) {
            return;
        }
        this.sinc0 = Math.sin(gauss.phic0);
        this.cosc0 = Math.cos(gauss.phic0);
        this.R2 = 2 * gauss.rc;
    }

    @Override
    public Point forward(Point p) {
        Point q = new Point(ProjMath.adjustLon(p.x - long0, over), p.y, p.z);
        gauss.forward(q);

        double sinc = Math.sin(q.y);
        double cosc = Math.cos(q.y);
        double cosl = Math.cos(q.x);
        double k = k0 * R2 / (1 + sinc0 * sinc + cosc0 * cosc * cosl);
        double x = k * cosc * Math.sin(q.x);
        double y = k * (cosc0 * sinc - sinc0 * cosc * cosl);

        return new Point(a * x + x0, a * y + y0, p.z);
    }

    @Override
    public Point inverse(Point p) {
        double x = (p.x - x0) / a / k0;
        double y = (p.y - y0) / a / k0;
        double lon, lat;

        double rho = Math.hypot(x, y);
        if (rho != 0) {
            double c = 2 * Math.atan2(rho, R2);
            double sinc = Math.sin(c);
            double cosc = Math.cos(c);
            lat = Math.asin(cosc * sinc0 + y * sinc * cosc0 / rho);
            lon = Math.atan2(x * sinc, rho * cosc0 * cosc - y * sinc0 * sinc);
        } else {
            lat = gauss.phic0;
            lon = 0;
        }

        Point q = new Point(lon, lat, p.z);
        if (gauss.inverse(q) == null) {
            return null;
        }
        return new Point(ProjMath.adjustLon(q.x + long0, over), q.y, p.z);
    }
}
