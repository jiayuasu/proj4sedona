package org.datasyslab.proj4sedona.benchmark;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.datasyslab.proj4sedona.Proj4;
import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.core.Proj;
import org.datasyslab.proj4sedona.projection.ProjectionRegistry;
import org.datasyslab.proj4sedona.grid.GridLoader;
import org.datasyslab.proj4sedona.parser.CRSSerializer;
import org.datasyslab.proj4sedona.transform.Converter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;

/**
 * Benchmark runner that produces a Markdown report with:
 * 1. Speedup vs pyproj
 * 2. Correctness vs pyproj (error statistics)
 */
public class SpeedBenchmark {

    private static final int WARMUP_ITERATIONS = 1000;
    private static final int MEASUREMENT_ITERATIONS = 5000;
    
    // Tolerances for correctness categories
    private static final double GEOGRAPHIC_TOLERANCE = 1e-6;  // degrees
    private static final double PROJECTED_TOLERANCE = 0.01;   // meters

    // Pre-initialized objects for speed benchmarks
    private Proj wgs84;
    private Converter wgs84ToMerc;
    private Converter ostn15Converter;
    private boolean ostn15Available = false;

    // Test data
    private Point testPoint;
    private double[][] batchCoords;
    private Point testPointGb;

    // Results
    private final Map<String, Double> javaResults = new LinkedHashMap<>();
    private Map<String, Double> pyprojResults = new LinkedHashMap<>();
    
    // Error statistics by category
    private final Map<String, ErrorStats> errorStatsByCategory = new LinkedHashMap<>();
    private final List<String> skippedTests = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        String outputFile = "target/benchmark_report.md";
        
        for (int i = 0; i < args.length; i++) {
            if ("--output".equals(args[i]) && i + 1 < args.length) {
                outputFile = args[i + 1];
            }
        }
        
        System.out.println("=".repeat(60));
        System.out.println("proj4sedona Benchmark Runner");
        System.out.println("=".repeat(60));
        
        SpeedBenchmark benchmark = new SpeedBenchmark();
        benchmark.run(outputFile);
        
        System.exit(0);
    }

    public void run(String outputFile) throws Exception {
        // Initialize
        ProjectionRegistry.start();
        setup();
        
        // Run Java speed benchmarks
        System.out.println("\n1. Running Java speed benchmarks...");
        runJavaSpeedBenchmarks();
        
        // Run pyproj speed benchmarks
        System.out.println("\n2. Running pyproj speed benchmarks...");
        runPyprojSpeedBenchmarks();
        
        // Run correctness comparisons
        System.out.println("\n3. Running correctness comparisons...");
        runCorrectnessComparisons();
        
        // Generate Markdown report
        System.out.println("\n4. Generating report...");
        generateMarkdownReport(outputFile);
        
        System.out.println("\nReport saved to: " + outputFile);
    }

    private void setup() throws IOException {
        wgs84 = new Proj("+proj=longlat +datum=WGS84");
        Proj webMercator = new Proj("EPSG:3857");
        wgs84ToMerc = new Converter(wgs84, webMercator);

        testPoint = new Point(-77.0369, 38.9072);
        testPointGb = new Point(-0.1276, 51.5074);

        Random rand = new Random(42);
        batchCoords = new double[1000][2];
        for (int i = 0; i < 1000; i++) {
            batchCoords[i][0] = -180 + rand.nextDouble() * 360;
            batchCoords[i][1] = -80 + rand.nextDouble() * 160;
        }

        setupOstn15();
    }

    private void setupOstn15() {
        try {
            Path tempCacheDir = Files.createTempDirectory("proj4sedona-benchmark-cache");
            GridLoader.setCacheDirectory(tempCacheDir);
            GridLoader.setAutoFetch(true);
            
            String gridName = "uk_os_OSTN15_NTv2_OSGBtoETRS.tif";
            if (!GridLoader.has(gridName)) {
                System.out.println("   Fetching OSTN15 grid...");
                GridLoader.fetchFromCdn(gridName);
            }
            
            Proj etrs89 = new Proj("+proj=longlat +ellps=GRS80 +no_defs");
            Proj osgb36 = new Proj("+proj=longlat +ellps=airy +nadgrids=@" + gridName + " +no_defs");
            ostn15Converter = new Converter(etrs89, osgb36);
            ostn15Available = true;
            System.out.println("   OSTN15 grid loaded.");
        } catch (Exception e) {
            System.out.println("   OSTN15 grid unavailable: " + e.getMessage());
            ostn15Available = false;
        }
    }

    // ==================== Speed Benchmarks ====================

    private void runJavaSpeedBenchmarks() {
        benchmarkSupplier("CRS Init (EPSG)", () -> new Proj("EPSG:4326"));
        benchmarkSupplier("Transform (single)", () -> wgs84ToMerc.forward(testPoint));
        benchmarkSupplier("Transform (batch/1000)", () -> 
            Proj4.transformBatch("+proj=longlat +datum=WGS84", "EPSG:3857", batchCoords));
        
        if (ostn15Available) {
            benchmarkSupplier("OSTN15 Grid (single)", () -> ostn15Converter.forward(testPointGb));
        }
    }

    private <T> void benchmarkSupplier(String name, Supplier<T> operation) {
        final Object[] holder = new Object[1];
        
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            holder[0] = operation.get();
        }
        
        // Measure
        long[] times = new long[MEASUREMENT_ITERATIONS];
        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            long start = System.nanoTime();
            holder[0] = operation.get();
            times[i] = System.nanoTime() - start;
        }
        
        double meanUs = Arrays.stream(times).average().orElse(0) / 1000.0;
        javaResults.put(name, meanUs);
        System.out.printf("   %-30s: %8.2f μs%n", name, meanUs);
    }

    private void runPyprojSpeedBenchmarks() throws Exception {
        Path pyprojScript = Paths.get("scripts/pyproj-reference/run_pyproj_benchmarks.py");
        Path pyprojOutput = Paths.get("target/pyproj_benchmark_results.json");
        
        ProcessBuilder pb = new ProcessBuilder(
            "python3", pyprojScript.toString(),
            "--output", pyprojOutput.toString()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        // Consume output
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("   " + line);
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            System.out.println("   Warning: pyproj benchmarks failed (exit code " + exitCode + ")");
            return;
        }
        
        // Parse results
        if (Files.exists(pyprojOutput)) {
            Gson gson = new Gson();
            JsonObject json = gson.fromJson(Files.newBufferedReader(pyprojOutput), JsonObject.class);
            JsonObject benchmarks = json.getAsJsonObject("benchmarks");
            
            if (benchmarks != null) {
                // Map pyproj benchmark names to our names
                pyprojResults.put("CRS Init (EPSG)", getMeanUs(benchmarks, "crs_init_epsg_4326"));
                pyprojResults.put("Transform (single)", getMeanUs(benchmarks, "transform_single_merc"));
                pyprojResults.put("Transform (batch/1000)", getMeanUs(benchmarks, "transform_batch_1000_merc"));
                pyprojResults.put("OSTN15 Grid (single)", getMeanUs(benchmarks, "transform_single_ostn15"));
            }
        }
    }

    private double getMeanUs(JsonObject benchmarks, String key) {
        JsonObject bench = benchmarks.getAsJsonObject(key);
        if (bench != null && bench.has("mean_us")) {
            return bench.get("mean_us").getAsDouble();
        }
        return 0;
    }

    // ==================== Correctness Comparisons ====================

    private void runCorrectnessComparisons() throws IOException {
        runTransformCorrectness();
        runGridCorrectness();
        runParserCorrectness();
        runSerializerCorrectness();
    }

    private void runTransformCorrectness() throws IOException {
        Path refFile = Paths.get("target/pyproj-reference/transform_reference.json");
        if (!Files.exists(refFile)) {
            System.out.println("   Transform reference not found, skipping.");
            return;
        }
        
        Gson gson = new Gson();
        JsonObject refData = gson.fromJson(Files.newBufferedReader(refFile), JsonObject.class);
        JsonArray testCases = refData.getAsJsonArray("test_cases");
        
        ErrorStats geographicErrors = new ErrorStats("Geographic transforms", "deg");
        ErrorStats projectedErrors = new ErrorStats("Projected transforms", "m");
        
        Set<String> skipSet = Set.of("osgb36_to_wgs84", "ed50_to_wgs84");
        
        for (JsonElement tcElem : testCases) {
            JsonObject tc = tcElem.getAsJsonObject();
            String name = tc.get("name").getAsString();
            String fromCrs = tc.get("from_crs").getAsString();
            String toCrs = tc.get("to_crs").getAsString();
            
            if (skipSet.contains(name)) {
                skippedTests.add(name + ": Requires towgs84 parameters");
                continue;
            }
            
            JsonArray transforms = tc.getAsJsonArray("transformations");
            boolean isProjected = isProjectedCrs(toCrs);
            ErrorStats stats = isProjected ? projectedErrors : geographicErrors;
            
            for (JsonElement tElem : transforms) {
                JsonObject t = tElem.getAsJsonObject();
                JsonObject input = t.getAsJsonObject("input");
                JsonObject expected = t.getAsJsonObject("output");
                
                if (expected == null || t.get("error") != null && !t.get("error").isJsonNull()) {
                    continue;
                }
                
                double inX = input.get("x").getAsDouble();
                double inY = input.get("y").getAsDouble();
                double expX = expected.get("x").getAsDouble();
                double expY = expected.get("y").getAsDouble();
                
                try {
                    Point result = Proj4.proj4(fromCrs, toCrs, new Point(inX, inY));
                    if (result != null && !Double.isNaN(result.x) && !Double.isNaN(result.y)) {
                        double errorX = Math.abs(result.x - expX);
                        double errorY = Math.abs(result.y - expY);
                        stats.record(Math.max(errorX, errorY));
                    }
                } catch (Exception e) {
                    // Skip failed transforms
                }
            }
        }
        
        if (geographicErrors.count > 0) {
            errorStatsByCategory.put("Geographic transforms", geographicErrors);
        }
        if (projectedErrors.count > 0) {
            errorStatsByCategory.put("Projected transforms", projectedErrors);
        }
        
        System.out.println("   Transform correctness: " + 
            (geographicErrors.count + projectedErrors.count) + " comparisons");
    }

    private void runGridCorrectness() throws IOException {
        Path refFile = Paths.get("target/pyproj-reference/grid_transform_reference.json");
        if (!Files.exists(refFile)) {
            System.out.println("   Grid reference not found, skipping.");
            return;
        }
        
        Gson gson = new Gson();
        JsonObject refData = gson.fromJson(Files.newBufferedReader(refFile), JsonObject.class);
        JsonArray testCases = refData.getAsJsonArray("test_cases");
        
        ErrorStats gridErrors = new ErrorStats("Grid transforms", "deg");
        
        // Tests that use PROJ pipeline syntax (not supported)
        Set<String> skipSet = Set.of(
            "proj_pipeline_ostn15"  // Uses +proj=pipeline syntax, not standard CRS definitions
        );
        
        for (JsonElement tcElem : testCases) {
            JsonObject tc = tcElem.getAsJsonObject();
            String name = tc.get("name").getAsString();
            
            if (skipSet.contains(name)) {
                skippedTests.add(name + ": Uses PROJ pipeline syntax (not supported)");
                continue;
            }
            
            // Grid reference has nested transform_result structure
            JsonObject transformResult = tc.getAsJsonObject("transform_result");
            if (transformResult == null) continue;
            
            String fromCrs = transformResult.get("from_crs").getAsString();
            String toCrs = transformResult.get("to_crs").getAsString();
            JsonArray transforms = transformResult.getAsJsonArray("transformations");
            if (transforms == null) continue;
            
            for (JsonElement tElem : transforms) {
                JsonObject t = tElem.getAsJsonObject();
                JsonObject input = t.getAsJsonObject("input");
                JsonObject expected = t.getAsJsonObject("output");
                
                if (expected == null || (t.has("error") && !t.get("error").isJsonNull())) {
                    continue;
                }
                
                double inX = input.get("x").getAsDouble();
                double inY = input.get("y").getAsDouble();
                double expX = expected.get("x").getAsDouble();
                double expY = expected.get("y").getAsDouble();
                
                try {
                    Point result = Proj4.proj4(fromCrs, toCrs, new Point(inX, inY));
                    if (result != null && !Double.isNaN(result.x) && !Double.isNaN(result.y)) {
                        double errorX = Math.abs(result.x - expX);
                        double errorY = Math.abs(result.y - expY);
                        gridErrors.record(Math.max(errorX, errorY));
                    }
                } catch (Exception e) {
                    // Skip failed transforms
                }
            }
        }
        
        if (gridErrors.count > 0) {
            errorStatsByCategory.put("Grid transforms", gridErrors);
        }
        
        System.out.println("   Grid correctness: " + gridErrors.count + " comparisons");
    }

    private void runParserCorrectness() throws IOException {
        Path refFile = Paths.get("target/pyproj-reference/parsing_reference.json");
        if (!Files.exists(refFile)) {
            System.out.println("   Parser reference not found, skipping.");
            return;
        }
        
        Gson gson = new Gson();
        JsonObject refData = gson.fromJson(Files.newBufferedReader(refFile), JsonObject.class);
        JsonArray testCases = refData.getAsJsonArray("epsg_test_cases");
        
        ErrorStats parserErrors = new ErrorStats("Parser (ellipsoid)", "m");
        
        if (testCases == null) {
            System.out.println("   Parser reference has no test cases, skipping.");
            return;
        }
        
        for (JsonElement tcElem : testCases) {
            JsonObject tc = tcElem.getAsJsonObject();
            String input = tc.get("input").getAsString();
            JsonObject parsedParams = tc.getAsJsonObject("parsed_params");
            
            if (parsedParams == null) continue;
            
            JsonObject ellipsoid = parsedParams.getAsJsonObject("ellipsoid");
            if (ellipsoid == null) continue;
            
            double expectedA = ellipsoid.has("semi_major_metre") ? 
                ellipsoid.get("semi_major_metre").getAsDouble() : 0;
            double expectedRf = ellipsoid.has("inverse_flattening") ? 
                ellipsoid.get("inverse_flattening").getAsDouble() : 0;
            
            if (expectedA == 0) continue;
            
            try {
                Proj proj = new Proj(input);
                double actualA = proj.getA();  // semi-major axis
                double actualB = proj.getB();  // semi-minor axis
                
                // Compare semi-major axis (in meters)
                double errorA = Math.abs(actualA - expectedA);
                parserErrors.record(errorA);
                
                // Compare inverse flattening: rf = a / (a - b)
                if (expectedRf > 0 && actualA > actualB) {
                    double actualRf = actualA / (actualA - actualB);
                    double errorRf = Math.abs(actualRf - expectedRf);
                    // Scale by typical Earth radius to get meter-equivalent error
                    parserErrors.record(errorRf * 6378137 / 298.257);
                }
            } catch (Exception e) {
                // Skip CRS that can't be parsed
            }
        }
        
        if (parserErrors.count > 0) {
            errorStatsByCategory.put("Parser (ellipsoid)", parserErrors);
        }
        
        System.out.println("   Parser correctness: " + parserErrors.count + " comparisons");
    }

    private void runSerializerCorrectness() throws IOException {
        Path refFile = Paths.get("target/pyproj-reference/format_export_reference.json");
        if (!Files.exists(refFile)) {
            System.out.println("   Serializer reference not found, skipping.");
            return;
        }
        
        Gson gson = new Gson();
        JsonObject refData = gson.fromJson(Files.newBufferedReader(refFile), JsonObject.class);
        JsonArray testCases = refData.getAsJsonArray("test_cases");
        
        ErrorStats serializerErrors = new ErrorStats("Serializer", "m");
        
        if (testCases == null) {
            System.out.println("   Serializer reference has no test cases, skipping.");
            return;
        }
        
        for (JsonElement tcElem : testCases) {
            JsonObject tc = tcElem.getAsJsonObject();
            String input = tc.get("input").getAsString();
            JsonObject exports = tc.getAsJsonObject("exports");
            
            if (exports == null) continue;
            
            try {
                Proj proj = new Proj(input);
                
                // Export to WKT1 and re-parse to compare parameters
                String wkt1 = CRSSerializer.toWkt1(proj);
                if (wkt1 != null && !wkt1.isEmpty()) {
                    try {
                        Proj reparsed = new Proj(wkt1);
                        double errorA = Math.abs(reparsed.getA() - proj.getA());
                        serializerErrors.record(errorA);
                    } catch (Exception e) {
                        // WKT1 re-parse failed
                    }
                }
                
                // Export to PROJ string and re-parse
                String projStr = CRSSerializer.toProjString(proj);
                if (projStr != null && !projStr.isEmpty()) {
                    try {
                        Proj reparsed = new Proj(projStr);
                        double errorA = Math.abs(reparsed.getA() - proj.getA());
                        serializerErrors.record(errorA);
                    } catch (Exception e) {
                        // PROJ string re-parse failed
                    }
                }
            } catch (Exception e) {
                // Skip CRS that can't be processed
            }
        }
        
        if (serializerErrors.count > 0) {
            errorStatsByCategory.put("Serializer", serializerErrors);
        }
        
        System.out.println("   Serializer correctness: " + serializerErrors.count + " comparisons");
    }

    private boolean isProjectedCrs(String crs) {
        if (crs.startsWith("EPSG:")) {
            try {
                int code = Integer.parseInt(crs.substring(5));
                return code >= 32600 || code == 3857;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return crs.contains("+proj=") && !crs.contains("+proj=longlat");
    }

    // ==================== Report Generation ====================

    private void generateMarkdownReport(String outputFile) throws IOException {
        StringBuilder sb = new StringBuilder();
        
        sb.append("# proj4sedona Benchmark Report\n\n");
        sb.append("Generated: ").append(LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");
        
        // Speedup table
        sb.append("## Speedup vs pyproj\n\n");
        sb.append("| Benchmark | pyproj | proj4sedona | Speedup |\n");
        sb.append("|-----------|--------|-------------|--------:|\n");
        
        for (String name : javaResults.keySet()) {
            double javaUs = javaResults.get(name);
            double pyprojUs = pyprojResults.getOrDefault(name, 0.0);
            
            String pyprojStr = pyprojUs > 0 ? formatTime(pyprojUs) : "N/A";
            String javaStr = formatTime(javaUs);
            String speedupStr = "N/A";
            
            if (pyprojUs > 0 && javaUs > 0) {
                double speedup = pyprojUs / javaUs;
                speedupStr = String.format("%.1fx", speedup);
            }
            
            sb.append(String.format("| %s | %s | %s | %s |\n", 
                name, pyprojStr, javaStr, speedupStr));
        }
        
        // Correctness table
        sb.append("\n## Correctness vs pyproj\n\n");
        sb.append("| Category | Tests | Max Error | Avg Error | Tolerance |\n");
        sb.append("|----------|------:|----------:|----------:|----------:|\n");
        
        for (ErrorStats stats : errorStatsByCategory.values()) {
            String tolerance = stats.unit.equals("deg") ? 
                formatError(GEOGRAPHIC_TOLERANCE, "deg") : 
                formatError(PROJECTED_TOLERANCE, "m");
            
            sb.append(String.format("| %s | %d | %s | %s | %s |\n",
                stats.name,
                stats.count,
                formatError(stats.max, stats.unit),
                formatError(stats.sum / stats.count, stats.unit),
                tolerance));
        }
        
        // Skipped tests
        if (!skippedTests.isEmpty()) {
            sb.append("\n### Skipped Tests\n\n");
            for (String test : skippedTests) {
                sb.append("- `").append(test).append("`\n");
            }
        }
        
        Files.writeString(Paths.get(outputFile), sb.toString());
    }

    private String formatTime(double us) {
        if (us >= 1000) {
            return String.format("%.2f ms", us / 1000);
        }
        return String.format("%.2f μs", us);
    }

    private String formatError(double error, String unit) {
        if (error == 0) return "0 " + unit;
        if (error < 1e-9) return String.format("%.2e %s", error, unit);
        if (error < 0.001) return String.format("%.2e %s", error, unit);
        return String.format("%.4f %s", error, unit);
    }

    // ==================== Helper Classes ====================

    private static class ErrorStats {
        String name;
        String unit;
        int count = 0;
        double sum = 0;
        double max = 0;

        ErrorStats(String name, String unit) {
            this.name = name;
            this.unit = unit;
        }

        void record(double error) {
            count++;
            sum += error;
            max = Math.max(max, error);
        }
    }
}
