package org.proj4.projections;

import org.proj4.core.Point;
import org.proj4.core.Projection;
import org.proj4.constants.Values;
import org.proj4.common.MathUtils;

/**
 * Albers Equal Area Conic projection implementation.
 * This is an equal-area conic projection commonly used for mapping
 * large areas that are predominantly east-west in extent.
 */
public class AlbersEqualArea {
    
    /**
     * Initializes an Albers Equal Area projection.
     * @param proj the projection to initialize
     */
    public static void init(Projection proj) {
        if (Math.abs(proj.lat1 + proj.lat2) < Values.EPSLN) {
            return;
        }
        
        double temp = proj.b / proj.a;
        double es = 1 - Math.pow(temp, 2);
        double e3 = Math.sqrt(es);
        
        double sin_po = Math.sin(proj.lat1);
        double cos_po = Math.cos(proj.lat1);
        double con = sin_po;
        double ms1 = MathUtils.msfnz(e3, sin_po, cos_po);
        double qs1 = MathUtils.qsfnz(e3, sin_po);
        
        sin_po = Math.sin(proj.lat2);
        cos_po = Math.cos(proj.lat2);
        double ms2 = MathUtils.msfnz(e3, sin_po, cos_po);
        double qs2 = MathUtils.qsfnz(e3, sin_po);
        
        sin_po = Math.sin(proj.lat0);
        cos_po = Math.cos(proj.lat0);
        double qs0 = MathUtils.qsfnz(e3, sin_po);
        
        double ns0;
        if (Math.abs(proj.lat1 - proj.lat2) > Values.EPSLN) {
            ns0 = (ms1 * ms1 - ms2 * ms2) / (qs2 - qs1);
        } else {
            ns0 = con;
        }
        
        double c = ms1 * ms1 + ns0 * qs1;
        double rh = proj.a * Math.sqrt(c - ns0 * qs0) / ns0;
        
        // Store calculated values in projection object
        proj.e3 = e3;
        proj.ns0 = ns0;
        proj.c = c;
        proj.rho0 = rh;
    }
    
    /**
     * Forward transformation for Albers Equal Area.
     * @param proj the projection
     * @param p the input point (longitude, latitude in radians)
     * @return the transformed point (x, y in meters)
     */
    public static Point forward(Projection proj, Point p) {
        double lon = p.x;
        double lat = p.y;
        
        double sin_phi = Math.sin(lat);
        
        double qs = MathUtils.qsfnz(proj.e3, sin_phi);
        double rh1 = proj.a * Math.sqrt(proj.c - proj.ns0 * qs) / proj.ns0;
        double theta = proj.ns0 * adjustLon(lon - proj.long0);
        double x = rh1 * Math.sin(theta) + proj.x0;
        double y = proj.rho0 - rh1 * Math.cos(theta) + proj.y0;
        
        return new Point(x, y, p.z, p.m);
    }
    
    /**
     * Inverse transformation for Albers Equal Area.
     * @param proj the projection
     * @param p the input point (x, y in meters)
     * @return the transformed point (longitude, latitude in radians)
     */
    public static Point inverse(Projection proj, Point p) {
        double rh1, qs, con, theta, lon, lat;
        
        p.x -= proj.x0;
        p.y = proj.rho0 - p.y + proj.y0;
        if (proj.ns0 >= 0) {
            rh1 = Math.sqrt(p.x * p.x + p.y * p.y);
            con = 1;
        } else {
            rh1 = -Math.sqrt(p.x * p.x + p.y * p.y);
            con = -1;
        }
        theta = 0;
        if (rh1 != 0) {
            theta = Math.atan2(con * p.x, con * p.y);
        }
        con = rh1 * proj.ns0 / proj.a;
        if (proj.sphere) {
            lat = Math.asin((proj.c - con * con) / (2 * proj.ns0));
        } else {
            qs = (proj.c - con * con) / proj.ns0;
            lat = phi1z(proj.e3, qs);
        }
        
        lon = adjustLon(theta / proj.ns0 + proj.long0);
        return new Point(lon, lat, p.z, p.m);
    }
    
    /**
     * Function to compute phi1, the latitude for the inverse of the
     * Albers Conical Equal-Area projection.
     */
    private static double phi1z(double eccent, double qs) {
        double sinphi, cosphi, con, com, dphi;
        double phi = Math.asin(0.5 * qs);
        if (eccent < Values.EPSLN) {
            return phi;
        }
        
        double eccnts = eccent * eccent;
        for (int i = 1; i <= 25; i++) {
            sinphi = Math.sin(phi);
            cosphi = Math.cos(phi);
            con = eccent * sinphi;
            com = 1 - con * con;
            dphi = 0.5 * com * com / cosphi * (qs / (1 - eccnts) - sinphi / com + 0.5 / eccent * Math.log((1 - con) / (1 + con)));
            phi = phi + dphi;
            if (Math.abs(dphi) <= 1e-7) {
                return phi;
            }
        }
        return Double.NaN;
    }
    
    /**
     * Adjusts longitude to valid range.
     */
    private static double adjustLon(double lon) {
        return MathUtils.adjustLon(lon);
    }
}
