package org.datasyslab.proj4sedona.projection;

import org.datasyslab.proj4sedona.common.ProjMath;
import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.Point;

/**
 * Robinson projection implementation.
 * Mirrors: lib/projections/robin.js
 * 
 * <p>A pseudocylindrical compromise projection that attempts to minimize
 * all distortions. Commonly used for world maps by National Geographic.</p>
 */
public class Robinson implements Projection {

    private static final String[] NAMES = {"Robinson", "robin"};

    private static final double[][] COEFS_X = {
        {1.0000, 2.2199e-17, -7.15515e-05, 3.1103e-06},
        {0.9986, -0.000482243, -2.4897e-05, -1.3309e-06},
        {0.9954, -0.00083103, -4.48605e-05, -9.86701e-07},
        {0.9900, -0.00135364, -5.9661e-05, 3.6777e-06},
        {0.9822, -0.00167442, -4.49547e-06, -5.72411e-06},
        {0.9730, -0.00214868, -9.03571e-05, 1.8736e-08},
        {0.9600, -0.00305085, -9.00761e-05, 1.64917e-06},
        {0.9427, -0.00382792, -6.53386e-05, -2.6154e-06},
        {0.9216, -0.00467746, -0.00010457, 4.81243e-06},
        {0.8962, -0.00536223, -3.23831e-05, -5.43432e-06},
        {0.8679, -0.00609363, -0.000113898, 3.32484e-06},
        {0.8350, -0.00698325, -6.40253e-05, 9.34959e-07},
        {0.7986, -0.00755338, -5.00009e-05, 9.35324e-07},
        {0.7597, -0.00798324, -3.5971e-05, -2.27626e-06},
        {0.7186, -0.00851367, -7.01149e-05, -8.6303e-06},
        {0.6732, -0.00986209, -0.000199569, 1.91974e-05},
        {0.6213, -0.010418, 8.83923e-05, 6.24051e-06},
        {0.5722, -0.00906601, 0.000182, 6.24051e-06},
        {0.5322, -0.00677797, 0.000275608, 6.24051e-06}
    };

    private static final double[][] COEFS_Y = {
        {-5.20417e-18, 0.0124, 1.21431e-18, -8.45284e-11},
        {0.0620, 0.0124, -1.26793e-09, 4.22642e-10},
        {0.1240, 0.0124, 5.07171e-09, -1.60604e-09},
        {0.1860, 0.0123999, -1.90189e-08, 6.00152e-09},
        {0.2480, 0.0124002, 7.10039e-08, -2.24e-08},
        {0.3100, 0.0123992, -2.64997e-07, 8.35986e-08},
        {0.3720, 0.0124029, 9.88983e-07, -3.11994e-07},
        {0.4340, 0.0123893, -3.69093e-06, -4.35621e-07},
        {0.4958, 0.0123198, -1.02252e-05, -3.45523e-07},
        {0.5571, 0.0121916, -1.54081e-05, -5.82288e-07},
        {0.6176, 0.0119938, -2.41424e-05, -5.25327e-07},
        {0.6769, 0.011713, -3.20223e-05, -5.16405e-07},
        {0.7346, 0.0113541, -3.97684e-05, -6.09052e-07},
        {0.7903, 0.0109107, -4.89042e-05, -1.04739e-06},
        {0.8435, 0.0103431, -6.4615e-05, -1.40374e-09},
        {0.8936, 0.00969686, -6.4636e-05, -8.547e-06},
        {0.9394, 0.00840947, -0.000192841, -4.2106e-06},
        {0.9761, 0.00616527, -0.000256, -4.2106e-06},
        {1.0000, 0.00328947, -0.000319159, -4.2106e-06}
    };

    private static final double FXC = 0.8487;
    private static final double FYC = 1.3523;
    private static final double C1 = Values.R2D / 5;  // rad to 5-degree interval
    private static final double RC1 = 1 / C1;
    private static final int NODES = 18;

    private double a, long0, x0, y0;
    private Boolean over;

    @Override
    public String[] getNames() { return NAMES; }

    @Override
    public void init(ProjectionParams params) {
        this.a = params.a;
        this.long0 = params.getLong0();
        this.x0 = params.x0;
        this.y0 = params.y0;
        this.over = params.over;
    }

    private double poly3Val(double[] coefs, double x) {
        return coefs[0] + x * (coefs[1] + x * (coefs[2] + x * coefs[3]));
    }

    private double poly3Der(double[] coefs, double x) {
        return coefs[1] + x * (2 * coefs[2] + x * 3 * coefs[3]);
    }

    @Override
    public Point forward(Point p) {
        double lon = ProjMath.adjustLon(p.x - long0, over);
        double dphi = Math.abs(p.y);
        int i = (int) Math.floor(dphi * C1);
        if (i < 0) i = 0;
        else if (i >= NODES) i = NODES - 1;

        dphi = Values.R2D * (dphi - RC1 * i);
        double x = poly3Val(COEFS_X[i], dphi) * lon;
        double y = poly3Val(COEFS_Y[i], dphi);
        if (p.y < 0) y = -y;

        x = x * a * FXC + x0;
        y = y * a * FYC + y0;

        return new Point(x, y, p.z);
    }

    @Override
    public Point inverse(Point p) {
        double x = (p.x - x0) / (a * FXC);
        double y = Math.abs(p.y - y0) / (a * FYC);
        double lon, lat;

        if (y >= 1) {
            // Pathologic case at poles
            lon = x / COEFS_X[NODES][0];
            lat = (p.y - y0 < 0) ? -Values.HALF_PI : Values.HALF_PI;
        } else {
            // Find table interval
            int i = (int) Math.floor(y * NODES);
            if (i < 0) i = 0;
            else if (i >= NODES) i = NODES - 1;

            while (true) {
                if (COEFS_Y[i][0] > y) {
                    i--;
                } else if (COEFS_Y[i + 1][0] <= y) {
                    i++;
                } else {
                    break;
                }
            }

            // Linear interpolation in 5-degree interval
            double[] coefs = COEFS_Y[i];
            double t = 5 * (y - coefs[0]) / (COEFS_Y[i + 1][0] - coefs[0]);

            // Newton-Raphson to find t
            for (int iter = 0; iter < 100; iter++) {
                double upd = (poly3Val(coefs, t) - y) / poly3Der(coefs, t);
                t -= upd;
                if (Math.abs(upd) < Values.EPSLN) break;
            }

            lon = x / poly3Val(COEFS_X[i], t);
            lat = (5 * i + t) * Values.D2R;
            if (p.y - y0 < 0) lat = -lat;
        }

        lon = ProjMath.adjustLon(lon + long0, over);
        return new Point(lon, lat, p.z);
    }
}
