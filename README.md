# Proj4Java

A Java port of the Proj4js library for coordinate system transformations.

## Overview

Proj4Java is a Java library that provides coordinate system transformations, similar to the popular JavaScript Proj4js library. It allows you to transform point coordinates from one coordinate system to another, including datum transformations.

## Features

- **Coordinate System Transformations**: Transform coordinates between different projections
- **Multiple Projection Support**: Currently supports WGS84 (longitude/latitude) and basic Mercator projections
- **Extensible Architecture**: Easy to add new projection implementations
- **Type Safety**: Full Java type safety with proper error handling
- **Maven Integration**: Ready-to-use Maven project structure

## Quick Start

### Maven Dependency

Add this to your `pom.xml`:

```xml
<dependency>
    <groupId>org.proj4</groupId>
    <artifactId>proj4java</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Basic Usage

```java
import org.proj4.Proj4Java;
import org.proj4.core.Point;

// Create a point
Point point = new Point(-71.0, 41.0); // Longitude, Latitude

// Transform from WGS84 to WGS84 (identity transformation)
Point result = Proj4Java.transform("WGS84", point);
System.out.println(result); // Point{x=-71.000000, y=41.000000}

// Create a converter for repeated transformations
Proj4Java.Converter converter = Proj4Java.converter("WGS84");
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
Point p6 = Proj4Java.toPoint(10.0, 20.0);
Point p7 = Proj4Java.toPoint(new double[]{10.0, 20.0, 30.0});
```

## Architecture

### Core Classes

- **`Point`**: Represents a coordinate point with x, y, z, and m components
- **`Projection`**: Represents a map projection with transformation methods
- **`Proj4Java`**: Main entry point with static transformation methods
- **`Converter`**: Provides forward/inverse transformation capabilities

### Constants

- **`Values`**: Mathematical constants and utility functions
- **`Ellipsoid`**: Ellipsoid definitions for various reference systems
- **`Datum`**: Datum definitions with transformation parameters

### Package Structure

```
org.proj4/
├── core/           # Core transformation classes
├── constants/      # Mathematical constants and definitions
├── projections/    # Projection implementations (future)
├── datum/          # Datum transformation logic (future)
└── transform/      # Coordinate transformation logic (future)
```

## Supported Projections

Currently implemented:
- **WGS84** (EPSG:4326): Longitude/Latitude coordinate system
- **Basic Mercator**: Web Mercator projection (EPSG:3857)

## Building

```bash
# Clone the repository
git clone <repository-url>
cd proj4java

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

### Phase 2 (Planned)
- [ ] Additional projection implementations (Lambert, UTM, etc.)
- [ ] PROJ string parsing
- [ ] WKT (Well-Known Text) support
- [ ] Datum transformations
- [ ] Grid-based datum adjustments

### Phase 3 (Future)
- [ ] PROJJSON support
- [ ] MGRS coordinate support
- [ ] Performance optimizations
- [ ] Additional mathematical utilities

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

### Proj4Java Class

#### Static Methods

- `transform(String toProj, Point coords)` - Transform coordinates from WGS84 to specified projection
- `transform(String fromProj, String toProj, Point coords)` - Transform between two projections
- `converter(String toProj)` - Create a converter from WGS84 to specified projection
- `converter(String fromProj, String toProj)` - Create a converter between two projections
- `toPoint(double x, double y)` - Create a point from coordinates
- `toPoint(double[] coords)` - Create a point from coordinate array
- `getVersion()` - Get library version

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
