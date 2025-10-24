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
 * Orthographic projection implementation. Ported from proj4js ortho.js
 *
 * <p>The Orthographic projection is an azimuthal perspective projection that projects the sphere
 * onto a flat surface from an infinite distance. It shows the Earth as it would appear from space,
 * with only one hemisphere visible at a time.
 */
public class Orthographic {

  /**
   * Initialize the Orthographic projection parameters.
   *
   * @param projection the projection to initialize
   */
  public static void init(Projection projection) {
    projection.sinP14 = Math.sin(projection.lat0);
    projection.cosP14 = Math.cos(projection.lat0);
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

    double dlon = MathUtils.adjustLon(lon - projection.long0);
    double sinphi = Math.sin(lat);
    double cosphi = Math.cos(lat);
    double coslon = Math.cos(dlon);

    double g = projection.sinP14 * sinphi + projection.cosP14 * cosphi * coslon;
    double ksp = 1.0;

    double x = 0;
    double y = 0;

    if ((g > 0) || (Math.abs(g) <= Values.EPSLN)) {
      x = projection.a * ksp * cosphi * Math.sin(dlon);
      y =
          projection.y0
              + projection.a
                  * ksp
                  * (projection.cosP14 * sinphi - projection.sinP14 * cosphi * coslon);
    }

    point.x = x;
    point.y = y;
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

    double rh = Math.sqrt(point.x * point.x + point.y * point.y);
    double z = MathUtils.asinz(rh / projection.a);
    double sinz = Math.sin(z);
    double cosz = Math.cos(z);

    double lon = projection.long0;
    double lat;

    if (Math.abs(rh) <= Values.EPSLN) {
      lat = projection.lat0;
      point.x = lon;
      point.y = lat;
      return point;
    }

    lat = MathUtils.asinz(cosz * projection.sinP14 + (point.y * sinz * projection.cosP14) / rh);
    double con = Math.abs(projection.lat0) - Values.HALF_PI;

    if (Math.abs(con) <= Values.EPSLN) {
      if (projection.lat0 >= 0) {
        lon = MathUtils.adjustLon(projection.long0 + Math.atan2(point.x, -point.y));
      } else {
        lon = MathUtils.adjustLon(projection.long0 - Math.atan2(-point.x, point.y));
      }
      point.x = lon;
      point.y = lat;
      return point;
    }

    lon =
        MathUtils.adjustLon(
            projection.long0
                + Math.atan2(
                    (point.x * sinz),
                    rh * projection.cosP14 * cosz - point.y * projection.sinP14 * sinz));

    point.x = lon;
    point.y = lat;
    return point;
  }

  /** Projection names */
  public static final String[] NAMES = {"ortho", "Orthographic"};
}
