#!/usr/bin/env python3
"""
Generate grid-based transformation reference data using pyproj.

This script generates test cases for coordinate transformations that use
grid files (like NTv2 grids) from cdn.proj.org.

Grid files tested:
- us_noaa_conus.tif (NAD83 to NAD83(HARN) for CONUS)
- ca_nrc_ntv2_0.tif (NAD27 to NAD83 for Canada)
- uk_os_OSTN15_NTv2_OSGBtoETRS.tif (OSGB36 to ETRS89 for Great Britain)
"""

import json
from typing import Dict, List, Any
from pyproj import CRS, Transformer
from pyproj.transformer import TransformerGroup
import os


def get_grid_test_cases() -> List[Dict[str, Any]]:
    """Define grid-based transformation test cases."""
    return [
        # US CONUS grid transformation (NAD83 to NAD83(HARN))
        {
            "name": "conus_nad83_to_harn",
            "from_crs": "EPSG:4269",  # NAD83
            "to_crs": "EPSG:4152",    # NAD83(HARN)
            "grid_file": "us_noaa_conus.tif",
            "test_points": [
                {"name": "denver", "lon": -104.9903, "lat": 39.7392},
                {"name": "chicago", "lon": -87.6298, "lat": 41.8781},
                {"name": "los_angeles", "lon": -118.2437, "lat": 34.0522},
                {"name": "miami", "lon": -80.1918, "lat": 25.7617},
                {"name": "seattle", "lon": -122.3321, "lat": 47.6062},
            ],
            "desc": "NAD83 to NAD83(HARN) using CONUS grid"
        },
        # Canada NTv2 grid transformation (NAD27 to NAD83)
        {
            "name": "canada_nad27_to_nad83",
            "from_crs": "EPSG:4267",  # NAD27
            "to_crs": "EPSG:4269",    # NAD83
            "grid_file": "ca_nrc_ntv2_0.tif",
            "test_points": [
                {"name": "toronto", "lon": -79.3832, "lat": 43.6532},
                {"name": "vancouver", "lon": -123.1207, "lat": 49.2827},
                {"name": "montreal", "lon": -73.5673, "lat": 45.5017},
                {"name": "calgary", "lon": -114.0719, "lat": 51.0447},
                {"name": "ottawa", "lon": -75.6972, "lat": 45.4215},
            ],
            "desc": "NAD27 to NAD83 using Canadian NTv2 grid"
        },
        # Test with PROJ string using nadgrids
        {
            "name": "proj_nadgrids_conus",
            "from_crs": "+proj=longlat +datum=NAD83 +no_defs",
            "to_crs": "+proj=longlat +ellps=GRS80 +nadgrids=@us_noaa_conus.tif +no_defs",
            "grid_file": "us_noaa_conus.tif",
            "test_points": [
                {"name": "phoenix", "lon": -112.0740, "lat": 33.4484},
                {"name": "dallas", "lon": -96.7970, "lat": 32.7767},
            ],
            "desc": "NAD83 with explicit nadgrids parameter"
        },
        # UK OSTN15 grid transformation (ETRS89 to OSGB36)
        {
            "name": "uk_etrs89_to_osgb36",
            "from_crs": "EPSG:4258",  # ETRS89
            "to_crs": "EPSG:4277",    # OSGB36
            "grid_file": "uk_os_OSTN15_NTv2_OSGBtoETRS.tif",
            "test_points": [
                {"name": "london", "lon": -0.1276, "lat": 51.5074},
                {"name": "edinburgh", "lon": -3.1883, "lat": 55.9533},
                {"name": "cardiff", "lon": -3.1791, "lat": 51.4816},
                {"name": "manchester", "lon": -2.2426, "lat": 53.4808},
                {"name": "birmingham", "lon": -1.8904, "lat": 52.4862},
            ],
            "desc": "ETRS89 to OSGB36 using OSTN15 grid"
        },
        # UK OSTN15 with explicit PROJ pipeline (ETRS89 to OSGB36)
        # Uses hgridshift with inverse to transform from ETRS89 to OSGB36
        {
            "name": "proj_pipeline_ostn15",
            "from_crs": "ETRS89_pipeline",  # Special marker for pipeline
            "to_crs": "OSGB36_pipeline",
            "pipeline": "+proj=pipeline +step +inv +proj=longlat +ellps=GRS80 +step +proj=hgridshift +grids=uk_os_OSTN15_NTv2_OSGBtoETRS.tif +inv +step +proj=longlat +ellps=airy",
            "grid_file": "uk_os_OSTN15_NTv2_OSGBtoETRS.tif",
            "test_points": [
                {"name": "london", "lon": -0.1276, "lat": 51.5074},
                {"name": "edinburgh", "lon": -3.1883, "lat": 55.9533},
                {"name": "cardiff", "lon": -3.1791, "lat": 51.4816},
                {"name": "manchester", "lon": -2.2426, "lat": 53.4808},
                {"name": "birmingham", "lon": -1.8904, "lat": 52.4862},
            ],
            "desc": "ETRS89 to OSGB36 using PROJ pipeline with hgridshift"
        },
        # UK OSTN15 with explicit +nadgrids (Forward: ETRS89 to OSGB36)
        # This test case uses explicit PROJ strings that proj4sedona can execute
        # Note: We use EPSG transformer for reference values since pyproj +nadgrids doesn't work for OSTN15
        {
            "name": "proj_nadgrids_ostn15_forward",
            "from_crs": "+proj=longlat +ellps=GRS80 +no_defs",  # ETRS89
            "to_crs": "+proj=longlat +ellps=airy +nadgrids=@uk_os_OSTN15_NTv2_OSGBtoETRS.tif +no_defs",  # OSGB36 with grid
            "grid_file": "uk_os_OSTN15_NTv2_OSGBtoETRS.tif",
            "test_points": [
                {"name": "london", "lon": -0.1276, "lat": 51.5074},
                {"name": "edinburgh", "lon": -3.1883, "lat": 55.9533},
                {"name": "cardiff", "lon": -3.1791, "lat": 51.4816},
                {"name": "manchester", "lon": -2.2426, "lat": 53.4808},
                {"name": "birmingham", "lon": -1.8904, "lat": 52.4862},
            ],
            "desc": "ETRS89 to OSGB36 using explicit +nadgrids (forward direction)",
            "reference_transformer": ("EPSG:4258", "EPSG:4277"),  # Use EPSG for reference
        },
        # UK OSTN15 with explicit +nadgrids (Inverse: OSGB36 to ETRS89)
        # This test case uses explicit PROJ strings that proj4sedona can execute
        # Note: We use EPSG transformer for reference values since pyproj +nadgrids doesn't work for OSTN15
        {
            "name": "proj_nadgrids_ostn15_inverse",
            "from_crs": "+proj=longlat +ellps=airy +nadgrids=@uk_os_OSTN15_NTv2_OSGBtoETRS.tif +no_defs",  # OSGB36 with grid
            "to_crs": "+proj=longlat +ellps=GRS80 +no_defs",  # ETRS89
            "grid_file": "uk_os_OSTN15_NTv2_OSGBtoETRS.tif",
            "test_points": [
                # Use OSGB36 coordinates (from EPSG:4258->EPSG:4277 forward transform)
                {"name": "london", "lon": -0.12601865501757062, "lat": 51.506888185279635},
                {"name": "edinburgh", "lon": -3.1868736941194284, "lat": 55.95336278929409},
                {"name": "cardiff", "lon": -3.177835328022739, "lat": 51.4811301825721},
                {"name": "manchester", "lon": -2.2411629558334205, "lat": 53.48053580324155},
                {"name": "birmingham", "lon": -1.8889628322226104, "lat": 52.48581568047493},
            ],
            "desc": "OSGB36 to ETRS89 using explicit +nadgrids (inverse direction)",
            "reference_transformer": ("EPSG:4277", "EPSG:4258"),  # Use EPSG for reference
        },
    ]


def transform_with_grid(from_crs_str: str, to_crs_str: str, 
                         test_points: List[Dict], 
                         verbose: bool = False,
                         pipeline: str = None,
                         reference_transformer: tuple = None) -> Dict[str, Any]:
    """Perform transformation and return results.
    
    Args:
        reference_transformer: Optional tuple of (from_crs, to_crs) EPSG codes to use
                              for computing reference values when the primary CRS
                              definition doesn't work properly in pyproj.
    """
    
    result = {
        "from_crs": from_crs_str,
        "to_crs": to_crs_str,
        "transformations": [],
        "transformer_info": {},
        "error": None
    }
    
    try:
        if pipeline:
            # Use explicit pipeline
            transformer = Transformer.from_pipeline(pipeline)
            result["transformer_info"] = {
                "pipeline": pipeline,
                "type": "explicit_pipeline"
            }
        elif reference_transformer:
            # Use EPSG-based transformer for reference values
            ref_from, ref_to = reference_transformer
            from_crs = CRS(ref_from)
            to_crs = CRS(ref_to)
            transformer = Transformer.from_crs(from_crs, to_crs, always_xy=True)
            result["transformer_info"] = {
                "reference_from": ref_from,
                "reference_to": ref_to,
                "type": "epsg_reference",
                "note": "Using EPSG transformer for reference values"
            }
        else:
            from_crs = CRS(from_crs_str)
            to_crs = CRS(to_crs_str)
            
            # Get transformer group to see available transformations
            tg = TransformerGroup(from_crs, to_crs, always_xy=True)
            
            result["transformer_info"] = {
                "num_transformers": len(tg.transformers),
                "best_accuracy": tg.best_available if hasattr(tg, 'best_available') else None,
            }
            
            # Use the best transformer
            transformer = Transformer.from_crs(from_crs, to_crs, always_xy=True)
        
        for point in test_points:
            try:
                x_out, y_out = transformer.transform(point["lon"], point["lat"])
                
                result["transformations"].append({
                    "point_name": point["name"],
                    "input": {"x": point["lon"], "y": point["lat"]},
                    "output": {"x": float(x_out), "y": float(y_out)},
                    "error": None
                })
            except Exception as e:
                result["transformations"].append({
                    "point_name": point["name"],
                    "input": {"x": point["lon"], "y": point["lat"]},
                    "output": None,
                    "error": str(e)
                })
                
    except Exception as e:
        result["error"] = str(e)
    
    return result


def generate_grid_reference(output_file: str, verbose: bool = False) -> None:
    """Generate grid transformation reference data."""
    
    reference_data = {
        "version": "1.0",
        "generator": "pyproj",
        "pyproj_version": None,
        "proj_data_dir": None,
        "notes": [
            "Grid files are fetched from cdn.proj.org by pyproj automatically",
            "Results may vary slightly based on pyproj/PROJ version and grid file version",
            "Tolerance for grid-based transforms should be ~1cm (0.01m)"
        ],
        "test_cases": []
    }
    
    # Get pyproj version and data directory
    import pyproj
    reference_data["pyproj_version"] = pyproj.__version__
    reference_data["proj_data_dir"] = pyproj.datadir.get_data_dir()
    
    # Enable network for grid downloads
    pyproj.network.set_network_enabled(True)
    
    for test_case in get_grid_test_cases():
        if verbose:
            print(f"  Processing: {test_case['name']}")
        
        transform_result = transform_with_grid(
            test_case["from_crs"],
            test_case["to_crs"],
            test_case["test_points"],
            verbose,
            pipeline=test_case.get("pipeline"),
            reference_transformer=test_case.get("reference_transformer")
        )
        
        case_data = {
            "name": test_case["name"],
            "description": test_case["desc"],
            "grid_file": test_case["grid_file"],
            "from_crs": test_case["from_crs"],
            "to_crs": test_case["to_crs"],
            "transform_result": transform_result,
        }
        
        # Add pipeline to output if present
        if "pipeline" in test_case:
            case_data["pipeline"] = test_case["pipeline"]
        
        reference_data["test_cases"].append(case_data)
    
    # Write output
    with open(output_file, 'w') as f:
        json.dump(reference_data, f, indent=2)


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", "-o", default="grid_transform_reference.json")
    parser.add_argument("--verbose", "-v", action="store_true")
    args = parser.parse_args()
    
    generate_grid_reference(args.output, args.verbose)
    print(f"Generated: {args.output}")
