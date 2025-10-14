package org.apache.sedona.proj.datum;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;

import org.apache.sedona.proj.Proj4Sedona;
import org.apache.sedona.proj.core.Point;

/**
 * Comprehensive test class for ProjCdnClient functionality.
 */
public class ProjCdnClientTest {
    
    @BeforeEach
    void setUp() {
        // Clear cache before each test
        ProjCdnClient.clearCache();
    }
    
    @AfterEach
    void tearDown() {
        // Clean up after each test
        ProjCdnClient.clearCache();
    }
    
    @Nested
    @DisplayName("URL Construction Tests")
    class UrlConstructionTests {
        
        @Test
        @DisplayName("Should construct CDN URLs correctly")
        void testCdnUrlConstruction() {
            String url = ProjCdnClient.getCdnUrl("test_grid.tif");
            assertThat(url).isEqualTo("https://cdn.proj.org/test_grid.tif");
            
            String url2 = ProjCdnClient.getCdnUrl("ca_nrc_NA83SCRS.tif");
            assertThat(url2).isEqualTo("https://cdn.proj.org/ca_nrc_NA83SCRS.tif");
            
            String url3 = ProjCdnClient.getCdnUrl("us_noaa_NAD83_NAD83_CSRS.tif");
            assertThat(url3).isEqualTo("https://cdn.proj.org/us_noaa_NAD83_NAD83_CSRS.tif");
        }
        
        @Test
        @DisplayName("Should handle special characters in grid names")
        void testSpecialCharactersInGridNames() {
            String url = ProjCdnClient.getCdnUrl("grid_with_underscores.tif");
            assertThat(url).isEqualTo("https://cdn.proj.org/grid_with_underscores.tif");
            
            String url2 = ProjCdnClient.getCdnUrl("grid-with-dashes.tif");
            assertThat(url2).isEqualTo("https://cdn.proj.org/grid-with-dashes.tif");
        }
        
        @Test
        @DisplayName("Should handle empty and null grid names")
        void testEmptyAndNullGridNames() {
            // Empty string should throw exception
            assertThatThrownBy(() -> ProjCdnClient.getCdnUrl(""))
                .isInstanceOf(IllegalArgumentException.class);
            
            // Null string should throw exception
            assertThatThrownBy(() -> ProjCdnClient.getCdnUrl(null))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
    
    @Nested
    @DisplayName("Cache Management Tests")
    class CacheManagementTests {
        
        @Test
        @DisplayName("Should handle empty cache correctly")
        void testEmptyCache() {
            assertThat(ProjCdnClient.getCacheSize()).isZero();
            assertThat(ProjCdnClient.getCachedGridKeys()).isEmpty();
            assertThat(ProjCdnClient.isGridCached("test")).isFalse();
        }
        
        @Test
        @DisplayName("Should clear cache correctly")
        void testClearCache() {
            // Clear empty cache
            ProjCdnClient.clearCache();
            assertThat(ProjCdnClient.getCacheSize()).isZero();
            
            // Clear cache multiple times
            ProjCdnClient.clearCache();
            ProjCdnClient.clearCache();
            assertThat(ProjCdnClient.getCacheSize()).isZero();
        }
        
        @Test
        @DisplayName("Should handle cache removal correctly")
        void testCacheRemoval() {
            // Test removing non-existent grid
            GeoTiffReader.GeoTiffGrid removed = ProjCdnClient.removeFromCache("non_existent");
            assertThat(removed).isNull();
            
            // Test removing null key
            GeoTiffReader.GeoTiffGrid removed2 = ProjCdnClient.removeFromCache(null);
            assertThat(removed2).isNull();
            
            // Test removing empty key
            GeoTiffReader.GeoTiffGrid removed3 = ProjCdnClient.removeFromCache("");
            assertThat(removed3).isNull();
        }
        
        @Test
        @DisplayName("Should handle cache size correctly")
        void testCacheSize() {
            // Initial size should be zero
            assertThat(ProjCdnClient.getCacheSize()).isZero();
            
            // Size should remain zero after operations on empty cache
            ProjCdnClient.removeFromCache("test");
            assertThat(ProjCdnClient.getCacheSize()).isZero();
        }
    }
    
    @Nested
    @DisplayName("Grid Download Tests")
    class GridDownloadTests {
        
        @Test
        @DisplayName("Should handle invalid URLs gracefully")
        void testInvalidUrls() {
            // Test with invalid URL
            assertThatThrownBy(() -> ProjCdnClient.downloadGridFromUrl("test", "invalid-url"))
                .isInstanceOf(IOException.class);
            
            // Test with null URL
            assertThatThrownBy(() -> ProjCdnClient.downloadGridFromUrl("test", null))
                .isInstanceOf(IllegalArgumentException.class);
        }
        
        @Test
        @DisplayName("Should handle network errors gracefully")
        void testNetworkErrors() {
            // Test with unreachable URL
            assertThatThrownBy(() -> ProjCdnClient.downloadGridFromUrl("test", "http://unreachable.example.com/grid.tif"))
                .isInstanceOf(IOException.class);
        }
        
        @Test
        @DisplayName("Should handle non-existent grid files")
        void testNonExistentGridFiles() {
            // Test with non-existent grid file
            assertThatThrownBy(() -> ProjCdnClient.downloadGrid("non_existent_grid.tif"))
                .isInstanceOf(IOException.class);
        }
    }
    
    @Nested
    @DisplayName("Stream Download Tests")
    class StreamDownloadTests {
        
        @Test
        @DisplayName("Should handle stream download correctly")
        void testStreamDownload() {
            // Test with invalid grid name
            assertThatThrownBy(() -> ProjCdnClient.downloadGridStream("non_existent_grid.tif"))
                .isInstanceOf(IOException.class);
        }
        
        @Test
        @DisplayName("Should handle null grid name in stream download")
        void testNullGridNameInStreamDownload() {
            assertThatThrownBy(() -> ProjCdnClient.downloadGridStream(null))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
    
    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {
        
        @Test
        @DisplayName("Should work with Proj4Sedona main API")
        void testProj4SedonaIntegration() {
            // Test cache operations through Proj4Sedona API
            assertThat(Proj4Sedona.getGridCacheSize()).isZero();
            assertThat(Proj4Sedona.getCachedGridKeys()).isEmpty();
            assertThat(Proj4Sedona.isGridCached("test")).isFalse();
            
            // Test cache clearing through Proj4Sedona API
            Proj4Sedona.clearGridCache();
            assertThat(Proj4Sedona.getGridCacheSize()).isZero();
        }
        
        @Test
        @DisplayName("Should handle download operations through Proj4Sedona API")
        void testProj4SedonaDownloadIntegration() {
            // Test download operations (these will fail with network errors, but should be handled gracefully)
            assertThatThrownBy(() -> Proj4Sedona.downloadGrid("non_existent_grid.tif"))
                .isInstanceOf(IOException.class);
            
            assertThatThrownBy(() -> Proj4Sedona.downloadGrid("test_key", "non_existent_grid.tif"))
                .isInstanceOf(IOException.class);
            
            assertThatThrownBy(() -> Proj4Sedona.downloadGridFromUrl("test_key", "invalid-url"))
                .isInstanceOf(IOException.class);
        }
    }
    
    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {
        
        @Test
        @DisplayName("Should handle null parameters gracefully")
        void testNullParameters() {
            // Test null grid name
            assertThatThrownBy(() -> ProjCdnClient.downloadGrid(null))
                .isInstanceOf(IllegalArgumentException.class);
            
            assertThatThrownBy(() -> ProjCdnClient.downloadGrid(null, "grid.tif"))
                .isInstanceOf(IllegalArgumentException.class);
            
            assertThatThrownBy(() -> ProjCdnClient.downloadGrid("key", null))
                .isInstanceOf(IllegalArgumentException.class);
            
            assertThatThrownBy(() -> ProjCdnClient.downloadGridFromUrl(null, "url"))
                .isInstanceOf(IllegalArgumentException.class);
            
            assertThatThrownBy(() -> ProjCdnClient.downloadGridFromUrl("key", null))
                .isInstanceOf(IllegalArgumentException.class);
        }
        
        @Test
        @DisplayName("Should handle empty parameters gracefully")
        void testEmptyParameters() {
            // Test empty grid name
            assertThatThrownBy(() -> ProjCdnClient.downloadGrid(""))
                .isInstanceOf(IllegalArgumentException.class);
            
            assertThatThrownBy(() -> ProjCdnClient.downloadGrid("", "grid.tif"))
                .isInstanceOf(IllegalArgumentException.class);
            
            assertThatThrownBy(() -> ProjCdnClient.downloadGrid("key", ""))
                .isInstanceOf(IllegalArgumentException.class);
        }
        
        @Test
        @DisplayName("Should handle malformed URLs gracefully")
        void testMalformedUrls() {
            // Test malformed URLs
            assertThatThrownBy(() -> ProjCdnClient.downloadGridFromUrl("test", "not-a-url"))
                .isInstanceOf(IOException.class);
            
            assertThatThrownBy(() -> ProjCdnClient.downloadGridFromUrl("test", "ftp://invalid"))
                .isInstanceOf(IOException.class);
        }
    }
    
    @Nested
    @DisplayName("Concurrent Access Tests")
    class ConcurrentAccessTests {
        
        @Test
        @DisplayName("Should handle concurrent cache access safely")
        void testConcurrentCacheAccess() throws InterruptedException {
            // Test concurrent cache operations
            Thread[] threads = new Thread[10];
            
            for (int i = 0; i < threads.length; i++) {
                final int threadId = i;
                threads[i] = new Thread(() -> {
                    // Each thread performs cache operations
                    for (int j = 0; j < 100; j++) {
                        ProjCdnClient.getCacheSize();
                        ProjCdnClient.getCachedGridKeys();
                        ProjCdnClient.isGridCached("test_" + threadId + "_" + j);
                        ProjCdnClient.removeFromCache("test_" + threadId + "_" + j);
                    }
                });
            }
            
            // Start all threads
            for (Thread thread : threads) {
                thread.start();
            }
            
            // Wait for all threads to complete
            for (Thread thread : threads) {
                thread.join();
            }
            
            // Cache should still be in a valid state
            assertThat(ProjCdnClient.getCacheSize()).isZero();
        }
        
        @Test
        @DisplayName("Should handle concurrent cache clearing safely")
        void testConcurrentCacheClearing() throws InterruptedException {
            // Test concurrent cache clearing
            Thread[] threads = new Thread[5];
            
            for (int i = 0; i < threads.length; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < 50; j++) {
                        ProjCdnClient.clearCache();
                        ProjCdnClient.getCacheSize();
                    }
                });
            }
            
            // Start all threads
            for (Thread thread : threads) {
                thread.start();
            }
            
            // Wait for all threads to complete
            for (Thread thread : threads) {
                thread.join();
            }
            
            // Cache should be cleared
            assertThat(ProjCdnClient.getCacheSize()).isZero();
        }
    }
    
    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {
        
        @Test
        @DisplayName("Should handle many cache operations efficiently")
        void testManyCacheOperations() {
            long startTime = System.currentTimeMillis();
            
            // Perform many cache operations
            for (int i = 0; i < 10000; i++) {
                ProjCdnClient.getCacheSize();
                ProjCdnClient.getCachedGridKeys();
                ProjCdnClient.isGridCached("test_" + i);
                ProjCdnClient.removeFromCache("test_" + i);
            }
            
            long endTime = System.currentTimeMillis();
            
            // Should complete within reasonable time
            assertThat(endTime - startTime).isLessThan(1000); // 1 second
        }
        
        @Test
        @DisplayName("Should handle repeated cache clearing efficiently")
        void testRepeatedCacheClearing() {
            long startTime = System.currentTimeMillis();
            
            // Perform many cache clearing operations
            for (int i = 0; i < 1000; i++) {
                ProjCdnClient.clearCache();
            }
            
            long endTime = System.currentTimeMillis();
            
            // Should complete within reasonable time
            assertThat(endTime - startTime).isLessThan(1000); // 1 second
        }
    }
    
    @Nested
    @DisplayName("Example-Based Integration Tests")
    class ExampleBasedIntegrationTests {
        
        @Test
        @DisplayName("Should demonstrate local file loading like in example")
        void testLocalFileLoadingExample() throws NoSuchMethodException {
            // Example 2: Demonstrate how to load a grid from a local file
            // This test verifies the API structure for local file loading
            
            // The example shows this pattern:
            // try (FileInputStream fis = new FileInputStream("path/to/grid.tif")) {
            //     GeoTiffReader.GeoTiffGrid grid = Proj4Sedona.nadgrid("my_grid", fis);
            //     Proj4Sedona.registerNadgrid("my_grid", grid);
            // }
            
            // We can't test actual file loading without real GeoTIFF files,
            // but we can verify the API methods exist and work with mock data
            assertThat(Proj4Sedona.class.getMethod("nadgrid", String.class, InputStream.class)).isNotNull();
            assertThat(Proj4Sedona.class.getMethod("registerNadgrid", String.class, GeoTiffReader.GeoTiffGrid.class)).isNotNull();
        }
        
        @Test
        @DisplayName("Should demonstrate CDN download like in example")
        void testCdnDownloadExample() throws NoSuchMethodException {
            // Example 2: Demonstrate downloading from PROJ CDN
            // The example shows: GeoTiffReader.GeoTiffGrid grid = Proj4Sedona.downloadGrid("ca_nrc_NA83SCRS.tif");
            
            // We can't test actual downloads without network access,
            // but we can verify the API methods exist
            assertThat(Proj4Sedona.class.getMethod("downloadGrid", String.class)).isNotNull();
            assertThat(Proj4Sedona.class.getMethod("downloadGridFromUrl", String.class, String.class)).isNotNull();
        }
        
        @Test
        @DisplayName("Should demonstrate coordinate transformation with grids like in example")
        void testGridTransformationExample() throws NoSuchMethodException {
            // Example 3: Demonstrate using grids in coordinate transformations
            // The example shows creating a point and transforming it
            
            // Create a point in Toronto (like in the example)
            Point toronto = new Point(-79.3832, 43.6532); // Longitude, Latitude in degrees
            
            // Verify the point was created correctly
            assertThat(toronto.x).isCloseTo(-79.3832, within(0.0001));
            assertThat(toronto.y).isCloseTo(43.6532, within(0.0001));
            
            // The example shows this pattern for transformations with grids:
            // String toProj = "+proj=longlat +datum=NAD83 +nadgrids=ca_nrc_NA83SCRS.tif";
            // Point transformed = Proj4Sedona.transform(fromProj, toProj, point);
            
            // We can verify the transform method exists
            assertThat(Proj4Sedona.class.getMethod("transform", String.class, Point.class)).isNotNull();
            assertThat(Proj4Sedona.class.getMethod("transform", String.class, String.class, Point.class)).isNotNull();
        }
        
        @Test
        @DisplayName("Should demonstrate complete workflow like in example")
        void testCompleteWorkflowExample() {
            // This test demonstrates the complete workflow from the example:
            // 1. Create/register a grid
            // 2. Use it in transformations
            // 3. Manage cache
            
            // Step 1: Create and register a mock grid (like Example 1)
            String gridName = "example_workflow_grid.tif";
            GeoTiffReader.GeoTiffSubgrid subgrid = new GeoTiffReader.GeoTiffSubgrid(
                -2.0, 2.0, -2.0, 2.0, 1.0, 1.0, 4, 4,
                new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0},
                new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0}
            );
            GeoTiffReader.GeoTiffGrid grid = new GeoTiffReader.GeoTiffGrid(
                gridName, new GeoTiffReader.GeoTiffSubgrid[]{subgrid}
            );
            
            Proj4Sedona.registerNadgrid(gridName, grid);
            assertThat(Proj4Sedona.hasNadgrid(gridName)).isTrue();
            
            // Step 2: Test interpolation (like in Example 1) - use a point clearly within the grid bounds
            double testLat = Math.toRadians(0.0);  // Center of grid
            double testLon = Math.toRadians(0.0);  // Center of grid
            double[] shifts = GeoTiffReader.interpolateGrid(grid, testLat, testLon);
            assertThat(shifts).isNotNull();
            assertThat(shifts).hasSize(2);
            
            // Step 3: Cache management (like Example 4)
            assertThat(Proj4Sedona.getNadgridCount()).isGreaterThan(0);
            assertThat(Proj4Sedona.getNadgridNames()).contains(gridName);
            
            // Step 4: Clean up
            Proj4Sedona.clearNadgrids();
            assertThat(Proj4Sedona.getNadgridCount()).isEqualTo(0);
        }
    }
}
