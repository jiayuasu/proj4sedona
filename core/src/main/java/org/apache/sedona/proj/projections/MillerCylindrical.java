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
 * Miller Cylindrical projection implementation. Ported from proj4js mill.js
 *
 * <p>The Miller cylindrical projection is a modified Mercator projection that uses less extreme
 * distortion near the poles. It was proposed by Osborn Maitland Miller in 1942 and is commonly used
 * for world maps.
 *
 * <p>Reference: "New Equal-Area Map Projections for Noncircular Regions", John P. Snyder, The
 * American Cartographer, Vol 15, No. 4, October 1988, pp. 341-355.
 */
public class MillerCylindrical {

  /**
   * Initialize the Miller Cylindrical projection. No special initialization needed.
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

    double dlon = MathUtils.adjustLon(lon - projection.long0);
    double x = projection.x0 + projection.a * dlon;
    double y =
        projection.y0 + projection.a * Math.log(Math.tan((Values.PI / 4) + (lat / 2.5))) * 1.25;

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

    double lon = MathUtils.adjustLon(projection.long0 + point.x / projection.a);
    double lat = 2.5 * (Math.atan(Math.exp(0.8 * point.y / projection.a)) - Values.PI / 4);

    point.x = lon;
    point.y = lat;
    return point;
  }

  /** Projection names */
  public static final String[] NAMES = {"Miller_Cylindrical", "mill"};
}
