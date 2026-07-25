# Getting Started

This guide covers installation, basic usage, and core concepts of Proj4Sedona.

## Installation

### Maven

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>org.datasyslab</groupId>
    <artifactId>proj4sedona</artifactId>
    <version>0.1.3</version>
</dependency>
```

### Gradle

```groovy
implementation 'org.datasyslab:proj4sedona:0.1.3'
```

### Building from Source

```bash
git clone https://github.com/jiayuasu/proj4sedona.git
cd proj4sedona
mvn clean install
```

## Your First Transformation

Transform a longitude/latitude coordinate (WGS84) to Web Mercator:

```java
import org.datasyslab.proj4sedona.Proj4;

double[] result = Proj4.proj4(
    "+proj=longlat +datum=WGS84",
    "+proj=merc +a=6378137 +b=6378137 +lat_ts=0 +lon_0=0 +x_0=0 +y_0=0 +k=1 +units=m",
    new double[]{-77.0369, 38.9072}
);
System.out.println("X: " + result[0] + ", Y: " + result[1]);
```

Or use EPSG codes:

```java
double[] result = Proj4.proj4("EPSG:4326", "EPSG:3857", new double[]{-77.0369, 38.9072});
```

## Core Concepts

### Proj

A `Proj` object represents a parsed coordinate reference system (CRS). You can create one from any supported format:

```java
import org.datasyslab.proj4sedona.core.Proj;

Proj wgs84 = new Proj("+proj=longlat +datum=WGS84");
Proj utm18 = new Proj("EPSG:32618");
Proj webMerc = new Proj("EPSG:3857");
```

`Proj` objects can also be exported back to different formats:

```java
String projStr = wgs84.toProjString();
String wkt1 = wgs84.toWkt1();
String wkt2 = wgs84.toWkt2();
String json = wgs84.toProjJson();
```

### Point

A `Point` holds x, y, and optionally z and m coordinates:

```java
import org.datasyslab.proj4sedona.core.Point;

Point p1 = new Point(-77.0, 38.9);
Point p2 = new Point(-77.0, 38.9, 100.0);      // with elevation
Point p3 = new Point(-77.0, 38.9, 100.0, 0.5);  // with elevation and measure
Point p4 = new Point(new double[]{-77.0, 38.9});

// Factory methods
Point p5 = Proj4.point(-77.0, 38.9);
Point p6 = Proj4.point(-77.0, 38.9, 100.0);
```

### Converter

A `Converter` is a reusable transformer between two CRS. Create one and use it for repeated transforms:

```java
import org.datasyslab.proj4sedona.transform.Converter;

Converter conv = Proj4.proj4("EPSG:4326", "EPSG:3857");

// Transform forward (source -> target)
Point mercator = conv.forward(new Point(-77.0, 38.9));

// Transform inverse (target -> source)
Point lonLat = conv.inverse(mercator);
```

This is more efficient than calling `Proj4.proj4()` with strings each time, since the CRS definitions are parsed only once.

### Supported Input Formats

Proj4Sedona accepts CRS definitions in several formats:

| Format | Example | Detection |
|--------|---------|-----------|
| PROJ string | `+proj=utm +zone=18 +datum=WGS84` | Starts with `+` |
| WKT1 | `PROJCS["WGS 84 / UTM zone 18N", ...]` | Starts with `PROJCS`, `GEOGCS`, etc. |
| WKT2 | `PROJCRS["WGS 84 / UTM zone 18N", ...]` | Starts with `PROJCRS`, `GEOGCRS`, etc. |
| PROJJSON | `{"$schema": "...", "type": "ProjectedCRS", ...}` | Starts with `{` |
| EPSG code | `EPSG:4326` | Pattern `AUTHORITY:CODE` |
| Shorthand | `WGS84` | Pre-registered aliases |

See [CRS Formats](crs-formats.md) for details on each format.

## Next Steps

- [Coordinate Transformations](coordinate-transformations.md) -- batch transforms and performance tips
- [Projections](projections.md) -- all supported map projections
- [JTS Integration](jts-integration.md) -- transforming JTS geometries
- [MGRS Coordinates](mgrs.md) -- Military Grid Reference System conversion
