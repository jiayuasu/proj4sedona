package org.datasyslab.proj4sedona.projection;

import java.util.Arrays;
import java.util.List;

import org.datasyslab.proj4sedona.common.ProjMath;
import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.Point;

/**
 * Hotine Oblique Mercator projection (omerc).
 * Mirrors: lib/projections/omerc.js
 *
 * <p>The conformal cylindrical projection with an arbitrary oblique central line,
 * used by grids such as EPSG:3375 (RSO Malaysia), Alaska zone 1, and the Swiss
 * grids' WKT form. Supports both parameterizations: azimuth/rectified-grid-angle
 * (variants A and B) and the two-point form. Variant A ({@code +no_uoff} / the
 * "natural origin" WKT method names) omits the origin offset.</p>
 */
public class ObliqueMercator implements Projection {

    private static final String[] NAMES = {
        "Hotine_Oblique_Mercator", "Hotine Oblique Mercator",
        "Hotine_Oblique_Mercator_variant_A", "Hotine_Oblique_Mercator_Variant_B",
        "Hotine_Oblique_Mercator_Azimuth_Natural_Origin",
        "Hotine_Oblique_Mercator_Two_Point_Natural_Origin",
        "Hotine_Oblique_Mercator_Azimuth_Center", "Oblique_Mercator", "omerc"
    };

    // Projection names that use variant A (no origin offset).
    private static final List<String> TYPE_A_PROJECTIONS = Arrays.asList(
        "Hotine_Oblique_Mercator", "Hotine_Oblique_Mercator_variant_A",
        "Hotine_Oblique_Mercator_Azimuth_Natural_Origin");

    private static final double TOL = 1e-7;

    private double a, e, es, k0, lat0, x0, y0;
    private boolean noOff, noRot;
    private double A, B, E, lam0, singam, cosgam, sinrot, cosrot, rB, ArB, BrA, u0, vPoleN, vPoleS;
    private Boolean over;

    @Override
    public String[] getNames() { return NAMES; }

    /** Mirrors getNormalizedProjName: collapse "-", "(", ")", whitespace to single "_". */
    private static String normalizeProjName(String n) {
        return n.replaceAll("[-()\\s]+", " ").trim().replace(' ', '_');
    }

    private boolean isTypeA(ProjectionParams params) {
        if (Boolean.TRUE.equals(params.noUoff)) {
            return true;
        }
        String projName = params.projName;
        if (projName == null) {
            return false;
        }
        return TYPE_A_PROJECTIONS.contains(projName)
            || TYPE_A_PROJECTIONS.contains(normalizeProjName(projName));
    }

    @Override
    public void init(ProjectionParams params) {
        this.a = params.a;
        this.es = params.es;
        this.e = Math.sqrt(es);
        this.k0 = params.k0;
        this.lat0 = params.getLat0();
        this.x0 = params.x0;
        this.y0 = params.y0;
        this.over = params.over;

        double con, com, cosph0, D, F, H, L, sinph0, p, J;
        double gamma = 0, gamma0, lamc = 0, lam1 = 0, lam2 = 0, phi1 = 0, phi2 = 0, alphaC = 0;

        // only Type A uses the no_off / no_uoff property
        this.noOff = isTypeA(params);
        this.noRot = Boolean.TRUE.equals(params.noRot);

        boolean alp = params.alpha != null;
        boolean gam = params.rectifiedGridAngle != null;

        if (alp) {
            alphaC = params.alpha;
        }
        if (gam) {
            gamma = params.rectifiedGridAngle;
            if (!alp) {
                alphaC = 0;
                alp = true;
            }
        }

        if (alp || gam) {
            lamc = params.longc;
        } else {
            if (params.long1 == null || params.lat1 == null
                || params.long2 == null || params.lat2 == null) {
                throw new IllegalArgumentException(
                    "Oblique Mercator requires either alpha (+ optional gamma) or the "
                        + "two-point parameters lon_1/lat_1/lon_2/lat_2");
            }
            lam1 = params.long1;
            phi1 = params.lat1;
            lam2 = params.long2;
            phi2 = params.lat2;

            if (Math.abs(phi1 - phi2) <= TOL || (con = Math.abs(phi1)) <= TOL
                || Math.abs(con - Values.HALF_PI) <= TOL
                || Math.abs(Math.abs(lat0) - Values.HALF_PI) <= TOL
                || Math.abs(Math.abs(phi2) - Values.HALF_PI) <= TOL) {
                throw new IllegalArgumentException("Invalid two-point Oblique Mercator parameters");
            }
        }

        double oneEs = 1.0 - es;
        com = Math.sqrt(oneEs);

        if (Math.abs(lat0) > Values.EPSLN) {
            sinph0 = Math.sin(lat0);
            cosph0 = Math.cos(lat0);
            con = 1 - es * sinph0 * sinph0;
            this.B = cosph0 * cosph0;
            this.B = Math.sqrt(1 + es * this.B * this.B / oneEs);
            this.A = this.B * k0 * com / con;
            D = this.B * com / (cosph0 * Math.sqrt(con));
            F = D * D - 1;

            if (F <= 0) {
                F = 0;
            } else {
                F = Math.sqrt(F);
                if (lat0 < 0) {
                    F = -F;
                }
            }

            this.E = F += D;
            this.E *= Math.pow(ProjMath.tsfnz(e, lat0, sinph0), this.B);
        } else {
            this.B = 1 / com;
            this.A = k0;
            this.E = D = F = 1;
        }

        if (alp || gam) {
            if (alp) {
                gamma0 = Math.asin(Math.sin(alphaC) / D);
                if (!gam) {
                    gamma = alphaC;
                }
            } else {
                gamma0 = gamma;
                alphaC = Math.asin(D * Math.sin(gamma0));
            }
            this.lam0 = lamc - Math.asin(0.5 * (F - 1 / F) * Math.tan(gamma0)) / this.B;
        } else {
            H = Math.pow(ProjMath.tsfnz(e, phi1, Math.sin(phi1)), this.B);
            L = Math.pow(ProjMath.tsfnz(e, phi2, Math.sin(phi2)), this.B);
            F = this.E / H;
            p = (L - H) / (L + H);
            J = this.E * this.E;
            J = (J - L * H) / (J + L * H);
            con = lam1 - lam2;

            if (con < -Math.PI) {
                lam2 -= Values.TWO_PI;
            } else if (con > Math.PI) {
                lam2 += Values.TWO_PI;
            }

            this.lam0 = ProjMath.adjustLon(
                0.5 * (lam1 + lam2) - Math.atan(J * Math.tan(0.5 * this.B * (lam1 - lam2)) / p) / this.B, over);
            gamma0 = Math.atan(2 * Math.sin(this.B * ProjMath.adjustLon(lam1 - this.lam0, over)) / (F - 1 / F));
            gamma = alphaC = Math.asin(D * Math.sin(gamma0));
        }

        this.singam = Math.sin(gamma0);
        this.cosgam = Math.cos(gamma0);
        this.sinrot = Math.sin(gamma);
        this.cosrot = Math.cos(gamma);

        this.rB = 1 / this.B;
        this.ArB = this.A * this.rB;
        this.BrA = 1 / this.ArB;

        if (this.noOff) {
            this.u0 = 0;
        } else {
            this.u0 = Math.abs(this.ArB * Math.atan(Math.sqrt(D * D - 1) / Math.cos(alphaC)));
            if (lat0 < 0) {
                this.u0 = -this.u0;
            }
        }

        F = 0.5 * gamma0;
        this.vPoleN = this.ArB * Math.log(Math.tan(Values.FORTPI - F));
        this.vPoleS = this.ArB * Math.log(Math.tan(Values.FORTPI + F));
    }

    @Override
    public Point forward(Point pt) {
        double px = pt.x - this.lam0;
        double py = pt.y;
        double S, T, U, V, temp, u, v;

        if (Math.abs(Math.abs(py) - Values.HALF_PI) > Values.EPSLN) {
            double W = this.E / Math.pow(ProjMath.tsfnz(e, py, Math.sin(py)), this.B);

            temp = 1 / W;
            S = 0.5 * (W - temp);
            T = 0.5 * (W + temp);
            V = Math.sin(this.B * px);
            U = (S * this.singam - V * this.cosgam) / T;

            if (Math.abs(Math.abs(U) - 1.0) < Values.EPSLN) {
                return null;
            }

            v = 0.5 * this.ArB * Math.log((1 - U) / (1 + U));
            temp = Math.cos(this.B * px);

            if (Math.abs(temp) < TOL) {
                u = this.A * px;
            } else {
                u = this.ArB * Math.atan2((S * this.cosgam + V * this.singam), temp);
            }
        } else {
            v = py > 0 ? this.vPoleN : this.vPoleS;
            u = this.ArB * py;
        }

        double cx, cy;
        if (this.noRot) {
            cx = u;
            cy = v;
        } else {
            u -= this.u0;
            cx = v * this.cosrot + u * this.sinrot;
            cy = u * this.cosrot - v * this.sinrot;
        }

        return new Point(this.a * cx + this.x0, this.a * cy + this.y0, pt.z);
    }

    @Override
    public Point inverse(Point pt) {
        double px = (pt.x - this.x0) * (1.0 / this.a);
        double py = (pt.y - this.y0) * (1.0 / this.a);
        double u, v;

        if (this.noRot) {
            v = py;
            u = px;
        } else {
            v = px * this.cosrot - py * this.sinrot;
            u = py * this.cosrot + px * this.sinrot + this.u0;
        }

        double Qp = Math.exp(-this.BrA * v);
        double Sp = 0.5 * (Qp - 1 / Qp);
        double Tp = 0.5 * (Qp + 1 / Qp);
        double Vp = Math.sin(this.BrA * u);
        double Up = (Vp * this.cosgam + Sp * this.singam) / Tp;

        double cx, cy;
        if (Math.abs(Math.abs(Up) - 1) < Values.EPSLN) {
            cx = 0;
            cy = Up < 0 ? -Values.HALF_PI : Values.HALF_PI;
        } else {
            cy = this.E / Math.sqrt((1 + Up) / (1 - Up));
            cy = ProjMath.phi2z(e, Math.pow(cy, 1 / this.B));
            if (Double.isInfinite(cy)) {
                return null;
            }
            cx = -this.rB * Math.atan2((Sp * this.cosgam - Vp * this.singam), Math.cos(this.BrA * u));
        }

        return new Point(cx + this.lam0, cy, pt.z);
    }
}
