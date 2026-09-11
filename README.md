# Proj4Sedona

A high-performance Java library for coordinate system transformations, ported from Proj4js (https://github.com/proj4js/proj4js).

## Overview

Proj4Sedona provides coordinate system transformations, datum conversions, and projection operations for geospatial applications.

**Key Features:**
- High-performance: faster than Python's pyproj
- Format support: PROJ strings, WKT1/WKT2, PROJJSON, EPSG codes
- 34 map projections (Mercator, UTM, Lambert, Albers, Krovak, Oblique Mercator, Equal Earth, Geocentric, etc.), plus the longlat identity transform
- MGRS coordinate conversion
- GeoTIFF datum grids with PROJ CDN integration
- JTS geometry transformation support
- Extensive unit tests

## Documentation

Full documentation is available in the [docs/](docs/) folder:

- [Getting Started](docs/getting-started.md) -- installation and first transformation
- [Coordinate Transformations](docs/coordinate-transformations.md) -- single, batch, and flat array transforms
- [CRS Formats](docs/crs-formats.md) -- PROJ strings, WKT1, WKT2, PROJJSON, EPSG codes
- [Projections](docs/projections.md) -- all 34 map projections plus the longlat identity
- [Datum Transformations](docs/datum-transformations.md) -- 3-param, 7-param, and grid-based shifts
- [Grid Shifts](docs/grid-shifts.md) -- NTv2/GeoTIFF grid loading and CDN auto-fetching
- [MGRS Coordinates](docs/mgrs.md) -- Military Grid Reference System conversion
- [JTS Integration](docs/jts-integration.md) -- transforming JTS geometries
- [CRS Registry](docs/crs-registry.md) -- extending the CRS provider chain
- [Caching and Performance](docs/caching-and-performance.md) -- projection caching and thread safety
- [Constants Reference](docs/constants-reference.md) -- datums, ellipsoids, units, prime meridians

**Serialization compatibility:** Standard WKT/PROJJSON exporters now throw
`UnsupportedOperationException` instead of returning lossy output for definitions they
cannot represent. WKT2 and PROJJSON preserve supported three- and seven-parameter
`+towgs84` operations as a `BoundCRS` (geocentric sources are supported in PROJJSON
only), while WKT1 uses `TOWGS84`. Canonical named grid-shift datums such as NAD27
export through their standard datum identity; custom grid operations remain rejected.
All standard exporters reject `+over`, `+R_A`, and two-point `omerc`. WKT2 and
PROJJSON also reject `+approx`; WKT1 preserves approximate Transverse Mercator with
the executable `Fast_Transverse_Mercator` method. Use `toProjString()` for remaining
unsupported operation semantics. The complete list is in the
[CRS export fidelity guide](docs/crs-formats.md#export-fidelity).

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
String mgrs = Proj4.toMGRS(-77.0369, 38.9072);  // "18SUJ2338308450"

// With custom accuracy (0=100km, 1=10km, 2=1km, 3=100m, 4=10m, 5=1m)
String mgrs1km = Proj4.toMGRS(-77.0369, 38.9072, 2);  // "18SUJ2308"

// Convert MGRS to lon/lat
double[] lonLat = Proj4.fromMGRS("18SUJ2338308450");  // [-77.0369, 38.9072]

// Or use the MGRS class directly
String mgrs2 = MGRS.forward(new double[]{-77.0369, 38.9072}, 5);
double[] point = MGRS.toPoint("18SUJ2338308450");
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

34 map projections — the complete proj4js set — plus the longlat identity transform. Identity is listed under Other below but is not counted among the 34:
- **Cylindrical**: Mercator, Transverse Mercator, UTM, Miller, Equirectangular, Cylindrical Equal Area, Cassini-Soldner, Swiss Oblique Mercator, Hotine Oblique Mercator, Gauss-Schreiber Transverse Mercator
- **Pseudocylindrical**: Sinusoidal, Mollweide, Robinson, Equal Earth, Eckert VI, Van der Grinten
- **Conic**: Lambert Conformal Conic, Albers Equal Area, Equidistant Conic, Polyconic, Krovak, Bonne
- **Azimuthal**: Lambert Azimuthal Equal Area, Stereographic, Oblique Stereographic Alternative, Azimuthal Equidistant, Orthographic, Gnomonic, Tilted Perspective
- **Other**: Geostationary Satellite, New Zealand Map Grid, Geocentric (ECEF), Quadrilateralized Spherical Cube, General Oblique Transformation (rotated pole), Identity (longlat)

## NetCDF CF Grid Mappings

`CfGridMapping` translates CF (Climate and Forecast) convention grid mapping attributes —
the parameter form used by netCDF files that carry no `crs_wkt` — into a PROJ string or
`Proj`, following CF conventions Appendix F:

```java
import org.datasyslab.proj4sedona.cf.CfGridMapping;

Map<String, Object> cf = new HashMap<>();
cf.put("grid_mapping_name", "lambert_conformal_conic");
cf.put("standard_parallel", new double[]{33.0, 45.0});
cf.put("longitude_of_central_meridian", -97.0);
cf.put("latitude_of_projection_origin", 40.0);

String projString = CfGridMapping.toProjString(cf);
// "+proj=lcc +lat_1=33 +lat_2=45 +lat_0=40 +lon_0=-97 +datum=WGS84 +no_defs"
Proj proj = CfGridMapping.toProj(cf);
String wkt = proj.toWkt2();
```

Supported grid mappings: `latitude_longitude`, `albers_conical_equal_area`,
`azimuthal_equidistant`, `geostationary`, `lambert_azimuthal_equal_area`,
`lambert_conformal_conic` (1SP/2SP), `lambert_cylindrical_equal_area`, `mercator`,
`orthographic`, `polar_stereographic`, `sinusoidal`, `stereographic`,
`transverse_mercator`, and netCDF-Java's `universal_transverse_mercator`. Ellipsoid and
datum attributes (`semi_major_axis`, `inverse_flattening`, `earth_radius`,
`reference_ellipsoid_name`, `horizontal_datum_name`, ...), prime meridians, `towgs84`,
and non-metre coordinate units (`toProjString(cf, "km")`) are handled; with no
identifying figure parameters, WGS 84 is assumed, as GDAL and pyproj do.

This module is a Proj4Sedona extension with no proj4js counterpart; its behavior is
validated against pyproj's `CRS.from_cf` and GDAL's `importFromCF1` (divergences between
those two are resolved case by case and documented in the Javadoc).

## Upstream Sync Status

Proj4Sedona tracks three upstream code bases. The port is complete and audited as of the
commits below — every upstream change up to these points is either ported, backported,
or documented as an intentional divergence (divergences follow PROJ where proj4js is
known to be wrong; each is noted in the relevant Javadoc and pinned by a test):

| Upstream | Ported through | Date |
|---|---|---|
| [proj4js](https://github.com/proj4js/proj4js) | commit `955bfd6` | 2026-07-28 |
| [wkt-parser](https://github.com/proj4js/wkt-parser) | v1.5.6 | 2026-07-23 |
| [mgrs](https://github.com/proj4js/mgrs) | v2.2.0 | 2026-07-21 |

Some covered behavior is newer than the published `proj4@2.21.0` npm package and may
differ until the next upstream release.

The datum registry is synchronized as a complete snapshot of proj4js's generated
`lib/constants/Datum.js`, rather than inferred from the commit range above. The pinned
snapshot contains 438 canonical records and is guarded by an exhaustive count,
content-hash, field, and alias parity test. To verify or refresh it from a clean
proj4js checkout:

```bash
node scripts/sync-proj4js-datums.mjs --check /path/to/proj4js
# Omit --check to refresh the snapshot after intentionally advancing the pin.
```

To find upstream changes that may need porting since the last sync:

```bash
# In a proj4js checkout:
git log 955bfd6..origin/main -- lib/

# For wkt-parser, diff the published packages:
npm pack wkt-parser@1.5.6 && npm pack wkt-parser@latest

# For MGRS, diff the published packages:
npm pack mgrs@2.2.0 && npm pack mgrs@latest
```

When updating this table, audit each new commit/diff hunk as ported, not applicable,
or needs-backport, and land backports one commit per upstream change.

## Building

```bash
mvn clean install
```

## Benchmarks

Run benchmarks against pyproj:

The strict `-Pbenchmarks` profile requires `python3` on `PATH` with the `pyproj` and
NumPy packages installed. The build intentionally fails when Python or either package
is missing, because the parity gate cannot validate results without its reference
implementation.

```bash
mvn verify -Pbenchmarks
```

This generates `target/benchmark_report.md` containing:
1. **Speedup vs pyproj**: Performance comparison table
2. **Correctness vs pyproj**: Error statistics (max/avg error per category)
3. **Parity coverage**: Every generated transform, grid, parser, serializer, and
   exhaustive active-EPSG meridian-axis case is reported as compared, explicitly
   skipped with a reason, or failed

The correctness pass is a build gate, not a best-effort benchmark. Missing or duplicate
reference rows, non-finite results, parse/serialization errors, stale skip or tolerance
declarations, and errors above tolerance all fail the Maven build after the report is
written.

**Benchmark Categories:**
- CRS initialization
- Single and batch transformations
- OSTN15 grid-based transformations

**Typical Results** (M1 MacBook Pro):
| Operation | Throughput |
|-----------|------------|
| Cached projection lookup | ~100M ops/sec |
| Single transformation | ~500K ops/sec |
| Batch 1000 points | ~2ms |

## Releasing

To release a new version to Maven Central:

1. Update the version in `pom.xml`
2. Run `mvn clean deploy -DperformRelease -DskipTests`

## Use Cases

Proj4Sedona is suitable for a wide range of geospatial applications.
