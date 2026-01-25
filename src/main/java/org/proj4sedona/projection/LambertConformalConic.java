package org.proj4sedona.projection;

import org.proj4sedona.common.ProjMath;
import org.proj4sedona.constants.Values;
import org.proj4sedona.core.Point;

/**
 * Lambert Conformal Conic projection implementation.
 * Mirrors: lib/projections/lcc.js
 * 
 * <p>A conic projection that preserves angles (conformal). Commonly used for
 * mid-latitude regions with east-west extent, such as the US State Plane system.</p>
 * 
 * <p>Supports both 1SP (single standard parallel) and 2SP (two standard parallels) variants.</p>
 */
public class LambertConformalConic implements Projection {

    private static final String[] NAMES = {
        "Lambert Tangential Conformal Conic Projection",
        "Lambert_Conformal_Conic", "Lambert_Conformal_Conic_1SP",
        "Lambert_Conformal_Conic_2SP", "lcc",
        "Lambert Conic Conformal (1SP)", "Lambert Conic Conformal (2SP)"
    };

    private double a, e, es, lat0, lat1, lat2, long0, k0, x0, y0;
    private double ns, f0, rh;
    private Boolean over;

    @Override
    public String[] getNames() { return NAMES; }

    @Override
    public void init(ProjectionParams params) {
        this.a = params.a;
        this.es = params.es;
        this.e = Math.sqrt(es);
        this.lat0 = params.getLat0();
        this.lat1 = params.getLat1();
        this.lat2 = params.getLat2();
        this.long0 = params.getLong0();
        this.k0 = params.k0;
        this.x0 = params.x0;
        this.y0 = params.y0;
        this.over = params.over;

        // Standard parallels cannot be equal and on opposite sides of equator
        if (Math.abs(lat1 + lat2) < Values.EPSLN) {
            return;
        }

        double sin1 = Math.sin(lat1);
        double cos1 = Math.cos(lat1);
        double ms1 = ProjMath.msfnz(e, sin1, cos1);
        double ts1 = ProjMath.tsfnz(e, lat1, sin1);

        double sin2 = Math.sin(lat2);
        double cos2 = Math.cos(lat2);
        double ms2 = ProjMath.msfnz(e, sin2, cos2);
        double ts2 = ProjMath.tsfnz(e, lat2, sin2);

        double ts0 = Math.abs(Math.abs(lat0) - Values.HALF_PI) < Values.EPSLN
            ? 0 : ProjMath.tsfnz(e, lat0, Math.sin(lat0));

        if (Math.abs(lat1 - lat2) > Values.EPSLN) {
            ns = Math.log(ms1 / ms2) / Math.log(ts1 / ts2);
        } else {
            ns = sin1;
        }
        if (Double.isNaN(ns)) {
            ns = sin1;
        }
        f0 = ms1 / (ns * Math.pow(ts1, ns));
        rh = a * f0 * Math.pow(ts0, ns);
    }

    @Override
    public Point forward(Point p) {
        double lon = p.x;
        double lat = p.y;

        // Singular cases near poles
        if (Math.abs(2 * Math.abs(lat) - Math.PI) <= Values.EPSLN) {
            lat = ProjMath.sign(lat) * (Values.HALF_PI - 2 * Values.EPSLN);
        }

        double con = Math.abs(Math.abs(lat) - Values.HALF_PI);
        double ts, rh1;
        if (con > Values.EPSLN) {
            ts = ProjMath.tsfnz(e, lat, Math.sin(lat));
            rh1 = a * f0 * Math.pow(ts, ns);
        } else {
            con = lat * ns;
            if (con <= 0) {
                return null;
            }
            rh1 = 0;
        }
        double theta = ns * ProjMath.adjustLon(lon - long0, over);
        double x = k0 * (rh1 * Math.sin(theta)) + x0;
        double y = k0 * (rh - rh1 * Math.cos(theta)) + y0;

        return new Point(x, y, p.z);
    }

    @Override
    public Point inverse(Point p) {
        double x = (p.x - x0) / k0;
        double y = (rh - (p.y - y0) / k0);
        double rh1, con;

        if (ns > 0) {
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

        double lat, lon;
        if (rh1 != 0 || ns > 0) {
            con = 1 / ns;
            double ts = Math.pow(rh1 / (a * f0), con);
            lat = ProjMath.phi2z(e, ts);
            if (lat == -9999) {
                return null;
            }
        } else {
            lat = -Values.HALF_PI;
        }
        lon = ProjMath.adjustLon(theta / ns + long0, over);

        return new Point(lon, lat, p.z);
    }
}
