#!/usr/bin/env python3
"""
Compare pyproj and proj4sedona benchmark results.

This script takes benchmark results from both pyproj and proj4sedona (Java JMH)
and generates a comparison report in Markdown format.
"""

import argparse
import json
from typing import Dict, Any, Optional
from datetime import datetime


def load_json(filepath: str) -> Optional[Dict[str, Any]]:
    """Load JSON file safely."""
    try:
        with open(filepath, 'r') as f:
            return json.load(f)
    except Exception as e:
        print(f"Warning: Could not load {filepath}: {e}")
        return None


def format_throughput(ops_per_sec: float) -> str:
    """Format throughput value for display."""
    if ops_per_sec >= 1_000_000:
        return f"{ops_per_sec / 1_000_000:.2f}M ops/sec"
    elif ops_per_sec >= 1_000:
        return f"{ops_per_sec / 1_000:.2f}K ops/sec"
    else:
        return f"{ops_per_sec:.2f} ops/sec"


def format_time(us: float) -> str:
    """Format time value for display."""
    if us >= 1000:
        return f"{us / 1000:.2f} ms"
    else:
        return f"{us:.2f} μs"


def compare_benchmark(pyproj_bench: Dict, java_bench: Dict) -> Dict[str, Any]:
    """Compare a single benchmark between pyproj and Java."""
    pyproj_mean = pyproj_bench.get("mean_us", 0)
    java_mean = java_bench.get("mean_us", 0)
    
    if pyproj_mean > 0 and java_mean > 0:
        speedup = pyproj_mean / java_mean
        if speedup >= 1:
            faster = "Java"
            speedup_str = f"{speedup:.2f}x"
        else:
            faster = "Python"
            speedup_str = f"{1/speedup:.2f}x"
    else:
        faster = "N/A"
        speedup_str = "N/A"
    
    return {
        "pyproj_mean_us": pyproj_mean,
        "java_mean_us": java_mean,
        "faster": faster,
        "speedup": speedup_str,
    }


def generate_report(pyproj_results: Dict, java_results: Dict) -> str:
    """Generate a Markdown comparison report."""
    
    lines = []
    lines.append("# Performance Comparison: proj4sedona vs pyproj")
    lines.append("")
    lines.append(f"Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    lines.append("")
    
    # Version info
    lines.append("## Environment")
    lines.append("")
    lines.append("| Library | Version |")
    lines.append("|---------|---------|")
    if pyproj_results:
        lines.append(f"| pyproj | {pyproj_results.get('pyproj_version', 'N/A')} |")
    if java_results:
        lines.append(f"| proj4sedona | {java_results.get('version', 'N/A')} |")
    lines.append("")
    
    # Benchmark comparison table
    lines.append("## Benchmark Results")
    lines.append("")
    lines.append("| Benchmark | pyproj (μs) | proj4sedona (μs) | Faster | Speedup |")
    lines.append("|-----------|-------------|------------------|--------|---------|")
    
    # Define benchmark mappings (pyproj name -> java name)
    benchmark_mappings = [
        ("CRS Init (EPSG:4326)", "crs_init_epsg_4326", "projInitWgs84"),
        ("CRS Init (PROJ string)", "crs_init_proj_string", "projInitWgs84"),
        ("CRS Init (UTM)", "crs_init_epsg_utm", "projInitUtm"),
        ("Transform Single (Mercator)", "transform_single_merc", "transformWgs84ToMerc"),
        ("Transform Single (UTM)", "transform_single_utm", "transformWgs84ToUtm"),
        ("Transform Batch 1000 (Mercator)", "transform_batch_1000_merc", "transformBatch1000Points"),
        ("Export to WKT1", "crs_export_wkt1", "exportToWkt1"),
        ("Export to PROJ string", "crs_export_proj", "exportToProjString"),
    ]
    
    pyproj_benchmarks = pyproj_results.get("benchmarks", {}) if pyproj_results else {}
    java_benchmarks = java_results.get("benchmarks", {}) if java_results else {}
    
    for name, pyproj_key, java_key in benchmark_mappings:
        pyproj_bench = pyproj_benchmarks.get(pyproj_key, {})
        java_bench = java_benchmarks.get(java_key, {})
        
        pyproj_mean = pyproj_bench.get("mean_us", 0)
        java_mean = java_bench.get("mean_us", 0)
        
        pyproj_str = format_time(pyproj_mean) if pyproj_mean > 0 else "N/A"
        java_str = format_time(java_mean) if java_mean > 0 else "N/A"
        
        if pyproj_mean > 0 and java_mean > 0:
            speedup = pyproj_mean / java_mean
            if speedup >= 1:
                faster = "Java ✓"
                speedup_str = f"{speedup:.2f}x"
            else:
                faster = "Python ✓"
                speedup_str = f"{1/speedup:.2f}x"
        else:
            faster = "N/A"
            speedup_str = "N/A"
        
        lines.append(f"| {name} | {pyproj_str} | {java_str} | {faster} | {speedup_str} |")
    
    lines.append("")
    
    # Throughput comparison
    lines.append("## Throughput Comparison")
    lines.append("")
    lines.append("| Operation | pyproj | proj4sedona |")
    lines.append("|-----------|--------|-------------|")
    
    throughput_mappings = [
        ("Single Transform", "transform_single_merc", "transformWgs84ToMerc"),
        ("Batch Transform (per point)", "transform_batch_per_point_merc", "transformBatchPerPoint"),
    ]
    
    for name, pyproj_key, java_key in throughput_mappings:
        pyproj_bench = pyproj_benchmarks.get(pyproj_key, {})
        java_bench = java_benchmarks.get(java_key, {})
        
        pyproj_throughput = pyproj_bench.get("throughput_ops_per_sec", 0)
        java_throughput = java_bench.get("throughput_ops_per_sec", 0)
        
        pyproj_str = format_throughput(pyproj_throughput) if pyproj_throughput > 0 else "N/A"
        java_str = format_throughput(java_throughput) if java_throughput > 0 else "N/A"
        
        lines.append(f"| {name} | {pyproj_str} | {java_str} |")
    
    lines.append("")
    
    # Notes
    lines.append("## Notes")
    lines.append("")
    lines.append("- Lower time values are better")
    lines.append("- Higher throughput values are better")
    lines.append("- Benchmarks run on the same hardware for fair comparison")
    lines.append("- Results may vary based on system load and configuration")
    lines.append("")
    
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(
        description="Compare pyproj and proj4sedona benchmark results"
    )
    parser.add_argument(
        "--pyproj", "-p",
        required=True,
        help="Path to pyproj benchmark results JSON"
    )
    parser.add_argument(
        "--java", "-j",
        required=True,
        help="Path to Java benchmark results JSON"
    )
    parser.add_argument(
        "--output", "-o",
        default="benchmark_comparison_report.md",
        help="Output file for comparison report"
    )
    
    args = parser.parse_args()
    
    print("Loading benchmark results...")
    pyproj_results = load_json(args.pyproj)
    java_results = load_json(args.java)
    
    if not pyproj_results and not java_results:
        print("Error: No benchmark results could be loaded")
        return 1
    
    print("Generating comparison report...")
    report = generate_report(pyproj_results, java_results)
    
    with open(args.output, 'w') as f:
        f.write(report)
    
    print(f"Report saved to: {args.output}")
    return 0


if __name__ == "__main__":
    exit(main())
