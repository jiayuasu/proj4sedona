package org.datasyslab.proj4sedona.projection;

import org.datasyslab.proj4sedona.common.ProjMath;
import org.datasyslab.proj4sedona.core.Point;

/**
 * Krovak (oblique conformal conic) projection.
 * Mirrors: lib/projections/krovak.js
 *
 * <p>Used by the Czech and Slovak national grids, e.g. EPSG:5514 / EPSG:2065
 * (S-JTSK / Krovak). Krovak is always defined on the Bessel 1841 ellipsoid, so the
 * ellipsoid constants are fixed here as in proj4js.</p>
 *
 * <p>Following proj4js, the {@code czech} orientation flag is never set, so output
 * uses the south-west (negated) orientation for every Krovak alias — including the
 * "North Orientated" method names.</p>
 */
public class Krovak implements Projection {

    private static final String[] NAMES = {
        "Krovak", "Krovak Modified", "Krovak (North Orientated)",
        "Krovak Modified (North Orientated)", "krovak"
    };

    private double a, e, e2, lat0, long0, k0, x0, y0;
    private double s45, s0, alfa, k, n0, n, ro0, ad;
    // proj4js never sets the czech (orientation) flag, so it is always false and the
    // south-west negated orientation is used for every Krovak alias. Kept as a field to
    // mirror upstream structure.
    private final boolean czech = false;
    private Boolean over;

    @Override
    public String[] getNames() { return NAMES; }

    @Override
    public void init(ProjectionParams params) {
        // Krovak is always on the Bessel 1841 ellipsoid.
        this.a = 6377397.155;
        this.e2 = 0.006674372230614;
        this.e = Math.sqrt(this.e2);

        this.lat0 = params.getLat0();
        if (this.lat0 == 0) {
            this.lat0 = 0.863937979737193;
        }
        this.long0 = params.getLong0();
        if (this.long0 == 0) {
            this.long0 = 0.7417649320975901 - 0.308341501185665;
        }
        // Persist the resolved central meridian (like UTM's zone-derived long0), so
        // wrappers such as ob_tran can compensate for it.
        params.long0 = this.long0;
        // Krovak's scale factor is 0.9999. proj4js defaults it when +k is omitted;
        // ProjectionParams cannot distinguish "omitted" from "k=1" (both surface as the
        // 1.0 default), and k=1 is not a real Krovak definition, so treat 1.0/0 as unset.
        this.k0 = params.k0;
        if (this.k0 == 0 || this.k0 == 1.0) {
            this.k0 = 0.9999;
        }
        // proj4js's krovak ignores +x_0/+y_0; this codebase applies offsets in the
        // projection (Transform does not), and PROJ/pyproj apply them, so apply them here.
        this.x0 = params.x0;
        this.y0 = params.y0;
        this.over = params.over;

        this.s45 = 0.785398163397448; // 45 degrees
        double s90 = 2 * this.s45;
        double fi0 = this.lat0;
        this.alfa = Math.sqrt(1 + (this.e2 * Math.pow(Math.cos(fi0), 4)) / (1 - this.e2));
        double uq = 1.04216856380474;
        double u0 = Math.asin(Math.sin(fi0) / this.alfa);
        double g = Math.pow((1 + this.e * Math.sin(fi0)) / (1 - this.e * Math.sin(fi0)), this.alfa * this.e / 2);
        this.k = Math.tan(u0 / 2 + this.s45) / Math.pow(Math.tan(fi0 / 2 + this.s45), this.alfa) * g;
        this.n0 = this.a * Math.sqrt(1 - this.e2) / (1 - this.e2 * Math.pow(Math.sin(fi0), 2));
        this.s0 = 1.37008346281555;
        this.n = Math.sin(this.s0);
        this.ro0 = this.k0 * this.n0 / Math.tan(this.s0);
        this.ad = s90 - uq;
    }

    @Override
    public Point forward(Point p) {
        double lat = p.y;
        double deltaLon = ProjMath.adjustLon(p.x - long0, over);

        double gfi = Math.pow((1 + e * Math.sin(lat)) / (1 - e * Math.sin(lat)), alfa * e / 2);
        double u = 2 * (Math.atan(k * Math.pow(Math.tan(lat / 2 + s45), alfa) / gfi) - s45);
        double deltav = -deltaLon * alfa;
        double s = Math.asin(Math.cos(ad) * Math.sin(u) + Math.sin(ad) * Math.cos(u) * Math.cos(deltav));
        double d = Math.asin(Math.cos(u) * Math.sin(deltav) / Math.cos(s));
        double eps = n * d;
        double ro = ro0 * Math.pow(Math.tan(s0 / 2 + s45), n) / Math.pow(Math.tan(s / 2 + s45), n);

        double y = ro * Math.cos(eps);
        double x = ro * Math.sin(eps);

        if (!czech) {
            y *= -1;
            x *= -1;
        }
        return new Point(x + x0, y + y0, p.z);
    }

    @Override
    public Point inverse(Point p) {
        // Remove false easting/northing, then revert x, y (as proj4js does).
        double px = (p.y - y0);
        double py = (p.x - x0);
        if (!czech) {
            py *= -1;
            px *= -1;
        }

        double ro = Math.sqrt(px * px + py * py);
        double eps = Math.atan2(py, px);
        double d = eps / Math.sin(s0);
        double s = 2 * (Math.atan(Math.pow(ro0 / ro, 1 / n) * Math.tan(s0 / 2 + s45)) - s45);
        double u = Math.asin(Math.cos(ad) * Math.sin(s) - Math.sin(ad) * Math.cos(s) * Math.cos(d));
        double deltav = Math.asin(Math.cos(s) * Math.sin(d) / Math.cos(u));
        double lon = long0 - deltav / alfa;

        double fi1 = u;
        double lat = 0;
        boolean ok = false;
        int iter = 0;
        do {
            lat = 2 * (Math.atan(Math.pow(k, -1 / alfa) * Math.pow(Math.tan(u / 2 + s45), 1 / alfa)
                * Math.pow((1 + e * Math.sin(fi1)) / (1 - e * Math.sin(fi1)), e / 2)) - s45);
            if (Math.abs(fi1 - lat) < 0.0000000001) {
                ok = true;
            }
            fi1 = lat;
            iter += 1;
        } while (!ok && iter < 15);
        if (iter >= 15) {
            return null;
        }

        return new Point(lon, lat, p.z);
    }
}
