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
package org.apache.sedona.proj.optimization;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.sedona.proj.core.Point;
import org.apache.sedona.proj.core.Projection;
import org.junit.jupiter.api.Test;

/** Comprehensive tests for BatchTransformer optimization utilities. */
public class BatchTransformerTest {

  private static final double EPSILON = 1e-6;

  @Test
  public void testConstructorWithStrings() {
    BatchTransformer transformer = new BatchTransformer("EPSG:4326", "EPSG:3857", false);
    assertNotNull(transformer);
    assertNotNull(transformer.getFromProjection());
    assertNotNull(transformer.getToProjection());
  }

  @Test
  public void testConstructorWithProjections() {
    Projection wgs84 = new Projection("EPSG:4326");
    Projection webMercator = new Projection("EPSG:3857");
    BatchTransformer transformer = new BatchTransformer(wgs84, webMercator, false);
    assertNotNull(transformer);
    assertEquals(wgs84, transformer.getFromProjection());
    assertEquals(webMercator, transformer.getToProjection());
  }

  @Test
  public void testTransformSinglePoint() {
    BatchTransformer transformer = new BatchTransformer("EPSG:4326", "EPSG:3857", false);
    Point wgs84Point = new Point(0, 0); // Prime meridian, equator
    Point transformed = transformer.transform(wgs84Point);

    assertNotNull(transformed);
    assertEquals(0.0, transformed.x, EPSILON);
    assertEquals(0.0, transformed.y, EPSILON);
  }

  @Test
  public void testTransformBatch() {
    BatchTransformer transformer = new BatchTransformer("EPSG:4326", "EPSG:3857", false);

    List<Point> points = new ArrayList<>();
    points.add(new Point(0, 0));
    points.add(new Point(10, 20));
    points.add(new Point(-5, 15));

    List<Point> transformed = transformer.transformBatch(points);

    assertNotNull(transformed);
    assertEquals(3, transformed.size());
    assertNotNull(transformed.get(0));
    assertNotNull(transformed.get(1));
    assertNotNull(transformed.get(2));
  }

  @Test
  public void testTransformBatchFiltered() {
    BatchTransformer transformer = new BatchTransformer("EPSG:4326", "EPSG:3857", false);

    List<Point> points = new ArrayList<>();
    points.add(new Point(0, 0));
    points.add(null); // Should be filtered out
    points.add(new Point(10, 20));
    points.add(null); // Should be filtered out
    points.add(new Point(-5, 15));

    List<Point> transformed = transformer.transformBatchFiltered(points);

    assertNotNull(transformed);
    assertEquals(3, transformed.size());
    assertNotNull(transformed.get(0));
    assertNotNull(transformed.get(1));
    assertNotNull(transformed.get(2));
  }

  @Test
  public void testTransformArrays() {
    BatchTransformer transformer = new BatchTransformer("EPSG:4326", "EPSG:3857", false);

    double[] xCoords = {0, 10, -5};
    double[] yCoords = {0, 20, 15};

    Point[] transformed = transformer.transformArrays(xCoords, yCoords);

    assertNotNull(transformed);
    assertEquals(3, transformed.length);
    assertNotNull(transformed[0]);
    assertNotNull(transformed[1]);
    assertNotNull(transformed[2]);
  }

  @Test
  public void testTransformArraysMismatchedLength() {
    BatchTransformer transformer = new BatchTransformer("EPSG:4326", "EPSG:3857", false);

    double[] xCoords = {0, 10, -5};
    double[] yCoords = {0, 20}; // Different length

    assertThrows(
        IllegalArgumentException.class, () -> transformer.transformArrays(xCoords, yCoords));
  }

  @Test
  public void testTransformBatchEmptyList() {
    BatchTransformer transformer = new BatchTransformer("EPSG:4326", "EPSG:3857", false);

    List<Point> points = new ArrayList<>();
    List<Point> transformed = transformer.transformBatch(points);

    assertNotNull(transformed);
    assertEquals(0, transformed.size());
  }

  @Test
  public void testTransformBatchFilteredAllNulls() {
    BatchTransformer transformer = new BatchTransformer("EPSG:4326", "EPSG:3857", false);

    List<Point> points = Arrays.asList(null, null, null);
    List<Point> transformed = transformer.transformBatchFiltered(points);

    assertNotNull(transformed);
    assertEquals(0, transformed.size());
  }

  @Test
  public void testTransformArraysEmptyArrays() {
    BatchTransformer transformer = new BatchTransformer("EPSG:4326", "EPSG:3857", false);

    double[] xCoords = {};
    double[] yCoords = {};

    Point[] transformed = transformer.transformArrays(xCoords, yCoords);

    assertNotNull(transformed);
    assertEquals(0, transformed.length);
  }

  @Test
  public void testUTMTransformationBatch() {
    BatchTransformer transformer =
        new BatchTransformer("EPSG:4326", "EPSG:32633", false); // WGS84 to UTM Zone 33N

    List<Point> points = new ArrayList<>();
    points.add(new Point(15, 45)); // Central meridian of zone 33
    points.add(new Point(16, 46));
    points.add(new Point(14, 44));

    List<Point> transformed = transformer.transformBatch(points);

    assertEquals(3, transformed.size());
    for (Point p : transformed) {
      assertNotNull(p);
      assertTrue(p.x > 0); // Should be in positive easting
      assertTrue(p.y > 0); // Should be in positive northing
    }
  }

  @Test
  public void testRoundTripTransformation() {
    BatchTransformer forward = new BatchTransformer("EPSG:4326", "EPSG:3857", false);
    BatchTransformer backward = new BatchTransformer("EPSG:3857", "EPSG:4326", false);

    List<Point> original = new ArrayList<>();
    original.add(new Point(0, 0));
    original.add(new Point(10, 20));
    original.add(new Point(-5, 15));

    List<Point> forwardTransformed = forward.transformBatch(original);
    List<Point> backwardTransformed = backward.transformBatch(forwardTransformed);

    assertEquals(original.size(), backwardTransformed.size());
    for (int i = 0; i < original.size(); i++) {
      assertEquals(original.get(i).x, backwardTransformed.get(i).x, 1e-2);
      assertEquals(original.get(i).y, backwardTransformed.get(i).y, 1e-2);
    }
  }

  @Test
  public void testLargeBatchTransformation() {
    BatchTransformer transformer = new BatchTransformer("EPSG:4326", "EPSG:3857", false);

    // Create large batch - limit coordinates to valid ranges
    List<Point> points = new ArrayList<>();
    for (int i = 0; i < 1000; i++) {
      double lon = (i % 360) - 180; // -180 to 179
      double lat = ((i % 180) - 90); // -90 to 89
      // Clamp latitude to valid range
      if (lat > 85) lat = 85;
      if (lat < -85) lat = -85;
      points.add(new Point(lon, lat));
    }

    List<Point> transformed = transformer.transformBatch(points);

    assertTrue(transformed.size() > 0); // Some may fail
    for (Point p : transformed) {
      if (p != null) {
        assertTrue(Double.isFinite(p.x));
        assertTrue(Double.isFinite(p.y));
      }
    }
  }

  @Test
  public void testWithEnforceAxis() {
    BatchTransformer transformer = new BatchTransformer("EPSG:4326", "EPSG:3857", true);

    Point point = new Point(10, 20);
    Point transformed = transformer.transform(point);

    assertNotNull(transformed);
  }

  @Test
  public void testGetProjections() {
    Projection wgs84 = new Projection("EPSG:4326");
    Projection webMercator = new Projection("EPSG:3857");
    BatchTransformer transformer = new BatchTransformer(wgs84, webMercator, false);

    assertSame(wgs84, transformer.getFromProjection());
    assertSame(webMercator, transformer.getToProjection());
  }
}
