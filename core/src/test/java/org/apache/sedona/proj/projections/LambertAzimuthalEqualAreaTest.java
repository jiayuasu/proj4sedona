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
 * Test cases for Lambert Azimuthal Equal Area projection (LAEA). Tests various aspects including
 * polar, equatorial, and oblique aspects.
 */
public class LambertAzimuthalEqualAreaTest {

  private static final double TOLERANCE = 1e-6;
  private static final double COORD_TOLERANCE = 1e-8;

  @Test
  public void testProjectionInitialization() {
    String projString = "+proj=laea +lat_0=45 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    assertNotNull(proj);
    assertEquals("laea", proj.projName);
    assertEquals(Math.toRadians(45), proj.lat0, COORD_TOLERANCE);
    assertEquals(0.0, proj.long0, COORD_TOLERANCE);
  }

  @Test
  public void testNorthPolarAspect() {
    // LAEA North Pole
    String projString = "+proj=laea +lat_0=90 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    // Test point: 10°E, 60°N
    Point input = new Point(Math.toRadians(10.0), Math.toRadians(60.0));
    Point forward = proj.forward.transform(new Point(input.x, input.y));
    assertNotNull(forward);

    // Test round-trip
    Point inverse = proj.inverse.transform(new Point(forward.x, forward.y));
    assertNotNull(inverse);

    assertEquals(input.x, inverse.x, COORD_TOLERANCE, "Longitude should match after round-trip");
    assertEquals(input.y, inverse.y, COORD_TOLERANCE, "Latitude should match after round-trip");
  }

  @Test
  public void testSouthPolarAspect() {
    // LAEA South Pole
    String projString = "+proj=laea +lat_0=-90 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    // Test point: 45°E, -60°S
    Point input = new Point(Math.toRadians(45.0), Math.toRadians(-60.0));
    Point forward = proj.forward.transform(new Point(input.x, input.y));
    assertNotNull(forward);

    // Test round-trip
    Point inverse = proj.inverse.transform(new Point(forward.x, forward.y));
    assertNotNull(inverse);

    assertEquals(input.x, inverse.x, COORD_TOLERANCE, "Longitude should match after round-trip");
    assertEquals(input.y, inverse.y, COORD_TOLERANCE, "Latitude should match after round-trip");
  }

  @Test
  public void testEquatorialAspect() {
    // LAEA Equatorial
    String projString = "+proj=laea +lat_0=0 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    // Test point: 10°E, 20°N
    Point input = new Point(Math.toRadians(10.0), Math.toRadians(20.0));
    Point forward = proj.forward.transform(new Point(input.x, input.y));
    assertNotNull(forward);

    // Test round-trip
    Point inverse = proj.inverse.transform(new Point(forward.x, forward.y));
    assertNotNull(inverse);

    assertEquals(input.x, inverse.x, COORD_TOLERANCE, "Longitude should match after round-trip");
    assertEquals(input.y, inverse.y, COORD_TOLERANCE, "Latitude should match after round-trip");
  }

  @Test
  public void testObliqueAspect() {
    // LAEA Oblique (centered on 40°N, 100°W)
    String projString = "+proj=laea +lat_0=40 +lon_0=-100 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    // Test point: 90°W, 35°N
    Point input = new Point(Math.toRadians(-90.0), Math.toRadians(35.0));
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
    // LAEA with spherical approximation
    String projString = "+proj=laea +lat_0=45 +lon_0=0 +x_0=0 +y_0=0 +R=6371000 +units=m";
    Projection proj = new Projection(projString);

    // Test point: 5°E, 50°N
    Point input = new Point(Math.toRadians(5.0), Math.toRadians(50.0));
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
    // LAEA centered on specific location
    String projString =
        "+proj=laea +lat_0=52 +lon_0=10 +x_0=4321000 +y_0=3210000 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    // Test center point
    Point input = new Point(Math.toRadians(10.0), Math.toRadians(52.0));
    Point forward = proj.forward.transform(new Point(input.x, input.y));
    assertNotNull(forward);

    // At center, x should equal false easting, y should equal false northing
    assertEquals(4321000.0, forward.x, TOLERANCE);
    assertEquals(3210000.0, forward.y, TOLERANCE);
  }

  @Test
  public void testMultiplePoints() {
    String projString = "+proj=laea +lat_0=45 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    // Test multiple points for round-trip accuracy
    double[][] testPoints = {
      {0.0, 0.0}, // Equator at prime meridian
      {10.0, 20.0}, // Low latitude
      {-45.0, 60.0}, // High latitude
      {90.0, 30.0}, // East
      {-90.0, 30.0}, // West
      {0.0, 45.0}, // At center latitude
      {0.0, 80.0}, // Near pole
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
  public void testEqualAreaProperty() {
    // LAEA should preserve area
    String projString = "+proj=laea +lat_0=0 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    // This is a basic test - a more rigorous test would compare areas of polygons
    // before and after transformation
    Point input = new Point(Math.toRadians(10.0), Math.toRadians(10.0));
    Point forward = proj.forward.transform(new Point(input.x, input.y));
    assertNotNull(forward, "Equal area projection should not fail for valid points");
  }
}
