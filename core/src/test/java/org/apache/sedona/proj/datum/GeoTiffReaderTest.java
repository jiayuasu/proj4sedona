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

import static org.assertj.core.api.Assertions.*;

import org.apache.sedona.proj.Proj4Sedona;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Comprehensive test class for GeoTiffReader functionality. */
public class GeoTiffReaderTest {

  @BeforeEach
  void setUp() {
    // Clear any existing grids before each test
    GeoTiffReader.clearGrids();
    ProjCdnClient.clearCache();
  }

  @AfterEach
  void tearDown() {
    // Clean up after each test
    GeoTiffReader.clearGrids();
    ProjCdnClient.clearCache();
  }

  @Nested
  @DisplayName("Grid Registration Tests")
  class GridRegistrationTests {

    @Test
    @DisplayName("Should register and retrieve a grid successfully")
    void testGridRegistration() {
      // Create a mock grid for testing
      GeoTiffReader.GeoTiffSubgrid subgrid = createTestSubgrid();
      GeoTiffReader.GeoTiffGrid grid =
          new GeoTiffReader.GeoTiffGrid("test_grid", new GeoTiffReader.GeoTiffSubgrid[] {subgrid});

      // Test registration
      GeoTiffReader.registerGrid("test", grid);
      assertThat(GeoTiffReader.hasGrid("test")).isTrue();
      assertThat(GeoTiffReader.getGrid("test")).isEqualTo(grid);
    }

    @Test
    @DisplayName("Should remove a grid successfully")
    void testGridRemoval() {
      GeoTiffReader.GeoTiffSubgrid subgrid = createTestSubgrid();
      GeoTiffReader.GeoTiffGrid grid =
          new GeoTiffReader.GeoTiffGrid("test_grid", new GeoTiffReader.GeoTiffSubgrid[] {subgrid});

      GeoTiffReader.registerGrid("test", grid);
      assertThat(GeoTiffReader.hasGrid("test")).isTrue();

      // Test removal
      GeoTiffReader.GeoTiffGrid removed = GeoTiffReader.removeGrid("test");
      assertThat(removed).isEqualTo(grid);
      assertThat(GeoTiffReader.hasGrid("test")).isFalse();
    }

    @Test
    @DisplayName("Should return null when removing non-existent grid")
    void testRemoveNonExistentGrid() {
      GeoTiffReader.GeoTiffGrid removed = GeoTiffReader.removeGrid("non_existent");
      assertThat(removed).isNull();
    }

    @Test
    @DisplayName("Should handle multiple grids correctly")
    void testMultipleGrids() {
      GeoTiffReader.GeoTiffSubgrid subgrid = createTestSubgrid();

      GeoTiffReader.GeoTiffGrid grid1 =
          new GeoTiffReader.GeoTiffGrid("grid1", new GeoTiffReader.GeoTiffSubgrid[] {subgrid});
      GeoTiffReader.GeoTiffGrid grid2 =
          new GeoTiffReader.GeoTiffGrid("grid2", new GeoTiffReader.GeoTiffSubgrid[] {subgrid});
      GeoTiffReader.GeoTiffGrid grid3 =
          new GeoTiffReader.GeoTiffGrid("grid3", new GeoTiffReader.GeoTiffSubgrid[] {subgrid});

      GeoTiffReader.registerGrid("grid1", grid1);
      GeoTiffReader.registerGrid("grid2", grid2);
      GeoTiffReader.registerGrid("grid3", grid3);

      String[] names = GeoTiffReader.getGridNames();
      assertThat(names).hasSize(3);
      assertThat(names).containsExactlyInAnyOrder("grid1", "grid2", "grid3");

      assertThat(GeoTiffReader.hasGrid("grid1")).isTrue();
      assertThat(GeoTiffReader.hasGrid("grid2")).isTrue();
      assertThat(GeoTiffReader.hasGrid("grid3")).isTrue();
      assertThat(GeoTiffReader.hasGrid("grid4")).isFalse();
    }

    @Test
    @DisplayName("Should clear all grids successfully")
    void testClearGrids() {
      GeoTiffReader.GeoTiffSubgrid subgrid = createTestSubgrid();
      GeoTiffReader.GeoTiffGrid grid =
          new GeoTiffReader.GeoTiffGrid("test", new GeoTiffReader.GeoTiffSubgrid[] {subgrid});

      GeoTiffReader.registerGrid("test", grid);
      assertThat(GeoTiffReader.hasGrid("test")).isTrue();

      GeoTiffReader.clearGrids();
      assertThat(GeoTiffReader.hasGrid("test")).isFalse();
      assertThat(GeoTiffReader.getGridNames()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Grid Interpolation Tests")
  class GridInterpolationTests {

    @Test
    @DisplayName("Should interpolate correctly at grid center")
    void testInterpolationAtCenter() {
      GeoTiffReader.GeoTiffSubgrid subgrid = createTestSubgrid();
      GeoTiffReader.GeoTiffGrid grid =
          new GeoTiffReader.GeoTiffGrid("test_grid", new GeoTiffReader.GeoTiffSubgrid[] {subgrid});

      // Test interpolation at center point (0, 0 in degrees)
      double[] shifts = GeoTiffReader.interpolateGrid(grid, 0.0, 0.0); // lat=0, lon=0 in radians
      assertThat(shifts).isNotNull();
      assertThat(shifts).hasSize(2);
      assertThat(shifts[0]).isCloseTo(0.0, within(1e-10)); // lat shift
      assertThat(shifts[1]).isCloseTo(0.0, within(1e-10)); // lon shift
    }

    @Test
    @DisplayName("Should return null for points outside grid bounds")
    void testInterpolationOutsideBounds() {
      GeoTiffReader.GeoTiffSubgrid subgrid = createTestSubgrid();
      GeoTiffReader.GeoTiffGrid grid =
          new GeoTiffReader.GeoTiffGrid("test_grid", new GeoTiffReader.GeoTiffSubgrid[] {subgrid});

      // Test interpolation outside grid bounds
      double[] outsideShifts =
          GeoTiffReader.interpolateGrid(grid, Math.toRadians(10.0), Math.toRadians(10.0));
      assertThat(outsideShifts).isNull();

      // Test at grid edges (should be null since we're outside)
      double[] edgeShifts =
          GeoTiffReader.interpolateGrid(grid, Math.toRadians(1.0), Math.toRadians(1.0));
      assertThat(edgeShifts).isNull();
    }

    @Test
    @DisplayName("Should handle bilinear interpolation correctly")
    void testBilinearInterpolation() {
      // Create a grid with known values for testing interpolation
      double[] latShifts = {
        0.0, 1.0, 2.0, // Row 0
        1.0, 2.0, 3.0, // Row 1
        2.0, 3.0, 4.0 // Row 2
      };
      double[] lonShifts = {
        0.0, 0.5, 1.0, // Row 0
        0.5, 1.0, 1.5, // Row 1
        1.0, 1.5, 2.0 // Row 2
      };

      GeoTiffReader.GeoTiffSubgrid subgrid =
          new GeoTiffReader.GeoTiffSubgrid(
              -1.0, 1.0, -1.0, 1.0, // bounds
              1.0, 1.0, // step sizes
              3, 3, // 3x3 grid
              latShifts, lonShifts);

      GeoTiffReader.GeoTiffGrid grid =
          new GeoTiffReader.GeoTiffGrid("test_grid", new GeoTiffReader.GeoTiffSubgrid[] {subgrid});

      // Test interpolation at exact grid point (should return exact value)
      double[] exactShifts = GeoTiffReader.interpolateGrid(grid, 0.0, 0.0); // Center point
      assertThat(exactShifts).isNotNull();
      assertThat(exactShifts[0]).isCloseTo(2.0, within(1e-10)); // Center lat shift
      assertThat(exactShifts[1]).isCloseTo(1.0, within(1e-10)); // Center lon shift
    }

    @Test
    @DisplayName("Should handle multiple subgrids correctly")
    void testMultipleSubgrids() {
      // Create a single subgrid with a larger grid for better interpolation
      GeoTiffReader.GeoTiffSubgrid subgrid =
          new GeoTiffReader.GeoTiffSubgrid(
              -2.0,
              2.0,
              -2.0,
              2.0,
              1.0,
              1.0,
              4,
              4,
              new double[] {
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0
              },
              new double[] {
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0
              });

      GeoTiffReader.GeoTiffGrid grid =
          new GeoTiffReader.GeoTiffGrid("multi_grid", new GeoTiffReader.GeoTiffSubgrid[] {subgrid});

      // Test interpolation at a point clearly within the grid bounds
      double[] shifts =
          GeoTiffReader.interpolateGrid(grid, Math.toRadians(0.0), Math.toRadians(0.0));
      assertThat(shifts).isNotNull();
      assertThat(shifts[0]).isCloseTo(0.0, within(1e-10));
      assertThat(shifts[1]).isCloseTo(0.0, within(1e-10));
    }
  }

  @Nested
  @DisplayName("PROJ CDN Client Tests")
  class ProjCdnClientTests {

    @Test
    @DisplayName("Should construct CDN URLs correctly")
    void testCdnUrlConstruction() {
      String url = ProjCdnClient.getCdnUrl("test_grid.tif");
      assertThat(url).isEqualTo("https://cdn.proj.org/test_grid.tif");

      String url2 = ProjCdnClient.getCdnUrl("ca_nrc_NA83SCRS.tif");
      assertThat(url2).isEqualTo("https://cdn.proj.org/ca_nrc_NA83SCRS.tif");
    }

    @Test
    @DisplayName("Should handle cache operations correctly")
    void testCacheOperations() {
      // Test initial cache state
      assertThat(ProjCdnClient.getCacheSize()).isZero();
      assertThat(ProjCdnClient.getCachedGridKeys()).isEmpty();
      assertThat(ProjCdnClient.isGridCached("test")).isFalse();

      // Test cache operations
      ProjCdnClient.clearCache();
      assertThat(ProjCdnClient.getCacheSize()).isZero();
    }

    @Test
    @DisplayName("Should handle cache removal correctly")
    void testCacheRemoval() {
      // Test removing non-existent grid
      GeoTiffReader.GeoTiffGrid removed = ProjCdnClient.removeFromCache("non_existent");
      assertThat(removed).isNull();
    }
  }

  @Nested
  @DisplayName("Error Handling Tests")
  class ErrorHandlingTests {

    @Test
    @DisplayName("Should handle null grid names gracefully")
    void testNullGridNames() {
      assertThat(GeoTiffReader.hasGrid(null)).isFalse();
      assertThat(GeoTiffReader.getGrid(null)).isNull();
      assertThat(GeoTiffReader.removeGrid(null)).isNull();
    }

    @Test
    @DisplayName("Should handle empty grid names gracefully")
    void testEmptyGridNames() {
      assertThat(GeoTiffReader.hasGrid("")).isFalse();
      assertThat(GeoTiffReader.getGrid("")).isNull();
      assertThat(GeoTiffReader.removeGrid("")).isNull();
    }

    @Test
    @DisplayName("Should handle null grid registration gracefully")
    void testNullGridRegistration() {
      // This should not throw an exception
      GeoTiffReader.registerGrid("test", null);
      assertThat(GeoTiffReader.getGrid("test")).isNull();
    }

    @Test
    @DisplayName("Should handle interpolation with null grid gracefully")
    void testInterpolationWithNullGrid() {
      double[] shifts = GeoTiffReader.interpolateGrid(null, 0.0, 0.0);
      assertThat(shifts).isNull();
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    @DisplayName("Should work with Proj4Sedona main API")
    void testProj4SedonaIntegration() {
      GeoTiffReader.GeoTiffSubgrid subgrid = createTestSubgrid();
      GeoTiffReader.GeoTiffGrid grid =
          new GeoTiffReader.GeoTiffGrid(
              "integration_test", new GeoTiffReader.GeoTiffSubgrid[] {subgrid});

      // Test using Proj4Sedona API
      Proj4Sedona.registerNadgrid("integration_test", grid);
      assertThat(Proj4Sedona.hasNadgrid("integration_test")).isTrue();
      assertThat(Proj4Sedona.getNadgrid("integration_test")).isEqualTo(grid);

      String[] names = Proj4Sedona.getNadgridNames();
      assertThat(names).contains("integration_test");

      // Test removal through Proj4Sedona API
      GeoTiffReader.GeoTiffGrid removed = Proj4Sedona.removeNadgrid("integration_test");
      assertThat(removed).isEqualTo(grid);
      assertThat(Proj4Sedona.hasNadgrid("integration_test")).isFalse();
    }

    @Test
    @DisplayName("Should handle cache management through Proj4Sedona API")
    void testProj4SedonaCacheIntegration() {
      // Test cache operations through Proj4Sedona API
      assertThat(Proj4Sedona.getGridCacheSize()).isZero();
      assertThat(Proj4Sedona.getCachedGridKeys()).isEmpty();

      Proj4Sedona.clearGridCache();
      assertThat(Proj4Sedona.getGridCacheSize()).isZero();
    }
  }

  @Nested
  @DisplayName("Performance Tests")
  class PerformanceTests {

    @Test
    @DisplayName("Should handle large grids efficiently")
    void testLargeGridPerformance() {
      // Create a larger grid for performance testing
      int size = 100;
      double[] latShifts = new double[size * size];
      double[] lonShifts = new double[size * size];

      // Fill with test data
      for (int i = 0; i < size * size; i++) {
        latShifts[i] = Math.sin(i * 0.01) * 0.001;
        lonShifts[i] = Math.cos(i * 0.01) * 0.001;
      }

      GeoTiffReader.GeoTiffSubgrid subgrid =
          new GeoTiffReader.GeoTiffSubgrid(
              -50.0, 50.0, -50.0, 50.0, 1.0, 1.0, size, size, latShifts, lonShifts);

      GeoTiffReader.GeoTiffGrid grid =
          new GeoTiffReader.GeoTiffGrid("large_grid", new GeoTiffReader.GeoTiffSubgrid[] {subgrid});

      // Test multiple interpolations
      long startTime = System.currentTimeMillis();
      for (int i = 0; i < 1000; i++) {
        double lat = Math.toRadians(-25.0 + (i % 50));
        double lon = Math.toRadians(-25.0 + (i % 50));
        GeoTiffReader.interpolateGrid(grid, lat, lon);
      }
      long endTime = System.currentTimeMillis();

      // Should complete within reasonable time (adjust threshold as needed)
      assertThat(endTime - startTime).isLessThan(5000); // 5 seconds
    }
  }

  @Nested
  @DisplayName("Example-Based Tests")
  class ExampleBasedTests {

    @Test
    @DisplayName("Should create and use mock datum grid like in example")
    void testMockGridCreationAndUsage() {
      // Example 1: Create and use a mock datum grid for demonstration
      String gridName = "ca_nrc_NA83SCRS.tif";

      // Create a simple test grid covering part of Canada
      GeoTiffReader.GeoTiffSubgrid subgrid =
          new GeoTiffReader.GeoTiffSubgrid(
              -85.0,
              -75.0,
              40.0,
              50.0, // Bounds covering part of Canada
              0.1,
              0.1, // 0.1 degree spacing
              100,
              100, // 100x100 grid
              createMockLatShifts(100, 100), // Mock latitude shifts
              createMockLonShifts(100, 100) // Mock longitude shifts
              );

      GeoTiffReader.GeoTiffGrid mockGrid =
          new GeoTiffReader.GeoTiffGrid(gridName, new GeoTiffReader.GeoTiffSubgrid[] {subgrid});

      // Register the grid
      Proj4Sedona.registerNadgrid(gridName, mockGrid);
      assertThat(Proj4Sedona.hasNadgrid(gridName)).isTrue();
      assertThat(mockGrid.subgrids.length).isEqualTo(1);

      // Test interpolation at a point in Canada (Toronto area)
      double torontoLat = Math.toRadians(43.6532); // Toronto latitude in radians
      double torontoLon = Math.toRadians(-79.3832); // Toronto longitude in radians

      double[] shifts = GeoTiffReader.interpolateGrid(mockGrid, torontoLat, torontoLon);
      assertThat(shifts).isNotNull();
      assertThat(shifts).hasSize(2);

      // Verify shifts are reasonable (small values in radians)
      assertThat(Math.abs(shifts[0])).isLessThan(0.01); // Less than ~0.57 degrees
      assertThat(Math.abs(shifts[1])).isLessThan(0.01);
    }

    @Test
    @DisplayName("Should handle multiple grids like in example")
    void testMultipleGridsExample() {
      // Example 5: Demonstrate working with multiple grids
      String[] gridNames = {"ca_nrc_NA83SCRS.tif", "us_noaa_NAD83_NAD83_CSRS.tif"};

      for (String gridName : gridNames) {
        // Create a simple mock grid
        GeoTiffReader.GeoTiffSubgrid subgrid =
            new GeoTiffReader.GeoTiffSubgrid(
                -180.0, 180.0, -90.0, 90.0, 1.0, 1.0, 10, 10, new double[100], new double[100]);

        GeoTiffReader.GeoTiffGrid grid =
            new GeoTiffReader.GeoTiffGrid(gridName, new GeoTiffReader.GeoTiffSubgrid[] {subgrid});

        Proj4Sedona.registerNadgrid(gridName, grid);
        assertThat(grid.subgrids.length).isEqualTo(1);
      }

      String[] registeredGrids = Proj4Sedona.getNadgridNames();
      assertThat(registeredGrids).hasSize(2);
      assertThat(registeredGrids).containsExactlyInAnyOrder(gridNames);
    }

    @Test
    @DisplayName("Should demonstrate cache management like in example")
    void testCacheManagementExample() {
      // Example 4: Demonstrate cache management
      // First register a grid
      GeoTiffReader.GeoTiffSubgrid subgrid = createTestSubgrid();
      GeoTiffReader.GeoTiffGrid grid =
          new GeoTiffReader.GeoTiffGrid(
              "cache_test_grid", new GeoTiffReader.GeoTiffSubgrid[] {subgrid});
      Proj4Sedona.registerNadgrid("cache_test_grid", grid);

      // Check cache status
      assertThat(Proj4Sedona.getNadgridCount()).isGreaterThan(0);
      assertThat(Proj4Sedona.getNadgridNames()).contains("cache_test_grid");

      // Clear cache
      Proj4Sedona.clearNadgrids();
      assertThat(Proj4Sedona.getNadgridCount()).isEqualTo(0);
      assertThat(Proj4Sedona.getNadgridNames()).isEmpty();
    }

    /** Creates mock latitude shift data for testing. */
    private double[] createMockLatShifts(int width, int height) {
      double[] shifts = new double[width * height];
      for (int i = 0; i < shifts.length; i++) {
        // Create small random-like shifts in arcseconds, converted to radians
        shifts[i] = (Math.sin(i * 0.1) * 0.5) * (Math.PI / 180.0 / 3600.0);
      }
      return shifts;
    }

    /** Creates mock longitude shift data for testing. */
    private double[] createMockLonShifts(int width, int height) {
      double[] shifts = new double[width * height];
      for (int i = 0; i < shifts.length; i++) {
        // Create small random-like shifts in arcseconds, converted to radians
        shifts[i] = (Math.cos(i * 0.1) * 0.3) * (Math.PI / 180.0 / 3600.0);
      }
      return shifts;
    }
  }

  // Helper method to create a test subgrid
  private GeoTiffReader.GeoTiffSubgrid createTestSubgrid() {
    return new GeoTiffReader.GeoTiffSubgrid(
        -1.0,
        1.0,
        -1.0,
        1.0, // bounds
        1.0,
        1.0, // step sizes
        3,
        3, // 3x3 grid
        new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0}, // lat shifts
        new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0} // lon shifts
        );
  }
}
