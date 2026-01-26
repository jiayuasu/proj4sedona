#!/usr/bin/env python3
"""
Generate CRS format export reference data using pyproj.

This script generates test cases for CRS export to various formats:
- WKT1 (OGC Well-Known Text 1)
- WKT2 (ISO 19162:2019)
- PROJ string
- PROJJSON
"""

import json
from typing import Dict, List, Any
from pyproj import CRS


def get_test_crs_definitions() -> List[Dict[str, Any]]:
    """Define CRS definitions to test format exports."""
    return [
        # Geographic CRS
        {
            "input": "EPSG:4326",
            "name": "wgs84_geographic",
            "desc": "WGS84 Geographic"
        },
        {
            "input": "EPSG:4269",
            "name": "nad83_geographic",
            "desc": "NAD83 Geographic"
        },
        # Projected CRS - Mercator
        {
            "input": "EPSG:3857",
            "name": "web_mercator",
            "desc": "Web Mercator"
        },
        # UTM Zones
        {
            "input": "EPSG:32610",
            "name": "utm_10n",
            "desc": "UTM Zone 10N"
        },
        {
            "input": "EPSG:32632",
            "name": "utm_32n",
            "desc": "UTM Zone 32N"
        },
        {
            "input": "EPSG:32733",
            "name": "utm_33s",
            "desc": "UTM Zone 33S"
        },
        # Lambert Conformal Conic (from PROJ string)
        {
            "input": "+proj=lcc +lat_1=33 +lat_2=45 +lat_0=39 +lon_0=-96 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs",
            "name": "lcc_us",
            "desc": "Lambert Conformal Conic (US)"
        },
        # Transverse Mercator
        {
            "input": "+proj=tmerc +lat_0=0 +lon_0=9 +k=0.9996 +x_0=500000 +y_0=0 +datum=WGS84 +units=m +no_defs",
            "name": "tmerc_custom",
            "desc": "Transverse Mercator"
        },
        # Stereographic
        {
            "input": "EPSG:5041",
            "name": "ups_north",
            "desc": "UPS North (Polar Stereographic)"
        },
        # Albers Equal Area
        {
            "input": "+proj=aea +lat_1=29.5 +lat_2=45.5 +lat_0=23 +lon_0=-96 +x_0=0 +y_0=0 +datum=NAD83 +units=m +no_defs",
            "name": "aea_conus",
            "desc": "Albers Equal Area (CONUS)"
        },
        # Equidistant Conic
        {
            "input": "+proj=eqdc +lat_0=40 +lon_0=-96 +lat_1=20 +lat_2=60 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs",
            "name": "eqdc_custom",
            "desc": "Equidistant Conic"
        },
    ]


def generate_format_reference(output_file: str, verbose: bool = False) -> None:
    """Generate format export reference data."""
    
    reference_data = {
        "version": "1.0",
        "generator": "pyproj",
        "pyproj_version": None,
        "test_cases": []
    }
    
    # Get pyproj version
    import pyproj
    reference_data["pyproj_version"] = pyproj.__version__
    
    for crs_def in get_test_crs_definitions():
        if verbose:
            print(f"  Processing: {crs_def['name']}")
        
        try:
            crs = CRS(crs_def["input"])
            
            # Export to all formats
            test_case = {
                "name": crs_def["name"],
                "description": crs_def["desc"],
                "input": crs_def["input"],
                "exports": {
                    "wkt1": crs.to_wkt(version="WKT1_GDAL"),
                    "wkt2": crs.to_wkt(version="WKT2_2019"),
                    "proj_string": crs.to_proj4(),
                    "projjson": crs.to_json_dict(),
                },
                "round_trip_verification": {},
                "error": None
            }
            
            # Verify round-trip for each format
            for fmt_name, fmt_value in test_case["exports"].items():
                if fmt_value is None:
                    test_case["round_trip_verification"][fmt_name] = {
                        "success": False,
                        "error": "Export returned None"
                    }
                    continue
                    
                try:
                    if fmt_name == "projjson":
                        # For PROJJSON, we need to convert dict to string first
                        crs_roundtrip = CRS.from_json_dict(fmt_value)
                    else:
                        crs_roundtrip = CRS(fmt_value)
                    
                    # Check if round-trip preserves key properties
                    test_case["round_trip_verification"][fmt_name] = {
                        "success": True,
                        "preserves_type": crs.type_name == crs_roundtrip.type_name,
                        "preserves_ellipsoid_a": abs(
                            crs.ellipsoid.semi_major_metre - crs_roundtrip.ellipsoid.semi_major_metre
                        ) < 0.01 if crs.ellipsoid and crs_roundtrip.ellipsoid else None,
                        "error": None
                    }
                except Exception as e:
                    test_case["round_trip_verification"][fmt_name] = {
                        "success": False,
                        "error": str(e)
                    }
            
        except Exception as e:
            test_case = {
                "name": crs_def["name"],
                "description": crs_def["desc"],
                "input": crs_def["input"],
                "exports": None,
                "round_trip_verification": None,
                "error": str(e)
            }
        
        reference_data["test_cases"].append(test_case)
    
    # Write output
    with open(output_file, 'w') as f:
        json.dump(reference_data, f, indent=2)


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", "-o", default="format_export_reference.json")
    parser.add_argument("--verbose", "-v", action="store_true")
    args = parser.parse_args()
    
    generate_format_reference(args.output, args.verbose)
    print(f"Generated: {args.output}")
