# Projections

Proj4Sedona supports 15 map projections covering cylindrical, pseudocylindrical, conic, and azimuthal families. This page lists each projection with its PROJ name, aliases, and a usage example.

## Cylindrical Projections

### Mercator

Conformal cylindrical projection. Standard for web maps.

- PROJ name: `merc`
- Aliases: `Mercator`, `Popular Visualisation Pseudo Mercator`, `Mercator_1SP`, `Mercator_Auxiliary_Sphere`, `Mercator_Variant_A`, `Mercator_Variant_B`

```java
double[] result = Proj4.proj4(
    "+proj=longlat +datum=WGS84",
    "+proj=merc +a=6378137 +b=6378137 +lat_ts=0 +lon_0=0 +x_0=0 +y_0=0 +k=1 +units=m",
    new double[]{-122.4194, 37.7749}
);
```

### Transverse Mercator

Conformal cylindrical projection rotated 90 degrees. Used for UTM zones and many national grids.

- PROJ name: `tmerc`, `etmerc`
- Aliases: `Transverse_Mercator`, `Transverse Mercator`, `Gauss Kruger`, `Gauss_Kruger`, `Extended_Transverse_Mercator`

```java
double[] result = Proj4.proj4(
    "+proj=longlat +datum=WGS84",
    "+proj=tmerc +lat_0=49 +lon_0=-2 +k=0.9996012717 +x_0=400000 +y_0=-100000 +datum=WGS84 +units=m",
    new double[]{-2.0, 49.0}
);
```

### Universal Transverse Mercator (UTM)

Standardized Transverse Mercator system dividing the world into 60 zones.

- PROJ name: `utm`
- Aliases: `Universal Transverse Mercator System`, `Universal_Transverse_Mercator`

```java
// Northern hemisphere, zone 18 (covers eastern US)
double[] result = Proj4.proj4(
    "+proj=longlat +datum=WGS84",
    "+proj=utm +zone=18 +datum=WGS84",
    new double[]{-77.0369, 38.9072}
);

// Southern hemisphere
double[] resultSouth = Proj4.proj4(
    "+proj=longlat +datum=WGS84",
    "+proj=utm +zone=56 +south +datum=WGS84",
    new double[]{151.2093, -33.8688}
);
```

### Equidistant Cylindrical (Plate Carree)

Equidistant cylindrical projection. Maps longitude and latitude directly to x and y.

- PROJ name: `eqc`
- Aliases: `Equidistant_Cylindrical`, `Plate_Carree`, `Equirectangular`

```java
double[] result = Proj4.proj4(
    "+proj=longlat +datum=WGS84",
    "+proj=eqc +datum=WGS84",
    new double[]{-77.0, 38.9}
);
```

### Cylindrical Equal Area

Equal-area cylindrical projection.

- PROJ name: `cea`
- Aliases: `Cylindrical_Equal_Area`, `Lambert_Cylindrical_Equal_Area`

```java
double[] result = Proj4.proj4(
    "+proj=longlat +datum=WGS84",
    "+proj=cea +lon_0=0 +lat_ts=30 +datum=WGS84 +units=m",
    new double[]{-77.0, 38.9}
);
```

## Pseudocylindrical Projections

### Sinusoidal

Equal-area pseudocylindrical projection with straight parallels.

- PROJ name: `sinu`
- Aliases: `Sinusoidal`

```java
double[] result = Proj4.proj4(
    "+proj=longlat +datum=WGS84",
    "+proj=sinu +lon_0=0 +datum=WGS84 +units=m",
    new double[]{-77.0, 38.9}
);
```

### Mollweide

Equal-area pseudocylindrical projection. Common for world maps.

- PROJ name: `moll`
- Aliases: `Mollweide`

```java
double[] result = Proj4.proj4(
    "+proj=longlat +datum=WGS84",
    "+proj=moll +lon_0=0 +datum=WGS84 +units=m",
    new double[]{-77.0, 38.9}
);
```

### Robinson

Compromise pseudocylindrical projection designed for visual appeal.

- PROJ name: `robin`
- Aliases: `Robinson`

```java
double[] result = Proj4.proj4(
    "+proj=longlat +datum=WGS84",
    "+proj=robin +lon_0=0 +datum=WGS84 +units=m",
    new double[]{-77.0, 38.9}
);
```

## Conic Projections

### Lambert Conformal Conic

Conformal conic projection. Widely used for aeronautical charts and State Plane coordinates.

- PROJ name: `lcc`
- Aliases: `Lambert_Conformal_Conic`, `Lambert_Conformal_Conic_1SP`, `Lambert_Conformal_Conic_2SP`, `Lambert Conic Conformal (1SP)`, `Lambert Conic Conformal (2SP)`

```java
// Two standard parallels
double[] result = Proj4.proj4(
    "+proj=longlat +datum=WGS84",
    "+proj=lcc +lat_1=33 +lat_2=45 +lat_0=39 +lon_0=-96 +x_0=0 +y_0=0 +datum=WGS84 +units=m",
    new double[]{-96.0, 39.0}
);
```

### Albers Equal Area

Equal-area conic projection. Common for thematic maps of mid-latitude regions.

- PROJ name: `aea`
- Aliases: `Albers_Conic_Equal_Area`, `Albers_Equal_Area`, `Albers`

```java
double[] result = Proj4.proj4(
    "+proj=longlat +datum=WGS84",
    "+proj=aea +lat_1=29.5 +lat_2=45.5 +lat_0=37.5 +lon_0=-96 +x_0=0 +y_0=0 +datum=WGS84 +units=m",
    new double[]{-96.0, 37.5}
);
```

### Equidistant Conic

Conic projection preserving distances along meridians.

- PROJ name: `eqdc`
- Aliases: `Equidistant_Conic`

```java
double[] result = Proj4.proj4(
    "+proj=longlat +datum=WGS84",
    "+proj=eqdc +lat_1=33 +lat_2=45 +lat_0=39 +lon_0=-96 +x_0=0 +y_0=0 +datum=WGS84 +units=m",
    new double[]{-96.0, 39.0}
);
```

## Azimuthal Projections

### Lambert Azimuthal Equal Area

Equal-area azimuthal projection. Used for continental and hemispheric maps.

- PROJ name: `laea`
- Aliases: `Lambert Azimuthal Equal Area`, `Lambert_Azimuthal_Equal_Area`

```java
double[] result = Proj4.proj4(
    "+proj=longlat +datum=WGS84",
    "+proj=laea +lat_0=52 +lon_0=10 +x_0=4321000 +y_0=3210000 +datum=WGS84 +units=m",
    new double[]{10.0, 52.0}
);
```

### Stereographic

Conformal azimuthal projection. Used for polar regions and some national grids.

- PROJ name: `stere`
- Aliases: `Stereographic`, `Stereographic_South_Pole`, `Stereographic_North_Pole`, `Polar_Stereographic`, `Polar_Stereographic_variant_A`, `Polar_Stereographic_variant_B`, `Oblique_Stereographic`

```java
// Polar stereographic (North Pole)
double[] result = Proj4.proj4(
    "+proj=longlat +datum=WGS84",
    "+proj=stere +lat_0=90 +lat_ts=71 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m",
    new double[]{0.0, 85.0}
);
```

### Azimuthal Equidistant

Azimuthal projection preserving distances from the center point.

- PROJ name: `aeqd`
- Aliases: `Azimuthal_Equidistant`

```java
double[] result = Proj4.proj4(
    "+proj=longlat +datum=WGS84",
    "+proj=aeqd +lat_0=38.9 +lon_0=-77.0 +x_0=0 +y_0=0 +datum=WGS84 +units=m",
    new double[]{-77.5, 39.0}
);
```

## Other Projections

### Identity (Longitude/Latitude)

Passes coordinates through without projection. Used to represent geographic (unprojected) CRS.

- PROJ name: `longlat`
- Aliases: `identity`

```java
Proj wgs84 = new Proj("+proj=longlat +datum=WGS84");
```

## Projection Summary Table

| Category | Projection | PROJ Name | Properties |
|----------|-----------|-----------|------------|
| Cylindrical | Mercator | `merc` | Conformal |
| Cylindrical | Transverse Mercator | `tmerc` | Conformal |
| Cylindrical | UTM | `utm` | Conformal |
| Cylindrical | Equidistant Cylindrical | `eqc` | Equidistant |
| Cylindrical | Cylindrical Equal Area | `cea` | Equal-area |
| Pseudocylindrical | Sinusoidal | `sinu` | Equal-area |
| Pseudocylindrical | Mollweide | `moll` | Equal-area |
| Pseudocylindrical | Robinson | `robin` | Compromise |
| Conic | Lambert Conformal Conic | `lcc` | Conformal |
| Conic | Albers Equal Area | `aea` | Equal-area |
| Conic | Equidistant Conic | `eqdc` | Equidistant |
| Azimuthal | Lambert Azimuthal Equal Area | `laea` | Equal-area |
| Azimuthal | Stereographic | `stere` | Conformal |
| Azimuthal | Azimuthal Equidistant | `aeqd` | Equidistant |
| Other | Identity | `longlat` | None |

## See Also

- [Coordinate Transformations](coordinate-transformations.md) -- using projections for transforms
- [Constants Reference](constants-reference.md) -- built-in ellipsoids and datums
