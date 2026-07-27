# CRS Formats

Proj4Sedona can parse and export coordinate reference system definitions in multiple formats. This guide covers all supported formats with examples.

## Parsing CRS Definitions

The `Proj` constructor auto-detects the format:

```java
import org.datasyslab.proj4sedona.core.Proj;

// All of these create equivalent Proj objects for WGS84 / UTM zone 18N
Proj fromProj = new Proj("+proj=utm +zone=18 +datum=WGS84 +units=m +no_defs");
Proj fromEpsg = new Proj("EPSG:32618");
Proj fromWkt1 = new Proj("PROJCS[\"WGS 84 / UTM zone 18N\", ...]");
Proj fromWkt2 = new Proj("PROJCRS[\"WGS 84 / UTM zone 18N\", ...]");
Proj fromJson = new Proj("{\"type\": \"ProjectedCRS\", ...}");
```

### Format Detection

| Format | How it is detected |
|--------|-------------------|
| PROJ string | Starts with `+` |
| WKT1 | Contains `[` and starts with `PROJCS`, `GEOGCS`, `GEOCCS`, or `LOCAL_CS` |
| WKT2 | Contains `[` and starts with `PROJCRS`, `GEOGCRS`, `GEODCRS`, `BOUNDCRS`, or `VERTCRS` |
| PROJJSON | Starts with `{` |
| Authority code | Matches pattern `AUTHORITY:CODE` (e.g., `EPSG:4326`, `ESRI:102001`, `IAU_2015:49900`) |
| Shorthand alias | Matches a pre-registered name like `WGS84` or `GOOGLE` |

## PROJ Strings

PROJ strings are the most compact representation. Parameters start with `+`:

```java
// Geographic CRS (longitude/latitude)
Proj wgs84 = new Proj("+proj=longlat +datum=WGS84 +no_defs");

// Projected CRS (UTM)
Proj utm = new Proj("+proj=utm +zone=18 +datum=WGS84 +units=m +no_defs");

// Projected CRS with ellipsoid and towgs84
Proj osgb = new Proj("+proj=tmerc +lat_0=49 +lon_0=-2 +k=0.9996012717 " +
    "+x_0=400000 +y_0=-100000 +ellps=airy " +
    "+towgs84=446.448,-125.157,542.060,0.1502,0.2470,0.8421,-20.4894 +units=m");

// Lambert Conformal Conic with standard parallels
Proj lcc = new Proj("+proj=lcc +lat_1=33 +lat_2=45 +lat_0=39 +lon_0=-96 " +
    "+x_0=0 +y_0=0 +datum=WGS84 +units=m");
```

Common PROJ parameters:

| Parameter | Meaning | Example |
|-----------|---------|---------|
| `+proj` | Projection name | `longlat`, `utm`, `merc`, `lcc` |
| `+datum` | Datum name | `WGS84`, `NAD83`, `NAD27` |
| `+ellps` | Ellipsoid name | `WGS84`, `GRS80`, `airy`, `bessel` |
| `+zone` | UTM zone number | `1` to `60` |
| `+south` | Southern hemisphere UTM | (flag, no value) |
| `+lat_0` | Latitude of origin | Degrees |
| `+lon_0` | Central meridian | Degrees |
| `+lat_1`, `+lat_2` | Standard parallels | Degrees |
| `+k`, `+k_0` | Scale factor | e.g., `0.9996` |
| `+x_0` | False easting | Meters |
| `+y_0` | False northing | Meters |
| `+units` | Linear unit | `m`, `ft`, `us-ft`, `km` |
| `+towgs84` | Datum shift parameters | 3 or 7 values |
| `+nadgrids` | NAD grid file(s) | Comma-separated filenames |
| `+no_defs` | Do not use defaults | (flag) |
| `+a` | Semi-major axis | Meters |
| `+b` | Semi-minor axis | Meters |
| `+rf` | Inverse flattening | Dimensionless |

## WKT1

Well-Known Text version 1 (OGC standard):

```java
String wkt1 = "GEOGCS[\"WGS 84\"," +
    "DATUM[\"WGS_1984\"," +
        "SPHEROID[\"WGS 84\",6378137,298.257223563]]," +
    "PRIMEM[\"Greenwich\",0]," +
    "UNIT[\"degree\",0.0174532925199433]]";

Proj wgs84 = new Proj(wkt1);
```

A projected CRS in WKT1:

```java
String wkt1Proj = "PROJCS[\"WGS 84 / UTM zone 18N\"," +
    "GEOGCS[\"WGS 84\"," +
        "DATUM[\"WGS_1984\"," +
            "SPHEROID[\"WGS 84\",6378137,298.257223563]]," +
        "PRIMEM[\"Greenwich\",0]," +
        "UNIT[\"degree\",0.0174532925199433]]," +
    "PROJECTION[\"Transverse_Mercator\"]," +
    "PARAMETER[\"latitude_of_origin\",0]," +
    "PARAMETER[\"central_meridian\",-75]," +
    "PARAMETER[\"scale_factor\",0.9996]," +
    "PARAMETER[\"false_easting\",500000]," +
    "PARAMETER[\"false_northing\",0]," +
    "UNIT[\"metre\",1]]";

Proj utm18 = new Proj(wkt1Proj);
```

## WKT2

Well-Known Text version 2 (ISO 19162):

```java
String wkt2 = "GEOGCRS[\"WGS 84\"," +
    "DATUM[\"World Geodetic System 1984\"," +
        "ELLIPSOID[\"WGS 84\",6378137,298.257223563,LENGTHUNIT[\"metre\",1]]]," +
    "CS[ellipsoidal,2]," +
        "AXIS[\"latitude\",north,ORDER[1]]," +
        "AXIS[\"longitude\",east,ORDER[2]]," +
        "ANGLEUNIT[\"degree\",0.0174532925199433]]";

Proj wgs84 = new Proj(wkt2);
```

### Detecting WKT Version

```java
import org.datasyslab.proj4sedona.parser.WktParser;

WktParser.isWkt("GEOGCS[...]");    // true (WKT1 or WKT2)
WktParser.isWkt2("GEOGCRS[...]");  // true (WKT2 only)
WktParser.isWkt2("GEOGCS[...]");   // false (WKT1)
WktParser.isWkt("+proj=longlat");  // false (PROJ string)
```

## PROJJSON

JSON-based CRS format (following the PROJJSON schema):

```java
String projJson = "{" +
    "\"type\": \"GeographicCRS\"," +
    "\"name\": \"WGS 84\"," +
    "\"datum\": {" +
        "\"type\": \"GeodeticReferenceFrame\"," +
        "\"name\": \"World Geodetic System 1984\"," +
        "\"ellipsoid\": {" +
            "\"name\": \"WGS 84\"," +
            "\"semi_major_axis\": 6378137," +
            "\"inverse_flattening\": 298.257223563" +
        "}" +
    "}," +
    "\"coordinate_system\": {" +
        "\"subtype\": \"ellipsoidal\"," +
        "\"axis\": [{\"name\": \"Latitude\", \"direction\": \"north\", \"unit\": \"degree\"}," +
                   "{\"name\": \"Longitude\", \"direction\": \"east\", \"unit\": \"degree\"}]" +
    "}" +
"}";

Proj wgs84 = new Proj(projJson);
```

## EPSG and Authority Codes

Look up CRS from the built-in registry or remote providers:

```java
// Common EPSG codes
Proj wgs84 = new Proj("EPSG:4326");     // WGS84 geographic
Proj webMerc = new Proj("EPSG:3857");   // Web Mercator
Proj nad83 = new Proj("EPSG:4269");     // NAD83 geographic
Proj utm18n = new Proj("EPSG:32618");   // WGS84 / UTM zone 18N

// Other authorities (resolved via URL providers)
Proj esri = new Proj("ESRI:102001");
Proj iau = new Proj("IAU_2015:49900");
```

Built-in EPSG codes (no network needed):
- `EPSG:4326` -- WGS84
- `EPSG:4269` -- NAD83
- `EPSG:3857` -- Web Mercator
- `EPSG:32601` through `EPSG:32660` -- UTM zones 1-60 North
- `EPSG:32701` through `EPSG:32760` -- UTM zones 1-60 South
- `EPSG:5041` -- UPS North
- `EPSG:5042` -- UPS South

See [CRS Registry](crs-registry.md) for extending the provider chain.

## Shorthand Aliases

Some common CRS have shorthand names:

```java
Proj wgs84 = new Proj("WGS84");   // same as EPSG:4326
Proj google = new Proj("GOOGLE"); // same as EPSG:3857
```

## Exporting CRS Definitions

Any `Proj` object can be exported to all supported formats:

```java
Proj proj = new Proj("EPSG:32618");

// Export to PROJ string
String projStr = proj.toProjString();
// "+proj=utm +zone=18 +lon_0=-75 +k_0=0.9996 +x_0=500000.0 +ellps=WGS84 +datum=WGS84 +no_defs"
// (metre CRSs emit no +units= token, matching PROJ)

// Export to WKT1
String wkt1 = proj.toWkt1();
// "PROJCS[\"EPSG:32618\", ...]"  (the CRS name is the input srsCode)

// Export to WKT2
String wkt2 = proj.toWkt2();
// "PROJCRS[\"EPSG:32618\", ...]"

// Export to PROJJSON
String json = proj.toProjJson();
// {"type": "ProjectedCRS", ...}

// Export to PROJJSON with pretty-printing
String prettyJson = proj.toProjJson(true);

// Get EPSG code (if known)
String epsg = proj.toEpsgCode();    // "EPSG:32618"

// Get full authority reference
String[] auth = proj.toAuthority();   // {"EPSG", "32618"}
```

### Export Fidelity

No single export is lossless for every supported definition. `toProjString()`
preserves PROJ-specific operation parameters that WKT1, WKT2, and PROJJSON cannot
encode, but legacy PROJ CRS strings cannot preserve all standard CRS metadata.
For polar origins affected only by angular-unit conversion noise,
`toProjString()` emits an exact `+lat_0=90` or `+lat_0=-90` because PROJ rejects
values outside that range. WKT2 and PROJJSON preserve the parsed value instead,
since those formats have no equivalent restriction; the in-memory latitude is
not normalized.

For example, WKT2 and PROJJSON can distinguish two polar horizontal axes using
meridian metadata, while `+axis` cannot represent two axes that both point north
or south. Proj4Sedona retains each such axis's name, abbreviation, direction,
order, linear unit, and meridian when parsing WKT2 or PROJJSON, and reproduces
that metadata in either standard format. `toProjString()` omits `+axis` only after
the retained pair has been validated against the polar origin; WKT1 rejects the
same input because it has no equivalent representation. Duplicate permutations
supplied in raw PROJ input, incomplete or inconsistent meridian metadata, and all
other invalid axis permutations are rejected with
`UnsupportedOperationException`.

The retained meridians also make duplicate-direction polar axes enforceable.
With `enforceAxis=true`, coordinate transforms resolve each `nnu` or `ssu`
horizontal coordinate to its positive easting or northing role from the axis
meridian and projection origin. This supports both easting-first and
northing-first definitions without treating the shared north/south direction as
a sign inversion. Missing, malformed, or drifted metadata fails safely instead
of dropping a coordinate. The default `enforceAxis=false` behavior and ordinary
valid PROJ permutations such as `enu` and `neu` are unchanged.

When a standard export would silently change coordinates, Proj4Sedona throws
`UnsupportedOperationException` and, where the definition is representable in a
legacy PROJ string, directs the caller to `toProjString()`.

This is a compatibility change for callers that previously accepted lossy standard
output: they must now handle `UnsupportedOperationException` or explicitly request a
PROJ string.

This applies to PROJ-only longitude handling (`+over`, `+lon_wrap`), authalic-radius
mode (`+R_A`), `ob_tran`, Tilted Perspective, unsupported Oblique Mercator variants
(`+no_rot` and the two-point form), and grid-shift operations. Approximate Transverse
Mercator is format-dependent: WKT2 and PROJJSON reject it, while WKT1 emits
the executable `Fast_Transverse_Mercator` method. Other uses of `+approx` are rejected
by all standard exporters. WKT2 and
PROJJSON preserve coordinate-affecting three- and seven-parameter `+towgs84`
operations as a `BoundCRS` targeting WGS 84. This includes zero-valued operations
on a non-WGS84 ellipsoid when they are not recoverable from a canonical named datum;
the ellipsoid conversion still changes coordinates in that case.
Geographic and projected sources target EPSG:4326 with EPSG methods 9603 and 9606;
geocentric PROJJSON targets EPSG:4978 with methods 1031 and 1033. Helmert BoundCRS
import is limited to those target/method combinations so an arbitrary target or
coordinate-frame rotation cannot be silently rewritten as `+towgs84`. WKT1 preserves
the same operation with `TOWGS84`. Grid-shift operations remain importable but are not
yet exportable. Geocentric CRS export is supported as a PROJ string or PROJJSON, but
not yet as WKT.

For supported definitions, standard exports preserve projection parameters,
linear-unit factors, horizontal axis order and direction, and non-Greenwich prime
meridians. Re-importing an exported definition therefore retains the coordinate
semantics represented by that format.

### Using CRSSerializer Directly

```java
import org.datasyslab.proj4sedona.parser.CRSSerializer;

Proj proj = new Proj("+proj=utm +zone=18 +datum=WGS84");

String projStr = CRSSerializer.toProjString(proj);
String wkt1 = CRSSerializer.toWkt1(proj);
String wkt2 = CRSSerializer.toWkt2(proj);
String json = CRSSerializer.toProjJson(proj);
String epsg = CRSSerializer.toEpsgCode(proj);
String[] auth = CRSSerializer.toAuthority(proj);
```

## See Also

- [Getting Started](getting-started.md) -- basic usage
- [CRS Registry](crs-registry.md) -- adding custom CRS providers
- [Constants Reference](constants-reference.md) -- built-in datums and ellipsoids
