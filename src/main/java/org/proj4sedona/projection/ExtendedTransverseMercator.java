package org.proj4sedona.projection;

import org.proj4sedona.common.ProjMath;
import org.proj4sedona.core.Point;

/**
 * Extended Transverse Mercator projection implementation.
 * Mirrors: lib/projections/etmerc.js
 * 
 * <p>This is the more accurate version of the Transverse Mercator projection,
 * based on the Krüger-n series expansion. It is used by UTM and provides
 * higher accuracy than the traditional tmerc implementation.</p>
 * 
 * <p>Based on the mapshaper-proj implementation:
 * https://github.com/mbloch/mapshaper-proj/blob/master/src/projections/etmerc.js</p>
 * 
 * <p>Registered names:</p>
 * <ul>
 *   <li>etmerc - Extended Transverse Mercator</li>
 *   <li>tmerc - Transverse Mercator (defaults to etmerc for accuracy)</li>
 *   <li>Transverse_Mercator</li>
 *   <li>Gauss_Kruger, Gauss Kruger</li>
 * </ul>
 * 
 * <p>When the {@code +approx} flag is set, falls back to the simpler (faster but
 * less accurate) traditional Transverse Mercator algorithm.</p>
 */
public class ExtendedTransverseMercator implements Projection {

    private static final String[] NAMES = {
        "Extended_Transverse_Mercator", "Extended Transverse Mercator", 
        "etmerc", "Transverse_Mercator", "Transverse Mercator", 
        "Gauss Kruger", "Gauss_Kruger", "tmerc"
    };

    // Projection parameters
    protected double a;      // Semi-major axis
    protected double es;     // Eccentricity squared
    protected double ep2;    // Second eccentricity squared
    protected double k0;     // Scale factor
    protected double x0;     // False easting
    protected double y0;     // False northing
    protected double long0;  // Central meridian (radians)
    protected double lat0;   // Latitude of origin (radians)
    protected Boolean over;  // Allow longitude output outside -180 to 180
    protected Boolean approx; // Use approximate (traditional tmerc) algorithm

    // Extended TM coefficients (Krüger series)
    protected double[] cgb;  // Conformal to geodetic latitude coefficients
    protected double[] cbg;  // Geodetic to conformal latitude coefficients
    protected double[] utg;  // ξ,η to φ,λ coefficients
    protected double[] gtu;  // φ,λ to ξ,η coefficients
    protected double Qn;     // Meridian quadrant scale factor
    protected double Zb;     // Meridional arc constant

    // For approximate mode (fallback to simple tmerc)
    protected double[] en;   // Meridional distance coefficients
    protected double ml0;    // Meridional distance at lat0
    protected boolean useApprox = false;

    @Override
    public String[] getNames() {
        return NAMES;
    }

    @Override
    public void init(ProjectionParams params) {
        this.a = params.a;
        this.es = params.es;
        this.ep2 = params.ep2;
        this.k0 = params.k0;
        this.x0 = params.x0;
        this.y0 = params.y0;
        this.long0 = params.getLong0();
        this.lat0 = params.getLat0();
        this.over = params.over;
        this.approx = params.approx;

        // Check if we should use approximate (simple tmerc) mode
        if (Boolean.TRUE.equals(approx) || Double.isNaN(es) || es <= 0) {
            useApprox = true;
            if (es > 0) {
                en = ProjMath.pjEnfn(es);
                ml0 = ProjMath.pjMlfn(lat0, Math.sin(lat0), Math.cos(lat0), en);
            }
            return;
        }

        // Initialize extended TM coefficients using Krüger series
        cgb = new double[6];
        cbg = new double[6];
        utg = new double[6];
        gtu = new double[6];

        double f = es / (1 + Math.sqrt(1 - es));
        double n = f / (2 - f);
        double np = n;

        // Compute Krüger series coefficients
        cgb[0] = n * (2 + n * (-2.0/3 + n * (-2 + n * (116.0/45 + n * (26.0/45 + n * (-2854.0/675))))));
        cbg[0] = n * (-2 + n * (2.0/3 + n * (4.0/3 + n * (-82.0/45 + n * (32.0/45 + n * (4642.0/4725))))));
        np *= n;
        cgb[1] = np * (7.0/3 + n * (-8.0/5 + n * (-227.0/45 + n * (2704.0/315 + n * (2323.0/945)))));
        cbg[1] = np * (5.0/3 + n * (-16.0/15 + n * (-13.0/9 + n * (904.0/315 + n * (-1522.0/945)))));
        np *= n;
        cgb[2] = np * (56.0/15 + n * (-136.0/35 + n * (-1262.0/105 + n * (73814.0/2835))));
        cbg[2] = np * (-26.0/15 + n * (34.0/21 + n * (8.0/5 + n * (-12686.0/2835))));
        np *= n;
        cgb[3] = np * (4279.0/630 + n * (-332.0/35 + n * (-399572.0/14175)));
        cbg[3] = np * (1237.0/630 + n * (-12.0/5 + n * (-24832.0/14175)));
        np *= n;
        cgb[4] = np * (4174.0/315 + n * (-144838.0/6237));
        cbg[4] = np * (-734.0/315 + n * (109598.0/31185));
        np *= n;
        cgb[5] = np * (601676.0/22275);
        cbg[5] = np * (444337.0/155925);

        np = Math.pow(n, 2);
        Qn = k0 / (1 + n) * (1 + np * (1.0/4 + np * (1.0/64 + np / 256)));

        utg[0] = n * (-0.5 + n * (2.0/3 + n * (-37.0/96 + n * (1.0/360 + n * (81.0/512 + n * (-96199.0/604800))))));
        gtu[0] = n * (0.5 + n * (-2.0/3 + n * (5.0/16 + n * (41.0/180 + n * (-127.0/288 + n * (7891.0/37800))))));
        utg[1] = np * (-1.0/48 + n * (-1.0/15 + n * (437.0/1440 + n * (-46.0/105 + n * (1118711.0/3870720)))));
        gtu[1] = np * (13.0/48 + n * (-3.0/5 + n * (557.0/1440 + n * (281.0/630 + n * (-1983433.0/1935360)))));
        np *= n;
        utg[2] = np * (-17.0/480 + n * (37.0/840 + n * (209.0/4480 + n * (-5569.0/90720))));
        gtu[2] = np * (61.0/240 + n * (-103.0/140 + n * (15061.0/26880 + n * (167603.0/181440))));
        np *= n;
        utg[3] = np * (-4397.0/161280 + n * (11.0/504 + n * (830251.0/7257600)));
        gtu[3] = np * (49561.0/161280 + n * (-179.0/168 + n * (6601661.0/7257600)));
        np *= n;
        utg[4] = np * (-4583.0/161280 + n * (108847.0/3991680));
        gtu[4] = np * (34729.0/80640 + n * (-3418889.0/1995840));
        np *= n;
        utg[5] = np * (-20648693.0/638668800);
        gtu[5] = np * (212378941.0/319334400);

        double Z = ProjMath.gatg(cbg, lat0);
        Zb = -Qn * (Z + ProjMath.clens(gtu, 2 * Z));
    }

    /**
     * Forward projection: geographic (lon/lat in radians) to projected (x/y in meters).
     */
    @Override
    public Point forward(Point p) {
        if (useApprox) {
            return forwardApprox(p);
        }

        double Ce = ProjMath.adjustLon(p.x - long0, over);
        double Cn = p.y;

        Cn = ProjMath.gatg(cbg, Cn);
        double sinCn = Math.sin(Cn);
        double cosCn = Math.cos(Cn);
        double sinCe = Math.sin(Ce);
        double cosCe = Math.cos(Ce);

        Cn = Math.atan2(sinCn, cosCe * cosCn);
        Ce = Math.atan2(sinCe * cosCn, Math.hypot(sinCn, cosCn * cosCe));
        Ce = ProjMath.asinhy(Math.tan(Ce));

        double[] tmp = ProjMath.clensCmplx(gtu, 2 * Cn, 2 * Ce);
        Cn += tmp[0];
        Ce += tmp[1];

        double x, y;
        if (Math.abs(Ce) <= 2.623395162778) {
            x = a * (Qn * Ce) + x0;
            y = a * (Qn * Cn + Zb) + y0;
        } else {
            x = Double.POSITIVE_INFINITY;
            y = Double.POSITIVE_INFINITY;
        }

        return new Point(x, y, p.z);
    }

    /**
     * Forward projection using approximate (traditional tmerc) algorithm.
     */
    private Point forwardApprox(Point p) {
        double lon = p.x;
        double lat = p.y;
        double deltaLon = ProjMath.adjustLon(lon - long0, over);
        double sinPhi = Math.sin(lat);
        double cosPhi = Math.cos(lat);
        double x, y;

        if (es == 0 || en == null) {
            // Spherical case
            double b = cosPhi * Math.sin(deltaLon);
            if (Math.abs(Math.abs(b) - 1) < 1e-10) {
                return new Point(Double.NaN, Double.NaN, p.z);
            }
            x = 0.5 * a * k0 * Math.log((1 + b) / (1 - b)) + x0;
            double temp = cosPhi * Math.cos(deltaLon) / Math.sqrt(1 - b * b);
            y = (temp >= 1) ? 0 : (temp <= -1) ? Math.PI : Math.acos(temp);
            if (lat < 0) y = -y;
            y = a * k0 * (y - lat0) + y0;
        } else {
            // Ellipsoidal case
            double al = cosPhi * deltaLon;
            double als = al * al;
            double c = ep2 * cosPhi * cosPhi;
            double cs = c * c;
            double tq = Math.abs(cosPhi) > 1e-10 ? Math.tan(lat) : 0;
            double t = tq * tq;
            double ts = t * t;
            double con = 1 - es * sinPhi * sinPhi;
            al = al / Math.sqrt(con);
            double ml = ProjMath.pjMlfn(lat, sinPhi, cosPhi, en);

            x = a * (k0 * al * (1 + als / 6 * (1 - t + c + als / 20 * (5 - 18 * t + ts + 14 * c - 58 * t * c + als / 42 * (61 + 179 * ts - ts * t - 479 * t))))) + x0;
            y = a * (k0 * (ml - ml0 + sinPhi * deltaLon * al / 2 * (1 + als / 12 * (5 - t + 9 * c + 4 * cs + als / 30 * (61 + ts - 58 * t + 270 * c - 330 * t * c + als / 56 * (1385 + 543 * ts - ts * t - 3111 * t)))))) + y0;
        }

        return new Point(x, y, p.z);
    }

    /**
     * Inverse projection: projected (x/y in meters) to geographic (lon/lat in radians).
     */
    @Override
    public Point inverse(Point p) {
        if (useApprox) {
            return inverseApprox(p);
        }

        double Ce = (p.x - x0) / a;
        double Cn = (p.y - y0) / a;

        Cn = (Cn - Zb) / Qn;
        Ce = Ce / Qn;

        double lon, lat;
        if (Math.abs(Ce) <= 2.623395162778) {
            double[] tmp = ProjMath.clensCmplx(utg, 2 * Cn, 2 * Ce);
            Cn += tmp[0];
            Ce += tmp[1];
            Ce = Math.atan(ProjMath.sinh(Ce));

            double sinCn = Math.sin(Cn);
            double cosCn = Math.cos(Cn);
            double sinCe = Math.sin(Ce);
            double cosCe = Math.cos(Ce);

            Cn = Math.atan2(sinCn * cosCe, Math.hypot(sinCe, cosCe * cosCn));
            Ce = Math.atan2(sinCe, cosCe * cosCn);

            lon = ProjMath.adjustLon(Ce + long0, over);
            lat = ProjMath.gatg(cgb, Cn);
        } else {
            lon = Double.POSITIVE_INFINITY;
            lat = Double.POSITIVE_INFINITY;
        }

        return new Point(lon, lat, p.z);
    }

    /**
     * Inverse projection using approximate (traditional tmerc) algorithm.
     */
    private Point inverseApprox(Point p) {
        double x = (p.x - x0) / a;
        double y = (p.y - y0) / a;
        double lon, lat;

        if (es == 0 || en == null) {
            // Spherical case
            double f = Math.exp(x / k0);
            double g = 0.5 * (f - 1 / f);
            double temp = lat0 + y / k0;
            double h = Math.cos(temp);
            double con = Math.sqrt((1 - h * h) / (1 + g * g));
            lat = ProjMath.asinz(con);
            if (y < 0) lat = -lat;
            lon = (g == 0 && h == 0) ? 0 : ProjMath.adjustLon(Math.atan2(g, h) + long0, over);
        } else {
            // Ellipsoidal case
            double con = ml0 + y / k0;
            double phi = ProjMath.pjInvMlfn(con, es, en);

            if (Math.abs(phi) < Math.PI / 2) {
                double sinPhi = Math.sin(phi);
                double cosPhi = Math.cos(phi);
                double tanPhi = Math.abs(cosPhi) > 1e-10 ? Math.tan(phi) : 0;
                double c = ep2 * cosPhi * cosPhi;
                double cs = c * c;
                double t = tanPhi * tanPhi;
                double ts = t * t;
                con = 1 - es * sinPhi * sinPhi;
                double d = x * Math.sqrt(con) / k0;
                double ds = d * d;
                con = con * tanPhi;

                lat = phi - (con * ds / (1 - es)) * 0.5 * (1 - ds / 12 * (5 + 3 * t - 9 * c * t + c - 4 * cs - ds / 30 * (61 + 90 * t - 252 * c * t + 45 * ts + 46 * c - ds / 56 * (1385 + 3633 * t + 4095 * ts + 1574 * ts * t))));
                lon = ProjMath.adjustLon(long0 + (d * (1 - ds / 6 * (1 + 2 * t + c - ds / 20 * (5 + 28 * t + 24 * ts + 8 * c * t + 6 * c - ds / 42 * (61 + 662 * t + 1320 * ts + 720 * ts * t)))) / cosPhi), over);
            } else {
                lat = Math.PI / 2 * ProjMath.sign(y);
                lon = 0;
            }
        }

        return new Point(lon, lat, p.z);
    }
}
