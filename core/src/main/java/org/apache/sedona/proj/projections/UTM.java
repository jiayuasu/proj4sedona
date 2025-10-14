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

import org.apache.sedona.proj.constants.Values;
import org.apache.sedona.proj.core.Point;
import org.apache.sedona.proj.core.Projection;

/**
 * Universal Transverse Mercator (UTM) projection implementation. UTM is a specialized case of the
 * Transverse Mercator projection used for mapping large areas with high accuracy.
 */
public class UTM {

  /**
   * Initializes a UTM projection.
   *
   * @param proj the projection to initialize
   */
  public static void init(Projection proj) {
    // UTM uses Transverse Mercator with specific parameters
    proj.k0 = 0.9996; // UTM scale factor
    proj.x0 = 500000.0; // UTM false easting
    proj.y0 = proj.utmSouth ? 10000000.0 : 0.0; // UTM false northing

    // Calculate central meridian from zone
    if (proj.zone != 0) {
      proj.long0 = (proj.zone - 1) * 6.0 - 180.0 + 3.0; // Convert to radians
      proj.long0 *= Values.D2R;
    }

    // Initialize Transverse Mercator parameters
    TransverseMercator.init(proj);
  }

  /**
   * Forward transformation for UTM.
   *
   * @param proj the projection
   * @param p the input point (longitude, latitude in radians)
   * @return the transformed point (x, y in meters)
   */
  public static Point forward(Projection proj, Point p) {
    return TransverseMercator.forward(proj, p);
  }

  /**
   * Inverse transformation for UTM.
   *
   * @param proj the projection
   * @param p the input point (x, y in meters)
   * @return the transformed point (longitude, latitude in radians)
   */
  public static Point inverse(Projection proj, Point p) {
    return TransverseMercator.inverse(proj, p);
  }

  /**
   * Calculates the UTM zone from longitude.
   *
   * @param lon longitude in degrees
   * @return UTM zone number
   */
  public static int getZoneFromLongitude(double lon) {
    return (int) Math.floor((lon + 180.0) / 6.0) + 1;
  }

  /**
   * Calculates the central meridian for a UTM zone.
   *
   * @param zone UTM zone number
   * @return central meridian in degrees
   */
  public static double getCentralMeridianFromZone(int zone) {
    return (zone - 1) * 6.0 - 180.0 + 3.0;
  }

  /**
   * Determines if a latitude is in the southern hemisphere for UTM.
   *
   * @param lat latitude in degrees
   * @return true if southern hemisphere
   */
  public static boolean isSouthernHemisphere(double lat) {
    return lat < 0.0;
  }
}
