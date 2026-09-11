# Projections

Proj4Sedona supports 34 map projections — the complete proj4js set — plus the geographic identity (longlat) transform, covering cylindrical, pseudocylindrical, conic, azimuthal, and special families. The identity transform is documented under Other Projections but is not itself a map projection and is not counted among the 34. This page lists each projection with its PROJ name, aliases, and a usage example.

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

### Miller Cylindrical

Compromise cylindrical projection related to Mercator but showing the poles.

- PROJ name: `mill`
- Aliases: `Miller_Cylindrical`

```java
double[] result = Proj4.proj4(
    "+proj=longlat +datum=WGS84",
    "+proj=mill +lon_0=0 +a=6378137 +b=6378137 +units=m",
    new double[]{-77.0, 38.9}
);
```

### Cassini-Soldner

Transverse cylindrical projection, true to scale along the central meridian. Used by older national and cadastral grids (e.g. EPSG:2066).

- PROJ name: `cass`
- Aliases: `Cassini`, `Cassini_Soldner`

```java
double[] result = Proj4.proj4(
    "+proj=longlat +datum=WGS84",
    "+proj=cass +lat_0=11.25 +lon_0=-60.69 +x_0=187500 +y_0=180000 +datum=WGS84 +units=m",
    new double[]{-60.7, 11.25}
);
```

### Swiss Oblique Mercator

Oblique conformal cylindrical projection used by the Swiss national grids (EPSG:21781 LV03, EPSG:2056 LV95).

- PROJ name: `somerc`
- Aliases: `Swiss_Oblique_Mercator`

```java
double[] result = Proj4.proj4(
    "+proj=longlat +datum=WGS84",
    "+proj=somerc +lat_0=46.9524055555 +lon_0=7.4395833333 +k_0=1 +x_0=2600000 +y_0=1200000 +ellps=bessel +towgs84=674.374,15.056,405.346 +units=m",
    new double[]{8.55, 47.37}
);
```

### Hotine Oblique Mercator

Oblique Mercator with an arbitrary central line (EPSG:3375 RSO Malaysia, Alaska zone 1). Supports the azimuth/rectified-grid-angle parameterization (variants A and B via `+no_uoff`) and the two-point form.

- PROJ name: `omerc`
- Aliases: `Hotine_Oblique_Mercator`, `Oblique_Mercator`, `Hotine_Oblique_Mercator_Azimuth_Center`, and variants

```java
double[] result = Proj4.proj4(
    "+proj=longlat +datum=WGS84",
    "+proj=omerc +lat_0=4 +lonc=102.25 +alpha=323.0257964667 +k=0.99984 +x_0=804671 +y_0=0 +no_uoff +gamma=323.1301023611 +ellps=GRS80 +units=m",
    new double[]{102.5, 4.2}
);
```

### Gauss-Schreiber Transverse Mercator

Double-projection variant of the Transverse Mercator via a Gauss sphere. Used by legacy French grids (e.g. IGNF Reunion, Martinique).

- PROJ name: `gstmerc`
- Aliases: `gstmerg`, `Gauss_Schreiber_Transverse_Mercator`

```java
double[] result = Proj4.proj4(
    "+proj=longlat +datum=WGS84",
    "+proj=gstmerc +lat_0=-21.116666667 +lon_0=55.53333333 +k_0=1 +x_0=160000 +y_0=50000 +ellps=intl +units=m",
    new double[]{55.5, -21.1}
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

### Equal Earth

Equal-area pseudocylindrical world projection (Savric, Patterson & Jenny, 2018). Spherical and ellipsoidal (authalic) forms.

- PROJ name: `eqearth`
- Aliases: `Equal Earth`, `Equal_Earth`

```java
double[] result = Proj4.proj4(
    "+proj=longlat +datum=WGS84",
    "+proj=eqearth +lon_0=0 +datum=WGS84 +units=m",
    new double[]{-77.0, 38.9}
);
```

### Eckert VI

Equal-area pseudocylindrical world projection; always computed spherically.

- PROJ name: `eck6`
- Aliases: `Eckert_VI`

```java
double[] result = Proj4.proj4(
    "+proj=longlat +datum=WGS84",
    "+proj=eck6 +lon_0=0 +a=6371007 +b=6371007 +units=m",
    new double[]{-77.0, 38.9}
);
```

### Van der Grinten

Projects the world into a circle; computed on a sphere of radius `a`.

- PROJ name: `vandg`
- Aliases: `Van_der_Grinten_I`, `VanDerGrinten`, `Van_der_Grinten`

```java
double[] result = Proj4.proj4(
    "+proj=longlat +datum=WGS84",
    "+proj=vandg +lon_0=0 +a=6371007 +b=6371007 +units=m",
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

### Polyconic

American Polyconic: each parallel has its own cone, true to scale along the central meridian and every parallel. Used by EPSG:5880 / EPSG:29101 (Brazil Polyconic).

- PROJ name: `poly`
- Aliases: `Polyconic`, `American_Polyconic`

```java
double[] result = Proj4.proj4(
    "+proj=longlat +datum=WGS84",
    "+proj=poly +lat_0=0 +lon_0=-54 +x_0=5000000 +y_0=10000000 +ellps=GRS80 +units=m",
    new double[]{-47.9, -15.8}
);
```

### Krovak

Oblique conformal conic used by the Czech and Slovak national grids (EPSG:5514 / EPSG:2065, S-JTSK). Always on the Bessel 1841 ellipsoid; output is south-west oriented (negative), as in proj4js.

- PROJ name: `krovak`
- Aliases: `Krovak`, `Krovak Modified`, `Krovak (North Orientated)`, `Krovak Modified (North Orientated)`

```java
double[] result = Proj4.proj4(
    "+proj=longlat +ellps=bessel",
    "+proj=krovak +lat_0=49.5 +lon_0=24.8333333333 +alpha=30.2881397222 +k=0.9999 +ellps=bessel +units=m",
    new double[]{14.42, 50.08}
);
```

### Bonne

Pseudoconic equal-area projection; true to scale along the central meridian and every parallel. Requires a non-zero standard parallel (`+lat_1`).

- PROJ name: `bonne`
- Aliases: `Bonne (Werner lat_1=90)`

```java
double[] result = Proj4.proj4(
    "+proj=longlat +datum=WGS84",
    "+proj=bonne +lat_1=40 +lon_0=0 +datum=WGS84 +units=m",
    new double[]{10.0, 50.0}
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
- Aliases: `Stereographic`, `Stereographic_South_Pole`, `Stereographic_North_Pole`, `Polar_Stereographic`, `Polar_Stereographic_variant_A`, `Polar_Stereographic_variant_B`

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

### Oblique Stereographic Alternative

Double Stereographic (EPSG method 9809): maps the ellipsoid to a conformal sphere first, then applies the spherical stereographic. Used by EPSG:28992 (Amersfoort / RD New) and EPSG:2036 (New Brunswick). Distinct from the Snyder `stere`.

- PROJ name: `sterea`
- Aliases: `Oblique_Stereographic`, `Double_Stereographic`

```java
double[] result = Proj4.proj4(
    "+proj=longlat +datum=WGS84",
    "+proj=sterea +lat_0=52.1561605556 +lon_0=5.3876388889 +k=0.9999079 +x_0=155000 +y_0=463000 +ellps=bessel +towgs84=565.417,50.3319,465.552,-0.398957,0.343988,-1.8774,4.0725 +units=m",
    new double[]{5.2, 52.25}
);
```

### Gnomonic

Azimuthal projection from the center of the sphere; every great circle maps to a straight line.

- PROJ name: `gnom`
- Aliases: `Gnomonic`

```java
double[] result = Proj4.proj4(
    "+proj=longlat +datum=WGS84",
    "+proj=gnom +lat_0=45 +lon_0=0 +a=6378137 +b=6378137 +units=m",
    new double[]{5.0, 50.0}
);
```

### Orthographic

View of the globe from infinite distance; only the near hemisphere is projectable (far-side points return null).

- PROJ name: `ortho`
- Aliases: `Orthographic`

```java
double[] result = Proj4.proj4(
    "+proj=longlat +datum=WGS84",
    "+proj=ortho +lat_0=45 +lon_0=0 +a=6378137 +b=6378137 +units=m",
    new double[]{5.0, 50.0}
);
```

### Tilted Perspective

General perspective view of the globe from an arbitrary height, azimuth, and tilt — a satellite or aerial camera view. The untilted case is the vertical near-side perspective (`nsper`).

- PROJ name: `tpers`
- Aliases: `Tilted_Perspective`

```java
double[] result = Proj4.proj4(
    "+proj=longlat +datum=WGS84",
    "+proj=tpers +h=5500000 +lat_0=40 +lon_0=-75 +azi=20 +tilt=30 +units=m",
    new double[]{-74, 40.7}
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

### Geostationary Satellite

The view of the Earth from a geostationary satellite. Requires the satellite height (`+h`); the `+sweep` axis (x/y) selects the instrument sweep convention. Far-side points return null.

- PROJ name: `geos`
- Aliases: `Geostationary_Satellite`, `Geostationary Satellite (Sweep X)`, `Geostationary Satellite (Sweep Y)`

```java
double[] result = Proj4.proj4(
    "+proj=longlat +datum=WGS84",
    "+proj=geos +h=35785831 +sweep=y +lon_0=0 +datum=WGS84 +units=m",
    new double[]{10.0, -5.0}
);
```

### New Zealand Map Grid

Sixth-order conformal fit specific to New Zealand (EPSG:27200). Defined only for the New Zealand region.

- PROJ name: `nzmg`
- Aliases: `New_Zealand_Map_Grid`

```java
double[] result = Proj4.proj4(
    "+proj=longlat +datum=WGS84",
    "+proj=nzmg +lat_0=-41 +lon_0=173 +x_0=2510000 +y_0=6023150 +ellps=intl +units=m",
    new double[]{174.78, -41.29}
);
```

### Geocentric (ECEF)

Not a map projection: converts geodetic longitude/latitude/height to Earth-centered, Earth-fixed X/Y/Z coordinates (e.g. EPSG:4978). Two-dimensional input returns the computed Z. Geocentric CRSs are also recognized when parsed from PROJJSON (`GeodeticCRS` + Cartesian coordinate system) and WKT2 (`GEODCRS` with `CS[Cartesian,3]`).

- PROJ name: `geocent`
- Aliases: `Geocentric`, `geocentric`, `Geocent`

```java
double[] xyz = Proj4.proj4(
    "+proj=longlat +datum=WGS84",
    "+proj=geocent +datum=WGS84 +units=m",
    new double[]{2.35, 48.85, 100}
);
```

### Quadrilateralized Spherical Cube

Projects the sphere onto the six faces of a circumscribed cube with approximately equal-area facets. Used by astronomical and planetary data sets (COBE).

- PROJ name: `qsc`
- Aliases: `Quadrilateralized Spherical Cube`, `Quadrilateralized_Spherical_Cube`

```java
double[] result = Proj4.proj4(
    "+proj=longlat +datum=WGS84",
    "+proj=qsc +lat_0=0 +lon_0=0 +ellps=WGS84 +units=m",
    new double[]{5, 15}
);
```

### General Oblique Transformation

Meta-projection that rotates the sphere so an arbitrary point becomes the pole (or an arbitrary great circle the equator) before applying an inner projection given by `+o_proj`. Standard for rotated-pole grids in climate and ocean modeling.

- PROJ name: `ob_tran`
- Aliases: `General Oblique Transformation`, `General_Oblique_Transformation`

```java
// Rotated-pole longitude/latitude grid (output in degrees)
double[] rotated = Proj4.proj4(
    "+proj=longlat +datum=WGS84",
    "+proj=ob_tran +o_proj=longlat +o_lon_p=0 +o_lat_p=35 +lon_0=-113 +R=6371229",
    new double[]{-105, 40}
);
```

## Projection Summary Table

| Category | Projection | PROJ Name | Properties |
|----------|-----------|-----------|------------|
| Cylindrical | Mercator | `merc` | Conformal |
| Cylindrical | Transverse Mercator | `tmerc` | Conformal |
| Cylindrical | UTM | `utm` | Conformal |
| Cylindrical | Equidistant Cylindrical | `eqc` | Equidistant |
| Cylindrical | Cylindrical Equal Area | `cea` | Equal-area |
| Cylindrical | Miller Cylindrical | `mill` | Compromise |
| Cylindrical | Cassini-Soldner | `cass` | Equidistant (central meridian) |
| Cylindrical | Swiss Oblique Mercator | `somerc` | Conformal |
| Cylindrical | Hotine Oblique Mercator | `omerc` | Conformal |
| Pseudocylindrical | Sinusoidal | `sinu` | Equal-area |
| Pseudocylindrical | Mollweide | `moll` | Equal-area |
| Pseudocylindrical | Robinson | `robin` | Compromise |
| Pseudocylindrical | Equal Earth | `eqearth` | Equal-area |
| Pseudocylindrical | Eckert VI | `eck6` | Equal-area |
| Pseudocylindrical | Van der Grinten | `vandg` | Compromise |
| Conic | Lambert Conformal Conic | `lcc` | Conformal |
| Conic | Albers Equal Area | `aea` | Equal-area |
| Conic | Equidistant Conic | `eqdc` | Equidistant |
| Conic | Polyconic | `poly` | Compromise |
| Conic | Krovak | `krovak` | Conformal |
| Conic | Bonne | `bonne` | Equal-area |
| Azimuthal | Lambert Azimuthal Equal Area | `laea` | Equal-area |
| Azimuthal | Stereographic | `stere` | Conformal |
| Azimuthal | Azimuthal Equidistant | `aeqd` | Equidistant |
| Azimuthal | Oblique Stereographic Alternative | `sterea` | Conformal |
| Azimuthal | Gnomonic | `gnom` | Gnomonic |
| Azimuthal | Orthographic | `ortho` | Perspective |
| Other | Geostationary Satellite | `geos` | Perspective |
| Cylindrical | Gauss-Schreiber Transverse Mercator | `gstmerc` | Conformal |
| Azimuthal | Tilted Perspective | `tpers` | Perspective |
| Other | New Zealand Map Grid | `nzmg` | Conformal (regional fit) |
| Other | Geocentric (ECEF) | `geocent` | Coordinate conversion |
| Other | Quadrilateralized Spherical Cube | `qsc` | Approximately equal-area |
| Other | General Oblique Transformation | `ob_tran` | Meta-projection (rotated pole) |
| Other | Identity | `longlat` | None |

## See Also

- [Coordinate Transformations](coordinate-transformations.md) -- using projections for transforms
- [Constants Reference](constants-reference.md) -- built-in ellipsoids and datums
