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
package org.apache.sedona.proj.jts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.sedona.proj.Proj4Sedona;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

/** Test suite for JTSTransform class. */
class JTSTransformTest {

  private GeometryFactory gf;

  @BeforeEach
  void setUp() {
    gf = new GeometryFactory();
  }

  @Test
  void testTransformPoint() {
    // Create a point in WGS84 (Boston)
    org.locationtech.jts.geom.Point point = gf.createPoint(new Coordinate(-71.0, 41.0));

    // Transform to Web Mercator
    org.locationtech.jts.geom.Point transformed =
        (org.locationtech.jts.geom.Point)
            JTSTransform.transform(point, "EPSG:4326", "EPSG:3857", gf);

    // Verify transformation
    assertThat(transformed).isNotNull();
    assertThat(transformed.getX()).isCloseTo(-7903683.846322424, offset(0.1));
    assertThat(transformed.getY()).isCloseTo(5012341.663847514, offset(0.1));
  }

  @Test
  void testTransformPointWithZ() {
    // Create a point with Z coordinate
    org.locationtech.jts.geom.Point point = gf.createPoint(new Coordinate(-71.0, 41.0, 100.0));

    // Transform to Web Mercator
    org.locationtech.jts.geom.Point transformed =
        (org.locationtech.jts.geom.Point)
            JTSTransform.transform(point, "EPSG:4326", "EPSG:3857", gf);

    // Verify transformation (Z should be preserved)
    assertThat(transformed).isNotNull();
    assertThat(transformed.getX()).isCloseTo(-7903683.846322424, offset(0.1));
    assertThat(transformed.getY()).isCloseTo(5012341.663847514, offset(0.1));
    assertThat(transformed.getCoordinate().getZ()).isCloseTo(100.0, offset(0.1));
  }

  @Test
  void testTransformLineString() {
    // Create a linestring in WGS84
    Coordinate[] coords =
        new Coordinate[] {
          new Coordinate(-71.0, 41.0), new Coordinate(-70.0, 42.0), new Coordinate(-69.0, 43.0)
        };
    LineString lineString = gf.createLineString(coords);

    // Transform to Web Mercator
    LineString transformed =
        (LineString) JTSTransform.transform(lineString, "EPSG:4326", "EPSG:3857", gf);

    // Verify transformation
    assertThat(transformed).isNotNull();
    assertThat(transformed.getNumPoints()).isEqualTo(3);

    // Check first point
    assertThat(transformed.getCoordinateN(0).x).isCloseTo(-7903683.846322424, offset(0.1));
    assertThat(transformed.getCoordinateN(0).y).isCloseTo(5012341.663847514, offset(0.1));

    // Check last point
    assertThat(transformed.getCoordinateN(2).x).isCloseTo(-7681044.864735875, offset(1.0));
    assertThat(transformed.getCoordinateN(2).y).isCloseTo(5311971.846945471, offset(1.0));
  }

  @Test
  void testTransformPolygon() {
    // Create a polygon in WGS84 (Boston area)
    Coordinate[] coords =
        new Coordinate[] {
          new Coordinate(-71.0, 41.0),
          new Coordinate(-71.0, 42.0),
          new Coordinate(-70.0, 42.0),
          new Coordinate(-70.0, 41.0),
          new Coordinate(-71.0, 41.0) // Close the ring
        };
    Polygon polygon = gf.createPolygon(coords);

    // Transform to Web Mercator
    Polygon transformed = (Polygon) JTSTransform.transform(polygon, "EPSG:4326", "EPSG:3857", gf);

    // Verify transformation
    assertThat(transformed).isNotNull();
    assertThat(transformed.getNumPoints()).isEqualTo(5);

    // Verify area is larger in Web Mercator (due to distortion)
    assertThat(transformed.getArea()).isGreaterThan(polygon.getArea());

    // Check a corner point
    Coordinate firstCoord = transformed.getExteriorRing().getCoordinateN(0);
    assertThat(firstCoord.x).isCloseTo(-7903683.846322424, offset(0.1));
    assertThat(firstCoord.y).isCloseTo(5012341.663847514, offset(0.1));
  }

  @Test
  void testTransformPolygonWithHole() {
    // Create polygon with a hole
    Coordinate[] shell =
        new Coordinate[] {
          new Coordinate(-72.0, 41.0),
          new Coordinate(-72.0, 42.0),
          new Coordinate(-70.0, 42.0),
          new Coordinate(-70.0, 41.0),
          new Coordinate(-72.0, 41.0)
        };

    Coordinate[] hole =
        new Coordinate[] {
          new Coordinate(-71.5, 41.3),
          new Coordinate(-71.5, 41.7),
          new Coordinate(-70.5, 41.7),
          new Coordinate(-70.5, 41.3),
          new Coordinate(-71.5, 41.3)
        };

    Polygon polygon =
        gf.createPolygon(gf.createLinearRing(shell), new LinearRing[] {gf.createLinearRing(hole)});

    // Transform to Web Mercator
    Polygon transformed = (Polygon) JTSTransform.transform(polygon, "EPSG:4326", "EPSG:3857", gf);

    // Verify transformation
    assertThat(transformed).isNotNull();
    assertThat(transformed.getNumInteriorRing()).isEqualTo(1);

    // Verify hole is preserved
    LinearRing transformedHole = (LinearRing) transformed.getInteriorRingN(0);
    assertThat(transformedHole.getNumPoints()).isEqualTo(5);
  }

  @Test
  void testTransformMultiPoint() {
    // Create multipoint
    org.locationtech.jts.geom.Point[] points =
        new org.locationtech.jts.geom.Point[] {
          gf.createPoint(new Coordinate(-71.0, 41.0)),
          gf.createPoint(new Coordinate(-70.0, 42.0)),
          gf.createPoint(new Coordinate(-69.0, 43.0))
        };
    MultiPoint multiPoint = gf.createMultiPoint(points);

    // Transform to Web Mercator
    MultiPoint transformed =
        (MultiPoint) JTSTransform.transform(multiPoint, "EPSG:4326", "EPSG:3857", gf);

    // Verify transformation
    assertThat(transformed).isNotNull();
    assertThat(transformed.getNumGeometries()).isEqualTo(3);

    // Check first point
    org.locationtech.jts.geom.Point firstPoint =
        (org.locationtech.jts.geom.Point) transformed.getGeometryN(0);
    assertThat(firstPoint.getX()).isCloseTo(-7903683.846322424, offset(0.1));
    assertThat(firstPoint.getY()).isCloseTo(5012341.663847514, offset(0.1));
  }

  @Test
  void testTransformMultiLineString() {
    // Create multilinestring
    LineString[] lineStrings =
        new LineString[] {
          gf.createLineString(
              new Coordinate[] {new Coordinate(-71.0, 41.0), new Coordinate(-70.0, 42.0)}),
          gf.createLineString(
              new Coordinate[] {new Coordinate(-69.0, 43.0), new Coordinate(-68.0, 44.0)})
        };
    MultiLineString multiLineString = gf.createMultiLineString(lineStrings);

    // Transform to Web Mercator
    MultiLineString transformed =
        (MultiLineString) JTSTransform.transform(multiLineString, "EPSG:4326", "EPSG:3857", gf);

    // Verify transformation
    assertThat(transformed).isNotNull();
    assertThat(transformed.getNumGeometries()).isEqualTo(2);
  }

  @Test
  void testTransformMultiPolygon() {
    // Create multipolygon
    Polygon poly1 =
        gf.createPolygon(
            new Coordinate[] {
              new Coordinate(-71.0, 41.0),
              new Coordinate(-71.0, 42.0),
              new Coordinate(-70.0, 42.0),
              new Coordinate(-70.0, 41.0),
              new Coordinate(-71.0, 41.0)
            });

    Polygon poly2 =
        gf.createPolygon(
            new Coordinate[] {
              new Coordinate(-69.0, 43.0),
              new Coordinate(-69.0, 44.0),
              new Coordinate(-68.0, 44.0),
              new Coordinate(-68.0, 43.0),
              new Coordinate(-69.0, 43.0)
            });

    MultiPolygon multiPolygon = gf.createMultiPolygon(new Polygon[] {poly1, poly2});

    // Transform to Web Mercator
    MultiPolygon transformed =
        (MultiPolygon) JTSTransform.transform(multiPolygon, "EPSG:4326", "EPSG:3857", gf);

    // Verify transformation
    assertThat(transformed).isNotNull();
    assertThat(transformed.getNumGeometries()).isEqualTo(2);
  }

  @Test
  void testTransformGeometryCollection() {
    // Create geometry collection with mixed types
    Geometry[] geometries =
        new Geometry[] {
          gf.createPoint(new Coordinate(-71.0, 41.0)),
          gf.createLineString(
              new Coordinate[] {new Coordinate(-70.0, 42.0), new Coordinate(-69.0, 43.0)}),
          gf.createPolygon(
              new Coordinate[] {
                new Coordinate(-68.0, 44.0),
                new Coordinate(-68.0, 45.0),
                new Coordinate(-67.0, 45.0),
                new Coordinate(-67.0, 44.0),
                new Coordinate(-68.0, 44.0)
              })
        };
    GeometryCollection collection = gf.createGeometryCollection(geometries);

    // Transform to Web Mercator
    GeometryCollection transformed =
        (GeometryCollection) JTSTransform.transform(collection, "EPSG:4326", "EPSG:3857", gf);

    // Verify transformation
    assertThat(transformed).isNotNull();
    assertThat(transformed.getNumGeometries()).isEqualTo(3);
    assertThat(transformed.getGeometryN(0)).isInstanceOf(org.locationtech.jts.geom.Point.class);
    assertThat(transformed.getGeometryN(1)).isInstanceOf(LineString.class);
    assertThat(transformed.getGeometryN(2)).isInstanceOf(Polygon.class);
  }

  @Test
  void testTransformUTM() {
    // Create a point in WGS84
    org.locationtech.jts.geom.Point point = gf.createPoint(new Coordinate(-71.0, 41.0));

    // Transform to UTM Zone 19N
    org.locationtech.jts.geom.Point transformed =
        (org.locationtech.jts.geom.Point)
            JTSTransform.transform(point, "EPSG:4326", "EPSG:32619", gf);

    // Verify transformation (approximate UTM coordinates for Boston)
    assertThat(transformed).isNotNull();
    assertThat(transformed.getX()).isCloseTo(331792.1148057905, offset(10.0));
    assertThat(transformed.getY()).isCloseTo(4540683.529276983, offset(200.0));
  }

  @Test
  void testTransformWithConverter() {
    // Create a converter for multiple transformations
    Proj4Sedona.Converter converter = Proj4Sedona.converter("EPSG:4326", "EPSG:3857");

    // Transform multiple geometries using the same converter
    org.locationtech.jts.geom.Point point1 = gf.createPoint(new Coordinate(-71.0, 41.0));
    org.locationtech.jts.geom.Point point2 = gf.createPoint(new Coordinate(-70.0, 42.0));

    org.locationtech.jts.geom.Point transformed1 =
        (org.locationtech.jts.geom.Point) JTSTransform.transform(point1, converter, gf);
    org.locationtech.jts.geom.Point transformed2 =
        (org.locationtech.jts.geom.Point) JTSTransform.transform(point2, converter, gf);

    // Verify both transformations
    assertThat(transformed1).isNotNull();
    assertThat(transformed2).isNotNull();
    assertThat(transformed1.getX()).isCloseTo(-7903683.846322424, offset(0.1));
    assertThat(transformed2.getX()).isCloseTo(-7792364.365146369, offset(0.1));
  }

  @Test
  void testTransformEmptyGeometry() {
    // Create empty polygon
    Polygon emptyPolygon = gf.createPolygon((Coordinate[]) null);

    // Transform empty geometry
    Polygon transformed =
        (Polygon) JTSTransform.transform(emptyPolygon, "EPSG:4326", "EPSG:3857", gf);

    // Verify it returns the same empty geometry
    assertThat(transformed).isNotNull();
    assertThat(transformed.isEmpty()).isTrue();
  }

  @Test
  void testTransformNullGeometry() {
    // Test null geometry
    assertThatThrownBy(() -> JTSTransform.transform(null, "EPSG:4326", "EPSG:3857", gf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Geometry cannot be null");
  }

  @Test
  void testTransformNullConverter() {
    // Test null converter
    org.locationtech.jts.geom.Point point = gf.createPoint(new Coordinate(-71.0, 41.0));

    assertThatThrownBy(() -> JTSTransform.transform(point, (Proj4Sedona.Converter) null, gf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Converter cannot be null");
  }

  @Test
  void testRoundTripTransformation() {
    // Create a polygon
    Coordinate[] coords =
        new Coordinate[] {
          new Coordinate(-71.0, 41.0),
          new Coordinate(-71.0, 42.0),
          new Coordinate(-70.0, 42.0),
          new Coordinate(-70.0, 41.0),
          new Coordinate(-71.0, 41.0)
        };
    Polygon original = gf.createPolygon(coords);

    // Transform to Web Mercator and back
    Polygon toWebMercator =
        (Polygon) JTSTransform.transform(original, "EPSG:4326", "EPSG:3857", gf);
    Polygon backToWGS84 =
        (Polygon) JTSTransform.transform(toWebMercator, "EPSG:3857", "EPSG:4326", gf);

    // Verify round-trip accuracy (should be very close to original)
    for (int i = 0; i < original.getNumPoints(); i++) {
      Coordinate origCoord = original.getExteriorRing().getCoordinateN(i);
      Coordinate roundTripCoord = backToWGS84.getExteriorRing().getCoordinateN(i);

      assertThat(roundTripCoord.x).isCloseTo(origCoord.x, offset(1e-6));
      assertThat(roundTripCoord.y).isCloseTo(origCoord.y, offset(1e-6));
    }
  }

  @Test
  void testTransformWithPROJString() {
    // Create a point
    org.locationtech.jts.geom.Point point = gf.createPoint(new Coordinate(-71.0, 41.0));

    // Transform using PROJ strings instead of EPSG codes
    org.locationtech.jts.geom.Point transformed =
        (org.locationtech.jts.geom.Point)
            JTSTransform.transform(
                point, "+proj=longlat +datum=WGS84", "+proj=utm +zone=19 +datum=WGS84", gf);

    // Verify transformation
    assertThat(transformed).isNotNull();
    assertThat(transformed.getX()).isCloseTo(331792.1148057905, offset(10.0));
    assertThat(transformed.getY()).isCloseTo(4540683.529276983, offset(200.0));
  }

  @Test
  void testTransformWithIntEPSG() {
    // Create a point in WGS84
    org.locationtech.jts.geom.Point point = gf.createPoint(new Coordinate(-71.0, 41.0));

    // Transform using integer EPSG codes
    org.locationtech.jts.geom.Point transformed =
        (org.locationtech.jts.geom.Point) JTSTransform.transform(point, 4326, 3857, gf);

    // Verify transformation
    assertThat(transformed).isNotNull();
    assertThat(transformed.getX()).isCloseTo(-7903683.846322424, offset(0.1));
    assertThat(transformed.getY()).isCloseTo(5012341.663847514, offset(0.1));
  }

  @Test
  void testTransformStringToInt() {
    // Create a point
    org.locationtech.jts.geom.Point point = gf.createPoint(new Coordinate(-71.0, 41.0));

    // Transform from PROJ string to EPSG code (int)
    org.locationtech.jts.geom.Point transformed =
        (org.locationtech.jts.geom.Point)
            JTSTransform.transform(point, "+proj=longlat +datum=WGS84", 3857, gf);

    // Verify transformation
    assertThat(transformed).isNotNull();
    assertThat(transformed.getX()).isCloseTo(-7903683.846322424, offset(0.1));
    assertThat(transformed.getY()).isCloseTo(5012341.663847514, offset(0.1));
  }

  @Test
  void testTransformIntToString() {
    // Create a point in Web Mercator
    org.locationtech.jts.geom.Point point =
        gf.createPoint(new Coordinate(-7903683.846322424, 5012341.663847514));

    // Transform from EPSG code (int) to PROJ string
    org.locationtech.jts.geom.Point transformed =
        (org.locationtech.jts.geom.Point)
            JTSTransform.transform(point, 3857, "+proj=longlat +datum=WGS84", gf);

    // Verify transformation (should get back to original WGS84)
    assertThat(transformed).isNotNull();
    assertThat(transformed.getX()).isCloseTo(-71.0, offset(1e-6));
    assertThat(transformed.getY()).isCloseTo(41.0, offset(1e-6));
  }

  @Test
  void testTransformPolygonWithIntEPSG() {
    // Create a polygon in WGS84
    Coordinate[] coords =
        new Coordinate[] {
          new Coordinate(-71.0, 41.0),
          new Coordinate(-71.0, 42.0),
          new Coordinate(-70.0, 42.0),
          new Coordinate(-70.0, 41.0),
          new Coordinate(-71.0, 41.0)
        };
    Polygon polygon = gf.createPolygon(coords);

    // Transform using integer EPSG codes - cleaner API!
    Polygon transformed = (Polygon) JTSTransform.transform(polygon, 4326, 3857, gf);

    // Verify transformation
    assertThat(transformed).isNotNull();
    assertThat(transformed.getNumPoints()).isEqualTo(5);

    // Check corner point
    Coordinate firstCoord = transformed.getExteriorRing().getCoordinateN(0);
    assertThat(firstCoord.x).isCloseTo(-7903683.846322424, offset(0.1));
    assertThat(firstCoord.y).isCloseTo(5012341.663847514, offset(0.1));
  }

  /**
   * Helper method to create offset for double comparisons.
   *
   * @param offset the offset value
   * @return offset for assertions
   */
  private static org.assertj.core.data.Offset<Double> offset(double offset) {
    return org.assertj.core.data.Offset.offset(offset);
  }
}
