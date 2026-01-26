package org.datasyslab.proj4sedona.projection;

import org.datasyslab.proj4sedona.common.ProjMath;
import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.Point;

/**
 * Cylindrical Equal Area projection implementation.
 * Mirrors: lib/projections/cea.js
 * 
 * <p>A cylindrical projection that preserves area. Also known as Lambert
 * Cylindrical Equal Area when lat_ts=0.</p>
 */
public class CylindricalEqualArea implements Projection {

    private static final String[] NAMES = {
        "Cylindrical_Equal_Area", "cea", "Lambert_Cylindrical_Equal_Area"
    };

    private double a, es, e, lat0, latTs, long0, x0, y0;
    private double k0, qp;
    private double[] apa;
    private boolean sphere;
    private Boolean over;

    // Authalic latitude coefficients
    private static final double P00 = 0.33333333333333333333;
    private static final double P01 = 0.17222222222222222222;
    private static final double P02 = 0.10257936507936507936;
    private static final double P10 = 0.06388888888888888888;
    private static final double P11 = 0.06640211640211640211;
    private static final double P20 = 0.01641501294219154443;

    @Override
    public String[] getNames() { return NAMES; }

    @Override
    public void init(ProjectionParams params) {
        this.a = params.a;
        this.es = params.es;
        this.e = Math.sqrt(es);
        this.lat0 = params.getLat0();
        this.latTs = params.getLatTs();
        this.long0 = params.getLong0();
        this.x0 = params.x0;
        this.y0 = params.y0;
        this.sphere = params.sphere;
        this.over = params.over;

        if (sphere) {
            k0 = Math.cos(latTs);
        } else {
            k0 = ProjMath.msfnz(e, Math.sin(latTs), Math.cos(latTs));
            qp = ProjMath.qsfnz(e, 1);
            apa = authset(es);
        }
    }

    private double[] authset(double es) {
        double[] APA = new double[3];
        APA[0] = es * P00;
        double t = es * es;
        APA[0] += t * P01;
        APA[1] = t * P10;
        t *= es;
        APA[0] += t * P02;
        APA[1] += t * P11;
        APA[2] = t * P20;
        return APA;
    }

    private double authlat(double beta, double[] APA) {
        double t = beta + beta;
        return beta + APA[0] * Math.sin(t) + APA[1] * Math.sin(t + t) + APA[2] * Math.sin(t + t + t);
    }

    @Override
    public Point forward(Point p) {
        double lon = ProjMath.adjustLon(p.x - long0, over);
        double lat = p.y;
        double x, y;

        if (sphere) {
            x = a * k0 * lon + x0;
            y = a * Math.sin(lat) / k0 + y0;
        } else {
            double q = ProjMath.qsfnz(e, Math.sin(lat));
            x = a * k0 * lon + x0;
            y = 0.5 * a * q / k0 + y0;
        }

        return new Point(x, y, p.z);
    }

    @Override
    public Point inverse(Point p) {
        double x = p.x - x0;
        double y = p.y - y0;
        double lon, lat;

        if (sphere) {
            lon = ProjMath.adjustLon(x / (a * k0) + long0, over);
            lat = ProjMath.asinz(y * k0 / a);
        } else {
            double beta = ProjMath.asinz(2 * y * k0 / (a * qp));
            lat = authlat(beta, apa);
            lon = ProjMath.adjustLon(x / (a * k0) + long0, over);
        }

        return new Point(lon, lat, p.z);
    }
}
