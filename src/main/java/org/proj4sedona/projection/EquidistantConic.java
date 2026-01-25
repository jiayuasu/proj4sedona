package org.proj4sedona.projection;

import org.proj4sedona.common.ProjMath;
import org.proj4sedona.constants.Values;
import org.proj4sedona.core.Point;

/**
 * Equidistant Conic projection implementation.
 * Mirrors: lib/projections/eqdc.js
 * 
 * <p>A conic projection that preserves distances along meridians and standard parallels.
 * Useful for mapping regions with east-west extent.</p>
 */
public class EquidistantConic implements Projection {

    private static final String[] NAMES = {"Equidistant_Conic", "eqdc"};

    private double a, e, es, lat0, lat1, lat2, long0, x0, y0;
    private double e0, e1, e2, e3, ns, g, rh;
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
        this.lat1 = params.getLat1();
        this.lat2 = params.getLat2();
        this.long0 = params.getLong0();
        this.x0 = params.x0;
        this.y0 = params.y0;
        this.sphere = params.sphere;
        this.over = params.over;

        if (Math.abs(lat1 + lat2) < Values.EPSLN) {
            return;
        }

        e0 = ProjMath.e0fn(es);
        e1 = ProjMath.e1fn(es);
        e2 = ProjMath.e2fn(es);
        e3 = ProjMath.e3fn(es);

        double sinPhi = Math.sin(lat1);
        double cosPhi = Math.cos(lat1);
        double ms1 = ProjMath.msfnz(e, sinPhi, cosPhi);
        double ml1 = ProjMath.mlfn(e0, e1, e2, e3, lat1);

        if (Math.abs(lat1 - lat2) < Values.EPSLN) {
            ns = sinPhi;
        } else {
            sinPhi = Math.sin(lat2);
            cosPhi = Math.cos(lat2);
            double ms2 = ProjMath.msfnz(e, sinPhi, cosPhi);
            double ml2 = ProjMath.mlfn(e0, e1, e2, e3, lat2);
            ns = (ms1 - ms2) / (ml2 - ml1);
        }
        g = ml1 + ms1 / ns;
        double ml0 = ProjMath.mlfn(e0, e1, e2, e3, lat0);
        rh = a * (g - ml0);
    }

    @Override
    public Point forward(Point p) {
        double lon = p.x;
        double lat = p.y;
        double rh1;

        if (sphere) {
            rh1 = a * (g - lat);
        } else {
            double ml = ProjMath.mlfn(e0, e1, e2, e3, lat);
            rh1 = a * (g - ml);
        }
        double theta = ns * ProjMath.adjustLon(lon - long0, over);
        double x = x0 + rh1 * Math.sin(theta);
        double y = y0 + rh - rh1 * Math.cos(theta);

        return new Point(x, y, p.z);
    }

    @Override
    public Point inverse(Point p) {
        double x = p.x - x0;
        double y = rh - p.y + y0;
        double rh1, con;

        if (ns >= 0) {
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
        if (sphere) {
            lon = ProjMath.adjustLon(long0 + theta / ns, over);
            lat = ProjMath.adjustLat(g - rh1 / a);
        } else {
            double ml = g - rh1 / a;
            lat = ProjMath.imlfn(ml, e0, e1, e2, e3);
            lon = ProjMath.adjustLon(long0 + theta / ns, over);
        }

        return new Point(lon, lat, p.z);
    }
}
