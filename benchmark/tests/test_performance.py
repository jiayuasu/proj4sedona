"""
Comprehensive performance benchmark tests for Proj4Sedona vs pyproj.
Tests all CRS formats (EPSG, WKT2, PROJJSON) and batch transformations.
"""

import pytest
import time
import subprocess
import os
import sys
import psutil
import math
from typing import Dict, Any, List, Tuple
from conftest import map_scenario_to_crs_format


@pytest.mark.performance
class TestPerformanceBenchmarks:
    """Comprehensive performance benchmark tests for all CRS formats."""
    
    def test_epsg_performance_benchmark(self, java_runner, python_runner, test_scenarios, benchmark_iterations):
        """Performance benchmark using EPSG codes."""
        print("\n🚀 Running EPSG performance benchmark...")
        
        java_results = {}
        python_results = {}
        
        for scenario in test_scenarios:
            print(f"\n📊 Testing EPSG: {scenario['name']}")
            
            # Run Java benchmark
            java_result = java_runner.run_benchmark(scenario, benchmark_iterations, crs_format="epsg")
            assert java_result["returncode"] == 0, f"Java EPSG benchmark failed: {java_result['error']}"
            java_metrics = self._parse_benchmark_output(java_result["output"])
            java_results[scenario['name']] = java_metrics
            
            # Run Python benchmark
            python_result = python_runner.run_benchmark(scenario, benchmark_iterations, crs_format="epsg")
            assert python_result["returncode"] == 0, f"Python EPSG benchmark failed: {python_result['error']}"
            python_metrics = self._parse_benchmark_output(python_result["output"])
            python_results[scenario['name']] = python_metrics
        
        # Print summary table
        self._print_summary_table(java_results, python_results, "EPSG")
    
    def test_wkt2_performance_benchmark(self, java_runner, python_runner, test_scenarios, benchmark_iterations, wkt2_definitions):
        """Performance benchmark using WKT2 definitions."""
        print("\n🚀 Running WKT2 performance benchmark...")
        
        java_results = {}
        python_results = {}
        
        for scenario in test_scenarios:
            print(f"\n📊 Testing WKT2: {scenario['name']}")
            
            # Map scenario to WKT2 definitions
            wkt2_scenario = map_scenario_to_crs_format(scenario, "wkt2")
            
            # Run Java benchmark
            java_result = java_runner.run_benchmark(wkt2_scenario, benchmark_iterations, crs_format="wkt2", wkt2_defs=wkt2_definitions)
            assert java_result["returncode"] == 0, f"Java WKT2 benchmark failed: {java_result['error']}"
            java_metrics = self._parse_benchmark_output(java_result["output"])
            java_results[scenario['name']] = java_metrics
            
            # Run Python benchmark
            python_result = python_runner.run_benchmark(wkt2_scenario, benchmark_iterations, crs_format="wkt2", wkt2_defs=wkt2_definitions)
            assert python_result["returncode"] == 0, f"Python WKT2 benchmark failed: {python_result['error']}"
            python_metrics = self._parse_benchmark_output(python_result["output"])
            python_results[scenario['name']] = python_metrics
        
        # Print summary table
        self._print_summary_table(java_results, python_results, "WKT2")
    
    def test_projjson_performance_benchmark(self, java_runner, python_runner, test_scenarios, benchmark_iterations, projjson_definitions):
        """Performance benchmark using PROJJSON definitions."""
        print("\n🚀 Running PROJJSON performance benchmark...")
        
        java_results = {}
        python_results = {}
        
        for scenario in test_scenarios:
            print(f"\n📊 Testing PROJJSON: {scenario['name']}")
            
            # Map scenario to PROJJSON definitions
            projjson_scenario = map_scenario_to_crs_format(scenario, "projjson")
            
            # Run Java benchmark
            java_result = java_runner.run_benchmark(projjson_scenario, benchmark_iterations, crs_format="projjson", projjson_defs=projjson_definitions)
            assert java_result["returncode"] == 0, f"Java PROJJSON benchmark failed: {java_result['error']}"
            java_metrics = self._parse_benchmark_output(java_result["output"])
            java_results[scenario['name']] = java_metrics
            
            # Run Python benchmark
            python_result = python_runner.run_benchmark(projjson_scenario, benchmark_iterations, crs_format="projjson", projjson_defs=projjson_definitions)
            assert python_result["returncode"] == 0, f"Python PROJJSON benchmark failed: {python_result['error']}"
            python_metrics = self._parse_benchmark_output(python_result["output"])
            python_results[scenario['name']] = python_metrics
        
        # Print summary table
        self._print_summary_table(java_results, python_results, "PROJJSON")
    
    def test_batch_performance_benchmark(self, batch_java_runner, batch_python_runner, batch_test_scenarios, wkt2_definitions, projjson_definitions, benchmark_iterations):
        """Batch transformation performance benchmark."""
        print("\n🚀 Running batch performance benchmark...")
        
        for scenario in batch_test_scenarios:
            print(f"\n📊 Testing batch: {scenario['name']}")
            
            # Collect results for table
            batch_results = []
            
            for batch_size in scenario['batch_sizes']:
                results_row = {"batch_size": batch_size}
                
                # Test EPSG
                epsg_java_tps, epsg_python_tps = self._run_batch_format_test(batch_java_runner, batch_python_runner, scenario, batch_size, "epsg", benchmark_iterations=benchmark_iterations)
                results_row["epsg_java"] = epsg_java_tps
                results_row["epsg_python"] = epsg_python_tps
                
                # Test WKT2
                wkt2_scenario = map_scenario_to_crs_format(scenario, "wkt2")
                wkt2_java_tps, wkt2_python_tps = self._run_batch_format_test(batch_java_runner, batch_python_runner, wkt2_scenario, batch_size, "wkt2", wkt2_definitions, benchmark_iterations=benchmark_iterations)
                results_row["wkt2_java"] = wkt2_java_tps
                results_row["wkt2_python"] = wkt2_python_tps
                
                # Test PROJJSON
                projjson_scenario = map_scenario_to_crs_format(scenario, "projjson")
                projjson_java_tps, projjson_python_tps = self._run_batch_format_test(batch_java_runner, batch_python_runner, projjson_scenario, batch_size, "projjson", None, projjson_definitions, benchmark_iterations=benchmark_iterations)
                results_row["projjson_java"] = projjson_java_tps
                results_row["projjson_python"] = projjson_python_tps
                
                batch_results.append(results_row)
            
            # Print summary table for this scenario
            self._print_batch_summary_table(scenario['name'], batch_results)
    
    def _run_batch_format_test(self, java_runner, python_runner, scenario, batch_size, crs_format, wkt2_defs=None, projjson_defs=None, benchmark_iterations=10000):
        """Run batch test for a specific CRS format."""
        iterations = max(100, benchmark_iterations // batch_size)  # Adjust iterations based on batch size
        
        # Run Java batch benchmark
        java_result = java_runner.run_batch_benchmark(scenario, batch_size, iterations, crs_format, wkt2_defs, projjson_defs)
        assert java_result["returncode"] == 0, f"Java batch {crs_format} benchmark failed: {java_result['error']}"
        java_metrics = self._parse_benchmark_output(java_result["output"])
        
        # Run Python batch benchmark
        python_result = python_runner.run_batch_benchmark(scenario, batch_size, iterations, crs_format, wkt2_defs, projjson_defs)
        assert python_result["returncode"] == 0, f"Python batch {crs_format} benchmark failed: {python_result['error']}"
        python_metrics = self._parse_benchmark_output(python_result["output"])
        
        # Get TPS values with error handling
        java_tps = java_metrics.get("tps", 0)
        python_tps = python_metrics.get("tps", 0)
        
        return java_tps, python_tps
    
    def _parse_benchmark_output(self, output: str) -> Dict[str, Any]:
        """Parse benchmark output to extract metrics (works for Java, Python, and batch)."""
        lines = output.split('\n')
        metrics = {}
        
        for line in lines:
            if "Average per transformation:" in line:
                metrics["avg_time_ms"] = float(line.split(":")[1].strip().replace(" ms", ""))
            elif "Transformations per second:" in line:
                metrics["tps"] = float(line.split(":")[1].strip())
            elif "Success rate:" in line:
                metrics["success_rate"] = float(line.split(":")[1].strip().replace("%", ""))
            elif "Total time:" in line:
                metrics["total_time_ms"] = float(line.split(":")[1].strip().replace(" ms", ""))
        
        return metrics
    
    def _print_batch_summary_table(self, scenario_name: str, batch_results: List[Dict[str, Any]]):
        """Print a summary table for batch performance results."""
        print(f"\n{'='*180}")
        print(f"📊 BATCH PERFORMANCE SUMMARY - {scenario_name}")
        print(f"{'='*180}")
        print(f"| {'Batch Size':^12} | {'EPSG Java':>15} | {'EPSG Python':>15} | {'EPSG Speedup':>13} | {'WKT2 Java':>15} | {'WKT2 Python':>15} | {'WKT2 Speedup':>13} | {'PROJJSON Java':>15} | {'PROJJSON Python':>15} | {'PROJJSON Speedup':>16} |")
        print(f"|{'-'*14}|{'-'*17}|{'-'*17}|{'-'*15}|{'-'*17}|{'-'*17}|{'-'*15}|{'-'*17}|{'-'*19}|{'-'*18}|")
        
        for row in batch_results:
            batch_size = row["batch_size"]
            
            # Helper to ensure numeric values
            # Returns 0 for conversion failures, which is acceptable for missing/invalid 
            # throughput values as they indicate no performance data was collected
            def to_float(val):
                try:
                    return float(val) if val else 0
                except (TypeError, ValueError):
                    return 0
            
            # EPSG
            epsg_java = to_float(row["epsg_java"])
            epsg_python = to_float(row["epsg_python"])
            epsg_speedup = epsg_java / epsg_python if epsg_python > 0 else 0
            epsg_speedup_str = f"{epsg_speedup:.2f}x"
            
            # WKT2
            wkt2_java = to_float(row["wkt2_java"])
            wkt2_python = to_float(row["wkt2_python"])
            wkt2_speedup = wkt2_java / wkt2_python if wkt2_python > 0 else 0
            wkt2_speedup_str = f"{wkt2_speedup:.2f}x"
            
            # PROJJSON
            projjson_java = to_float(row["projjson_java"])
            projjson_python = to_float(row["projjson_python"])
            projjson_speedup = projjson_java / projjson_python if projjson_python > 0 else 0
            projjson_speedup_str = f"{projjson_speedup:.2f}x"
            
            print(f"| {batch_size:>12,} | {epsg_java:>15,.0f} | {epsg_python:>15,.0f} | {epsg_speedup_str:>13} | "
                  f"{wkt2_java:>15,.0f} | {wkt2_python:>15,.0f} | {wkt2_speedup_str:>13} | "
                  f"{projjson_java:>15,.0f} | {projjson_python:>15,.0f} | {projjson_speedup_str:>16} |")
        
        print(f"{'='*180}")
        print("💡 Legend: Speedup > 1.0 = Java faster, Speedup < 1.0 = Python faster")
        print(f"{'='*180}")
    
    def _print_summary_table(self, java_results: Dict[str, Any], python_results: Dict[str, Any], crs_format: str):
        """Print a summary table of all benchmark results."""
        print(f"\n{'='*140}")
        print(f"📊 {crs_format} PERFORMANCE BENCHMARK SUMMARY TABLE")
        print(f"{'='*140}")
        print(f"| {'Scenario':<45} | {'Java TPS':>15} | {'Python TPS':>15} | {'Speedup':>12} | {'Winner':^10} | {'Status':^10} |")
        print(f"|{'-'*47}|{'-'*17}|{'-'*17}|{'-'*14}|{'-'*12}|{'-'*12}|")
        
        failed_tests = []
        
        for scenario_name in java_results.keys():
            java_metrics = java_results[scenario_name]
            python_metrics = python_results[scenario_name]
            
            # Check if metrics are valid
            java_tps = java_metrics.get("tps", 0)
            python_tps = python_metrics.get("tps", 0)
            
            if java_tps == 0 or python_tps == 0:
                failed_tests.append(scenario_name)
                status = "❌ FAIL"
                speedup = 0
                winner = "ERROR"
            else:
                speedup = java_tps / python_tps if python_tps > 0 else 0
                winner = "Java" if speedup > 1.0 else "Python"
                status = "✅ PASS"
            
            # Truncate scenario name if too long
            display_name = scenario_name[:42] + "..." if len(scenario_name) > 45 else scenario_name
            
            print(f"| {display_name:<45} | {java_tps:>15,.0f} | {python_tps:>15,.0f} | {speedup:>11.2f}x | {winner:^10} | {status:^10} |")
        
        print(f"{'='*140}")
        print("💡 Legend: Speedup > 1.0 = Java faster")
        
        if failed_tests:
            print(f"\n❌ FAILED TESTS:")
            for test in failed_tests:
                print(f"  - {test}")
        else:
            print(f"\n✅ ALL TESTS PASSED")
        
        print(f"{'='*140}")

