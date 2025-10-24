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
 * Equal Earth projection implementation. Ported from proj4js eqearth.js
 *
 * <p>Copyright 2018 Bernie Jenny, Monash University, Melbourne, Australia.
 *
 * <p>Equal Earth is a projection inspired by the Robinson projection, but unlike the Robinson
 * projection retains the relative size of areas. The projection was designed in 2018 by Bojan
 * Savric, Tom Patterson and Bernhard Jenny.
 *
 * <p>Publication: Bojan Savric, Tom Patterson and Bernhard Jenny (2018). The Equal Earth map
 * projection, International Journal of Geographical Information Science, DOI:
 * 10.1080/13658816.2018.1504949
 */
public class EqualEarth {

  private static final double A1 = 1.340264;
  private static final double A2 = -0.081106;
  private static final double A3 = 0.000893;
  private static final double A4 = 0.003796;
  private static final double M = Math.sqrt(3) / 2.0;

  /**
   * Initialize the Equal Earth projection parameters.
   *
   * @param projection the projection to initialize
   */
  public static void init(Projection projection) {
    projection.es = 0;
  }

  /**
   * Forward transformation from geographic to projected coordinates (lon/lat to x/y).
   *
   * @param point the geographic point (lon, lat in radians)
   * @param projection the projection parameters
   * @return the projected point (x, y)
   */
  public static Point forward(Projection projection, Point point) {
    double lam = MathUtils.adjustLon(point.x - projection.long0);
    double phi = point.y;

    double paramLat = Math.asin(M * Math.sin(phi));
    double paramLatSq = paramLat * paramLat;
    double paramLatPow6 = paramLatSq * paramLatSq * paramLatSq;

    double x =
        lam
            * Math.cos(paramLat)
            / (M * (A1 + 3 * A2 * paramLatSq + paramLatPow6 * (7 * A3 + 9 * A4 * paramLatSq)));
    double y = paramLat * (A1 + A2 * paramLatSq + paramLatPow6 * (A3 + A4 * paramLatSq));

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
    double x = (point.x - projection.x0) / projection.a;
    double y = (point.y - projection.y0) / projection.a;

    double EPS = 1e-9;
    int NITER = 12;
    double paramLat = y;
    double paramLatSq, paramLatPow6, fy, fpy, dlat;

    for (int i = 0; i < NITER; ++i) {
      paramLatSq = paramLat * paramLat;
      paramLatPow6 = paramLatSq * paramLatSq * paramLatSq;
      fy = paramLat * (A1 + A2 * paramLatSq + paramLatPow6 * (A3 + A4 * paramLatSq)) - y;
      fpy = A1 + 3 * A2 * paramLatSq + paramLatPow6 * (7 * A3 + 9 * A4 * paramLatSq);
      paramLat -= dlat = fy / fpy;
      if (Math.abs(dlat) < EPS) {
        break;
      }
    }

    paramLatSq = paramLat * paramLat;
    paramLatPow6 = paramLatSq * paramLatSq * paramLatSq;
    x =
        M
            * x
            * (A1 + 3 * A2 * paramLatSq + paramLatPow6 * (7 * A3 + 9 * A4 * paramLatSq))
            / Math.cos(paramLat);
    y = Math.asin(Math.sin(paramLat) / M);

    point.x = MathUtils.adjustLon(x + projection.long0);
    point.y = y;
    return point;
  }

  /** Projection names */
  public static final String[] NAMES = {"eqearth", "Equal Earth", "Equal_Earth"};
}
