package org.datasyslab.proj4sedona.projection;

import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.Point;

/**
 * Tilted Perspective projection (tpers).
 * Mirrors: lib/projections/tpers.js
 *
 * <p>The view of the Earth from a camera at height {@code +h}, optionally tilted
 * ({@code +tilt}, from nadir) and rotated ({@code +azi}, from north) — a
 * generalization of the near-sided perspective. Spherical only (computed on a
 * sphere of radius {@code a}); defaults follow proj4js: h = 100 km (Kármán line),
 * azi = 0 (north), tilt = 0 (nadir).</p>
 *
 * <p>Intentional divergences from proj4js, both matching PROJ and this codebase's
 * conventions: {@code +x_0/+y_0} are applied (proj4js ignores them; PROJ applies
 * them), and points not visible from the camera are unprojectable ({@code null};
 * proj4js returns finite garbage where PROJ returns inf).</p>
 */
public class TiltedPerspective implements Projection {

    private static final String[] NAMES = {"Tilted_Perspective", "tpers"};

    private static final int N_POLE = 0;
    private static final int S_POLE = 1;
    private static final int EQUIT = 2;
    private static final int OBLIQ = 3;

    private double a, lat0, long0, x0, y0;
    private int mode;
    private double sinph0, cosph0;
    private double pn1, p, rp, h1, pfact;
    private double cg, sg, cw, sw;

    @Override
    public String[] getNames() { return NAMES; }

    @Override
    public void init(ProjectionParams params) {
        this.a = params.a;
        this.lat0 = params.getLat0();
        this.long0 = params.getLong0();
        this.x0 = params.x0;
        this.y0 = params.y0;
        // Defaults per proj4js: h = Kármán line, azi = north, tilt = nadir.
        // tilt/azi are parsed to radians by ProjString, matching the other angles.
        double h = params.h != null ? params.h : 100000;
        double tilt = params.tilt != null ? params.tilt : 0;
        double azi = params.azi != null ? params.azi : 0;

        if (Math.abs(Math.abs(lat0) - Values.HALF_PI) < Values.EPSLN) {
            this.mode = lat0 < 0 ? S_POLE : N_POLE;
        } else if (Math.abs(lat0) < Values.EPSLN) {
            this.mode = EQUIT;
        } else {
            this.mode = OBLIQ;
            this.sinph0 = Math.sin(lat0);
            this.cosph0 = Math.cos(lat0);
        }

        this.pn1 = h / a; // Normalize relative to the Earth's radius
        if (pn1 <= 0 || pn1 > 1e10) {
            throw new IllegalArgumentException("Invalid height (+h) for Tilted Perspective projection");
        }

        this.p = 1 + pn1;
        this.rp = 1 / p;
        this.h1 = 1 / pn1;
        this.pfact = (p + 1) * h1;

        this.cg = Math.cos(azi);
        this.sg = Math.sin(azi);
        this.cw = Math.cos(tilt);
        this.sw = Math.sin(tilt);
    }

    @Override
    public Point forward(Point pt) {
        double lam = pt.x - long0;
        double sinphi = Math.sin(pt.y);
        double cosphi = Math.cos(pt.y);
        double coslam = Math.cos(lam);
        double x, y;

        switch (mode) {
            case OBLIQ: y = sinph0 * sinphi + cosph0 * cosphi * coslam; break;
            case EQUIT: y = cosphi * coslam; break;
            case S_POLE: y = -sinphi; break;
            default: y = sinphi; break; // N_POLE
        }
        // Visibility: y is the cosine of the angular distance from the sub-camera
        // point; points beyond the horizon (cos < 1/p) are not visible. PROJ returns
        // inf here; proj4js returns finite garbage — return null instead.
        if (y < rp) {
            return null;
        }
        y = pn1 / (p - y);
        x = y * cosphi * Math.sin(lam);

        switch (mode) {
            case OBLIQ: y *= cosph0 * sinphi - sinph0 * cosphi * coslam; break;
            case EQUIT: y *= sinphi; break;
            case N_POLE: y *= -(cosphi * coslam); break;
            default: y *= cosphi * coslam; break; // S_POLE
        }

        // Tilt
        double yt = y * cg + x * sg;
        double ba = 1 / (yt * sw * h1 + cw);
        x = (x * cg - y * sg) * cw * ba;
        y = yt * ba;

        return new Point(x * a + x0, y * a + y0, pt.z);
    }

    @Override
    public Point inverse(Point pt) {
        double px = (pt.x - x0) / a;
        double py = (pt.y - y0) / a;
        double rx, ry;

        // Un-Tilt
        double yt = 1 / (pn1 - py * sw);
        double bm = pn1 * px * yt;
        double bq = pn1 * py * cw * yt;
        px = bm * cg + bq * sg;
        py = bq * cg - bm * sg;

        double rh = Math.hypot(px, py);
        if (Math.abs(rh) < Values.EPSLN) {
            rx = 0;
            ry = py;
        } else {
            double sinz = 1 - rh * rh * pfact;
            if (sinz < 0) {
                // Outside the projectable disk: not invertible (proj4js yields NaN).
                return null;
            }
            sinz = (p - Math.sqrt(sinz)) / (pn1 / rh + rh / pn1);
            if (Math.abs(sinz) > 1) {
                return null;
            }
            double cosz = Math.sqrt(1 - sinz * sinz);
            switch (mode) {
                case OBLIQ:
                    ry = Math.asin(cosz * sinph0 + py * sinz * cosph0 / rh);
                    py = (cosz - sinph0 * Math.sin(ry)) * rh;
                    px *= sinz * cosph0;
                    break;
                case EQUIT:
                    ry = Math.asin(py * sinz / rh);
                    py = cosz * rh;
                    px *= sinz;
                    break;
                case N_POLE:
                    ry = Math.asin(cosz);
                    py = -py;
                    break;
                default: // S_POLE
                    ry = -Math.asin(cosz);
                    break;
            }
            rx = Math.atan2(px, py);
        }

        return new Point(rx + long0, ry, pt.z);
    }
}
