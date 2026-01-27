#!/usr/bin/env python3
"""
Run pyproj performance benchmarks for comparison with proj4sedona.

This script runs a series of benchmarks measuring pyproj performance for:
- Projection initialization
- Single point transformation
- Batch transformation
- CRS parsing

Results are output as JSON for comparison with Java JMH benchmark results.
"""

import argparse
import json
import time
from typing import Dict, List, Any
import numpy as np
from pyproj import CRS, Transformer


def benchmark(func, iterations: int = 1000, warmup: int = 100) -> Dict[str, float]:
    """Run a benchmark and return timing statistics."""
    
    # Warmup
    for _ in range(warmup):
        func()
    
    # Benchmark
    times = []
    for _ in range(iterations):
        start = time.perf_counter_ns()
        func()
        end = time.perf_counter_ns()
        times.append(end - start)
    
    times_us = np.array(times) / 1000  # Convert to microseconds
    
    return {
        "iterations": iterations,
        "mean_us": float(np.mean(times_us)),
        "median_us": float(np.median(times_us)),
        "std_us": float(np.std(times_us)),
        "min_us": float(np.min(times_us)),
        "max_us": float(np.max(times_us)),
        "p50_us": float(np.percentile(times_us, 50)),
        "p90_us": float(np.percentile(times_us, 90)),
        "p99_us": float(np.percentile(times_us, 99)),
        "throughput_ops_per_sec": float(1_000_000 / np.mean(times_us))
    }


def run_benchmarks() -> Dict[str, Any]:
    """Run all benchmarks and return results."""
    
    results = {
        "version": "1.0",
        "generator": "pyproj",
        "pyproj_version": None,
        "benchmarks": {}
    }
    
    import pyproj
    results["pyproj_version"] = pyproj.__version__
    
    print("Running pyproj benchmarks...")
    print()
    
    # Enable network for grid downloads
    import pyproj
    pyproj.network.set_network_enabled(True)
    
    # Pre-fetch OSTN15 grid to avoid network latency during benchmarks
    print("Pre-fetching OSTN15 grid (this may take a moment)...")
    try:
        # Create transformer once to trigger grid download
        _prefetch = Transformer.from_crs("EPSG:4258", "EPSG:4277", always_xy=True)
        _prefetch.transform(-0.1276, 51.5074)  # Trigger actual grid load
        print("OSTN15 grid ready.")
    except Exception as e:
        print(f"Warning: Could not pre-fetch OSTN15 grid: {e}")
    print()
    
    # Pre-create reusable objects
    wgs84 = CRS("EPSG:4326")
    merc = CRS("EPSG:3857")
    utm32n = CRS("EPSG:32632")
    etrs89 = CRS("EPSG:4258")  # ETRS89 for OSTN15
    osgb36 = CRS("EPSG:4277")  # OSGB36 for OSTN15
    transformer_wgs84_merc = Transformer.from_crs(wgs84, merc, always_xy=True)
    transformer_wgs84_utm = Transformer.from_crs(wgs84, utm32n, always_xy=True)
    transformer_ostn15 = Transformer.from_crs(etrs89, osgb36, always_xy=True)
    transformer_ostn15_inverse = Transformer.from_crs(osgb36, etrs89, always_xy=True)
    
    # Test coordinates
    lon, lat = -77.0369, 38.9072  # Washington DC
    lon_gb, lat_gb = -0.1276, 51.5074  # London, GB (ETRS89)
    lon_gb_osgb, lat_gb_osgb = -0.12602, 51.50689  # London, GB (OSGB36)
    
    # Batch data
    batch_lons = np.random.uniform(-180, 180, 1000)
    batch_lats = np.random.uniform(-80, 80, 1000)
    
    # Batch data for GB (England area - dense grid coverage for reliable benchmarks)
    np.random.seed(42)  # Reproducibility
    # Use 100 points in England area where OSTN15 has dense coverage
    batch_lons_gb = np.random.uniform(-2.0, 0.5, 100)    # England longitude (ETRS89)
    batch_lats_gb = np.random.uniform(51.0, 53.0, 100)   # England latitude (ETRS89)
    
    # Pre-transform batch data for inverse benchmarks (OSGB36 coordinates)
    batch_lons_gb_osgb, batch_lats_gb_osgb = transformer_ostn15.transform(batch_lons_gb, batch_lats_gb)
    
    # === CRS Initialization Benchmarks ===
    
    print("1. CRS Initialization Benchmarks")
    
    # CRS from EPSG code
    print("   - CRS from EPSG:4326...", end=" ")
    results["benchmarks"]["crs_init_epsg_4326"] = benchmark(
        lambda: CRS("EPSG:4326"), iterations=1000
    )
    print(f"{results['benchmarks']['crs_init_epsg_4326']['mean_us']:.2f} us")
    
    # CRS from PROJ string
    print("   - CRS from PROJ string...", end=" ")
    results["benchmarks"]["crs_init_proj_string"] = benchmark(
        lambda: CRS("+proj=longlat +datum=WGS84 +no_defs"), iterations=1000
    )
    print(f"{results['benchmarks']['crs_init_proj_string']['mean_us']:.2f} us")
    
    # CRS from UTM EPSG
    print("   - CRS from EPSG:32632 (UTM)...", end=" ")
    results["benchmarks"]["crs_init_epsg_utm"] = benchmark(
        lambda: CRS("EPSG:32632"), iterations=1000
    )
    print(f"{results['benchmarks']['crs_init_epsg_utm']['mean_us']:.2f} us")
    
    print()
    
    # === Transformer Creation Benchmarks ===
    
    print("2. Transformer Creation Benchmarks")
    
    print("   - Transformer WGS84 -> Web Mercator...", end=" ")
    results["benchmarks"]["transformer_create_merc"] = benchmark(
        lambda: Transformer.from_crs(wgs84, merc, always_xy=True), iterations=500
    )
    print(f"{results['benchmarks']['transformer_create_merc']['mean_us']:.2f} us")
    
    print("   - Transformer WGS84 -> UTM...", end=" ")
    results["benchmarks"]["transformer_create_utm"] = benchmark(
        lambda: Transformer.from_crs(wgs84, utm32n, always_xy=True), iterations=500
    )
    print(f"{results['benchmarks']['transformer_create_utm']['mean_us']:.2f} us")
    
    print("   - Transformer ETRS89 -> OSGB36 (OSTN15)...", end=" ")
    # Use fewer iterations for OSTN15 as it involves grid operations
    results["benchmarks"]["transformer_create_ostn15"] = benchmark(
        lambda: Transformer.from_crs(etrs89, osgb36, always_xy=True), iterations=100, warmup=10
    )
    print(f"{results['benchmarks']['transformer_create_ostn15']['mean_us']:.2f} us")
    
    print("   - Transformer OSGB36 -> ETRS89 (OSTN15 inverse)...", end=" ")
    results["benchmarks"]["transformer_create_ostn15_inverse"] = benchmark(
        lambda: Transformer.from_crs(osgb36, etrs89, always_xy=True), iterations=100, warmup=10
    )
    print(f"{results['benchmarks']['transformer_create_ostn15_inverse']['mean_us']:.2f} us")
    
    print()
    
    # === Single Point Transformation Benchmarks ===
    
    print("3. Single Point Transformation Benchmarks")
    
    print("   - WGS84 -> Web Mercator...", end=" ")
    results["benchmarks"]["transform_single_merc"] = benchmark(
        lambda: transformer_wgs84_merc.transform(lon, lat), iterations=10000
    )
    print(f"{results['benchmarks']['transform_single_merc']['mean_us']:.2f} us")
    
    print("   - WGS84 -> UTM...", end=" ")
    results["benchmarks"]["transform_single_utm"] = benchmark(
        lambda: transformer_wgs84_utm.transform(lon, lat), iterations=10000
    )
    print(f"{results['benchmarks']['transform_single_utm']['mean_us']:.2f} us")
    
    print("   - ETRS89 -> OSGB36 (OSTN15)...", end=" ")
    results["benchmarks"]["transform_single_ostn15"] = benchmark(
        lambda: transformer_ostn15.transform(lon_gb, lat_gb), iterations=10000
    )
    print(f"{results['benchmarks']['transform_single_ostn15']['mean_us']:.2f} us")
    
    print("   - OSGB36 -> ETRS89 (OSTN15 inverse)...", end=" ")
    results["benchmarks"]["transform_single_ostn15_inverse"] = benchmark(
        lambda: transformer_ostn15_inverse.transform(lon_gb_osgb, lat_gb_osgb), iterations=10000
    )
    print(f"{results['benchmarks']['transform_single_ostn15_inverse']['mean_us']:.2f} us")
    
    print()
    
    # === Batch Transformation Benchmarks ===
    
    print("4. Batch Transformation Benchmarks (1000 points)")
    
    print("   - WGS84 -> Web Mercator...", end=" ")
    results["benchmarks"]["transform_batch_1000_merc"] = benchmark(
        lambda: transformer_wgs84_merc.transform(batch_lons, batch_lats), iterations=1000
    )
    print(f"{results['benchmarks']['transform_batch_1000_merc']['mean_us']:.2f} us")
    
    print("   - WGS84 -> UTM...", end=" ")
    results["benchmarks"]["transform_batch_1000_utm"] = benchmark(
        lambda: transformer_wgs84_utm.transform(batch_lons, batch_lats), iterations=1000
    )
    print(f"{results['benchmarks']['transform_batch_1000_utm']['mean_us']:.2f} us")
    
    print("   - ETRS89 -> OSGB36 (OSTN15, 100 GB points)...", end=" ")
    # Use fewer iterations and smaller batch for OSTN15 (grid interpolation is expensive)
    results["benchmarks"]["transform_batch_100_ostn15"] = benchmark(
        lambda: transformer_ostn15.transform(batch_lons_gb, batch_lats_gb), iterations=50, warmup=5
    )
    print(f"{results['benchmarks']['transform_batch_100_ostn15']['mean_us']:.2f} us")
    
    print("   - OSGB36 -> ETRS89 (OSTN15 inverse, 100 GB points)...", end=" ")
    results["benchmarks"]["transform_batch_100_ostn15_inverse"] = benchmark(
        lambda: transformer_ostn15_inverse.transform(batch_lons_gb_osgb, batch_lats_gb_osgb), iterations=50, warmup=5
    )
    print(f"{results['benchmarks']['transform_batch_100_ostn15_inverse']['mean_us']:.2f} us")
    
    # Per-point throughput
    batch_merc_per_point = results["benchmarks"]["transform_batch_1000_merc"]["mean_us"] / 1000
    results["benchmarks"]["transform_batch_per_point_merc"] = {
        "mean_us": batch_merc_per_point,
        "throughput_ops_per_sec": 1_000_000 / batch_merc_per_point
    }
    
    batch_ostn15_per_point = results["benchmarks"]["transform_batch_100_ostn15"]["mean_us"] / 100
    results["benchmarks"]["transform_batch_per_point_ostn15"] = {
        "mean_us": batch_ostn15_per_point,
        "throughput_ops_per_sec": 1_000_000 / batch_ostn15_per_point
    }
    
    batch_ostn15_inverse_per_point = results["benchmarks"]["transform_batch_100_ostn15_inverse"]["mean_us"] / 100
    results["benchmarks"]["transform_batch_per_point_ostn15_inverse"] = {
        "mean_us": batch_ostn15_inverse_per_point,
        "throughput_ops_per_sec": 1_000_000 / batch_ostn15_inverse_per_point
    }
    
    print()
    
    # === CRS Export Benchmarks ===
    
    print("5. CRS Export Benchmarks")
    
    print("   - Export to WKT1...", end=" ")
    results["benchmarks"]["crs_export_wkt1"] = benchmark(
        lambda: wgs84.to_wkt(version="WKT1_GDAL"), iterations=1000
    )
    print(f"{results['benchmarks']['crs_export_wkt1']['mean_us']:.2f} us")
    
    print("   - Export to WKT2...", end=" ")
    results["benchmarks"]["crs_export_wkt2"] = benchmark(
        lambda: wgs84.to_wkt(version="WKT2_2019"), iterations=1000
    )
    print(f"{results['benchmarks']['crs_export_wkt2']['mean_us']:.2f} us")
    
    print("   - Export to PROJ string...", end=" ")
    results["benchmarks"]["crs_export_proj"] = benchmark(
        lambda: wgs84.to_proj4(), iterations=1000
    )
    print(f"{results['benchmarks']['crs_export_proj']['mean_us']:.2f} us")
    
    print()
    print("Benchmarks complete!")
    
    return results


def main():
    parser = argparse.ArgumentParser(
        description="Run pyproj performance benchmarks"
    )
    parser.add_argument(
        "--output", "-o",
        default="pyproj_benchmark_results.json",
        help="Output file for benchmark results"
    )
    parser.add_argument(
        "--verbose", "-v",
        action="store_true",
        help="Verbose output"
    )
    
    args = parser.parse_args()
    
    results = run_benchmarks()
    
    with open(args.output, 'w') as f:
        json.dump(results, f, indent=2)
    
    print(f"\nResults saved to: {args.output}")


if __name__ == "__main__":
    main()
