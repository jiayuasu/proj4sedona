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
 * Lambert Azimuthal Equal Area projection implementation. Ported from proj4js laea.js
 *
 * <p>Reference: "New Equal-Area Map Projections for Noncircular Regions", John P. Snyder, The
 * American Cartographer, Vol 15, No. 4, October 1988, pp. 341-355.
 *
 * <p>The Lambert Azimuthal Equal Area projection is an azimuthal projection that preserves area.
 * It's commonly used for maps of continents and polar regions.
 */
public class LambertAzimuthalEqualArea {

  /** South Pole aspect mode */
  public static final int S_POLE = 1;

  /** North Pole aspect mode */
  public static final int N_POLE = 2;

  /** Equatorial aspect mode */
  public static final int EQUIT = 3;

  /** Oblique aspect mode */
  public static final int OBLIQ = 4;

  // Constants for authalic latitude
  private static final double P00 = 0.33333333333333333333;
  private static final double P01 = 0.17222222222222222222;
  private static final double P02 = 0.10257936507936507936;
  private static final double P10 = 0.06388888888888888888;
  private static final double P11 = 0.06640211640211640211;
  private static final double P20 = 0.01641501294219154443;

  private static double[] authset(double es) {
    double[] APA = new double[3];
    double t;
    APA[0] = es * P00;
    t = es * es;
    APA[0] += t * P01;
    APA[1] = t * P10;
    t *= es;
    APA[0] += t * P02;
    APA[1] += t * P11;
    APA[2] = t * P20;
    return APA;
  }

  private static double authlat(double beta, double[] APA) {
    double t = beta + beta;
    return (beta + APA[0] * Math.sin(t) + APA[1] * Math.sin(t + t) + APA[2] * Math.sin(t + t + t));
  }

  /**
   * Initialize the Lambert Azimuthal Equal Area projection parameters.
   *
   * @param projection the projection to initialize
   */
  public static void init(Projection projection) {
    double t = Math.abs(projection.lat0);
    if (Math.abs(t - Values.HALF_PI) < Values.EPSLN) {
      projection.mode = projection.lat0 < 0 ? S_POLE : N_POLE;
    } else if (Math.abs(t) < Values.EPSLN) {
      projection.mode = EQUIT;
    } else {
      projection.mode = OBLIQ;
    }

    if (projection.es > 0) {
      double sinphi;
      projection.qp = MathUtils.qsfnz(projection.e, 1);
      projection.mmf = 0.5 / (1 - projection.es);
      projection.apa = authset(projection.es);

      switch (projection.mode) {
        case N_POLE:
        case S_POLE:
          projection.dd = 1;
          break;
        case EQUIT:
          projection.rq = Math.sqrt(0.5 * projection.qp);
          projection.dd = 1 / projection.rq;
          projection.xmf = 1;
          projection.ymf = 0.5 * projection.qp;
          break;
        case OBLIQ:
          projection.rq = Math.sqrt(0.5 * projection.qp);
          sinphi = Math.sin(projection.lat0);
          projection.sinb1 = MathUtils.qsfnz(projection.e, sinphi) / projection.qp;
          projection.cosb1 = Math.sqrt(1 - projection.sinb1 * projection.sinb1);
          projection.dd =
              Math.cos(projection.lat0)
                  / (Math.sqrt(1 - projection.es * sinphi * sinphi)
                      * projection.rq
                      * projection.cosb1);
          projection.ymf = (projection.xmf = projection.rq) / projection.dd;
          projection.xmf *= projection.dd;
          break;
      }
    } else {
      if (projection.mode == OBLIQ) {
        projection.sinph0 = Math.sin(projection.lat0);
        projection.cosph0 = Math.cos(projection.lat0);
      }
    }
  }

  /**
   * Forward transformation from geographic to projected coordinates (lon/lat to x/y).
   *
   * @param point the geographic point (lon, lat in radians)
   * @param projection the projection parameters
   * @return the projected point (x, y)
   */
  public static Point forward(Projection projection, Point point) {
    double x = 0, y = 0; // Initialize to avoid compiler warnings
    double lam = point.x;
    double phi = point.y;

    lam = MathUtils.adjustLon(lam - projection.long0);

    if (projection.sphere) {
      double sinphi = Math.sin(phi);
      double cosphi = Math.cos(phi);
      double coslam = Math.cos(lam);

      switch (projection.mode) {
        case OBLIQ:
        case EQUIT:
          y =
              (projection.mode == EQUIT)
                  ? 1 + cosphi * coslam
                  : 1 + projection.sinph0 * sinphi + projection.cosph0 * cosphi * coslam;
          if (y <= Values.EPSLN) {
            return null;
          }
          y = Math.sqrt(2 / y);
          x = y * cosphi * Math.sin(lam);
          y *=
              (projection.mode == EQUIT)
                  ? sinphi
                  : projection.cosph0 * sinphi - projection.sinph0 * cosphi * coslam;
          break;
        case N_POLE:
          coslam = -coslam;
          // fall through
        case S_POLE:
          if (Math.abs(phi + projection.lat0) < Values.EPSLN) {
            return null;
          }
          y = Values.FORTPI - phi * 0.5;
          y = 2 * ((projection.mode == S_POLE) ? Math.cos(y) : Math.sin(y));
          x = y * Math.sin(lam);
          y *= coslam;
          break;
      }
    } else {
      double sinb = 0;
      double cosb = 0;
      double b = 0;
      double coslam = Math.cos(lam);
      double sinlam = Math.sin(lam);
      double sinphi = Math.sin(phi);
      double q = MathUtils.qsfnz(projection.e, sinphi);

      if (projection.mode == OBLIQ || projection.mode == EQUIT) {
        sinb = q / projection.qp;
        cosb = Math.sqrt(1 - sinb * sinb);
      }

      switch (projection.mode) {
        case OBLIQ:
          b = 1 + projection.sinb1 * sinb + projection.cosb1 * cosb * coslam;
          break;
        case EQUIT:
          b = 1 + cosb * coslam;
          break;
        case N_POLE:
          b = Values.HALF_PI + phi;
          q = projection.qp - q;
          break;
        case S_POLE:
          b = phi - Values.HALF_PI;
          q = projection.qp + q;
          break;
      }

      if (Math.abs(b) < Values.EPSLN) {
        return null;
      }

      switch (projection.mode) {
        case OBLIQ:
        case EQUIT:
          b = Math.sqrt(2 / b);
          if (projection.mode == OBLIQ) {
            y = projection.ymf * b * (projection.cosb1 * sinb - projection.sinb1 * cosb * coslam);
          } else {
            y = (b = Math.sqrt(2 / (1 + cosb * coslam))) * sinb * projection.ymf;
          }
          x = projection.xmf * b * cosb * sinlam;
          break;
        case N_POLE:
        case S_POLE:
          if (q >= 0) {
            x = (b = Math.sqrt(q)) * sinlam;
            y = coslam * ((projection.mode == S_POLE) ? b : -b);
          } else {
            x = y = 0;
          }
          break;
      }
    }

    point.x = projection.a * x + projection.x0;
    point.y = projection.a * y + projection.y0;
    return point;
  }

  /**
   * Inverse transformation from projected to geographic coordinates (x/y to lon/lat).
   *
   * @param point the projected point (x, y)
   * @param projection the projection parameters
   * @return the geographic point (lon, lat in radians)
   */
  public static Point inverse(Projection projection, Point point) {
    point.x -= projection.x0;
    point.y -= projection.y0;
    double x = point.x / projection.a;
    double y = point.y / projection.a;
    double lam, phi;

    if (projection.sphere) {
      double cosz = 0;
      double rh = Math.sqrt(x * x + y * y);
      phi = rh * 0.5;

      if (phi > 1) {
        return null;
      }
      phi = 2 * Math.asin(phi);

      if (projection.mode == OBLIQ || projection.mode == EQUIT) {
        double sinz = Math.sin(phi);
        cosz = Math.cos(phi);
      }

      switch (projection.mode) {
        case EQUIT:
          phi = (Math.abs(rh) <= Values.EPSLN) ? 0 : Math.asin(y * Math.sin(phi) / rh);
          x *= Math.sin(phi);
          y = cosz * rh;
          break;
        case OBLIQ:
          phi =
              (Math.abs(rh) <= Values.EPSLN)
                  ? projection.lat0
                  : Math.asin(
                      cosz * projection.sinph0 + y * Math.sin(phi) * projection.cosph0 / rh);
          x *= Math.sin(phi) * projection.cosph0;
          y = (cosz - Math.sin(phi) * projection.sinph0) * rh;
          break;
        case N_POLE:
          y = -y;
          phi = Values.HALF_PI - phi;
          break;
        case S_POLE:
          phi -= Values.HALF_PI;
          break;
      }
      lam =
          (y == 0 && (projection.mode == EQUIT || projection.mode == OBLIQ)) ? 0 : Math.atan2(x, y);
    } else {
      double ab = 0;
      if (projection.mode == OBLIQ || projection.mode == EQUIT) {
        x /= projection.dd;
        y *= projection.dd;
        double rho = Math.sqrt(x * x + y * y);
        if (rho < Values.EPSLN) {
          point.x = projection.long0;
          point.y = projection.lat0;
          return point;
        }
        double sCe = 2 * Math.asin(0.5 * rho / projection.rq);
        double cCe = Math.cos(sCe);
        x *= (sCe = Math.sin(sCe));
        if (projection.mode == OBLIQ) {
          ab = cCe * projection.sinb1 + y * sCe * projection.cosb1 / rho;
          double q = projection.qp * ab;
          y = rho * projection.cosb1 * cCe - y * projection.sinb1 * sCe;
        } else {
          ab = y * sCe / rho;
          double q = projection.qp * ab;
          y = rho * cCe;
        }
      } else if (projection.mode == N_POLE || projection.mode == S_POLE) {
        if (projection.mode == N_POLE) {
          y = -y;
        }
        double q = (x * x + y * y);
        if (q == 0) {
          point.x = projection.long0;
          point.y = projection.lat0;
          return point;
        }
        ab = 1 - q / projection.qp;
        if (projection.mode == S_POLE) {
          ab = -ab;
        }
      }
      lam = Math.atan2(x, y);
      phi = authlat(Math.asin(ab), projection.apa);
    }

    point.x = MathUtils.adjustLon(projection.long0 + lam);
    point.y = phi;
    return point;
  }

  /** Projection names */
  public static final String[] NAMES = {
    "Lambert Azimuthal Equal Area", "Lambert_Azimuthal_Equal_Area", "laea"
  };
}
