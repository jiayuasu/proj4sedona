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
package org.apache.sedona.proj.wkt;

import static org.assertj.core.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests for the WKT processor. */
public class WKTProcessorTest {

  @Test
  public void testProcessGeographicWKT() throws WKTParseException {
    String wkt =
        "GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\",SPHEROID[\"WGS 84\",6378137,298.257223563]],PRIMEM[\"Greenwich\",0],UNIT[\"degree\",0.0174532925199433]]";

    Map<String, Object> result = WKTProcessor.process(wkt);

    assertThat(result).isNotNull();
    assertThat(result.get("projName")).isEqualTo("longlat");
    assertThat(result.get("datumCode")).isEqualTo("wgs84");
    assertThat(result.get("ellps")).isEqualTo("WGS 84");
    assertThat(result.get("a")).isEqualTo(6378137.0);
    assertThat(result.get("rf")).isEqualTo(298.257223563);
  }

  @Test
  public void testProcessProjectedWKT() throws WKTParseException {
    String wkt =
        "PROJCS[\"WGS 84 / UTM zone 32N\",GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\",SPHEROID[\"WGS 84\",6378137,298.257223563]],PRIMEM[\"Greenwich\",0],UNIT[\"degree\",0.0174532925199433]],PROJECTION[\"Transverse_Mercator\"],PARAMETER[\"latitude_of_origin\",0],PARAMETER[\"central_meridian\",9],PARAMETER[\"scale_factor\",0.9996],PARAMETER[\"false_easting\",500000],PARAMETER[\"false_northing\",0],UNIT[\"metre\",1]]";

    Map<String, Object> result = WKTProcessor.process(wkt);

    assertThat(result).isNotNull();
    assertThat(result.get("projName")).isEqualTo("utm");
    assertThat(result.get("datumCode")).isEqualTo("wgs84");
    assertThat(result.get("lat0")).isEqualTo(0.0); // latitude_of_origin converted to radians
    assertThat(result.get("long0"))
        .isEqualTo(0.15707963267948966); // central_meridian converted to radians (9 degrees)
    assertThat(result.get("k0")).isEqualTo(0.9996);
    assertThat(result.get("x0")).isEqualTo(500000.0);
    assertThat(result.get("y0")).isEqualTo(0.0);
  }

  @Test
  public void testProcessWithAuthority() throws WKTParseException {
    String wkt =
        "GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\",SPHEROID[\"WGS 84\",6378137,298.257223563]],PRIMEM[\"Greenwich\",0],UNIT[\"degree\",0.0174532925199433],AUTHORITY[\"EPSG\",\"4326\"]]";

    Map<String, Object> result = WKTProcessor.process(wkt);

    assertThat(result).isNotNull();
    assertThat(result.get("title")).isEqualTo("EPSG:4326");
  }

  @Test
  public void testProcessWithAxis() throws WKTParseException {
    String wkt =
        "GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\",SPHEROID[\"WGS 84\",6378137,298.257223563]],PRIMEM[\"Greenwich\",0],UNIT[\"degree\",0.0174532925199433],AXIS[\"Longitude\",EAST],AXIS[\"Latitude\",NORTH]]";

    Map<String, Object> result = WKTProcessor.process(wkt);

    assertThat(result).isNotNull();
    assertThat(result.get("axis")).isEqualTo("enu");
  }

  @Test
  public void testProcessWithTOWGS84() throws WKTParseException {
    String wkt =
        "GEOGCS[\"NAD83\",DATUM[\"North_American_Datum_1983\",SPHEROID[\"GRS 1980\",6378137,298.257222101],TOWGS84[0,0,0,0,0,0,0]],PRIMEM[\"Greenwich\",0],UNIT[\"degree\",0.0174532925199433]]";

    Map<String, Object> result = WKTProcessor.process(wkt);

    assertThat(result).isNotNull();
    assertThat(result.get("datumCode")).isEqualTo("north_american_datum_1983");
    assertThat(result.get("datum_params")).isNotNull();
  }

  @Test
  public void testProcessInvalidInput() {
    assertThatThrownBy(() -> WKTProcessor.process(123))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void testProcessNullInput() {
    assertThatThrownBy(() -> WKTProcessor.process(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void testProcessWKT2GeographicCRS() throws WKTParseException {
    String wkt =
        "GEOGCRS[\"WGS 84\","
            + "DATUM[\"World Geodetic System 1984\","
            + "ELLIPSOID[\"WGS 84\",6378137,298.257223563,"
            + "LENGTHUNIT[\"metre\",1]]],"
            + "PRIMEM[\"Greenwich\",0,"
            + "ANGLEUNIT[\"degree\",0.0174532925199433]],"
            + "CS[ellipsoidal,2],"
            + "AXIS[\"geodetic latitude (Lat)\",north,"
            + "ORDER[1],"
            + "ANGLEUNIT[\"degree\",0.0174532925199433]],"
            + "AXIS[\"geodetic longitude (Lon)\",east,"
            + "ORDER[2],"
            + "ANGLEUNIT[\"degree\",0.0174532925199433]],"
            + "ID[\"EPSG\",4326]]";

    Map<String, Object> result = WKTProcessor.process(wkt);

    assertThat(result).isNotNull();
    assertThat(result.get("projName")).isEqualTo("longlat");
    assertThat(result.get("name")).isEqualTo("WGS 84");
    assertThat(result.get("ellps")).isEqualTo("WGS84"); // Normalized
    assertThat(result.get("a")).isEqualTo(6378137.0);
    assertThat(result.get("rf")).isEqualTo(298.257223563);
    assertThat(result.get("title")).isEqualTo("EPSG:4326");
  }

  @Test
  public void testProcessWKT2ProjectedCRSUTM() throws WKTParseException {
    String wkt =
        "PROJCRS[\"WGS 84 / UTM zone 19N\","
            + "BASEGEOGCRS[\"WGS 84\","
            + "DATUM[\"World Geodetic System 1984\","
            + "ELLIPSOID[\"WGS 84\",6378137,298.257223563,"
            + "LENGTHUNIT[\"metre\",1]]],"
            + "PRIMEM[\"Greenwich\",0,"
            + "ANGLEUNIT[\"degree\",0.0174532925199433]]],"
            + "CONVERSION[\"UTM zone 19N\","
            + "METHOD[\"Transverse Mercator\"],"
            + "PARAMETER[\"Latitude of natural origin\",0,"
            + "ANGLEUNIT[\"degree\",0.0174532925199433]],"
            + "PARAMETER[\"Longitude of natural origin\",-69,"
            + "ANGLEUNIT[\"degree\",0.0174532925199433]],"
            + "PARAMETER[\"Scale factor at natural origin\",0.9996,"
            + "SCALEUNIT[\"unity\",1]],"
            + "PARAMETER[\"False easting\",500000,"
            + "LENGTHUNIT[\"metre\",1]],"
            + "PARAMETER[\"False northing\",0,"
            + "LENGTHUNIT[\"metre\",1]]],"
            + "CS[Cartesian,2],"
            + "AXIS[\"(E)\",east,ORDER[1],LENGTHUNIT[\"metre\",1]],"
            + "AXIS[\"(N)\",north,ORDER[2],LENGTHUNIT[\"metre\",1]],"
            + "ID[\"EPSG\",32619]]";

    Map<String, Object> result = WKTProcessor.process(wkt);

    assertThat(result).isNotNull();
    assertThat(result.get("projName")).isEqualTo("tmerc");
    assertThat(result.get("name")).isEqualTo("WGS 84 / UTM zone 19N");
    assertThat(result.get("ellps")).isEqualTo("WGS84");
    assertThat(result.get("a")).isEqualTo(6378137.0);
    assertThat(result.get("rf")).isEqualTo(298.257223563);
    assertThat(result.get("k0")).isEqualTo(0.9996);
    assertThat(result.get("lat0")).isEqualTo(0.0);
    assertThat((Double) result.get("long0"))
        .isCloseTo(-1.2042771838760877, within(1e-10)); // -69 degrees in radians
    assertThat(result.get("x0")).isEqualTo(500000.0);
    assertThat(result.get("y0")).isEqualTo(0.0);
    assertThat(result.get("units")).isEqualTo("meter");
    assertThat(result.get("title")).isEqualTo("EPSG:32619");
  }

  @Test
  public void testProcessWKT2WebMercator() throws WKTParseException {
    String wkt =
        "PROJCRS[\"WGS 84 / Pseudo-Mercator\","
            + "BASEGEOGCRS[\"WGS 84\","
            + "DATUM[\"World Geodetic System 1984\","
            + "ELLIPSOID[\"WGS 84\",6378137,298.257223563,"
            + "LENGTHUNIT[\"metre\",1]]],"
            + "PRIMEM[\"Greenwich\",0,"
            + "ANGLEUNIT[\"degree\",0.0174532925199433]]],"
            + "CONVERSION[\"Popular Visualisation Pseudo-Mercator\","
            + "METHOD[\"Popular Visualisation Pseudo Mercator\"],"
            + "PARAMETER[\"Latitude of natural origin\",0,"
            + "ANGLEUNIT[\"degree\",0.0174532925199433]],"
            + "PARAMETER[\"Longitude of natural origin\",0,"
            + "ANGLEUNIT[\"degree\",0.0174532925199433]],"
            + "PARAMETER[\"False easting\",0,LENGTHUNIT[\"metre\",1]],"
            + "PARAMETER[\"False northing\",0,LENGTHUNIT[\"metre\",1]]],"
            + "CS[Cartesian,2],"
            + "AXIS[\"(E)\",east,ORDER[1],LENGTHUNIT[\"metre\",1]],"
            + "AXIS[\"(N)\",north,ORDER[2],LENGTHUNIT[\"metre\",1]],"
            + "ID[\"EPSG\",3857]]";

    Map<String, Object> result = WKTProcessor.process(wkt);

    assertThat(result).isNotNull();
    assertThat(result.get("projName")).isEqualTo("merc");
    assertThat(result.get("name")).isEqualTo("WGS 84 / Pseudo-Mercator");
    assertThat(result.get("ellps")).isEqualTo("WGS84");
    assertThat(result.get("a")).isEqualTo(6378137.0);
    assertThat(result.get("rf")).isEqualTo(298.257223563);
    assertThat(result.get("title")).isEqualTo("EPSG:3857");
  }
}
