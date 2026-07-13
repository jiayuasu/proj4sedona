# Coordinate Transformations

This guide covers all the ways to transform coordinates with Proj4Sedona, from single-point transforms to high-performance batch operations.

## Single-Point Transforms

### Using Arrays

The simplest way to transform a coordinate:

```java
import org.datasyslab.proj4sedona.Proj4;

// From WGS84 (lon/lat) to Web Mercator (meters)
double[] result = Proj4.proj4(
    "+proj=longlat +datum=WGS84",
    "+proj=merc +a=6378137 +b=6378137 +lat_ts=0 +lon_0=0 +x_0=0 +y_0=0 +k=1 +units=m",
    new double[]{-77.0369, 38.9072}
);
// result[0] = X in meters, result[1] = Y in meters
```

### Using Point Objects

```java
import org.datasyslab.proj4sedona.core.Point;

Point p = new Point(-77.0369, 38.9072);
Point result = Proj4.proj4(
    "+proj=longlat +datum=WGS84",
    "+proj=utm +zone=18 +datum=WGS84",
    p
);
System.out.println("Easting: " + result.x + ", Northing: " + result.y);
```

### From WGS84 (Single Argument)

When the source CRS is WGS84, you can omit it:

```java
// These are equivalent
Point result1 = Proj4.proj4("EPSG:4326", "EPSG:3857", new Point(-77.0, 38.9));
Point result2 = Proj4.proj4("EPSG:3857", new Point(-77.0, 38.9));
```

## Reusable Converters

For repeated transforms between the same CRS pair, create a `Converter` to avoid re-parsing the CRS definitions each time:

```java
import org.datasyslab.proj4sedona.transform.Converter;

// Create once
Converter conv = Proj4.proj4("EPSG:4326", "EPSG:3857");

// Use many times
Point result1 = conv.forward(new Point(-77.0, 38.9));
Point result2 = conv.forward(new Point(-122.4, 37.8));
Point result3 = conv.forward(new Point(0.0, 51.5));

// Inverse transform (target -> source)
Point backToLonLat = conv.inverse(result1);
```

You can also pass arrays to a converter:

```java
double[] meters = conv.forward(new double[]{-77.0, 38.9});
double[] degrees = conv.inverse(meters);
```

### Cached Converters

Use `cachedConverter` for converters that also cache their underlying `Proj` objects:

```java
Converter conv = Proj4.cachedConverter("EPSG:4326", "EPSG:3857");
```

### Building Converters from Proj Objects

```java
import org.datasyslab.proj4sedona.core.Proj;

Proj from = new Proj("+proj=longlat +datum=WGS84");
Proj to = new Proj("+proj=utm +zone=32 +datum=WGS84");
Converter conv = Proj4.converter(from, to);
```

### Using Transform Directly

For one-off transforms with existing `Proj` objects:

```java
import org.datasyslab.proj4sedona.transform.Transform;

Proj src = new Proj("EPSG:4326");
Proj dst = new Proj("EPSG:32618");
Point result = Transform.transform(src, dst, new Point(-77.0, 38.9));
```

## Batch Transformations

For transforming many coordinates at once, batch methods are significantly faster than looping over single-point transforms.

### 2D Array Batches

Transform an array of `[x, y]` pairs:

```java
double[][] coords = {
    {-77.0, 38.9},    // Washington, DC
    {-122.4, 37.8},   // San Francisco
    {0.0, 51.5},       // London
    {139.7, 35.7},     // Tokyo
    {151.2, -33.9}     // Sydney
};

double[][] results = Proj4.transformBatch(
    "+proj=longlat +datum=WGS84",
    "+proj=merc +datum=WGS84",
    coords
);

for (double[] r : results) {
    System.out.println("X: " + r[0] + ", Y: " + r[1]);
}
```

### Flat Array Batches

For coordinates stored as `[x1, y1, x2, y2, ...]`, the flat method avoids array-of-array overhead:

```java
double[] flat = {
    -77.0, 38.9,
    -122.4, 37.8,
    0.0, 51.5
};

double[] results = Proj4.transformFlat(
    "+proj=longlat +datum=WGS84",
    "+proj=merc +datum=WGS84",
    flat
);
// results = [x1, y1, x2, y2, x3, y3]
```

### 3D Flat Array Batches

For coordinates with elevation, stored as `[x1, y1, z1, x2, y2, z2, ...]`:

```java
double[] flat3d = {
    -77.0, 38.9, 100.0,
    -122.4, 37.8, 50.0
};

double[] results = Proj4.transformFlat3D(
    "+proj=longlat +datum=WGS84",
    "+proj=merc +datum=WGS84",
    flat3d
);
// results = [x1, y1, z1, x2, y2, z2]
```

## Axis Order and Enforcement

Some CRS definitions specify non-standard axis orders (e.g., northing/easting instead of easting/northing). By default, Proj4Sedona assumes standard ENU (east, north, up) order and does not reorder coordinates. Set the `enforceAxis` parameter on `Converter` to honor the axis order declared in the CRS definition:

```java
// A source CRS whose declared axis order is northing, easting, up.
String neuSource = "+proj=longlat +datum=WGS84 +axis=neu +no_defs";
Converter conv = Proj4.proj4(neuSource, "EPSG:32618");

// Default: assumes standard ENU order; the +axis=neu declaration is ignored,
// so the point is still read as (longitude, latitude).
Point result1 = conv.forward(new Point(-77.0, 38.9));

// enforceAxis=true: honor the CRS-declared order, so the point is read as
// (north, east) = (latitude, longitude). This yields the same projected
// coordinate as result1 above.
Point result2 = conv.forward(new Point(38.9, -77.0), true);
```

## Choosing the Right Method

| Method | Best for | Notes |
|--------|----------|-------|
| `Proj4.proj4(from, to, coord)` | One-off transforms or a few points | Parses CRS each call |
| `Converter.forward(point)` | Repeated transforms with the same CRS pair | Parse once, use many times |
| `Proj4.cachedConverter(from, to)` | Application-wide reuse | Caches both `Proj` objects and `Converter` |
| `Proj4.transformBatch(from, to, coords)` | 100+ coordinate pairs | ~30% faster than looping |
| `Proj4.transformFlat(from, to, flat)` | Flat `[x1,y1,x2,y2,...]` arrays | Most memory-efficient |
| `Proj4.transformFlat3D(from, to, flat)` | 3D flat arrays with elevation | Preserves Z values |

## See Also

- [Caching and Performance](caching-and-performance.md) -- projection caching and preloading
- [JTS Integration](jts-integration.md) -- transforming JTS geometry objects
- [CRS Formats](crs-formats.md) -- all supported CRS input formats
