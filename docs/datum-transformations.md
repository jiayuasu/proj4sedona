# Datum Transformations

A datum defines the reference frame used to measure positions on the Earth. When transforming between CRS that use different datums, a datum shift is applied automatically. This guide explains how datum transformations work in Proj4Sedona.

## How Datum Shifts Work

When you transform coordinates between two CRS with different datums, Proj4Sedona applies the following pipeline:

1. Inverse projection (projected coordinates to geographic)
2. Convert to geocentric (cartesian XYZ)
3. Apply datum shift (translation, rotation, scale)
4. Convert back to geographic
5. Forward projection (geographic to projected coordinates)

This happens automatically when you call `Proj4.proj4()` or use a `Converter`.

## 3-Parameter Datum Shift

The simplest datum shift uses three translation values (dX, dY, dZ in meters) to move the origin:

```java
// GGRS87 uses a 3-parameter shift: -199.87, 74.79, 246.62
double[] result = Proj4.proj4(
    "+proj=longlat +ellps=GRS80 +towgs84=-199.87,74.79,246.62 +no_defs",
    "+proj=longlat +datum=WGS84",
    new double[]{23.7275, 37.9838}
);
```

The `+towgs84` parameter with 3 values specifies dX, dY, dZ.

## 7-Parameter Datum Shift (Helmert)

For higher accuracy, a 7-parameter Helmert transformation includes three translations, three rotations (in arc-seconds), and a scale factor (in ppm):

```java
// OSGB36 uses 7 parameters: dX, dY, dZ, rX, rY, rZ, scale
double[] result = Proj4.proj4(
    "+proj=longlat +ellps=airy +towgs84=446.448,-125.157,542.060,0.1502,0.2470,0.8421,-20.4894",
    "+proj=longlat +datum=WGS84",
    new double[]{-2.0, 49.0}
);
```

The 7 `+towgs84` values are: dX, dY, dZ (meters), rX, rY, rZ (arc-seconds), dS (ppm).

## Grid-Based Datum Shift

For the highest accuracy, some datums use grid shift files (NTv2 or GeoTIFF) that provide spatially varying corrections:

```java
// NAD27 uses grid files for the shift
double[] result = Proj4.proj4(
    "+proj=longlat +datum=NAD27",
    "+proj=longlat +datum=NAD83",
    new double[]{-77.0, 38.9}
);
```

NAD27 is defined with `+nadgrids=@conus,@alaska,@ntv2_0.gsb,@ntv1_can.dat`, so the appropriate grid file is used based on the location.

See [Grid Shifts](grid-shifts.md) for details on loading and configuring grid files.

## Specifying Datums

### Using Built-In Datum Names

```java
Proj wgs84 = new Proj("+proj=longlat +datum=WGS84");
Proj nad83 = new Proj("+proj=longlat +datum=NAD83");
Proj nad27 = new Proj("+proj=longlat +datum=NAD27");
Proj osgb36 = new Proj("+proj=longlat +datum=OSGB36");
```

### Using Ellipsoid and towgs84

```java
// Equivalent to +datum=OSGB36
Proj osgb = new Proj("+proj=longlat +ellps=airy " +
    "+towgs84=446.448,-125.157,542.060,0.1502,0.2470,0.8421,-20.4894");
```

### Using Ellipsoid Dimensions Directly

```java
// Custom ellipsoid with semi-major axis and inverse flattening
Proj custom = new Proj("+proj=longlat +a=6378137 +rf=298.257223563 +towgs84=0,0,0");
```

## Built-In Datums

Proj4Sedona includes definitions for common datums. Each datum specifies an ellipsoid and shift parameters.

| Datum Code | Ellipsoid | Shift Type | Datum Name |
|------------|-----------|------------|------------|
| `wgs84` | WGS84 | 3-param (identity) | WGS 1984 |
| `nad83` | GRS80 | 3-param (identity) | North American Datum 1983 |
| `nad27` | clrk66 | Grid files | North American Datum 1927 |
| `osgb36` | airy | 7-param | Ordnance Survey of Great Britain 1936 |
| `potsdam` | bessel | 7-param | Potsdam Rauenberg 1950 DHDN |
| `ch1903` | bessel | 3-param | Swiss CH1903 |
| `ggrs87` | GRS80 | 3-param | Greek Geodetic Reference System 1987 |
| `carthage` | clark80 | 3-param | Carthage 1934 Tunisia |
| `hermannskogel` | bessel | 7-param | Hermannskogel |
| `mgi` | bessel | 7-param | Militar-Geographische Institut |
| `osni52` | airy | 7-param | Irish National |
| `ire65` | mod_airy | 7-param | Ireland 1965 |
| `rassadiran` | intl | 3-param | Rassadiran |
| `nzgd49` | intl | 7-param | New Zealand Geodetic Datum 1949 |
| `s_jtsk` | bessel | 3-param | S-JTSK (Ferro) |
| `beduaram` | clrk80 | 3-param | Beduaram |
| `gunung_segara` | bessel | 3-param | Gunung Segara Jakarta |
| `rnb72` | intl | 7-param | Reseau National Belge 1972 |

Datum names are resolved case-insensitively. Aliases like "World Geodetic System 1984" and "WGS 84" also resolve to `wgs84`.

## Example: Datum Round-Trip

```java
import org.datasyslab.proj4sedona.Proj4;
import org.datasyslab.proj4sedona.transform.Converter;

// Create a converter from OSGB36 to WGS84
Converter conv = Proj4.proj4(
    "+proj=longlat +datum=OSGB36",
    "+proj=longlat +datum=WGS84"
);

// Forward: OSGB36 -> WGS84
Point wgs84Point = conv.forward(new Point(-2.0, 49.0));

// Inverse: WGS84 -> OSGB36
Point osgbPoint = conv.inverse(wgs84Point);
// osgbPoint should be close to (-2.0, 49.0) within datum accuracy
```

## Example: Projected CRS with Different Datums

```java
// Transform from British National Grid (OSGB36) to UTM zone 30N (WGS84)
double[] result = Proj4.proj4(
    "+proj=tmerc +lat_0=49 +lon_0=-2 +k=0.9996012717 +x_0=400000 +y_0=-100000 " +
        "+ellps=airy +towgs84=446.448,-125.157,542.060,0.1502,0.2470,0.8421,-20.4894 +units=m",
    "+proj=utm +zone=30 +datum=WGS84 +units=m",
    new double[]{400000, -100000}
);
```

The datum shift is applied automatically between the two projections.

## See Also

- [Grid Shifts](grid-shifts.md) -- loading and using NTv2/GeoTIFF grid files
- [Constants Reference](constants-reference.md) -- full list of datums and ellipsoids
- [CRS Formats](crs-formats.md) -- specifying datums in different CRS formats
