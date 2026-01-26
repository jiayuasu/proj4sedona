package org.proj4sedona.integration;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.proj4sedona.Proj4;
import org.proj4sedona.core.Point;
import org.proj4sedona.grid.GridLoader;
import org.proj4sedona.projection.ProjectionRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Integration tests for grid-based coordinate transformations.
 * 
 * <p>These tests verify coordinate transformations that use grid files
 * (like NTv2 grids) for higher accuracy datum transformations.</p>
 * 
 * <p>Grid files are fetched from cdn.proj.org when needed.</p>
 * 
 * <p>Test cases cover:</p>
 * <ul>
 *   <li>NAD83 to NAD83(HARN) using us_noaa_conus.tif</li>
 *   <li>NAD27 to NAD83 using ca_nrc_ntv2_0.tif</li>
 * </ul>
 */
public class GridTransformIT {

    private static final String REFERENCE_FILE = "/pyproj-reference/grid_transform_reference.json";
    
    // Grid transformations can have slightly different results due to
    // interpolation differences (bilinear vs bicubic, grid versions, etc.)
    // so we use a larger tolerance than standard geographic transformations
    private static final double GRID_TOLERANCE = 0.01;  // 1cm for projected
    private static final double GEOGRAPHIC_TOLERANCE = 1e-5;  // ~1m - accounts for interpolation differences
    
    private static JsonObject referenceData;
    private static Path tempCacheDir;
    
    @BeforeAll
    static void setup() throws IOException {
        // Initialize projection registry
        ProjectionRegistry.start();
        
        // Create temp directory for grid cache
        tempCacheDir = Files.createTempDirectory("proj4sedona-grid-cache");
        GridLoader.setCacheDirectory(tempCacheDir);
        GridLoader.setAutoFetch(true);
        
        // Pre-fetch required grid files from CDN
        // This ensures grids are available before running transformation tests
        prefetchGrids();
        
        // Load reference data
        referenceData = loadReferenceData();
        
        assertNotNull(referenceData, "Reference data should be loaded");
    }
    
    /**
     * Pre-fetch grid files from CDN before running tests.
     * This is done once at setup time to avoid network latency during tests.
     */
    private static void prefetchGrids() {
        String[] requiredGrids = {"us_noaa_conus.tif", "ca_nrc_ntv2_0.tif"};
        
        for (String gridName : requiredGrids) {
            if (!GridLoader.has(gridName)) {
                try {
                    System.out.println("Pre-fetching grid from CDN: " + gridName);
                    GridLoader.fetchFromCdn(gridName);
                    System.out.println("Successfully fetched: " + gridName);
                } catch (IOException e) {
                    System.out.println("Could not fetch " + gridName + ": " + e.getMessage());
                    System.out.println("Tests requiring this grid will be skipped.");
                }
            }
        }
    }
    
    @AfterAll
    static void cleanup() throws IOException {
        // Disable auto-fetch
        GridLoader.setAutoFetch(false);
        GridLoader.setCacheDirectory(null);
        
        // Clean up temp directory
        if (tempCacheDir != null && Files.exists(tempCacheDir)) {
            Files.walk(tempCacheDir)
                .sorted((a, b) -> -a.compareTo(b))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        // Ignore cleanup errors
                    }
                });
        }
    }
    
    private static JsonObject loadReferenceData() throws IOException {
        try (InputStream is = GridTransformIT.class.getResourceAsStream(REFERENCE_FILE)) {
            if (is == null) {
                throw new IOException("Reference file not found: " + REFERENCE_FILE);
            }
            Gson gson = new Gson();
            return gson.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), JsonObject.class);
        }
    }
    
    @TestFactory
    @DisplayName("Grid-based transformation tests")
    Collection<DynamicTest> gridTransformTests() {
        List<DynamicTest> tests = new ArrayList<>();
        
        if (!referenceData.has("test_cases")) {
            return tests;
        }
        
        JsonArray testCases = referenceData.getAsJsonArray("test_cases");
        
        for (JsonElement element : testCases) {
            JsonObject testCase = element.getAsJsonObject();
            String name = testCase.get("name").getAsString();
            String description = testCase.get("description").getAsString();
            String gridFile = testCase.get("grid_file").getAsString();
            String fromCrs = testCase.get("from_crs").getAsString();
            String toCrs = testCase.get("to_crs").getAsString();
            
            JsonObject transformResult = testCase.getAsJsonObject("transform_result");
            if (transformResult == null) {
                continue;
            }
            
            // Check for error in transform result
            if (transformResult.has("error") && !transformResult.get("error").isJsonNull()) {
                tests.add(dynamicTest(name + " (skipped - reference error)", () -> {
                    System.out.println("Skipping " + name + ": " + transformResult.get("error").getAsString());
                }));
                continue;
            }
            
            JsonArray transformations = transformResult.getAsJsonArray("transformations");
            if (transformations == null) {
                continue;
            }
            
            // Create tests for each transformation
            for (JsonElement transElement : transformations) {
                JsonObject transformation = transElement.getAsJsonObject();
                String pointName = transformation.get("point_name").getAsString();
                
                // Skip if transformation has error
                if (transformation.has("error") && !transformation.get("error").isJsonNull()) {
                    continue;
                }
                
                JsonObject input = transformation.getAsJsonObject("input");
                JsonObject expectedOutput = transformation.getAsJsonObject("output");
                
                if (expectedOutput == null || expectedOutput.isJsonNull()) {
                    continue;
                }
                
                double inputX = input.get("x").getAsDouble();
                double inputY = input.get("y").getAsDouble();
                double expectedX = expectedOutput.get("x").getAsDouble();
                double expectedY = expectedOutput.get("y").getAsDouble();
                
                String testName = String.format("%s: %s (%s)", name, pointName, gridFile);
                
                tests.add(dynamicTest(testName, () -> {
                    testGridTransformation(fromCrs, toCrs, gridFile,
                        inputX, inputY, expectedX, expectedY, pointName);
                }));
            }
        }
        
        return tests;
    }
    
    private void testGridTransformation(String fromCrs, String toCrs, String gridFile,
                                         double inputX, double inputY,
                                         double expectedX, double expectedY,
                                         String pointName) {
        // Check if the required grid file is loaded
        boolean gridAvailable = GridLoader.has(gridFile);
        assumeTrue(gridAvailable, 
            "Skipping test: grid file " + gridFile + " is not available (requires network access to cdn.proj.org)");
        
        // Check if we can parse the CRS codes
        try {
            Proj4.getCachedProj(fromCrs);
            Proj4.getCachedProj(toCrs);
        } catch (IllegalArgumentException e) {
            assumeTrue(false, "Skipping test: " + e.getMessage());
            return;
        }
        
        // Perform transformation
        Point result = Proj4.proj4(fromCrs, toCrs, new Point(inputX, inputY));
        
        assertNotNull(result, "Transformation result should not be null for " + pointName);
        assertFalse(Double.isNaN(result.x), "Result X should not be NaN for " + pointName);
        assertFalse(Double.isNaN(result.y), "Result Y should not be NaN for " + pointName);
        
        // Verify that transformation actually happened (not identity)
        // If the difference from input is too small, the grid might not have been applied
        double deltaX = Math.abs(result.x - inputX);
        double deltaY = Math.abs(result.y - inputY);
        double expectedDeltaX = Math.abs(expectedX - inputX);
        double expectedDeltaY = Math.abs(expectedY - inputY);
        
        // Only check if expected transformation is significant (> 1e-7 degrees ~= 1cm)
        if (expectedDeltaX > 1e-7 || expectedDeltaY > 1e-7) {
            assertTrue(deltaX > 1e-8 || deltaY > 1e-8,
                String.format("Grid transformation for %s may not have been applied " +
                    "(result very close to input). Ensure grid file %s is properly loaded.",
                    pointName, gridFile));
        }
        
        // Compare with expected values using appropriate tolerance
        // Grid transformations operate on geographic coordinates
        assertEquals(expectedX, result.x, GEOGRAPHIC_TOLERANCE,
            String.format("X coordinate mismatch for %s: expected %f, got %f",
                pointName, expectedX, result.x));
        assertEquals(expectedY, result.y, GEOGRAPHIC_TOLERANCE,
            String.format("Y coordinate mismatch for %s: expected %f, got %f",
                pointName, expectedY, result.y));
    }
    
    @TestFactory
    @DisplayName("Grid loading tests")
    Collection<DynamicTest> gridLoadingTests() {
        List<DynamicTest> tests = new ArrayList<>();
        
        // Test that we can check for grid availability
        tests.add(dynamicTest("Check CONUS grid availability", () -> {
            // This test verifies the grid loader infrastructure works
            // Actual grid availability depends on network/cache
            assertDoesNotThrow(() -> {
                boolean hasGrid = GridLoader.has("us_noaa_conus.tif");
                System.out.println("us_noaa_conus.tif available: " + hasGrid);
            });
        }));
        
        tests.add(dynamicTest("Check Canada grid availability", () -> {
            assertDoesNotThrow(() -> {
                boolean hasGrid = GridLoader.has("ca_nrc_ntv2_0.tif");
                System.out.println("ca_nrc_ntv2_0.tif available: " + hasGrid);
            });
        }));
        
        return tests;
    }
}
