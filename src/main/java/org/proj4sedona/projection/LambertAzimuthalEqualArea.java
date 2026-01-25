package org.proj4sedona.projection;

import org.proj4sedona.common.ProjMath;
import org.proj4sedona.constants.Values;
import org.proj4sedona.core.Point;

/**
 * Lambert Azimuthal Equal Area projection implementation.
 * Mirrors: lib/projections/laea.js
 * 
 * <p>An azimuthal projection that preserves area. Commonly used for continental
 * and hemisphere maps where accurate area representation is important.</p>
 */
public class LambertAzimuthalEqualArea implements Projection {

    private static final String[] NAMES = {
        "Lambert Azimuthal Equal Area", "Lambert_Azimuthal_Equal_Area", "laea"
    };

    private static final int S_POLE = 1, N_POLE = 2, EQUIT = 3, OBLIQ = 4;

    // Authalic latitude coefficients
    private static final double P00 = 0.33333333333333333333;
    private static final double P01 = 0.17222222222222222222;
    private static final double P02 = 0.10257936507936507936;
    private static final double P10 = 0.06388888888888888888;
    private static final double P11 = 0.06640211640211640211;
    private static final double P20 = 0.01641501294219154443;

    private double a, e, es, lat0, long0, x0, y0;
    private int mode;
    private double[] apa;
    private double dd, rq, qp, sinb1, cosb1, ymf, xmf, sinph0, cosph0;
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

        double t = Math.abs(lat0);
        if (Math.abs(t - Values.HALF_PI) < Values.EPSLN) {
            mode = (lat0 < 0) ? S_POLE : N_POLE;
        } else if (Math.abs(t) < Values.EPSLN) {
            mode = EQUIT;
        } else {
            mode = OBLIQ;
        }

        if (es > 0) {
            qp = ProjMath.qsfnz(e, 1);
            apa = authset(es);

            switch (mode) {
                case N_POLE:
                case S_POLE:
                    dd = 1;
                    break;
                case EQUIT:
                    rq = Math.sqrt(0.5 * qp);
                    dd = 1 / rq;
                    xmf = 1;
                    ymf = 0.5 * qp;
                    break;
                case OBLIQ:
                    rq = Math.sqrt(0.5 * qp);
                    double sinphi = Math.sin(lat0);
                    sinb1 = ProjMath.qsfnz(e, sinphi) / qp;
                    cosb1 = Math.sqrt(1 - sinb1 * sinb1);
                    dd = Math.cos(lat0) / (Math.sqrt(1 - es * sinphi * sinphi) * rq * cosb1);
                    ymf = (xmf = rq) / dd;
                    xmf *= dd;
                    break;
            }
        } else {
            if (mode == OBLIQ) {
                sinph0 = Math.sin(lat0);
                cosph0 = Math.cos(lat0);
            }
        }
    }

    private double[] authset(double es) {
        double[] APA = new double[3];
        APA[0] = es * P00;
        double t = es * es;
        APA[0] += t * P01;
        APA[1] = t * P10;
        t *= es;
        APA[0] += t * P02;
        APA[1] += t * P11;
        APA[2] = t * P20;
        return APA;
    }

    private double authlat(double beta, double[] APA) {
        double t = beta + beta;
        return beta + APA[0] * Math.sin(t) + APA[1] * Math.sin(t + t) + APA[2] * Math.sin(t + t + t);
    }

    @Override
    public Point forward(Point p) {
        double lam = ProjMath.adjustLon(p.x - long0, over);
        double phi = p.y;
        double x = 0, y = 0;

        if (sphere) {
            double sinphi = Math.sin(phi);
            double cosphi = Math.cos(phi);
            double coslam = Math.cos(lam);

            if (mode == OBLIQ || mode == EQUIT) {
                double yVal = (mode == EQUIT) ? 1 + cosphi * coslam : 1 + sinph0 * sinphi + cosph0 * cosphi * coslam;
                if (yVal <= Values.EPSLN) {
                    return null;
                }
                yVal = Math.sqrt(2 / yVal);
                x = yVal * cosphi * Math.sin(lam);
                y = (mode == EQUIT) ? yVal * sinphi : yVal * (cosph0 * sinphi - sinph0 * cosphi * coslam);
            } else {
                // N_POLE or S_POLE
                if (mode == N_POLE) {
                    coslam = -coslam;
                }
                if (Math.abs(phi + lat0) < Values.EPSLN) {
                    return null;
                }
                y = Values.FORTPI - phi * 0.5;
                y = 2 * ((mode == S_POLE) ? Math.cos(y) : Math.sin(y));
                x = y * Math.sin(lam);
                y *= coslam;
            }
        } else {
            double sinb = 0, cosb = 0, b = 0;
            double coslam = Math.cos(lam);
            double sinlam = Math.sin(lam);
            double sinphi = Math.sin(phi);
            double q = ProjMath.qsfnz(e, sinphi);

            if (mode == OBLIQ || mode == EQUIT) {
                sinb = q / qp;
                cosb = Math.sqrt(1 - sinb * sinb);
            }

            switch (mode) {
                case OBLIQ:
                    b = 1 + sinb1 * sinb + cosb1 * cosb * coslam;
                    break;
                case EQUIT:
                    b = 1 + cosb * coslam;
                    break;
                case N_POLE:
                    b = Values.HALF_PI + phi;
                    q = qp - q;
                    break;
                case S_POLE:
                    b = phi - Values.HALF_PI;
                    q = qp + q;
                    break;
            }

            if (Math.abs(b) < Values.EPSLN) {
                return null;
            }

            switch (mode) {
                case OBLIQ:
                    b = Math.sqrt(2 / b);
                    y = ymf * b * (cosb1 * sinb - sinb1 * cosb * coslam);
                    x = xmf * b * cosb * sinlam;
                    break;
                case EQUIT:
                    b = Math.sqrt(2 / (1 + cosb * coslam));
                    y = b * sinb * ymf;
                    x = xmf * b * cosb * sinlam;
                    break;
                case N_POLE:
                case S_POLE:
                    if (q >= 0) {
                        b = Math.sqrt(q);
                        x = b * sinlam;
                        y = coslam * ((mode == S_POLE) ? b : -b);
                    }
                    break;
            }
        }

        return new Point(a * x + x0, a * y + y0, p.z);
    }

    @Override
    public Point inverse(Point p) {
        double x = (p.x - x0) / a;
        double y = (p.y - y0) / a;
        double lam, phi;

        if (sphere) {
            double rh = Math.sqrt(x * x + y * y);
            phi = rh * 0.5;
            if (phi > 1) {
                return null;
            }
            phi = 2 * Math.asin(phi);
            double sinz = 0, cosz = 0;
            if (mode == OBLIQ || mode == EQUIT) {
                sinz = Math.sin(phi);
                cosz = Math.cos(phi);
            }

            switch (mode) {
                case EQUIT:
                    phi = (Math.abs(rh) <= Values.EPSLN) ? 0 : Math.asin(y * sinz / rh);
                    x *= sinz;
                    y = cosz * rh;
                    break;
                case OBLIQ:
                    phi = (Math.abs(rh) <= Values.EPSLN) ? lat0 : Math.asin(cosz * sinph0 + y * sinz * cosph0 / rh);
                    x *= sinz * cosph0;
                    y = (cosz - Math.sin(phi) * sinph0) * rh;
                    break;
                case N_POLE:
                    y = -y;
                    phi = Values.HALF_PI - phi;
                    break;
                case S_POLE:
                    phi -= Values.HALF_PI;
                    break;
                default:
                    phi = 0;
            }
            lam = (y == 0 && (mode == EQUIT || mode == OBLIQ)) ? 0 : Math.atan2(x, y);
        } else {
            double ab = 0, rho, sCe, cCe, q;
            if (mode == OBLIQ || mode == EQUIT) {
                x /= dd;
                y *= dd;
                rho = Math.sqrt(x * x + y * y);
                if (rho < Values.EPSLN) {
                    return new Point(long0, lat0, p.z);
                }
                sCe = 2 * Math.asin(0.5 * rho / rq);
                cCe = Math.cos(sCe);
                x *= (sCe = Math.sin(sCe));
                if (mode == OBLIQ) {
                    ab = cCe * sinb1 + y * sCe * cosb1 / rho;
                    y = rho * cosb1 * cCe - y * sinb1 * sCe;
                } else {
                    ab = y * sCe / rho;
                    y = rho * cCe;
                }
            } else {
                if (mode == N_POLE) {
                    y = -y;
                }
                q = x * x + y * y;
                if (q == 0) {
                    return new Point(long0, lat0, p.z);
                }
                ab = 1 - q / qp;
                if (mode == S_POLE) {
                    ab = -ab;
                }
            }
            lam = Math.atan2(x, y);
            phi = authlat(Math.asin(ab), apa);
        }

        return new Point(ProjMath.adjustLon(long0 + lam, over), phi, p.z);
    }
}
