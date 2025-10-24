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

/** Test cases for Orthographic projection. */
public class OrthographicTest {

  private static final double TOLERANCE = 1.0;
  private static final double COORD_TOLERANCE = 1e-8;

  @Test
  public void testOrthographicInitialization() {
    String projString = "+proj=ortho +lat_0=40 +lon_0=-100 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    assertNotNull(proj);
    assertEquals("ortho", proj.projName);
  }

  @Test
  public void testOrthographicCenter() {
    String projString = "+proj=ortho +lat_0=40 +lon_0=-100 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    // Test center point
    Point input = new Point(Math.toRadians(-100), Math.toRadians(40));
    Point forward = proj.forward.transform(new Point(input.x, input.y));
    assertNotNull(forward);

    // At center, coordinates should be close to 0,0
    assertEquals(0, forward.x, TOLERANCE);
    assertEquals(0, forward.y, TOLERANCE);

    // Test round-trip
    Point inverse = proj.inverse.transform(new Point(forward.x, forward.y));
    assertNotNull(inverse);

    assertEquals(input.x, inverse.x, COORD_TOLERANCE);
    assertEquals(input.y, inverse.y, COORD_TOLERANCE);
  }

  @Test
  public void testOrthographicEquatorial() {
    String projString = "+proj=ortho +lat_0=0 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    // Test visible hemisphere
    double[][] testPoints = {
      {0.0, 0.0}, // Center
      {45.0, 0.0}, // Eastern horizon
      {-45.0, 0.0}, // Western horizon
      {0.0, 45.0}, // Northern horizon
      {0.0, -45.0}, // Southern horizon
      {30.0, 30.0}, // Northeast
      {-30.0, -30.0}, // Southwest
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
  public void testOrthographicPolar() {
    // Orthographic from North Pole
    String projString = "+proj=ortho +lat_0=90 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    Point input = new Point(Math.toRadians(10.0), Math.toRadians(60.0));
    Point forward = proj.forward.transform(new Point(input.x, input.y));
    assertNotNull(forward);

    // Test round-trip
    Point inverse = proj.inverse.transform(new Point(forward.x, forward.y));
    assertNotNull(inverse);

    assertEquals(input.x, inverse.x, COORD_TOLERANCE);
    assertEquals(input.y, inverse.y, COORD_TOLERANCE);
  }
}
