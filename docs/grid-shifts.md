# Grid Shifts

For high-accuracy datum transformations, Proj4Sedona supports NTv2 (.gsb) and GeoTIFF (.tif) grid shift files. These provide spatially varying corrections that are more accurate than parametric (3/7-parameter) shifts. Grid files can be loaded from disk, from byte arrays, or fetched automatically from the PROJ CDN.

## Loading Grid Files

### From Disk

```java
import org.datasyslab.proj4sedona.grid.GridLoader;
import org.datasyslab.proj4sedona.grid.GridData;

// Load a GeoTIFF grid file
GridLoader.loadFile("conus", "/path/to/us_noaa_conus.tif");

// Load an NTv2 grid file
GridLoader.loadFile("ntv2_canada", "/path/to/ca_nrc_ntv2_0.gsb");

// The format is auto-detected from the file contents
```

### From Byte Array

```java
import java.nio.file.Files;
import java.nio.file.Path;

byte[] gridData = Files.readAllBytes(Path.of("/path/to/grid.tif"));
GridLoader.load("my_grid", gridData);
```

### Checking and Retrieving Loaded Grids

```java
if (GridLoader.has("conus")) {
    GridData grid = GridLoader.get("conus");
    System.out.println("Subgrids: " + grid.getSubgrids().size());
}

// Remove a grid
GridLoader.remove("conus");

// Clear all loaded grids
GridLoader.clear();
```

## Automatic CDN Fetching

Grid files can be downloaded automatically from the PROJ CDN (https://cdn.proj.org/) when a transformation requires them.

### Enable Auto-Fetching

```java
import org.datasyslab.proj4sedona.grid.GridLoader;
import java.nio.file.Path;

// Enable auto-fetching
GridLoader.setAutoFetch(true);

// Optional: set a cache directory for downloaded files
GridLoader.setCacheDirectory(Path.of("/tmp/proj4-grids"));

// Now transformations that need grid files will download them automatically
double[] result = Proj4.proj4(
    "+proj=longlat +datum=NAD27",
    "+proj=longlat +datum=NAD83",
    new double[]{-77.0, 38.9}
);
```

### Manual CDN Download

Fetch a specific grid file without enabling auto-fetch:

```java
import org.datasyslab.proj4sedona.grid.GridCdnFetcher;
import org.datasyslab.proj4sedona.grid.GridData;
import org.datasyslab.proj4sedona.grid.GridLoader;

import java.util.concurrent.CompletableFuture;

// Synchronous fetch and load
GridData grid = GridLoader.fetchFromCdn("us_noaa_conus.tif");

// Asynchronous fetch
CompletableFuture<GridData> future = GridCdnFetcher.fetchAndLoadAsync("ca_nrc_ntv2_0.tif");

// Fetch with local caching
byte[] data = GridCdnFetcher.fetchWithCache("us_noaa_conus.tif");
```

### Custom CDN URL

```java
GridLoader.setCdnUrl("https://my-mirror.example.com/grids/");
```

## Automatic Grid Injection

Proj4Sedona has a `TransformationRegistry` that automatically selects the correct grid file for known CRS pairs. When you transform between these CRS pairs, the appropriate grid is injected into the transformation pipeline.

```java
import org.datasyslab.proj4sedona.grid.TransformationRegistry;

// Check if a mapping exists
boolean hasMapping = TransformationRegistry.hasGridMapping("EPSG:4267", "EPSG:4269");

// Get the grid file name for a CRS pair
String gridFile = TransformationRegistry.getGridFile("EPSG:4267", "EPSG:4269");
// "ca_nrc_ntv2_0.tif"
```

### Pre-Registered Grid Mappings

| Source CRS | Target CRS | Grid File | Region |
|------------|------------|-----------|--------|
| EPSG:4267 (NAD27) | EPSG:4269 (NAD83) | `ca_nrc_ntv2_0.tif` | Canada |
| EPSG:4269 (NAD83) | EPSG:4152 (NAD83 HARN) | `us_noaa_nadcon5_nad83_1986_nad83_harn_conus.tif` | USA (CONUS) |
| EPSG:4277 (OSGB36) | EPSG:4258 (ETRS89) | `uk_os_OSTN15_NTv2_OSGBtoETRS.tif` | United Kingdom |
| EPSG:4203 (AGD84) | EPSG:4283 (GDA94) | `au_icsm_National_84_02_07_01.tif` | Australia |
| EPSG:4275 (NTF) | EPSG:4171 (RGF93) | `fr_ign_gr3df97a.tif` | France |
| EPSG:4314 (DHDN) | EPSG:4258 (ETRS89) | `de_adv_BETA2007.tif` | Germany |
| EPSG:4272 (NZGD49) | EPSG:4167 (NZGD2000) | `nz_linz_nzgd2kgrid0005.tif` | New Zealand |

## Using Grids in PROJ Strings

Grids are referenced via the `+nadgrids` parameter:

```java
// The @ prefix makes the grid optional (won't error if missing)
Proj nad27 = new Proj("+proj=longlat +ellps=clrk66 +nadgrids=@conus,@alaska,@ntv2_0.gsb,@ntv1_can.dat");

// Without @, the grid is mandatory
Proj osgb = new Proj("+proj=longlat +ellps=airy +nadgrids=OSTN15_NTv2_OSGBtoETRS.gsb");
```

### Parsing nadgrids

The `GridLoader.getNadgrids()` method parses the comma-separated list:

```java
import org.datasyslab.proj4sedona.grid.NadgridInfo;

List<NadgridInfo> grids = GridLoader.getNadgrids("@conus,@alaska,@ntv2_0.gsb,null");
// Returns list with each grid's name, mandatory flag, and loaded data
// "null" entries are returned as a NadgridInfo with isNull() == true
// (an identity / no-shift step, not dropped from the list)
// "@" prefix means optional (mandatory=false)
```

## Supported Grid Formats

| Format | Extension | Description |
|--------|-----------|-------------|
| NTv2 | `.gsb` | National Transformation version 2, binary format with latitude/longitude shifts |
| GeoTIFF | `.tif` | Cloud-optimized GeoTIFF grids from the PROJ CDN |

Both formats are auto-detected when loading via `GridLoader.load()` or `GridLoader.loadFile()`.

## Common Grid Files

| Grid File | Region | Use Case |
|-----------|--------|----------|
| `us_noaa_conus.tif` | US (CONUS) | NAD27 to NAD83 |
| `ca_nrc_ntv2_0.tif` | Canada | NAD27 to NAD83 |
| `us_noaa_alaska.tif` | Alaska | NAD27 to NAD83 |
| `us_noaa_hawaii.tif` | Hawaii | NAD27 to NAD83 |
| `uk_os_OSTN15_NTv2_OSGBtoETRS.tif` | United Kingdom | OSGB36 to ETRS89 |
| `de_adv_BETA2007.tif` | Germany | DHDN to ETRS89 |
| `fr_ign_gr3df97a.tif` | France | NTF to RGF93 |
| `au_icsm_National_84_02_07_01.tif` | Australia | AGD84 to GDA94 |
| `nz_linz_nzgd2kgrid0005.tif` | New Zealand | NZGD49 to NZGD2000 |

## End-to-End Example

```java
import org.datasyslab.proj4sedona.Proj4;
import org.datasyslab.proj4sedona.grid.GridLoader;
import java.nio.file.Path;

// 1. Enable CDN auto-fetch with local caching
GridLoader.setAutoFetch(true);
GridLoader.setCacheDirectory(Path.of("/tmp/proj4-grids"));

// 2. Transform NAD27 to NAD83 (grid downloaded automatically)
double[] nad83 = Proj4.proj4(
    "EPSG:4267",  // NAD27
    "EPSG:4269",  // NAD83
    new double[]{-77.0, 38.9}
);

// 3. Transform OSGB36 to WGS84 (7-parameter Helmert from the OSGB36 datum)
double[] wgs84 = Proj4.proj4(
    "EPSG:4277",  // OSGB36
    "EPSG:4326",  // WGS84
    new double[]{-2.0, 49.0}
);
```

## See Also

- [Datum Transformations](datum-transformations.md) -- overview of datum shift methods
- [Constants Reference](constants-reference.md) -- built-in datum definitions
