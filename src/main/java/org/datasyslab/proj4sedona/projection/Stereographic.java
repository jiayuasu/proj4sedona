package org.datasyslab.proj4sedona.projection;

import org.datasyslab.proj4sedona.common.ProjMath;
import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.Point;

/**
 * Stereographic projection implementation.
 * Mirrors: lib/projections/stere.js
 * 
 * <p>An azimuthal conformal projection. Commonly used for polar regions
 * (Polar Stereographic) and for mapping circular regions.</p>
 */
public class Stereographic implements Projection {

    private static final String[] NAMES = {
        "stere", "Stereographic", "Stereographic_South_Pole", "Stereographic_North_Pole",
        "Polar_Stereographic_variant_A", "Polar_Stereographic_variant_B", 
        "Polar_Stereographic", "Oblique_Stereographic"
    };

    private double a, e, es, lat0, long0, k0, x0, y0;
    private double sinlat0, coslat0, con, cons, ms1, X0, cosX0, sinX0;
    private Double latTs;
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
        this.k0 = params.k0;
        this.x0 = params.x0;
        this.y0 = params.y0;
        this.latTs = params.latTs;
        this.sphere = params.sphere;
        this.over = params.over;

        coslat0 = Math.cos(lat0);
        sinlat0 = Math.sin(lat0);

        if (sphere) {
            if (k0 == 1 && latTs != null && Math.abs(coslat0) <= Values.EPSLN) {
                k0 = 0.5 * (1 + ProjMath.sign(lat0) * Math.sin(latTs));
            }
        } else {
            if (Math.abs(coslat0) <= Values.EPSLN) {
                con = (lat0 > 0) ? 1 : -1;  // North or South pole
            }
            cons = Math.sqrt(Math.pow(1 + e, 1 + e) * Math.pow(1 - e, 1 - e));
            if (k0 == 1 && latTs != null && Math.abs(coslat0) <= Values.EPSLN && Math.abs(Math.cos(latTs)) > Values.EPSLN) {
                k0 = 0.5 * cons * ProjMath.msfnz(e, Math.sin(latTs), Math.cos(latTs)) / 
                     ProjMath.tsfnz(e, con * latTs, con * Math.sin(latTs));
            }
            ms1 = ProjMath.msfnz(e, sinlat0, coslat0);
            X0 = 2 * Math.atan(ssfn(lat0, sinlat0, e)) - Values.HALF_PI;
            cosX0 = Math.cos(X0);
            sinX0 = Math.sin(X0);
        }
    }

    private double ssfn(double phit, double sinphi, double eccen) {
        sinphi *= eccen;
        return Math.tan(0.5 * (Values.HALF_PI + phit)) * Math.pow((1 - sinphi) / (1 + sinphi), 0.5 * eccen);
    }

    @Override
    public Point forward(Point p) {
        double lon = p.x;
        double lat = p.y;
        double sinlat = Math.sin(lat);
        double coslat = Math.cos(lat);
        double dlon = ProjMath.adjustLon(lon - long0, over);

        // Origin point case
        if (Math.abs(Math.abs(lon - long0) - Math.PI) <= Values.EPSLN && Math.abs(lat + lat0) <= Values.EPSLN) {
            return new Point(Double.NaN, Double.NaN, p.z);
        }

        double x, y;
        if (sphere) {
            double A = 2 * k0 / (1 + sinlat0 * sinlat + coslat0 * coslat * Math.cos(dlon));
            x = a * A * coslat * Math.sin(dlon) + x0;
            y = a * A * (coslat0 * sinlat - sinlat0 * coslat * Math.cos(dlon)) + y0;
        } else {
            double X = 2 * Math.atan(ssfn(lat, sinlat, e)) - Values.HALF_PI;
            double cosX = Math.cos(X);
            double sinX = Math.sin(X);

            if (Math.abs(coslat0) <= Values.EPSLN) {
                // Polar case
                double ts = ProjMath.tsfnz(e, lat * con, con * sinlat);
                double rh = 2 * a * k0 * ts / cons;
                x = x0 + rh * Math.sin(lon - long0);
                y = y0 - con * rh * Math.cos(lon - long0);
            } else if (Math.abs(sinlat0) < Values.EPSLN) {
                // Equatorial case
                double A = 2 * a * k0 / (1 + cosX * Math.cos(dlon));
                y = A * sinX;
                x = A * cosX * Math.sin(dlon) + x0;
                y += y0;
            } else {
                // Oblique case
                double A = 2 * a * k0 * ms1 / (cosX0 * (1 + sinX0 * sinX + cosX0 * cosX * Math.cos(dlon)));
                x = A * cosX * Math.sin(dlon) + x0;
                y = A * (cosX0 * sinX - sinX0 * cosX * Math.cos(dlon)) + y0;
            }
        }

        return new Point(x, y, p.z);
    }

    @Override
    public Point inverse(Point p) {
        double x = p.x - x0;
        double y = p.y - y0;
        double rh = Math.sqrt(x * x + y * y);
        double lon, lat;

        if (sphere) {
            double c = 2 * Math.atan(rh / (2 * a * k0));
            lon = long0;
            lat = lat0;
            if (rh <= Values.EPSLN) {
                return new Point(lon, lat, p.z);
            }
            lat = Math.asin(Math.cos(c) * sinlat0 + y * Math.sin(c) * coslat0 / rh);
            if (Math.abs(coslat0) < Values.EPSLN) {
                lon = (lat0 > 0) 
                    ? ProjMath.adjustLon(long0 + Math.atan2(x, -y), over)
                    : ProjMath.adjustLon(long0 + Math.atan2(x, y), over);
            } else {
                lon = ProjMath.adjustLon(long0 + Math.atan2(x * Math.sin(c), 
                    rh * coslat0 * Math.cos(c) - y * sinlat0 * Math.sin(c)), over);
            }
        } else {
            if (Math.abs(coslat0) <= Values.EPSLN) {
                // Polar case
                if (rh <= Values.EPSLN) {
                    return new Point(long0, lat0, p.z);
                }
                x *= con;
                y *= con;
                double ts = rh * cons / (2 * a * k0);
                lat = con * ProjMath.phi2z(e, ts);
                lon = con * ProjMath.adjustLon(con * long0 + Math.atan2(x, -y), over);
            } else {
                // Oblique/equatorial case
                double ce = 2 * Math.atan(rh * cosX0 / (2 * a * k0 * ms1));
                lon = long0;
                double Chi;
                if (rh <= Values.EPSLN) {
                    Chi = X0;
                } else {
                    Chi = Math.asin(Math.cos(ce) * sinX0 + y * Math.sin(ce) * cosX0 / rh);
                    lon = ProjMath.adjustLon(long0 + Math.atan2(x * Math.sin(ce), 
                        rh * cosX0 * Math.cos(ce) - y * sinX0 * Math.sin(ce)), over);
                }
                lat = -ProjMath.phi2z(e, Math.tan(0.5 * (Values.HALF_PI + Chi)));
            }
        }

        return new Point(lon, lat, p.z);
    }
}
