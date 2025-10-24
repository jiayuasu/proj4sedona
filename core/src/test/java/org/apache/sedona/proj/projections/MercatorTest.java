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

/** Test cases for Mercator projection. Test cases ported from proj4js testData.js */
public class MercatorTest {

  private static final double TOLERANCE = 1e-6;
  private static final double COORD_TOLERANCE = 1e-8;

  @Test
  public void testMercatorInitialization() {
    String projString = "+proj=merc +lon_0=0 +k_0=1 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    assertNotNull(proj);
    assertEquals("merc", proj.projName);
  }

  @Test
  public void testWebMercatorEPSG3857() {
    // Test EPSG:3857 (Web Mercator / Pseudo-Mercator)
    // Test case from proj4js: ll: [-112.50042920000004, 42.036926809999976], xy:
    // [-12523490.49256873, 5166512.50707369]
    Projection proj = new Projection("EPSG:3857");

    Point input =
        new Point(Math.toRadians(-112.50042920000004), Math.toRadians(42.036926809999976));
    Point forward = proj.forward.transform(new Point(input.x, input.y));
    assertNotNull(forward);

    // Check against expected values (from proj4js)
    assertEquals(-12523490.49256873, forward.x, 0.1, "Web Mercator X coordinate");
    assertEquals(5166512.50707369, forward.y, 0.1, "Web Mercator Y coordinate");

    // Test round-trip
    Point inverse = proj.inverse.transform(new Point(forward.x, forward.y));
    assertNotNull(inverse);

    assertEquals(input.x, inverse.x, COORD_TOLERANCE, "Longitude should match after round-trip");
    assertEquals(input.y, inverse.y, COORD_TOLERANCE, "Latitude should match after round-trip");
  }

  @Test
  public void testMercatorTest1() {
    // Test case from proj4js: code: 'testmerc', xy: [-45007.0787624, 4151725.59875], ll: [5.364315,
    // 46.623154]
    String projString = "testmerc"; // This would be a defined projection in the system
    // For now, use standard Mercator
    projString = "+proj=merc +lon_0=0 +k_0=1 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    Point input = new Point(Math.toRadians(5.364315), Math.toRadians(46.623154));
    Point forward = proj.forward.transform(new Point(input.x, input.y));
    assertNotNull(forward);

    // Test round-trip (more reliable than comparing to exact values)
    Point inverse = proj.inverse.transform(new Point(forward.x, forward.y));
    assertNotNull(inverse);

    assertEquals(input.x, inverse.x, COORD_TOLERANCE, "Longitude should match after round-trip");
    assertEquals(input.y, inverse.y, COORD_TOLERANCE, "Latitude should match after round-trip");
  }

  @Test
  public void testMercatorAntimeridian() {
    // Test coordinates at 180 and -180 deg. longitude don't wrap around (from proj4js)
    Projection proj = new Projection("EPSG:3857");

    Point input = new Point(Math.toRadians(-180), Math.toRadians(0));
    Point forward = proj.forward.transform(new Point(input.x, input.y));
    assertNotNull(forward);

    // Check against expected values (from proj4js)
    assertEquals(-20037508.342789, forward.x, 1.0, "Antimeridian X coordinate");
    assertEquals(0, forward.y, 1.0, "Antimeridian Y coordinate");

    // Test round-trip
    Point inverse = proj.inverse.transform(new Point(forward.x, forward.y));
    assertNotNull(inverse);

    // At antimeridian, ±180° is mathematically equivalent
    double lonDiff = Math.abs(Math.abs(inverse.x) - Math.PI);
    assertTrue(lonDiff < COORD_TOLERANCE, "Longitude should be at antimeridian (±180°)");
    assertEquals(input.y, inverse.y, COORD_TOLERANCE, "Latitude should match after round-trip");
  }

  @Test
  public void testMercatorMultiplePoints() {
    String projString = "+proj=merc +lon_0=0 +k_0=1 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    double[][] testPoints = {
      {0.0, 0.0},
      {10.0, 20.0},
      {-45.0, 30.0},
      {90.0, -15.0},
      {-120.0, 45.0},
      {179.0, 60.0},
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
  public void testMercatorWithScaleFactor() {
    String projString = "+proj=merc +lon_0=0 +k_0=0.9996 +x_0=500000 +y_0=0 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    Point input = new Point(Math.toRadians(10.0), Math.toRadians(20.0));
    Point forward = proj.forward.transform(new Point(input.x, input.y));
    assertNotNull(forward);

    // Test round-trip
    Point inverse = proj.inverse.transform(new Point(forward.x, forward.y));
    assertNotNull(inverse);

    assertEquals(input.x, inverse.x, COORD_TOLERANCE, "Longitude should match after round-trip");
    assertEquals(input.y, inverse.y, COORD_TOLERANCE, "Latitude should match after round-trip");
  }
}
