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
 * Cylindrical Equal Area projection implementation. Ported from proj4js/lib/projections/cea.js
 *
 * <p>Reference: "Cartographic Projection Procedures for the UNIX Environment- A User's Manual" by
 * Gerald I. Evenden, USGS Open File Report 90-284 and Release 4 Interim Reports (2003)
 */
public class CylindricalEqualArea {

  // Projection-specific parameter
  private double k0;
  private boolean sphere;

  /**
   * Initialize the Cylindrical Equal Area projection.
   *
   * @param proj the projection definition
   */
  public void initialize(Projection proj) {
    this.sphere = proj.sphere;

    // For ellipsoidal case, compute k0
    if (!this.sphere) {
      this.k0 = MathUtils.msfnz(proj.e, Math.sin(proj.lat_ts), Math.cos(proj.lat_ts));
    }
  }

  /**
   * Forward transformation: lat,long to x,y.
   *
   * @param lon longitude in radians
   * @param lat latitude in radians
   * @param proj projection parameters
   * @return array containing [x, y] coordinates
   */
  public double[] forward(double lon, double lat, Projection proj) {
    double x, y;

    // Adjust longitude
    double dlon = MathUtils.adjustLon(lon - proj.long0);

    if (this.sphere) {
      // Spherical case
      x = proj.x0 + proj.a * dlon * Math.cos(proj.lat_ts);
      y = proj.y0 + proj.a * Math.sin(lat) / Math.cos(proj.lat_ts);
    } else {
      // Ellipsoidal case
      double qs = MathUtils.qsfnz(proj.e, Math.sin(lat));
      x = proj.x0 + proj.a * this.k0 * dlon;
      y = proj.y0 + proj.a * qs * 0.5 / this.k0;
    }

    return new double[] {x, y};
  }

  /**
   * Inverse transformation: x,y to lat,long.
   *
   * @param x x coordinate
   * @param y y coordinate
   * @param proj projection parameters
   * @return array containing [longitude, latitude] in radians
   */
  public double[] inverse(double x, double y, Projection proj) {
    x -= proj.x0;
    y -= proj.y0;

    double lon, lat;

    if (this.sphere) {
      // Spherical case
      lon = MathUtils.adjustLon(proj.long0 + (x / proj.a) / Math.cos(proj.lat_ts));
      lat = Math.asin((y / proj.a) * Math.cos(proj.lat_ts));
    } else {
      // Ellipsoidal case
      lat = MathUtils.iqsfnz(proj.e, 2 * y * this.k0 / proj.a);
      lon = MathUtils.adjustLon(proj.long0 + x / (proj.a * this.k0));
    }

    return new double[] {lon, lat};
  }

  // Static methods to match the pattern used by other projections

  /**
   * Initialize the Cylindrical Equal Area projection.
   *
   * @param proj projection parameters to initialize
   */
  public static void init(Projection proj) {
    CylindricalEqualArea cea = new CylindricalEqualArea();
    cea.initialize(proj);

    // Store the instance in the projection for later use
    proj.cea = cea;
  }

  /**
   * Forward transformation for Cylindrical Equal Area.
   *
   * @param proj projection parameters
   * @param point input point with longitude and latitude
   * @return transformed point with x and y coordinates
   */
  public static Point forward(Projection proj, Point point) {
    CylindricalEqualArea cea = (CylindricalEqualArea) proj.cea;
    if (cea == null) {
      throw new IllegalStateException("Projection not initialized");
    }

    double[] result = cea.forward(point.x, point.y, proj);
    return new Point(result[0], result[1], point.z, point.m);
  }

  /**
   * Inverse transformation for Cylindrical Equal Area.
   *
   * @param proj projection parameters
   * @param point input point with x and y coordinates
   * @return transformed point with longitude and latitude
   */
  public static Point inverse(Projection proj, Point point) {
    CylindricalEqualArea cea = (CylindricalEqualArea) proj.cea;
    if (cea == null) {
      throw new IllegalStateException("Projection not initialized");
    }

    double[] result = cea.inverse(point.x, point.y, proj);
    return new Point(result[0], result[1], point.z, point.m);
  }
}
