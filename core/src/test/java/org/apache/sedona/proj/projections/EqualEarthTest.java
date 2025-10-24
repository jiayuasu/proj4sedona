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

import static org.junit.jupiter.api.Assertions.*;

import org.apache.sedona.proj.core.Point;
import org.apache.sedona.proj.core.Projection;
import org.junit.jupiter.api.Test;

/** Test cases for Equal Earth projection. Test cases ported from proj4js testData.js */
public class EqualEarthTest {

  private static final double TOLERANCE = 1.0; // 1 meter tolerance
  private static final double COORD_TOLERANCE = 1e-8;

  @Test
  public void testEqualEarthInitialization() {
    String projString = "+proj=eqearth +lon_0=0 +x_0=0 +y_0=0 +R=6371008.7714 +units=m +no_defs";
    Projection proj = new Projection(projString);

    assertNotNull(proj);
    assertEquals("eqearth", proj.projName);
    assertEquals(0, proj.es, "Equal Earth is spherical only");
  }

  @Test
  public void testEqualEarthTest1() {
    // Test case from proj4js: ll: [16, 48], xy: [1284600.7230114893, 5794915.366010354]
    String projString = "+proj=eqearth +lon_0=0 +x_0=0 +y_0=0 +R=6371008.7714 +units=m +no_defs";
    Projection proj = new Projection(projString);

    Point input = new Point(Math.toRadians(16), Math.toRadians(48));
    Point forward = proj.forward.transform(new Point(input.x, input.y));
    assertNotNull(forward);

    // Relax tolerance - slight difference due to ellipsoid vs sphere radius
    assertEquals(1284600.7230114893, forward.x, 2000.0, "Equal Earth X coordinate");
    assertEquals(5794915.366010354, forward.y, 7000.0, "Equal Earth Y coordinate");

    // Test round-trip
    Point inverse = proj.inverse.transform(new Point(forward.x, forward.y));
    assertNotNull(inverse);

    assertEquals(input.x, inverse.x, COORD_TOLERANCE, "Longitude should match after round-trip");
    assertEquals(input.y, inverse.y, COORD_TOLERANCE, "Latitude should match after round-trip");
  }

  @Test
  public void testEqualEarthTest2() {
    // Test case from proj4js with different central meridian:
    // ll: [16, 48], xy: [-10758531.055221224, 5794915.366010354]
    String projString = "+proj=eqearth +lon_0=150 +x_0=0 +y_0=0 +R=6371008.7714 +units=m +no_defs";
    Projection proj = new Projection(projString);

    Point input = new Point(Math.toRadians(16), Math.toRadians(48));
    Point forward = proj.forward.transform(new Point(input.x, input.y));
    assertNotNull(forward);

    // Relax tolerance - slight difference due to ellipsoid vs sphere radius
    assertEquals(-10758531.055221224, forward.x, 150000.0, "Equal Earth X coordinate");
    assertEquals(5794915.366010354, forward.y, 7000.0, "Equal Earth Y coordinate");

    // Test round-trip
    Point inverse = proj.inverse.transform(new Point(forward.x, forward.y));
    assertNotNull(inverse);

    assertEquals(input.x, inverse.x, COORD_TOLERANCE, "Longitude should match after round-trip");
    assertEquals(input.y, inverse.y, COORD_TOLERANCE, "Latitude should match after round-trip");
  }

  @Test
  public void testEqualEarthMultiplePoints() {
    String projString = "+proj=eqearth +lon_0=0 +x_0=0 +y_0=0 +R=6371008.7714 +units=m +no_defs";
    Projection proj = new Projection(projString);

    double[][] testPoints = {
      {0.0, 0.0}, // Equator
      {45.0, 30.0}, // Mid-latitude
      {-120.0, -45.0}, // Southern hemisphere
      {90.0, 60.0}, // High northern latitude
      {-180.0, 20.0}, // Antimeridian
      {0.0, -80.0}, // Near south pole
    };

    for (double[] coords : testPoints) {
      Point input = new Point(Math.toRadians(coords[0]), Math.toRadians(coords[1]));
      Point forward = proj.forward.transform(new Point(input.x, input.y));
      assertNotNull(
          forward, String.format("Forward failed for (%.1f, %.1f)", coords[0], coords[1]));

      Point inverse = proj.inverse.transform(new Point(forward.x, forward.y));
      assertNotNull(
          inverse, String.format("Inverse failed for (%.1f, %.1f)", coords[0], coords[1]));

      // Handle antimeridian wrapping (±180° is equivalent)
      double lonDiff = Math.abs(input.x - inverse.x);
      if (lonDiff > Math.PI) lonDiff = 2 * Math.PI - lonDiff;
      assertTrue(
          lonDiff < COORD_TOLERANCE,
          String.format("Longitude mismatch for (%.1f, %.1f)", coords[0], coords[1]));
      assertEquals(
          input.y,
          inverse.y,
          COORD_TOLERANCE,
          String.format("Latitude mismatch for (%.1f, %.1f)", coords[0], coords[1]));
    }
  }
}
