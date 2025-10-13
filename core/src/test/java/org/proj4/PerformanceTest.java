package org.proj4;

import org.proj4.core.Point;
import org.proj4.optimization.BatchTransformer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Performance tests to demonstrate the benefits of caching and batch processing.
 */
public class PerformanceTest {
    
    private static final String UTM_PROJ = "+proj=utm +zone=15 +datum=WGS84";
    private static final String WGS84_PROJ = "WGS84";
    private static final int TEST_POINTS = 1000;
    
    private List<Point> testPoints;
    private Random random;
    
    @BeforeEach
    public void setUp() {
        // Clear cache before each test
        Proj4Sedona.clearProjectionCache();
        
        // Generate test points
        random = new Random(42); // Fixed seed for reproducible results
        testPoints = new ArrayList<>();
        for (int i = 0; i < TEST_POINTS; i++) {
            double lon = -180 + random.nextDouble() * 360;
            double lat = -90 + random.nextDouble() * 180;
            testPoints.add(new Point(lon, lat));
        }
    }
    
    @Test
    public void testProjectionCaching() {
        // Test that caching works
        assertEquals(0, Proj4Sedona.getProjectionCacheSize());
        
        // First transformation - should cache projections
        Point result1 = Proj4Sedona.transform(WGS84_PROJ, UTM_PROJ, testPoints.get(0));
        assertNotNull(result1);
        assertTrue(Proj4Sedona.getProjectionCacheSize() > 0);
        
        // Second transformation - should use cached projections
        int cacheSize = Proj4Sedona.getProjectionCacheSize();
        Point result2 = Proj4Sedona.transform(WGS84_PROJ, UTM_PROJ, testPoints.get(1));
        assertNotNull(result2);
        assertEquals(cacheSize, Proj4Sedona.getProjectionCacheSize()); // Cache size should not increase
    }
    
    @Test
    public void testBatchTransformation() {
        // Test batch transformation
        List<Point> results = Proj4Sedona.transformBatch(WGS84_PROJ, UTM_PROJ, testPoints);
        
        assertEquals(TEST_POINTS, results.size());
        
        // Verify all points were transformed
        for (Point result : results) {
            assertNotNull(result);
            assertFalse(Double.isNaN(result.x));
            assertFalse(Double.isNaN(result.y));
        }
    }
    
    @Test
    public void testArrayTransformation() {
        // Prepare coordinate arrays
        double[] xCoords = new double[TEST_POINTS];
        double[] yCoords = new double[TEST_POINTS];
        
        for (int i = 0; i < TEST_POINTS; i++) {
            xCoords[i] = testPoints.get(i).x;
            yCoords[i] = testPoints.get(i).y;
        }
        
        // Test array transformation
        Point[] results = Proj4Sedona.transformArrays(WGS84_PROJ, UTM_PROJ, xCoords, yCoords);
        
        assertEquals(TEST_POINTS, results.length);
        
        // Verify all points were transformed
        for (Point result : results) {
            assertNotNull(result);
            assertFalse(Double.isNaN(result.x));
            assertFalse(Double.isNaN(result.y));
        }
    }
    
    @Test
    public void testBatchTransformerReuse() {
        // Create a batch transformer
        BatchTransformer transformer = Proj4Sedona.createBatchTransformer(WGS84_PROJ, UTM_PROJ);
        
        // Transform multiple batches
        List<Point> batch1 = testPoints.subList(0, 100);
        List<Point> batch2 = testPoints.subList(100, 200);
        
        List<Point> results1 = transformer.transformBatch(batch1);
        List<Point> results2 = transformer.transformBatch(batch2);
        
        assertEquals(100, results1.size());
        assertEquals(100, results2.size());
        
        // Verify transformations are correct
        for (Point result : results1) {
            assertNotNull(result);
            assertFalse(Double.isNaN(result.x));
            assertFalse(Double.isNaN(result.y));
        }
    }
    
    @Test
    public void testPerformanceComparison() {
        // This test demonstrates the performance benefits
        // Note: In a real performance test, you would measure actual execution times
        
        // Method 1: Individual transformations (slower due to repeated projection creation)
        long start1 = System.nanoTime();
        List<Point> individualResults = new ArrayList<>();
        for (Point point : testPoints.subList(0, 100)) {
            Point result = Proj4Sedona.transform(WGS84_PROJ, UTM_PROJ, point);
            individualResults.add(result);
        }
        long time1 = System.nanoTime() - start1;
        
        // Method 2: Batch transformation (faster due to cached projections)
        long start2 = System.nanoTime();
        List<Point> batchResults = Proj4Sedona.transformBatch(WGS84_PROJ, UTM_PROJ, testPoints.subList(0, 100));
        long time2 = System.nanoTime() - start2;
        
        // Both methods should produce the same results
        assertEquals(individualResults.size(), batchResults.size());
        for (int i = 0; i < individualResults.size(); i++) {
            Point individual = individualResults.get(i);
            Point batch = batchResults.get(i);
            assertEquals(individual.x, batch.x, 1e-10);
            assertEquals(individual.y, batch.y, 1e-10);
        }
        
        // Batch method should be faster (though this is not guaranteed in unit tests)
        // In real scenarios with larger datasets, the difference would be more pronounced
        System.out.println("Individual transformations: " + (time1 / 1_000_000) + " ms");
        System.out.println("Batch transformations: " + (time2 / 1_000_000) + " ms");
    }
    
    @Test
    public void testCacheManagement() {
        // Test cache clearing
        Proj4Sedona.transform(WGS84_PROJ, UTM_PROJ, testPoints.get(0));
        assertTrue(Proj4Sedona.getProjectionCacheSize() > 0);
        
        Proj4Sedona.clearProjectionCache();
        assertEquals(0, Proj4Sedona.getProjectionCacheSize());
    }
}
