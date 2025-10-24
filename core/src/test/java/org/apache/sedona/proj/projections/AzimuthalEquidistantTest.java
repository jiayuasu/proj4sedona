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

/** Test cases for Azimuthal Equidistant projection. Test cases ported from proj4js testData.js */
public class AzimuthalEquidistantTest {

  private static final double TOLERANCE = 1.0;
  private static final double COORD_TOLERANCE = 1e-7;

  @Test
  public void testAzimuthalEquidistantInitialization() {
    String projString = "+proj=aeqd +lat_0=0 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs";
    Projection proj = new Projection(projString);

    assertNotNull(proj);
    assertEquals("aeqd", proj.projName);
  }

  @Test
  public void testAeqdCenter() {
    // Test case from proj4js: ll: [0, 0], xy: [0, 0]
    String projString = "+proj=aeqd +lat_0=0 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs";
    Projection proj = new Projection(projString);

    Point input = new Point(0, 0);
    Point forward = proj.forward.transform(new Point(input.x, input.y));
    assertNotNull(forward);

    assertEquals(0, forward.x, TOLERANCE, "Center X should be 0");
    assertEquals(0, forward.y, TOLERANCE, "Center Y should be 0");

    // Test round-trip
    Point inverse = proj.inverse.transform(new Point(forward.x, forward.y));
    assertNotNull(inverse);

    assertEquals(input.x, inverse.x, COORD_TOLERANCE);
    assertEquals(input.y, inverse.y, COORD_TOLERANCE);
  }

  @Test
  public void testAeqdEquatorEast() {
    // Test case from proj4js: ll: [2, 0], xy: [222638.98158654713, 0]
    String projString = "+proj=aeqd +lat_0=0 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs";
    Projection proj = new Projection(projString);

    Point input = new Point(Math.toRadians(2), Math.toRadians(0));
    Point forward = proj.forward.transform(new Point(input.x, input.y));
    assertNotNull(forward);

    // Check against expected values (from proj4js)
    assertEquals(222638.98158654713, forward.x, TOLERANCE, "AEQD X coordinate");
    assertEquals(0, forward.y, TOLERANCE, "AEQD Y coordinate");

    // Test round-trip
    Point inverse = proj.inverse.transform(new Point(forward.x, forward.y));
    assertNotNull(inverse);

    assertEquals(input.x, inverse.x, COORD_TOLERANCE);
    assertEquals(input.y, inverse.y, COORD_TOLERANCE);
  }

  @Test
  public void testAeqdSouth() {
    // Test case from proj4js: ll: [0, -52], xy: [0, -5763343.550010418]
    String projString = "+proj=aeqd +lat_0=0 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs";
    Projection proj = new Projection(projString);

    Point input = new Point(Math.toRadians(0), Math.toRadians(-52));
    Point forward = proj.forward.transform(new Point(input.x, input.y));
    assertNotNull(forward);

    // Check against expected values (from proj4js)
    assertEquals(0, forward.x, TOLERANCE, "AEQD X coordinate");
    assertEquals(-5763343.550010418, forward.y, TOLERANCE, "AEQD Y coordinate");

    // Test round-trip
    Point inverse = proj.inverse.transform(new Point(forward.x, forward.y));
    assertNotNull(inverse);

    assertEquals(input.x, inverse.x, COORD_TOLERANCE);
    assertEquals(input.y, inverse.y, COORD_TOLERANCE);
  }

  @Test
  public void testAeqdLargeDistance() {
    // Test case from proj4js: ll: [89, 0], xy: [9907434.680601347, 0]
    String projString = "+proj=aeqd +lat_0=0 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs";
    Projection proj = new Projection(projString);

    Point input = new Point(Math.toRadians(89), Math.toRadians(0));
    Point forward = proj.forward.transform(new Point(input.x, input.y));
    assertNotNull(forward);

    // Check against expected values (from proj4js)
    assertEquals(9907434.680601347, forward.x, TOLERANCE, "AEQD X coordinate");
    assertEquals(0, forward.y, TOLERANCE, "AEQD Y coordinate");

    // Test round-trip
    Point inverse = proj.inverse.transform(new Point(forward.x, forward.y));
    assertNotNull(inverse);

    assertEquals(input.x, inverse.x, COORD_TOLERANCE);
    assertEquals(input.y, inverse.y, COORD_TOLERANCE);
  }

  @Test
  public void testAeqdSpherical() {
    // Test case from proj4js: +proj=aeqd +lat_0=0 +lon_0=0 +a=6371000 +b=6371000
    // ll: [91, 0], xy: [10118738.32, 0.00]
    String projString =
        "+proj=aeqd +lat_0=0 +lon_0=0 +x_0=0 +y_0=0 +a=6371000 +b=6371000 +units=m +no_defs";
    Projection proj = new Projection(projString);

    Point input = new Point(Math.toRadians(91), Math.toRadians(0));
    Point forward = proj.forward.transform(new Point(input.x, input.y));
    assertNotNull(forward);

    // Check against expected values (from proj4js)
    assertEquals(10118738.32, forward.x, TOLERANCE, "AEQD spherical X coordinate");
    assertEquals(0, forward.y, TOLERANCE, "AEQD spherical Y coordinate");

    // Test round-trip
    Point inverse = proj.inverse.transform(new Point(forward.x, forward.y));
    assertNotNull(inverse);

    assertEquals(input.x, inverse.x, COORD_TOLERANCE);
    assertEquals(input.y, inverse.y, COORD_TOLERANCE);
  }

  @Test
  public void testAeqdObliqueCenter() {
    // Test case from proj4js: +proj=aeqd +lat_0=83.6625 +lon_0=-29.8333
    // ll: [150.1667, 87.38418697931058], xy: [0, 1000000]
    String projString =
        "+proj=aeqd +lat_0=83.6625 +lon_0=-29.8333 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs";
    Projection proj = new Projection(projString);

    Point input = new Point(Math.toRadians(150.1667), Math.toRadians(87.38418697931058));
    Point forward = proj.forward.transform(new Point(input.x, input.y));
    assertNotNull(forward);

    // Check against expected values (from proj4js)
    assertEquals(0, forward.x, TOLERANCE, "AEQD oblique X coordinate");
    assertEquals(1000000, forward.y, TOLERANCE, "AEQD oblique Y coordinate");

    // Test round-trip
    Point inverse = proj.inverse.transform(new Point(forward.x, forward.y));
    assertNotNull(inverse);

    assertEquals(input.x, inverse.x, COORD_TOLERANCE);
    assertEquals(input.y, inverse.y, COORD_TOLERANCE);
  }
}
