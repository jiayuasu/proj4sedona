package org.datasyslab.proj4sedona.projection;

import org.datasyslab.proj4sedona.common.ProjMath;
import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.Point;

/**
 * Bonne (pseudoconic, equal-area) projection.
 * Mirrors: lib/projections/bonne.js
 *
 * <p>Parallels are concentric circular arcs; true to scale along the central meridian
 * and every parallel. Supports both the spherical and ellipsoidal forms.</p>
 */
public class Bonne implements Projection {

    private static final String[] NAMES = {"bonne", "Bonne (Werner lat_1=90)"};

    private static final double EPS10 = 1e-10;

    private double a, es, phi1, long0, x0, y0;
    private double[] en;
    private double m1, am1, cphi1;
    private boolean sphere;
    private Boolean over;

    @Override
    public String[] getNames() { return NAMES; }

    @Override
    public void init(ProjectionParams params) {
        this.a = params.a;
        this.es = params.es;
        this.long0 = params.getLong0();
        this.x0 = params.x0;
        this.y0 = params.y0;
        this.over = params.over;

        this.phi1 = params.getLat1();
        if (Math.abs(phi1) < EPS10) {
            throw new IllegalArgumentException("Bonne requires a non-zero standard parallel (+lat_1)");
        }

        this.sphere = params.sphere || es == 0;
        if (!sphere) {
            this.en = ProjMath.pjEnfn(es);
            double sinPhi1 = Math.sin(phi1);
            double cosPhi1 = Math.cos(phi1);
            this.m1 = ProjMath.pjMlfn(phi1, sinPhi1, cosPhi1, en);
            this.am1 = cosPhi1 / (Math.sqrt(1 - es * sinPhi1 * sinPhi1) * sinPhi1);
        } else {
            if (Math.abs(phi1) + EPS10 >= Values.HALF_PI) {
                this.cphi1 = 0;
            } else {
                this.cphi1 = 1 / Math.tan(phi1);
            }
        }
    }

    @Override
    public Point forward(Point p) {
        double lam = ProjMath.adjustLon(p.x - long0, over);
        double phi = p.y;
        double x, y;

        if (!sphere) {
            double sinPhi = Math.sin(phi);
            double cosPhi = Math.cos(phi);
            double rh = am1 + m1 - ProjMath.pjMlfn(phi, sinPhi, cosPhi, en);
            double e = cosPhi * lam / (rh * Math.sqrt(1 - es * sinPhi * sinPhi));
            x = rh * Math.sin(e);
            y = am1 - rh * Math.cos(e);
        } else {
            double rh = cphi1 + phi1 - phi;
            if (Math.abs(rh) > EPS10) {
                double e = lam * Math.cos(phi) / rh;
                x = rh * Math.sin(e);
                y = cphi1 - rh * Math.cos(e);
            } else {
                x = 0;
                y = 0;
            }
        }

        return new Point(a * x + x0, a * y + y0, p.z);
    }

    @Override
    public Point inverse(Point p) {
        double px = (p.x - x0) / a;
        double py = (p.y - y0) / a;
        double lam, phi;

        if (!sphere) {
            py = am1 - py;
            double rh = Math.hypot(px, py);
            phi = ProjMath.pjInvMlfn(am1 + m1 - rh, es, en);
            double s = Math.abs(phi);
            if (s < Values.HALF_PI) {
                s = Math.sin(phi);
                lam = rh * Math.atan2(px, py) * Math.sqrt(1 - es * s * s) / Math.cos(phi);
            } else if (Math.abs(s - Values.HALF_PI) <= EPS10) {
                lam = 0;
            } else {
                return null;
            }
        } else {
            py = cphi1 - py;
            double rh = Math.hypot(px, py);
            phi = cphi1 + phi1 - rh;
            if (Math.abs(phi) > Values.HALF_PI) {
                return null;
            }
            if (Math.abs(Math.abs(phi) - Values.HALF_PI) <= EPS10) {
                lam = 0;
            } else {
                lam = rh * Math.atan2(px, py) / Math.cos(phi);
            }
        }

        return new Point(ProjMath.adjustLon(lam + long0, over), ProjMath.adjustLat(phi), p.z);
    }
}
