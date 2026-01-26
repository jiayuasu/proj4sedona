package org.datasyslab.proj4sedona.projection;

import org.datasyslab.proj4sedona.common.ProjMath;
import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.Point;

/**
 * Azimuthal Equidistant projection implementation.
 * Mirrors: lib/projections/aeqd.js
 * 
 * <p>An azimuthal projection that preserves distances from the center point.
 * Commonly used for radio/telecommunications coverage maps and air route maps.</p>
 */
public class AzimuthalEquidistant implements Projection {

    private static final String[] NAMES = {"Azimuthal_Equidistant", "aeqd"};

    private double a, es, f, lat0, long0, x0, y0;
    private double sinP12, cosP12;
    private boolean sphere;
    private Boolean over;

    @Override
    public String[] getNames() { return NAMES; }

    @Override
    public void init(ProjectionParams params) {
        this.a = params.a;
        this.es = params.es;
        this.lat0 = params.getLat0();
        this.long0 = params.getLong0();
        this.x0 = params.x0;
        this.y0 = params.y0;
        this.sphere = params.sphere;
        this.over = params.over;

        sinP12 = Math.sin(lat0);
        cosP12 = Math.cos(lat0);
        f = es / (1 + Math.sqrt(1 - es));  // Flattening
    }

    @Override
    public Point forward(Point p) {
        double lon = p.x;
        double lat = p.y;
        double sinphi = Math.sin(lat);
        double cosphi = Math.cos(lat);
        double dlon = ProjMath.adjustLon(lon - long0, over);
        double x, y;

        if (sphere) {
            if (Math.abs(sinP12 - 1) <= Values.EPSLN) {
                // North Pole
                x = x0 + a * (Values.HALF_PI - lat) * Math.sin(dlon);
                y = y0 - a * (Values.HALF_PI - lat) * Math.cos(dlon);
            } else if (Math.abs(sinP12 + 1) <= Values.EPSLN) {
                // South Pole
                x = x0 + a * (Values.HALF_PI + lat) * Math.sin(dlon);
                y = y0 + a * (Values.HALF_PI + lat) * Math.cos(dlon);
            } else {
                double cosC = sinP12 * sinphi + cosP12 * cosphi * Math.cos(dlon);
                double c = Math.acos(cosC);
                double kp = (c != 0) ? c / Math.sin(c) : 1;
                x = x0 + a * kp * cosphi * Math.sin(dlon);
                y = y0 + a * kp * (cosP12 * sinphi - sinP12 * cosphi * Math.cos(dlon));
            }
        } else {
            double e0 = ProjMath.e0fn(es);
            double e1 = ProjMath.e1fn(es);
            double e2 = ProjMath.e2fn(es);
            double e3 = ProjMath.e3fn(es);

            if (Math.abs(sinP12 - 1) <= Values.EPSLN) {
                // North Pole
                double Mlp = a * ProjMath.mlfn(e0, e1, e2, e3, Values.HALF_PI);
                double Ml = a * ProjMath.mlfn(e0, e1, e2, e3, lat);
                x = x0 + (Mlp - Ml) * Math.sin(dlon);
                y = y0 - (Mlp - Ml) * Math.cos(dlon);
            } else if (Math.abs(sinP12 + 1) <= Values.EPSLN) {
                // South Pole
                double Mlp = a * ProjMath.mlfn(e0, e1, e2, e3, Values.HALF_PI);
                double Ml = a * ProjMath.mlfn(e0, e1, e2, e3, lat);
                x = x0 + (Mlp + Ml) * Math.sin(dlon);
                y = y0 + (Mlp + Ml) * Math.cos(dlon);
            } else {
                // General case - use Vincenty formulas
                if (Math.abs(lon) < Values.EPSLN && Math.abs(lat - lat0) < Values.EPSLN) {
                    return new Point(0, 0, p.z);
                }
                double[] vars = vincentyInverse(lat0, long0, lat, lon, a, f);
                double azi1 = vars[0];
                double s12 = vars[1];
                x = s12 * Math.sin(azi1);
                y = s12 * Math.cos(azi1);
            }
        }

        return new Point(x, y, p.z);
    }

    @Override
    public Point inverse(Point p) {
        double x = p.x - x0;
        double y = p.y - y0;
        double lon, lat;

        if (sphere) {
            double rh = Math.sqrt(x * x + y * y);
            if (rh > 2 * Values.HALF_PI * a) {
                return null;
            }
            double z = rh / a;
            double sinz = Math.sin(z);
            double cosz = Math.cos(z);

            lon = long0;
            if (Math.abs(rh) <= Values.EPSLN) {
                lat = lat0;
            } else {
                lat = ProjMath.asinz(cosz * sinP12 + y * sinz * cosP12 / rh);
                double con = Math.abs(lat0) - Values.HALF_PI;
                if (Math.abs(con) <= Values.EPSLN) {
                    lon = (lat0 >= 0) 
                        ? ProjMath.adjustLon(long0 + Math.atan2(x, -y), over)
                        : ProjMath.adjustLon(long0 - Math.atan2(-x, y), over);
                } else {
                    lon = ProjMath.adjustLon(long0 + Math.atan2(x * sinz, 
                        rh * cosP12 * cosz - y * sinP12 * sinz), over);
                }
            }
        } else {
            double e0 = ProjMath.e0fn(es);
            double e1 = ProjMath.e1fn(es);
            double e2 = ProjMath.e2fn(es);
            double e3 = ProjMath.e3fn(es);

            if (Math.abs(sinP12 - 1) <= Values.EPSLN) {
                // North Pole
                double Mlp = a * ProjMath.mlfn(e0, e1, e2, e3, Values.HALF_PI);
                double rh = Math.sqrt(x * x + y * y);
                double M = Mlp - rh;
                lat = ProjMath.imlfn(M / a, e0, e1, e2, e3);
                lon = ProjMath.adjustLon(long0 + Math.atan2(x, -y), over);
            } else if (Math.abs(sinP12 + 1) <= Values.EPSLN) {
                // South Pole
                double Mlp = a * ProjMath.mlfn(e0, e1, e2, e3, Values.HALF_PI);
                double rh = Math.sqrt(x * x + y * y);
                double M = rh - Mlp;
                lat = ProjMath.imlfn(M / a, e0, e1, e2, e3);
                lon = ProjMath.adjustLon(long0 + Math.atan2(x, y), over);
            } else {
                // General case - use Vincenty formulas
                double azi1 = Math.atan2(x, y);
                double s12 = Math.sqrt(x * x + y * y);
                double[] vars = vincentyDirect(lat0, long0, azi1, s12, a, f);
                lat = vars[0];
                lon = vars[1];
            }
        }

        return new Point(lon, lat, p.z);
    }

    // Vincenty inverse: compute azimuth and distance between two points
    private double[] vincentyInverse(double lat1, double lon1, double lat2, double lon2, double a, double f) {
        double L = lon2 - lon1;
        double U1 = Math.atan((1 - f) * Math.tan(lat1));
        double U2 = Math.atan((1 - f) * Math.tan(lat2));
        double sinU1 = Math.sin(U1), cosU1 = Math.cos(U1);
        double sinU2 = Math.sin(U2), cosU2 = Math.cos(U2);

        double lambda = L, lambdaP;
        int iterLimit = 100;
        double sinLambda, cosLambda, sinSigma, cosSigma, sigma, sinAlpha, cos2Alpha, cos2SigmaM, C;

        do {
            sinLambda = Math.sin(lambda);
            cosLambda = Math.cos(lambda);
            sinSigma = Math.sqrt(Math.pow(cosU2 * sinLambda, 2) + 
                Math.pow(cosU1 * sinU2 - sinU1 * cosU2 * cosLambda, 2));
            if (sinSigma == 0) {
                return new double[]{0, 0};
            }
            cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosLambda;
            sigma = Math.atan2(sinSigma, cosSigma);
            sinAlpha = cosU1 * cosU2 * sinLambda / sinSigma;
            cos2Alpha = 1 - sinAlpha * sinAlpha;
            cos2SigmaM = (cos2Alpha != 0) ? cosSigma - 2 * sinU1 * sinU2 / cos2Alpha : 0;
            C = f / 16 * cos2Alpha * (4 + f * (4 - 3 * cos2Alpha));
            lambdaP = lambda;
            lambda = L + (1 - C) * f * sinAlpha * 
                (sigma + C * sinSigma * (cos2SigmaM + C * cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)));
        } while (Math.abs(lambda - lambdaP) > 1e-12 && --iterLimit > 0);

        if (iterLimit == 0) {
            return new double[]{Double.NaN, Double.NaN};
        }

        double b = a * (1 - f);
        double uSq = cos2Alpha * (a * a - b * b) / (b * b);
        double A = 1 + uSq / 16384 * (4096 + uSq * (-768 + uSq * (320 - 175 * uSq)));
        double B = uSq / 1024 * (256 + uSq * (-128 + uSq * (74 - 47 * uSq)));
        double deltaSigma = B * sinSigma * (cos2SigmaM + B / 4 * (cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)
            - B / 6 * cos2SigmaM * (-3 + 4 * sinSigma * sinSigma) * (-3 + 4 * cos2SigmaM * cos2SigmaM)));

        double s = b * A * (sigma - deltaSigma);
        double azi1 = Math.atan2(cosU2 * sinLambda, cosU1 * sinU2 - sinU1 * cosU2 * cosLambda);

        return new double[]{azi1, s};
    }

    // Vincenty direct: compute destination given start, azimuth, and distance
    private double[] vincentyDirect(double lat1, double lon1, double azi1, double s12, double a, double f) {
        double b = a * (1 - f);
        double U1 = Math.atan((1 - f) * Math.tan(lat1));
        double sinU1 = Math.sin(U1), cosU1 = Math.cos(U1);
        double sinAlpha1 = Math.sin(azi1), cosAlpha1 = Math.cos(azi1);

        double sigma1 = Math.atan2(sinU1, cosU1 * cosAlpha1);
        double sinAlpha = cosU1 * sinAlpha1;
        double cos2Alpha = 1 - sinAlpha * sinAlpha;
        double uSq = cos2Alpha * (a * a - b * b) / (b * b);
        double A = 1 + uSq / 16384 * (4096 + uSq * (-768 + uSq * (320 - 175 * uSq)));
        double B = uSq / 1024 * (256 + uSq * (-128 + uSq * (74 - 47 * uSq)));

        double sigma = s12 / (b * A), sigmaP;
        int iterLimit = 100;
        double cos2SigmaM, sinSigma, cosSigma, deltaSigma;

        do {
            cos2SigmaM = Math.cos(2 * sigma1 + sigma);
            sinSigma = Math.sin(sigma);
            cosSigma = Math.cos(sigma);
            deltaSigma = B * sinSigma * (cos2SigmaM + B / 4 * (cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)
                - B / 6 * cos2SigmaM * (-3 + 4 * sinSigma * sinSigma) * (-3 + 4 * cos2SigmaM * cos2SigmaM)));
            sigmaP = sigma;
            sigma = s12 / (b * A) + deltaSigma;
        } while (Math.abs(sigma - sigmaP) > 1e-12 && --iterLimit > 0);

        if (iterLimit == 0) {
            return new double[]{Double.NaN, Double.NaN};
        }

        double tmp = sinU1 * sinSigma - cosU1 * cosSigma * cosAlpha1;
        double lat2 = Math.atan2(sinU1 * cosSigma + cosU1 * sinSigma * cosAlpha1,
            (1 - f) * Math.sqrt(sinAlpha * sinAlpha + tmp * tmp));
        double lambda = Math.atan2(sinSigma * sinAlpha1, cosU1 * cosSigma - sinU1 * sinSigma * cosAlpha1);
        double C = f / 16 * cos2Alpha * (4 + f * (4 - 3 * cos2Alpha));
        double L = lambda - (1 - C) * f * sinAlpha * 
            (sigma + C * sinSigma * (cos2SigmaM + C * cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)));
        double lon2 = lon1 + L;

        return new double[]{lat2, lon2};
    }
}
