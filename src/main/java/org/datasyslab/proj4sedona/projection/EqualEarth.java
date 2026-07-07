package org.datasyslab.proj4sedona.projection;

import org.datasyslab.proj4sedona.common.ProjMath;
import org.datasyslab.proj4sedona.core.Point;

/**
 * Equal Earth projection (eqearth).
 * Mirrors: lib/projections/eqearth.js
 *
 * <p>An equal-area pseudocylindrical projection (Savric, Patterson &amp; Jenny, 2018)
 * inspired by Robinson but preserving areas. Supports the spherical and ellipsoidal
 * (authalic) forms.</p>
 */
public class EqualEarth implements Projection {

    private static final String[] NAMES = {"eqearth", "Equal Earth", "Equal_Earth"};

    private static final double A1 = 1.340264;
    private static final double A2 = -0.081106;
    private static final double A3 = 0.000893;
    private static final double A4 = 0.003796;
    private static final double M = Math.sqrt(3) / 2.0;

    private double a, es, e, long0, x0, y0;
    private double[] apa;
    private double qp, rqda;
    private Boolean over;

    @Override
    public String[] getNames() { return NAMES; }

    @Override
    public void init(ProjectionParams params) {
        this.a = params.a;
        this.es = params.es;
        this.e = Math.sqrt(es);
        this.long0 = params.getLong0();
        this.x0 = params.x0;
        this.y0 = params.y0;
        this.over = params.over;

        if (es != 0) {
            this.apa = ProjMath.authset(es);
            this.qp = ProjMath.qsfnz(e, 1);
            this.rqda = Math.sqrt(0.5 * qp);
        }
    }

    @Override
    public Point forward(Point p) {
        double lam = ProjMath.adjustLon(p.x - long0, over);
        double sinphi = Math.sin(p.y);
        if (es != 0) {
            sinphi = ProjMath.qsfnz(e, sinphi) / qp;
        }
        double paramLat = Math.asin(M * sinphi);
        double paramLatSq = paramLat * paramLat;
        double paramLatPow6 = paramLatSq * paramLatSq * paramLatSq;
        double x = lam * Math.cos(paramLat)
            / (M * (A1 + 3 * A2 * paramLatSq + paramLatPow6 * (7 * A3 + 9 * A4 * paramLatSq)));
        double y = paramLat * (A1 + A2 * paramLatSq + paramLatPow6 * (A3 + A4 * paramLatSq));

        if (es != 0) {
            x *= rqda;
            y *= rqda;
        }

        return new Point(a * x + x0, a * y + y0, p.z);
    }

    @Override
    public Point inverse(Point p) {
        double px = (p.x - x0) / a;
        double py = (p.y - y0) / a;
        if (es != 0) {
            px /= rqda;
            py /= rqda;
        }

        double EPS = 1e-9;
        int NITER = 12;
        double paramLat = py;
        double paramLatSq, paramLatPow6, fy, fpy, dlat;
        for (int i = 0; i < NITER; ++i) {
            paramLatSq = paramLat * paramLat;
            paramLatPow6 = paramLatSq * paramLatSq * paramLatSq;
            fy = paramLat * (A1 + A2 * paramLatSq + paramLatPow6 * (A3 + A4 * paramLatSq)) - py;
            fpy = A1 + 3 * A2 * paramLatSq + paramLatPow6 * (7 * A3 + 9 * A4 * paramLatSq);
            dlat = fy / fpy;
            paramLat -= dlat;
            if (Math.abs(dlat) < EPS) {
                break;
            }
        }
        paramLatSq = paramLat * paramLat;
        paramLatPow6 = paramLatSq * paramLatSq * paramLatSq;
        double lon = M * px * (A1 + 3 * A2 * paramLatSq + paramLatPow6 * (7 * A3 + 9 * A4 * paramLatSq))
            / Math.cos(paramLat);
        double lat = Math.asin(Math.sin(paramLat) / M);

        if (es != 0) {
            lat = ProjMath.authlat(lat, apa);
        }

        return new Point(ProjMath.adjustLon(lon + long0, over), lat, p.z);
    }
}
