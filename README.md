# Proj4Sedona

A high-performance Java library for coordinate system transformations, ported from Proj4js (https://github.com/proj4js/proj4js).

## Overview

Proj4Sedona provides coordinate system transformations, datum conversions, and projection operations for geospatial applications.

**Key Features:**
- 🚀 High-performance: faster than Python's pyproj
- 🌍 Format support: PROJ strings, WKT1/WKT2, PROJJSON, EPSG codes
- 🗺️ 21 map projections (Mercator, UTM, Lambert, Albers, Sinusoidal, Robinson, etc.)
- 🎯 MGRS coordinate conversion
- 📊 GeoTIFF datum grids with PROJ CDN integration
- ✅ lots of unit tests

## Quick Start

### Maven Dependency


### Basic Usage


### MGRS Coordinates


### Datum Transformations with GeoTIFF Grids


## Supported Projections

21 map projections including:
- **Cylindrical**: Mercator, Transverse Mercator, UTM, Miller, Equirectangular, Cylindrical Equal Area
- **Pseudocylindrical**: Sinusoidal, Mollweide, Robinson, Equal Earth
- **Conic**: Lambert Conformal Conic, Albers Equal Area, Equidistant Conic
- **Azimuthal**: Lambert Azimuthal Equal Area, Stereographic, Azimuthal Equidistant, Orthographic, Gnomonic
- **Other**: Van der Grinten, Hotine Oblique Mercator

## Building

## Performance Benchmarks

Located in the `benchmark/` directory:


**Results**: Proj4Sedona is 2-5x faster than pyproj for most transformations.

## Use Cases

Proj4Sedona is suitable for a wide range of geospatial applications.
