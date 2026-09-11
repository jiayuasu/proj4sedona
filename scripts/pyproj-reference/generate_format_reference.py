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
        {
            "input": "+proj=longlat +datum=NAD27 +no_defs",
            "name": "nad27_geographic",
            "desc": "NAD27 Geographic with canonical grid-shift datum"
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
        {
            "input": "+proj=utm +zone=11 +datum=NAD27 +units=m +no_defs",
            "name": "nad27_utm_11n",
            "desc": "NAD27 UTM Zone 11N with canonical grid-shift datum"
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
        # Semantically sensitive serializer cases.  These guard properties that
        # cannot be inferred from the ellipsoid axes alone.
        {
            "input": "+proj=longlat +ellps=WGS84 +pm=paris +axis=enu +no_defs",
            "name": "paris_prime_meridian",
            "desc": "Geographic CRS with the Paris prime meridian"
        },
        {
            "input": "+proj=tmerc +lat_0=0 +lon_0=-75 +k=0.9996 +x_0=500000 +y_0=0 +datum=WGS84 +units=us-ft +axis=enu +no_defs",
            "name": "tmerc_us_survey_foot",
            "desc": "Transverse Mercator in US survey feet"
        },
        {
            "input": "+proj=tmerc +lat_0=0 +lon_0=15 +k=0.9996 +x_0=500000 +y_0=0 +datum=WGS84 +units=m +axis=neu +no_defs",
            "name": "tmerc_north_east_axis",
            "desc": "Transverse Mercator with north/east axis order"
        },
        {
            "input": "+proj=longlat +ellps=intl +towgs84=-87,-98,-121 +axis=enu +no_defs",
            "name": "longlat_three_parameter_datum",
            "desc": "Geographic CRS with a three-parameter datum transformation"
        },
    ]


def generate_format_reference(output_file: str, verbose: bool = False) -> None:
    """Generate format export reference data."""

    crs_definitions = get_test_crs_definitions()
    expected_formats = ["wkt1", "wkt2", "proj_string", "projjson"]
    supported_comparison_count = sum(
        len(definition.get("java_supported_formats", expected_formats))
        for definition in crs_definitions
    )
    comparison_count = len(crs_definitions) * len(expected_formats)
    reference_data = {
        "version": "1.2",
        "generator": "pyproj",
        "pyproj_version": None,
        "expected_test_case_count": len(crs_definitions),
        "expected_formats": expected_formats,
        "expected_semantic_checks": [
            "projection_method",
            "conversion_parameters",
            "utm_zone_hemisphere",
            "prime_meridian",
            "linear_unit",
            "axis",
            "datum_transform",
        ],
        "expected_comparison_count": comparison_count,
        "expected_supported_comparison_count": supported_comparison_count,
        "expected_rejection_count": comparison_count - supported_comparison_count,
        "tolerance_m": 0.01,
        "test_cases": []
    }
    
    # Get pyproj version
    import pyproj
    reference_data["pyproj_version"] = pyproj.__version__
    
    for crs_def in crs_definitions:
        if verbose:
            print(f"  Processing: {crs_def['name']}")
        
        try:
            crs = CRS(crs_def["input"])
            test_case = {
                "case_id": crs_def["name"],
                "name": crs_def["name"],
                "description": crs_def["desc"],
                "input": crs_def["input"],
                "java_supported_formats": crs_def.get(
                    "java_supported_formats", expected_formats
                ),
                "exports": {},
                "round_trip_verification": {},
                "error": None
            }

            export_functions = {
                "wkt1": lambda: crs.to_wkt(version="WKT1_GDAL"),
                "wkt2": lambda: crs.to_wkt(version="WKT2_2019"),
                "proj_string": crs.to_proj4,
                "projjson": crs.to_json_dict,
            }

            # Generate and verify each row independently so one failing exporter
            # cannot remove the remaining formats from the fixture.
            for fmt_name in expected_formats:
                try:
                    fmt_value = export_functions[fmt_name]()
                    test_case["exports"][fmt_name] = fmt_value
                except Exception as e:
                    test_case["exports"][fmt_name] = None
                    test_case["round_trip_verification"][fmt_name] = {
                        "success": False,
                        "error": f"export failed: {e}"
                    }
                    continue

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
                "case_id": crs_def["name"],
                "name": crs_def["name"],
                "description": crs_def["desc"],
                "input": crs_def["input"],
                "java_supported_formats": crs_def.get(
                    "java_supported_formats", expected_formats
                ),
                "exports": {fmt: None for fmt in expected_formats},
                "round_trip_verification": {
                    fmt: {"success": False, "error": str(e)}
                    for fmt in expected_formats
                },
                "error": str(e)
            }
        
        reference_data["test_cases"].append(test_case)
    
    # Write output
    with open(output_file, 'w') as f:
        json.dump(reference_data, f, indent=2, allow_nan=False)


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", "-o", default="format_export_reference.json")
    parser.add_argument("--verbose", "-v", action="store_true")
    args = parser.parse_args()
    
    generate_format_reference(args.output, args.verbose)
    print(f"Generated: {args.output}")
