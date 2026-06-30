package org.datasyslab.proj4sedona.projection;

import org.datasyslab.proj4sedona.common.ProjMath;
import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.Point;

/**
 * (American) Polyconic projection implementation.
 * Mirrors: lib/projections/poly.js
 *
 * <p>A projection in which the parallels are arcs of circles whose radius equals
 * the cotangent of the latitude. Each parallel has its own cone of projection, so
 * scale is true along the central meridian and along each parallel. Used by several
 * national grids, e.g. EPSG:5880 (SIRGAS 2000 / Brazil Polyconic).</p>
 */
public class Polyconic implements Projection {

    private static final String[] NAMES = {"Polyconic", "American_Polyconic", "poly"};

    private static final int MAX_ITER = 20;

    private double a, b, es, e, lat0, long0, x0, y0;
    private double e0, e1, e2, e3, ml0;
    private boolean sphere;
    private Boolean over;

    @Override
    public String[] getNames() { return NAMES; }

    @Override
    public void init(ProjectionParams params) {
        this.a = params.a;
        this.b = params.b;
        this.lat0 = params.getLat0();
        this.long0 = params.getLong0();
        this.x0 = params.x0;
        this.y0 = params.y0;
        this.sphere = params.sphere;
        this.over = params.over;

        // Mirror poly.js: recompute es/e from the axes so the projection is
        // self-consistent even when es was not propagated.
        double temp = this.b / this.a;
        this.es = 1 - temp * temp;
        this.e = Math.sqrt(this.es);
        this.e0 = ProjMath.e0fn(this.es);
        this.e1 = ProjMath.e1fn(this.es);
        this.e2 = ProjMath.e2fn(this.es);
        this.e3 = ProjMath.e3fn(this.es);
        this.ml0 = this.a * ProjMath.mlfn(this.e0, this.e1, this.e2, this.e3, this.lat0);
    }

    @Override
    public Point forward(Point p) {
        double lon = p.x;
        double lat = p.y;
        double x, y;

        double dlon = ProjMath.adjustLon(lon - long0, over);
        double el = dlon * Math.sin(lat);
        if (sphere) {
            if (Math.abs(lat) <= Values.EPSLN) {
                x = a * dlon;
                y = -1 * a * lat0;
            } else {
                x = a * Math.sin(el) / Math.tan(lat);
                y = a * (ProjMath.adjustLat(lat - lat0) + (1 - Math.cos(el)) / Math.tan(lat));
            }
        } else {
            if (Math.abs(lat) <= Values.EPSLN) {
                x = a * dlon;
                y = -1 * ml0;
            } else {
                double nl = ProjMath.gN(a, e, Math.sin(lat)) / Math.tan(lat);
                x = nl * Math.sin(el);
                y = a * ProjMath.mlfn(e0, e1, e2, e3, lat) - ml0 + nl * (1 - Math.cos(el));
            }
        }

        return new Point(x + x0, y + y0, p.z);
    }

    @Override
    public Point inverse(Point p) {
        double lon, lat = 0;
        double al, bl, phi, dphi;
        double x = p.x - x0;
        double y = p.y - y0;

        if (sphere) {
            if (Math.abs(y + a * lat0) <= Values.EPSLN) {
                lon = ProjMath.adjustLon(x / a + long0, over);
                lat = 0;
            } else {
                al = lat0 + y / a;
                bl = x * x / a / a + al * al;
                phi = al;
                double tanphi;
                for (int i = MAX_ITER; i != 0; --i) {
                    tanphi = Math.tan(phi);
                    dphi = -1 * (al * (phi * tanphi + 1) - phi - 0.5 * (phi * phi + bl) * tanphi)
                            / ((phi - al) / tanphi - 1);
                    phi += dphi;
                    if (Math.abs(dphi) <= Values.EPSLN) {
                        lat = phi;
                        break;
                    }
                }
                lon = ProjMath.adjustLon(long0 + (Math.asin(x * Math.tan(phi) / a)) / Math.sin(lat), over);
            }
        } else {
            if (Math.abs(y + ml0) <= Values.EPSLN) {
                lat = 0;
                lon = ProjMath.adjustLon(long0 + x / a, over);
            } else {
                al = (ml0 + y) / a;
                bl = x * x / a / a + al * al;
                phi = al;
                double cl = 0, mln, mlnp, ma, con;
                for (int i = MAX_ITER; i != 0; --i) {
                    con = e * Math.sin(phi);
                    cl = Math.sqrt(1 - con * con) * Math.tan(phi);
                    mln = a * ProjMath.mlfn(e0, e1, e2, e3, phi);
                    mlnp = e0 - 2 * e1 * Math.cos(2 * phi) + 4 * e2 * Math.cos(4 * phi)
                            - 6 * e3 * Math.cos(6 * phi);
                    ma = mln / a;
                    dphi = (al * (cl * ma + 1) - ma - 0.5 * cl * (ma * ma + bl))
                            / (es * Math.sin(2 * phi) * (ma * ma + bl - 2 * al * ma) / (4 * cl)
                                + (al - ma) * (cl * mlnp - 2 / Math.sin(2 * phi)) - mlnp);
                    phi -= dphi;
                    if (Math.abs(dphi) <= Values.EPSLN) {
                        lat = phi;
                        break;
                    }
                }

                cl = Math.sqrt(1 - es * Math.pow(Math.sin(lat), 2)) * Math.tan(lat);
                lon = ProjMath.adjustLon(long0 + Math.asin(x * cl / a) / Math.sin(lat), over);
            }
        }

        return new Point(lon, lat, p.z);
    }
}
