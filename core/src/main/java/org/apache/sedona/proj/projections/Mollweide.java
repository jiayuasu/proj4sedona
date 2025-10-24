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
 * Mollweide projection implementation. Ported from proj4js moll.js
 *
 * <p>The Mollweide projection is a pseudocylindrical equal-area map projection. It sacrifices
 * accuracy of angle and shape in favor of accurately representing area. The projection was first
 * published by Karl Brandan Mollweide in 1805.
 */
public class Mollweide {

  /**
   * Initialize the Mollweide projection. No special initialization needed.
   *
   * @param projection the projection to initialize
   */
  public static void init(Projection projection) {
    // No-op
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

    double deltaLon = MathUtils.adjustLon(lon - projection.long0);
    double theta = lat;
    double con = Values.PI * Math.sin(lat);

    // Iterate using the Newton-Raphson method to find theta
    while (true) {
      double deltaTheta = -(theta + Math.sin(theta) - con) / (1 + Math.cos(theta));
      theta += deltaTheta;
      if (Math.abs(deltaTheta) < Values.EPSLN) {
        break;
      }
    }
    theta /= 2;

    // If the latitude is 90 deg, force the x coordinate to be "0 + false easting"
    // This is done here because of precision problems with "cos(theta)"
    if (Values.HALF_PI - Math.abs(lat) < Values.EPSLN) {
      deltaLon = 0;
    }

    double x = 0.900316316158 * projection.a * deltaLon * Math.cos(theta) + projection.x0;
    double y = 1.4142135623731 * projection.a * Math.sin(theta) + projection.y0;

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

    double arg = point.y / (1.4142135623731 * projection.a);

    // Because of division by zero problems, 'arg' cannot be 1.
    // Therefore a number very close to one is used instead.
    if (Math.abs(arg) > 0.999999999999) {
      arg = 0.999999999999;
    }

    double theta = Math.asin(arg);
    double lon =
        MathUtils.adjustLon(
            projection.long0 + (point.x / (0.900316316158 * projection.a * Math.cos(theta))));

    if (lon < (-Values.PI)) {
      lon = -Values.PI;
    }
    if (lon > Values.PI) {
      lon = Values.PI;
    }

    arg = (2 * theta + Math.sin(2 * theta)) / Values.PI;
    if (Math.abs(arg) > 1) {
      arg = 1;
    }
    double lat = Math.asin(arg);

    point.x = lon;
    point.y = lat;
    return point;
  }

  /** Projection names */
  public static final String[] NAMES = {"Mollweide", "moll"};
}
