package org.proj4sedona.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.proj4sedona.Proj4;
import org.proj4sedona.core.Point;
import org.proj4sedona.core.Proj;
import org.proj4sedona.mgrs.MGRS;
import org.proj4sedona.transform.Converter;

import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for Proj4Sedona performance testing.
 * 
 * Run with: mvn exec:java -Dexec.mainClass="org.proj4sedona.benchmark.Proj4Benchmark"
 * Or: java -jar target/benchmarks.jar
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class Proj4Benchmark {

    // Pre-initialized projections for transformation benchmarks
    private Proj wgs84;
    private Proj webMercator;
    private Proj utm18n;
    private Converter wgs84ToMerc;
    private Converter wgs84ToUtm;

    // Test coordinates
    private Point testPoint;
    private double[] testCoords;
    private String testMgrs;

    // Batch test data
    private double[][] batchCoords;
    private double[] flatCoords;

    @Setup(Level.Trial)
    public void setup() {
        // Initialize projections once
        wgs84 = new Proj("+proj=longlat +datum=WGS84");
        webMercator = new Proj("+proj=merc +a=6378137 +b=6378137 +lat_ts=0 +lon_0=0 +x_0=0 +y_0=0 +k=1 +units=m");
        utm18n = new Proj("+proj=utm +zone=18 +datum=WGS84");

        // Create converters
        wgs84ToMerc = new Converter(wgs84, webMercator);
        wgs84ToUtm = new Converter(wgs84, utm18n);

        // Test data: Washington DC
        testPoint = new Point(-77.0369, 38.9072);
        testCoords = new double[]{-77.0369, 38.9072};
        testMgrs = "18SUJ2338308451";

        // Batch test data (1000 points)
        batchCoords = new double[1000][2];
        flatCoords = new double[2000];
        for (int i = 0; i < 1000; i++) {
            double lon = -180 + (i % 360);
            double lat = -80 + (i / 10.0) % 160;
            batchCoords[i][0] = lon;
            batchCoords[i][1] = lat;
            flatCoords[i * 2] = lon;
            flatCoords[i * 2 + 1] = lat;
        }
    }

    // ========== Point Creation Benchmarks ==========

    @Benchmark
    public Point pointCreationConstructor() {
        return new Point(-77.0369, 38.9072);
    }

    @Benchmark
    public Point pointCreationFactory() {
        return Point.of(-77.0369, 38.9072);
    }

    @Benchmark
    public Point pointCreationFromArray() {
        return new Point(testCoords);
    }

    // ========== Projection Initialization Benchmarks ==========

    @Benchmark
    public Proj projInitWgs84() {
        return new Proj("+proj=longlat +datum=WGS84");
    }

    @Benchmark
    public Proj projInitMercator() {
        return new Proj("+proj=merc +datum=WGS84");
    }

    @Benchmark
    public Proj projInitUtm() {
        return new Proj("+proj=utm +zone=18 +datum=WGS84");
    }

    @Benchmark
    public Proj projInitCachedWgs84() {
        return Proj4.getCachedProj("+proj=longlat +datum=WGS84");
    }

    @Benchmark
    public Proj projInitCachedUtm() {
        return Proj4.getCachedProj("+proj=utm +zone=18 +datum=WGS84");
    }

    // ========== Single Transformation Benchmarks ==========

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
    public double[] transformWithArrays() {
        return Proj4.proj4("+proj=longlat +datum=WGS84", "+proj=merc +datum=WGS84", testCoords);
    }

    // ========== Batch Transformation Benchmarks ==========

    @Benchmark
    public void transformBatch1000Points(Blackhole bh) {
        double[][] results = Proj4.transformBatch(
            "+proj=longlat +datum=WGS84",
            "+proj=merc +datum=WGS84",
            batchCoords
        );
        bh.consume(results);
    }

    @Benchmark
    public void transformFlat1000Points(Blackhole bh) {
        double[] results = Proj4.transformFlat(
            "+proj=longlat +datum=WGS84",
            "+proj=merc +datum=WGS84",
            flatCoords
        );
        bh.consume(results);
    }

    // ========== MGRS Benchmarks ==========

    @Benchmark
    public String mgrsForward() {
        return MGRS.forward(testCoords, 5);
    }

    @Benchmark
    public double[] mgrsInverse() {
        return MGRS.toPoint(testMgrs);
    }

    @Benchmark
    public String mgrsRoundTrip() {
        double[] point = MGRS.toPoint(testMgrs);
        return MGRS.forward(point, 5);
    }

    // ========== Main method to run benchmarks ==========

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(Proj4Benchmark.class.getSimpleName())
                .forks(1)
                .warmupIterations(3)
                .measurementIterations(5)
                .build();

        new Runner(opt).run();
    }
}
