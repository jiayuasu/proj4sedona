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
package org.apache.sedona.proj.core;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Tests for the enhanced Projection class with PROJ string support. */
public class ProjectionTest {

  @Test
  public void testCreateProjectionFromProjString() {
    String projString =
        "+proj=merc +lat_ts=0 +lon_0=0 +k=1.0 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs";
    Projection proj = new Projection(projString);

    assertThat(proj.projName).isEqualTo("merc");
    assertThat(proj.name).isEqualTo("merc");
    assertThat(proj.datumCode).isEqualTo("WGS84");
    assertThat(proj.units).isEqualTo("m");
    assertThat(proj.lat_ts).isEqualTo(0.0);
    assertThat(proj.long0).isEqualTo(0.0);
    assertThat(proj.k0).isEqualTo(1.0);
    assertThat(proj.x0).isEqualTo(0.0);
    assertThat(proj.y0).isEqualTo(0.0);
  }

  @Test
  public void testCreateLongLatProjectionFromProjString() {
    String projString = "+proj=longlat +datum=WGS84 +no_defs";
    Projection proj = new Projection(projString);

    assertThat(proj.projName).isEqualTo("longlat");
    assertThat(proj.name).isEqualTo("longlat");
    assertThat(proj.datumCode).isEqualTo("WGS84");
    assertThat(proj.forward).isNotNull();
    assertThat(proj.inverse).isNotNull();
  }

  @Test
  public void testCreateUTMProjectionFromProjString() {
    String projString = "+proj=utm +zone=10 +datum=WGS84 +units=m +no_defs";
    Projection proj = new Projection(projString);

    assertThat(proj.projName).isEqualTo("utm");
    assertThat(proj.datumCode).isEqualTo("WGS84");
    assertThat(proj.units).isEqualTo("m");
    // Note: zone parameter would be stored but UTM transformation not yet implemented
  }

  @Test
  public void testCreateLambertProjectionFromProjString() {
    String projString =
        "+proj=lcc +lat_1=33 +lat_2=45 +lat_0=39 +lon_0=-96 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs";
    Projection proj = new Projection(projString);

    assertThat(proj.projName).isEqualTo("lcc");
    assertThat(proj.datumCode).isEqualTo("WGS84");
    assertThat(proj.units).isEqualTo("m");
    // Note: Lambert transformation not yet implemented
  }

  @Test
  public void testCreateProjectionWithCustomEllipsoid() {
    String projString = "+proj=merc +a=6378137 +b=6356752.314245 +datum=WGS84 +units=m +no_defs";
    Projection proj = new Projection(projString);

    assertThat(proj.projName).isEqualTo("merc");
    assertThat(proj.a).isEqualTo(6378137.0);
    assertThat(proj.b).isEqualTo(6356752.314245);
    assertThat(proj.datumCode).isEqualTo("WGS84");
  }

  @Test
  public void testCreateProjectionWithPrimeMeridian() {
    String projString = "+proj=longlat +datum=WGS84 +pm=paris +no_defs";
    Projection proj = new Projection(projString);

    assertThat(proj.projName).isEqualTo("longlat");
    assertThat(proj.datumCode).isEqualTo("WGS84");
    assertThat(proj.from_greenwich).isNotEqualTo(0.0);
  }

  @Test
  public void testCreateProjectionWithTowgs84() {
    String projString = "+proj=longlat +datum=WGS84 +towgs84=0,0,0 +no_defs";
    Projection proj = new Projection(projString);

    assertThat(proj.projName).isEqualTo("longlat");
    assertThat(proj.datumCode).isEqualTo("WGS84");
    assertThat(proj.datum_params).isNotNull();
    assertThat(proj.datum_params).contains("0", "0", "0");
  }

  @Test
  public void testCreateProjectionWithNadgrids() {
    String projString = "+proj=longlat +datum=NAD27 +nadgrids=@conus +no_defs";
    Projection proj = new Projection(projString);

    assertThat(proj.projName).isEqualTo("longlat");
    assertThat(proj.datumCode).isEqualTo("NAD27");
    assertThat(proj.nadgrids).isEqualTo("@conus");
  }

  @Test
  public void testMercatorForwardTransformation() {
    String projString =
        "+proj=merc +lat_ts=0 +lon_0=0 +k=1.0 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs";
    Projection proj = new Projection(projString);

    Point input = new Point(0.0, 0.0); // Longitude, Latitude in radians
    Point result = proj.forward.transform(input);

    assertThat(result).isNotNull();
    assertThat(result.x).isCloseTo(0.0, within(1e-6));
    assertThat(result.y).isCloseTo(0.0, within(1e-6));
  }

  @Test
  public void testMercatorInverseTransformation() {
    String projString =
        "+proj=merc +lat_ts=0 +lon_0=0 +k=1.0 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs";
    Projection proj = new Projection(projString);

    Point input = new Point(0.0, 0.0); // X, Y in meters
    Point result = proj.inverse.transform(input);

    assertThat(result).isNotNull();
    assertThat(result.x).isCloseTo(0.0, within(1e-6));
    assertThat(result.y).isCloseTo(0.0, within(1e-6));
  }

  @Test
  public void testLongLatIdentityTransformation() {
    String projString = "+proj=longlat +datum=WGS84 +no_defs";
    Projection proj = new Projection(projString);

    Point input = new Point(1.0, 2.0, 3.0, 4.0);
    Point forwardResult = proj.forward.transform(input);
    Point inverseResult = proj.inverse.transform(input);

    assertThat(forwardResult).isEqualTo(input);
    assertThat(inverseResult).isEqualTo(input);
  }

  @Test
  public void testCreateProjectionWithInvalidString() {
    assertThatThrownBy(() -> new Projection("invalid proj string"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported SRS code");
  }

  @Test
  public void testCreateProjectionWithNullString() {
    assertThatThrownBy(() -> new Projection((String) null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SRS code cannot be null or empty");
  }

  @Test
  public void testCreateProjectionWithEmptyString() {
    assertThatThrownBy(() -> new Projection(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SRS code cannot be null or empty");
  }

  @Test
  public void testCreateProjectionWithWhitespaceString() {
    assertThatThrownBy(() -> new Projection("   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SRS code cannot be null or empty");
  }

  @Test
  public void testProjectionWithDefaultValues() {
    String projString = "+proj=merc +datum=WGS84 +no_defs";
    Projection proj = new Projection(projString);

    // Check that default values are set correctly
    assertThat(proj.lat0).isEqualTo(0.0);
    assertThat(proj.long0).isEqualTo(0.0);
    assertThat(proj.x0).isEqualTo(0.0);
    assertThat(proj.y0).isEqualTo(0.0);
    assertThat(proj.k0).isEqualTo(1.0);
    assertThat(proj.axis).isEqualTo("enu");
    assertThat(proj.units).isEqualTo("m");
  }

  @Test
  public void testProjectionWithCustomUnits() {
    String projString = "+proj=merc +datum=WGS84 +units=ft +no_defs";
    Projection proj = new Projection(projString);

    assertThat(proj.units).isEqualTo("ft");
    assertThat(proj.to_meter).isNotEqualTo(1.0); // Should be conversion factor for feet
  }

  @Test
  public void testProjectionWithCustomAxis() {
    String projString = "+proj=longlat +datum=WGS84 +axis=neu +no_defs";
    Projection proj = new Projection(projString);

    assertThat(proj.axis).isEqualTo("neu");
  }
}
