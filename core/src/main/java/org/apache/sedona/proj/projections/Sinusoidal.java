package org.apache.sedona.proj.projections;

import org.apache.sedona.proj.common.MathUtils;
import org.apache.sedona.proj.constants.Values;
import org.apache.sedona.proj.core.Point;
import org.apache.sedona.proj.core.Projection;

/**
 * Sinusoidal projection implementation.
 * Ported from proj4js/lib/projections/sinu.js
 */
public class Sinusoidal {
    
    private static final int MAX_ITER = 20;
    
    // Projection-specific parameters
    private double[] en;
    private double n;
    private double m;
    private double C_y;
    private double C_x;
    private double es;
    private boolean sphere;
    
    /**
     * Initialize the Sinusoidal projection.
     * @param proj the projection definition
     */
    public void initialize(Projection proj) {
        this.es = proj.es;
        this.sphere = proj.sphere;
        
        if (!this.sphere) {
            this.en = MathUtils.enfn(this.es);
            // For ellipsoidal case, we still need to set n, m, C_y, C_x
            this.n = 1;
            this.m = 0;
            this.C_y = Math.sqrt((this.m + 1) / this.n);
            this.C_x = this.C_y / (this.m + 1);
        } else {
            this.n = 1;
            this.m = 0;
            this.es = 0;
            this.C_y = Math.sqrt((this.m + 1) / this.n);
            this.C_x = this.C_y / (this.m + 1);
        }
    }
    
    /**
     * Forward transformation: lat,long to x,y.
     */
    public double[] forward(double lon, double lat, Projection proj) {
        double x, y;
        
        // Forward equations
        lon = MathUtils.adjustLon(lon - proj.long0);
        
        if (this.sphere) {
            if (this.m == 0) {
                lat = this.n != 1 ? Math.asin(this.n * Math.sin(lat)) : lat;
            } else {
                double k = this.n * Math.sin(lat);
                for (int i = MAX_ITER; i > 0; --i) {
                    double V = (this.m * lat + Math.sin(lat) - k) / (this.m + Math.cos(lat));
                    lat -= V;
                    if (Math.abs(V) < Values.EPSLN) {
                        break;
                    }
                }
            }
            x = proj.a * this.C_x * lon * (this.m + Math.cos(lat));
            y = proj.a * this.C_y * lat;
        } else {
            double s = Math.sin(lat);
            double c = Math.cos(lat);
            y = proj.a * MathUtils.mlfn(lat, s, c, this.en);
            x = proj.a * lon * c / Math.sqrt(1 - this.es * s * s);
        }
        
        x += proj.x0;
        y += proj.y0;
        
        return new double[]{x, y};
    }
    
    /**
     * Inverse transformation: x,y to lat,long.
     */
    public double[] inverse(double x, double y, Projection proj) {
        double lat, temp, lon, s;
        
        x -= proj.x0;
        lon = x / proj.a;
        y -= proj.y0;
        lat = y / proj.a;
        
        if (this.sphere) {
            lat /= this.C_y;
            lon = lon / (this.C_x * (this.m + Math.cos(lat)));
            if (this.m != 0) {
                lat = MathUtils.asinz((this.m * lat + Math.sin(lat)) / this.n);
            } else if (this.n != 1) {
                lat = MathUtils.asinz(Math.sin(lat) / this.n);
            }
            lon = MathUtils.adjustLon(lon + proj.long0);
            lat = MathUtils.adjustLat(lat);
        } else {
            lat = MathUtils.invMlfn(y / proj.a, this.es, this.en);
            s = Math.abs(lat);
            if (s < Values.HALF_PI) {
                s = Math.sin(lat);
                // Use the same formula as proj4js
                temp = proj.long0 + x * Math.sqrt(1 - this.es * s * s) / (proj.a * Math.cos(lat));
                lon = MathUtils.adjustLon(temp);
            } else if ((s - Values.EPSLN) < Values.HALF_PI) {
                lon = proj.long0;
            }
        }
        
        return new double[]{lon, lat};
    }
    
    // Static methods to match the pattern used by other projections
    
    /**
     * Initialize the Sinusoidal projection.
     */
    public static void init(Projection proj) {
        Sinusoidal sinu = new Sinusoidal();
        sinu.initialize(proj);
        
        // Store the instance in the projection for later use
        proj.sinu = sinu;
    }
    
    /**
     * Forward transformation for Sinusoidal.
     */
    public static Point forward(Projection proj, Point point) {
        Sinusoidal sinu = (Sinusoidal) proj.sinu;
        if (sinu == null) {
            throw new IllegalStateException("Projection not initialized");
        }
        
        double[] result = sinu.forward(point.x, point.y, proj);
        return new Point(result[0], result[1], point.z, point.m);
    }
    
    /**
     * Inverse transformation for Sinusoidal.
     */
    public static Point inverse(Projection proj, Point point) {
        Sinusoidal sinu = (Sinusoidal) proj.sinu;
        if (sinu == null) {
            throw new IllegalStateException("Projection not initialized");
        }
        
        double[] result = sinu.inverse(point.x, point.y, proj);
        return new Point(result[0], result[1], point.z, point.m);
    }
}
