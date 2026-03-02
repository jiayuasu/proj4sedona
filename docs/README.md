# Proj4Sedona Documentation

Proj4Sedona is a high-performance Java library for coordinate system transformations, ported from [Proj4js](https://github.com/proj4js/proj4js). It supports PROJ strings, WKT1, WKT2, PROJJSON, and EPSG code inputs, 21 map projections, datum transformations with grid shift support, MGRS conversion, and JTS geometry integration.

## Guides

- [Getting Started](getting-started.md) -- Installation, first transformation, core concepts
- [Coordinate Transformations](coordinate-transformations.md) -- Single, batch, and flat array transforms; converters and performance tips
- [CRS Formats](crs-formats.md) -- Parsing and exporting PROJ strings, WKT1, WKT2, PROJJSON, and EPSG codes
- [Projections](projections.md) -- All 21 supported map projections with examples
- [Datum Transformations](datum-transformations.md) -- 3-parameter, 7-parameter, and grid-based datum shifts
- [Grid Shifts](grid-shifts.md) -- NTv2 and GeoTIFF grid loading, PROJ CDN auto-fetching, cache configuration
- [MGRS Coordinates](mgrs.md) -- Military Grid Reference System conversion and UPS polar support
- [JTS Integration](jts-integration.md) -- Transforming JTS geometries (Point, LineString, Polygon, Multi*, GeometryCollection)
- [CRS Registry](crs-registry.md) -- Built-in definitions, URL providers, custom providers, aliases
- [Caching and Performance](caching-and-performance.md) -- Projection caching, preloading, batch transforms, thread safety

## Reference

- [Constants Reference](constants-reference.md) -- Built-in datums, ellipsoids, units, and prime meridians
