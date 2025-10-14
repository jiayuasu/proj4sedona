package org.proj4.projections;

import org.proj4.core.Projection;
import org.proj4.core.Point;
import org.proj4.common.MathUtils;
import org.proj4.constants.Values;

/**
 * Equidistant Conic projection implementation.
 * Ported from proj4js/lib/projections/eqdc.js
 */
public class EquidistantConic {
    
    // Projection-specific parameters
    private double temp;
    private double es;
    private double e;
    private double e0;
    private double e1;
    private double e2;
    private double e3;
    private double[] en;
    private double sin_phi;
    private double cos_phi;
    private double ms1;
    private double ml1;
    private double ms2;
    private double ml2;
    private double ns;
    private double g;
    private double ml0;
    private double rh;
    
    /**
     * Initialize the Equidistant Conic projection.
     * @param proj the projection instance
     */
    public void initialize(Projection proj) {
        // Standard Parallels cannot be equal and on opposite sides of the equator
        if (Math.abs(proj.lat1 + proj.lat2) < Values.EPSLN) {
            return; // Skip initialization if invalid parameters
        }
        
        double lat2 = proj.lat2 != 0 ? proj.lat2 : proj.lat1;
        this.temp = proj.b / proj.a;
        this.es = 1 - Math.pow(this.temp, 2);
        this.e = Math.sqrt(this.es);
        this.e0 = MathUtils.e0fn(this.es);
        this.e1 = MathUtils.e1fn(this.es);
        this.e2 = MathUtils.e2fn(this.es);
        this.e3 = MathUtils.e3fn(this.es);
        
        this.sin_phi = Math.sin(proj.lat1);
        this.cos_phi = Math.cos(proj.lat1);
        
        this.ms1 = MathUtils.msfnz(this.e, this.sin_phi, this.cos_phi);
        // Use enfn to get the correct coefficients for mlfn
        this.en = MathUtils.enfn(this.es);
        this.ml1 = MathUtils.mlfn(proj.lat1, this.sin_phi, this.cos_phi, this.en);
        
        if (Math.abs(proj.lat1 - lat2) < Values.EPSLN) {
            this.ns = this.sin_phi;
        } else {
            this.sin_phi = Math.sin(lat2);
            this.cos_phi = Math.cos(lat2);
            this.ms2 = MathUtils.msfnz(this.e, this.sin_phi, this.cos_phi);
            this.ml2 = MathUtils.mlfn(this.e0, this.e1, this.e2, this.e3, lat2);
            this.ns = (this.ms1 - this.ms2) / (this.ml2 - this.ml1);
        }
        this.g = this.ml1 + this.ms1 / this.ns;
        this.ml0 = MathUtils.mlfn(this.e0, this.e1, this.e2, this.e3, proj.lat0);
        this.rh = proj.a * (this.g - this.ml0);
    }
    
    /**
     * Forward transformation: geographic to projected coordinates.
     * @param proj the projection instance
     * @param point the point to transform
     * @return the transformed point
     */
    public Point forwardTransform(Projection proj, Point point) {
        double lon = point.x;
        double lat = point.y;
        double rh1;
        
        // Forward equations
        if (proj.sphere) {
            rh1 = proj.a * (this.g - lat);
        } else {
            double ml = MathUtils.mlfn(this.e0, this.e1, this.e2, this.e3, lat);
            rh1 = proj.a * (this.g - ml);
        }
        double theta = this.ns * MathUtils.adjustLon(lon - proj.long0);
        double x = proj.x0 + rh1 * Math.sin(theta);
        double y = proj.y0 + this.rh - rh1 * Math.cos(theta);
        
        return new Point(x, y);
    }
    
    /**
     * Inverse transformation: projected to geographic coordinates.
     * @param proj the projection instance
     * @param point the point to transform
     * @return the transformed point
     */
    public Point inverseTransform(Projection proj, Point point) {
        double x = point.x - proj.x0;
        double y = this.rh - point.y + proj.y0;
        double con, rh1, lat, lon;
        
        if (this.ns >= 0) {
            rh1 = Math.sqrt(x * x + y * y);
            con = 1;
        } else {
            rh1 = -Math.sqrt(x * x + y * y);
            con = -1;
        }
        
        double theta = 0;
        if (rh1 != 0) {
            theta = Math.atan2(con * x, con * y);
        }
        
        if (proj.sphere) {
            lon = MathUtils.adjustLon(proj.long0 + theta / this.ns);
            lat = this.g - rh1 / proj.a;
        } else {
            double ml = this.g - rh1 / proj.a;
            lat = MathUtils.invMlfn(ml, this.es, this.en);
            lon = MathUtils.adjustLon(proj.long0 + theta / this.ns);
        }
        
        return new Point(lon, lat);
    }
    
    /**
     * Static initialization method for the projection.
     * @param proj the projection instance
     */
    public static void init(Projection proj) {
        EquidistantConic eqdc = new EquidistantConic();
        eqdc.initialize(proj);
        proj.eqdc = eqdc;
    }
    
    /**
     * Static forward transformation method.
     * @param proj the projection instance
     * @param point the point to transform
     * @return the transformed point
     */
    public static Point forward(Projection proj, Point point) {
        return ((EquidistantConic) proj.eqdc).forwardTransform(proj, point);
    }
    
    /**
     * Static inverse transformation method.
     * @param proj the projection instance
     * @param point the point to transform
     * @return the transformed point
     */
    public static Point inverse(Projection proj, Point point) {
        return ((EquidistantConic) proj.eqdc).inverseTransform(proj, point);
    }
}
