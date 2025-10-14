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

import org.apache.sedona.proj.constants.Datum;
import org.apache.sedona.proj.constants.Values;
import org.apache.sedona.proj.core.Point;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for datum transformation functionality. */
public class DatumTransformTest {

  private Datum.DatumDef wgs84Datum;
  private Datum.DatumDef nad83Datum;
  private Datum.DatumDef nad27Datum;

  @BeforeEach
  public void setUp() {
    // Create test datums
    wgs84Datum = new Datum.DatumDef("0,0,0", "WGS84", "WGS84");
    wgs84Datum.setEllipsoidParams(
        6378137.0, 6356752.314245, 0.0066943799901413165, 0.006739496742276434);

    nad83Datum = new Datum.DatumDef("0,0,0", "GRS80", "NAD83");
    nad83Datum.setEllipsoidParams(6378137.0, 6356752.314140, 0.00669438002290, 0.00673949677548);

    nad27Datum =
        new Datum.DatumDef(
            null,
            "@conus,@alaska,@ntv2_0.gsb,@ntv1_can.dat",
            "clrk66",
            "NAD27",
            Values.PJD_GRIDSHIFT);
    nad27Datum.setEllipsoidParams(6378206.4, 6356583.8, 0.006768658, 0.006814785);
  }

  @Test
  public void testCompareDatums() {
    // Test identical datums
    assertTrue(DatumTransform.compareDatums(wgs84Datum, wgs84Datum));

    // Test different datums
    // Note: WGS84 and NAD83 are considered identical because they have the same ellipsoid
    // parameters
    // and zero transformation parameters, so they are effectively the same datum
    assertTrue(DatumTransform.compareDatums(wgs84Datum, nad83Datum));
    assertFalse(DatumTransform.compareDatums(nad83Datum, nad27Datum));
  }

  @Test
  public void testTransformIdenticalDatums() {
    Point original = new Point(-71.0 * Values.D2R, 41.0 * Values.D2R, 0.0);
    Point transformed = DatumTransform.transform(wgs84Datum, wgs84Datum, original);

    assertNotNull(transformed);
    assertEquals(original.x, transformed.x, 1e-10);
    assertEquals(original.y, transformed.y, 1e-10);
    assertEquals(original.z, transformed.z, 1e-10);
  }

  @Test
  public void testTransformWGS84ToNAD83() {
    // Since both WGS84 and NAD83 have zero transformation parameters,
    // the transformation should be nearly identical
    Point original = new Point(-71.0 * Values.D2R, 41.0 * Values.D2R, 0.0);
    Point transformed = DatumTransform.transform(wgs84Datum, nad83Datum, original);

    assertNotNull(transformed);
    // Should be very close since both are essentially WGS84
    assertEquals(original.x, transformed.x, 1e-6);
    assertEquals(original.y, transformed.y, 1e-6);
  }

  @Test
  public void testGeodeticToGeocentric() {
    Point geodetic = new Point(0.0, 0.0, 0.0); // Greenwich, Equator, sea level
    Point geocentric = DatumTransform.geodeticToGeocentric(geodetic, wgs84Datum.es, wgs84Datum.a);

    assertNotNull(geocentric);
    // At equator, longitude 0, X should be approximately the semi-major axis
    assertEquals(wgs84Datum.a, geocentric.x, 1.0);
    assertEquals(0.0, geocentric.y, 1e-10);
    assertEquals(0.0, geocentric.z, 1e-10);
  }

  @Test
  public void testGeocentricToGeodetic() {
    // Test round-trip conversion
    Point original = new Point(0.0, 0.0, 0.0);
    Point geocentric = DatumTransform.geodeticToGeocentric(original, wgs84Datum.es, wgs84Datum.a);
    Point backToGeodetic =
        DatumTransform.geocentricToGeodetic(geocentric, wgs84Datum.es, wgs84Datum.a, wgs84Datum.b);

    assertNotNull(backToGeodetic);
    assertEquals(original.x, backToGeodetic.x, 1e-10);
    assertEquals(original.y, backToGeodetic.y, 1e-10);
    assertEquals(original.z, backToGeodetic.z, 1e-10);
  }

  @Test
  public void testGeocentricToWgs84_3Param() {
    // Test 3-parameter transformation
    Datum.DatumDef testDatum = new Datum.DatumDef("100,200,300", "WGS84", "Test");
    testDatum.setEllipsoidParams(
        6378137.0, 6356752.314245, 0.0066943799901413165, 0.006739496742276434);

    Point geocentric = new Point(1000.0, 2000.0, 3000.0);
    Point transformed =
        DatumTransform.geocentricToWgs84(geocentric, Values.PJD_3PARAM, testDatum.datum_params);

    assertNotNull(transformed);
    assertEquals(1100.0, transformed.x, 1e-10); // 1000 + 100
    assertEquals(2200.0, transformed.y, 1e-10); // 2000 + 200
    assertEquals(3300.0, transformed.z, 1e-10); // 3000 + 300
  }

  @Test
  public void testGeocentricFromWgs84_3Param() {
    // Test inverse 3-parameter transformation
    Datum.DatumDef testDatum = new Datum.DatumDef("100,200,300", "WGS84", "Test");
    testDatum.setEllipsoidParams(
        6378137.0, 6356752.314245, 0.0066943799901413165, 0.006739496742276434);

    Point wgs84 = new Point(1100.0, 2200.0, 3300.0);
    Point transformed =
        DatumTransform.geocentricFromWgs84(wgs84, Values.PJD_3PARAM, testDatum.datum_params);

    assertNotNull(transformed);
    assertEquals(1000.0, transformed.x, 1e-10); // 1100 - 100
    assertEquals(2000.0, transformed.y, 1e-10); // 2200 - 200
    assertEquals(3000.0, transformed.z, 1e-10); // 3300 - 300
  }

  @Test
  public void testTransformWithNoDatum() {
    Datum.DatumDef noDatum = new Datum.DatumDef(null, null, "WGS84", "NoDatum", Values.PJD_NODATUM);
    noDatum.setEllipsoidParams(
        6378137.0, 6356752.314245, 0.0066943799901413165, 0.006739496742276434);

    Point original = new Point(-71.0 * Values.D2R, 41.0 * Values.D2R, 0.0);
    Point transformed = DatumTransform.transform(noDatum, wgs84Datum, original);

    assertNotNull(transformed);
    assertEquals(original.x, transformed.x, 1e-10);
    assertEquals(original.y, transformed.y, 1e-10);
    assertEquals(original.z, transformed.z, 1e-10);
  }
}
