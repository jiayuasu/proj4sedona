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
 * Stereographic projection implementation. Ported from proj4js stere.js
 *
 * <p>The Stereographic projection is a conformal azimuthal projection. It's commonly used for polar
 * regions and in the Universal Polar Stereographic (UPS) grid system.
 */
public class Stereographic {

  private static double ssfn(double phit, double sinphi, double eccen) {
    sinphi *= eccen;
    return (Math.tan(0.5 * (Values.HALF_PI + phit))
        * Math.pow((1 - sinphi) / (1 + sinphi), 0.5 * eccen));
  }

  /**
   * Initialize the Stereographic projection parameters.
   *
   * @param projection the projection to initialize
   */
  public static void init(Projection projection) {
    projection.coslat0 = Math.cos(projection.lat0);
    projection.sinlat0 = Math.sin(projection.lat0);

    if (projection.sphere) {
      if (projection.k0 == 1
          && !Double.isNaN(projection.lat_ts)
          && Math.abs(projection.coslat0) <= Values.EPSLN) {
        projection.k0 = 0.5 * (1 + MathUtils.sign(projection.lat0) * Math.sin(projection.lat_ts));
      }
    } else {
      if (Math.abs(projection.coslat0) <= Values.EPSLN) {
        if (projection.lat0 > 0) {
          // North pole
          projection.con = 1;
        } else {
          // South pole
          projection.con = -1;
        }
      }
      projection.cons =
          Math.sqrt(
              Math.pow(1 + projection.e, 1 + projection.e)
                  * Math.pow(1 - projection.e, 1 - projection.e));

      if (projection.k0 == 1
          && !Double.isNaN(projection.lat_ts)
          && Math.abs(projection.coslat0) <= Values.EPSLN
          && Math.abs(Math.cos(projection.lat_ts)) > Values.EPSLN) {
        // When k0 is 1 (default value) and lat_ts is a valid number and lat0 is at a pole and
        // lat_ts is not at a pole
        // Recalculate k0 using formula 21-35 from p161 of Snyder, 1987
        projection.k0 =
            0.5
                * projection.cons
                * MathUtils.msfnz(
                    projection.e, Math.sin(projection.lat_ts), Math.cos(projection.lat_ts))
                / MathUtils.tsfnz(
                    projection.e,
                    projection.con * projection.lat_ts,
                    projection.con * Math.sin(projection.lat_ts));
      }
      projection.ms1 = MathUtils.msfnz(projection.e, projection.sinlat0, projection.coslat0);
      projection.X0 =
          2 * Math.atan(ssfn(projection.lat0, projection.sinlat0, projection.e)) - Values.HALF_PI;
      projection.cosX0 = Math.cos(projection.X0);
      projection.sinX0 = Math.sin(projection.X0);
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
    double lon = point.x;
    double lat = point.y;
    double sinlat = Math.sin(lat);
    double coslat = Math.cos(lat);
    double dlon = MathUtils.adjustLon(lon - projection.long0);

    if (Math.abs(Math.abs(lon - projection.long0) - Values.PI) <= Values.EPSLN
        && Math.abs(lat + projection.lat0) <= Values.EPSLN) {
      // Case of the origin point
      point.x = Double.NaN;
      point.y = Double.NaN;
      return point;
    }

    if (projection.sphere) {
      double A =
          2
              * projection.k0
              / (1 + projection.sinlat0 * sinlat + projection.coslat0 * coslat * Math.cos(dlon));
      point.x = projection.a * A * coslat * Math.sin(dlon) + projection.x0;
      point.y =
          projection.a
                  * A
                  * (projection.coslat0 * sinlat - projection.sinlat0 * coslat * Math.cos(dlon))
              + projection.y0;
      return point;
    } else {
      double X = 2 * Math.atan(ssfn(lat, sinlat, projection.e)) - Values.HALF_PI;
      double cosX = Math.cos(X);
      double sinX = Math.sin(X);

      if (Math.abs(projection.coslat0) <= Values.EPSLN) {
        double ts = MathUtils.tsfnz(projection.e, lat * projection.con, projection.con * sinlat);
        double rh = 2 * projection.a * projection.k0 * ts / projection.cons;
        point.x = projection.x0 + rh * Math.sin(lon - projection.long0);
        point.y = projection.y0 - projection.con * rh * Math.cos(lon - projection.long0);
        return point;
      } else if (Math.abs(projection.sinlat0) < Values.EPSLN) {
        // Equator
        double A = 2 * projection.a * projection.k0 / (1 + cosX * Math.cos(dlon));
        point.y = A * sinX;
      } else {
        // Other case
        double A =
            2
                * projection.a
                * projection.k0
                * projection.ms1
                / (projection.cosX0
                    * (1 + projection.sinX0 * sinX + projection.cosX0 * cosX * Math.cos(dlon)));
        point.x = A * cosX * Math.sin(dlon) + projection.x0;
        point.y =
            A * (projection.cosX0 * sinX - projection.sinX0 * cosX * Math.cos(dlon))
                + projection.y0;
        return point;
      }
      point.x =
          projection.x0
              + (2 * projection.a * projection.k0 / (1 + cosX * Math.cos(dlon)))
                  * cosX
                  * Math.sin(dlon);
    }
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
    double lon, lat;
    double rh = Math.sqrt(point.x * point.x + point.y * point.y);

    if (projection.sphere) {
      double c = 2 * Math.atan(rh / (2 * projection.a * projection.k0));
      lon = projection.long0;
      lat = projection.lat0;

      if (rh <= Values.EPSLN) {
        point.x = lon;
        point.y = lat;
        return point;
      }

      lat =
          Math.asin(
              Math.cos(c) * projection.sinlat0 + point.y * Math.sin(c) * projection.coslat0 / rh);

      if (Math.abs(projection.coslat0) < Values.EPSLN) {
        if (projection.lat0 > 0) {
          lon = MathUtils.adjustLon(projection.long0 + Math.atan2(point.x, -1 * point.y));
        } else {
          lon = MathUtils.adjustLon(projection.long0 + Math.atan2(point.x, point.y));
        }
      } else {
        lon =
            MathUtils.adjustLon(
                projection.long0
                    + Math.atan2(
                        point.x * Math.sin(c),
                        rh * projection.coslat0 * Math.cos(c)
                            - point.y * projection.sinlat0 * Math.sin(c)));
      }

      point.x = lon;
      point.y = lat;
      return point;
    } else {
      if (Math.abs(projection.coslat0) <= Values.EPSLN) {
        if (rh <= Values.EPSLN) {
          lat = projection.lat0;
          lon = projection.long0;
          point.x = lon;
          point.y = lat;
          return point;
        }
        point.x *= projection.con;
        point.y *= projection.con;
        double ts = rh * projection.cons / (2 * projection.a * projection.k0);
        lat = projection.con * MathUtils.phi2z(projection.e, ts);
        lon =
            projection.con
                * MathUtils.adjustLon(
                    projection.con * projection.long0 + Math.atan2(point.x, -1 * point.y));
      } else {
        double ce =
            2
                * Math.atan(
                    rh * projection.cosX0 / (2 * projection.a * projection.k0 * projection.ms1));
        lon = projection.long0;
        double Chi;

        if (rh <= Values.EPSLN) {
          Chi = projection.X0;
        } else {
          Chi =
              Math.asin(
                  Math.cos(ce) * projection.sinX0 + point.y * Math.sin(ce) * projection.cosX0 / rh);
          lon =
              MathUtils.adjustLon(
                  projection.long0
                      + Math.atan2(
                          point.x * Math.sin(ce),
                          rh * projection.cosX0 * Math.cos(ce)
                              - point.y * projection.sinX0 * Math.sin(ce)));
        }
        lat = -1 * MathUtils.phi2z(projection.e, Math.tan(0.5 * (Values.HALF_PI + Chi)));
      }

      point.x = lon;
      point.y = lat;
      return point;
    }
  }

  /** Projection names */
  public static final String[] NAMES = {
    "stere",
    "Stereographic_South_Pole",
    "Polar Stereographic (variant A)",
    "Polar_Stereographic",
    "Polar_Stereographic_variant_B"
  };
}
