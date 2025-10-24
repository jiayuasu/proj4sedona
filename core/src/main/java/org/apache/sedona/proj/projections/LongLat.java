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

import org.apache.sedona.proj.core.Point;
import org.apache.sedona.proj.core.Projection;

/**
 * Geographic (longitude/latitude) projection. Also known as the identity projection or longlat.
 * Ported from proj4js longlat.js
 *
 * <p>This projection performs no transformation - it simply returns the input coordinates
 * unchanged. It's used for geographic coordinate systems (lat/lon) like WGS84 (EPSG:4326).
 */
public class LongLat {

  /**
   * Initialize the LongLat projection. No initialization needed for this projection.
   *
   * @param projection the projection to initialize
   */
  public static void init(Projection projection) {
    // No-op for longlat
  }

  /**
   * Forward transformation (identity function - no transformation).
   *
   * @param point the geographic point
   * @param projection the projection parameters (unused)
   * @return the same point (no transformation)
   */
  public static Point forward(Projection projection, Point point) {
    return point;
  }

  /**
   * Inverse transformation (identity function - no transformation).
   *
   * @param point the geographic point
   * @param projection the projection parameters (unused)
   * @return the same point (no transformation)
   */
  public static Point inverse(Projection projection, Point point) {
    return point;
  }

  /** Projection names */
  public static final String[] NAMES = {"longlat", "identity"};
}
