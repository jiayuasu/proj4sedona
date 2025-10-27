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

import static org.junit.jupiter.api.Assertions.*;

import org.apache.sedona.proj.Proj4Sedona;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

/** Test suite for JTSTransform class. */
class JTSTransformTest {

  private static final double EPSILON = 0.1; // Default tolerance
  private static final double HIGH_PRECISION = 1e-6; // High precision tolerance
  private static final double UTM_TOLERANCE = 200.0; // UTM tolerance

  private GeometryFactory gf;

  @BeforeEach
  void setUp() {
    gf = new GeometryFactory();
  }

  @Test
  void testTransformPoint() {
    org.locationtech.jts.geom.Point point = gf.createPoint(new Coordinate(-71.0, 41.0));
    org.locationtech.jts.geom.Point transformed =
        (org.locationtech.jts.geom.Point)
            JTSTransform.transform(point, "EPSG:4326", "EPSG:3857", gf);

    assertNotNull(transformed);
    assertEquals(-7903683.846322424, transformed.getX(), EPSILON);
    assertEquals(5012341.663847514, transformed.getY(), EPSILON);
  }

  @Test
  void testTransformPointWithZ() {
    org.locationtech.jts.geom.Point point = gf.createPoint(new Coordinate(-71.0, 41.0, 100.0));
    org.locationtech.jts.geom.Point transformed =
        (org.locationtech.jts.geom.Point)
            JTSTransform.transform(point, "EPSG:4326", "EPSG:3857", gf);

    assertNotNull(transformed);
    assertEquals(-7903683.846322424, transformed.getX(), EPSILON);
    assertEquals(5012341.663847514, transformed.getY(), EPSILON);
    assertEquals(100.0, transformed.getCoordinate().getZ(), EPSILON);
  }

  @Test
  void testTransformLineString() {
    Coordinate[] coords =
        new Coordinate[] {
          new Coordinate(-71.0, 41.0), new Coordinate(-70.0, 42.0), new Coordinate(-69.0, 43.0)
        };
    LineString lineString = gf.createLineString(coords);

    LineString transformed =
        (LineString) JTSTransform.transform(lineString, "EPSG:4326", "EPSG:3857", gf);

    assertNotNull(transformed);
    assertEquals(3, transformed.getNumPoints());
    assertEquals(-7903683.846322424, transformed.getCoordinateN(0).x, EPSILON);
    assertEquals(5012341.663847514, transformed.getCoordinateN(0).y, EPSILON);
    assertEquals(-7681044.864735875, transformed.getCoordinateN(2).x, 1.0);
    assertEquals(5311971.846945471, transformed.getCoordinateN(2).y, 1.0);
  }

  @Test
  void testTransformPolygon() {
    Coordinate[] coords =
        new Coordinate[] {
          new Coordinate(-71.0, 41.0),
          new Coordinate(-71.0, 42.0),
          new Coordinate(-70.0, 42.0),
          new Coordinate(-70.0, 41.0),
          new Coordinate(-71.0, 41.0)
        };
    Polygon polygon = gf.createPolygon(coords);

    Polygon transformed = (Polygon) JTSTransform.transform(polygon, "EPSG:4326", "EPSG:3857", gf);

    assertNotNull(transformed);
    assertEquals(5, transformed.getNumPoints());
    assertTrue(transformed.getArea() > polygon.getArea());

    Coordinate firstCoord = transformed.getExteriorRing().getCoordinateN(0);
    assertEquals(-7903683.846322424, firstCoord.x, EPSILON);
    assertEquals(5012341.663847514, firstCoord.y, EPSILON);
  }

  @Test
  void testTransformPolygonWithHole() {
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

    Polygon transformed = (Polygon) JTSTransform.transform(polygon, "EPSG:4326", "EPSG:3857", gf);

    assertNotNull(transformed);
    assertEquals(1, transformed.getNumInteriorRing());

    LinearRing transformedHole = (LinearRing) transformed.getInteriorRingN(0);
    assertEquals(5, transformedHole.getNumPoints());
  }

  @Test
  void testTransformMultiPoint() {
    org.locationtech.jts.geom.Point[] points =
        new org.locationtech.jts.geom.Point[] {
          gf.createPoint(new Coordinate(-71.0, 41.0)),
          gf.createPoint(new Coordinate(-70.0, 42.0)),
          gf.createPoint(new Coordinate(-69.0, 43.0))
        };
    MultiPoint multiPoint = gf.createMultiPoint(points);

    MultiPoint transformed =
        (MultiPoint) JTSTransform.transform(multiPoint, "EPSG:4326", "EPSG:3857", gf);

    assertNotNull(transformed);
    assertEquals(3, transformed.getNumGeometries());

    org.locationtech.jts.geom.Point firstPoint =
        (org.locationtech.jts.geom.Point) transformed.getGeometryN(0);
    assertEquals(-7903683.846322424, firstPoint.getX(), EPSILON);
    assertEquals(5012341.663847514, firstPoint.getY(), EPSILON);
  }

  @Test
  void testTransformMultiLineString() {
    LineString[] lineStrings =
        new LineString[] {
          gf.createLineString(
              new Coordinate[] {new Coordinate(-71.0, 41.0), new Coordinate(-70.0, 42.0)}),
          gf.createLineString(
              new Coordinate[] {new Coordinate(-69.0, 43.0), new Coordinate(-68.0, 44.0)})
        };
    MultiLineString multiLineString = gf.createMultiLineString(lineStrings);

    MultiLineString transformed =
        (MultiLineString) JTSTransform.transform(multiLineString, "EPSG:4326", "EPSG:3857", gf);

    assertNotNull(transformed);
    assertEquals(2, transformed.getNumGeometries());
  }

  @Test
  void testTransformMultiPolygon() {
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

    MultiPolygon transformed =
        (MultiPolygon) JTSTransform.transform(multiPolygon, "EPSG:4326", "EPSG:3857", gf);

    assertNotNull(transformed);
    assertEquals(2, transformed.getNumGeometries());
  }

  @Test
  void testTransformGeometryCollection() {
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

    GeometryCollection transformed =
        (GeometryCollection) JTSTransform.transform(collection, "EPSG:4326", "EPSG:3857", gf);

    assertNotNull(transformed);
    assertEquals(3, transformed.getNumGeometries());
    assertTrue(transformed.getGeometryN(0) instanceof org.locationtech.jts.geom.Point);
    assertTrue(transformed.getGeometryN(1) instanceof LineString);
    assertTrue(transformed.getGeometryN(2) instanceof Polygon);
  }

  @Test
  void testTransformUTM() {
    org.locationtech.jts.geom.Point point = gf.createPoint(new Coordinate(-71.0, 41.0));
    org.locationtech.jts.geom.Point transformed =
        (org.locationtech.jts.geom.Point)
            JTSTransform.transform(point, "EPSG:4326", "EPSG:32619", gf);

    assertNotNull(transformed);
    assertEquals(331792.1148057905, transformed.getX(), 10.0);
    assertEquals(4540683.529276983, transformed.getY(), UTM_TOLERANCE);
  }

  @Test
  void testTransformWithConverter() {
    Proj4Sedona.Converter converter = Proj4Sedona.converter("EPSG:4326", "EPSG:3857");

    org.locationtech.jts.geom.Point point1 = gf.createPoint(new Coordinate(-71.0, 41.0));
    org.locationtech.jts.geom.Point point2 = gf.createPoint(new Coordinate(-70.0, 42.0));

    org.locationtech.jts.geom.Point transformed1 =
        (org.locationtech.jts.geom.Point) JTSTransform.transform(point1, converter, gf);
    org.locationtech.jts.geom.Point transformed2 =
        (org.locationtech.jts.geom.Point) JTSTransform.transform(point2, converter, gf);

    assertNotNull(transformed1);
    assertNotNull(transformed2);
    assertEquals(-7903683.846322424, transformed1.getX(), EPSILON);
    assertEquals(-7792364.365146369, transformed2.getX(), EPSILON);
  }

  @Test
  void testTransformEmptyGeometry() {
    Polygon emptyPolygon = gf.createPolygon((Coordinate[]) null);
    Polygon transformed =
        (Polygon) JTSTransform.transform(emptyPolygon, "EPSG:4326", "EPSG:3857", gf);

    assertNotNull(transformed);
    assertTrue(transformed.isEmpty());
  }

  @Test
  void testTransformNullGeometry() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> JTSTransform.transform(null, "EPSG:4326", "EPSG:3857", gf));

    assertTrue(exception.getMessage().contains("Geometry cannot be null"));
  }

  @Test
  void testTransformNullConverter() {
    org.locationtech.jts.geom.Point point = gf.createPoint(new Coordinate(-71.0, 41.0));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> JTSTransform.transform(point, (Proj4Sedona.Converter) null, gf));

    assertTrue(exception.getMessage().contains("Converter cannot be null"));
  }

  @Test
  void testRoundTripTransformation() {
    Coordinate[] coords =
        new Coordinate[] {
          new Coordinate(-71.0, 41.0),
          new Coordinate(-71.0, 42.0),
          new Coordinate(-70.0, 42.0),
          new Coordinate(-70.0, 41.0),
          new Coordinate(-71.0, 41.0)
        };
    Polygon original = gf.createPolygon(coords);

    Polygon toWebMercator =
        (Polygon) JTSTransform.transform(original, "EPSG:4326", "EPSG:3857", gf);
    Polygon backToWGS84 =
        (Polygon) JTSTransform.transform(toWebMercator, "EPSG:3857", "EPSG:4326", gf);

    for (int i = 0; i < original.getNumPoints(); i++) {
      Coordinate origCoord = original.getExteriorRing().getCoordinateN(i);
      Coordinate roundTripCoord = backToWGS84.getExteriorRing().getCoordinateN(i);

      assertEquals(origCoord.x, roundTripCoord.x, HIGH_PRECISION);
      assertEquals(origCoord.y, roundTripCoord.y, HIGH_PRECISION);
    }
  }

  @Test
  void testTransformWithPROJString() {
    org.locationtech.jts.geom.Point point = gf.createPoint(new Coordinate(-71.0, 41.0));
    org.locationtech.jts.geom.Point transformed =
        (org.locationtech.jts.geom.Point)
            JTSTransform.transform(
                point, "+proj=longlat +datum=WGS84", "+proj=utm +zone=19 +datum=WGS84", gf);

    assertNotNull(transformed);
    assertEquals(331792.1148057905, transformed.getX(), 10.0);
    assertEquals(4540683.529276983, transformed.getY(), UTM_TOLERANCE);
  }

  @Test
  void testTransformWithIntEPSG() {
    org.locationtech.jts.geom.Point point = gf.createPoint(new Coordinate(-71.0, 41.0));
    org.locationtech.jts.geom.Point transformed =
        (org.locationtech.jts.geom.Point) JTSTransform.transform(point, 4326, 3857, gf);

    assertNotNull(transformed);
    assertEquals(-7903683.846322424, transformed.getX(), EPSILON);
    assertEquals(5012341.663847514, transformed.getY(), EPSILON);
  }

  @Test
  void testTransformStringToInt() {
    org.locationtech.jts.geom.Point point = gf.createPoint(new Coordinate(-71.0, 41.0));
    org.locationtech.jts.geom.Point transformed =
        (org.locationtech.jts.geom.Point)
            JTSTransform.transform(point, "+proj=longlat +datum=WGS84", 3857, gf);

    assertNotNull(transformed);
    assertEquals(-7903683.846322424, transformed.getX(), EPSILON);
    assertEquals(5012341.663847514, transformed.getY(), EPSILON);
  }

  @Test
  void testTransformIntToString() {
    org.locationtech.jts.geom.Point point =
        gf.createPoint(new Coordinate(-7903683.846322424, 5012341.663847514));
    org.locationtech.jts.geom.Point transformed =
        (org.locationtech.jts.geom.Point)
            JTSTransform.transform(point, 3857, "+proj=longlat +datum=WGS84", gf);

    assertNotNull(transformed);
    assertEquals(-71.0, transformed.getX(), HIGH_PRECISION);
    assertEquals(41.0, transformed.getY(), HIGH_PRECISION);
  }

  @Test
  void testTransformPolygonWithIntEPSG() {
    Coordinate[] coords =
        new Coordinate[] {
          new Coordinate(-71.0, 41.0),
          new Coordinate(-71.0, 42.0),
          new Coordinate(-70.0, 42.0),
          new Coordinate(-70.0, 41.0),
          new Coordinate(-71.0, 41.0)
        };
    Polygon polygon = gf.createPolygon(coords);

    Polygon transformed = (Polygon) JTSTransform.transform(polygon, 4326, 3857, gf);

    assertNotNull(transformed);
    assertEquals(5, transformed.getNumPoints());

    Coordinate firstCoord = transformed.getExteriorRing().getCoordinateN(0);
    assertEquals(-7903683.846322424, firstCoord.x, EPSILON);
    assertEquals(5012341.663847514, firstCoord.y, EPSILON);
  }
}
