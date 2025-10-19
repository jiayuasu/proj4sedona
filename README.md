# Proj4Sedona

A Java port of the Proj4js library for coordinate system transformations.

## Overview

Proj4Sedona is a Java library that provides coordinate system transformations, similar to the popular JavaScript Proj4js library. It allows you to transform point coordinates from one coordinate system to another, including datum transformations.

## Features

- **Coordinate System Transformations**: Transform coordinates between different projections
- **Multiple Projection Support**: Supports WGS84, Mercator, Lambert Conformal Conic, UTM, Transverse Mercator, and Albers Equal Area projections
- **Datum Transformations**: Full support for 3-parameter and 7-parameter Helmert transformations
- **Grid-Based Adjustments**: Support for NTv2 and other grid-based datum transformations
- **PROJ String Support**: Parse and use PROJ.4 string definitions
- **WKT Support**: Parse and use Well-Known Text (WKT) coordinate system definitions
- **Extensible Architecture**: Easy to add new projection implementations
- **Type Safety**: Full Java type safety with proper error handling
- **Maven Integration**: Ready-to-use Maven project structure

## Quick Start

### Maven Dependency

Add this to your `pom.xml` for the core functionality:

```xml
<dependency>
    <groupId>org.apache.sedona.proj</groupId>
    <artifactId>proj4sedona-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

For MGRS support, also add:

```xml
<dependency>
    <groupId>org.apache.sedona.proj</groupId>
    <artifactId>proj4sedona-mgrs</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

For WKT parsing support, also add:

```xml
<dependency>
    <groupId>org.apache.sedona.proj</groupId>
    <artifactId>proj4sedona-wkt-parser</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Basic Usage

```java
import org.apache.sedona.proj.Proj4Sedona;
import org.apache.sedona.proj.core.Point;

// Create a point
Point point = new Point(-71.0, 41.0); // Longitude, Latitude

// Transform from WGS84 to WGS84 (identity transformation)
Point result = Proj4Sedona.transform("WGS84", point);
System.out.println(result); // Point{x=-71.000000, y=41.000000}

// Create a converter for repeated transformations
Proj4Sedona.Converter converter = Proj4Sedona.converter("WGS84");
Point transformed = converter.forward(point);
```

### Creating Points

```java
// From individual coordinates
Point p1 = new Point(10.0, 20.0);
Point p2 = new Point(10.0, 20.0, 30.0); // with Z coordinate
Point p3 = new Point(10.0, 20.0, 30.0, 40.0); // with Z and M coordinates

// From array
double[] coords = {10.0, 20.0, 30.0};
Point p4 = Point.fromArray(coords);

// From string
Point p5 = Point.fromString("10.0,20.0,30.0");

// Using utility methods
Point p6 = Proj4Sedona.toPoint(10.0, 20.0);
Point p7 = Proj4Sedona.toPoint(new double[]{10.0, 20.0, 30.0});
```

### Advanced Usage with Datum Transformations

```java
// Transform between different datums
Point point = new Point(-71.0, 41.0); // Boston, MA

// Transform from WGS84 to NAD83
Point nad83 = Proj4Sedona.transform(
    "+proj=longlat +datum=WGS84",
    "+proj=longlat +datum=NAD83",
    point
);

// Transform with custom 3-parameter datum
Point custom = Proj4Sedona.transform(
    "WGS84",
    "+proj=longlat +datum=WGS84 +towgs84=100,200,300",
    point
);

// Transform with 7-parameter datum
Point sevenParam = Proj4Sedona.transform(
    "WGS84",
    "+proj=longlat +datum=WGS84 +towgs84=100,200,300,1,2,3,1.000001",
    point
);

// Use different projections with datum transformations
Point utm = Proj4Sedona.transform(
    "+proj=longlat +datum=WGS84",
    "+proj=utm +zone=19 +datum=NAD83",
    point
);

// Load and use GeoTIFF datum grids from PROJ CDN
GeoTiffReader.GeoTiffGrid grid = Proj4Sedona.downloadGrid("ca_nrc_NA83SCRS.tif");
Point withGrid = Proj4Sedona.transform(
    "+proj=longlat +datum=WGS84",
    "+proj=longlat +datum=NAD83 +nadgrids=ca_nrc_NA83SCRS.tif",
    point
);
```

## GeoTIFF Datum Grid Support

Proj4Sedona now supports reading GeoTIFF datum grids, similar to proj4js. This allows you to use high-precision datum transformations by downloading grids from the PROJ CDN.

### Basic Usage

```java
// Download a datum grid from PROJ CDN
GeoTiffReader.GeoTiffGrid grid = Proj4Sedona.downloadGrid("ca_nrc_NA83SCRS.tif");

// Use the grid in transformations
Point point = new Point(-79.3832, 43.6532); // Toronto
Point transformed = Proj4Sedona.transform(
    "+proj=longlat +datum=WGS84",
    "+proj=longlat +datum=NAD83 +nadgrids=ca_nrc_NA83SCRS.tif",
    point
);
```

### Loading from Local Files

```java
// Load from a local file
try (FileInputStream fis = new FileInputStream("path/to/grid.tif")) {
    GeoTiffReader.GeoTiffGrid grid = Proj4Sedona.nadgrid("my_grid", fis);
}
```

### Cache Management

```java
// Check cache status
int cacheSize = Proj4Sedona.getGridCacheSize();
String[] cachedGrids = Proj4Sedona.getCachedGridKeys();

// Clear cache if needed
Proj4Sedona.clearGridCache();
```

### Available Grids

The PROJ CDN hosts many datum grids from various countries and organizations. Some examples:

- `ca_nrc_NA83SCRS.tif` - Canadian NAD83 to NAD83(CSRS) transformation
- `us_noaa_NAD83_NAD83_CSRS.tif` - US NAD83 transformations
- `au_ga_GDA94_GDA2020_conformal.tif` - Australian datum transformation

For a complete list, visit the [PROJ-data repository](https://github.com/OSGeo/PROJ-data).

## MGRS Coordinate Support

Proj4Sedona includes full support for MGRS (Military Grid Reference System) coordinates, allowing conversion between WGS84 lat/lng coordinates and MGRS strings.

### Basic Usage

```java
// Convert lat/lon to MGRS
String mgrs = Proj4Sedona.mgrsForward(-71.0, 41.0); // Boston, MA
System.out.println(mgrs); // "19TCH 12345 67890"

// Convert with custom accuracy (5 digits = 1 meter, 4 digits = 10 meters, etc.)
String mgrs1m = Proj4Sedona.mgrsForward(-71.0, 41.0, 5); // 1 meter accuracy
String mgrs10m = Proj4Sedona.mgrsForward(-71.0, 41.0, 4); // 10 meter accuracy

// Convert MGRS back to lat/lon
double[] coords = Proj4Sedona.mgrsToPoint("19TCH 12345 67890");
System.out.println("Longitude: " + coords[0] + ", Latitude: " + coords[1]);

// Get bounding box for MGRS string
double[] bbox = Proj4Sedona.mgrsInverse("19TCH 12345 67890");
// Returns [left, bottom, right, top] in WGS84 degrees

// Create Point from MGRS
Point point = Proj4Sedona.fromMGRS("19TCH 12345 67890");
```

### MGRS Features

- **Accuracy Control**: Specify precision from 100km to 1 meter
- **Bidirectional Conversion**: Convert between lat/lon and MGRS
- **Bounding Box Support**: Get spatial extent of MGRS grid cells
- **UTM Zone Handling**: Automatic UTM zone detection and management
- **Hemisphere Support**: Both northern and southern hemisphere coordinates

## PROJJSON Support

Proj4Sedona supports the modern PROJJSON format for coordinate reference system definitions, providing a more structured and interoperable way to define projections.

### Basic Usage

```java
// Parse PROJJSON string
String projJson = """
{
  "$schema": "https://proj.org/schemas/v0.7/projjson.schema.json",
  "type": "ProjectedCRS",
  "name": "WGS 84 / UTM zone 19N",
  "base_crs": {
    "type": "GeographicCRS",
    "name": "WGS 84"
  },
  "conversion": {
    "name": "UTM zone 19N",
    "method": {
      "name": "Transverse Mercator"
    },
    "parameters": [
      {"name": "Latitude of natural origin", "value": 0},
      {"name": "Longitude of natural origin", "value": -69},
      {"name": "Scale factor at natural origin", "value": 0.9996},
      {"name": "False easting", "value": 500000},
      {"name": "False northing", "value": 0}
    ]
  }
}
""";

Projection proj = Proj4Sedona.fromProjJson(projJson);

// Convert PROJ string to PROJJSON
ProjJsonDefinition definition = Proj4Sedona.toProjJson("+proj=utm +zone=19 +datum=WGS84");

// Convert PROJJSON to PROJ string
String projString = Proj4Sedona.toProjString(definition);
```

### PROJJSON Features

- **Full PROJJSON Support**: Parse and create PROJJSON definitions
- **Bidirectional Conversion**: Convert between PROJ strings and PROJJSON
- **Schema Validation**: Support for PROJJSON schema validation
- **Modern Format**: Use the latest coordinate reference system format
- **Interoperability**: Compatible with other PROJJSON implementations

## Performance Optimizations

Proj4Sedona includes several performance optimization features for processing large datasets efficiently.

### Batch Processing

```java
// Create a batch transformer for repeated transformations
BatchTransformer transformer = Proj4Sedona.createBatchTransformer("WGS84", "EPSG:3857");

// Transform multiple points efficiently
List<Point> points = Arrays.asList(
    new Point(-71.0, 41.0),
    new Point(-70.0, 42.0),
    new Point(-69.0, 43.0)
);
List<Point> transformed = transformer.transformBatch(points);

// Transform coordinate arrays
double[] xCoords = {-71.0, -70.0, -69.0};
double[] yCoords = {41.0, 42.0, 43.0};
Point[] results = Proj4Sedona.transformArrays("WGS84", "EPSG:3857", xCoords, yCoords);
```

### Cache Management

```java
// Check projection cache size
int cacheSize = Proj4Sedona.getProjectionCacheSize();

// Clear projection cache to free memory
Proj4Sedona.clearProjectionCache();

// Check grid cache status
int gridCacheSize = Proj4Sedona.getGridCacheSize();
String[] cachedGrids = Proj4Sedona.getCachedGridKeys();

// Clear grid cache
Proj4Sedona.clearGridCache();
```

### Performance Features

- **Batch Transformation**: Process multiple points efficiently
- **Projection Caching**: Cache parsed projections for reuse
- **Grid Caching**: Cache downloaded datum grids
- **Memory Management**: Clear caches to free memory
- **Optimized Math**: Fast mathematical operations for transformations

## Architecture

### Core Classes

- **`Point`**: Represents a coordinate point with x, y, z, and m components
- **`Projection`**: Represents a map projection with transformation methods
- **`Proj4Sedona`**: Main entry point with static transformation methods
- **`Converter`**: Provides forward/inverse transformation capabilities
- **`GeoTiffReader`**: Reads and processes GeoTIFF datum grids
- **`ProjCdnClient`**: Downloads grids from the PROJ CDN

### Constants

- **`Values`**: Mathematical constants and utility functions
- **`Ellipsoid`**: Ellipsoid definitions for various reference systems
- **`Datum`**: Datum definitions with transformation parameters

### Module Structure

Proj4Sedona is organized as a multi-module Maven project:

```
proj4sedona/
├── core/           # Core transformation functionality
│   └── src/main/java/org/apache/sedona/proj/
│       ├── core/           # Core transformation classes
│       ├── constants/      # Mathematical constants and definitions
│       ├── projections/    # Projection implementations
│       ├── datum/          # Datum transformation logic
│       ├── parse/          # PROJ string parsing
│       ├── projjson/       # PROJJSON support
│       ├── optimization/  # Performance optimizations
│       └── cache/         # Projection caching
├── mgrs/           # MGRS coordinate support
│   └── src/main/java/org/apache/sedona/proj/mgrs/
│       └── Mgrs.java      # MGRS coordinate conversion
└── wkt-parser/     # WKT parsing functionality
    └── src/main/java/org/apache/sedona/proj/wkt/
        ├── WKTParser.java     # WKT parsing
        ├── WKTProcessor.java  # WKT processing
        └── WKTUtils.java      # WKT utilities
```

### Package Structure

```
org.apache.sedona.proj/
├── core/           # Core transformation classes
├── constants/      # Mathematical constants and definitions
├── projections/    # Projection implementations
├── datum/          # Datum transformation logic
│   ├── GeoTiffReader.java    # GeoTIFF grid reading
│   └── ProjCdnClient.java    # PROJ CDN integration
├── parse/          # PROJ string parsing
├── projjson/       # PROJJSON support
├── optimization/   # Performance optimizations
├── cache/          # Projection caching
└── common/         # Common mathematical utilities
```

## Supported Projections

Currently implemented:
- **WGS84** (EPSG:4326): Longitude/Latitude coordinate system
- **Mercator**: Web Mercator projection (EPSG:3857)
- **Lambert Conformal Conic**: Conic projection for mid-latitude regions
- **UTM**: Universal Transverse Mercator projection
- **Transverse Mercator**: Base projection for UTM
- **Albers Equal Area**: Equal-area conic projection
- **Sinusoidal**: Sinusoidal projection for equal-area mapping
- **Equidistant Conic**: Conic projection with equidistant meridians
- **Hotine Oblique Mercator**: Oblique Mercator projection

## Supported Datum Transformations

- **3-Parameter Helmert**: Translation-only transformations (dx, dy, dz)
- **7-Parameter Helmert**: Full Helmert transformations (dx, dy, dz, rx, ry, rz, scale)
- **Grid-Based**: NTv2 and GeoTIFF grid-based datum adjustments
- **GeoTIFF Support**: Download and use datum grids from PROJ CDN
- **Built-in Datums**: WGS84, NAD83, NAD27, and other common datums

## Building

```bash
# Clone the repository
git clone <repository-url>
cd proj4sedona

# Build the project
mvn clean compile

# Run tests
mvn test

# Package
mvn package
```

## Testing

The project includes comprehensive unit tests:

```bash
mvn test
```

Test coverage includes:
- Point creation and manipulation
- Basic coordinate transformations
- Converter functionality
- Utility methods

## Roadmap

### Phase 1 (Current)
- ✅ Basic project structure
- ✅ Core Point and Projection classes
- ✅ WGS84 and basic Mercator projections
- ✅ Unit tests

### Phase 2 (Completed)
- ✅ Additional projection implementations (Lambert, UTM, etc.)
- ✅ PROJ string parsing
- ✅ WKT (Well-Known Text) support
- ✅ Datum transformations
- ✅ Grid-based datum adjustments

### Phase 3 (Completed)
- ✅ PROJJSON support
- ✅ MGRS coordinate support
- ✅ Performance optimizations
- ✅ Additional mathematical utilities

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Ensure all tests pass
6. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- Based on the original [Proj4js](https://github.com/proj4js/proj4js) JavaScript library
- Inspired by the [PROJ](https://proj.org/) C++ library
- Part of the [MetaCRS](https://trac.osgeo.org/metacrs/wiki) group of projects

## API Reference

### Proj4Sedona Class

#### Static Methods

**Core Transformation Methods:**
- `transform(String toProj, Point coords)` - Transform coordinates from WGS84 to specified projection
- `transform(String fromProj, String toProj, Point coords)` - Transform between two projections
- `converter(String toProj)` - Create a converter from WGS84 to specified projection
- `converter(String fromProj, String toProj)` - Create a converter between two projections
- `toPoint(double x, double y)` - Create a point from coordinates
- `toPoint(double x, double y, double z)` - Create a point from coordinates with Z
- `toPoint(double[] coords)` - Create a point from coordinate array
- `getVersion()` - Get library version

**MGRS Methods:**
- `mgrsForward(double lon, double lat)` - Convert lat/lon to MGRS with default accuracy
- `mgrsForward(double lon, double lat, int accuracy)` - Convert lat/lon to MGRS with custom accuracy
- `mgrsInverse(String mgrs)` - Convert MGRS to lat/lon bounding box
- `mgrsToPoint(String mgrs)` - Convert MGRS to lat/lon point
- `fromMGRS(String mgrs)` - Create Point from MGRS string

**PROJJSON Methods:**
- `fromProjJson(String projJsonString)` - Create projection from PROJJSON string
- `fromProjJson(ProjJsonDefinition definition)` - Create projection from PROJJSON definition
- `toProjJson(String projString)` - Convert PROJ string to PROJJSON definition
- `toProjString(ProjJsonDefinition definition)` - Convert PROJJSON to PROJ string

**Performance Methods:**
- `createBatchTransformer(String fromProj, String toProj)` - Create batch transformer
- `transformBatch(String fromProj, String toProj, List<Point> points)` - Transform multiple points
- `transformArrays(String fromProj, String toProj, double[] xCoords, double[] yCoords)` - Transform coordinate arrays

**Cache Management:**
- `clearProjectionCache()` - Clear projection cache
- `getProjectionCacheSize()` - Get projection cache size
- `clearGridCache()` - Clear grid cache
- `getGridCacheSize()` - Get grid cache size
- `getCachedGridKeys()` - Get cached grid keys

**GeoTIFF Grid Methods:**
- `nadgrid(String key, String url)` - Load grid from URL
- `nadgrid(String key, InputStream inputStream)` - Load grid from stream
- `registerNadgrid(String key, GeoTiffGrid grid)` - Register grid
- `getNadgrid(String key)` - Get registered grid
- `hasNadgrid(String key)` - Check if grid is registered
- `removeNadgrid(String key)` - Remove grid
- `getNadgridNames()` - Get all grid names
- `getNadgridCount()` - Get grid count
- `clearNadgrids()` - Clear all grids

**PROJ CDN Methods:**
- `downloadGrid(String gridName)` - Download grid from PROJ CDN
- `downloadGrid(String key, String gridName)` - Download grid with custom key
- `downloadGridFromUrl(String key, String url)` - Download grid from custom URL
- `isGridCached(String key)` - Check if grid is cached
- `removeFromCache(String key)` - Remove grid from cache

### Point Class

#### Constructors
- `Point()` - Default point (0,0,0,0)
- `Point(double x, double y)` - 2D point
- `Point(double x, double y, double z)` - 3D point
- `Point(double x, double y, double z, double m)` - 4D point

#### Static Methods
- `fromArray(double[] coords)` - Create from array
- `fromString(String coordString)` - Create from string

#### Instance Methods
- `copy()` - Create a copy
- `toArray()` - Convert to array
- `hasZ()` - Check if Z coordinate is defined
- `hasM()` - Check if M coordinate is defined

### Converter Class

#### Methods
- `forward(Point coords)` - Transform forward
- `inverse(Point coords)` - Transform inverse
- `getProjection()` - Get destination projection (for single projections)
