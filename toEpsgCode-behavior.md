# `Proj.toEpsgCode()` behavior with PROJJSON input

## Summary

`Proj.toEpsgCode()` performs **CRS identification by parameters**, not **metadata extraction**. When given a PROJJSON string, it parses the mathematical CRS definition (datum, ellipsoid, projection parameters), builds a proj4j `CoordinateReferenceSystem` object, and compares it against its internal EPSG database to find the best match. It does **not** read the `id.authority` / `id.code` fields from the PROJJSON.

This is a problem for use cases like GeoParquet SRID preservation, where we need to read the *declared* EPSG code from metadata rather than *infer* one from CRS parameters.

## Test results (proj4sedona 0.0.4)

| Input | `id` field says | `toEpsgCode()` returns | Issue |
|---|---|---|---|
| NAD83(2011) full PROJJSON | EPSG:6318 | **EPSG:4326** | Silently remapped — GRS 1980 ≈ WGS 84 |
| UTM 32N minimal PROJJSON | EPSG:32632 | **IllegalArgumentException** | Cannot parse PROJJSON without full CRS definition |
| No `id` field (GRS 1980 ellipsoid) | *(none)* | **EPSG:4326** | Guessed 4326 from ellipsoid parameters |
| `"EPSG:4326"` string | *(direct)* | EPSG:4326 | OK — simple string codes work |
| `"EPSG:32632"` string | *(direct)* | EPSG:32632 | OK — simple string codes work |
| IAU:49900 (Mars) | IAU:49900 | **EPSG:4326** | Non-EPSG authority silently mapped to 4326 |
| UTM 32N full PROJJSON | EPSG:32632 | **null** | Projected CRS identification fails |

## Root cause analysis

`toEpsgCode()` works in two phases:

1. **Parse** the input into a proj4j `CoordinateReferenceSystem` — this extracts the mathematical CRS definition (datum, ellipsoid, coordinate system, projection)
2. **Identify** by comparing those parameters against the internal EPSG database

This explains each failure:

- **NAD83(2011) → 4326**: GRS 1980 ellipsoid (semi_major_axis=6378137, inverse_flattening=298.257222101) is nearly identical to WGS 84 (298.257223563). The parameter matcher can't distinguish them.
- **Mars IAU:49900 → 4326**: IAU authority is not in proj4j's database. Falls back to a default/closest match.
- **No `id` + GRS 1980 → 4326**: Same parameter-matching issue as NAD83.
- **Minimal PROJJSON → exception**: Without full datum/ellipsoid/coordinate_system definition, proj4j can't construct a CRS object.
- **Full UTM 32N → null**: Projected CRS identification doesn't find a match for the Transverse Mercator parameters in the database.

## Recommendation

For reading declared EPSG codes from PROJJSON metadata (e.g., GeoParquet CRS), use direct JSON parsing of `id.authority` and `id.code` instead of `Proj.toEpsgCode()`.

`toEpsgCode()` is appropriate when you need to *identify* what EPSG code a set of CRS parameters corresponds to (e.g., for CRS transformation), but not for *preserving* a declared identifier from metadata.

## Reproducer

```java
import org.datasyslab.proj4sedona.core.Proj;

// EPSG:6318 declared in id field, but toEpsgCode() returns EPSG:4326
String projjson = "{\"type\":\"GeographicCRS\",\"name\":\"NAD83(2011)\","
    + "\"datum\":{\"type\":\"GeodeticReferenceFrame\","
    + "\"name\":\"NAD83 (National Spatial Reference System 2011)\","
    + "\"ellipsoid\":{\"name\":\"GRS 1980\",\"semi_major_axis\":6378137,"
    + "\"inverse_flattening\":298.257222101}},"
    + "\"coordinate_system\":{\"subtype\":\"ellipsoidal\","
    + "\"axis\":[{\"name\":\"Lat\",\"direction\":\"north\",\"unit\":\"degree\"},"
    + "{\"name\":\"Lon\",\"direction\":\"east\",\"unit\":\"degree\"}]},"
    + "\"id\":{\"authority\":\"EPSG\",\"code\":6318}}";

Proj proj = new Proj(projjson);
System.out.println(proj.toEpsgCode()); // prints "EPSG:4326", expected "EPSG:6318"
```
