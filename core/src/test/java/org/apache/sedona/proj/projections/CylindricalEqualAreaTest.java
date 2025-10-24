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

import org.apache.sedona.proj.Proj4Sedona;
import org.apache.sedona.proj.core.Point;
import org.apache.sedona.proj.core.Projection;
import org.junit.jupiter.api.Test;

/**
 * Test cases for Cylindrical Equal Area projection (CEA). Tests EPSG:6933 (WGS 84 / NSIDC EASE-Grid
 * 2.0 Global) and verifies transformations.
 */
public class CylindricalEqualAreaTest {

  @Test
  public void testProjectionInitialization() {
    // Test initialization from PROJ string
    String projString = "+proj=cea +lat_ts=30 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    assertNotNull(proj);
    assertEquals("cea", proj.projName);
    assertNotNull(proj.cea);

    // Check that ellipsoid parameters are set correctly
    assertEquals(6378137.0, proj.a, 1e-6); // WGS84 semi-major axis
    assertEquals(0.006694379990141, proj.es, 1e-10); // WGS84 eccentricity squared
    assertEquals(0.081819190842622, proj.e, 1e-10); // WGS84 eccentricity

    // Check that projection parameters are set correctly
    assertEquals(Math.toRadians(30), proj.lat_ts, 1e-10); // Standard parallel at 30°
    assertEquals(0.0, proj.long0, 1e-10); // Central meridian at 0°
    assertEquals(0.0, proj.x0, 1e-6); // False easting
    assertEquals(0.0, proj.y0, 1e-6); // False northing
  }

  @Test
  public void testEPSG6933Initialization() {
    // Test EPSG:6933 initialization
    Projection proj = new Projection("EPSG:6933");

    assertNotNull(proj);
    System.out.println("Projection name: " + proj.projName);
    System.out.println("Projection title: " + proj.title);
    System.out.println("lat_ts: " + Math.toDegrees(proj.lat_ts) + "°");
    System.out.println("long0: " + Math.toDegrees(proj.long0) + "°");

    assertEquals("cea", proj.projName, "EPSG:6933 should use CEA projection");
    assertNotNull(proj.cea, "CEA projection instance should be initialized");
  }

  @Test
  public void testForwardTransformation() {
    // Test forward transformation with PROJ string
    String projString = "+proj=cea +lat_ts=30 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    // Test center point (0°E, 0°N)
    Point input = new Point(0.0, 0.0);
    Point result = proj.forward.transform(input);

    assertNotNull(result);
    assertFalse(Double.isNaN(result.x));
    assertFalse(Double.isNaN(result.y));

    // The center point should map to (0, 0)
    assertEquals(0.0, result.x, 1e-6);
    assertEquals(0.0, result.y, 1e-6);
  }

  @Test
  public void testInverseTransformation() {
    // Test inverse transformation
    String projString = "+proj=cea +lat_ts=30 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    // Test center point
    Point input = new Point(0.0, 0.0);
    Point result = proj.inverse.transform(input);

    assertNotNull(result);
    assertFalse(Double.isNaN(result.x));
    assertFalse(Double.isNaN(result.y));

    // Should return to the center coordinates
    assertEquals(0.0, result.x, 1e-6);
    assertEquals(0.0, result.y, 1e-6);
  }

  @Test
  public void testRoundTripAccuracy() {
    // Test round-trip accuracy
    String projString = "+proj=cea +lat_ts=30 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    // Test point: 10°E, 15°N (in radians)
    Point original = new Point(Math.toRadians(10), Math.toRadians(15));

    Point projected = proj.forward.transform(original);
    Point backToGeographic = proj.inverse.transform(projected);

    assertNotNull(projected);
    assertNotNull(backToGeographic);
    assertFalse(Double.isNaN(projected.x) || Double.isNaN(projected.y));
    assertFalse(Double.isNaN(backToGeographic.x) || Double.isNaN(backToGeographic.y));

    // Round-trip accuracy should be within reasonable tolerance
    assertEquals(original.x, backToGeographic.x, 1e-8);
    assertEquals(original.y, backToGeographic.y, 1e-8);
  }

  @Test
  public void testEPSG6933Transformation() {
    // Test actual EPSG:6933 transformation with the user's test points
    // Input: lon,lat order
    double[] lons = {41.57587966, 41.57617993, 41.57647863};
    double[] lats = {-76.69285786, -76.69230338, -76.69175185};

    // Expected outputs (from pyproj reference)
    double[][] expected = {
      {4011501.976552, -7143390.895453},
      {4011530.948488, -7143374.405123},
      {4011559.768940, -7143358.001859}
    };

    for (int i = 0; i < lons.length; i++) {
      // EPSG:4326 is in degrees, not radians
      Point input = new Point(lons[i], lats[i]);
      Point output = Proj4Sedona.transform("EPSG:4326", "EPSG:6933", input);

      assertNotNull(output);
      assertFalse(Double.isNaN(output.x), "X coordinate should not be NaN for point " + (i + 1));
      assertFalse(Double.isNaN(output.y), "Y coordinate should not be NaN for point " + (i + 1));

      // Print actual vs expected for debugging
      System.out.printf(
          "Point %d: Input (%.8f°, %.8f°) -> Output (%.6f, %.6f), Expected (%.6f, %.6f)%n",
          i + 1, lons[i], lats[i], output.x, output.y, expected[i][0], expected[i][1]);

      // Compare with pyproj reference (allowing small tolerance)
      assertEquals(expected[i][0], output.x, 1.0, "X coordinate mismatch for point " + (i + 1));
      assertEquals(expected[i][1], output.y, 1.0, "Y coordinate mismatch for point " + (i + 1));
    }
  }

  @Test
  public void testMultiplePoints() {
    // Test multiple points across the world
    String projString = "+proj=cea +lat_ts=30 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
    Projection proj = new Projection(projString);

    double[][] testPoints = {
      {0.0, 0.0}, // Center point
      {10.0, 15.0}, // 10°E, 15°N
      {-10.0, 15.0}, // 10°W, 15°N
      {10.0, -15.0}, // 10°E, 15°S
      {-10.0, -15.0}, // 10°W, 15°S
      {30.0, 30.0}, // 30°E, 30°N
      {-30.0, 30.0}, // 30°W, 30°N
      {30.0, -30.0}, // 30°E, 30°S
      {-30.0, -30.0} // 30°W, 30°S
    };

    for (double[] point : testPoints) {
      Point input = new Point(Math.toRadians(point[0]), Math.toRadians(point[1]));

      Point projected = proj.forward.transform(input);
      assertNotNull(projected);
      assertFalse(Double.isNaN(projected.x) || Double.isNaN(projected.y));

      Point backToGeographic = proj.inverse.transform(projected);
      assertNotNull(backToGeographic);
      assertFalse(Double.isNaN(backToGeographic.x) || Double.isNaN(backToGeographic.y));

      // Round-trip accuracy
      assertEquals(input.x, backToGeographic.x, 1e-8);
      assertEquals(input.y, backToGeographic.y, 1e-8);
    }
  }
}
