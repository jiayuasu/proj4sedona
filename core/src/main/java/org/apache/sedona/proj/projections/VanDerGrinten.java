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
 * Van der Grinten projection implementation. Ported from proj4js vandg.js
 *
 * <p>The Van der Grinten projection is a compromise map projection that shows the entire Earth in a
 * circle. It was the first projection used by the National Geographic Society and was standard from
 * 1922 to 1988.
 */
public class VanDerGrinten {

  /**
   * Initialize the Van der Grinten projection parameters.
   *
   * @param projection the projection to initialize
   */
  public static void init(Projection projection) {
    projection.R = projection.a;
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
    double x, y;

    if (Math.abs(lat) <= Values.EPSLN) {
      x = projection.x0 + projection.R * dlon;
      y = projection.y0;
      point.x = x;
      point.y = y;
      return point;
    }

    double theta = MathUtils.asinz(2 * Math.abs(lat / Values.PI));

    if ((Math.abs(dlon) <= Values.EPSLN)
        || (Math.abs(Math.abs(lat) - Values.HALF_PI) <= Values.EPSLN)) {
      x = projection.x0;
      if (lat >= 0) {
        y = projection.y0 + Values.PI * projection.R * Math.tan(0.5 * theta);
      } else {
        y = projection.y0 + Values.PI * projection.R * -Math.tan(0.5 * theta);
      }
      point.x = x;
      point.y = y;
      return point;
    }

    double al = 0.5 * Math.abs((Values.PI / dlon) - (dlon / Values.PI));
    double asq = al * al;
    double sinth = Math.sin(theta);
    double costh = Math.cos(theta);

    double g = costh / (sinth + costh - 1);
    double gsq = g * g;
    double m = g * (2 / sinth - 1);
    double msq = m * m;
    double con =
        Values.PI
            * projection.R
            * (al * (g - msq) + Math.sqrt(asq * (g - msq) * (g - msq) - (msq + asq) * (gsq - msq)))
            / (msq + asq);

    if (dlon < 0) {
      con = -con;
    }
    x = projection.x0 + con;

    double q = asq + g;
    con =
        Values.PI
            * projection.R
            * (m * q - al * Math.sqrt((msq + asq) * (asq + 1) - q * q))
            / (msq + asq);

    if (lat >= 0) {
      y = projection.y0 + con;
    } else {
      y = projection.y0 - con;
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

    // Handle center point (0, 0)
    if (Math.abs(point.x) < Values.EPSLN && Math.abs(point.y) < Values.EPSLN) {
      point.x = projection.long0;
      point.y = 0;
      return point;
    }

    double con = Values.PI * projection.R;
    double xx = point.x / con;
    double yy = point.y / con;
    double xys = xx * xx + yy * yy;
    double c1 = -Math.abs(yy) * (1 + xys);
    double c2 = c1 - 2 * yy * yy + xx * xx;
    double c3 = -2 * c1 + 1 + 2 * yy * yy + xys * xys;
    double d = yy * yy / c3 + (2 * c2 * c2 * c2 / c3 / c3 / c3 - 9 * c1 * c2 / c3 / c3) / 27;
    double a1 = (c1 - c2 * c2 / 3 / c3) / c3;
    double m1 = 2 * Math.sqrt(-a1 / 3);
    con = ((3 * d) / a1) / m1;

    if (Math.abs(con) > 1) {
      if (con >= 0) {
        con = 1;
      } else {
        con = -1;
      }
    }

    double th1 = Math.acos(con) / 3;
    double lat;
    if (point.y >= 0) {
      lat = (-m1 * Math.cos(th1 + Values.PI / 3) - c2 / 3 / c3) * Values.PI;
    } else {
      lat = -(-m1 * Math.cos(th1 + Values.PI / 3) - c2 / 3 / c3) * Values.PI;
    }

    double lon;
    if (Math.abs(xx) < Values.EPSLN) {
      lon = projection.long0;
    } else {
      lon =
          MathUtils.adjustLon(
              projection.long0
                  + Values.PI
                      * (xys - 1 + Math.sqrt(1 + 2 * (xx * xx - yy * yy) + xys * xys))
                      / 2
                      / xx);
    }

    point.x = lon;
    point.y = lat;
    return point;
  }

  /** Projection names */
  public static final String[] NAMES = {
    "Van_der_Grinten_I", "VanDerGrinten", "Van_der_Grinten", "vandg"
  };
}
