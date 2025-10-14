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
package org.apache.sedona.proj;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.sedona.proj.core.Point;
import org.apache.sedona.proj.core.Projection;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite to ensure proj4sedona compatibility with proj4js. Tests key projections
 * and transformations against original proj4js test cases.
 */
public class Proj4jsCompatibilityTest {

  @Test
  public void testUTMProjections() {
    // Test UTM Zone 15 (from original proj4js test data)
    String utmProj = "+proj=utm +zone=15 +datum=WGS84";
    Projection utm = new Projection(utmProj);

    // Test point: -96.0, 39.0 degrees
    Point original = new Point(-96.0 * Math.PI / 180.0, 39.0 * Math.PI / 180.0);

    // Forward transformation
    Point forward = utm.forward.transform(original);
    assertNotNull(forward);

    // Inverse transformation
    Point inverse = utm.inverse.transform(forward);
    assertNotNull(inverse);

    // Round-trip precision should match proj4js standard (1e-6 degrees)
    assertEquals(original.x, inverse.x, 1e-6);
    assertEquals(original.y, inverse.y, 1e-6);
  }

  @Test
  public void testLambertConformalConic() {
    // Test LCC projection
    String lccProj = "+proj=lcc +lat_1=33 +lat_2=45 +lat_0=39 +lon_0=-96 +datum=WGS84";
    Projection lcc = new Projection(lccProj);

    Point original = new Point(-96.0 * Math.PI / 180.0, 39.0 * Math.PI / 180.0);

    Point forward = lcc.forward.transform(original);
    assertNotNull(forward);

    Point inverse = lcc.inverse.transform(forward);
    assertNotNull(inverse);

    // LCC should have high precision
    assertEquals(original.x, inverse.x, 1e-10);
    assertEquals(original.y, inverse.y, 1e-10);
  }

  @Test
  public void testAlbersEqualArea() {
    // Test AEA projection
    String aeaProj = "+proj=aea +lat_1=29.5 +lat_2=45.5 +lat_0=23 +lon_0=-96 +datum=WGS84";
    Projection aea = new Projection(aeaProj);

    Point original = new Point(-96.0 * Math.PI / 180.0, 39.0 * Math.PI / 180.0);

    Point forward = aea.forward.transform(original);
    assertNotNull(forward);

    Point inverse = aea.inverse.transform(forward);
    assertNotNull(inverse);

    // AEA should have high precision
    assertEquals(original.x, inverse.x, 1e-10);
    assertEquals(original.y, inverse.y, 1e-10);
  }

  @Test
  public void testMercatorProjection() {
    // Test Mercator projection
    String mercProj = "+proj=merc +datum=WGS84";
    Projection merc = new Projection(mercProj);

    Point original = new Point(-96.0 * Math.PI / 180.0, 39.0 * Math.PI / 180.0);

    Point forward = merc.forward.transform(original);
    assertNotNull(forward);

    Point inverse = merc.inverse.transform(forward);
    assertNotNull(inverse);

    // Mercator should have good precision
    assertEquals(original.x, inverse.x, 1e-8);
    assertEquals(original.y, inverse.y, 1e-8);
  }

  @Test
  public void testTransverseMercator() {
    // Test Transverse Mercator (from proj4js test data)
    String tmercProj = "+proj=tmerc +ellps=GRS80 +lat_1=0.5 +lat_2=2 +n=0.5";
    Projection tmerc = new Projection(tmercProj);

    // Test point from proj4js: [2, 1] degrees
    Point original = new Point(2.0 * Math.PI / 180.0, 1.0 * Math.PI / 180.0);

    Point forward = tmerc.forward.transform(original);
    assertNotNull(forward);

    // Expected result from proj4js: [222650.79679577847, 110642.2294119271]
    // Allow some tolerance for numerical differences
    assertEquals(222650.79679577847, forward.x, 1e-3);
    assertEquals(110642.2294119271, forward.y, 1e-3);

    Point inverse = tmerc.inverse.transform(forward);
    assertNotNull(inverse);

    // Round-trip precision
    assertEquals(original.x, inverse.x, 1e-6);
    assertEquals(original.y, inverse.y, 1e-6);
  }

  @Test
  public void testWebMercator() {
    // Test Web Mercator (EPSG:3857)
    String webMercProj = "EPSG:3857";
    Projection webMerc = new Projection(webMercProj);

    // Test point: [0, 0] degrees
    Point original = new Point(0.0, 0.0);

    Point forward = webMerc.forward.transform(original);
    assertNotNull(forward);

    // Web Mercator origin should be [0, 0]
    assertEquals(0.0, forward.x, 1e-6);
    assertEquals(0.0, forward.y, 1e-6);

    Point inverse = webMerc.inverse.transform(forward);
    assertNotNull(inverse);

    assertEquals(original.x, inverse.x, 1e-8);
    assertEquals(original.y, inverse.y, 1e-8);
  }

  @Test
  public void testLongitudeLatitude() {
    // Test basic longitude/latitude (no projection)
    String longlatProj = "+proj=longlat +datum=WGS84";
    Projection longlat = new Projection(longlatProj);

    Point original = new Point(-96.0 * Math.PI / 180.0, 39.0 * Math.PI / 180.0);

    Point forward = longlat.forward.transform(original);
    assertNotNull(forward);

    // Longitude/latitude should pass through unchanged
    assertEquals(original.x, forward.x, 1e-15);
    assertEquals(original.y, forward.y, 1e-15);

    Point inverse = longlat.inverse.transform(forward);
    assertNotNull(inverse);

    assertEquals(original.x, inverse.x, 1e-15);
    assertEquals(original.y, inverse.y, 1e-15);
  }

  @Test
  public void testDatumTransformations() {
    // Test datum transformation between WGS84 and NAD83
    String wgs84Proj = "+proj=longlat +datum=WGS84";
    String nad83Proj = "+proj=longlat +datum=NAD83";

    Projection wgs84 = new Projection(wgs84Proj);
    Projection nad83 = new Projection(nad83Proj);

    Point original = new Point(-96.0 * Math.PI / 180.0, 39.0 * Math.PI / 180.0);

    // Transform from WGS84 to NAD83
    Point transformed = Proj4Sedona.transform(wgs84Proj, nad83Proj, original);
    assertNotNull(transformed);

    // Transform back to WGS84
    Point backTransformed = Proj4Sedona.transform(nad83Proj, wgs84Proj, transformed);
    assertNotNull(backTransformed);

    // Round-trip should be accurate (datum transformations have some inherent uncertainty)
    assertEquals(original.x, backTransformed.x, 1e-4);
    assertEquals(original.y, backTransformed.y, 1e-4);
  }

  @Test
  public void testProjStringParsing() {
    // Test various PROJ string formats
    String[] projStrings = {
      "+proj=utm +zone=15 +datum=WGS84",
      "+proj=lcc +lat_1=33 +lat_2=45 +lat_0=39 +lon_0=-96 +datum=WGS84",
      "+proj=aea +lat_1=29.5 +lat_2=45.5 +lat_0=23 +lon_0=-96 +datum=WGS84",
      "+proj=merc +datum=WGS84",
      "+proj=longlat +datum=WGS84",
      "EPSG:3857",
      "EPSG:4326"
    };

    for (String projString : projStrings) {
      Projection proj = new Projection(projString);
      assertNotNull(proj);
      assertNotNull(proj.forward);
      assertNotNull(proj.inverse);
    }
  }

  @Test
  public void testWKTSupport() {
    // Test WKT parsing
    String wkt =
        "GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\",SPHEROID[\"WGS 84\",6378137,298.257223563]],PRIMEM[\"Greenwich\",0],UNIT[\"degree\",0.0174532925199433]]";

    Projection proj = new Projection(wkt);
    assertNotNull(proj);
    assertEquals("longlat", proj.projName);
    assertEquals("wgs84", proj.datumCode);
  }

  @Test
  public void testEdgeCases() {
    // Test edge cases that might cause issues

    // Test coordinates at poles (should work fine)
    String utmProj = "+proj=utm +zone=15 +datum=WGS84";
    Projection utm = new Projection(utmProj);

    Point polePoint = new Point(0.0, 90.0 * Math.PI / 180.0);
    Point poleForward = utm.forward.transform(polePoint);
    assertNotNull(poleForward);

    Point poleInverse = utm.inverse.transform(poleForward);
    assertNotNull(poleInverse);

    assertEquals(polePoint.x, poleInverse.x, 1e-6);
    assertEquals(polePoint.y, poleInverse.y, 1e-6);

    // Test coordinates near the equator within UTM zone 15 (central meridian -93°)
    Point equatorPoint = new Point(-93.0 * Math.PI / 180.0, 0.0);
    Point equatorForward = utm.forward.transform(equatorPoint);
    assertNotNull(equatorForward);

    Point equatorInverse = utm.inverse.transform(equatorForward);
    assertNotNull(equatorInverse);

    assertEquals(equatorPoint.x, equatorInverse.x, 1e-6);
    assertEquals(equatorPoint.y, equatorInverse.y, 1e-6);
  }
}
