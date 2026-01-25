package org.proj4sedona.projection;

import org.proj4sedona.common.ProjMath;
import org.proj4sedona.constants.Values;
import org.proj4sedona.core.Point;

/**
 * Albers Equal Area Conic projection implementation.
 * Mirrors: lib/projections/aea.js
 * 
 * <p>A conic projection that preserves area. Commonly used for thematic maps
 * and statistical analysis where accurate area representation is important.</p>
 */
public class AlbersEqualArea implements Projection {

    private static final String[] NAMES = {
        "Albers_Conic_Equal_Area", "Albers_Equal_Area", "Albers", "aea"
    };

    private double a, e3, es, lat0, lat1, lat2, long0, x0, y0;
    private double ns0, c, rh;
    private boolean sphere;
    private Boolean over;

    @Override
    public String[] getNames() { return NAMES; }

    @Override
    public void init(ProjectionParams params) {
        this.a = params.a;
        this.es = params.es;
        this.e3 = Math.sqrt(es);
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

        double sinPo = Math.sin(lat1);
        double cosPo = Math.cos(lat1);
        double ms1 = ProjMath.msfnz(e3, sinPo, cosPo);
        double qs1 = ProjMath.qsfnz(e3, sinPo);

        sinPo = Math.sin(lat2);
        cosPo = Math.cos(lat2);
        double ms2 = ProjMath.msfnz(e3, sinPo, cosPo);
        double qs2 = ProjMath.qsfnz(e3, sinPo);

        sinPo = Math.sin(lat0);
        double qs0 = ProjMath.qsfnz(e3, sinPo);

        if (Math.abs(lat1 - lat2) > Values.EPSLN) {
            ns0 = (ms1 * ms1 - ms2 * ms2) / (qs2 - qs1);
        } else {
            ns0 = Math.sin(lat1);
        }
        c = ms1 * ms1 + ns0 * qs1;
        rh = a * Math.sqrt(c - ns0 * qs0) / ns0;
    }

    @Override
    public Point forward(Point p) {
        double lon = p.x;
        double lat = p.y;

        double sinPhi = Math.sin(lat);
        double qs = ProjMath.qsfnz(e3, sinPhi);
        double rh1 = a * Math.sqrt(c - ns0 * qs) / ns0;
        double theta = ns0 * ProjMath.adjustLon(lon - long0, over);
        double x = rh1 * Math.sin(theta) + x0;
        double y = rh - rh1 * Math.cos(theta) + y0;

        return new Point(x, y, p.z);
    }

    @Override
    public Point inverse(Point p) {
        double x = p.x - x0;
        double y = rh - p.y + y0;
        double rh1, con;

        if (ns0 >= 0) {
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

        con = rh1 * ns0 / a;
        double lat;
        if (sphere) {
            lat = Math.asin((c - con * con) / (2 * ns0));
        } else {
            double qs = (c - con * con) / ns0;
            Double latResult = phi1z(e3, qs);
            if (latResult == null) {
                return null;
            }
            lat = latResult;
        }

        double lon = ProjMath.adjustLon(theta / ns0 + long0, over);
        return new Point(lon, lat, p.z);
    }

    /**
     * Compute phi1 for inverse Albers.
     */
    private Double phi1z(double eccent, double qs) {
        double phi = ProjMath.asinz(0.5 * qs);
        if (eccent < Values.EPSLN) {
            return phi;
        }

        double eccnts = eccent * eccent;
        for (int i = 1; i <= 25; i++) {
            double sinphi = Math.sin(phi);
            double cosphi = Math.cos(phi);
            double con = eccent * sinphi;
            double com = 1 - con * con;
            double dphi = 0.5 * com * com / cosphi * 
                (qs / (1 - eccnts) - sinphi / com + 0.5 / eccent * Math.log((1 - con) / (1 + con)));
            phi = phi + dphi;
            if (Math.abs(dphi) <= 1e-7) {
                return phi;
            }
        }
        return null;
    }
}
