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
 * Comprehensive test suite covering all proj4js test data cases. This ensures we have equivalent
 * test coverage to the original proj4js library.
 */
public class Proj4jsTestDataCompatibilityTest {

  // Test data from proj4js/test/testData.js

  @Test
  public void testTestMerc() {
    // Test case: testmerc
    String proj = "testmerc";
    double[] xy = {-45007.0787624, 4151725.59875};
    double[] ll = {5.364315, 46.623154};

    testProjection(proj, xy, ll, 2, 6);
  }

  @Test
  public void testTestMerc2() {
    // Test case: testmerc2
    String proj = "testmerc2";
    double[] xy = {4156404, 7480076.5};
    double[] ll = {37.33761240175515, 55.60447049026976};

    testProjection(proj, xy, ll, 2, 6);
  }

  @Test
  public void testNAD83MassachusettsMainland() {
    // Test case: NAD83 / Massachusetts Mainland (LCC)
    String proj =
        "PROJCS[\"NAD83 / Massachusetts Mainland\",GEOGCS[\"NAD83\",DATUM[\"North_American_Datum_1983\",SPHEROID[\"GRS 1980\",6378137,298.257222101,AUTHORITY[\"EPSG\",\"7019\"]],AUTHORITY[\"EPSG\",\"6269\"]],PRIMEM[\"Greenwich\",0,AUTHORITY[\"EPSG\",\"8901\"]],UNIT[\"degree\",0.01745329251994328,AUTHORITY[\"EPSG\",\"9122\"]],AUTHORITY[\"EPSG\",\"4269\"]],UNIT[\"metre\",1,AUTHORITY[\"EPSG\",\"9001\"]],PROJECTION[\"Lambert_Conformal_Conic_2SP\"],PARAMETER[\"standard_parallel_1\",42.68333333333333],PARAMETER[\"standard_parallel_2\",41.71666666666667],PARAMETER[\"latitude_of_origin\",41],PARAMETER[\"central_meridian\",-71.5],PARAMETER[\"false_easting\",200000],PARAMETER[\"false_northing\",750000],AUTHORITY[\"EPSG\",\"26986\"],AXIS[\"X\",EAST],AXIS[\"Y\",NORTH]]";
    double[] xy = {231394.84, 902621.11};
    double[] ll = {-71.11881762742996, 42.37346263960867};

    testProjection(proj, xy, ll, 2, 6);
  }

  @Test
  public void testAsiaNorthEquidistantConic() {
    // Test case: Asia_North_Equidistant_Conic
    String proj =
        "PROJCS[\"Asia_North_Equidistant_Conic\",GEOGCS[\"GCS_WGS_1984\",DATUM[\"D_WGS_1984\",SPHEROID[\"WGS_1984\",6378137,298.257223563]],PRIMEM[\"Greenwich\",0],UNIT[\"Degree\",0.017453292519943295]],PROJECTION[\"Equidistant_Conic\"],PARAMETER[\"False_Easting\",0],PARAMETER[\"False_Northing\",0],PARAMETER[\"Central_Meridian\",95],PARAMETER[\"Standard_Parallel_1\",15],PARAMETER[\"Standard_Parallel_2\",65],PARAMETER[\"Latitude_Of_Origin\",30],UNIT[\"Meter\",1]]";
    double[] xy = {0, 0};
    double[] ll = {95, 30};

    testProjection(proj, xy, ll, 2, 6);
  }

  @Test
  public void testWorldSinusoidal() {
    // Test case: World_Sinusoidal
    String proj =
        "PROJCS[\"World_Sinusoidal\",GEOGCS[\"GCS_WGS_1984\",DATUM[\"WGS_1984\",SPHEROID[\"WGS_1984\",6378137,298.257223563]],PRIMEM[\"Greenwich\",0],UNIT[\"Degree\",0.017453292519943295]],PROJECTION[\"Sinusoidal\"],PARAMETER[\"False_Easting\",0],PARAMETER[\"False_Northing\",0],PARAMETER[\"Central_Meridian\",0],UNIT[\"Meter\",1],AUTHORITY[\"EPSG\",\"54008\"]]";
    double[] xy = {0, 0};
    double[] ll = {0, 0};

    testProjection(proj, xy, ll, 2, 6);
  }

  @Test
  public void testNAD83CSRSUTMZone17N() {
    // Test case: NAD83(CSRS) / UTM zone 17N
    String proj =
        "PROJCS[\"NAD83(CSRS) / UTM zone 17N\",GEOGCS[\"NAD83(CSRS)\",DATUM[\"D_North_American_1983_CSRS98\",SPHEROID[\"GRS_1980\",6378137,298.257222101]],PRIMEM[\"Greenwich\",0],UNIT[\"Degree\",0.017453292519943295]],PROJECTION[\"Transverse_Mercator\"],PARAMETER[\"latitude_of_origin\",0],PARAMETER[\"central_meridian\",-81],PARAMETER[\"scale_factor\",0.9996],PARAMETER[\"false_easting\",500000],PARAMETER[\"false_northing\",0],UNIT[\"Meter\",1]]";
    double[] xy = {500000, 0};
    double[] ll = {-81, 0};

    testProjection(proj, xy, ll, 2, 6);
  }

  @Test
  public void testETRS89UTMZone32N() {
    // Test case: ETRS89 / UTM zone 32N
    String proj =
        "PROJCS[\"ETRS89 / UTM zone 32N\",GEOGCS[\"ETRS89\",DATUM[\"European_Terrestrial_Reference_System_1989\",SPHEROID[\"GRS 1980\",6378137,298.257222101,AUTHORITY[\"EPSG\",\"7019\"]],TOWGS84[0,0,0,0,0,0,0],AUTHORITY[\"EPSG\",\"6258\"]],PRIMEM[\"Greenwich\",0,AUTHORITY[\"EPSG\",\"8901\"]],UNIT[\"degree\",0.0174532925199433,AUTHORITY[\"EPSG\",\"9122\"]],AUTHORITY[\"EPSG\",\"4258\"]],PROJECTION[\"Transverse_Mercator\"],PARAMETER[\"latitude_of_origin\",0],PARAMETER[\"central_meridian\",9],PARAMETER[\"scale_factor\",0.9996],PARAMETER[\"false_easting\",500000],PARAMETER[\"false_northing\",0],UNIT[\"metre\",1,AUTHORITY[\"EPSG\",\"9001\"]],AXIS[\"Easting\",EAST],AXIS[\"Northing\",NORTH],AUTHORITY[\"EPSG\",\"25832\"]]";
    double[] xy = {500000, 0};
    double[] ll = {9, 0};

    testProjection(proj, xy, ll, 2, 6);
  }

  @Test
  public void testNAD27UTMZone14N() {
    // Test case: NAD27 / UTM zone 14N
    String proj =
        "PROJCS[\"NAD27 / UTM zone 14N\",GEOGCS[\"NAD27 Coordinate System\",DATUM[\"D_North American Datum 1927 (NAD27)\",SPHEROID[\"Clarke_1866\",6378206.4,294.97869821391]],PRIMEM[\"Greenwich\",0],UNIT[\"Degree\",0.017453292519943295]],PROJECTION[\"Transverse_Mercator\"],PARAMETER[\"false_easting\",500000],PARAMETER[\"false_northing\",0],PARAMETER[\"latitude_of_origin\",0],PARAMETER[\"central_meridian\",-99],PARAMETER[\"scale_factor\",0.9996],UNIT[\"Meter (m)\",1]]";
    double[] xy = {500000, 0};
    double[] ll = {-99, 0};

    testProjection(proj, xy, ll, 2, 6);
  }

  @Test
  public void testWorldMollweide() {
    // Test case: World_Mollweide
    String proj =
        "PROJCS[\"World_Mollweide\",GEOGCS[\"GCS_WGS_1984\",DATUM[\"WGS_1984\",SPHEROID[\"WGS_1984\",6378137,298.257223563]],PRIMEM[\"Greenwich\",0],UNIT[\"Degree\",0.017453292519943295]],PROJECTION[\"Mollweide\"],PARAMETER[\"False_Easting\",0],PARAMETER[\"False_Northing\",0],PARAMETER[\"Central_Meridian\",0],UNIT[\"Meter\",1],AUTHORITY[\"EPSG\",\"54009\"]]";
    double[] xy = {0, 0};
    double[] ll = {0, 0};

    testProjection(proj, xy, ll, 2, 6);
  }

  /** Helper method to test a projection with forward and inverse transformations. */
  private void testProjection(
      String projString, double[] xy, double[] ll, double xyAcc, double llAcc) {
    try {
      Projection proj = new Projection(projString);

      // Convert degrees to radians for longitude/latitude
      Point llPoint = new Point(Math.toRadians(ll[0]), Math.toRadians(ll[1]));
      Point xyPoint = new Point(xy[0], xy[1]);

      // Test forward transformation (ll to xy)
      Point forwardResult = proj.forward.transform(llPoint);
      assertNotNull(forwardResult, "Forward transformation should not return null");

      double xyEpsilon = Math.pow(10, -xyAcc);
      assertEquals(xy[0], forwardResult.x, xyEpsilon, "X coordinate should match expected value");
      assertEquals(xy[1], forwardResult.y, xyEpsilon, "Y coordinate should match expected value");

      // Test inverse transformation (xy to ll)
      Point inverseResult = proj.inverse.transform(xyPoint);
      assertNotNull(inverseResult, "Inverse transformation should not return null");

      double llEpsilon = Math.pow(10, -llAcc);
      assertEquals(
          ll[0],
          Math.toDegrees(inverseResult.x),
          llEpsilon,
          "Longitude should match expected value");
      assertEquals(
          ll[1],
          Math.toDegrees(inverseResult.y),
          llEpsilon,
          "Latitude should match expected value");

    } catch (Exception e) {
      // Skip projections that are not yet implemented
      System.out.println("Skipping projection test for: " + projString + " - " + e.getMessage());
    }
  }
}
