package org.apache.sedona.proj.projections;

import org.apache.sedona.proj.core.Point;
import org.apache.sedona.proj.core.Projection;
import org.apache.sedona.proj.constants.Values;
import org.apache.sedona.proj.common.MathUtils;

/**
 * Lambert Conformal Conic projection implementation.
 * This is a conformal conic projection commonly used for mapping large areas
 * that are predominantly east-west in extent.
 */
public class LambertConformalConic {
    
    /**
     * Initializes a Lambert Conformal Conic projection.
     * @param proj the projection to initialize
     */
    public static void init(Projection proj) {
        // Set defaults
        if (proj.lat2 == 0.0) {
            proj.lat2 = proj.lat1;
        }
        if (proj.k0 == 0.0) {
            proj.k0 = 1.0;
        }
        if (proj.x0 == 0.0 && proj.y0 == 0.0) {
            proj.x0 = 0.0;
            proj.y0 = 0.0;
        }
        
        // Standard Parallels cannot be equal and on opposite sides of the equator
        if (Math.abs(proj.lat1 + proj.lat2) < Values.EPSLN) {
            return;
        }
        
        double sin1 = Math.sin(proj.lat1);
        double cos1 = Math.cos(proj.lat1);
        double ms1 = MathUtils.msfnz(proj.e, sin1, cos1);
        double ts1 = MathUtils.tsfnz(proj.e, proj.lat1, sin1);
        
        double sin2 = Math.sin(proj.lat2);
        double cos2 = Math.cos(proj.lat2);
        double ms2 = MathUtils.msfnz(proj.e, sin2, cos2);
        double ts2 = MathUtils.tsfnz(proj.e, proj.lat2, sin2);
        
        double ts0 = Math.abs(Math.abs(proj.lat0) - Values.HALF_PI) < Values.EPSLN
            ? 0.0 // Handle poles by setting ts0 to 0
            : MathUtils.tsfnz(proj.e, proj.lat0, Math.sin(proj.lat0));
        
        double ns;
        if (Math.abs(proj.lat1 - proj.lat2) > Values.EPSLN) {
            ns = Math.log(ms1 / ms2) / Math.log(ts1 / ts2);
        } else {
            ns = sin1;
        }
        if (Double.isNaN(ns)) {
            ns = sin1;
        }
        
        double f0 = ms1 / (ns * Math.pow(ts1, ns));
        double rh = proj.a * f0 * Math.pow(ts0, ns);
        
        // Store calculated values in projection object
        proj.n = ns;  // Use ns for consistency with original
        proj.rho0 = rh;  // Use rh for consistency with original
        proj.f0 = f0;  // Store f0 for forward/inverse calculations
    }
    
    /**
     * Forward transformation for Lambert Conformal Conic.
     * @param proj the projection
     * @param p the input point (longitude, latitude in radians)
     * @return the transformed point (x, y in meters)
     */
    public static Point forward(Projection proj, Point p) {
        double lon = p.x;
        double lat = p.y;
        
        // singular cases :
        if (Math.abs(2 * Math.abs(lat) - Math.PI) <= Values.EPSLN) {
            lat = Math.signum(lat) * (Values.HALF_PI - 2 * Values.EPSLN);
        }
        
        double con = Math.abs(Math.abs(lat) - Values.HALF_PI);
        double ts, rh1;
        if (con > Values.EPSLN) {
            ts = MathUtils.tsfnz(proj.e, lat, Math.sin(lat));
            rh1 = proj.a * proj.f0 * Math.pow(ts, proj.n);
        } else {
            con = lat * proj.n;
            if (con <= 0) {
                return null;
            }
            rh1 = 0;
        }
        double theta = proj.n * adjustLon(lon - proj.long0);
        double x = proj.k0 * (rh1 * Math.sin(theta)) + proj.x0;
        double y = proj.k0 * (proj.rho0 - rh1 * Math.cos(theta)) + proj.y0;
        
        return new Point(x, y, p.z, p.m);
    }
    
    /**
     * Inverse transformation for Lambert Conformal Conic.
     * @param proj the projection
     * @param p the input point (x, y in meters)
     * @return the transformed point (longitude, latitude in radians)
     */
    public static Point inverse(Projection proj, Point p) {
        double rh1, con, ts;
        double lat, lon;
        double x = (p.x - proj.x0) / proj.k0;
        double y = (proj.rho0 - (p.y - proj.y0) / proj.k0);
        if (proj.n > 0) {
            rh1 = Math.sqrt(x * x + y * y);
            con = 1;
        } else {
            rh1 = -Math.sqrt(x * x + y * y);
            con = -1;
        }
        double theta = 0;
        if (rh1 != 0) {
            theta = Math.atan2((con * x), (con * y));
        }
        if ((rh1 != 0) || (proj.n > 0)) {
            con = 1 / proj.n;
            ts = Math.pow((rh1 / (proj.a * proj.f0)), con);
            lat = MathUtils.phi2z(proj.e, ts);
            if (lat == -9999) {
                return null;
            }
        } else {
            lat = -Values.HALF_PI;
        }
        lon = adjustLon(theta / proj.n + proj.long0);
        
        return new Point(lon, lat, p.z, p.m);
    }
    
    /**
     * Adjusts longitude to valid range.
     */
    private static double adjustLon(double lon) {
        return MathUtils.adjustLon(lon);
    }
}
