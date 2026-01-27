package org.datasyslab.proj4sedona.benchmark;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.datasyslab.proj4sedona.core.Proj;
import org.datasyslab.proj4sedona.parser.CRSSerializer;
import org.datasyslab.proj4sedona.projection.ProjectionRegistry;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Correctness benchmarks for CRS serialization/export.
 * 
 * <p>These tests verify that proj4sedona can correctly export CRS definitions
 * to various formats (WKT1, WKT2, PROJ string, PROJJSON) and that the exported
 * formats can be parsed back to produce equivalent CRS definitions.</p>
 * 
 * <p>Test cases cover:</p>
 * <ul>
 *   <li>Format export correctness</li>
 *   <li>Round-trip verification (export then parse)</li>
 *   <li>Cross-format compatibility</li>
 * </ul>
 */
public class SerializerCorrectnessBenchmark {

    private static final Path REFERENCE_FILE = Paths.get("target/pyproj-reference/format_export_reference.json");
    
    private static final double ELLIPSOID_TOLERANCE = 0.1;
    
    private static JsonObject referenceData;
    
    @BeforeAll
    static void setup() throws IOException {
        // Initialize projection registry
        ProjectionRegistry.start();
        
        // Load reference data
        referenceData = loadReferenceData();
        
        assertNotNull(referenceData, "Reference data should be loaded");
    }
    
    private static JsonObject loadReferenceData() throws IOException {
        if (!Files.exists(REFERENCE_FILE)) {
            throw new IOException("Reference file not found: " + REFERENCE_FILE + 
                ". Run 'mvn verify -Pbenchmarks' to generate pyproj reference data.");
        }
        Gson gson = new Gson();
        return gson.fromJson(new InputStreamReader(Files.newInputStream(REFERENCE_FILE), StandardCharsets.UTF_8), JsonObject.class);
    }
    
    @TestFactory
    @DisplayName("Format export tests")
    Collection<DynamicTest> formatExportTests() {
        List<DynamicTest> tests = new ArrayList<>();
        
        if (!referenceData.has("test_cases")) {
            return tests;
        }
        
        JsonArray testCases = referenceData.getAsJsonArray("test_cases");
        
        for (JsonElement element : testCases) {
            JsonObject testCase = element.getAsJsonObject();
            String name = testCase.get("name").getAsString();
            String description = testCase.get("description").getAsString();
            String input = testCase.get("input").getAsString();
            
            // Skip if test case has error
            if (testCase.has("error") && !testCase.get("error").isJsonNull()) {
                continue;
            }
            
            // Test WKT1 export
            tests.add(dynamicTest(name + ": export to WKT1", () -> {
                testWkt1Export(input, description);
            }));
            
            // Test WKT2 export
            tests.add(dynamicTest(name + ": export to WKT2", () -> {
                testWkt2Export(input, description);
            }));
            
            // Test PROJ string export
            tests.add(dynamicTest(name + ": export to PROJ string", () -> {
                testProjStringExport(input, description);
            }));
            
            // Test PROJJSON export
            tests.add(dynamicTest(name + ": export to PROJJSON", () -> {
                testProjJsonExport(input, description);
            }));
        }
        
        return tests;
    }
    
    private void testWkt1Export(String crsDefinition, String description) {
        try {
            Proj proj = new Proj(crsDefinition);
            String wkt1 = CRSSerializer.toWkt1(proj);
            
            assertNotNull(wkt1, "WKT1 export should not be null for: " + description);
            assertFalse(wkt1.isEmpty(), "WKT1 export should not be empty");
            
            // Verify WKT1 structure
            assertTrue(wkt1.startsWith("GEOGCS[") || wkt1.startsWith("PROJCS["),
                "WKT1 should start with GEOGCS or PROJCS");
            assertTrue(wkt1.contains("DATUM["), "WKT1 should contain DATUM");
            assertTrue(wkt1.contains("SPHEROID[") || wkt1.contains("ELLIPSOID["),
                "WKT1 should contain SPHEROID or ELLIPSOID");
            
        } catch (Exception e) {
            fail("WKT1 export failed for " + description + ": " + e.getMessage());
        }
    }
    
    private void testWkt2Export(String crsDefinition, String description) {
        try {
            Proj proj = new Proj(crsDefinition);
            String wkt2 = CRSSerializer.toWkt2(proj);
            
            assertNotNull(wkt2, "WKT2 export should not be null for: " + description);
            assertFalse(wkt2.isEmpty(), "WKT2 export should not be empty");
            
            // Verify WKT2 structure
            assertTrue(wkt2.startsWith("GEOGCRS[") || wkt2.startsWith("PROJCRS["),
                "WKT2 should start with GEOGCRS or PROJCRS");
            assertTrue(wkt2.contains("DATUM["), "WKT2 should contain DATUM");
            assertTrue(wkt2.contains("ELLIPSOID["), "WKT2 should contain ELLIPSOID");
            
        } catch (Exception e) {
            fail("WKT2 export failed for " + description + ": " + e.getMessage());
        }
    }
    
    private void testProjStringExport(String crsDefinition, String description) {
        try {
            Proj proj = new Proj(crsDefinition);
            String projString = CRSSerializer.toProjString(proj);
            
            assertNotNull(projString, "PROJ string export should not be null for: " + description);
            assertFalse(projString.isEmpty(), "PROJ string export should not be empty");
            
            // Verify PROJ string structure
            assertTrue(projString.startsWith("+proj="), "PROJ string should start with +proj=");
            
        } catch (Exception e) {
            fail("PROJ string export failed for " + description + ": " + e.getMessage());
        }
    }
    
    private void testProjJsonExport(String crsDefinition, String description) {
        try {
            Proj proj = new Proj(crsDefinition);
            String projJson = CRSSerializer.toProjJson(proj);
            
            assertNotNull(projJson, "PROJJSON export should not be null for: " + description);
            assertFalse(projJson.isEmpty(), "PROJJSON export should not be empty");
            
            // Verify PROJJSON structure
            assertTrue(projJson.contains("\"type\""), "PROJJSON should contain type field");
            
            // Parse JSON to verify validity
            Gson gson = new Gson();
            JsonObject json = gson.fromJson(projJson, JsonObject.class);
            assertNotNull(json, "PROJJSON should be valid JSON");
            assertTrue(json.has("type"), "PROJJSON should have type field");
            
        } catch (Exception e) {
            fail("PROJJSON export failed for " + description + ": " + e.getMessage());
        }
    }
    
    @TestFactory
    @DisplayName("Round-trip tests")
    Collection<DynamicTest> roundTripTests() {
        List<DynamicTest> tests = new ArrayList<>();
        
        if (!referenceData.has("test_cases")) {
            return tests;
        }
        
        JsonArray testCases = referenceData.getAsJsonArray("test_cases");
        
        for (JsonElement element : testCases) {
            JsonObject testCase = element.getAsJsonObject();
            String name = testCase.get("name").getAsString();
            String input = testCase.get("input").getAsString();
            
            // Skip if test case has error
            if (testCase.has("error") && !testCase.get("error").isJsonNull()) {
                continue;
            }
            
            // Test round-trip through WKT1
            tests.add(dynamicTest(name + ": round-trip via WKT1", () -> {
                testWkt1RoundTrip(input, name);
            }));
            
            // Test round-trip through PROJJSON
            tests.add(dynamicTest(name + ": round-trip via PROJJSON", () -> {
                testProjJsonRoundTrip(input, name);
            }));
            
            // Test round-trip through PROJ string
            tests.add(dynamicTest(name + ": round-trip via PROJ string", () -> {
                testProjStringRoundTrip(input, name);
            }));
        }
        
        return tests;
    }
    
    private void testWkt1RoundTrip(String crsDefinition, String name) {
        try {
            // Parse original
            Proj original = new Proj(crsDefinition);
            double originalA = original.getA();
            
            // Export to WKT1
            String wkt1 = CRSSerializer.toWkt1(original);
            assertNotNull(wkt1, "WKT1 export should succeed");
            
            // Parse back from WKT1
            Proj parsed = new Proj(wkt1);
            assertNotNull(parsed, "Parsing WKT1 should succeed");
            
            // Verify ellipsoid is preserved
            assertEquals(originalA, parsed.getA(), ELLIPSOID_TOLERANCE,
                "Semi-major axis should be preserved in WKT1 round-trip for " + name);
            
        } catch (Exception e) {
            fail("WKT1 round-trip failed for " + name + ": " + e.getMessage());
        }
    }
    
    private void testProjJsonRoundTrip(String crsDefinition, String name) {
        try {
            // Parse original
            Proj original = new Proj(crsDefinition);
            double originalA = original.getA();
            
            // Export to PROJJSON
            String projJson = CRSSerializer.toProjJson(original);
            assertNotNull(projJson, "PROJJSON export should succeed");
            
            // Parse back from PROJJSON
            Proj parsed = new Proj(projJson);
            assertNotNull(parsed, "Parsing PROJJSON should succeed");
            
            // Verify ellipsoid is preserved
            assertEquals(originalA, parsed.getA(), ELLIPSOID_TOLERANCE,
                "Semi-major axis should be preserved in PROJJSON round-trip for " + name);
            
        } catch (Exception e) {
            fail("PROJJSON round-trip failed for " + name + ": " + e.getMessage());
        }
    }
    
    private void testProjStringRoundTrip(String crsDefinition, String name) {
        try {
            // Parse original
            Proj original = new Proj(crsDefinition);
            double originalA = original.getA();
            
            // Export to PROJ string
            String projString = CRSSerializer.toProjString(original);
            assertNotNull(projString, "PROJ string export should succeed");
            
            // Parse back from PROJ string
            Proj parsed = new Proj(projString);
            assertNotNull(parsed, "Parsing PROJ string should succeed");
            
            // Verify ellipsoid is preserved
            assertEquals(originalA, parsed.getA(), ELLIPSOID_TOLERANCE,
                "Semi-major axis should be preserved in PROJ string round-trip for " + name);
            
        } catch (Exception e) {
            fail("PROJ string round-trip failed for " + name + ": " + e.getMessage());
        }
    }
}
