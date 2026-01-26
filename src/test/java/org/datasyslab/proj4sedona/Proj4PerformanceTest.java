package org.datasyslab.proj4sedona;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.core.Proj;
import org.datasyslab.proj4sedona.transform.Converter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Proj4 performance features: caching, batch transformations.
 */
public class Proj4PerformanceTest {

    private static final double TOLERANCE = 1e-6;

    @BeforeEach
    void setUp() {
        Proj4.clearCache();
    }

    @AfterEach
    void tearDown() {
        Proj4.clearCache();
    }

    // ========== Caching Tests ==========

    @Test
    void testGetCachedProj() {
        String projDef = "+proj=utm +zone=18 +datum=WGS84";
        
        Proj proj1 = Proj4.getCachedProj(projDef);
        Proj proj2 = Proj4.getCachedProj(projDef);
        
        assertNotNull(proj1);
        assertSame(proj1, proj2, "Same definition should return same cached instance");
    }

    @Test
    void testCacheSize() {
        assertEquals(0, Proj4.getCacheSize());
        
        Proj4.getCachedProj("+proj=longlat +datum=WGS84");
        assertEquals(1, Proj4.getCacheSize());
        
        Proj4.getCachedProj("+proj=merc +datum=WGS84");
        assertEquals(2, Proj4.getCacheSize());
        
        // Same definition should not increase cache size
        Proj4.getCachedProj("+proj=longlat +datum=WGS84");
        assertEquals(2, Proj4.getCacheSize());
    }

    @Test
    void testClearCache() {
        Proj4.getCachedProj("+proj=longlat +datum=WGS84");
        Proj4.getCachedProj("+proj=merc +datum=WGS84");
        assertEquals(2, Proj4.getCacheSize());
        
        Proj4.clearCache();
        assertEquals(0, Proj4.getCacheSize());
    }

    @Test
    void testCachedConverter() {
        Converter conv = Proj4.cachedConverter(
            "+proj=longlat +datum=WGS84",
            "+proj=merc +datum=WGS84"
        );
        
        assertNotNull(conv);
        
        // Should have cached both projections
        assertEquals(2, Proj4.getCacheSize());
        
        // Converter should work
        Point result = conv.forward(new Point(-77.0, 38.9));
        assertNotNull(result);
        assertFalse(Double.isNaN(result.x));
    }

    // ========== Batch Transformation Tests ==========

    @Test
    void testTransformBatch() {
        double[][] coords = {
            {-77.0, 38.9},
            {-122.4, 37.8},
            {0.0, 51.5}
        };

        double[][] results = Proj4.transformBatch(
            "+proj=longlat +datum=WGS84",
            "+proj=merc +datum=WGS84",
            coords
        );

        assertEquals(3, results.length);
        for (double[] result : results) {
            assertEquals(2, result.length);
            assertFalse(Double.isNaN(result[0]), "X should not be NaN");
            assertFalse(Double.isNaN(result[1]), "Y should not be NaN");
        }
    }

    @Test
    void testTransformBatchEmpty() {
        double[][] results = Proj4.transformBatch(
            "+proj=longlat +datum=WGS84",
            "+proj=merc +datum=WGS84",
            new double[0][0]
        );

        assertEquals(0, results.length);
    }

    @Test
    void testTransformBatchNull() {
        double[][] results = Proj4.transformBatch(
            "+proj=longlat +datum=WGS84",
            "+proj=merc +datum=WGS84",
            null
        );

        assertEquals(0, results.length);
    }

    @Test
    void testTransformBatchWithNullEntries() {
        double[][] coords = {
            {-77.0, 38.9},
            null,
            {0.0, 51.5}
        };

        double[][] results = Proj4.transformBatch(
            "+proj=longlat +datum=WGS84",
            "+proj=merc +datum=WGS84",
            coords
        );

        assertEquals(3, results.length);
        assertFalse(Double.isNaN(results[0][0]));
        assertTrue(Double.isNaN(results[1][0])); // null entry produces NaN
        assertFalse(Double.isNaN(results[2][0]));
    }

    @Test
    void testTransformFlat() {
        double[] coords = {-77.0, 38.9, -122.4, 37.8, 0.0, 51.5};

        double[] results = Proj4.transformFlat(
            "+proj=longlat +datum=WGS84",
            "+proj=merc +datum=WGS84",
            coords
        );

        assertEquals(6, results.length);
        for (int i = 0; i < results.length; i++) {
            assertFalse(Double.isNaN(results[i]), "Result " + i + " should not be NaN");
        }
    }

    @Test
    void testTransformFlatEmpty() {
        double[] results = Proj4.transformFlat(
            "+proj=longlat +datum=WGS84",
            "+proj=merc +datum=WGS84",
            new double[0]
        );

        assertEquals(0, results.length);
    }

    @Test
    void testTransformFlat3D() {
        double[] coords = {-77.0, 38.9, 100.0, -122.4, 37.8, 200.0};

        double[] results = Proj4.transformFlat3D(
            "+proj=longlat +datum=WGS84",
            "+proj=merc +datum=WGS84",
            coords
        );

        assertEquals(6, results.length);
        // Z values should be preserved
        assertFalse(Double.isNaN(results[2]));
        assertFalse(Double.isNaN(results[5]));
    }

    // ========== Round-Trip Tests ==========

    @Test
    void testBatchRoundTrip() {
        double[][] original = {
            {-77.0, 38.9},
            {-122.4, 37.8},
            {0.0, 51.5}
        };

        // Forward
        double[][] forward = Proj4.transformBatch(
            "+proj=longlat +datum=WGS84",
            "+proj=merc +datum=WGS84",
            original
        );

        // Inverse
        double[][] inverse = Proj4.transformBatch(
            "+proj=merc +datum=WGS84",
            "+proj=longlat +datum=WGS84",
            forward
        );

        // Compare
        for (int i = 0; i < original.length; i++) {
            assertEquals(original[i][0], inverse[i][0], TOLERANCE, 
                "X round-trip failed for point " + i);
            assertEquals(original[i][1], inverse[i][1], TOLERANCE, 
                "Y round-trip failed for point " + i);
        }
    }

    @Test
    void testFlatRoundTrip() {
        double[] original = {-77.0, 38.9, -122.4, 37.8, 0.0, 51.5};

        // Forward
        double[] forward = Proj4.transformFlat(
            "+proj=longlat +datum=WGS84",
            "+proj=merc +datum=WGS84",
            original
        );

        // Inverse
        double[] inverse = Proj4.transformFlat(
            "+proj=merc +datum=WGS84",
            "+proj=longlat +datum=WGS84",
            forward
        );

        // Compare
        for (int i = 0; i < original.length; i++) {
            assertEquals(original[i], inverse[i], TOLERANCE, 
                "Round-trip failed for index " + i);
        }
    }

    // ========== Performance Sanity Tests ==========

    @Test
    void testBatchPerformanceSanity() {
        // Create 1000 test points
        double[][] coords = new double[1000][2];
        for (int i = 0; i < 1000; i++) {
            coords[i][0] = -180 + (i % 360);
            coords[i][1] = -80 + (i / 10.0) % 160;
        }

        long start = System.nanoTime();
        double[][] results = Proj4.transformBatch(
            "+proj=longlat +datum=WGS84",
            "+proj=merc +datum=WGS84",
            coords
        );
        long elapsed = System.nanoTime() - start;

        assertEquals(1000, results.length);
        System.out.printf("Batch transform 1000 points: %.2f ms%n", elapsed / 1_000_000.0);
    }

    @Test
    void testFlatPerformanceSanity() {
        // Create 1000 test points (2000 values)
        double[] coords = new double[2000];
        for (int i = 0; i < 1000; i++) {
            coords[i * 2] = -180 + (i % 360);
            coords[i * 2 + 1] = -80 + (i / 10.0) % 160;
        }

        long start = System.nanoTime();
        double[] results = Proj4.transformFlat(
            "+proj=longlat +datum=WGS84",
            "+proj=merc +datum=WGS84",
            coords
        );
        long elapsed = System.nanoTime() - start;

        assertEquals(2000, results.length);
        System.out.printf("Flat transform 1000 points: %.2f ms%n", elapsed / 1_000_000.0);
    }

    @Test
    void testCachePerformanceBenefit() {
        String projDef = "+proj=utm +zone=18 +datum=WGS84";

        // First call (uncached)
        long start1 = System.nanoTime();
        Proj proj1 = Proj4.getCachedProj(projDef);
        long elapsed1 = System.nanoTime() - start1;

        // Second call (cached)
        long start2 = System.nanoTime();
        Proj proj2 = Proj4.getCachedProj(projDef);
        long elapsed2 = System.nanoTime() - start2;

        assertSame(proj1, proj2);
        assertTrue(elapsed2 < elapsed1, 
            "Cached lookup should be faster than initial creation");
        
        System.out.printf("First call: %.3f ms, Cached call: %.3f ms%n", 
            elapsed1 / 1_000_000.0, elapsed2 / 1_000_000.0);
    }
}
