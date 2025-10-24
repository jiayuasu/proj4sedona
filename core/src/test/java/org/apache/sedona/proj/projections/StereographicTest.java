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
 * Test cases for Stereographic projection (STERE). Tests various aspects including polar (UPS),
 * oblique, and equatorial aspects.
 */
public class StereographicTest {

  private static final double TOLERANCE = 1e-6;
  private static final double COORD_TOLERANCE = 1e-8;

  @Test
  public void testProjectionInitialization() {
    String projString =
        "+proj=stere +lat_0=90 +lon_0=0 +k_0=0.994 +x_0=2000000 +y_0=2000000 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    assertNotNull(proj);
    assertEquals("stere", proj.projName);
    assertEquals(Math.toRadians(90), proj.lat0, COORD_TOLERANCE);
    assertEquals(0.994, proj.k0, COORD_TOLERANCE);
  }

  @Test
  public void testNorthPolarStereographic() {
    // Universal Polar Stereographic (UPS) North
    String projString =
        "+proj=stere +lat_0=90 +lon_0=0 +k_0=0.994 +x_0=2000000 +y_0=2000000 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    // Test point: 0°E, 84°N
    Point input = new Point(Math.toRadians(0.0), Math.toRadians(84.0));
    Point forward = proj.forward.transform(new Point(input.x, input.y));
    assertNotNull(forward);

    // Test round-trip
    Point inverse = proj.inverse.transform(new Point(forward.x, forward.y));
    assertNotNull(inverse);

    assertEquals(input.x, inverse.x, COORD_TOLERANCE, "Longitude should match after round-trip");
    assertEquals(input.y, inverse.y, COORD_TOLERANCE, "Latitude should match after round-trip");
  }

  @Test
  public void testSouthPolarStereographic() {
    // Universal Polar Stereographic (UPS) South
    String projString =
        "+proj=stere +lat_0=-90 +lon_0=0 +k_0=0.994 +x_0=2000000 +y_0=2000000 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    // Test point: 45°E, -84°S
    Point input = new Point(Math.toRadians(45.0), Math.toRadians(-84.0));
    Point forward = proj.forward.transform(new Point(input.x, input.y));
    assertNotNull(forward);

    // Test round-trip
    Point inverse = proj.inverse.transform(new Point(forward.x, forward.y));
    assertNotNull(inverse);

    assertEquals(input.x, inverse.x, COORD_TOLERANCE, "Longitude should match after round-trip");
    assertEquals(input.y, inverse.y, COORD_TOLERANCE, "Latitude should match after round-trip");
  }

  @Test
  public void testObliqueStereographic() {
    // Oblique Stereographic (typical for mid-latitude regions)
    String projString =
        "+proj=stere +lat_0=46.5 +lon_0=25 +k_0=0.99975 +x_0=500000 +y_0=500000 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    // Test point: 26°E, 47°N
    Point input = new Point(Math.toRadians(26.0), Math.toRadians(47.0));
    Point forward = proj.forward.transform(new Point(input.x, input.y));
    assertNotNull(forward);

    // Test round-trip
    Point inverse = proj.inverse.transform(new Point(forward.x, forward.y));
    assertNotNull(inverse);

    assertEquals(input.x, inverse.x, COORD_TOLERANCE, "Longitude should match after round-trip");
    assertEquals(input.y, inverse.y, COORD_TOLERANCE, "Latitude should match after round-trip");
  }

  @Test
  public void testSphericalCase() {
    // Stereographic with spherical approximation
    String projString = "+proj=stere +lat_0=90 +lon_0=0 +k_0=1 +x_0=0 +y_0=0 +R=6371000 +units=m";
    Projection proj = new Projection(projString);

    // Test point: 10°E, 80°N
    Point input = new Point(Math.toRadians(10.0), Math.toRadians(80.0));
    Point forward = proj.forward.transform(new Point(input.x, input.y));
    assertNotNull(forward);

    // Test round-trip
    Point inverse = proj.inverse.transform(new Point(forward.x, forward.y));
    assertNotNull(inverse);

    assertEquals(input.x, inverse.x, COORD_TOLERANCE, "Longitude should match after round-trip");
    assertEquals(input.y, inverse.y, COORD_TOLERANCE, "Latitude should match after round-trip");
  }

  @Test
  public void testCenterPoint() {
    // Test that center point transforms correctly
    String projString =
        "+proj=stere +lat_0=52 +lon_0=5 +k_0=1 +x_0=155000 +y_0=463000 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    // Test center point
    Point input = new Point(Math.toRadians(5.0), Math.toRadians(52.0));
    Point forward = proj.forward.transform(new Point(input.x, input.y));
    assertNotNull(forward);

    // At center, should be close to false easting/northing
    assertEquals(155000.0, forward.x, TOLERANCE);
    assertEquals(463000.0, forward.y, TOLERANCE);
  }

  @Test
  public void testMultiplePoints() {
    String projString = "+proj=stere +lat_0=90 +lon_0=0 +k_0=1 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    // Test multiple points for round-trip accuracy
    double[][] testPoints = {
      {0.0, 85.0}, // Near pole
      {45.0, 80.0}, // High latitude
      {90.0, 75.0}, // High latitude, 90°E
      {-90.0, 70.0}, // High latitude, 90°W
      {180.0, 65.0}, // Antimeridian
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
  public void testConformalProperty() {
    // Stereographic should be conformal (preserve angles)
    // This is a basic test - actual conformal verification would require
    // computing scale factors and comparing angles
    String projString = "+proj=stere +lat_0=45 +lon_0=0 +k_0=1 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    Point input = new Point(Math.toRadians(10.0), Math.toRadians(50.0));
    Point forward = proj.forward.transform(new Point(input.x, input.y));
    assertNotNull(forward, "Conformal projection should not fail for valid points");
  }
}
