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

/** Test cases for Mollweide projection. Tests equal-area pseudocylindrical projection. */
public class MollweideTest {

  private static final double TOLERANCE = 1.0; // 1 meter tolerance
  private static final double COORD_TOLERANCE = 1e-8;

  @Test
  public void testMollweideInitialization() {
    String projString = "+proj=moll +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    assertNotNull(proj);
    assertEquals("moll", proj.projName);
  }

  @Test
  public void testMollweideCenter() {
    String projString = "+proj=moll +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    // Test center point (0, 0)
    Point input = new Point(0, 0);
    Point forward = proj.forward.transform(new Point(input.x, input.y));
    assertNotNull(forward);

    assertEquals(0, forward.x, TOLERANCE, "Center X should be 0");
    assertEquals(0, forward.y, TOLERANCE, "Center Y should be 0");

    // Test round-trip
    Point inverse = proj.inverse.transform(new Point(forward.x, forward.y));
    assertNotNull(inverse);

    assertEquals(input.x, inverse.x, COORD_TOLERANCE, "Longitude should match after round-trip");
    assertEquals(input.y, inverse.y, COORD_TOLERANCE, "Latitude should match after round-trip");
  }

  @Test
  public void testMollweideMultiplePoints() {
    String projString = "+proj=moll +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    double[][] testPoints = {
      {0.0, 0.0}, // Equator at prime meridian
      {90.0, 0.0}, // Equator at 90°E
      {-90.0, 0.0}, // Equator at 90°W
      {0.0, 45.0}, // Prime meridian at 45°N
      {0.0, -45.0}, // Prime meridian at 45°S
      {45.0, 30.0}, // Northeast quadrant
      {-120.0, -30.0}, // Southwest quadrant
      {0.0, 89.0}, // Near north pole
      {0.0, -89.0}, // Near south pole
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

  @Test
  public void testMollweidePoleHandling() {
    // Mollweide should handle poles specially
    String projString = "+proj=moll +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    // At exactly 90° or -90°, longitude is forced to 0
    Point input = new Point(Math.toRadians(45.0), Math.toRadians(90.0));
    Point forward = proj.forward.transform(new Point(input.x, input.y));
    assertNotNull(forward);

    // X coordinate should be close to 0 at pole
    assertEquals(0, forward.x, TOLERANCE, "X should be 0 at north pole");

    // Test round-trip (note: longitude may not match exactly at poles)
    Point inverse = proj.inverse.transform(new Point(forward.x, forward.y));
    assertNotNull(inverse);

    assertEquals(
        Math.toRadians(90.0), inverse.y, COORD_TOLERANCE, "Latitude should be 90° at north pole");
  }
}
