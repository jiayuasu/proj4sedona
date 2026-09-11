#!/usr/bin/env python3
"""
Generate CRS parsing reference data using pyproj.

This script generates test cases for CRS parsing from various formats:
- EPSG codes
- WKT1 strings
- WKT2 strings
- PROJ strings
- PROJJSON strings
"""

import json
import math
from typing import Dict, List, Any
from pyproj import CRS


def extract_effective_ellipsoid(crs: CRS) -> Dict[str, float]:
    """Return the axes used by the operational PROJ definition."""
    operational = crs.to_dict()
    metadata_a = crs.ellipsoid.semi_major_metre
    metadata_b = crs.ellipsoid.semi_minor_metre

    if "R" in operational:
        radius = float(operational["R"])
        return {"semi_major_metre": radius, "semi_minor_metre": radius}

    semi_major = float(operational.get("a", metadata_a))
    if "b" in operational:
        semi_minor = float(operational["b"])
    elif "rf" in operational:
        semi_minor = semi_major * (1 - 1 / float(operational["rf"]))
    elif "f" in operational:
        semi_minor = semi_major * (1 - float(operational["f"]))
    elif "es" in operational:
        semi_minor = semi_major * math.sqrt(1 - float(operational["es"]))
    else:
        semi_minor = metadata_b

    return {
        "semi_major_metre": semi_major,
        "semi_minor_metre": semi_minor,
    }


def get_epsg_test_cases() -> List[Dict[str, Any]]:
    """Define EPSG codes to test parsing."""
    return [
        {"code": "EPSG:4326", "desc": "WGS84 Geographic"},
        {"code": "EPSG:3857", "desc": "Web Mercator"},
        {"code": "EPSG:4269", "desc": "NAD83 Geographic"},
        {"code": "EPSG:32610", "desc": "WGS84 UTM Zone 10N"},
        {"code": "EPSG:32632", "desc": "WGS84 UTM Zone 32N"},
        {"code": "EPSG:32733", "desc": "WGS84 UTM Zone 33S"},
        {"code": "EPSG:4277", "desc": "OSGB36 Geographic"},
        {"code": "EPSG:5041", "desc": "UPS North"},
        {"code": "EPSG:5042", "desc": "UPS South"},
        {"code": "EPSG:2154", "desc": "RGF93 / Lambert-93 (France)"},
    ]


def get_proj_string_test_cases() -> List[Dict[str, Any]]:
    """Define PROJ strings to test parsing."""
    return [
        {
            "proj_string": "+proj=longlat +datum=WGS84 +no_defs",
            "desc": "WGS84 Geographic"
        },
        {
            "proj_string": "+proj=merc +a=6378137 +b=6378137 +lat_ts=0 +lon_0=0 +x_0=0 +y_0=0 +k=1 +units=m +no_defs",
            "desc": "Web Mercator"
        },
        {
            "proj_string": "+proj=utm +zone=32 +datum=WGS84 +units=m +no_defs",
            "desc": "UTM Zone 32N"
        },
        {
            "proj_string": "+proj=utm +zone=33 +south +datum=WGS84 +units=m +no_defs",
            "desc": "UTM Zone 33S"
        },
        {
            "proj_string": "+proj=lcc +lat_1=33 +lat_2=45 +lat_0=39 +lon_0=-96 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs",
            "desc": "Lambert Conformal Conic"
        },
        {
            "proj_string": "+proj=tmerc +lat_0=0 +lon_0=9 +k=0.9996 +x_0=500000 +y_0=0 +datum=WGS84 +units=m +no_defs",
            "desc": "Transverse Mercator"
        },
        {
            "proj_string": "+proj=stere +lat_0=90 +lon_0=0 +k=0.994 +x_0=2000000 +y_0=2000000 +datum=WGS84 +units=m +no_defs",
            "desc": "Polar Stereographic North"
        },
        {
            "proj_string": "+proj=aea +lat_1=29.5 +lat_2=45.5 +lat_0=23 +lon_0=-96 +x_0=0 +y_0=0 +datum=NAD83 +units=m +no_defs",
            "desc": "Albers Equal Area Conic"
        },
        {
            "proj_string": "+proj=longlat +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +no_defs",
            "desc": "GRS80 with TOWGS84"
        },
    ]


def extract_crs_params(crs: CRS) -> Dict[str, Any]:
    """Extract key parameters from a CRS object."""
    params = {
        "name": crs.name,
        "type_name": crs.type_name,
        "is_geographic": crs.is_geographic,
        "is_projected": crs.is_projected,
        "is_compound": crs.is_compound,
        "is_vertical": crs.is_vertical,
        "is_engineering": crs.is_engineering,
    }
    
    # Get ellipsoid info
    if crs.ellipsoid:
        params["ellipsoid"] = {
            "name": crs.ellipsoid.name,
            "semi_major_metre": crs.ellipsoid.semi_major_metre,
            "semi_minor_metre": crs.ellipsoid.semi_minor_metre,
            "inverse_flattening": crs.ellipsoid.inverse_flattening,
        }

    # Projection libraries operate on the ellipsoid encoded by the effective
    # PROJ definition. This intentionally differs from CRS metadata for
    # EPSG:3857: the metadata names WGS84, while Pseudo-Mercator computes on a
    # sphere with a=b=6378137. Keep both representations explicit.
    params["effective_ellipsoid"] = extract_effective_ellipsoid(crs)
    
    # Get datum info
    if crs.datum:
        params["datum"] = {
            "name": crs.datum.name,
        }
    
    # Get coordinate operation params if projected
    if crs.is_projected:
        params["coordinate_operation"] = {
            "method_name": crs.coordinate_operation.method_name if crs.coordinate_operation else None,
        }
        # Get projection parameters
        if crs.coordinate_operation:
            params["projection_params"] = {}
            for param in crs.coordinate_operation.params:
                params["projection_params"][param.name] = param.value
    
    # Get axis info
    params["axis_info"] = []
    for axis in crs.axis_info:
        params["axis_info"].append({
            "name": axis.name,
            "abbrev": axis.abbrev,
            "direction": axis.direction,
            "unit_name": axis.unit_name,
        })
    
    return params


def generate_parsing_reference(output_file: str, verbose: bool = False) -> None:
    """Generate parsing reference data."""

    epsg_cases = get_epsg_test_cases()
    proj_cases = get_proj_string_test_cases()
    wkt_source_cases = epsg_cases[:5]
    expected_counts = {
        "epsg": len(epsg_cases),
        "proj_string": len(proj_cases),
        "wkt": len(wkt_source_cases) * 2,
    }
    expected_counts["total"] = sum(expected_counts.values())

    reference_data = {
        "version": "1.1",
        "generator": "pyproj",
        "pyproj_version": None,
        "expected_case_counts": expected_counts,
        "tolerance_m": 0.01,
        "epsg_test_cases": [],
        "proj_string_test_cases": [],
        "wkt_test_cases": [],
    }
    
    # Get pyproj version
    import pyproj
    reference_data["pyproj_version"] = pyproj.__version__
    
    # Process EPSG codes
    if verbose:
        print("  Processing EPSG codes...")
    
    for epsg_case in epsg_cases:
        if verbose:
            print(f"    {epsg_case['code']}")
        
        try:
            crs = CRS(epsg_case["code"])
            test_case = {
                "case_id": "epsg/" + epsg_case["code"].lower().replace(":", "_"),
                "input": epsg_case["code"],
                "description": epsg_case["desc"],
                "parsed_params": extract_crs_params(crs),
                "wkt1": crs.to_wkt(version="WKT1_GDAL"),
                "wkt2": crs.to_wkt(version="WKT2_2019"),
                "proj_string": crs.to_proj4(),
                "projjson": crs.to_json_dict(),
                "error": None
            }
        except Exception as e:
            test_case = {
                "case_id": "epsg/" + epsg_case["code"].lower().replace(":", "_"),
                "input": epsg_case["code"],
                "description": epsg_case["desc"],
                "parsed_params": None,
                "wkt1": None,
                "wkt2": None,
                "proj_string": None,
                "projjson": None,
                "error": str(e)
            }
        
        reference_data["epsg_test_cases"].append(test_case)
    
    # Process PROJ strings
    if verbose:
        print("  Processing PROJ strings...")
    
    for index, proj_case in enumerate(proj_cases):
        if verbose:
            print(f"    {proj_case['desc']}")
        
        try:
            crs = CRS(proj_case["proj_string"])
            test_case = {
                "case_id": f"proj_string/{index:02d}",
                "input": proj_case["proj_string"],
                "description": proj_case["desc"],
                "parsed_params": extract_crs_params(crs),
                "wkt1": crs.to_wkt(version="WKT1_GDAL"),
                "wkt2": crs.to_wkt(version="WKT2_2019"),
                "proj_string": crs.to_proj4(),
                "projjson": crs.to_json_dict(),
                "error": None
            }
        except Exception as e:
            test_case = {
                "case_id": f"proj_string/{index:02d}",
                "input": proj_case["proj_string"],
                "description": proj_case["desc"],
                "parsed_params": None,
                "wkt1": None,
                "wkt2": None,
                "proj_string": None,
                "projjson": None,
                "error": str(e)
            }
        
        reference_data["proj_string_test_cases"].append(test_case)
    
    # Generate WKT test cases from EPSG codes
    if verbose:
        print("  Generating WKT test cases...")
    
    for epsg_case in wkt_source_cases:
        source_id = epsg_case["code"].lower().replace(":", "_")
        try:
            crs = CRS(epsg_case["code"])
            wkt1 = crs.to_wkt(version="WKT1_GDAL")
            wkt2 = crs.to_wkt(version="WKT2_2019")
        except Exception as e:
            wkt1 = None
            wkt2 = None
            source_error = str(e)
        else:
            source_error = None

        # Keep two deterministic rows per source EPSG even if one export or parse
        # fails. Consumers can then reconcile the fixture's declared count.
        for format_name, wkt_value in (("wkt1", wkt1), ("wkt2", wkt2)):
            case_id = f"wkt/{source_id}/{format_name}"
            if source_error is not None:
                wkt_case = {
                    "case_id": case_id,
                    "input": epsg_case["code"],
                    "input_format": format_name.upper(),
                    "description": f"{format_name.upper()} from {epsg_case['code']}",
                    "parsed_params": None,
                    "error": source_error,
                }
            else:
                try:
                    crs_from_wkt = CRS(wkt_value)
                    wkt_case = {
                        "case_id": case_id,
                        "input": wkt_value,
                        "input_format": format_name.upper(),
                        "description": f"{format_name.upper()} from {epsg_case['code']}",
                        "parsed_params": extract_crs_params(crs_from_wkt),
                        "error": None,
                    }
                except Exception as e:
                    wkt_case = {
                        "case_id": case_id,
                        "input": wkt_value,
                        "input_format": format_name.upper(),
                        "description": f"{format_name.upper()} from {epsg_case['code']}",
                        "parsed_params": None,
                        "error": str(e),
                    }
            reference_data["wkt_test_cases"].append(wkt_case)
    
    # Write output
    with open(output_file, 'w') as f:
        json.dump(reference_data, f, indent=2, allow_nan=False)


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", "-o", default="parsing_reference.json")
    parser.add_argument("--verbose", "-v", action="store_true")
    args = parser.parse_args()
    
    generate_parsing_reference(args.output, args.verbose)
    print(f"Generated: {args.output}")
