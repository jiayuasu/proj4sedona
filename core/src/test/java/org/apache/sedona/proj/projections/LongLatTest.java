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

/** Test cases for LongLat (geographic) projection. This is the identity projection. */
public class LongLatTest {

  private static final double COORD_TOLERANCE = 1e-10;

  @Test
  public void testLongLatInitialization() {
    String projString = "+proj=longlat +datum=WGS84";
    Projection proj = new Projection(projString);

    assertNotNull(proj);
    assertEquals("longlat", proj.projName);
  }

  @Test
  public void testLongLatIdentity() {
    String projString = "+proj=longlat +datum=WGS84";
    Projection proj = new Projection(projString);

    // LongLat is identity - coordinates should remain unchanged
    Point input = new Point(Math.toRadians(10.0), Math.toRadians(20.0));
    Point forward = proj.forward.transform(new Point(input.x, input.y));
    assertNotNull(forward);

    assertEquals(input.x, forward.x, COORD_TOLERANCE, "X should be unchanged (identity)");
    assertEquals(input.y, forward.y, COORD_TOLERANCE, "Y should be unchanged (identity)");

    // Inverse should also be identity
    Point inverse = proj.inverse.transform(new Point(forward.x, forward.y));
    assertNotNull(inverse);

    assertEquals(input.x, inverse.x, COORD_TOLERANCE, "Inverse X should match input");
    assertEquals(input.y, inverse.y, COORD_TOLERANCE, "Inverse Y should match input");
  }

  @Test
  public void testLongLatMultiplePoints() {
    String projString = "+proj=longlat +datum=WGS84";
    Projection proj = new Projection(projString);

    double[][] testPoints = {
      {0.0, 0.0},
      {-180.0, -90.0},
      {180.0, 90.0},
      {-45.0, 30.0},
      {90.0, -15.0},
    };

    for (double[] coords : testPoints) {
      Point input = new Point(Math.toRadians(coords[0]), Math.toRadians(coords[1]));
      Point forward = proj.forward.transform(new Point(input.x, input.y));
      assertNotNull(forward);

      // Should be identity
      assertEquals(input.x, forward.x, COORD_TOLERANCE);
      assertEquals(input.y, forward.y, COORD_TOLERANCE);
    }
  }

  @Test
  public void testEPSG4326() {
    // Test EPSG:4326 (WGS84 geographic)
    Projection proj = new Projection("EPSG:4326");

    assertNotNull(proj);

    Point input = new Point(Math.toRadians(-71.0), Math.toRadians(42.0));
    Point forward = proj.forward.transform(new Point(input.x, input.y));
    assertNotNull(forward);

    // Should be identity for geographic CRS
    assertEquals(input.x, forward.x, COORD_TOLERANCE);
    assertEquals(input.y, forward.y, COORD_TOLERANCE);
  }
}
