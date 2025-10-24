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

/** Test cases for Robinson projection. Test cases ported from proj4js testData.js */
public class RobinsonTest {

  private static final double TOLERANCE = 1.0; // 1 meter tolerance
  private static final double COORD_TOLERANCE = 1e-6; // Relaxed for Robinson's interpolation

  @Test
  public void testRobinsonInitialization() {
    String projString = "+proj=robin +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs";
    Projection proj = new Projection(projString);

    assertNotNull(proj);
    assertEquals("robin", proj.projName);
  }

  @Test
  public void testRobinsonTest1() {
    // Test case from proj4js: ll: [-15, -35], xy: [-1335949.91, -3743319.07]
    String projString = "+proj=robin +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs";
    Projection proj = new Projection(projString);

    Point input = new Point(Math.toRadians(-15), Math.toRadians(-35));
    Point forward = proj.forward.transform(new Point(input.x, input.y));
    assertNotNull(forward);

    // Check against expected values (from proj4js)
    assertEquals(-1335949.91, forward.x, TOLERANCE, "Robinson X coordinate");
    assertEquals(-3743319.07, forward.y, TOLERANCE, "Robinson Y coordinate");

    // Test round-trip
    Point inverse = proj.inverse.transform(new Point(forward.x, forward.y));
    assertNotNull(inverse);

    assertEquals(input.x, inverse.x, COORD_TOLERANCE, "Longitude should match after round-trip");
    assertEquals(input.y, inverse.y, COORD_TOLERANCE, "Latitude should match after round-trip");
  }

  @Test
  public void testRobinsonTest2() {
    // Test case from proj4js: ll: [-10, 50], xy: [-819964.60, 5326895.52]
    String projString = "+proj=robin +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs";
    Projection proj = new Projection(projString);

    Point input = new Point(Math.toRadians(-10), Math.toRadians(50));
    Point forward = proj.forward.transform(new Point(input.x, input.y));
    assertNotNull(forward);

    // Check against expected values (from proj4js)
    assertEquals(-819964.60, forward.x, TOLERANCE, "Robinson X coordinate");
    assertEquals(5326895.52, forward.y, TOLERANCE, "Robinson Y coordinate");

    // Test round-trip
    Point inverse = proj.inverse.transform(new Point(forward.x, forward.y));
    assertNotNull(inverse);

    assertEquals(input.x, inverse.x, COORD_TOLERANCE, "Longitude should match after round-trip");
    assertEquals(input.y, inverse.y, COORD_TOLERANCE, "Latitude should match after round-trip");
  }

  @Test
  public void testRobinsonWithCustomRadius() {
    // Test case from proj4js: +proj=robin +a=6400000, ll: [80, -20], xy: [7449059.80, -2146370.56]
    String projString = "+proj=robin +a=6400000";
    Projection proj = new Projection(projString);

    Point input = new Point(Math.toRadians(80), Math.toRadians(-20));
    Point forward = proj.forward.transform(new Point(input.x, input.y));
    assertNotNull(forward);

    // Check against expected values (from proj4js)
    assertEquals(7449059.80, forward.x, TOLERANCE, "Robinson X coordinate");
    assertEquals(-2146370.56, forward.y, TOLERANCE, "Robinson Y coordinate");

    // Test round-trip
    Point inverse = proj.inverse.transform(new Point(forward.x, forward.y));
    assertNotNull(inverse);

    assertEquals(input.x, inverse.x, COORD_TOLERANCE, "Longitude should match after round-trip");
    assertEquals(input.y, inverse.y, COORD_TOLERANCE, "Latitude should match after round-trip");
  }

  @Test
  public void testRobinsonWithFalseEastingNorthing() {
    // Test case from proj4js: +proj=robin +lon_0=15 +x_0=100000 +y_0=100000
    // ll: [-35, 40], xy: [-4253493.26, 4376351.58]
    String projString = "+proj=robin +lon_0=15 +x_0=100000 +y_0=100000 +datum=WGS84";
    Projection proj = new Projection(projString);

    Point input = new Point(Math.toRadians(-35), Math.toRadians(40));
    Point forward = proj.forward.transform(new Point(input.x, input.y));
    assertNotNull(forward);

    // Check against expected values (from proj4js)
    assertEquals(-4253493.26, forward.x, TOLERANCE, "Robinson X coordinate");
    assertEquals(4376351.58, forward.y, TOLERANCE, "Robinson Y coordinate");

    // Test round-trip
    Point inverse = proj.inverse.transform(new Point(forward.x, forward.y));
    assertNotNull(inverse);

    assertEquals(input.x, inverse.x, COORD_TOLERANCE, "Longitude should match after round-trip");
    assertEquals(input.y, inverse.y, COORD_TOLERANCE, "Latitude should match after round-trip");
  }

  @Test
  public void testRobinsonPolarRegions() {
    String projString = "+proj=robin +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs";
    Projection proj = new Projection(projString);

    // Test points near poles
    double[][] testPoints = {
      {0.0, 80.0}, // Near north pole
      {45.0, -70.0}, // Near south pole
      {-90.0, 60.0}, // High northern latitude
      {180.0, -50.0}, // High southern latitude
    };

    for (double[] coords : testPoints) {
      Point input = new Point(Math.toRadians(coords[0]), Math.toRadians(coords[1]));
      Point forward = proj.forward.transform(new Point(input.x, input.y));
      assertNotNull(
          forward, String.format("Forward failed for (%.1f, %.1f)", coords[0], coords[1]));

      Point inverse = proj.inverse.transform(new Point(forward.x, forward.y));
      assertNotNull(
          inverse, String.format("Inverse failed for (%.1f, %.1f)", coords[0], coords[1]));

      assertEquals(
          input.x,
          inverse.x,
          COORD_TOLERANCE,
          String.format("Longitude mismatch for (%.1f, %.1f)", coords[0], coords[1]));
      assertEquals(
          input.y,
          inverse.y,
          COORD_TOLERANCE,
          String.format("Latitude mismatch for (%.1f, %.1f)", coords[0], coords[1]));
    }
  }
}
