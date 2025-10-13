package org.proj4.projections;

import org.proj4.core.Point;
import org.proj4.core.Projection;
import org.proj4.constants.Values;
import org.proj4.common.MathUtils;

/**
 * Transverse Mercator projection implementation.
 * This is the base projection used by UTM and other transverse projections.
 */
public class TransverseMercator {
    
    /**
     * Initializes a Transverse Mercator projection.
     * @param proj the projection to initialize
     */
    public static void init(Projection proj) {
        // Set default values
        if (Double.isNaN(proj.x0)) proj.x0 = 0;
        if (Double.isNaN(proj.y0)) proj.y0 = 0;
        if (Double.isNaN(proj.long0)) proj.long0 = 0;
        if (Double.isNaN(proj.lat0)) proj.lat0 = 0;
        
        if (proj.es != 0) {
            // Calculate ellipsoid parameters
            proj.e0 = MathUtils.e0fn(proj.es);
            proj.e1 = MathUtils.e1fn(proj.es);
            proj.e2 = MathUtils.e2fn(proj.es);
            proj.e3 = MathUtils.e3fn(proj.es);
            
            // Calculate en coefficients for meridian length calculation
            proj.en = MathUtils.enfn(proj.es);
            
            // Calculate meridian distance at origin latitude
            proj.ml0 = MathUtils.mlfn(proj.lat0, Math.sin(proj.lat0), Math.cos(proj.lat0), proj.en);
        }
    }
    
    /**
     * Forward transformation for Transverse Mercator.
     * @param proj the projection
     * @param p the input point (longitude, latitude in radians)
     * @return the transformed point (x, y in meters)
     */
    public static Point forward(Projection proj, Point p) {
        double lon = p.x;
        double lat = p.y;
        
        double delta_lon = adjustLon(lon - proj.long0);
        double sin_phi = Math.sin(lat);
        double cos_phi = Math.cos(lat);
        
        double x, y;
        
        if (proj.es == 0) {
            // Spherical case
            double b = cos_phi * Math.sin(delta_lon);
            
            if (Math.abs(Math.abs(b) - 1) < Values.EPSLN) {
                return null; // Error condition
            } else {
                x = 0.5 * proj.a * proj.k0 * Math.log((1 + b) / (1 - b)) + proj.x0;
                y = cos_phi * Math.cos(delta_lon) / Math.sqrt(1 - b * b);
                b = Math.abs(y);
                
                if (b >= 1) {
                    if ((b - 1) > Values.EPSLN) {
                        return null; // Error condition
                    } else {
                        y = 0;
                    }
                } else {
                    y = Math.acos(y);
                }
                
                if (lat < 0) {
                    y = -y;
                }
                
                y = proj.a * proj.k0 * (y - proj.lat0) + proj.y0;
            }
        } else {
            // Ellipsoidal case
            double al = cos_phi * delta_lon;
            double als = al * al;
            double c = proj.ep2 * cos_phi * cos_phi;
            double cs = c * c;
            double tq = Math.abs(cos_phi) > Values.EPSLN ? Math.tan(lat) : 0;
            double t = tq * tq;
            double ts = t * t;
            double con = 1 - proj.es * sin_phi * sin_phi;
            al = al / Math.sqrt(con);
            double ml = MathUtils.mlfn(lat, sin_phi, cos_phi, proj.en);
            
            x = proj.a * (proj.k0 * al * (1
                + als / 6 * (1 - t + c
                    + als / 20 * (5 - 18 * t + ts + 14 * c - 58 * t * c
                        + als / 42 * (61 + 179 * ts - ts * t - 479 * t)))))
                + proj.x0;
            
            y = proj.a * (proj.k0 * (ml - proj.ml0
                + sin_phi * delta_lon * al / 2 * (1
                    + als / 12 * (5 - t + 9 * c + 4 * cs
                        + als / 30 * (61 + ts - 58 * t + 270 * c - 330 * t * c
                            + als / 56 * (1385 + 543 * ts - ts * t - 3111 * t))))))
                + proj.y0;
        }
        
        return new Point(x, y, p.z, p.m);
    }
    
    /**
     * Inverse transformation for Transverse Mercator.
     * @param proj the projection
     * @param p the input point (x, y in meters)
     * @return the transformed point (longitude, latitude in radians)
     */
    public static Point inverse(Projection proj, Point p) {
        double x = (p.x - proj.x0) / proj.a;
        double y = (p.y - proj.y0) / proj.a;
        
        double lat, lon;
        
        if (proj.es == 0) {
            // Spherical case
            double f = Math.exp(x / proj.k0);
            double g = 0.5 * (f - 1 / f);
            double temp = proj.lat0 + y / proj.k0;
            double h = Math.cos(temp);
            double con = Math.sqrt((1 - h * h) / (1 + g * g));
            lat = Math.asin(con);
            
            if (y < 0) {
                lat = -lat;
            }
            
            if ((g == 0) && (h == 0)) {
                lon = 0;
            } else {
                lon = adjustLon(Math.atan2(g, h) + proj.long0);
            }
        } else {
            // Ellipsoidal case
            double con = proj.ml0 + y / proj.k0;
            
            // Debug: check if en is null
            if (proj.en == null) {
                // Initialize en if it's null
                proj.en = MathUtils.enfn(proj.es);
                proj.ml0 = MathUtils.mlfn(proj.lat0, Math.sin(proj.lat0), Math.cos(proj.lat0), proj.en);
            }
            
            double phi = MathUtils.invMlfn(con, proj.es, proj.en);
            
            if (Math.abs(phi) < Values.HALF_PI) {
                double sin_phi = Math.sin(phi);
                double cos_phi = Math.cos(phi);
                double tan_phi = Math.abs(cos_phi) > Values.EPSLN ? Math.tan(phi) : 0;
                double c = proj.ep2 * cos_phi * cos_phi;
                double cs = c * c;
                double t = tan_phi * tan_phi;
                double ts = t * t;
                con = 1 - proj.es * sin_phi * sin_phi;
                double d = x * Math.sqrt(con) / proj.k0;
                double ds = d * d;
                con = con * tan_phi;
                
                lat = phi - (con * ds / (1 - proj.es)) * 0.5 * (1
                    - ds / 12 * (5 + 3 * t - 9 * c * t + c - 4 * cs
                        - ds / 30 * (61 + 90 * t - 252 * c * t + 45 * ts + 46 * c
                            - ds / 56 * (1385 + 3633 * t + 4095 * ts + 1574 * ts * t))));
                
                lon = adjustLon(proj.long0 + ((d * (1
                    - ds / 6 * (1 + 2 * t + c
                        - ds / 20 * (5 + 28 * t + 24 * ts + 8 * c * t + 6 * c
                            - ds / 42 * (61 + 662 * t + 1320 * ts + 720 * ts * t))))) / cos_phi));
            } else {
                lat = Values.HALF_PI * Math.signum(y);
                lon = 0;
            }
        }
        
        return new Point(lon, lat, p.z, p.m);
    }
    
    /**
     * Adjusts longitude to valid range.
     */
    private static double adjustLon(double lon) {
        return MathUtils.adjustLon(lon);
    }
}
