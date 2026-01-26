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
    
    # Pre-create reusable objects
    wgs84 = CRS("EPSG:4326")
    merc = CRS("EPSG:3857")
    utm32n = CRS("EPSG:32632")
    transformer_wgs84_merc = Transformer.from_crs(wgs84, merc, always_xy=True)
    transformer_wgs84_utm = Transformer.from_crs(wgs84, utm32n, always_xy=True)
    
    # Test coordinates
    lon, lat = -77.0369, 38.9072  # Washington DC
    
    # Batch data
    batch_lons = np.random.uniform(-180, 180, 1000)
    batch_lats = np.random.uniform(-80, 80, 1000)
    
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
    
    # Per-point throughput
    batch_merc_per_point = results["benchmarks"]["transform_batch_1000_merc"]["mean_us"] / 1000
    results["benchmarks"]["transform_batch_per_point_merc"] = {
        "mean_us": batch_merc_per_point,
        "throughput_ops_per_sec": 1_000_000 / batch_merc_per_point
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
