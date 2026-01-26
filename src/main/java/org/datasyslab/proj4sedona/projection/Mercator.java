package org.datasyslab.proj4sedona.projection;

import org.datasyslab.proj4sedona.common.ProjMath;
import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.Point;

/**
 * Mercator projection.
 * Mirrors: lib/projections/merc.js
 * 
 * The Mercator projection is a cylindrical map projection that became
 * the standard map projection for navigation because of its ability
 * to represent lines of constant course as straight line segments.
 */
public class Mercator implements Projection {

    public static final String[] NAMES = {
        "Mercator", 
        "Popular Visualisation Pseudo Mercator", 
        "Mercator_1SP", 
        "Mercator_Auxiliary_Sphere", 
        "Mercator_Variant_A", 
        "merc"
    };

    // Parameters
    private double a;           // Semi-major axis
    private double e;           // Eccentricity
    private double es;          // Eccentricity squared
    private double k0;          // Scale factor
    private double x0;          // False easting
    private double y0;          // False northing
    private double long0;       // Central meridian
    private Double latTs;       // Latitude of true scale
    private boolean sphere;     // True if sphere
    private Boolean over;       // Longitude wrapping

    @Override
    public String[] getNames() {
        return NAMES;
    }

    @Override
    public void init(ProjectionParams params) {
        this.a = params.a;
        this.e = params.e;
        this.x0 = params.x0;
        this.y0 = params.y0;
        this.long0 = params.getLong0();
        this.latTs = params.latTs;
        this.sphere = params.sphere;
        this.over = params.over;

        // Calculate eccentricity from a and b
        // Mirrors: lib/projections/merc.js lines 17-18
        double con = params.b / params.a;
        this.es = 1 - con * con;
        this.e = Math.sqrt(this.es);

        // Determine k0 based on lat_ts or explicit k0
        // Mirrors: lib/projections/merc.js lines 26-40
        if (latTs != null) {
            if (sphere) {
                this.k0 = Math.cos(latTs);
            } else {
                this.k0 = ProjMath.msfnz(this.e, Math.sin(latTs), Math.cos(latTs));
            }
        } else {
            this.k0 = params.k0;
        }
    }

    /**
     * Mercator forward equations - mapping lat,long to x,y.
     * Mirrors: lib/projections/merc.js forward()
     */
    @Override
    public Point forward(Point p) {
        double lon = p.x;
        double lat = p.y;

        // NOTE: This validation is inherited from proj4js (merc.js lines 50-52).
        // The logic appears inverted: it checks if lat is BETWEEN 90 and -90 AND
        // lon is BETWEEN 180 and -180, which would never be true for valid coords.
        // This effectively makes the check a no-op. We preserve this behavior for
        // compatibility, but the real validation happens in the pole check below.
        if (lat * Values.R2D > 90 && lat * Values.R2D < -90 && 
            lon * Values.R2D > 180 && lon * Values.R2D < -180) {
            return null;
        }

        // Check for pole - Mercator is undefined at the poles
        // Mirrors: lib/projections/merc.js lines 55-56
        if (Math.abs(Math.abs(lat) - Values.HALF_PI) <= Values.EPSLN) {
            return null;
        }

        double x, y;
        if (sphere) {
            // Spherical case
            // Mirrors: lib/projections/merc.js lines 59-60
            x = x0 + a * k0 * ProjMath.adjustLon(lon - long0, over);
            y = y0 + a * k0 * Math.log(Math.tan(Values.FORTPI + 0.5 * lat));
        } else {
            // Ellipsoidal case
            // Mirrors: lib/projections/merc.js lines 62-65
            double sinphi = Math.sin(lat);
            double ts = ProjMath.tsfnz(e, lat, sinphi);
            x = x0 + a * k0 * ProjMath.adjustLon(lon - long0, over);
            y = y0 - a * k0 * Math.log(ts);
        }

        p.x = x;
        p.y = y;
        return p;
    }

    /**
     * Mercator inverse equations - mapping x,y to lat/long.
     * Mirrors: lib/projections/merc.js inverse()
     */
    @Override
    public Point inverse(Point p) {
        double x = p.x - x0;
        double y = p.y - y0;
        double lon, lat;

        if (sphere) {
            // Spherical case
            // Mirrors: lib/projections/merc.js line 81
            lat = Values.HALF_PI - 2 * Math.atan(Math.exp(-y / (a * k0)));
        } else {
            // Ellipsoidal case
            // Mirrors: lib/projections/merc.js lines 83-87
            double ts = Math.exp(-y / (a * k0));
            lat = ProjMath.phi2z(e, ts);
            if (lat == -9999) {
                // No convergence in phi2z iteration
                return null;
            }
        }

        lon = ProjMath.adjustLon(long0 + x / (a * k0), over);

        p.x = lon;
        p.y = lat;
        return p;
    }
}
