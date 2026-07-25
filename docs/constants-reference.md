# Constants Reference

Proj4Sedona includes built-in definitions for datums, ellipsoids, linear units, and prime meridians. These are used when parsing PROJ strings with parameters like `+datum=WGS84`, `+ellps=airy`, `+units=us-ft`, or `+pm=paris`.

## Datums

Retrieved via `Datum.get(code)`. The `towgs84` field contains the parameters for transforming to WGS84.

The registry mirrors the complete generated proj4js datum snapshot: 438 canonical
definitions, comprising 437 three- or seven-parameter transformations and the NAD27
grid definition. This includes authority keys such as `EPSG_4124`, `ESRI_104101`, and
`IGNF_ANAA92G`, in addition to the familiar short names below. The authoritative
machine-readable list is
[`proj4js-datums.tsv`](../src/main/resources/org/datasyslab/proj4sedona/constants/proj4js-datums.tsv).

The following table lists the legacy named definitions:

| Code | Ellipsoid | Shift Parameters | Name |
|------|-----------|-----------------|------|
| `wgs84` | WGS84 | `0,0,0` | WGS 1984 |
| `nad83` | GRS80 | `0,0,0` | North American Datum 1983 |
| `nad27` | clrk66 | nadgrids: `@conus,@alaska,@ntv2_0.gsb,@ntv1_can.dat` | North American Datum 1927 |
| `osgb36` | airy | `446.448,-125.157,542.060,0.1502,0.2470,0.8421,-20.4894` | Ordnance Survey of Great Britain 1936 |
| `ch1903` | bessel | `674.374,15.056,405.346` | Swiss CH1903 |
| `ggrs87` | GRS80 | `-199.87,74.79,246.62` | Greek Geodetic Reference System 1987 |
| `potsdam` | bessel | `598.1,73.7,418.2,0.202,0.045,-2.455,6.7` | Potsdam Rauenberg 1950 DHDN |
| `carthage` | clrk80ign | `-263.0,6.0,431.0` | Carthage 1934 Tunisia |
| `hermannskogel` | bessel | `577.326,90.129,463.919,5.137,1.474,5.297,2.4232` | Hermannskogel |
| `mgi` | bessel | `577.326,90.129,463.919,5.137,1.474,5.297,2.4232` | Militar-Geographische Institut |
| `osni52` | airy | `482.530,-130.596,564.557,-1.042,-0.214,-0.631,8.15` | Irish National |
| `ire65` | mod_airy | `482.530,-130.596,564.557,-1.042,-0.214,-0.631,8.15` | Ireland 1965 |
| `rassadiran` | intl | `-133.63,-157.5,-158.62` | Rassadiran |
| `nzgd49` | intl | `59.47,-5.04,187.44,0.47,-0.1,1.024,-4.5993` | New Zealand Geodetic Datum 1949 |
| `s_jtsk` | bessel | `589,76,480` | S-JTSK (Ferro) |
| `beduaram` | clrk80 | `-106,-87,188` | Beduaram |
| `gunung_segara` | bessel | `-403,684,41` | Gunung Segara Jakarta |
| `rnb72` | intl | `106.869,-52.2978,103.724,-0.33657,0.456955,-1.84218,1` | Reseau National Belge 1972 |

### Datum Name Aliases

The following names also resolve to their respective datums:

| Alias | Resolves To |
|-------|-------------|
| `World Geodetic System 1984` | wgs84 |
| `World Geodetic System 1984 ensemble` | wgs84 |
| `WGS 84` | wgs84 |
| `North American Datum 1983` | nad83 |
| `North American Datum of 1983` | nad83 |
| `North American Datum 1927` | nad27 |
| `North American Datum of 1927` | nad27 |
| `Ordnance Survey of Great Britain 1936` | osgb36 |
| `OSGB 1936` | osgb36 |

## Ellipsoids

Retrieved via `Ellipsoid.get(code)`. Each ellipsoid defines the shape of the Earth with a semi-major axis (`a`) and either the semi-minor axis (`b`) or inverse flattening (`rf`).

| Code | a (m) | b (m) or rf | Name |
|------|-------|-------------|------|
| `MERIT` | 6378137 | rf=298.257 | MERIT 1983 |
| `SGS85` | 6378136 | rf=298.257 | Soviet Geodetic System 85 |
| `GRS80` | 6378137 | rf=298.257222101 | GRS 1980 (IUGG, 1980) |
| `IAU76` | 6378140 | rf=298.257 | IAU 1976 |
| `airy` | 6377563.396 | b=6356256.91 | Airy 1830 |
| `APL4` | 6378137 | rf=298.25 | Appl. Physics. 1965 |
| `NWL9D` | 6378145 | rf=298.25 | Naval Weapons Lab., 1965 |
| `mod_airy` | 6377340.189 | b=6356034.446 | Modified Airy |
| `andrae` | 6377104.43 | rf=300 | Andrae 1876 (Den., Iclnd.) |
| `aust_SA` | 6378160 | rf=298.25 | Australian Natl and S. Amer. 1969 |
| `GRS67` | 6378160 | rf=298.247167427 | GRS 67 (IUGG 1967) |
| `bessel` | 6377397.155 | rf=299.1528128 | Bessel 1841 |
| `bess_nam` | 6377483.865 | rf=299.1528128 | Bessel 1841 (Namibia) |
| `clrk66` | 6378206.4 | b=6356583.8 | Clarke 1866 |
| `clrk80` | 6378249.145 | rf=293.4663 | Clarke 1880 mod. |
| `clrk80ign` | 6378249.2 | b=6356515, rf=293.4660213 | Clarke 1880 (IGN) |
| `clrk58` | 6378293.645208759 | rf=294.2606763692654 | Clarke 1858 |
| `CPM` | 6375738.7 | rf=334.29 | Comm. des Poids et Mesures 1799 |
| `delmbr` | 6376428 | rf=311.5 | Delambre 1810 (Belgium) |
| `engelis` | 6378136.05 | rf=298.2566 | Engelis 1985 |
| `evrst30` | 6377276.345 | rf=300.8017 | Everest 1830 |
| `evrst48` | 6377304.063 | rf=300.8017 | Everest 1948 |
| `evrst56` | 6377301.243 | rf=300.8017 | Everest 1956 |
| `evrst69` | 6377295.664 | rf=300.8017 | Everest 1969 |
| `evrstSS` | 6377298.556 | rf=300.8017 | Everest (Sabah and Sarawak) |
| `fschr60` | 6378166 | rf=298.3 | Fischer (Mercury Datum) 1960 |
| `fschr60m` | 6378155 | rf=298.3 | Fischer 1960 |
| `fschr68` | 6378150 | rf=298.3 | Fischer 1968 |
| `helmert` | 6378200 | rf=298.3 | Helmert 1906 |
| `hough` | 6378270 | rf=297 | Hough |
| `intl` | 6378388 | rf=297 | International 1909 (Hayford) |
| `kaula` | 6378163 | rf=298.24 | Kaula 1961 |
| `lerch` | 6378139 | rf=298.257 | Lerch 1979 |
| `mprts` | 6397300 | rf=191 | Maupertius 1738 |
| `new_intl` | 6378157.5 | b=6356772.2 | New International 1967 |
| `plessis` | 6376523 | b=6355863 | Plessis 1817 (France) |
| `krass` | 6378245 | rf=298.3 | Krassovsky, 1942 |
| `SEasia` | 6378155 | b=6356773.3205 | Southeast Asia |
| `walbeck` | 6376896 | b=6355834.8467 | Walbeck |
| `WGS60` | 6378165 | rf=298.3 | WGS 60 |
| `WGS66` | 6378145 | rf=298.25 | WGS 66 |
| `WGS7` | 6378135 | rf=298.26 | WGS 72 |
| `WGS84` | 6378137 | rf=298.257223563 | WGS 84 |
| `sphere` | 6370997 | b=6370997 | Normal Sphere (r=6370997) |

## Units

Retrieved via `Units.getToMeter(code)`. Returns the multiplication factor to convert to meters.

| Code | To-Meter Factor | Description |
|------|----------------|-------------|
| `m` | 1.0 | Metre |
| `mm` | 0.001 | Millimetre |
| `cm` | 0.01 | Centimetre |
| `dm` | 0.1 | Decimetre |
| `km` | 1000.0 | Kilometre |
| `in` | 0.0254 | International Inch |
| `ft` | 0.3048 | International Foot |
| `yd` | 0.9144 | International Yard |
| `mi` | 1609.344 | International Statute Mile |
| `ch` | 20.1168 | International Chain |
| `link` | 0.201168 | International Link |
| `fath` | 1.8288 | International Fathom |
| `kmi` | 1852.0 | International Nautical Mile |
| `us-in` | 0.025400050800101 | US Survey Inch |
| `us-ft` | 0.304800609601219 | US Survey Foot |
| `us-yd` | 0.914401828803658 | US Survey Yard |
| `us-ch` | 20.1168402336805 | US Survey Chain |
| `us-mi` | 1609.34721869444 | US Survey Mile |
| `ind-ft` | 0.30479841 | Indian Foot |
| `ind-yd` | 0.91439523 | Indian Yard |
| `ind-ch` | 20.11669506 | Indian Chain |

### Usage in PROJ Strings

```
+units=m       (metres)
+units=ft      (international feet)
+units=us-ft   (US survey feet)
+units=km      (kilometres)
```

## Prime Meridians

Retrieved via `PrimeMeridian.get(name)`. Returns the offset from Greenwich in degrees.

| Name | Offset from Greenwich |
|------|----------------------|
| `greenwich` | 0.0 |
| `lisbon` | -9.131906111111 |
| `paris` | 2.337229166667 |
| `bogota` | -74.080916666667 |
| `madrid` | -3.687938888889 |
| `rome` | 12.452333333333 |
| `bern` | 7.439583333333 |
| `jakarta` | 106.807719444444 |
| `ferro` | -17.666666666667 |
| `brussels` | 4.367975 |
| `stockholm` | 18.058277777778 |
| `athens` | 23.7163375 |
| `oslo` | 10.722916666667 |

### Usage in PROJ Strings

```
+pm=greenwich   (default, can be omitted)
+pm=paris       (French mapping)
+pm=jakarta     (Indonesian mapping)
```

## See Also

- [Datum Transformations](datum-transformations.md) -- how datums are used in transforms
- [Projections](projections.md) -- supported map projections
- [CRS Formats](crs-formats.md) -- specifying constants in CRS definitions
