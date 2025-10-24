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
import org.apache.sedona.proj.core.Point;
import org.apache.sedona.proj.core.Projection;

/**
 * Equirectangular projection implementation. Also known as Plate Carrée or equi. Ported from
 * proj4js equi.js
 *
 * <p>The Equirectangular projection (also called the Equidistant Cylindrical projection or Plate
 * Carrée) is a simple map projection that maps meridians to vertical straight lines of constant
 * spacing, and circles of latitude to horizontal straight lines of constant spacing.
 */
public class Equirectangular {

  /**
   * Initialize the Equirectangular projection parameters.
   *
   * @param projection the projection to initialize
   */
  public static void init(Projection projection) {
    // Parameters are already initialized with defaults in Projection constructor
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
    double x = projection.x0 + projection.a * dlon * Math.cos(projection.lat0);
    double y = projection.y0 + projection.a * lat;

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

    double lat = point.y / projection.a;
    double lon =
        MathUtils.adjustLon(
            projection.long0 + point.x / (projection.a * Math.cos(projection.lat0)));

    point.x = lon;
    point.y = lat;
    return point;
  }

  /** Projection names */
  public static final String[] NAMES = {"equi", "Equirectangular", "Plate_Carree", "eqc"};
}
