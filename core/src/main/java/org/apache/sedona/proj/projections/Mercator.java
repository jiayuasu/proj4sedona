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
 * Mercator projection implementation. Also known as Popular Visualisation Pseudo Mercator,
 * Mercator_1SP, Mercator_Auxiliary_Sphere, or Mercator_Variant_A. Ported from proj4js merc.js
 *
 * <p>The Mercator projection is a cylindrical map projection that preserves angles and shapes of
 * small objects but distorts size and distance. It's widely used for web mapping applications (Web
 * Mercator EPSG:3857).
 *
 * @see <a href="https://en.wikipedia.org/wiki/Mercator_projection">Mercator projection</a>
 */
public class Mercator {

  /**
   * Initialize the Mercator projection parameters.
   *
   * @param projection the projection to initialize
   */
  public static void init(Projection projection) {
    double con = projection.b / projection.a;
    projection.es = 1 - con * con;

    projection.e = Math.sqrt(projection.es);

    if (projection.lat_ts != 0) {
      if (projection.sphere) {
        projection.k0 = Math.cos(projection.lat_ts);
      } else {
        projection.k0 =
            MathUtils.msfnz(projection.e, Math.sin(projection.lat_ts), Math.cos(projection.lat_ts));
      }
    } else {
      if (projection.k0 == 0) {
        if (projection.k != 0) {
          projection.k0 = projection.k;
        } else {
          projection.k0 = 1;
        }
      }
    }
  }

  /**
   * Forward transformation from geographic to projected coordinates (lon/lat to x/y).
   *
   * @param point the geographic point (lon, lat in radians)
   * @param projection the projection parameters
   * @return the projected point (x, y) or null if transformation fails
   */
  public static Point forward(Projection projection, Point point) {
    double lon = point.x;
    double lat = point.y;

    // Check for out of range coordinates
    if (lat * Values.R2D > 90
        && lat * Values.R2D < -90
        && lon * Values.R2D > 180
        && lon * Values.R2D < -180) {
      return null;
    }

    double x, y;
    if (Math.abs(Math.abs(lat) - Values.HALF_PI) <= Values.EPSLN) {
      // Latitude at or beyond poles
      return null;
    } else {
      if (projection.sphere) {
        x =
            projection.x0
                + projection.a * projection.k0 * MathUtils.adjustLon(lon - projection.long0);
        y =
            projection.y0
                + projection.a * projection.k0 * Math.log(Math.tan(Values.FORTPI + 0.5 * lat));
      } else {
        double sinphi = Math.sin(lat);
        double ts = MathUtils.tsfnz(projection.e, lat, sinphi);
        x =
            projection.x0
                + projection.a * projection.k0 * MathUtils.adjustLon(lon - projection.long0);
        y = projection.y0 - projection.a * projection.k0 * Math.log(ts);
      }
      point.x = x;
      point.y = y;
      return point;
    }
  }

  /**
   * Inverse transformation from projected to geographic coordinates (x/y to lon/lat).
   *
   * @param point the projected point (x, y)
   * @param projection the projection parameters
   * @return the geographic point (lon, lat in radians) or null if transformation fails
   */
  public static Point inverse(Projection projection, Point point) {
    double x = point.x - projection.x0;
    double y = point.y - projection.y0;
    double lon, lat;

    if (projection.sphere) {
      lat = Values.HALF_PI - 2 * Math.atan(Math.exp(-y / (projection.a * projection.k0)));
    } else {
      double ts = Math.exp(-y / (projection.a * projection.k0));
      lat = MathUtils.phi2z(projection.e, ts);
      if (lat == -9999) {
        return null;
      }
    }
    lon = MathUtils.adjustLon(projection.long0 + x / (projection.a * projection.k0));

    point.x = lon;
    point.y = lat;
    return point;
  }

  /** Projection names */
  public static final String[] NAMES = {
    "Mercator",
    "Popular Visualisation Pseudo Mercator",
    "Mercator_1SP",
    "Mercator_Auxiliary_Sphere",
    "Mercator_Variant_A",
    "merc"
  };
}
