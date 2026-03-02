# JTS Integration

Proj4Sedona integrates with the [JTS Topology Suite](https://github.com/locationtech/jts) to transform JTS geometry objects between coordinate reference systems. The `JTSGeometryTransformer` class handles all JTS geometry types including Point, LineString, Polygon, and their Multi variants.

## Setup

The JTS integration uses the `org.locationtech.jts` library. Make sure it is on your classpath (it is included as a dependency of Proj4Sedona).

## Creating a Transformer

### From CRS Strings

```java
import org.datasyslab.proj4sedona.jts.JTSGeometryTransformer;

// Using PROJ strings
JTSGeometryTransformer transformer = new JTSGeometryTransformer(
    "+proj=longlat +datum=WGS84",
    "+proj=merc +a=6378137 +b=6378137 +lat_ts=0 +lon_0=0 +x_0=0 +y_0=0 +k=1 +units=m"
);

// Using EPSG codes
JTSGeometryTransformer transformer2 = new JTSGeometryTransformer("EPSG:4326", "EPSG:3857");
```

### From Proj Objects

```java
import org.datasyslab.proj4sedona.core.Proj;

Proj from = new Proj("EPSG:4326");
Proj to = new Proj("EPSG:3857");
JTSGeometryTransformer transformer = new JTSGeometryTransformer(from, to);
```

### From a Converter

```java
import org.datasyslab.proj4sedona.transform.Converter;

Converter conv = Proj4.proj4("EPSG:4326", "EPSG:3857");
JTSGeometryTransformer transformer = new JTSGeometryTransformer(conv);
```

### With a Custom GeometryFactory

```java
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

GeometryFactory gf = new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), 3857);
JTSGeometryTransformer transformer = new JTSGeometryTransformer("EPSG:4326", "EPSG:3857", gf);
```

## Transforming Geometries

### Point

```java
import org.locationtech.jts.geom.*;

GeometryFactory gf = new GeometryFactory();
JTSGeometryTransformer transformer = new JTSGeometryTransformer(
    "+proj=longlat +datum=WGS84",
    "+proj=utm +zone=10 +datum=WGS84 +units=m"
);

// Transform a point (San Francisco)
Point sfLonLat = gf.createPoint(new Coordinate(-122.4194, 37.7749));
Geometry sfUtm = transformer.transform(sfLonLat);
System.out.println("UTM: " + sfUtm);
```

### LineString

```java
Coordinate[] coords = {
    new Coordinate(-122.4194, 37.7749),  // San Francisco
    new Coordinate(-118.2437, 34.0522),  // Los Angeles
    new Coordinate(-117.1611, 32.7157)   // San Diego
};
LineString line = gf.createLineString(coords);
Geometry transformed = transformer.transform(line);
```

### Polygon

```java
Coordinate[] shell = {
    new Coordinate(-122.5, 37.7),
    new Coordinate(-122.3, 37.7),
    new Coordinate(-122.3, 37.8),
    new Coordinate(-122.5, 37.8),
    new Coordinate(-122.5, 37.7)  // closed ring
};
Polygon polygon = gf.createPolygon(shell);
Geometry transformed = transformer.transform(polygon);
```

### Polygon with Hole

```java
// Outer ring
LinearRing outerRing = gf.createLinearRing(new Coordinate[]{
    new Coordinate(-122.5, 37.7),
    new Coordinate(-122.3, 37.7),
    new Coordinate(-122.3, 37.9),
    new Coordinate(-122.5, 37.9),
    new Coordinate(-122.5, 37.7)
});

// Inner hole
LinearRing hole = gf.createLinearRing(new Coordinate[]{
    new Coordinate(-122.45, 37.75),
    new Coordinate(-122.35, 37.75),
    new Coordinate(-122.35, 37.85),
    new Coordinate(-122.45, 37.85),
    new Coordinate(-122.45, 37.75)
});

Polygon polygon = gf.createPolygon(outerRing, new LinearRing[]{hole});
Geometry transformed = transformer.transform(polygon);
```

### MultiPoint

```java
Point[] points = {
    gf.createPoint(new Coordinate(-122.4194, 37.7749)),
    gf.createPoint(new Coordinate(-118.2437, 34.0522)),
    gf.createPoint(new Coordinate(-73.9857, 40.7484))
};
MultiPoint multiPoint = gf.createMultiPoint(points);
Geometry transformed = transformer.transform(multiPoint);
```

### MultiLineString, MultiPolygon, GeometryCollection

The `transform()` method handles all geometry types automatically. It inspects the geometry type and delegates to the appropriate method:

```java
// Works with any geometry type
Geometry result = transformer.transform(anyGeometry);
```

You can also call type-specific methods directly:

```java
Point result = transformer.transformPoint(point);
LineString result = transformer.transformLineString(lineString);
Polygon result = transformer.transformPolygon(polygon);
MultiPoint result = transformer.transformMultiPoint(multiPoint);
MultiLineString result = transformer.transformMultiLineString(multiLineString);
MultiPolygon result = transformer.transformMultiPolygon(multiPolygon);
GeometryCollection result = transformer.transformGeometryCollection(collection);
```

## Inverse Transform

Transform back from target CRS to source CRS:

```java
JTSGeometryTransformer transformer = new JTSGeometryTransformer("EPSG:4326", "EPSG:3857");

// Forward: WGS84 -> Web Mercator
Geometry mercator = transformer.transform(lonLatPoint);

// Inverse: Web Mercator -> WGS84
Geometry backToLonLat = transformer.inverse(mercator);
```

## Coordinate-Level Transform

Transform individual coordinates or coordinate arrays:

```java
// Single coordinate
Coordinate wgs84 = new Coordinate(-77.0, 38.9);
Coordinate utm = transformer.transformCoordinate(wgs84);

// Array of coordinates
Coordinate[] coords = {
    new Coordinate(-77.0, 38.9),
    new Coordinate(-77.5, 39.0)
};
Coordinate[] transformed = transformer.transformCoordinates(coords);
```

## Static Convenience Methods

For one-off transforms without creating a transformer instance:

```java
// Transform a geometry
Geometry result = JTSGeometryTransformer.transform("EPSG:4326", "EPSG:3857", geometry);

// Transform a coordinate
Coordinate result = JTSGeometryTransformer.transform("EPSG:4326", "EPSG:3857", coordinate);

// Transform a coordinate array
Coordinate[] results = JTSGeometryTransformer.transform("EPSG:4326", "EPSG:3857", coordArray);
```

## Cached Transformer

For repeated use, create a cached transformer that reuses parsed projections:

```java
JTSGeometryTransformer cached = JTSGeometryTransformer.cached("EPSG:4326", "EPSG:3857");

// Use for many geometries
for (Geometry geom : geometries) {
    Geometry transformed = cached.transform(geom);
    // ...
}
```

## Complete Example

Transform a set of city points from WGS84 to UTM, process them, and transform back:

```java
import org.datasyslab.proj4sedona.jts.JTSGeometryTransformer;
import org.locationtech.jts.geom.*;

GeometryFactory gf = new GeometryFactory();
JTSGeometryTransformer transformer = new JTSGeometryTransformer("EPSG:4326", "EPSG:32618");

// Create city points in WGS84 (lon/lat)
Point dc = gf.createPoint(new Coordinate(-77.0369, 38.9072));
Point nyc = gf.createPoint(new Coordinate(-73.9857, 40.7484));
Point philadelphia = gf.createPoint(new Coordinate(-75.1652, 39.9526));

// Transform to UTM zone 18N (meters)
Point dcUtm = (Point) transformer.transform(dc);
Point nycUtm = (Point) transformer.transform(nyc);
Point philaUtm = (Point) transformer.transform(philadelphia);

// Calculate distance in meters (using UTM coordinates)
double dcToNyc = dcUtm.distance(nycUtm);
System.out.printf("DC to NYC: %.0f meters%n", dcToNyc);

// Transform back to WGS84
Point dcBack = (Point) transformer.inverse(dcUtm);
System.out.printf("Round-trip: (%.4f, %.4f)%n", dcBack.getX(), dcBack.getY());
```

## See Also

- [Coordinate Transformations](coordinate-transformations.md) -- non-JTS coordinate transforms
- [CRS Formats](crs-formats.md) -- supported CRS input formats
