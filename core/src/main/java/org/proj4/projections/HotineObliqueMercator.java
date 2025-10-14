package org.proj4.projections;

import org.proj4.common.MathUtils;
import org.proj4.constants.Values;
import org.proj4.core.Point;
import org.proj4.core.Projection;

/**
 * Hotine Oblique Mercator projection implementation.
 * Ported from proj4js/lib/projections/omerc.js
 */
public class HotineObliqueMercator {
    
    private static final double TOL = 1e-7;
    
    // Projection-specific parameters
    private boolean noOff;
    private boolean noRot;
    private double rectifiedGridAngle;
    private double es;
    private double A;
    private double B;
    private double E;
    private double e;
    private double lam0;
    private double singam;
    private double cosgam;
    private double sinrot;
    private double cosrot;
    private double rB;
    private double ArB;
    private double BrA;
    private double u0;
    private double vPoleN;
    private double vPoleS;
    
    /**
     * Initialize the Oblique Mercator projection.
     * @param proj the projection definition
     */
    public void initialize(Projection proj) {
        double con, com, cosph0, D, F, H, L, sinph0, p, J, gamma = 0;
        double gamma0, lamc = 0, lam1 = 0, lam2 = 0, phi1 = 0, phi2 = 0, alpha_c = 0;
        
        // Only Type A uses the no_off or no_uoff property
        this.noOff = isTypeA(proj);
        this.noRot = proj.noRot;
        this.es = proj.es;
        this.e = proj.e;
        
        boolean alp = false;
        if (proj.alpha != 0) {
            alp = true;
        }
        
        boolean gam = false;
        if (proj.rectifiedGridAngle != 0) {
            gam = true;
            this.rectifiedGridAngle = proj.rectifiedGridAngle;
        }
        
        
        if (alp) {
            alpha_c = proj.alpha;
        }
        
        if (gam) {
            gamma = proj.rectifiedGridAngle;
        }
        
        if (alp || gam) {
            lamc = proj.longc;
        } else {
            lam1 = proj.long1;
            phi1 = proj.lat1;
            lam2 = proj.long2;
            phi2 = proj.lat2;
            
            if (Math.abs(phi1 - phi2) <= TOL || (con = Math.abs(phi1)) <= TOL
                || Math.abs(con - Values.HALF_PI) <= TOL || Math.abs(Math.abs(proj.lat0) - Values.HALF_PI) <= TOL
                || Math.abs(Math.abs(phi2) - Values.HALF_PI) <= TOL) {
                throw new IllegalArgumentException("Invalid projection parameters");
            }
        }
        
        double one_es = 1.0 - this.es;
        com = Math.sqrt(one_es);
        
        if (Math.abs(proj.lat0) > Values.EPSLN) {
            sinph0 = Math.sin(proj.lat0);
            cosph0 = Math.cos(proj.lat0);
            con = 1 - this.es * sinph0 * sinph0;
            this.B = cosph0 * cosph0;
            this.B = Math.sqrt(1 + this.es * this.B * this.B / one_es);
            this.A = this.B * proj.k0 * com / con;
            D = this.B * com / (cosph0 * Math.sqrt(con));
            F = D * D - 1;
            
            if (F <= 0) {
                F = 0;
            } else {
                F = Math.sqrt(F);
                if (proj.lat0 < 0) {
                    F = -F;
                }
            }
            
            this.E = F += D;
            this.E *= Math.pow(MathUtils.tsfnz(this.e, proj.lat0, sinph0), this.B);
        } else {
            this.B = 1 / com;
            this.A = proj.k0;
            this.E = D = F = 1;
        }
        
        if (alp || gam) {
            if (alp) {
                gamma0 = Math.asin(Math.sin(alpha_c) / D);
                if (!gam) {
                    gamma = alpha_c;
                }
            } else {
                gamma0 = gamma;
                alpha_c = Math.asin(D * Math.sin(gamma0));
            }
            this.lam0 = lamc - Math.asin(0.5 * (F - 1 / F) * Math.tan(gamma0)) / this.B;
        } else {
            H = Math.pow(MathUtils.tsfnz(this.e, phi1, Math.sin(phi1)), this.B);
            L = Math.pow(MathUtils.tsfnz(this.e, phi2, Math.sin(phi2)), this.B);
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
            
            this.lam0 = MathUtils.adjustLon(0.5 * (lam1 + lam2) - Math.atan(J * Math.tan(0.5 * this.B * (lam1 - lam2)) / p) / this.B);
            gamma0 = Math.atan(2 * Math.sin(this.B * MathUtils.adjustLon(lam1 - this.lam0)) / (F - 1 / F));
            gamma = alpha_c = Math.asin(D * Math.sin(gamma0));
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
            this.u0 = Math.abs(this.ArB * Math.atan(Math.sqrt(D * D - 1) / Math.cos(alpha_c)));
            
            if (proj.lat0 < 0) {
                this.u0 = -this.u0;
            }
        }
        
        F = 0.5 * gamma0;
        this.vPoleN = this.ArB * Math.log(Math.tan(Values.FORTPI - F));
        this.vPoleS = this.ArB * Math.log(Math.tan(Values.FORTPI + F));
    }
    
    /**
     * Check if this is a Type A projection.
     */
    private boolean isTypeA(Projection proj) {
        String[] typeAProjections = {
            "Hotine_Oblique_Mercator", "Hotine_Oblique_Mercator_variant_A", 
            "Hotine_Oblique_Mercator_Azimuth_Natural_Origin"
        };
        
        String projectionName = proj.projName;
        for (String typeA : typeAProjections) {
            if (typeA.equals(projectionName)) {
                return true;
            }
        }
        
        return proj.noUoff || proj.noOff;
    }
    
    /**
     * Forward transformation: lat,long to x,y.
     */
    public double[] forward(double lon, double lat, Projection proj) {
        double S, T, U, V, W, temp, u, v;
        lon = lon - this.lam0;
        
        if (Math.abs(Math.abs(lat) - Values.HALF_PI) > Values.EPSLN) {
            W = this.E / Math.pow(MathUtils.tsfnz(this.e, lat, Math.sin(lat)), this.B);
            
            temp = 1 / W;
            S = 0.5 * (W - temp);
            T = 0.5 * (W + temp);
            V = Math.sin(this.B * lon);
            U = (S * this.singam - V * this.cosgam) / T;
            
            if (Math.abs(Math.abs(U) - 1.0) < Values.EPSLN) {
                throw new IllegalArgumentException("Projection error");
            }
            
            v = 0.5 * this.ArB * Math.log((1 - U) / (1 + U));
            temp = Math.cos(this.B * lon);
            
            if (Math.abs(temp) < TOL) {
                u = this.A * lon;
            } else {
                u = this.ArB * Math.atan2((S * this.cosgam + V * this.singam), temp);
            }
        } else {
            v = lat > 0 ? this.vPoleN : this.vPoleS;
            u = this.ArB * lat;
        }
        
        double x, y;
        if (this.noRot) {
            x = u;
            y = v;
        } else {
            u -= this.u0;
            x = v * this.cosrot + u * this.sinrot;
            y = u * this.cosrot - v * this.sinrot;
        }
        
        // Apply scaling and offset (missing in original Java implementation)
        x = proj.a * x + proj.x0;
        y = proj.a * y + proj.y0;
        
        return new double[]{x, y};
    }
    
    /**
     * Inverse transformation: x,y to lat,long.
     */
    public double[] inverse(double x, double y, Projection proj) {
        double u, v, Qp, Sp, Tp, Vp, Up;
        
        // Remove scaling and offset (missing in original Java implementation)
        x = (x - proj.x0) * (1.0 / proj.a);
        y = (y - proj.y0) * (1.0 / proj.a);
        
        if (this.noRot) {
            v = y;
            u = x;
        } else {
            v = x * this.cosrot - y * this.sinrot;
            u = y * this.cosrot + x * this.sinrot + this.u0;
        }
        
        Qp = Math.exp(-this.BrA * v);
        Sp = 0.5 * (Qp - 1 / Qp);
        Tp = 0.5 * (Qp + 1 / Qp);
        Vp = Math.sin(this.BrA * u);
        Up = (Vp * this.cosgam + Sp * this.singam) / Tp;
        
        double lon, lat;
        if (Math.abs(Math.abs(Up) - 1) < Values.EPSLN) {
            lon = 0;
            lat = Up < 0 ? -Values.HALF_PI : Values.HALF_PI;
        } else {
            lat = this.E / Math.sqrt((1 + Up) / (1 - Up));
            lat = MathUtils.phi2z(this.e, Math.pow(lat, 1 / this.B));
            
            if (Double.isInfinite(lat)) {
                throw new IllegalArgumentException("Projection error");
            }
            
            lon = -this.rB * Math.atan2((Sp * this.cosgam - Vp * this.singam), Math.cos(this.BrA * u));
        }
        
        lon += this.lam0;
        lon = MathUtils.adjustLon(lon);
        
        return new double[]{lon, lat};
    }
    
    // Static methods to match the pattern used by other projections
    
    /**
     * Initialize the Hotine Oblique Mercator projection.
     */
    public static void init(Projection proj) {
        HotineObliqueMercator omerc = new HotineObliqueMercator();
        omerc.initialize(proj);
        
        // Store the instance in the projection for later use
        proj.omerc = omerc;
    }
    
    /**
     * Forward transformation for Hotine Oblique Mercator.
     */
    public static Point forward(Projection proj, Point point) {
        HotineObliqueMercator omerc = (HotineObliqueMercator) proj.omerc;
        if (omerc == null) {
            throw new IllegalStateException("Projection not initialized");
        }
        
        double[] result = omerc.forward(point.x, point.y, proj);
        return new Point(result[0], result[1], point.z, point.m);
    }
    
    /**
     * Inverse transformation for Hotine Oblique Mercator.
     */
    public static Point inverse(Projection proj, Point point) {
        HotineObliqueMercator omerc = (HotineObliqueMercator) proj.omerc;
        if (omerc == null) {
            throw new IllegalStateException("Projection not initialized");
        }
        
        double[] result = omerc.inverse(point.x, point.y, proj);
        return new Point(result[0], result[1], point.z, point.m);
    }
}
