package org.datasyslab.proj4sedona.benchmark;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.datasyslab.proj4sedona.Proj4;
import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.core.Proj;
import org.datasyslab.proj4sedona.grid.GridLoader;
import org.datasyslab.proj4sedona.parser.CRSSerializer;
import org.datasyslab.proj4sedona.transform.Converter;

import java.nio.file.Files;
import java.nio.file.Path;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * JMH speed benchmarks for performance comparison with pyproj.
 * 
 * <p>This class provides benchmarks that match the pyproj benchmark scenarios
 * for fair comparison between proj4sedona and pyproj.</p>
 * 
 * <p>Benchmark categories:</p>
 * <ul>
 *   <li>CRS initialization</li>
 *   <li>Single point transformation</li>
 *   <li>Batch transformation</li>
 *   <li>CRS export</li>
 *   <li>OSTN15 grid-based transformation (ETRS89 to OSGB36)</li>
 * </ul>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class SpeedBenchmark {

    // Pre-initialized objects
    private Proj wgs84;
    private Proj webMercator;
    private Proj utm32n;
    private Converter wgs84ToMerc;
    private Converter wgs84ToUtm;
    
    // OSTN15 objects (ETRS89 to OSGB36 and inverse)
    private Proj etrs89;
    private Proj osgb36;
    private Converter ostn15Converter;
    private Converter ostn15ConverterInverse;
    private boolean ostn15Available = false;

    // Test coordinates (Washington DC)
    private Point testPoint;
    private double[] testCoords;
    
    // Test coordinates for GB (London in ETRS89)
    private Point testPointGb;
    private double[] testCoordsGb;
    
    // Test coordinates for GB (London in OSGB36)
    private Point testPointGbOsgb;
    private double[] testCoordsGbOsgb;

    // Batch test data (1000 points)
    private double[][] batchCoords;
    private double[] flatCoords;
    
    // Batch test data for GB (1000 points across Great Britain in ETRS89)
    private double[][] batchCoordsGb;
    private double[] flatCoordsGb;
    
    // Batch test data for GB (1000 points in OSGB36, pre-transformed)
    private double[][] batchCoordsGbOsgb;
    private double[] flatCoordsGbOsgb;

    @Setup(Level.Trial)
    public void setup() {
        // Initialize projections
        wgs84 = new Proj("+proj=longlat +datum=WGS84");
        webMercator = new Proj("EPSG:3857");
        utm32n = new Proj("EPSG:32632");

        // Create converters
        wgs84ToMerc = new Converter(wgs84, webMercator);
        wgs84ToUtm = new Converter(wgs84, utm32n);

        // Test data (Washington DC)
        testPoint = new Point(-77.0369, 38.9072);
        testCoords = new double[]{-77.0369, 38.9072};
        
        // Test data for GB (London in ETRS89)
        testPointGb = new Point(-0.1276, 51.5074);
        testCoordsGb = new double[]{-0.1276, 51.5074};
        
        // Test data for GB (London in OSGB36 - pre-transformed values)
        testPointGbOsgb = new Point(-0.12602, 51.50689);
        testCoordsGbOsgb = new double[]{-0.12602, 51.50689};

        // Batch test data (1000 points)
        Random rand = new Random(42);
        batchCoords = new double[1000][2];
        flatCoords = new double[2000];
        for (int i = 0; i < 1000; i++) {
            double lon = -180 + rand.nextDouble() * 360;
            double lat = -80 + rand.nextDouble() * 160;
            batchCoords[i][0] = lon;
            batchCoords[i][1] = lat;
            flatCoords[i * 2] = lon;
            flatCoords[i * 2 + 1] = lat;
        }
        
        // Batch test data for GB (1000 points across Great Britain)
        batchCoordsGb = new double[1000][2];
        flatCoordsGb = new double[2000];
        for (int i = 0; i < 1000; i++) {
            double lon = -8.0 + rand.nextDouble() * 10.0;  // -8 to 2 (GB longitude range)
            double lat = 49.5 + rand.nextDouble() * 11.5;  // 49.5 to 61 (GB latitude range)
            batchCoordsGb[i][0] = lon;
            batchCoordsGb[i][1] = lat;
            flatCoordsGb[i * 2] = lon;
            flatCoordsGb[i * 2 + 1] = lat;
        }
        
        // Initialize OSTN15 (requires grid file)
        setupOstn15();
    }
    
    /**
     * Setup OSTN15 transformation (ETRS89 to OSGB36 using OSTN15 grid).
     * This requires the uk_os_OSTN15_NTv2_OSGBtoETRS.tif grid file.
     */
    private void setupOstn15() {
        try {
            // Try to load OSTN15 grid from CDN
            Path tempCacheDir = Files.createTempDirectory("proj4sedona-benchmark-cache");
            GridLoader.setCacheDirectory(tempCacheDir);
            GridLoader.setAutoFetch(true);
            
            String gridName = "uk_os_OSTN15_NTv2_OSGBtoETRS.tif";
            if (!GridLoader.has(gridName)) {
                System.out.println("Fetching OSTN15 grid from CDN for benchmarks...");
                GridLoader.fetchFromCdn(gridName);
            }
            
            // Initialize OSTN15 projections using explicit PROJ string with nadgrids
            String etrs89Proj = "+proj=longlat +ellps=GRS80 +no_defs";
            String osgb36Proj = "+proj=longlat +ellps=airy +nadgrids=@" + gridName + " +no_defs";
            
            etrs89 = new Proj(etrs89Proj);
            osgb36 = new Proj(osgb36Proj);
            ostn15Converter = new Converter(etrs89, osgb36);
            ostn15ConverterInverse = new Converter(osgb36, etrs89);
            ostn15Available = true;
            
            // Pre-transform batch coordinates to OSGB36 for inverse benchmarks
            batchCoordsGbOsgb = new double[batchCoordsGb.length][2];
            flatCoordsGbOsgb = new double[flatCoordsGb.length];
            for (int i = 0; i < batchCoordsGb.length; i++) {
                Point transformed = ostn15Converter.forward(new Point(batchCoordsGb[i][0], batchCoordsGb[i][1]));
                batchCoordsGbOsgb[i][0] = transformed.x;
                batchCoordsGbOsgb[i][1] = transformed.y;
                flatCoordsGbOsgb[i * 2] = transformed.x;
                flatCoordsGbOsgb[i * 2 + 1] = transformed.y;
            }
            
            System.out.println("OSTN15 benchmarks enabled (grid loaded successfully, bidirectional)");
        } catch (Exception e) {
            System.out.println("OSTN15 benchmarks disabled: " + e.getMessage());
            ostn15Available = false;
        }
    }

    // ========== CRS Initialization Benchmarks ==========

    @Benchmark
    public Proj projInitWgs84() {
        return new Proj("+proj=longlat +datum=WGS84");
    }

    @Benchmark
    public Proj projInitEpsg4326() {
        return new Proj("EPSG:4326");
    }

    @Benchmark
    public Proj projInitUtm() {
        return new Proj("EPSG:32632");
    }

    @Benchmark
    public Proj projInitMercator() {
        return new Proj("EPSG:3857");
    }

    @Benchmark
    public Proj projInitCached() {
        return Proj4.getCachedProj("EPSG:4326");
    }

    // ========== Single Point Transformation Benchmarks ==========

    @Benchmark
    public Point transformWgs84ToMerc(Blackhole bh) {
        Point result = wgs84ToMerc.forward(testPoint);
        bh.consume(result);
        return result;
    }

    @Benchmark
    public Point transformWgs84ToUtm(Blackhole bh) {
        Point result = wgs84ToUtm.forward(testPoint);
        bh.consume(result);
        return result;
    }

    @Benchmark
    public double[] transformArrays() {
        return Proj4.proj4("+proj=longlat +datum=WGS84", "EPSG:3857", testCoords);
    }

    // ========== Batch Transformation Benchmarks ==========

    @Benchmark
    public void transformBatch1000Points(Blackhole bh) {
        double[][] results = Proj4.transformBatch(
            "+proj=longlat +datum=WGS84",
            "EPSG:3857",
            batchCoords
        );
        bh.consume(results);
    }

    @Benchmark
    public void transformFlat1000Points(Blackhole bh) {
        double[] results = Proj4.transformFlat(
            "+proj=longlat +datum=WGS84",
            "EPSG:3857",
            flatCoords
        );
        bh.consume(results);
    }

    // ========== OSTN15 Transformation Benchmarks ==========

    @Benchmark
    public Point transformOstn15Single(Blackhole bh) {
        if (!ostn15Available) {
            return null;
        }
        Point result = ostn15Converter.forward(testPointGb);
        bh.consume(result);
        return result;
    }

    @Benchmark
    public void transformOstn15Batch1000Points(Blackhole bh) {
        if (!ostn15Available) {
            return;
        }
        String etrs89Proj = "+proj=longlat +ellps=GRS80 +no_defs";
        String osgb36Proj = "+proj=longlat +ellps=airy +nadgrids=@uk_os_OSTN15_NTv2_OSGBtoETRS.tif +no_defs";
        double[][] results = Proj4.transformBatch(etrs89Proj, osgb36Proj, batchCoordsGb);
        bh.consume(results);
    }

    // ========== OSTN15 Inverse Transformation Benchmarks (OSGB36 to ETRS89) ==========

    @Benchmark
    public Point transformOstn15InverseSingle(Blackhole bh) {
        if (!ostn15Available) {
            return null;
        }
        Point result = ostn15ConverterInverse.forward(testPointGbOsgb);
        bh.consume(result);
        return result;
    }

    @Benchmark
    public void transformOstn15InverseBatch1000Points(Blackhole bh) {
        if (!ostn15Available) {
            return;
        }
        String etrs89Proj = "+proj=longlat +ellps=GRS80 +no_defs";
        String osgb36Proj = "+proj=longlat +ellps=airy +nadgrids=@uk_os_OSTN15_NTv2_OSGBtoETRS.tif +no_defs";
        double[][] results = Proj4.transformBatch(osgb36Proj, etrs89Proj, batchCoordsGbOsgb);
        bh.consume(results);
    }

    // ========== CRS Export Benchmarks ==========

    @Benchmark
    public String exportToWkt1() {
        return CRSSerializer.toWkt1(wgs84);
    }

    @Benchmark
    public String exportToWkt2() {
        return CRSSerializer.toWkt2(wgs84);
    }

    @Benchmark
    public String exportToProjString() {
        return CRSSerializer.toProjString(wgs84);
    }

    @Benchmark
    public String exportToProjJson() {
        return CRSSerializer.toProjJson(wgs84);
    }

    // ========== Main method to run benchmarks and output JSON ==========

    public static void main(String[] args) throws RunnerException, IOException {
        String outputFile = "target/java_benchmark_results.json";
        
        // Parse arguments
        for (int i = 0; i < args.length; i++) {
            if ("--output".equals(args[i]) && i + 1 < args.length) {
                outputFile = args[i + 1];
            }
        }
        
        System.out.println("Running proj4sedona benchmarks...");
        System.out.println();
        
        Options opt = new OptionsBuilder()
                .include(SpeedBenchmark.class.getSimpleName())
                .forks(1)
                .warmupIterations(3)
                .measurementIterations(5)
                .build();

        Collection<RunResult> results = new Runner(opt).run();
        
        // Convert results to JSON
        Map<String, Object> jsonResults = new LinkedHashMap<>();
        jsonResults.put("version", "1.0");
        jsonResults.put("generator", "proj4sedona JMH");
        jsonResults.put("timestamp", System.currentTimeMillis());
        
        Map<String, Object> benchmarks = new LinkedHashMap<>();
        
        for (RunResult result : results) {
            String benchmarkName = result.getParams().getBenchmark();
            // Extract method name from full class name
            String methodName = benchmarkName.substring(benchmarkName.lastIndexOf('.') + 1);
            
            double meanUs = result.getPrimaryResult().getScore();
            double errorUs = result.getPrimaryResult().getScoreError();
            
            Map<String, Object> benchmarkData = new LinkedHashMap<>();
            benchmarkData.put("mean_us", meanUs);
            benchmarkData.put("error_us", errorUs);
            benchmarkData.put("throughput_ops_per_sec", 1_000_000.0 / meanUs);
            benchmarkData.put("unit", "μs/op");
            
            benchmarks.put(methodName, benchmarkData);
            
            System.out.printf("%-35s: %.2f ± %.2f μs/op%n", methodName, meanUs, errorUs);
        }
        
        jsonResults.put("benchmarks", benchmarks);
        
        // Add computed metrics
        if (benchmarks.containsKey("transformBatch1000Points")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> batchBench = (Map<String, Object>) benchmarks.get("transformBatch1000Points");
            double batchMean = (Double) batchBench.get("mean_us");
            
            Map<String, Object> perPointBench = new LinkedHashMap<>();
            perPointBench.put("mean_us", batchMean / 1000.0);
            perPointBench.put("throughput_ops_per_sec", 1_000_000.0 / (batchMean / 1000.0));
            benchmarks.put("transformBatchPerPoint", perPointBench);
        }
        
        // Add computed metrics for OSTN15 batch (forward)
        if (benchmarks.containsKey("transformOstn15Batch1000Points")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> batchBench = (Map<String, Object>) benchmarks.get("transformOstn15Batch1000Points");
            double batchMean = (Double) batchBench.get("mean_us");
            
            Map<String, Object> perPointBench = new LinkedHashMap<>();
            perPointBench.put("mean_us", batchMean / 1000.0);
            perPointBench.put("throughput_ops_per_sec", 1_000_000.0 / (batchMean / 1000.0));
            benchmarks.put("transformOstn15BatchPerPoint", perPointBench);
        }
        
        // Add computed metrics for OSTN15 batch (inverse)
        if (benchmarks.containsKey("transformOstn15InverseBatch1000Points")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> batchBench = (Map<String, Object>) benchmarks.get("transformOstn15InverseBatch1000Points");
            double batchMean = (Double) batchBench.get("mean_us");
            
            Map<String, Object> perPointBench = new LinkedHashMap<>();
            perPointBench.put("mean_us", batchMean / 1000.0);
            perPointBench.put("throughput_ops_per_sec", 1_000_000.0 / (batchMean / 1000.0));
            benchmarks.put("transformOstn15InverseBatchPerPoint", perPointBench);
        }
        
        // Write JSON output
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(outputFile)) {
            gson.toJson(jsonResults, writer);
        }
        
        System.out.println();
        System.out.println("Results saved to: " + outputFile);
    }
}
