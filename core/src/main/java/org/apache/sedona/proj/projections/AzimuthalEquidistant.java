/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sedona.proj.projections;

import org.apache.sedona.proj.common.MathUtils;
import org.apache.sedona.proj.constants.Values;
import org.apache.sedona.proj.core.Point;
import org.apache.sedona.proj.core.Projection;

/**
 * Azimuthal Equidistant projection implementation. Ported from proj4js aeqd.js
 *
 * <p>The Azimuthal Equidistant projection preserves both distance and direction from the central
 * point. It's commonly used for air route maps and seismic work.
 */
public class AzimuthalEquidistant {

  /** Helper class for Vincenty results. */
  private static class VincentyResult {
    double azi1;
    double s12;

    VincentyResult(double azi1, double s12) {
      this.azi1 = azi1;
      this.s12 = s12;
    }
  }

  /** Vincenty inverse formula - calculates distance and azimuth between two points. */
  private static VincentyResult vincentyInverse(
      double lat1, double lon1, double lat2, double lon2, double a, double f) {
    double b = a * (1 - f);
    double flatteningSquared = f * (2 - f);

    double U1 = Math.atan((1 - f) * Math.tan(lat1));
    double U2 = Math.atan((1 - f) * Math.tan(lat2));
    double sinU1 = Math.sin(U1);
    double cosU1 = Math.cos(U1);
    double sinU2 = Math.sin(U2);
    double cosU2 = Math.cos(U2);

    double L = lon2 - lon1;
    double lambda = L;
    double lambdaP;
    int iterLimit = 100;
    double cosSqAlpha = 0,
        sigma = 0,
        cos2SigmaM = 0,
        cosSigma = 0,
        sinSigma = 0,
        sinLambda,
        cosLambda;

    do {
      sinLambda = Math.sin(lambda);
      cosLambda = Math.cos(lambda);
      double sinSigmaSq =
          (cosU2 * sinLambda) * (cosU2 * sinLambda)
              + (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda)
                  * (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda);

      if (sinSigmaSq < 0) {
        sinSigmaSq = 0;
      }
      sinSigma = Math.sqrt(sinSigmaSq);

      cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosLambda;
      sigma = Math.atan2(sinSigma, cosSigma);
      double sinAlpha =
          (Math.abs(sinSigma) < Values.EPSLN) ? 0 : cosU1 * cosU2 * sinLambda / sinSigma;
      cosSqAlpha = 1 - sinAlpha * sinAlpha;
      cos2SigmaM =
          (Math.abs(cosSqAlpha) < Values.EPSLN) ? 0 : cosSigma - 2 * sinU1 * sinU2 / cosSqAlpha;
      double C = f / 16 * cosSqAlpha * (4 + f * (4 - 3 * cosSqAlpha));
      lambdaP = lambda;
      lambda =
          L
              + (1 - C)
                  * f
                  * sinAlpha
                  * (sigma
                      + C
                          * sinSigma
                          * (cos2SigmaM + C * cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)));
    } while (Math.abs(lambda - lambdaP) > 1e-12 && --iterLimit > 0);

    double uSq = cosSqAlpha * (a * a - b * b) / (b * b);
    double A = 1 + uSq / 16384 * (4096 + uSq * (-768 + uSq * (320 - 175 * uSq)));
    double B = uSq / 1024 * (256 + uSq * (-128 + uSq * (74 - 47 * uSq)));
    double deltaSigma =
        B
            * sinSigma
            * (cos2SigmaM
                + B
                    / 4
                    * (cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)
                        - B
                            / 6
                            * cos2SigmaM
                            * (-3 + 4 * sinSigma * sinSigma)
                            * (-3 + 4 * cos2SigmaM * cos2SigmaM)));

    double s12 = b * A * (sigma - deltaSigma);
    double azi1 = Math.atan2(cosU2 * sinLambda, cosU1 * sinU2 - sinU1 * cosU2 * cosLambda);

    return new VincentyResult(azi1, s12);
  }

  /** Vincenty direct formula - calculates end point given start point, azimuth and distance. */
  private static Point vincentyDirect(
      double lat1, double lon1, double azi1, double s12, double a, double f) {
    double b = a * (1 - f);
    double U1 = Math.atan((1 - f) * Math.tan(lat1));
    double sigma1 = Math.atan2(Math.tan(U1), Math.cos(azi1));
    double sinAlpha = Math.cos(U1) * Math.sin(azi1);
    double cosSqAlpha = 1 - sinAlpha * sinAlpha;
    double uSq = cosSqAlpha * (a * a - b * b) / (b * b);
    double A = 1 + uSq / 16384 * (4096 + uSq * (-768 + uSq * (320 - 175 * uSq)));
    double B = uSq / 1024 * (256 + uSq * (-128 + uSq * (74 - 47 * uSq)));

    double sigma = s12 / (b * A);
    double sigmaP;
    double cos2SigmaM = 0, sinSigma = 0, cosSigma = 0;
    int iterLimit = 100;

    do {
      cos2SigmaM = Math.cos(2 * sigma1 + sigma);
      sinSigma = Math.sin(sigma);
      cosSigma = Math.cos(sigma);
      double deltaSigma =
          B
              * sinSigma
              * (cos2SigmaM
                  + B
                      / 4
                      * (cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)
                          - B
                              / 6
                              * cos2SigmaM
                              * (-3 + 4 * sinSigma * sinSigma)
                              * (-3 + 4 * cos2SigmaM * cos2SigmaM)));
      sigmaP = sigma;
      sigma = s12 / (b * A) + deltaSigma;
    } while (Math.abs(sigma - sigmaP) > 1e-12 && --iterLimit > 0);

    double tmp = Math.sin(U1) * sinSigma - Math.cos(U1) * cosSigma * Math.cos(azi1);
    double lat2 =
        Math.atan2(
            Math.sin(U1) * cosSigma + Math.cos(U1) * sinSigma * Math.cos(azi1),
            (1 - f) * Math.sqrt(sinAlpha * sinAlpha + tmp * tmp));

    double lambda =
        Math.atan2(
            sinSigma * Math.sin(azi1),
            Math.cos(U1) * cosSigma - Math.sin(U1) * sinSigma * Math.cos(azi1));
    double C = f / 16 * cosSqAlpha * (4 + f * (4 - 3 * cosSqAlpha));
    double L =
        lambda
            - (1 - C)
                * f
                * sinAlpha
                * (sigma
                    + C
                        * sinSigma
                        * (cos2SigmaM + C * cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)));
    double lon2 = lon1 + L;

    return new Point(lon2, lat2);
  }

  /**
   * Initialize the Azimuthal Equidistant projection parameters.
   *
   * @param projection the projection to initialize
   */
  public static void init(Projection projection) {
    projection.sinP14 = Math.sin(projection.lat0);
    projection.cosP14 = Math.cos(projection.lat0);
    // Flattening for ellipsoid
    projection.e = projection.es / (1 + Math.sqrt(1 - projection.es));
  }

  /**
   * Forward transformation from geographic to projected coordinates (lon/lat to x/y).
   *
   * @param projection the projection parameters
   * @param point the geographic point (lon, lat in radians)
   * @return the projected point (x, y)
   */
  public static Point forward(Projection projection, Point point) {
    double lon = point.x;
    double lat = point.y;
    double sinphi = Math.sin(lat);
    double cosphi = Math.cos(lat);
    double dlon = MathUtils.adjustLon(lon - projection.long0);

    if (projection.sphere) {
      if (Math.abs(projection.sinP14 - 1) <= Values.EPSLN) {
        // North Pole case
        point.x = projection.x0 + projection.a * (Values.HALF_PI - lat) * Math.sin(dlon);
        point.y = projection.y0 - projection.a * (Values.HALF_PI - lat) * Math.cos(dlon);
        return point;
      } else if (Math.abs(projection.sinP14 + 1) <= Values.EPSLN) {
        // South Pole case
        point.x = projection.x0 + projection.a * (Values.HALF_PI + lat) * Math.sin(dlon);
        point.y = projection.y0 + projection.a * (Values.HALF_PI + lat) * Math.cos(dlon);
        return point;
      } else {
        // Default case
        double cosC = projection.sinP14 * sinphi + projection.cosP14 * cosphi * Math.cos(dlon);
        double c = Math.acos(cosC);
        double kp = c / Math.sin(c);
        if (c == 0) {
          kp = 1;
        }
        point.x = projection.x0 + projection.a * kp * cosphi * Math.sin(dlon);
        point.y =
            projection.y0
                + projection.a
                    * kp
                    * (projection.cosP14 * sinphi - projection.sinP14 * cosphi * Math.cos(dlon));
        return point;
      }
    } else {
      double e0 = MathUtils.e0fn(projection.es);
      double e1 = MathUtils.e1fn(projection.es);
      double e2 = MathUtils.e2fn(projection.es);
      double e3 = MathUtils.e3fn(projection.es);

      if (Math.abs(projection.sinP14 - 1) <= Values.EPSLN) {
        // North Pole case
        double Mlp = projection.a * MathUtils.mlfn(e0, e1, e2, e3, Values.HALF_PI);
        double Ml = projection.a * MathUtils.mlfn(e0, e1, e2, e3, lat);
        point.x = projection.x0 + (Mlp - Ml) * Math.sin(dlon);
        point.y = projection.y0 - (Mlp - Ml) * Math.cos(dlon);
        return point;
      } else if (Math.abs(projection.sinP14 + 1) <= Values.EPSLN) {
        // South Pole case
        double Mlp = projection.a * MathUtils.mlfn(e0, e1, e2, e3, Values.HALF_PI);
        double Ml = projection.a * MathUtils.mlfn(e0, e1, e2, e3, lat);
        point.x = projection.x0 + (Mlp + Ml) * Math.sin(dlon);
        point.y = projection.y0 + (Mlp + Ml) * Math.cos(dlon);
        return point;
      } else {
        // Default case
        if (Math.abs(lon) < Values.EPSLN && Math.abs(lat - projection.lat0) < Values.EPSLN) {
          point.x = 0;
          point.y = 0;
          return point;
        }
        VincentyResult vars =
            vincentyInverse(
                projection.lat0, projection.long0, lat, lon, projection.a, projection.e);
        point.x = vars.s12 * Math.sin(vars.azi1);
        point.y = vars.s12 * Math.cos(vars.azi1);
        return point;
      }
    }
  }

  /**
   * Inverse transformation from projected to geographic coordinates (x/y to lon/lat).
   *
   * @param projection the projection parameters
   * @param point the projected point (x, y)
   * @return the geographic point (lon, lat in radians)
   */
  public static Point inverse(Projection projection, Point point) {
    point.x -= projection.x0;
    point.y -= projection.y0;

    if (projection.sphere) {
      double rh = Math.sqrt(point.x * point.x + point.y * point.y);
      if (rh > (2 * Values.HALF_PI * projection.a)) {
        return null;
      }
      double z = rh / projection.a;
      double sinz = Math.sin(z);
      double cosz = Math.cos(z);

      double lon = projection.long0;
      double lat;

      if (Math.abs(rh) <= Values.EPSLN) {
        lat = projection.lat0;
      } else {
        lat = MathUtils.asinz(cosz * projection.sinP14 + (point.y * sinz * projection.cosP14) / rh);
        double con = Math.abs(projection.lat0) - Values.HALF_PI;
        if (Math.abs(con) <= Values.EPSLN) {
          if (projection.lat0 >= 0) {
            lon = MathUtils.adjustLon(projection.long0 + Math.atan2(point.x, -point.y));
          } else {
            lon = MathUtils.adjustLon(projection.long0 - Math.atan2(-point.x, point.y));
          }
        } else {
          lon =
              MathUtils.adjustLon(
                  projection.long0
                      + Math.atan2(
                          point.x * sinz,
                          rh * projection.cosP14 * cosz - point.y * projection.sinP14 * sinz));
        }
      }
      point.x = lon;
      point.y = lat;
      return point;
    } else {
      double e0 = MathUtils.e0fn(projection.es);
      double e1 = MathUtils.e1fn(projection.es);
      double e2 = MathUtils.e2fn(projection.es);
      double e3 = MathUtils.e3fn(projection.es);

      if (Math.abs(projection.sinP14 - 1) <= Values.EPSLN) {
        // North pole case
        double Mlp = projection.a * MathUtils.mlfn(e0, e1, e2, e3, Values.HALF_PI);
        double rh = Math.sqrt(point.x * point.x + point.y * point.y);
        double M = Mlp - rh;
        double lat = MathUtils.imlfn(M / projection.a, e0, e1, e2, e3);
        double lon = MathUtils.adjustLon(projection.long0 + Math.atan2(point.x, -1 * point.y));
        point.x = lon;
        point.y = lat;
        return point;
      } else if (Math.abs(projection.sinP14 + 1) <= Values.EPSLN) {
        // South pole case
        double Mlp = projection.a * MathUtils.mlfn(e0, e1, e2, e3, Values.HALF_PI);
        double rh = Math.sqrt(point.x * point.x + point.y * point.y);
        double M = rh - Mlp;
        double lat = MathUtils.imlfn(M / projection.a, e0, e1, e2, e3);
        double lon = MathUtils.adjustLon(projection.long0 + Math.atan2(point.x, point.y));
        point.x = lon;
        point.y = lat;
        return point;
      } else {
        // Default case
        double azi1 = Math.atan2(point.x, point.y);
        double s12 = Math.sqrt(point.x * point.x + point.y * point.y);
        Point result =
            vincentyDirect(
                projection.lat0, projection.long0, azi1, s12, projection.a, projection.e);
        point.x = result.x;
        point.y = result.y;
        return point;
      }
    }
  }

  /** Projection names */
  public static final String[] NAMES = {"Azimuthal_Equidistant", "aeqd"};
}
