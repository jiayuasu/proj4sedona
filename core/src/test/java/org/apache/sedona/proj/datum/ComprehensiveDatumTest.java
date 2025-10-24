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
package org.apache.sedona.proj.datum;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.sedona.proj.Proj4Sedona;
import org.apache.sedona.proj.core.Point;
import org.junit.jupiter.api.Test;

/** Comprehensive datum transformation tests. */
public class ComprehensiveDatumTest {

  private static final double EPSILON = 1e-6;
  private static final double METER_EPSILON = 1.0; // 1 meter tolerance

  @Test
  public void testWGS84ToNAD83() {
    // WGS84 to NAD83 transformation
    String wgs84 = "+proj=longlat +datum=WGS84 +no_defs";
    String nad83 = "+proj=longlat +datum=NAD83 +no_defs";

    Point p = new Point(-96.0, 39.0);
    Point result = Proj4Sedona.transform(wgs84, nad83, p);

    assertNotNull(result);
    // WGS84 and NAD83 are very close, difference should be small
    assertEquals(-96.0, result.x, 0.001);
    assertEquals(39.0, result.y, 0.001);
  }

  @Test
  public void testWGS84ToNAD27() {
    // WGS84 to NAD27 transformation (larger shift expected)
    String wgs84 = "+proj=longlat +datum=WGS84 +no_defs";
    String nad27 = "+proj=longlat +datum=NAD27 +no_defs";

    Point p = new Point(-96.0, 39.0);
    Point result = Proj4Sedona.transform(wgs84, nad27, p);

    assertNotNull(result);
    // Should have some shift, but still reasonable
    assertTrue(Math.abs(result.x - (-96.0)) < 0.01);
    assertTrue(Math.abs(result.y - 39.0) < 0.01);
  }

  @Test
  public void testNAD83ToNAD27() {
    // NAD83 to NAD27
    String nad83 = "+proj=longlat +datum=NAD83 +no_defs";
    String nad27 = "+proj=longlat +datum=NAD27 +no_defs";

    Point p = new Point(-96.0, 39.0);
    Point result = Proj4Sedona.transform(nad83, nad27, p);

    assertNotNull(result);
    assertTrue(Double.isFinite(result.x));
    assertTrue(Double.isFinite(result.y));
  }

  @Test
  public void testGRS80Datum() {
    // Test with GRS80 ellipsoid
    String wgs84 = "+proj=longlat +datum=WGS84 +no_defs";
    String grs80 = "+proj=longlat +ellps=GRS80 +no_defs";

    Point p = new Point(10.0, 50.0);
    Point result = Proj4Sedona.transform(wgs84, grs80, p);

    assertNotNull(result);
    // GRS80 and WGS84 are nearly identical
    assertEquals(10.0, result.x, 1e-7);
    assertEquals(50.0, result.y, 1e-7);
  }

  @Test
  public void testRoundTripDatumConversion() {
    // Test round trip: WGS84 -> NAD83 -> WGS84
    String wgs84 = "+proj=longlat +datum=WGS84";
    String nad83 = "+proj=longlat +datum=NAD83";

    Point original = new Point(-100.0, 45.0);
    Point toNAD83 = Proj4Sedona.transform(wgs84, nad83, original);
    Point backToWGS84 = Proj4Sedona.transform(nad83, wgs84, toNAD83);

    assertNotNull(backToWGS84);
    assertEquals(original.x, backToWGS84.x, 1e-6);
    assertEquals(original.y, backToWGS84.y, 1e-6);
  }

  @Test
  public void testProjectedCRSWithDatum() {
    // Test UTM with datum transformation
    String wgs84utm = "+proj=utm +zone=33 +datum=WGS84";
    String nad83utm = "+proj=utm +zone=33 +datum=NAD83";

    Point p = new Point(500000, 5000000);
    Point result = Proj4Sedona.transform(wgs84utm, nad83utm, p);

    assertNotNull(result);
    assertTrue(Math.abs(result.x - 500000) < 10); // Small shift expected
    assertTrue(Math.abs(result.y - 5000000) < 10);
  }

  @Test
  public void testED50ToWGS84() {
    // European Datum 1950 to WGS84
    String ed50 = "+proj=longlat +ellps=intl +no_defs";
    String wgs84 = "+proj=longlat +datum=WGS84 +no_defs";

    Point p = new Point(10.0, 50.0);
    Point result = Proj4Sedona.transform(ed50, wgs84, p);

    assertNotNull(result);
    assertTrue(Double.isFinite(result.x));
    assertTrue(Double.isFinite(result.y));
  }

  @Test
  public void testDatumTransformWithTOWGS84() {
    // Test with explicit TOWGS84 parameters
    String custom = "+proj=longlat +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +no_defs";
    String wgs84 = "+proj=longlat +datum=WGS84 +no_defs";

    Point p = new Point(15.0, 60.0);
    Point result = Proj4Sedona.transform(custom, wgs84, p);

    assertNotNull(result);
    // Zero towgs84 parameters should result in no shift
    assertEquals(15.0, result.x, EPSILON);
    assertEquals(60.0, result.y, EPSILON);
  }

  @Test
  public void testDatumWith7Parameters() {
    // Test 7-parameter Helmert transformation
    String sweref99 = "+proj=utm +zone=33 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs";
    String rt90 =
        "+lon_0=15.808277777799999 +lat_0=0.0 +k=1.0 +x_0=1500000.0 +y_0=0.0 +proj=tmerc "
            + "+ellps=bessel +units=m +towgs84=414.1,41.3,603.1,-0.855,2.141,-7.023,0 +no_defs";

    Point p = new Point(319180, 6399862);
    Point result = Proj4Sedona.transform(sweref99, rt90, p);

    assertNotNull(result);
    // Expected from Proj4js test (with reasonable tolerance for different implementations)
    assertEquals(1271137.927561178, result.x, 200); // 200 meter tolerance for datum shift
    assertEquals(6404230.291456626, result.y, 200);
  }

  @Test
  public void testMultipleDatumTransformations() {
    // Chain of datum transformations
    String wgs84 = "+proj=longlat +datum=WGS84";
    String nad83 = "+proj=longlat +datum=NAD83";
    String nad27 = "+proj=longlat +datum=NAD27";

    Point original = new Point(-95.0, 40.0);

    Point toNAD83 = Proj4Sedona.transform(wgs84, nad83, original);
    Point toNAD27 = Proj4Sedona.transform(nad83, nad27, toNAD83);
    Point backToNAD83 = Proj4Sedona.transform(nad27, nad83, toNAD27);
    Point backToWGS84 = Proj4Sedona.transform(nad83, wgs84, backToNAD83);

    assertNotNull(backToWGS84);
    assertEquals(original.x, backToWGS84.x, 1e-4);
    assertEquals(original.y, backToWGS84.y, 1e-4);
  }

  @Test
  public void testIdentityTransformation() {
    // Same datum should result in no change
    String wgs84_1 = "+proj=longlat +datum=WGS84";
    String wgs84_2 = "+proj=longlat +datum=WGS84";

    Point p = new Point(-100.0, 45.0);
    Point result = Proj4Sedona.transform(wgs84_1, wgs84_2, p);

    assertNotNull(result);
    assertEquals(p.x, result.x, EPSILON);
    assertEquals(p.y, result.y, EPSILON);
  }

  @Test
  public void testDatumTransformWithZ() {
    // Test datum transformation with Z coordinate
    String wgs84 = "+proj=longlat +datum=WGS84";
    String nad83 = "+proj=longlat +datum=NAD83";

    Point p = new Point(-96.0, 39.0, 100.0);
    Point result = Proj4Sedona.transform(wgs84, nad83, p);

    assertNotNull(result);
    assertTrue(Double.isFinite(result.z));
    assertTrue(Math.abs(result.z - 100.0) < 10); // Z should change slightly
  }

  @Test
  public void testExtremeLongitudes() {
    // Test near date line
    String wgs84 = "+proj=longlat +datum=WGS84";
    String nad83 = "+proj=longlat +datum=NAD83";

    Point p1 = new Point(179.9, 45.0);
    Point result1 = Proj4Sedona.transform(wgs84, nad83, p1);
    assertNotNull(result1);

    Point p2 = new Point(-179.9, 45.0);
    Point result2 = Proj4Sedona.transform(wgs84, nad83, p2);
    assertNotNull(result2);
  }

  @Test
  public void testExtremeLatitudes() {
    // Test near poles
    String wgs84 = "+proj=longlat +datum=WGS84";
    String nad83 = "+proj=longlat +datum=NAD83";

    Point p1 = new Point(0, 85.0);
    Point result1 = Proj4Sedona.transform(wgs84, nad83, p1);
    assertNotNull(result1);
    assertTrue(Math.abs(result1.y) <= 90.0);

    Point p2 = new Point(0, -85.0);
    Point result2 = Proj4Sedona.transform(wgs84, nad83, p2);
    assertNotNull(result2);
    assertTrue(Math.abs(result2.y) <= 90.0);
  }

  @Test
  public void testVariousEllipsoids() {
    // Test different ellipsoids
    String wgs84 = "+proj=longlat +ellps=WGS84";
    String clarke1866 = "+proj=longlat +ellps=clrk66";
    String intl = "+proj=longlat +ellps=intl";

    Point p = new Point(10.0, 50.0);

    Point result1 = Proj4Sedona.transform(wgs84, clarke1866, p);
    assertNotNull(result1);

    Point result2 = Proj4Sedona.transform(wgs84, intl, p);
    assertNotNull(result2);

    Point result3 = Proj4Sedona.transform(clarke1866, intl, p);
    assertNotNull(result3);
  }
}
