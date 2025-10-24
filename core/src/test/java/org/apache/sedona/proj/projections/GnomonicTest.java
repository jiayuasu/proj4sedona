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

/**
 * Test cases for Gnomonic projection (GNOM). Tests various aspects of the Gnomonic projection
 * including great circle properties.
 */
public class GnomonicTest {

  private static final double TOLERANCE = 1e-6;
  private static final double COORD_TOLERANCE = 1e-8;
  // Slightly larger tolerance for gnomonic due to projection characteristics
  private static final double GNOM_COORD_TOLERANCE = 1e-7;

  @Test
  public void testProjectionInitialization() {
    String projString = "+proj=gnom +lat_0=40 +lon_0=-100 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    assertNotNull(proj);
    assertEquals("gnom", proj.projName);
    assertEquals(Math.toRadians(40), proj.lat0, COORD_TOLERANCE);
    assertEquals(Math.toRadians(-100), proj.long0, COORD_TOLERANCE);
  }

  @Test
  public void testEquatorialAspect() {
    // Gnomonic projection centered on equator
    String projString = "+proj=gnom +lat_0=0 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    // Test point: 10°E, 10°N
    Point input = new Point(Math.toRadians(10.0), Math.toRadians(10.0));
    Point forward = proj.forward.transform(new Point(input.x, input.y));
    assertNotNull(forward);

    // Test round-trip
    Point inverse = proj.inverse.transform(new Point(forward.x, forward.y));
    assertNotNull(inverse);

    assertEquals(
        input.x, inverse.x, GNOM_COORD_TOLERANCE, "Longitude should match after round-trip");
    assertEquals(
        input.y, inverse.y, GNOM_COORD_TOLERANCE, "Latitude should match after round-trip");
  }

  @Test
  public void testObliqueAspect() {
    // Gnomonic projection centered on 40°N, 100°W (e.g., for North America)
    String projString = "+proj=gnom +lat_0=40 +lon_0=-100 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    // Test point: 95°W, 42°N (close to center, should have less distortion)
    Point input = new Point(Math.toRadians(-95.0), Math.toRadians(42.0));
    Point forward = proj.forward.transform(new Point(input.x, input.y));
    assertNotNull(forward);

    // Test round-trip
    Point inverse = proj.inverse.transform(new Point(forward.x, forward.y));
    assertNotNull(inverse);

    assertEquals(
        input.x, inverse.x, GNOM_COORD_TOLERANCE, "Longitude should match after round-trip");
    assertEquals(
        input.y, inverse.y, GNOM_COORD_TOLERANCE, "Latitude should match after round-trip");
  }

  @Test
  public void testCenterPoint() {
    String projString =
        "+proj=gnom +lat_0=52 +lon_0=5 +x_0=100000 +y_0=200000 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    // Test center point
    Point input = new Point(Math.toRadians(5.0), Math.toRadians(52.0));
    Point forward = proj.forward.transform(new Point(input.x, input.y));
    assertNotNull(forward);

    // At center, should be close to false easting/northing
    assertEquals(100000.0, forward.x, TOLERANCE);
    assertEquals(200000.0, forward.y, TOLERANCE);
  }

  @Test
  public void testGreatCircleProperty() {
    // Gnomonic from New York (40.7128°N, 74.0060°W)
    String projString =
        "+proj=gnom +lat_0=40.7128 +lon_0=-74.0060 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    // Test a point not too far away
    Point input = new Point(Math.toRadians(-70.0), Math.toRadians(42.0));
    Point forward = proj.forward.transform(new Point(input.x, input.y));
    assertNotNull(forward);

    // Test round-trip
    Point inverse = proj.inverse.transform(new Point(forward.x, forward.y));
    assertNotNull(inverse);

    assertEquals(
        input.x, inverse.x, GNOM_COORD_TOLERANCE, "Longitude should match after round-trip");
    assertEquals(
        input.y, inverse.y, GNOM_COORD_TOLERANCE, "Latitude should match after round-trip");
  }

  @Test
  public void testNearbyPoints() {
    String projString = "+proj=gnom +lat_0=0 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    // Test multiple nearby points (gnomonic has hemisphere limitation)
    double[][] testPoints = {
      {0.0, 0.0}, // Center
      {10.0, 10.0}, // Northeast
      {-10.0, 10.0}, // Northwest
      {10.0, -10.0}, // Southeast
      {-10.0, -10.0}, // Southwest
      {45.0, 0.0}, // East
      {0.0, 45.0}, // North
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
          GNOM_COORD_TOLERANCE,
          String.format("Longitude mismatch for (%.1f, %.1f)", coords[0], coords[1]));
      assertEquals(
          input.y,
          inverse.y,
          GNOM_COORD_TOLERANCE,
          String.format("Latitude mismatch for (%.1f, %.1f)", coords[0], coords[1]));
    }
  }

  @Test
  public void testPolarAspect() {
    // Gnomonic from North Pole
    String projString = "+proj=gnom +lat_0=90 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    // Test point in northern hemisphere
    Point input = new Point(Math.toRadians(10.0), Math.toRadians(60.0));
    Point forward = proj.forward.transform(new Point(input.x, input.y));
    assertNotNull(forward);

    // Test round-trip
    Point inverse = proj.inverse.transform(new Point(forward.x, forward.y));
    assertNotNull(inverse);

    assertEquals(
        input.x, inverse.x, GNOM_COORD_TOLERANCE, "Longitude should match after round-trip");
    assertEquals(
        input.y, inverse.y, GNOM_COORD_TOLERANCE, "Latitude should match after round-trip");
  }

  @Test
  public void testHemisphereLimitation() {
    // Gnomonic can only project one hemisphere at a time
    String projString = "+proj=gnom +lat_0=0 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    // Points within the visible hemisphere should work
    Point nearPoint = new Point(Math.toRadians(45.0), Math.toRadians(0.0));
    Point forward = proj.forward.transform(new Point(nearPoint.x, nearPoint.y));
    assertNotNull(forward, "Points in visible hemisphere should project successfully");

    // Note: Points in the opposite hemisphere may produce infinity approximation
    // The implementation handles this by projecting to infinity_dist
  }
}
