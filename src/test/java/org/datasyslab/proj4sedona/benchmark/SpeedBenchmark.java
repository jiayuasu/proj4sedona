package org.datasyslab.proj4sedona.benchmark;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.datasyslab.proj4sedona.Proj4;
import org.datasyslab.proj4sedona.core.DatumParams;
import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.core.Proj;
import org.datasyslab.proj4sedona.projection.Krovak;
import org.datasyslab.proj4sedona.projection.Mercator;
import org.datasyslab.proj4sedona.projection.ProjectionParams;
import org.datasyslab.proj4sedona.projection.ProjectionRegistry;
import org.datasyslab.proj4sedona.projection.Stereographic;
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
    private static final double ROBINSON_TOLERANCE = 0.5;     // meters
    /** WKT normalization is deliberately stable to one nanodegree. */
    private static final double ANGLE_SEMANTIC_TOLERANCE = Math.toRadians(1e-9);

    private static final Map<String, String> TRANSFORM_SKIPS;
    private static final Map<String, String> GRID_SKIPS;
    private static final Set<String> PROJECTION_TOLERANCE_OVERRIDES;

    static {
        Map<String, String> transformSkips = new LinkedHashMap<>();
        transformSkips.put("osgb36_to_wgs84",
            "Requires datum-shift parameters not present in the port definitions");
        transformSkips.put("ed50_to_wgs84",
            "Requires datum-shift parameters not present in the port definitions");
        TRANSFORM_SKIPS = Collections.unmodifiableMap(transformSkips);

        Map<String, String> gridSkips = new LinkedHashMap<>();
        gridSkips.put("proj_pipeline_ostn15", "PROJ pipeline syntax is not supported");
        GRID_SKIPS = Collections.unmodifiableMap(gridSkips);

        Set<String> toleranceOverrides = new LinkedHashSet<>();
        toleranceOverrides.add("proj_robin");
        PROJECTION_TOLERANCE_OVERRIDES = Collections.unmodifiableSet(toleranceOverrides);
    }

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
    // Per-projection correctness vs pyproj (reference cases named proj_*), rendered
    // as a dedicated report table so each projection's agreement is visible.
    private final Map<String, ErrorStats> perProjectionStats = new LinkedHashMap<>();
    private final ParityCheck parity = new ParityCheck();
    private final Set<String> usedTransformSkips = new LinkedHashSet<>();
    private final Set<String> usedGridSkips = new LinkedHashSet<>();
    private final Set<String> usedProjectionToleranceOverrides = new LinkedHashSet<>();

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
        parity.finalizeCoverage();
        
        // Generate Markdown report
        System.out.println("\n4. Generating report...");
        generateMarkdownReport(outputFile);
        
        System.out.println("\nReport saved to: " + outputFile);
        parity.throwIfFailed();
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
        
        // Pre-load grid files from test resources for grid correctness tests
        // (must be after setupOstn15 which configures the cache directory)
        loadTestGrids();
    }
    
    private void loadTestGrids() {
        Path gridsDir = Paths.get("src/test/resources/grids");
        String[] gridFiles = {
            "ca_nrc_ntv2_0.tif",      // Canadian NAD27 to NAD83
            "us_noaa_conus.tif",       // US NAD83 to HARN
            "ca_nrc_NA83SCRS.tif"      // Additional Canadian grid
        };
        
        for (String gridFile : gridFiles) {
            Path gridPath = gridsDir.resolve(gridFile);
            if (Files.exists(gridPath)) {
                try {
                    GridLoader.loadFile(gridFile, gridPath);
                    System.out.println("   Loaded grid: " + gridFile);
                } catch (IOException e) {
                    System.out.println("   Failed to load grid " + gridFile + ": " + e.getMessage());
                }
            } else {
                System.out.println("   Grid file not found: " + gridPath);
            }
        }
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

    private void runCorrectnessComparisons() {
        runParitySuite("transform", this::runTransformParity);
        runParitySuite("grid", this::runGridParity);
        runParitySuite("parser", this::runParserParity);
        runParitySuite("serializer", this::runSerializerParity);

        parity.auditDeclaredUses(
            "transform", "skip", TRANSFORM_SKIPS.keySet(), usedTransformSkips);
        parity.auditDeclaredUses("grid", "skip", GRID_SKIPS.keySet(), usedGridSkips);
        parity.auditDeclaredUses(
            "transform", "tolerance override",
            PROJECTION_TOLERANCE_OVERRIDES, usedProjectionToleranceOverrides);
    }

    private void runParitySuite(String suite, CheckedRunnable operation) {
        try {
            operation.run();
        } catch (Exception e) {
            parity.infrastructureFailure(suite, "could not consume reference data: "
                + describeException(e));
        }
    }

    private void runSerializerParity() throws IOException {
        final String suite = "serializer";
        final List<String> formats = Arrays.asList(
            "wkt1", "wkt2", "proj_string", "projjson");
        final List<String> semanticChecks = Arrays.asList(
            "projection_method", "conversion_parameters", "utm_zone_hemisphere",
            "prime_meridian", "linear_unit", "axis", "datum_transform");
        Path refFile = Paths.get("target/pyproj-reference/format_export_reference.json");
        if (!Files.exists(refFile)) {
            parity.infrastructureFailure(suite, "reference file is missing: " + refFile);
            return;
        }

        JsonObject root = readReference(refFile, suite);
        JsonArray cases = requiredArray(root, "test_cases", "serializer root");
        parity.reconcileCount(suite, "test-cases",
            requiredInt(root, "expected_test_case_count", "serializer root"), cases.size());
        List<String> declaredFormats = stringList(
            requiredArray(root, "expected_formats", "serializer root"),
            "serializer formats");
        if (!declaredFormats.equals(formats)) {
            parity.infrastructureFailure(suite,
                "expected_formats must be exactly " + formats + ", but was " + declaredFormats);
        }
        List<String> declaredSemanticChecks = stringList(
            requiredArray(root, "expected_semantic_checks", "serializer root"),
            "serializer semantic checks");
        if (!declaredSemanticChecks.equals(semanticChecks)) {
            parity.infrastructureFailure(suite,
                "expected_semantic_checks must be exactly " + semanticChecks
                    + ", but was " + declaredSemanticChecks);
        }
        int declaredSupportedComparisons = requiredInt(
            root, "expected_supported_comparison_count", "serializer root");
        int declaredRejections = requiredInt(
            root, "expected_rejection_count", "serializer root");
        double declaredTolerance = requiredDouble(root, "tolerance_m", "serializer root");
        String rootError = sameDouble(declaredTolerance, PROJECTED_TOLERANCE) ? null
            : "declared tolerance " + declaredTolerance
                + " does not match audited tolerance " + PROJECTED_TOLERANCE;
        ErrorStats serializer = new ErrorStats("Serializer", "m", PROJECTED_TOLERANCE);
        int supportedComparisons = 0;
        int expectedRejections = 0;

        for (int caseIndex = 0; caseIndex < cases.size(); caseIndex++) {
            JsonObject testCase = requiredObject(cases.get(caseIndex),
                "serializer case " + caseIndex);
            String caseId = requiredString(testCase, "case_id",
                "serializer case " + caseIndex);
            String input = optionalString(testCase, "input", null);
            String caseError = joinErrors(rootError, nullableError(testCase, "error"));
            List<String> javaSupportedFormats = stringList(
                requiredArray(testCase, "java_supported_formats",
                    "serializer case " + caseIndex),
                "serializer case " + caseIndex + " java_supported_formats");
            Set<String> javaSupportedFormatSet = new LinkedHashSet<>(javaSupportedFormats);
            if (javaSupportedFormatSet.size() != javaSupportedFormats.size()) {
                caseError = joinErrors(caseError,
                    "java_supported_formats contains duplicate entries");
            }
            if (!formats.containsAll(javaSupportedFormatSet)) {
                Set<String> unknownFormats = new LinkedHashSet<>(javaSupportedFormatSet);
                unknownFormats.removeAll(formats);
                caseError = joinErrors(caseError,
                    "java_supported_formats contains unknown entries: " + unknownFormats);
            }
            if (input == null || input.trim().isEmpty()) {
                caseError = joinErrors(caseError, "input is missing or empty");
            }
            JsonObject exports = optionalObject(testCase, "exports");
            JsonObject verification = optionalObject(testCase, "round_trip_verification");
            if (exports == null) {
                caseError = joinErrors(caseError, "pyproj exports object is missing");
            }
            if (verification == null) {
                caseError = joinErrors(caseError,
                    "pyproj round-trip verification object is missing");
            }

            Proj original = null;
            String originalError = null;
            if (caseError == null) {
                try {
                    original = new Proj(input);
                    if (!Double.isFinite(original.getA()) || !Double.isFinite(original.getB())) {
                        originalError = "Java input parser produced non-finite ellipsoid axes";
                    }
                } catch (Exception e) {
                    originalError = "Java input parser failed: " + describeException(e);
                }
            }

            for (String format : formats) {
                String id = caseId + "/" + format;
                parity.expect(suite, id);
                boolean javaFormatSupported = javaSupportedFormatSet.contains(format);
                if (javaFormatSupported) {
                    supportedComparisons++;
                } else {
                    expectedRejections++;
                }
                String validationError = caseError;
                if (exports != null) {
                    JsonElement exported = exports.get(format);
                    if (exported == null || exported.isJsonNull()) {
                        validationError = joinErrors(validationError,
                            "pyproj " + format + " export is missing");
                    } else if ("projjson".equals(format) && !exported.isJsonObject()) {
                        validationError = joinErrors(validationError,
                            "pyproj projjson export is malformed");
                    } else if (!"projjson".equals(format) && (!exported.isJsonPrimitive()
                            || exported.getAsString().trim().isEmpty())) {
                        validationError = joinErrors(validationError,
                            "pyproj " + format + " export is empty or malformed");
                    }
                }
                if (verification != null) {
                    JsonObject verified = optionalObject(verification, format);
                    if (verified == null) {
                        validationError = joinErrors(validationError,
                            "pyproj " + format + " round-trip result is missing");
                    } else if (!optionalBoolean(verified, "success", false)) {
                        validationError = joinErrors(validationError,
                            "pyproj " + format + " round-trip failed: "
                                + optionalString(verified, "error", "unknown error"));
                    } else if (isExplicitFalse(verified, "preserves_type")
                            || isExplicitFalse(verified, "preserves_ellipsoid_a")) {
                        validationError = joinErrors(validationError,
                            "pyproj " + format + " round-trip did not preserve CRS properties");
                    }
                }
                validationError = joinErrors(validationError, originalError);
                if (validationError != null) {
                    parity.failed(suite, id, validationError);
                    continue;
                }

                if (!javaFormatSupported) {
                    try {
                        serialize(original, format);
                        parity.failed(suite, id,
                            "Java " + format + " export succeeded, but the fixture requires "
                                + "an explicit unsupported-format rejection");
                    } catch (UnsupportedOperationException expected) {
                        serializer.record(0.0);
                        parity.compared(suite, id, 0.0, PROJECTED_TOLERANCE);
                    } catch (Exception e) {
                        parity.failed(suite, id,
                            "Java " + format + " export did not reject cleanly: "
                                + describeException(e));
                    }
                    continue;
                }

                try {
                    String serialized = serialize(original, format);
                    if (serialized == null || serialized.trim().isEmpty()) {
                        parity.failed(suite, id, "Java " + format + " export is empty");
                        continue;
                    }
                    Proj reparsed = new Proj(serialized);
                    double reparsedA = reparsed.getA();
                    double reparsedB = reparsed.getB();
                    if (!Double.isFinite(reparsedA) || !Double.isFinite(reparsedB)) {
                        parity.failed(suite, id,
                            "Java " + format + " round-trip produced non-finite axes");
                        continue;
                    }
                    String semanticError = serializerSemanticDifference(original, reparsed);
                    if (semanticError != null) {
                        parity.failed(suite, id,
                            "Java " + format + " round-trip changed CRS semantics: "
                                + semanticError);
                        continue;
                    }
                    double error = Math.max(
                        Math.abs(reparsedA - original.getA()),
                        Math.abs(reparsedB - original.getB()));
                    if (Double.isFinite(error)) {
                        serializer.record(error);
                    }
                    parity.compared(suite, id, error, PROJECTED_TOLERANCE);
                } catch (Exception e) {
                    parity.failed(suite, id,
                        "Java " + format + " round-trip failed: " + describeException(e));
                }
            }
        }

        parity.reconcileCount(suite, "comparisons",
            requiredInt(root, "expected_comparison_count", "serializer root"),
            cases.size() * formats.size());
        if (declaredSupportedComparisons != supportedComparisons) {
            parity.infrastructureFailure(suite,
                "expected_supported_comparison_count declares "
                    + declaredSupportedComparisons + ", but " + supportedComparisons
                    + " were present");
        }
        if (declaredRejections != expectedRejections) {
            parity.infrastructureFailure(suite,
                "expected_rejection_count declares " + declaredRejections + ", but "
                    + expectedRejections + " were present");
        }
        putStats("Serializer", serializer);
        System.out.println("   Serializer correctness: " + serializer.count + " comparisons");
    }

    private String serialize(Proj projection, String format) {
        switch (format) {
            case "wkt1":
                return CRSSerializer.toWkt1(projection);
            case "wkt2":
                return CRSSerializer.toWkt2(projection);
            case "proj_string":
                return CRSSerializer.toProjString(projection);
            case "projjson":
                return CRSSerializer.toProjJson(projection);
            default:
                throw new IllegalArgumentException("Unsupported serializer format: " + format);
        }
    }

    /**
     * Compare the CRS properties whose loss can change coordinates while leaving
     * the ellipsoid axes untouched.  Representation-only differences (notably UTM
     * encoded as its defining Transverse Mercator conversion) are normalized.
     */
    static String serializerSemanticDifference(Proj expected, Proj actual) {
        ProjectionParams expectedParams = expected.getParams();
        ProjectionParams actualParams = actual.getParams();
        List<String> differences = new ArrayList<>();

        String expectedMethod = normalizedProjectionMethod(expectedParams);
        String actualMethod = normalizedProjectionMethod(actualParams);
        if (!Objects.equals(expectedMethod, actualMethod)) {
            differences.add("projection method " + expectedMethod + " -> " + actualMethod);
        }
        compareConversionParameters(
            expectedParams, actualParams, expectedMethod, actualMethod, differences);

        UtmSemantics expectedUtm = utmSemantics(expectedParams);
        UtmSemantics actualUtm = utmSemantics(actualParams);
        if (!Objects.equals(expectedUtm, actualUtm)) {
            differences.add("UTM zone/hemisphere " + expectedUtm + " -> " + actualUtm);
        }

        double expectedPrimeMeridian = valueOrZero(expectedParams.fromGreenwich);
        double actualPrimeMeridian = valueOrZero(actualParams.fromGreenwich);
        if (!semanticAngleEquals(expectedPrimeMeridian, actualPrimeMeridian)) {
            differences.add("prime meridian " + expectedPrimeMeridian + " -> "
                + actualPrimeMeridian + " radians");
        }

        if (!"longlat".equals(expectedMethod) || !"longlat".equals(actualMethod)) {
            double expectedToMeter = effectiveToMeter(expectedParams);
            double actualToMeter = effectiveToMeter(actualParams);
            if (!semanticDoubleEquals(expectedToMeter, actualToMeter)) {
                differences.add("linear unit factor " + expectedToMeter + " -> "
                    + actualToMeter);
            }
        }

        String expectedAxis = effectiveAxis(expectedParams);
        String actualAxis = effectiveAxis(actualParams);
        if (!expectedAxis.equals(actualAxis)) {
            differences.add("axis " + expectedAxis + " -> " + actualAxis);
        }

        String datumDifference = datumTransformDifference(
            expectedParams.datum, actualParams.datum);
        if (datumDifference != null) {
            differences.add(datumDifference);
        }

        return differences.isEmpty() ? null : String.join("; ", differences);
    }

    private static void compareConversionParameters(
            ProjectionParams expected, ProjectionParams actual,
            String expectedMethod, String actualMethod, List<String> differences) {
        if (!Objects.equals(expectedMethod, actualMethod)
                || "longlat".equals(expectedMethod)) {
            return;
        }

        compareAngle("latitude of origin", expected.getLat0(), actual.getLat0(), differences);
        compareAngle("central meridian", expected.getLong0(), actual.getLong0(), differences);

        if (expected.lat1 != null || actual.lat1 != null) {
            compareAngle("first standard parallel",
                expected.getLat1(), actual.getLat1(), differences);
        }
        if (expected.lat2 != null || actual.lat2 != null) {
            compareAngle("second standard parallel",
                expected.getLat2(), actual.getLat2(), differences);
        }

        String baseMethod = expectedMethod == null
            ? null : expectedMethod.replace(":approx", "");
        // For Mercator and polar stereographic, lat_ts and k0 are interchangeable
        // parameterizations of the same scale.  Compare their resolved scale below
        // instead of rejecting a lossless method-variant conversion.
        if (!"merc".equals(baseMethod) && !"stere".equals(baseMethod)
                && (expected.latTs != null || actual.latTs != null
                    || "cea".equals(baseMethod) || "eqc".equals(baseMethod))) {
            compareAngle("latitude of true scale",
                effectiveLatitudeOfTrueScale(baseMethod, expected),
                effectiveLatitudeOfTrueScale(baseMethod, actual), differences);
        }

        double expectedScale = effectiveScaleFactor(expectedMethod, expected);
        double actualScale = effectiveScaleFactor(actualMethod, actual);
        if (!semanticDoubleEquals(expectedScale, actualScale)) {
            differences.add("effective scale factor " + expectedScale + " -> "
                + actualScale);
        }
        if (Math.abs(expected.x0 - actual.x0) > PROJECTED_TOLERANCE) {
            differences.add("false easting " + expected.x0 + " -> " + actual.x0
                + " metres");
        }
        if (Math.abs(expected.y0 - actual.y0) > PROJECTED_TOLERANCE) {
            differences.add("false northing " + expected.y0 + " -> " + actual.y0
                + " metres");
        }
    }

    private static void compareAngle(
            String label, double expected, double actual, List<String> differences) {
        if (!semanticAngleEquals(expected, actual)) {
            differences.add(label + " " + expected + " -> " + actual + " radians");
        }
    }

    private static boolean semanticAngleEquals(double first, double second) {
        return Double.isFinite(first) && Double.isFinite(second)
            && Math.abs(first - second) <= ANGLE_SEMANTIC_TOLERANCE;
    }

    private static double effectiveLatitudeOfTrueScale(
            String method, ProjectionParams params) {
        if (("cea".equals(method) || "eqc".equals(method)) && params.latTs == null) {
            return 0.0;
        }
        return valueOrZero(params.latTs);
    }

    private static double effectiveScaleFactor(String method, ProjectionParams params) {
        String baseMethod = method == null ? null : method.replace(":approx", "");
        if ("merc".equals(baseMethod)) {
            return Mercator.resolveScaleFactor(params);
        }
        if ("stere".equals(baseMethod)) {
            return Stereographic.resolveScaleFactor(params);
        }
        if ("krovak".equals(baseMethod)) {
            return Krovak.resolveScaleFactor(params);
        }
        if ("cea".equals(baseMethod) || "eqc".equals(baseMethod)) {
            return 1.0;
        }
        if ("lcc".equals(baseMethod) || "gstmerc".equals(baseMethod)
                || "omerc".equals(baseMethod) || "somerc".equals(baseMethod)
                || "sterea".equals(baseMethod) || "gnom".equals(baseMethod)
                || "tmerc:approx".equals(method)) {
            return params.getK0OrDefault(1.0);
        }
        return params.k0;
    }

    private static String normalizedProjectionMethod(ProjectionParams params) {
        String code = ProjectionRegistry.resolveProjCode(params.projName);
        if (code == null) {
            code = params.projName == null ? null
                : ProjectionRegistry.getNormalizedProjName(
                    params.projName.toLowerCase(Locale.ROOT));
        }
        // UTM is a constrained Transverse Mercator conversion in WKT and PROJJSON.
        if ("utm".equals(code)) {
            code = "tmerc";
        }
        if ("tmerc".equals(code) && Boolean.TRUE.equals(params.approx)) {
            return "tmerc:approx";
        }
        return code;
    }

    private static UtmSemantics utmSemantics(ProjectionParams params) {
        String method = normalizedProjectionMethod(params);
        if (method == null || !method.startsWith("tmerc")) {
            return null;
        }
        if (!semanticDoubleEquals(params.getLat0(), 0.0)
                || !semanticDoubleEquals(params.k0, 0.9996)
                || Math.abs(params.x0 - 500000.0) > PROJECTED_TOLERANCE
                || (Math.abs(params.y0) > PROJECTED_TOLERANCE
                    && Math.abs(params.y0 - 10000000.0) > PROJECTED_TOLERANCE)) {
            return null;
        }

        double longitudeDegrees = Math.toDegrees(params.getLong0());
        int derivedZone = (int) Math.round((longitudeDegrees + 183.0) / 6.0);
        if (derivedZone < 1 || derivedZone > 60
                || Math.abs(longitudeDegrees - (derivedZone * 6.0 - 183.0)) > 1e-9) {
            return null;
        }
        int zone = params.zone != null ? Math.abs(params.zone) : derivedZone;
        if (zone != derivedZone) {
            return null;
        }
        boolean south = Boolean.TRUE.equals(params.utmSouth)
            || Math.abs(params.y0 - 10000000.0) <= PROJECTED_TOLERANCE;
        return new UtmSemantics(zone, south);
    }

    private static double effectiveToMeter(ProjectionParams params) {
        return params.toMeter != null ? params.toMeter : 1.0;
    }

    private static String effectiveAxis(ProjectionParams params) {
        return params.axis == null ? "enu" : params.axis.toLowerCase(Locale.ROOT);
    }

    private static String datumTransformDifference(DatumParams expected, DatumParams actual) {
        String expectedGrid = meaningfulGrid(expected);
        String actualGrid = meaningfulGrid(actual);
        if (!Objects.equals(expectedGrid, actualGrid)) {
            return "datum grid " + expectedGrid + " -> " + actualGrid;
        }

        double[] expectedValues = meaningfulDatumParameters(expected);
        double[] actualValues = meaningfulDatumParameters(actual);
        if (expectedValues.length != actualValues.length) {
            return "datum transform " + Arrays.toString(expectedValues) + " -> "
                + Arrays.toString(actualValues);
        }
        for (int i = 0; i < expectedValues.length; i++) {
            if (!semanticDoubleEquals(expectedValues[i], actualValues[i])) {
                return "datum transform " + Arrays.toString(expectedValues) + " -> "
                    + Arrays.toString(actualValues);
            }
        }
        return null;
    }

    private static String meaningfulGrid(DatumParams datum) {
        if (datum == null || datum.getNadgrids() == null) {
            return null;
        }
        String value = datum.getNadgrids().trim();
        return value.matches("(?i)@?null") ? null : value;
    }

    private static double[] meaningfulDatumParameters(DatumParams datum) {
        if (datum == null || datum.getDatumParams() == null) {
            return new double[0];
        }
        double[] values = datum.getDatumParams();
        for (double value : values) {
            if (!semanticDoubleEquals(value, 0.0)) {
                return values;
            }
        }
        return new double[0];
    }

    private static double valueOrZero(Double value) {
        return value == null ? 0.0 : value;
    }

    private static boolean semanticDoubleEquals(double first, double second) {
        if (!Double.isFinite(first) || !Double.isFinite(second)) {
            return Double.doubleToLongBits(first) == Double.doubleToLongBits(second);
        }
        double scale = Math.max(1.0, Math.max(Math.abs(first), Math.abs(second)));
        return Math.abs(first - second) <= 1e-12 * scale;
    }

    private static final class UtmSemantics {
        private final int zone;
        private final boolean south;

        private UtmSemantics(int zone, boolean south) {
            this.zone = zone;
            this.south = south;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof UtmSemantics)) {
                return false;
            }
            UtmSemantics that = (UtmSemantics) other;
            return zone == that.zone && south == that.south;
        }

        @Override
        public int hashCode() {
            return Objects.hash(zone, south);
        }

        @Override
        public String toString() {
            return zone + (south ? "S" : "N");
        }
    }

    private void runParserParity() throws IOException {
        final String suite = "parser";
        Path refFile = Paths.get("target/pyproj-reference/parsing_reference.json");
        if (!Files.exists(refFile)) {
            parity.infrastructureFailure(suite, "reference file is missing: " + refFile);
            return;
        }

        JsonObject root = readReference(refFile, suite);
        JsonObject declaredCounts = requiredObject(root, "expected_case_counts", "parser root");
        double tolerance = requiredDouble(root, "tolerance_m", "parser root");
        String rootError = sameDouble(tolerance, PROJECTED_TOLERANCE) ? null
            : "declared tolerance " + tolerance
                + " does not match audited tolerance " + PROJECTED_TOLERANCE;
        ErrorStats parser = new ErrorStats("Parser (ellipsoid)", "m", PROJECTED_TOLERANCE);

        JsonArray epsgCases = requiredArray(root, "epsg_test_cases", "parser root");
        JsonArray projCases = requiredArray(root, "proj_string_test_cases", "parser root");
        JsonArray wktCases = requiredArray(root, "wkt_test_cases", "parser root");
        parity.reconcileCount(suite, "epsg",
            requiredInt(declaredCounts, "epsg", "parser counts"), epsgCases.size());
        parity.reconcileCount(suite, "proj-string",
            requiredInt(declaredCounts, "proj_string", "parser counts"), projCases.size());
        parity.reconcileCount(suite, "wkt",
            requiredInt(declaredCounts, "wkt", "parser counts"), wktCases.size());

        consumeParserRows(epsgCases, rootError, parser);
        consumeParserRows(projCases, rootError, parser);
        consumeParserRows(wktCases, rootError, parser);
        int actualTotal = epsgCases.size() + projCases.size() + wktCases.size();
        parity.reconcileCount(suite, "total",
            requiredInt(declaredCounts, "total", "parser counts"), actualTotal);

        putStats("Parser (ellipsoid)", parser);
        System.out.println("   Parser correctness: " + parser.count + " comparisons");
    }

    private void consumeParserRows(
            JsonArray rows, String rootError, ErrorStats stats) {
        final String suite = "parser";
        for (int index = 0; index < rows.size(); index++) {
            JsonObject row = requiredObject(rows.get(index), "parser row " + index);
            String id = requiredString(row, "case_id", "parser row " + index);
            parity.expect(suite, id);

            String validationError = joinErrors(rootError, nullableError(row, "error"));
            String input = optionalString(row, "input", null);
            if (input == null || input.trim().isEmpty()) {
                validationError = joinErrors(validationError, "input is missing or empty");
            }
            JsonObject parsed = optionalObject(row, "parsed_params");
            JsonObject ellipsoid = parsed == null
                ? null : optionalObject(parsed, "effective_ellipsoid");
            if (ellipsoid == null) {
                validationError = joinErrors(validationError,
                    "reference effective ellipsoid is missing");
            }

            double expectedA = Double.NaN;
            double expectedB = Double.NaN;
            if (ellipsoid != null) {
                expectedA = optionalFiniteDouble(ellipsoid, "semi_major_metre");
                expectedB = optionalFiniteDouble(ellipsoid, "semi_minor_metre");
                if (!Double.isFinite(expectedA) || !Double.isFinite(expectedB)
                        || expectedA <= 0 || expectedB <= 0) {
                    validationError = joinErrors(validationError,
                        "reference ellipsoid axes are missing, non-finite, or non-positive");
                }
            }
            if (validationError != null) {
                parity.failed(suite, id, validationError);
                continue;
            }

            try {
                Proj actual = new Proj(input);
                double actualA = actual.getA();
                double actualB = actual.getB();
                if (!Double.isFinite(actualA) || !Double.isFinite(actualB)) {
                    parity.failed(suite, id, "Java parser produced non-finite ellipsoid axes");
                    continue;
                }
                double error = Math.max(
                    Math.abs(actualA - expectedA), Math.abs(actualB - expectedB));
                if (Double.isFinite(error)) {
                    stats.record(error);
                }
                parity.compared(suite, id, error, PROJECTED_TOLERANCE);
            } catch (Exception e) {
                parity.failed(suite, id, "Java parser failed: " + describeException(e));
            }
        }
    }

    private void runGridParity() throws IOException {
        final String suite = "grid";
        Path refFile = Paths.get("target/pyproj-reference/grid_transform_reference.json");
        if (!Files.exists(refFile)) {
            parity.infrastructureFailure(suite, "reference file is missing: " + refFile);
            return;
        }

        JsonObject root = readReference(refFile, suite);
        JsonArray cases = requiredArray(root, "test_cases", "grid root");
        parity.reconcileCount(suite, "test-cases",
            requiredInt(root, "expected_test_case_count", "grid root"), cases.size());
        ErrorStats grid = new ErrorStats("Grid transforms", "deg", GEOGRAPHIC_TOLERANCE);
        int rowCount = 0;

        for (int caseIndex = 0; caseIndex < cases.size(); caseIndex++) {
            JsonObject testCase = requiredObject(cases.get(caseIndex), "grid case " + caseIndex);
            String name = requiredString(testCase, "name", "grid case " + caseIndex);
            double tolerance = requiredDouble(testCase, "tolerance_deg", name);
            String caseError = null;
            if (!sameDouble(tolerance, GEOGRAPHIC_TOLERANCE)) {
                caseError = "declared tolerance " + tolerance
                    + " does not match audited tolerance " + GEOGRAPHIC_TOLERANCE;
            }

            JsonObject result = requiredObject(testCase, "transform_result", name);
            caseError = joinErrors(caseError, nullableError(result, "error"));
            String fromCrs = requiredString(result, "from_crs", name);
            String toCrs = requiredString(result, "to_crs", name);
            JsonArray rows = requiredArray(result, "transformations", name);
            rowCount += rows.size();
            int caseExpected = requiredInt(testCase,
                "expected_transformation_count", name);
            parity.reconcileCount(suite, name, caseExpected, rows.size());
            int resultExpected = requiredInt(result,
                "expected_transformation_count", name + " transform result");
            if (caseExpected != resultExpected) {
                caseError = joinErrors(caseError,
                    "case and transform-result declared counts disagree");
            }

            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                JsonObject row = requiredObject(rows.get(rowIndex), name + " row " + rowIndex);
                String pointId = requiredString(row, "point_id", name + " row " + rowIndex);
                String id = name + "/" + pointId;
                parity.expect(suite, id);

                String validationError = joinErrors(caseError, nullableError(row, "error"));
                JsonObject input = optionalObject(row, "input");
                JsonObject expected = optionalObject(row, "output");
                validationError = joinErrors(validationError,
                    validateCoordinatePair(input, "input"));
                validationError = joinErrors(validationError,
                    validateCoordinatePair(expected, "reference output"));
                if (validationError != null) {
                    parity.failed(suite, id, validationError);
                    continue;
                }

                if (GRID_SKIPS.containsKey(name)) {
                    usedGridSkips.add(name);
                    parity.skipped(suite, id, GRID_SKIPS.get(name));
                    continue;
                }

                try {
                    Point actual = Proj4.proj4(fromCrs, toCrs,
                        new Point(input.get("x").getAsDouble(), input.get("y").getAsDouble()));
                    String actualError = validatePoint(actual);
                    if (actualError != null) {
                        parity.failed(suite, id, actualError);
                        continue;
                    }
                    double error = Math.max(
                        Math.abs(actual.x - expected.get("x").getAsDouble()),
                        Math.abs(actual.y - expected.get("y").getAsDouble()));
                    if (Double.isFinite(error)) {
                        grid.record(error);
                    }
                    parity.compared(suite, id, error, tolerance);
                } catch (Exception e) {
                    parity.failed(suite, id,
                        "Java grid transform failed: " + describeException(e));
                }
            }
        }

        parity.reconcileCount(suite, "transformations",
            requiredInt(root, "expected_transformation_count", "grid root"), rowCount);
        putStats("Grid transforms", grid);
        System.out.println("   Grid correctness: " + grid.count + " comparisons");
    }

    private void runTransformParity() throws IOException {
        final String suite = "transform";
        Path refFile = Paths.get("target/pyproj-reference/transform_reference.json");
        if (!Files.exists(refFile)) {
            parity.infrastructureFailure(suite, "reference file is missing: " + refFile);
            return;
        }

        JsonObject root = readReference(refFile, suite);
        JsonArray cases = requiredArray(root, "test_cases", "transform root");
        parity.reconcileCount(suite, "test-cases",
            requiredInt(root, "expected_test_case_count", "transform root"), cases.size());

        ErrorStats geographic = new ErrorStats(
            "Geographic transforms", "deg", GEOGRAPHIC_TOLERANCE);
        ErrorStats projected = new ErrorStats(
            "Projected transforms", "m", PROJECTED_TOLERANCE);
        int rowCount = 0;

        for (int caseIndex = 0; caseIndex < cases.size(); caseIndex++) {
            JsonObject testCase = requiredObject(cases.get(caseIndex),
                "transform case " + caseIndex);
            String name = requiredString(testCase, "name", "transform case " + caseIndex);
            String fromCrs = requiredString(testCase, "from_crs", name);
            String toCrs = requiredString(testCase, "to_crs", name);
            JsonArray rows = requiredArray(testCase, "transformations", name);
            rowCount += rows.size();
            parity.reconcileCount(suite, name,
                requiredInt(testCase, "expected_transformation_count", name), rows.size());

            double tolerance = toleranceForCrs(toCrs);
            boolean isProjected = sameDouble(tolerance, PROJECTED_TOLERANCE);
            ErrorStats stats;
            String caseError = nullableError(testCase, "error");
            if (name.startsWith("proj_")) {
                String description = optionalString(testCase, "description", name);
                double declaredTolerance = requiredDouble(testCase, "tolerance_m", name);
                double auditedTolerance = PROJECTED_TOLERANCE;
                if (PROJECTION_TOLERANCE_OVERRIDES.contains(name)) {
                    usedProjectionToleranceOverrides.add(name);
                    auditedTolerance = ROBINSON_TOLERANCE;
                }
                if (!sameDouble(declaredTolerance, auditedTolerance)) {
                    caseError = joinErrors(caseError, "declared tolerance " + declaredTolerance
                        + " does not match audited tolerance " + auditedTolerance);
                }
                tolerance = auditedTolerance;
                final double statsTolerance = tolerance;
                stats = perProjectionStats.computeIfAbsent(name,
                    ignored -> new ErrorStats(description, "m", statsTolerance));
            } else {
                stats = isProjected ? projected : geographic;
            }

            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                JsonObject row = requiredObject(rows.get(rowIndex),
                    name + " row " + rowIndex);
                String coordinateId = requiredString(row, "coordinate_id",
                    name + " row " + rowIndex);
                String id = name + "/" + coordinateId;
                parity.expect(suite, id);

                String validationError = joinErrors(caseError, nullableError(row, "error"));
                JsonObject input = optionalObject(row, "input");
                JsonObject expected = optionalObject(row, "output");
                validationError = joinErrors(validationError,
                    validateCoordinatePair(input, "input"));
                validationError = joinErrors(validationError,
                    validateCoordinatePair(expected, "reference output"));
                if (validationError != null) {
                    parity.failed(suite, id, validationError);
                    continue;
                }

                if (TRANSFORM_SKIPS.containsKey(name)) {
                    usedTransformSkips.add(name);
                    parity.skipped(suite, id, TRANSFORM_SKIPS.get(name));
                    continue;
                }

                try {
                    Point actual = Proj4.proj4(fromCrs, toCrs,
                        new Point(input.get("x").getAsDouble(), input.get("y").getAsDouble()));
                    String actualError = validatePoint(actual);
                    if (actualError != null) {
                        parity.failed(suite, id, actualError);
                        continue;
                    }
                    double error = Math.max(
                        Math.abs(actual.x - expected.get("x").getAsDouble()),
                        Math.abs(actual.y - expected.get("y").getAsDouble()));
                    if (Double.isFinite(error)) {
                        stats.record(error);
                    }
                    parity.compared(suite, id, error, tolerance);
                } catch (Exception e) {
                    parity.failed(suite, id,
                        "Java transform failed: " + describeException(e));
                }
            }
        }

        parity.reconcileCount(suite, "transformations",
            requiredInt(root, "expected_transformation_count", "transform root"), rowCount);
        putStats("Geographic transforms", geographic);
        putStats("Projected transforms", projected);
        int projectionCount = perProjectionStats.values().stream()
            .mapToInt(stats -> stats.count).sum();
        System.out.println("   Transform correctness: "
            + (geographic.count + projected.count) + " comparisons, "
            + projectionCount + " per-projection comparisons across "
            + perProjectionStats.size() + " projections");
    }

    private JsonObject readReference(Path path, String suite) throws IOException {
        Gson gson = new Gson();
        JsonObject root;
        try (Reader reader = Files.newBufferedReader(path)) {
            root = gson.fromJson(reader, JsonObject.class);
        }
        if (root == null) {
            throw new IllegalArgumentException("reference root is null");
        }
        String version = requiredString(root, "version", suite + " root");
        String expectedVersion = "serializer".equals(suite) ? "1.2" : "1.1";
        if (!expectedVersion.equals(version)) {
            throw new IllegalArgumentException(
                "unsupported reference schema " + version + " (expected "
                    + expectedVersion + ")");
        }
        return root;
    }

    private JsonObject requiredObject(JsonElement value, String context) {
        if (value == null || value.isJsonNull() || !value.isJsonObject()) {
            throw new IllegalArgumentException(context + " must be a JSON object");
        }
        return value.getAsJsonObject();
    }

    private JsonObject requiredObject(JsonObject owner, String field, String context) {
        return requiredObject(owner == null ? null : owner.get(field),
            context + "." + field);
    }

    private JsonObject optionalObject(JsonObject owner, String field) {
        if (owner == null) {
            return null;
        }
        JsonElement value = owner.get(field);
        return value == null || value.isJsonNull() || !value.isJsonObject()
            ? null : value.getAsJsonObject();
    }

    private JsonArray requiredArray(JsonObject owner, String field, String context) {
        JsonElement value = owner == null ? null : owner.get(field);
        if (value == null || value.isJsonNull() || !value.isJsonArray()) {
            throw new IllegalArgumentException(context + "." + field
                + " must be a JSON array");
        }
        return value.getAsJsonArray();
    }

    private String requiredString(JsonObject owner, String field, String context) {
        String value = optionalString(owner, field, null);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(context + "." + field
                + " must be a non-empty string");
        }
        return value;
    }

    private String optionalString(JsonObject owner, String field, String defaultValue) {
        if (owner == null) {
            return defaultValue;
        }
        JsonElement value = owner.get(field);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return defaultValue;
        }
        try {
            return value.getAsString();
        } catch (RuntimeException e) {
            return defaultValue;
        }
    }

    static int requiredInt(JsonObject owner, String field, String context) {
        JsonElement value = owner == null ? null : owner.get(field);
        try {
            if (value == null || value.isJsonNull() || !value.isJsonPrimitive()
                    || !value.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException();
            }
            return value.getAsBigDecimal().intValueExact();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(context + "." + field
                + " must be an integer", e);
        }
    }

    private double requiredDouble(JsonObject owner, String field, String context) {
        double value = optionalFiniteDouble(owner, field);
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(context + "." + field
                + " must be a finite number");
        }
        return value;
    }

    private double optionalFiniteDouble(JsonObject owner, String field) {
        if (owner == null) {
            return Double.NaN;
        }
        JsonElement value = owner.get(field);
        try {
            if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
                return Double.NaN;
            }
            double number = value.getAsDouble();
            return Double.isFinite(number) ? number : Double.NaN;
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }

    private boolean optionalBoolean(JsonObject owner, String field, boolean defaultValue) {
        if (owner == null) {
            return defaultValue;
        }
        JsonElement value = owner.get(field);
        try {
            return value == null || value.isJsonNull() || !value.isJsonPrimitive()
                ? defaultValue : value.getAsBoolean();
        } catch (RuntimeException e) {
            return defaultValue;
        }
    }

    private boolean isExplicitFalse(JsonObject owner, String field) {
        return owner.has(field) && !owner.get(field).isJsonNull()
            && !optionalBoolean(owner, field, true);
    }

    private List<String> stringList(JsonArray values, String context) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            JsonElement value = values.get(i);
            if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
                throw new IllegalArgumentException(context + " entry " + i
                    + " must be a string");
            }
            result.add(value.getAsString());
        }
        return result;
    }

    private String nullableError(JsonObject object, String field) {
        if (object == null) {
            return null;
        }
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        String message = value.isJsonPrimitive() ? value.getAsString() : value.toString();
        return "reference error: " + message;
    }

    private String validateCoordinatePair(JsonObject point, String label) {
        if (point == null) {
            return label + " is missing or malformed";
        }
        double x = optionalFiniteDouble(point, "x");
        double y = optionalFiniteDouble(point, "y");
        return Double.isFinite(x) && Double.isFinite(y)
            ? null : label + " contains a missing or non-finite coordinate";
    }

    private String validatePoint(Point point) {
        if (point == null) {
            return "Java transform returned null";
        }
        if (!Double.isFinite(point.x) || !Double.isFinite(point.y)) {
            return "Java transform returned a non-finite coordinate";
        }
        return null;
    }

    private String joinErrors(String first, String second) {
        if (first == null || first.trim().isEmpty()) {
            return second == null || second.trim().isEmpty() ? null : second;
        }
        if (second == null || second.trim().isEmpty()) {
            return first;
        }
        return first + "; " + second;
    }

    private boolean sameDouble(double first, double second) {
        return Double.doubleToLongBits(first) == Double.doubleToLongBits(second);
    }

    private String describeException(Exception exception) {
        String message = exception.getMessage();
        return exception.getClass().getSimpleName()
            + (message == null || message.trim().isEmpty() ? "" : ": " + message);
    }

    private void putStats(String key, ErrorStats stats) {
        if (stats.count > 0) {
            errorStatsByCategory.put(key, stats);
        }
    }

    static boolean isProjectedCrs(String crs) {
        String projectionName = new Proj(crs).getParams().projName;
        if (projectionName == null || projectionName.trim().isEmpty()) {
            throw new IllegalArgumentException("CRS has no projection name: " + crs);
        }
        return !"longlat".equalsIgnoreCase(projectionName);
    }

    static double toleranceForCrs(String crs) {
        return isProjectedCrs(crs) ? PROJECTED_TOLERANCE : GEOGRAPHIC_TOLERANCE;
    }

    // ==================== Report Generation ====================

    private void generateMarkdownReport(String outputFile) throws IOException {
        StringBuilder sb = new StringBuilder();
        
        sb.append("# proj4sedona Benchmark Report\n\n");
        sb.append("Generated: ").append(LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");
        sb.append("Parity status: **")
            .append(parity.hasFailures() ? "FAILED" : "PASSED")
            .append("**\n\n");
        
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
        sb.append("| Category | Tests | Max Error | Avg Error | Tolerance | Status |\n");
        sb.append("|----------|------:|----------:|----------:|----------:|:------:|\n");
        
        for (ErrorStats stats : errorStatsByCategory.values()) {
            sb.append(String.format("| %s | %d | %s | %s | %s | %s |\n",
                stats.name,
                stats.count,
                formatError(stats.max, stats.unit),
                formatError(stats.sum / stats.count, stats.unit),
                formatError(stats.tolerance, stats.unit),
                stats.max <= stats.tolerance ? "PASS" : "FAIL"));
        }
        
        // Per-projection correctness table
        if (!perProjectionStats.isEmpty()) {
            sb.append("\n### Per-projection correctness (vs pyproj)\n\n");
            sb.append("| Projection | Points | Max Error | Avg Error | Tolerance | Status |\n");
            sb.append("|------------|-------:|----------:|----------:|----------:|:------:|\n");
            for (Map.Entry<String, ErrorStats> e : perProjectionStats.entrySet()) {
                ErrorStats stats = e.getValue();
                if (stats.count == 0) {
                    continue;
                }
                sb.append(String.format("| %s | %d | %s | %s | %s | %s |\n",
                    stats.name,
                    stats.count,
                    formatError(stats.max, stats.unit),
                    formatError(stats.sum / stats.count, stats.unit),
                    formatError(stats.tolerance, stats.unit),
                    stats.max <= stats.tolerance ? "PASS" : "FAIL"));
            }
        }

        sb.append("\n### Reference coverage\n\n");
        sb.append("| Suite | Expected | Compared | Skipped | Failed | Mismatches |\n");
        sb.append("|-------|---------:|---------:|--------:|-------:|-----------:|\n");
        for (Map.Entry<String, ParityCheck.Coverage> entry
                : parity.coverageBySuite().entrySet()) {
            ParityCheck.Coverage coverage = entry.getValue();
            sb.append(String.format("| %s | %d | %d | %d | %d | %d |\n",
                entry.getKey(), coverage.expected(), coverage.compared(),
                coverage.skipped(), coverage.failed(), coverage.mismatches()));
        }

        if (!parity.skips().isEmpty()) {
            sb.append("\n### Explicit skips\n\n");
            for (String test : parity.skips()) {
                sb.append("- `").append(test).append("`\n");
            }
        }

        if (!parity.failures().isEmpty()) {
            sb.append("\n### Failures\n\n");
            for (String failure : parity.failures()) {
                sb.append("- `").append(failure.replace("`", "\\`")).append("`\n");
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

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }

    private static class ErrorStats {
        final String name;
        final String unit;
        final double tolerance;
        int count = 0;
        double sum = 0;
        double max = 0;

        ErrorStats(String name, String unit, double tolerance) {
            this.name = name;
            this.unit = unit;
            this.tolerance = tolerance;
        }

        void record(double error) {
            count++;
            sum += error;
            max = Math.max(max, error);
        }
    }
}
