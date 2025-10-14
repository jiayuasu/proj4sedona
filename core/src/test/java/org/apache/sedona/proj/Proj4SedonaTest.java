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

import static org.assertj.core.api.Assertions.*;

import org.apache.sedona.proj.core.Point;
import org.junit.jupiter.api.Test;

/** Basic tests for the Proj4Sedona library. */
public class Proj4SedonaTest {

  @Test
  public void testPointCreation() {
    Point point = new Point(10.0, 20.0);
    assertThat(point.x).isEqualTo(10.0);
    assertThat(point.y).isEqualTo(20.0);
    assertThat(point.z).isEqualTo(0.0);
    assertThat(point.m).isEqualTo(0.0);
  }

  @Test
  public void testPointFromArray() {
    double[] coords = {10.0, 20.0, 30.0, 40.0};
    Point point = Point.fromArray(coords);
    assertThat(point.x).isEqualTo(10.0);
    assertThat(point.y).isEqualTo(20.0);
    assertThat(point.z).isEqualTo(30.0);
    assertThat(point.m).isEqualTo(40.0);
  }

  @Test
  public void testPointFromString() {
    Point point = Point.fromString("10.0,20.0,30.0,40.0");
    assertThat(point.x).isEqualTo(10.0);
    assertThat(point.y).isEqualTo(20.0);
    assertThat(point.z).isEqualTo(30.0);
    assertThat(point.m).isEqualTo(40.0);
  }

  @Test
  public void testPointCopy() {
    Point original = new Point(10.0, 20.0, 30.0, 40.0);
    Point copy = original.copy();
    assertThat(copy).isNotSameAs(original);
    assertThat(copy).isEqualTo(original);
  }

  @Test
  public void testPointToString() {
    Point point = new Point(10.123456, 20.789012);
    String str = point.toString();
    assertThat(str).contains("10.123456");
    assertThat(str).contains("20.789012");
  }

  @Test
  public void testWGS84Projection() {
    Point point = new Point(-71.0, 41.0);
    Point result = Proj4Sedona.transform("WGS84", point);

    // For WGS84 to WGS84, should be identity transformation
    assertThat(result.x).isCloseTo(-71.0, within(1e-10));
    assertThat(result.y).isCloseTo(41.0, within(1e-10));
  }

  @Test
  public void testConverterCreation() {
    Proj4Sedona.Converter converter = Proj4Sedona.converter("WGS84");
    assertThat(converter).isNotNull();
    assertThat(converter.getProjection()).isNotNull();
  }

  @Test
  public void testToPointUtility() {
    Point point = Proj4Sedona.toPoint(10.0, 20.0);
    assertThat(point.x).isEqualTo(10.0);
    assertThat(point.y).isEqualTo(20.0);

    double[] coords = {30.0, 40.0, 50.0};
    Point point2 = Proj4Sedona.toPoint(coords);
    assertThat(point2.x).isEqualTo(30.0);
    assertThat(point2.y).isEqualTo(40.0);
    assertThat(point2.z).isEqualTo(50.0);
  }

  @Test
  public void testVersion() {
    String version = Proj4Sedona.getVersion();
    assertThat(version).isEqualTo("1.0.0-SNAPSHOT");
  }
}
