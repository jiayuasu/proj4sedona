# MGRS Coordinates

Proj4Sedona supports conversion between geographic coordinates (longitude/latitude) and Military Grid Reference System (MGRS) strings in the UTM latitude bands from 80°S through 84°N. MGRS is widely used in military and emergency services for unambiguous location references. This implementation tracks [proj4js/mgrs v2.2.0](https://github.com/proj4js/mgrs/releases/tag/v2.2.0).

## Converting to MGRS

### From Longitude/Latitude

```java
import org.datasyslab.proj4sedona.Proj4;
import org.datasyslab.proj4sedona.core.Point;

// Default accuracy (5 = 1-meter precision)
String mgrs = Proj4.toMGRS(-77.0369, 38.9072);
// "18SUJ2338308450"

// With explicit accuracy
String mgrs5 = Proj4.toMGRS(-77.0369, 38.9072, 5);  // "18SUJ2338308450" (1m)
String mgrs4 = Proj4.toMGRS(-77.0369, 38.9072, 4);  // "18SUJ23380845"  (10m)
String mgrs3 = Proj4.toMGRS(-77.0369, 38.9072, 3);  // "18SUJ233084"    (100m)
String mgrs2 = Proj4.toMGRS(-77.0369, 38.9072, 2);  // "18SUJ2308"      (1km)
String mgrs1 = Proj4.toMGRS(-77.0369, 38.9072, 1);  // "18SUJ20"        (10km)
String mgrs0 = Proj4.toMGRS(-77.0369, 38.9072, 0);  // "18SUJ"          (100km)
```

### From Array

```java
String mgrs = Proj4.toMGRS(new double[]{-77.0369, 38.9072});
String mgrs2 = Proj4.toMGRS(new double[]{-77.0369, 38.9072}, 3);
```

## Converting from MGRS

### To Center Point

```java
// Returns [longitude, latitude]
double[] lonLat = Proj4.fromMGRS("18SUJ2338308450");
// [-77.0369, 38.9072] (approximately)

// Using Point object
Point p = Proj4.mgrsToPoint("18SUJ2338308450");
// p.x = longitude, p.y = latitude
```

### To Bounding Box

Lower-accuracy MGRS strings represent an area, not a point. Use `mgrsInverse` to get the bounding box:

```java
// Returns [left, bottom, right, top] in degrees
double[] bbox = Proj4.mgrsInverse("18SUJ23");
// Bounding box of the 10km grid square
```

## Accuracy Levels

The accuracy parameter controls the precision of the MGRS string:

| Accuracy | Grid Size | Digits per Axis | Example |
|----------|----------|-----------------|---------|
| 0 | 100 km | 0 | `18SUJ` |
| 1 | 10 km | 1 | `18SUJ20` |
| 2 | 1 km | 2 | `18SUJ2308` |
| 3 | 100 m | 3 | `18SUJ233084` |
| 4 | 10 m | 4 | `18SUJ23380845` |
| 5 | 1 m | 5 | `18SUJ2338308450` |

## Using the MGRS Class Directly

For more control, use the `MGRS` class:

```java
import org.datasyslab.proj4sedona.mgrs.MGRS;

// Forward: lon/lat to MGRS string
String mgrs = MGRS.forward(new double[]{-77.0369, 38.9072}, 5);
// "18SUJ2338308450"

// Default accuracy (5)
String mgrsDefault = MGRS.forward(new double[]{-77.0369, 38.9072});

// Inverse: MGRS to bounding box [left, bottom, right, top]
double[] bbox = MGRS.inverse("18SUJ2338308450");

// To center point [longitude, latitude]
double[] center = MGRS.toPoint("18SUJ2338308450");
```

## MGRS String Format

An MGRS string has three parts:

```
18 S UJ 23383 08450
^  ^ ^  ^     ^
|  | |  |     Northing digits
|  | |  Easting digits
|  | 100km square identifier
|  Latitude band letter (C-X, excluding I and O)
UTM zone number (1-60)
```

## Special UTM Zones

MGRS follows the UTM zone system with two exceptions for Norway and Svalbard:

- Zone 32V is widened to cover southwestern Norway (normally zone 31V)
- Zones 32X, 34X, and 36X do not exist; they are merged into zones 31X, 33X, 35X, and 37X for Svalbard

Proj4Sedona handles these special zones automatically.

## UPS (Universal Polar Stereographic)

`MGRS.forward` intentionally rejects polar regions above 84°N or below 80°S, matching proj4js/mgrs. Proj4Sedona also provides a separate local Universal Polar Stereographic (UPS) API for those coordinates; it is not invoked automatically by `MGRS`.

```java
import org.datasyslab.proj4sedona.mgrs.UPS;

// Check if a latitude falls in a polar zone
boolean isNorth = UPS.isNorthPolar(85.0);  // true
boolean isSouth = UPS.isSouthPolar(-81.0); // true
boolean isUps = UPS.isUPS(85.0);           // true

// Convert to UPS coordinates
UPS.UPSCoordinate ups = UPS.fromLatLon(85.0, 10.0);
// ups.easting, ups.northing, ups.zone

// Convert back to lat/lon
double[] latLon = UPS.toLatLon(ups);

// Get the UPS zone letter
char zone = UPS.getZone(85.0, 10.0); // 'Z' (North Pole)
```

## Round-Trip Example

```java
import org.datasyslab.proj4sedona.Proj4;

// A set of world cities
double[][] cities = {
    {-77.0369, 38.9072},   // Washington, DC
    {-73.9857, 40.7484},   // New York
    {-0.1276, 51.5074},    // London
    {139.6917, 35.6895},   // Tokyo
    {151.2093, -33.8688},  // Sydney
};

for (double[] city : cities) {
    String mgrs = Proj4.toMGRS(city[0], city[1], 5);
    double[] back = Proj4.fromMGRS(mgrs);
    System.out.printf("(%8.4f, %7.4f) -> %s -> (%8.4f, %7.4f)%n",
        city[0], city[1], mgrs, back[0], back[1]);
}
```

## Error Handling

MGRS input is case-insensitive and may contain whitespace, so `4QFJ 12345 67890` and `4QFJ1234567890` decode identically. Invalid coordinates, accuracies, and MGRS strings throw an `IllegalArgumentException`. Longitude must be finite and between -180 and 180, latitude must be finite and between -80 and 84, accuracy must be between 0 and 5, UTM zones must be between 1 and 60, and each decoded axis may contain at most five digits.

```java
try {
    Proj4.fromMGRS("");        // empty string
} catch (IllegalArgumentException e) {
    System.out.println(e.getMessage());
}

try {
    Proj4.fromMGRS("99ZZZ");   // invalid zone
} catch (IllegalArgumentException e) {
    System.out.println(e.getMessage());
}
```

## See Also

- [Coordinate Transformations](coordinate-transformations.md) -- general coordinate transforms
- [Getting Started](getting-started.md) -- basic usage
