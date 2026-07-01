package org.datasyslab.proj4sedona.projection;

import org.datasyslab.proj4sedona.common.ProjMath;
import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.Point;

/**
 * Cassini-Soldner projection (cass).
 * Mirrors: lib/projections/cass.js
 *
 * <p>A transverse cylindrical projection, true to scale along the central meridian
 * and lines perpendicular to it. Used by a number of older national and cadastral
 * grids, e.g. EPSG:2066 (Mount Dillon / Tobago Grid).</p>
 */
public class CassiniSoldner implements Projection {

    private static final String[] NAMES = {"Cassini", "Cassini_Soldner", "cass"};

    private double a, e, es, lat0, long0, x0, y0;
    private double e0, e1, e2, e3, ml0;
    private boolean sphere;
    private Boolean over;

    @Override
    public String[] getNames() { return NAMES; }

    @Override
    public void init(ProjectionParams params) {
        this.a = params.a;
        this.es = params.es;
        this.e = Math.sqrt(es);
        this.lat0 = params.getLat0();
        this.long0 = params.getLong0();
        this.x0 = params.x0;
        this.y0 = params.y0;
        this.sphere = params.sphere;
        this.over = params.over;

        if (!sphere) {
            this.e0 = ProjMath.e0fn(es);
            this.e1 = ProjMath.e1fn(es);
            this.e2 = ProjMath.e2fn(es);
            this.e3 = ProjMath.e3fn(es);
            this.ml0 = a * ProjMath.mlfn(e0, e1, e2, e3, lat0);
        }
    }

    @Override
    public Point forward(Point p) {
        double lam = ProjMath.adjustLon(p.x - long0, over);
        double phi = p.y;
        double x, y;

        if (sphere) {
            x = a * Math.asin(Math.cos(phi) * Math.sin(lam));
            y = a * (Math.atan2(Math.tan(phi), Math.cos(lam)) - lat0);
        } else {
            double sinphi = Math.sin(phi);
            double cosphi = Math.cos(phi);
            double nl = ProjMath.gN(a, e, sinphi);
            double tl = Math.tan(phi) * Math.tan(phi);
            double al = lam * Math.cos(phi);
            double asq = al * al;
            double cl = es * cosphi * cosphi / (1 - es);
            double ml = a * ProjMath.mlfn(e0, e1, e2, e3, phi);

            x = nl * al * (1 - asq * tl * (1.0 / 6 - (8 - tl + 8 * cl) * asq / 120));
            y = ml - ml0 + nl * sinphi / cosphi * asq * (0.5 + (5 - tl + 6 * cl) * asq / 24);
        }

        return new Point(x + x0, y + y0, p.z);
    }

    @Override
    public Point inverse(Point p) {
        double x = (p.x - x0) / a;
        double y = (p.y - y0) / a;
        double phi, lam;

        if (sphere) {
            double dd = y + lat0;
            phi = Math.asin(Math.sin(dd) * Math.cos(x));
            lam = Math.atan2(Math.tan(x), Math.cos(dd));
        } else {
            double ml1 = ml0 / a + y;
            double phi1 = ProjMath.imlfn(ml1, e0, e1, e2, e3);
            if (Math.abs(Math.abs(phi1) - Values.HALF_PI) <= Values.EPSLN) {
                double yPole = (y < 0) ? -Values.HALF_PI : Values.HALF_PI;
                return new Point(long0, yPole, p.z);
            }
            double nl1 = ProjMath.gN(a, e, Math.sin(phi1));

            double rl1 = nl1 * nl1 * nl1 / a / a * (1 - es);
            double tanPhi1 = Math.tan(phi1);
            double tl1 = tanPhi1 * tanPhi1;
            double dl = x * a / nl1;
            double dsq = dl * dl;
            phi = phi1 - nl1 * tanPhi1 / rl1 * dl * dl * (0.5 - (1 + 3 * tl1) * dl * dl / 24);
            lam = dl * (1 - dsq * (tl1 / 3 + (1 + 3 * tl1) * tl1 * dsq / 15)) / Math.cos(phi1);
        }

        return new Point(ProjMath.adjustLon(lam + long0, over), ProjMath.adjustLat(phi), p.z);
    }
}
