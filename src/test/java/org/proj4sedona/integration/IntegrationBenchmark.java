package org.proj4sedona.integration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.proj4sedona.Proj4;
import org.proj4sedona.core.Point;
import org.proj4sedona.core.Proj;
import org.proj4sedona.parser.CRSSerializer;
import org.proj4sedona.transform.Converter;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for integration testing performance comparison with pyproj.
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
 * </ul>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class IntegrationBenchmark {

    // Pre-initialized objects
    private Proj wgs84;
    private Proj webMercator;
    private Proj utm32n;
    private Converter wgs84ToMerc;
    private Converter wgs84ToUtm;

    // Test coordinates (Washington DC)
    private Point testPoint;
    private double[] testCoords;

    // Batch test data (1000 points)
    private double[][] batchCoords;
    private double[] flatCoords;

    @Setup(Level.Trial)
    public void setup() {
        // Initialize projections
        wgs84 = new Proj("+proj=longlat +datum=WGS84");
        webMercator = new Proj("EPSG:3857");
        utm32n = new Proj("EPSG:32632");

        // Create converters
        wgs84ToMerc = new Converter(wgs84, webMercator);
        wgs84ToUtm = new Converter(wgs84, utm32n);

        // Test data
        testPoint = new Point(-77.0369, 38.9072);
        testCoords = new double[]{-77.0369, 38.9072};

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
                .include(IntegrationBenchmark.class.getSimpleName())
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
        
        // Write JSON output
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(outputFile)) {
            gson.toJson(jsonResults, writer);
        }
        
        System.out.println();
        System.out.println("Results saved to: " + outputFile);
    }
}
