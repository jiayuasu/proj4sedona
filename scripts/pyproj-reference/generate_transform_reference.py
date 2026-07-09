#!/usr/bin/env python3
"""
Generate transformation reference data using pyproj.

This script generates test cases for coordinate transformations covering:
- Common EPSG codes (4326, 3857, 4269, UTM zones)
- Datum transformations with TOWGS84 parameters
- Edge cases (poles, dateline, extreme coordinates)
"""

import json
from typing import Dict, List, Any
from pyproj import CRS, Transformer
from pyproj.exceptions import CRSError
import numpy as np


def get_test_coordinates() -> List[Dict[str, Any]]:
    """Define test coordinates with descriptions."""
    return [
        {"name": "origin", "lon": 0.0, "lat": 0.0, "desc": "Origin point"},
        {"name": "london", "lon": -0.1278, "lat": 51.5074, "desc": "London, UK"},
        {"name": "new_york", "lon": -74.006, "lat": 40.7128, "desc": "New York City"},
        {"name": "tokyo", "lon": 139.6917, "lat": 35.6895, "desc": "Tokyo, Japan"},
        {
            "name": "sydney",
            "lon": 151.2093,
            "lat": -33.8688,
            "desc": "Sydney, Australia",
        },
        {
            "name": "buenos_aires",
            "lon": -58.3816,
            "lat": -34.6037,
            "desc": "Buenos Aires",
        },
        {"name": "cape_town", "lon": 18.4241, "lat": -33.9249, "desc": "Cape Town, SA"},
        # Edge cases
        {"name": "north_pole_edge", "lon": 0.0, "lat": 89.9, "desc": "Near North Pole"},
        {
            "name": "south_pole_edge",
            "lon": 0.0,
            "lat": -89.9,
            "desc": "Near South Pole",
        },
        {
            "name": "dateline_east",
            "lon": 179.9,
            "lat": 0.0,
            "desc": "Near dateline east",
        },
        {
            "name": "dateline_west",
            "lon": -179.9,
            "lat": 0.0,
            "desc": "Near dateline west",
        },
        {"name": "antimeridian", "lon": 180.0, "lat": 45.0, "desc": "On antimeridian"},
        {
            "name": "prime_meridian",
            "lon": 0.0,
            "lat": 45.0,
            "desc": "On prime meridian",
        },
        # Extreme but valid coordinates
        {"name": "extreme_north", "lon": 45.0, "lat": 85.0, "desc": "Extreme north"},
        {"name": "extreme_south", "lon": -45.0, "lat": -85.0, "desc": "Extreme south"},
    ]


def _pts(*lonlats: Any) -> List[Dict[str, Any]]:
    """Compact helper: build a test_points list from (lon, lat) tuples."""
    return [
        {"name": f"pt_{i}", "lon": lon, "lat": lat, "desc": f"({lon}, {lat})"}
        for i, (lon, lat) in enumerate(lonlats)
    ]


def get_projection_coverage_pairs() -> List[Dict[str, Any]]:
    """One case per supported projection, verifying projection math against pyproj.

    Definitions use +datum=WGS84 (ellipsoidal) or an explicit sphere (+a=+b) so no
    datum shift is involved and the comparison exercises the projection math alone.
    Regional projections carry in-domain test_points; world projections use the
    shared global list. Keep this list in sync with ProjectionRegistry: every new
    projection port should add a case here so CI verifies it against pyproj.
    """
    return [
        # --- Cylindrical / transverse ---
        {
            "name": "proj_tmerc",
            "from_crs": "EPSG:4326",
            "to_crs": "+proj=tmerc +lat_0=0 +lon_0=9 +k=0.9996 +x_0=500000 +y_0=0 +datum=WGS84 +units=m +no_defs",
            "desc": "Transverse Mercator (etmerc)",
            "test_points": _pts((13.4, 52.5), (12.5, 41.9), (10.7, 59.9), (5.0, 45.0)),
        },
        {
            "name": "proj_somerc",
            "from_crs": "EPSG:4326",
            "to_crs": "+proj=somerc +lat_0=46.95240555555556 +lon_0=7.439583333333333 +k_0=1 +x_0=2600000 +y_0=1200000 +datum=WGS84 +units=m +no_defs",
            "desc": "Swiss Oblique Mercator",
            "test_points": _pts((7.44, 46.95), (8.55, 47.37), (6.14, 46.2), (9.5, 47.5)),
        },
        {
            "name": "proj_omerc",
            "from_crs": "EPSG:4326",
            "to_crs": "+proj=omerc +lat_0=4 +lonc=102.25 +alpha=323.0257964666666 +k=0.99984 +x_0=804671 +y_0=0 +no_uoff +gamma=323.1301023611111 +datum=WGS84 +units=m +no_defs",
            "desc": "Hotine Oblique Mercator (variant A)",
            "test_points": _pts((102.25, 4.0), (102.5, 4.2), (101.7, 3.06), (103.5, 5.5)),
        },
        {
            # Gauss Laborde Reunion; international ellipsoid source keeps the
            # comparison pure projection math (no datum leg).
            "name": "proj_gstmerc",
            "from_crs": "+proj=longlat +ellps=intl +no_defs",
            "to_crs": "+proj=gstmerc +lat_0=-21.116666667 +lon_0=55.53333333 +k_0=1 +x_0=160000 +y_0=50000 +ellps=intl +units=m +no_defs",
            "desc": "Gauss-Schreiber Transverse Mercator (Reunion)",
            "test_points": _pts((55.53333333, -21.116666667), (55.5, -21.1), (55.7, -21.3), (55.3, -20.9)),
        },
        {
            "name": "proj_cass",
            "from_crs": "EPSG:4326",
            "to_crs": "+proj=cass +lat_0=11.25217861111111 +lon_0=-60.68600888888889 +x_0=187500 +y_0=180000 +datum=WGS84 +units=m +no_defs",
            "desc": "Cassini-Soldner",
            "test_points": _pts((-60.7, 11.25), (-60.5, 11.4), (-60.9, 11.1)),
        },
        {
            "name": "proj_mill",
            "from_crs": "EPSG:4326",
            "to_crs": "+proj=mill +lon_0=0 +x_0=0 +y_0=0 +a=6378137 +b=6378137 +units=m +no_defs",
            "desc": "Miller Cylindrical (sphere)",
        },
        {
            "name": "proj_eqc",
            "from_crs": "EPSG:4326",
            "to_crs": "+proj=eqc +lat_ts=30 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs",
            "desc": "Equidistant Cylindrical",
        },
        {
            "name": "proj_cea",
            "from_crs": "EPSG:4326",
            "to_crs": "+proj=cea +lat_ts=30 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs",
            "desc": "Cylindrical Equal Area",
        },
        # --- Conic ---
        {
            "name": "proj_aea",
            "from_crs": "EPSG:4326",
            "to_crs": "+proj=aea +lat_1=29.5 +lat_2=45.5 +lat_0=23 +lon_0=-96 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs",
            "desc": "Albers Equal Area (US)",
            "test_points": _pts((-105.0, 39.7), (-87.6, 41.9), (-96.8, 32.8), (-74.0, 40.7)),
        },
        {
            "name": "proj_eqdc",
            "from_crs": "EPSG:4326",
            "to_crs": "+proj=eqdc +lat_1=33 +lat_2=45 +lat_0=39 +lon_0=-96 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs",
            "desc": "Equidistant Conic (US)",
            "test_points": _pts((-105.0, 39.7), (-87.6, 41.9), (-96.8, 32.8), (-74.0, 40.7)),
        },
        {
            "name": "proj_poly",
            "from_crs": "EPSG:4326",
            "to_crs": "+proj=poly +lat_0=0 +lon_0=-54 +x_0=5000000 +y_0=10000000 +datum=WGS84 +units=m +no_defs",
            "desc": "American Polyconic (Brazil)",
            "test_points": _pts((-54.0, -15.0), (-47.9, -15.8), (-60.0, -2.0), (-43.2, -22.9)),
        },
        {
            # Krovak is always on the Bessel ellipsoid; use a Bessel geographic source
            # so the comparison is pure projection math (a WGS84 source would add a
            # datum leg whose towgs84 handling differs between implementations).
            "name": "proj_krovak",
            "from_crs": "+proj=longlat +ellps=bessel +no_defs",
            "to_crs": "+proj=krovak +lat_0=49.5 +lon_0=24.83333333333333 +alpha=30.28813972222222 +k=0.9999 +x_0=0 +y_0=0 +ellps=bessel +units=m +no_defs",
            "desc": "Krovak (S-JTSK, Bessel geographic source)",
            "test_points": _pts((14.42, 50.08), (16.6, 49.2), (17.1, 48.15)),
        },
        {
            "name": "proj_bonne",
            "from_crs": "EPSG:4326",
            "to_crs": "+proj=bonne +lat_1=40 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs",
            "desc": "Bonne (ellipsoidal)",
            "test_points": _pts((5.0, 45.0), (10.0, 50.0), (-3.0, 38.0), (18.4, -33.9)),
        },
        # --- Azimuthal ---
        {
            "name": "proj_stere_oblique",
            "from_crs": "EPSG:4326",
            "to_crs": "+proj=stere +lat_0=52 +lon_0=5 +k=0.9999 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs",
            "desc": "Oblique Stereographic (Snyder)",
            "test_points": _pts((5.0, 52.0), (7.0, 53.0), (3.5, 50.5)),
        },
        {
            "name": "proj_sterea",
            "from_crs": "EPSG:4326",
            "to_crs": "+proj=sterea +lat_0=52.15616055555555 +lon_0=5.38763888888889 +k=0.9999079 +x_0=155000 +y_0=463000 +datum=WGS84 +units=m +no_defs",
            "desc": "Oblique Stereographic Alternative (RD-style)",
            "test_points": _pts((5.2, 52.25), (4.9, 52.37), (6.5, 53.2)),
        },
        {
            "name": "proj_laea",
            "from_crs": "EPSG:4326",
            "to_crs": "+proj=laea +lat_0=52 +lon_0=10 +x_0=4321000 +y_0=3210000 +datum=WGS84 +units=m +no_defs",
            "desc": "Lambert Azimuthal Equal Area (Europe)",
            "test_points": _pts((10.0, 52.0), (5.0, 45.0), (20.0, 60.0), (-3.0, 40.0)),
        },
        {
            "name": "proj_aeqd",
            "from_crs": "EPSG:4326",
            "to_crs": "+proj=aeqd +lat_0=40 +lon_0=-100 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs",
            "desc": "Azimuthal Equidistant (ellipsoidal)",
            "test_points": _pts((-100.0, 40.0), (-105.0, 39.7), (-87.6, 41.9), (-96.8, 32.8)),
        },
        {
            "name": "proj_gnom",
            "from_crs": "EPSG:4326",
            "to_crs": "+proj=gnom +lat_0=45 +lon_0=0 +x_0=0 +y_0=0 +a=6378137 +b=6378137 +units=m +no_defs",
            "desc": "Gnomonic (sphere)",
            "test_points": _pts((5.0, 50.0), (-3.0, 44.0), (2.0, 47.0), (10.0, 52.0)),
        },
        {
            "name": "proj_ortho",
            "from_crs": "EPSG:4326",
            "to_crs": "+proj=ortho +lat_0=45 +lon_0=0 +x_0=0 +y_0=0 +a=6378137 +b=6378137 +units=m +no_defs",
            "desc": "Orthographic (sphere)",
            "test_points": _pts((5.0, 50.0), (-3.0, 44.0), (2.0, 47.0), (10.0, 40.0)),
        },
        {
            "name": "proj_tpers",
            "from_crs": "EPSG:4326",
            "to_crs": "+proj=tpers +lat_0=40 +lon_0=-100 +h=5500000 +tilt=30 +azi=20 +a=6378137 +b=6378137 +units=m +no_defs",
            "desc": "Tilted Perspective (oblique, sphere)",
            "test_points": _pts((-100.0, 40.0), (-95.0, 42.0), (-105.0, 35.0)),
        },
        # --- Pseudocylindrical / world ---
        {
            "name": "proj_sinu",
            "from_crs": "EPSG:4326",
            "to_crs": "+proj=sinu +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs",
            "desc": "Sinusoidal (ellipsoidal)",
        },
        {
            "name": "proj_moll",
            "from_crs": "EPSG:4326",
            "to_crs": "+proj=moll +lon_0=0 +x_0=0 +y_0=0 +a=6371000 +b=6371000 +units=m +no_defs",
            "desc": "Mollweide (sphere)",
        },
        {
            # Known difference vs PROJ: proj4js's Robinson table interpolation differs
            # from PROJ's by up to ~0.4 m (relative ~3e-8) — an upstream proj4js-vs-PROJ
            # difference, faithfully ported. Expect a sub-meter max error here.
            "name": "proj_robin",
            "from_crs": "EPSG:4326",
            "to_crs": "+proj=robin +lon_0=0 +x_0=0 +y_0=0 +a=6371000 +b=6371000 +units=m +no_defs",
            "desc": "Robinson (sphere; ~0.4 m known proj4js-vs-PROJ difference)",
        },
        {
            "name": "proj_vandg",
            "from_crs": "EPSG:4326",
            "to_crs": "+proj=vandg +lon_0=0 +x_0=0 +y_0=0 +a=6371007 +b=6371007 +units=m +no_defs",
            "desc": "Van der Grinten (sphere)",
        },
        {
            "name": "proj_eqearth",
            "from_crs": "EPSG:4326",
            "to_crs": "+proj=eqearth +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs",
            "desc": "Equal Earth (ellipsoidal)",
        },
        {
            "name": "proj_eck6",
            "from_crs": "EPSG:4326",
            "to_crs": "+proj=eck6 +lon_0=0 +x_0=0 +y_0=0 +a=6371007 +b=6371007 +units=m +no_defs",
            "desc": "Eckert VI (sphere)",
        },
        # --- Miscellaneous ---
        {
            "name": "proj_geos_y",
            "from_crs": "EPSG:4326",
            "to_crs": "+proj=geos +h=35785831 +sweep=y +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs",
            "desc": "Geostationary (y sweep, ellipsoidal)",
            "test_points": _pts((0.0, 0.0), (10.0, -5.0), (-8.0, 12.0), (30.0, 40.0)),
        },
        {
            "name": "proj_geos_x",
            "from_crs": "EPSG:4326",
            "to_crs": "+proj=geos +h=35786023 +sweep=x +lon_0=-75 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs",
            "desc": "Geostationary (x sweep, ellipsoidal)",
            "test_points": _pts((-75.0, 0.0), (-95.0, 25.0), (-60.0, 10.0)),
        },
    ]


def get_crs_pairs() -> List[Dict[str, Any]]:
    """Define CRS pairs for transformation tests."""
    return get_projection_coverage_pairs() + [
        # Common transformations
        {
            "name": "wgs84_to_webmerc",
            "from_crs": "EPSG:4326",
            "to_crs": "EPSG:3857",
            "desc": "WGS84 to Web Mercator",
        },
        {
            "name": "webmerc_to_wgs84",
            "from_crs": "EPSG:3857",
            "to_crs": "EPSG:4326",
            "desc": "Web Mercator to WGS84",
        },
        {
            "name": "wgs84_to_utm10n",
            "from_crs": "EPSG:4326",
            "to_crs": "EPSG:32610",
            "desc": "WGS84 to UTM Zone 10N",
        },
        {
            "name": "wgs84_to_utm32n",
            "from_crs": "EPSG:4326",
            "to_crs": "EPSG:32632",
            "desc": "WGS84 to UTM Zone 32N",
        },
        {
            "name": "wgs84_to_utm33s",
            "from_crs": "EPSG:4326",
            "to_crs": "EPSG:32733",
            "desc": "WGS84 to UTM Zone 33S",
        },
        # NAD83 transformations
        {
            "name": "wgs84_to_nad83",
            "from_crs": "EPSG:4326",
            "to_crs": "EPSG:4269",
            "desc": "WGS84 to NAD83",
        },
        # Datum transformations with TOWGS84
        {
            "name": "osgb36_to_wgs84",
            "from_crs": "EPSG:4277",
            "to_crs": "EPSG:4326",
            "desc": "OSGB36 to WGS84 (datum shift)",
        },
        {
            "name": "ed50_to_wgs84",
            "from_crs": "EPSG:4230",
            "to_crs": "EPSG:4326",
            "desc": "ED50 to WGS84 (European datum)",
        },
        # Lambert Conformal Conic
        {
            "name": "wgs84_to_lcc",
            "from_crs": "EPSG:4326",
            "to_crs": "+proj=lcc +lat_1=33 +lat_2=45 +lat_0=39 +lon_0=-96 +x_0=0 +y_0=0 +datum=WGS84 +units=m",
            "desc": "WGS84 to LCC (US)",
        },
        # Stereographic (polar)
        {
            "name": "wgs84_to_ups_north",
            "from_crs": "EPSG:4326",
            "to_crs": "EPSG:5041",
            "desc": "WGS84 to UPS North",
        },
        {
            "name": "wgs84_to_ups_south",
            "from_crs": "EPSG:4326",
            "to_crs": "EPSG:5042",
            "desc": "WGS84 to UPS South",
        },
    ]


def transform_point(transformer: Transformer, lon: float, lat: float) -> Dict[str, Any]:
    """Transform a single point and return results."""
    try:
        x, y = transformer.transform(lon, lat)
        # Non-finite output (e.g. a point outside the projection's domain, such as
        # the far side of a Geostationary or Orthographic view) is recorded as an
        # error so consumers skip it; json.dump would otherwise emit non-standard
        # `Infinity` tokens.
        if not (np.isfinite(x) and np.isfinite(y)):
            return {
                "input": {"x": lon, "y": lat},
                "output": None,
                "error": "non-finite result (outside projection domain)",
            }
        return {
            "input": {"x": lon, "y": lat},
            "output": {"x": float(x), "y": float(y)},
            "error": None,
        }
    except Exception as e:
        return {"input": {"x": lon, "y": lat}, "output": None, "error": str(e)}


def generate_transform_reference(output_file: str, verbose: bool = False) -> None:
    """Generate transformation reference data."""

    test_coords = get_test_coordinates()
    crs_pairs = get_crs_pairs()

    reference_data = {
        "version": "1.0",
        "generator": "pyproj",
        "pyproj_version": None,
        "test_cases": [],
    }

    # Get pyproj version
    import pyproj

    reference_data["pyproj_version"] = pyproj.__version__

    # WGS84 for checking if input transformation is needed
    wgs84 = CRS("EPSG:4326")

    for crs_pair in crs_pairs:
        if verbose:
            print(f"  Processing: {crs_pair['name']}")

        try:
            from_crs = CRS(crs_pair["from_crs"])
            to_crs = CRS(crs_pair["to_crs"])
            transformer = Transformer.from_crs(from_crs, to_crs, always_xy=True)

            # Check if we need to transform input coordinates from WGS84 to from_crs
            # This is needed when from_crs is NOT WGS84 (e.g., webmerc_to_wgs84)
            need_input_transform = not from_crs.equals(wgs84)
            input_transformer = None
            if need_input_transform:
                input_transformer = Transformer.from_crs(
                    wgs84, from_crs, always_xy=True
                )

            # Regional projections define their own in-domain test points; world
            # projections use the shared global list.
            case_coords = crs_pair.get("test_points", test_coords)

            transformations = []
            for coord in case_coords:
                # Get input coordinates in from_crs coordinate system
                if need_input_transform:
                    try:
                        input_x, input_y = input_transformer.transform(
                            coord["lon"], coord["lat"]
                        )
                        # Check for invalid results (inf, nan)
                        if not (np.isfinite(input_x) and np.isfinite(input_y)):
                            continue
                    except Exception:
                        # Skip coordinates that can't be transformed to from_crs
                        continue
                else:
                    input_x, input_y = coord["lon"], coord["lat"]

                result = transform_point(transformer, input_x, input_y)
                result["coordinate_name"] = coord["name"]
                result["coordinate_desc"] = coord["desc"]
                # Store original WGS84 reference for traceability
                result["wgs84_reference"] = {"lon": coord["lon"], "lat": coord["lat"]}
                transformations.append(result)

            test_case = {
                "name": crs_pair["name"],
                "description": crs_pair["desc"],
                "from_crs": crs_pair["from_crs"],
                "to_crs": crs_pair["to_crs"],
                "from_crs_wkt": from_crs.to_wkt(),
                "to_crs_wkt": to_crs.to_wkt(),
                "transformations": transformations,
                "error": None,
            }
        except Exception as e:
            test_case = {
                "name": crs_pair["name"],
                "description": crs_pair["desc"],
                "from_crs": crs_pair["from_crs"],
                "to_crs": crs_pair["to_crs"],
                "from_crs_wkt": None,
                "to_crs_wkt": None,
                "transformations": [],
                "error": str(e),
            }

        reference_data["test_cases"].append(test_case)

    # Write output
    with open(output_file, "w") as f:
        json.dump(reference_data, f, indent=2)


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser()
    parser.add_argument("--output", "-o", default="transform_reference.json")
    parser.add_argument("--verbose", "-v", action="store_true")
    args = parser.parse_args()

    generate_transform_reference(args.output, args.verbose)
    print(f"Generated: {args.output}")
