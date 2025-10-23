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

import org.apache.sedona.proj.Proj4Sedona;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Tests for EPSG code support in Projection class. */
public class EpsgCodeTest {

  // =====================================================================
  // Tests for Hardcoded EPSG Codes (fast, no network required)
  // =====================================================================

  @Test
  public void testEpsg4326_WGS84() {
    Projection proj = new Projection("EPSG:4326");

    assertThat(proj).isNotNull();
    assertThat(proj.projName).isEqualTo("longlat");
    assertThat(proj.datumCode).isEqualTo("WGS84");

    // Test transformation (should be identity for WGS84 to WGS84)
    Point input = new Point(-71.0, 41.0);
    Point result = Proj4Sedona.transform(proj, proj, input, false);
    assertThat(result.x).isCloseTo(-71.0, within(0.000001));
    assertThat(result.y).isCloseTo(41.0, within(0.000001));
  }

  @Test
  public void testEpsg3857_WebMercator() {
    Projection proj = new Projection("EPSG:3857");

    assertThat(proj).isNotNull();
    assertThat(proj.projName).isEqualTo("merc");

    // Test transformation from WGS84 to Web Mercator
    Projection wgs84 = new Projection("EPSG:4326");
    Point input = new Point(0.0, 0.0); // Equator at Prime Meridian
    Point result = Proj4Sedona.transform(wgs84, proj, input, false);

    assertThat(result.x).isCloseTo(0.0, within(0.01));
    assertThat(result.y).isCloseTo(0.0, within(0.01));
  }

  @Test
  public void testEpsg4269_NAD83() {
    Projection proj = new Projection("EPSG:4269");

    assertThat(proj).isNotNull();
    assertThat(proj.projName).isEqualTo("longlat");
    assertThat(proj.datumCode).isEqualTo("NAD83");
    assertThat(proj.ellps).isEqualTo("GRS80");

    // Verify ellipsoid parameters for GRS80
    assertThat(proj.a).isCloseTo(6378137.0, within(0.01));
    assertThat(proj.b).isCloseTo(6356752.31414036, within(0.01));
  }

  @Test
  public void testEpsg32619_UTMZone19N() {
    Projection proj = new Projection("EPSG:32619");

    assertThat(proj).isNotNull();
    assertThat(proj.projName).isEqualTo("utm");
    assertThat(proj.datumCode).isEqualTo("WGS84");
    assertThat(proj.zone).isEqualTo(19);
    assertThat(proj.utmSouth).isFalse();

    // Test transformation from WGS84 to UTM Zone 19N
    Projection wgs84 = new Projection("EPSG:4326");
    Point input = new Point(-71.0, 41.0); // Boston, MA
    Point result = Proj4Sedona.transform(wgs84, proj, input, false);

    // Expected UTM coordinates for Boston
    assertThat(result.x).isCloseTo(331792.11, within(1.0));
    assertThat(result.y).isCloseTo(4540683.53, within(1.0));
  }

  @Test
  public void testEpsg32620_UTMZone20N() {
    Projection proj = new Projection("EPSG:32620");

    assertThat(proj).isNotNull();
    assertThat(proj.projName).isEqualTo("utm");
    assertThat(proj.zone).isEqualTo(20);
    assertThat(proj.utmSouth).isFalse();
  }

  @Test
  public void testEpsg32701_UTMZone1S() {
    Projection proj = new Projection("EPSG:32701");

    assertThat(proj).isNotNull();
    assertThat(proj.projName).isEqualTo("utm");
    assertThat(proj.zone).isEqualTo(1);
    assertThat(proj.utmSouth).isTrue();
  }

  @Test
  @Tag("network")
  public void testEpsg32145_LambertConformalConic() {
    // EPSG:32145 - NAD83 / New York Long Island (Lambert Conformal Conic)
    // This is NOT hardcoded, so it will fetch from spatialreference.org

    try {
      Projection proj = new Projection("EPSG:32145");

      assertThat(proj).isNotNull();
      assertThat(proj.projName)
          .isNotNull(); // spatialreference.org may return different projection types

      System.out.println("Successfully fetched EPSG:32145 from spatialreference.org");
      System.out.println("  Projection type: " + proj.projName);
      System.out.println("  Datum: " + proj.datumCode);

      // Test that it can actually transform (the important part)
      Projection wgs84 = new Projection("EPSG:4326");
      Point input = new Point(-74.0, 40.7);
      Point result = Proj4Sedona.transform(wgs84, proj, input, false);
      assertThat(result).isNotNull();
      System.out.println(
          "  Transformation works: (-74.0, 40.7) -> (" + result.x + ", " + result.y + ")");

    } catch (IllegalArgumentException e) {
      // Network might be unavailable or site might be down
      if (e.getMessage().contains("Failed to fetch")) {
        System.err.println("⚠️  Network test skipped: " + e.getMessage());
      } else {
        throw e;
      }
    }
  }

  @Test
  public void testMultipleUTMZones() {
    // Test that we can create projections for different UTM zones
    for (int zone = 1; zone <= 60; zone++) {
      // Northern hemisphere
      Projection projN = new Projection("EPSG:326" + String.format("%02d", zone));
      assertThat(projN.projName).isEqualTo("utm");
      assertThat(projN.zone).isEqualTo(zone);
      assertThat(projN.utmSouth).isFalse();

      // Southern hemisphere
      Projection projS = new Projection("EPSG:327" + String.format("%02d", zone));
      assertThat(projS.projName).isEqualTo("utm");
      assertThat(projS.zone).isEqualTo(zone);
      assertThat(projS.utmSouth).isTrue();
    }
  }

  // =====================================================================
  // Tests for Dynamic EPSG Lookup (requires network)
  // =====================================================================

  @Test
  @Tag("network")
  public void testDynamicEpsgLookup_EPSG2154_RGF93() {
    // EPSG:2154 - RGF93 / Lambert-93 (France)
    // This is NOT hardcoded, so it will fetch from spatialreference.org

    try {
      Projection proj = new Projection("EPSG:2154");

      assertThat(proj).isNotNull();
      assertThat(proj.projName).isNotNull();

      System.out.println("Successfully fetched EPSG:2154 from spatialreference.org");
      System.out.println("  Projection type: " + proj.projName);
      System.out.println("  Datum: " + proj.datumCode);

    } catch (IllegalArgumentException e) {
      // Network might be unavailable or site might be down
      if (e.getMessage().contains("Failed to fetch")) {
        System.err.println("⚠️  Network test skipped: " + e.getMessage());
        // Don't fail the test if network is unavailable
      } else {
        throw e;
      }
    }
  }

  @Test
  @Tag("network")
  public void testEpsgCaching() {
    // Test that EPSG codes are cached to avoid redundant network calls

    try {
      // Clear cache before test
      org.apache.sedona.proj.cache.EpsgDefinitionCache.clearCache();
      int initialSize = org.apache.sedona.proj.cache.EpsgDefinitionCache.getCacheSize();
      assertThat(initialSize).isEqualTo(0);

      // First fetch - should hit the network
      Projection proj1 = new Projection("EPSG:2154");
      assertThat(proj1).isNotNull();

      // Cache should now contain one entry
      assertThat(org.apache.sedona.proj.cache.EpsgDefinitionCache.getCacheSize()).isEqualTo(1);

      // Second fetch - should use cache (no network call)
      long startTime = System.nanoTime();
      Projection proj2 = new Projection("EPSG:2154");
      long duration = System.nanoTime() - startTime;

      assertThat(proj2).isNotNull();
      assertThat(proj2.projName).isEqualTo(proj1.projName);

      // Cache should still contain one entry
      assertThat(org.apache.sedona.proj.cache.EpsgDefinitionCache.getCacheSize()).isEqualTo(1);

      // Cached fetch should be very fast (< 10ms)
      assertThat(duration).isLessThan(10_000_000); // 10ms in nanoseconds

      System.out.println(
          "✅ EPSG caching works! Second fetch took "
              + (duration / 1_000_000.0)
              + "ms (from cache)");

    } catch (IllegalArgumentException e) {
      // Network might be unavailable
      if (e.getMessage().contains("Failed to fetch")) {
        System.err.println("⚠️  Network test skipped: " + e.getMessage());
      } else {
        throw e;
      }
    } finally {
      // Clean up
      org.apache.sedona.proj.cache.EpsgDefinitionCache.clearCache();
    }
  }

  @Test
  @Tag("network")
  public void testDynamicEpsgLookup_EPSG27700_OSGB36() {
    // EPSG:27700 - OSGB 1936 / British National Grid
    // This is NOT hardcoded, so it will fetch from spatialreference.org

    try {
      Projection proj = new Projection("EPSG:27700");

      assertThat(proj).isNotNull();
      assertThat(proj.projName).isNotNull();

      System.out.println("Successfully fetched EPSG:27700 from spatialreference.org");
      System.out.println("  Projection type: " + proj.projName);
      System.out.println("  Datum: " + proj.datumCode);

    } catch (IllegalArgumentException e) {
      // Network might be unavailable or site might be down
      if (e.getMessage().contains("Failed to fetch")) {
        System.err.println("⚠️  Network test skipped: " + e.getMessage());
        // Don't fail the test if network is unavailable
      } else {
        throw e;
      }
    }
  }

  @Test
  @Tag("network")
  public void testDynamicEpsgLookup_InvalidCode() {
    // Test that invalid EPSG codes are handled gracefully

    try {
      Projection proj = new Projection("EPSG:99999999");
      fail("Should have thrown IllegalArgumentException for invalid EPSG code");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage()).contains("EPSG");
      System.out.println("Correctly rejected invalid EPSG:99999999");
    }
  }

  // =====================================================================
  // Integration Tests
  // =====================================================================

  @Test
  public void testTransformationWithHardcodedEPSG() {
    // Test actual coordinate transformation using hardcoded EPSG codes
    Projection wgs84 = new Projection("EPSG:4326");
    Projection webMercator = new Projection("EPSG:3857");

    // Transform Boston coordinates from WGS84 to Web Mercator
    Point boston = new Point(-71.0, 41.0);
    Point bostonMercator = Proj4Sedona.transform(wgs84, webMercator, boston, false);

    assertThat(bostonMercator.x).isCloseTo(-7903683.846, within(1.0));
    assertThat(bostonMercator.y).isCloseTo(5012341.664, within(1.0));

    // Transform back
    Point bostonBack = Proj4Sedona.transform(webMercator, wgs84, bostonMercator, false);
    assertThat(bostonBack.x).isCloseTo(-71.0, within(0.000001));
    assertThat(bostonBack.y).isCloseTo(41.0, within(0.000001));
  }

  @Test
  public void testTransformationNAD83ToWGS84() {
    // Test datum transformation between NAD83 and WGS84
    Projection nad83 = new Projection("EPSG:4269");
    Projection wgs84 = new Projection("EPSG:4326");

    // For North America, NAD83 and WGS84 are very close (within meters)
    Point pointNAD83 = new Point(-71.0, 41.0);
    Point pointWGS84 = Proj4Sedona.transform(nad83, wgs84, pointNAD83, false);

    // Should be very close (NAD83 ≈ WGS84 in North America)
    assertThat(pointWGS84.x).isCloseTo(-71.0, within(0.001));
    assertThat(pointWGS84.y).isCloseTo(41.0, within(0.001));
  }

  @Test
  public void testTransformationUTMRoundTrip() {
    // Test round-trip transformation: WGS84 -> UTM -> WGS84
    Projection wgs84 = new Projection("EPSG:4326");
    Projection utm19n = new Projection("EPSG:32619");

    Point original = new Point(-71.0, 41.0);
    Point utm = Proj4Sedona.transform(wgs84, utm19n, original, false);
    Point back = Proj4Sedona.transform(utm19n, wgs84, utm, false);

    // Should get back to original coordinates (within tolerance)
    assertThat(back.x).isCloseTo(original.x, within(0.000001));
    assertThat(back.y).isCloseTo(original.y, within(0.000001));
  }

  // =====================================================================
  // Error Handling Tests
  // =====================================================================

  @Test
  @Tag("network")
  public void testInvalidEpsgCodeFormat() {
    // Invalid EPSG code will try to fetch from spatialreference.org and fail with 404
    assertThatThrownBy(() -> new Projection("EPSG:ABC"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Failed to fetch");
  }

  @Test
  public void testUnsupportedSrsCode() {
    assertThatThrownBy(() -> new Projection("UNKNOWN:1234"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported SRS code");
  }

  @Test
  public void testNullSrsCode() {
    assertThatThrownBy(() -> new Projection(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot be null");
  }

  @Test
  public void testEmptySrsCode() {
    assertThatThrownBy(() -> new Projection(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot be null or empty");
  }
}
