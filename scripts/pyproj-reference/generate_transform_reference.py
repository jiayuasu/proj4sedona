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
        {"name": "sydney", "lon": 151.2093, "lat": -33.8688, "desc": "Sydney, Australia"},
        {"name": "buenos_aires", "lon": -58.3816, "lat": -34.6037, "desc": "Buenos Aires"},
        {"name": "cape_town", "lon": 18.4241, "lat": -33.9249, "desc": "Cape Town, SA"},
        # Edge cases
        {"name": "north_pole_edge", "lon": 0.0, "lat": 89.9, "desc": "Near North Pole"},
        {"name": "south_pole_edge", "lon": 0.0, "lat": -89.9, "desc": "Near South Pole"},
        {"name": "dateline_east", "lon": 179.9, "lat": 0.0, "desc": "Near dateline east"},
        {"name": "dateline_west", "lon": -179.9, "lat": 0.0, "desc": "Near dateline west"},
        {"name": "antimeridian", "lon": 180.0, "lat": 45.0, "desc": "On antimeridian"},
        {"name": "prime_meridian", "lon": 0.0, "lat": 45.0, "desc": "On prime meridian"},
        # Extreme but valid coordinates
        {"name": "extreme_north", "lon": 45.0, "lat": 85.0, "desc": "Extreme north"},
        {"name": "extreme_south", "lon": -45.0, "lat": -85.0, "desc": "Extreme south"},
    ]


def get_crs_pairs() -> List[Dict[str, Any]]:
    """Define CRS pairs for transformation tests."""
    return [
        # Common transformations
        {
            "name": "wgs84_to_webmerc",
            "from_crs": "EPSG:4326",
            "to_crs": "EPSG:3857",
            "desc": "WGS84 to Web Mercator"
        },
        {
            "name": "webmerc_to_wgs84",
            "from_crs": "EPSG:3857",
            "to_crs": "EPSG:4326",
            "desc": "Web Mercator to WGS84"
        },
        {
            "name": "wgs84_to_utm10n",
            "from_crs": "EPSG:4326",
            "to_crs": "EPSG:32610",
            "desc": "WGS84 to UTM Zone 10N"
        },
        {
            "name": "wgs84_to_utm32n",
            "from_crs": "EPSG:4326",
            "to_crs": "EPSG:32632",
            "desc": "WGS84 to UTM Zone 32N"
        },
        {
            "name": "wgs84_to_utm33s",
            "from_crs": "EPSG:4326",
            "to_crs": "EPSG:32733",
            "desc": "WGS84 to UTM Zone 33S"
        },
        # NAD83 transformations
        {
            "name": "wgs84_to_nad83",
            "from_crs": "EPSG:4326",
            "to_crs": "EPSG:4269",
            "desc": "WGS84 to NAD83"
        },
        # Datum transformations with TOWGS84
        {
            "name": "osgb36_to_wgs84",
            "from_crs": "EPSG:4277",
            "to_crs": "EPSG:4326",
            "desc": "OSGB36 to WGS84 (datum shift)"
        },
        {
            "name": "ed50_to_wgs84",
            "from_crs": "EPSG:4230",
            "to_crs": "EPSG:4326",
            "desc": "ED50 to WGS84 (European datum)"
        },
        # Lambert Conformal Conic
        {
            "name": "wgs84_to_lcc",
            "from_crs": "EPSG:4326",
            "to_crs": "+proj=lcc +lat_1=33 +lat_2=45 +lat_0=39 +lon_0=-96 +x_0=0 +y_0=0 +datum=WGS84 +units=m",
            "desc": "WGS84 to LCC (US)"
        },
        # Stereographic (polar)
        {
            "name": "wgs84_to_ups_north",
            "from_crs": "EPSG:4326",
            "to_crs": "EPSG:5041",
            "desc": "WGS84 to UPS North"
        },
        {
            "name": "wgs84_to_ups_south",
            "from_crs": "EPSG:4326",
            "to_crs": "EPSG:5042",
            "desc": "WGS84 to UPS South"
        },
    ]


def transform_point(transformer: Transformer, lon: float, lat: float) -> Dict[str, Any]:
    """Transform a single point and return results."""
    try:
        x, y = transformer.transform(lon, lat)
        return {
            "input": {"x": lon, "y": lat},
            "output": {"x": float(x), "y": float(y)},
            "error": None
        }
    except Exception as e:
        return {
            "input": {"x": lon, "y": lat},
            "output": None,
            "error": str(e)
        }


def generate_transform_reference(output_file: str, verbose: bool = False) -> None:
    """Generate transformation reference data."""
    
    test_coords = get_test_coordinates()
    crs_pairs = get_crs_pairs()
    
    reference_data = {
        "version": "1.0",
        "generator": "pyproj",
        "pyproj_version": None,
        "test_cases": []
    }
    
    # Get pyproj version
    import pyproj
    reference_data["pyproj_version"] = pyproj.__version__
    
    for crs_pair in crs_pairs:
        if verbose:
            print(f"  Processing: {crs_pair['name']}")
        
        try:
            from_crs = CRS(crs_pair["from_crs"])
            to_crs = CRS(crs_pair["to_crs"])
            transformer = Transformer.from_crs(from_crs, to_crs, always_xy=True)
            
            transformations = []
            for coord in test_coords:
                result = transform_point(transformer, coord["lon"], coord["lat"])
                result["coordinate_name"] = coord["name"]
                result["coordinate_desc"] = coord["desc"]
                transformations.append(result)
            
            test_case = {
                "name": crs_pair["name"],
                "description": crs_pair["desc"],
                "from_crs": crs_pair["from_crs"],
                "to_crs": crs_pair["to_crs"],
                "from_crs_wkt": from_crs.to_wkt(),
                "to_crs_wkt": to_crs.to_wkt(),
                "transformations": transformations,
                "error": None
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
                "error": str(e)
            }
        
        reference_data["test_cases"].append(test_case)
    
    # Write output
    with open(output_file, 'w') as f:
        json.dump(reference_data, f, indent=2)


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", "-o", default="transform_reference.json")
    parser.add_argument("--verbose", "-v", action="store_true")
    args = parser.parse_args()
    
    generate_transform_reference(args.output, args.verbose)
    print(f"Generated: {args.output}")
