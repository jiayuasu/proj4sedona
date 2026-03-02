# Caching and Performance

This guide covers projection caching, preloading, batch transforms, and thread safety in Proj4Sedona.

## Projection Caching

Parsing CRS strings (PROJ, WKT, PROJJSON) has overhead. Use caching to avoid repeated parsing:

### getCachedProj

```java
import org.datasyslab.proj4sedona.Proj4;
import org.datasyslab.proj4sedona.core.Proj;

// First call parses the string; subsequent calls return the cached Proj
Proj utm18 = Proj4.getCachedProj("+proj=utm +zone=18 +datum=WGS84");
Proj utm18Again = Proj4.getCachedProj("+proj=utm +zone=18 +datum=WGS84");
// utm18 == utm18Again (same object)
```

### cachedConverter

Creates a `Converter` using cached `Proj` objects for both source and destination:

```java
import org.datasyslab.proj4sedona.transform.Converter;

Converter conv = Proj4.cachedConverter("EPSG:4326", "EPSG:3857");

// Both the source and destination Proj objects are cached.
// Calling cachedConverter again with the same arguments reuses them.
```

### Cache Management

```java
// Check cache size
int size = Proj4.getCacheSize();

// Clear the cache (e.g., to free memory)
Proj4.clearCache();
```

## Preloading Projections

For latency-sensitive applications, preload commonly used projections at startup:

### preloadCommonProjections

Preloads WGS84, Web Mercator, and UTM zones 10-19:

```java
// Call once at application startup
Proj4.preloadCommonProjections();
```

### preloadProjections

Preload specific projections for your application:

```java
Proj4.preloadProjections(
    "EPSG:4326",
    "EPSG:3857",
    "+proj=utm +zone=32 +datum=WGS84",
    "+proj=lcc +lat_1=33 +lat_2=45 +datum=WGS84"
);
```

## Batch Transforms

Batch methods reduce per-transform overhead by amortizing CRS parsing and pipeline setup.

### Performance Comparison

| Method | Overhead | Best For |
|--------|----------|----------|
| `Proj4.proj4(from, to, coord)` loop | CRS parsed each call | <10 points |
| `Converter.forward(point)` loop | CRS parsed once | 10-100 points |
| `Proj4.transformBatch(from, to, coords)` | CRS parsed once, batch optimized | 100+ points |
| `Proj4.transformFlat(from, to, flat)` | CRS parsed once, no array-of-array overhead | Large arrays |

### Example: Efficient Batch Transform

```java
// Bad: re-parses CRS definitions for every point
for (double[] coord : coords) {
    double[] result = Proj4.proj4("EPSG:4326", "EPSG:3857", coord);
}

// Good: parse once with a converter
Converter conv = Proj4.cachedConverter("EPSG:4326", "EPSG:3857");
for (double[] coord : coords) {
    double[] result = conv.forward(coord);
}

// Best: use batch method for large datasets
double[][] results = Proj4.transformBatch("EPSG:4326", "EPSG:3857", coords);
```

### Flat Array Transforms

When coordinates are already in flat arrays (common with binary formats or native arrays), use the flat methods to avoid creating intermediate arrays:

```java
// Coordinates as [x1, y1, x2, y2, ...]
double[] flat = new double[numPoints * 2];
// ... fill flat array ...

double[] results = Proj4.transformFlat("EPSG:4326", "EPSG:3857", flat);

// 3D variant: [x1, y1, z1, x2, y2, z2, ...]
double[] flat3d = new double[numPoints * 3];
double[] results3d = Proj4.transformFlat3D("EPSG:4326", "EPSG:3857", flat3d);
```

## Thread Safety

Proj4Sedona is designed for concurrent use:

- The projection cache (`Proj4.getCachedProj`) uses `ConcurrentHashMap` and is safe for concurrent reads and writes.
- The `Defs` provider chain uses `CopyOnWriteArrayList`, safe for concurrent reads with rare writes.
- `Converter` objects are stateless and can be shared across threads.
- `Proj` objects are safe to use concurrently after construction.
- `JTSGeometryTransformer` instances are safe to share (they use `Converter` internally).

### Recommended Pattern for Multi-Threaded Applications

```java
import org.datasyslab.proj4sedona.Proj4;
import org.datasyslab.proj4sedona.transform.Converter;

public class GeoService {
    // Create converter once, share across threads
    private final Converter wgs84ToMercator;

    public GeoService() {
        Proj4.preloadCommonProjections();
        this.wgs84ToMercator = Proj4.cachedConverter("EPSG:4326", "EPSG:3857");
    }

    // Safe to call from any thread
    public double[] toWebMercator(double lon, double lat) {
        return wgs84ToMercator.forward(new double[]{lon, lat});
    }
}
```

## Typical Performance

Measured on an M1 MacBook Pro:

| Operation | Throughput |
|-----------|------------|
| Cached projection lookup | ~100M ops/sec |
| Single transformation | ~500K ops/sec |
| Batch 1000 points | ~2ms |

Actual numbers depend on hardware and the specific projections used. Complex projections (e.g., Hotine Oblique Mercator) are slower than simple ones (e.g., UTM).

## Benchmarking

Run the included benchmarks:

```bash
mvn verify -Pbenchmarks
```

This generates `target/benchmark_report.md` with:
- Speedup vs pyproj for various operations
- Correctness validation against pyproj reference values

## See Also

- [Coordinate Transformations](coordinate-transformations.md) -- single and batch transform APIs
- [JTS Integration](jts-integration.md) -- caching with JTSGeometryTransformer
