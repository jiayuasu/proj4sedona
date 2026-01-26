# Proj4Sedona

A high-performance Java library for coordinate system transformations, ported from Proj4js (https://github.com/proj4js/proj4js).

## Overview

Proj4Sedona provides coordinate system transformations, datum conversions, and projection operations for geospatial applications.

**Key Features:**
- 🚀 High-performance: faster than Python's pyproj
- 🌍 Format support: PROJ strings, WKT1/WKT2, PROJJSON, EPSG codes
- 🗺️ 21 map projections (Mercator, UTM, Lambert, Albers, Sinusoidal, Robinson, etc.)
- 🎯 MGRS coordinate conversion
- 📊 GeoTIFF datum grids with PROJ CDN integration
- ✅ lots of unit tests

## Quick Start

### Maven Dependency


### Basic Usage

```java
import org.datasyslab.proj4sedona.Proj4;
import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.transform.Converter;

// Simple coordinate transformation
double[] result = Proj4.proj4(
    "+proj=longlat +datum=WGS84",
    "+proj=merc +datum=WGS84",
    new double[]{-77.0369, 38.9072}
);

// Using Point objects
Point p = Proj4.proj4("+proj=utm +zone=18 +datum=WGS84", new Point(-77.0, 38.9));

// Create a reusable converter
Converter conv = Proj4.proj4("EPSG:4326", "EPSG:3857");
Point result1 = conv.forward(new Point(-77.0, 38.9));
Point result2 = conv.inverse(result1);  // back to lon/lat
```

### High-Performance Batch Transformations

For transforming many coordinates efficiently:

```java
// Batch transformation (array of [x, y] pairs)
double[][] coords = {{-77.0, 38.9}, {-122.4, 37.8}, {0.0, 51.5}};
double[][] results = Proj4.transformBatch(
    "+proj=longlat +datum=WGS84",
    "+proj=merc +datum=WGS84",
    coords
);

// Flat array transformation [x1, y1, x2, y2, ...]
double[] flat = {-77.0, 38.9, -122.4, 37.8, 0.0, 51.5};
double[] flatResults = Proj4.transformFlat(
    "+proj=longlat +datum=WGS84",
    "+proj=merc +datum=WGS84",
    flat
);

// Use cached converter for repeated transformations
Converter conv = Proj4.cachedConverter("EPSG:4326", "EPSG:3857");
```

### Projection Caching

Avoid repeated parsing overhead with projection caching:

```java
import org.datasyslab.proj4sedona.Proj4;
import org.datasyslab.proj4sedona.core.Proj;

// Preload common projections at startup (WGS84, Web Mercator, UTM zones 10-19)
Proj4.preloadCommonProjections();

// Or preload specific projections for your application
Proj4.preloadProjections(
    "+proj=utm +zone=32 +datum=WGS84",
    "+proj=lcc +lat_1=33 +lat_2=45 +datum=WGS84"
);

// Get or create cached projection
Proj proj = Proj4.getCachedProj("+proj=utm +zone=18 +datum=WGS84");

// Cache management
int size = Proj4.getCacheSize();  // Number of cached projections
Proj4.clearCache();               // Clear all cached projections
```

**When to use batch vs single transforms:**
- **Single transforms** (`proj4()` methods): For interactive use, < 10 points, or when working with Point objects
- **Batch transforms** (`transformBatch()`): For datasets with 100+ coordinates, ~30% faster than looping
- **Flat transforms** (`transformFlat()`): For coordinates in `[x1,y1,x2,y2,...]` format, most memory-efficient

### MGRS Coordinates

Convert between geographic coordinates and Military Grid Reference System (MGRS):

```java
import org.datasyslab.proj4sedona.Proj4;
import org.datasyslab.proj4sedona.mgrs.MGRS;

// Convert lon/lat to MGRS
String mgrs = Proj4.toMGRS(-77.0369, 38.9072);  // "18SUJ2338308451"

// With custom accuracy (1=10km, 2=1km, 3=100m, 4=10m, 5=1m)
String mgrs1km = Proj4.toMGRS(-77.0369, 38.9072, 2);  // "18SUJ2308"

// Convert MGRS to lon/lat
double[] lonLat = Proj4.fromMGRS("18SUJ2338308451");  // [-77.0369, 38.9072]

// Or use the MGRS class directly
String mgrs2 = MGRS.forward(new double[]{-77.0369, 38.9072}, 5);
double[] point = MGRS.toPoint("18SUJ2338308451");
double[] bbox = MGRS.inverse("18SUJ23");  // [left, bottom, right, top]
```

### Datum Transformations with Grid Shift Files

Proj4Sedona supports NAD grid shift transformations using NTv2 (.gsb) and GeoTIFF (.tif) files
from the PROJ CDN (https://cdn.proj.org/).

#### Manual Loading

```java
import org.datasyslab.proj4sedona.grid.GridLoader;
import org.datasyslab.proj4sedona.grid.GridData;

// Load a grid file from disk (format auto-detected)
GridLoader.loadFile("conus", "/path/to/us_noaa_conus.tif");

// Or load from byte array
byte[] gridData = Files.readAllBytes(Path.of("/path/to/grid.gsb"));
GridLoader.load("my_grid", gridData);

// Check if grid is loaded
if (GridLoader.has("conus")) {
    GridData grid = GridLoader.get("conus");
    System.out.println("Loaded " + grid.getSubgrids().size() + " subgrids");
}
```

#### Automatic CDN Fetching

Grid files can be automatically downloaded from the PROJ CDN when needed:

```java
import org.datasyslab.proj4sedona.grid.GridLoader;
import java.nio.file.Path;

// Enable auto-fetching from CDN
GridLoader.setAutoFetch(true);

// Optional: Set a cache directory for downloaded grids
GridLoader.setCacheDirectory(Path.of("/path/to/cache"));

// Now grids are fetched automatically when transformations need them
// For example, this will auto-download us_noaa_conus.tif if not already loaded:
List<NadgridInfo> grids = GridLoader.getNadgrids("@us_noaa_conus.tif,null");
```

#### Direct CDN Download

You can also fetch grids directly without enabling auto-fetch:

```java
import org.datasyslab.proj4sedona.grid.GridLoader;
import org.datasyslab.proj4sedona.grid.GridCdnFetcher;

// Fetch and load a specific grid
GridData grid = GridLoader.fetchFromCdn("us_noaa_conus.tif");

// Or fetch asynchronously
CompletableFuture<GridData> future = GridCdnFetcher.fetchAndLoadAsync("ca_nrc_ntv2_0.tif");
```

**Grid File Support:**
- **NTv2 (.gsb)**: Native support
- **GeoTIFF (.tif)**: Supported via `geotiff.java` (included as dependency)

**Common Grid Files:**
| Grid | Region | Use Case |
|------|--------|----------|
| `us_noaa_conus.tif` | US (CONUS) | NAD27 to NAD83 |
| `ca_nrc_ntv2_0.tif` | Canada | NAD27 to NAD83 |
| `us_noaa_alaska.tif` | Alaska | NAD27 to NAD83 |
| `us_noaa_hawaii.tif` | Hawaii | NAD27 to NAD83 |

## Supported Projections

21 map projections including:
- **Cylindrical**: Mercator, Transverse Mercator, UTM, Miller, Equirectangular, Cylindrical Equal Area
- **Pseudocylindrical**: Sinusoidal, Mollweide, Robinson, Equal Earth
- **Conic**: Lambert Conformal Conic, Albers Equal Area, Equidistant Conic
- **Azimuthal**: Lambert Azimuthal Equal Area, Stereographic, Azimuthal Equidistant, Orthographic, Gnomonic
- **Other**: Van der Grinten, Hotine Oblique Mercator

## Building

```bash
mvn clean install
```

## Performance Benchmarks

JMH benchmarks are available to measure transformation throughput:

```bash
# Run benchmarks
mvn exec:java -Dexec.mainClass="org.datasyslab.proj4sedona.benchmark.Proj4Benchmark"

# Or run specific benchmark
java -jar target/benchmarks.jar Proj4Benchmark.transformWgs84ToMerc
```

**Benchmark Categories:**
- Point creation: constructor vs factory methods
- Projection initialization: cached vs uncached
- Single transformations: WGS84 to Mercator, UTM
- Batch transformations: 1000 points
- MGRS: encoding/decoding

**Typical Results** (M1 MacBook Pro):
| Operation | Throughput |
|-----------|------------|
| Point creation | ~50M ops/sec |
| Cached projection lookup | ~100M ops/sec |
| Single transformation | ~500K ops/sec |
| Batch 1000 points | ~2ms |
| MGRS encode | ~300K ops/sec |

## Use Cases

Proj4Sedona is suitable for a wide range of geospatial applications.
