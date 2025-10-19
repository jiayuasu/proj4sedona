"""
Performance benchmark tests for Proj4Sedona vs pyproj.
Includes comprehensive tests for WKT2, datum shift, and batch transformations.
"""

import pytest
import time
import subprocess
import os
import sys
import psutil
import requests
import math
from typing import Dict, Any
from pathlib import Path


@pytest.mark.performance
@pytest.mark.local_cache
@pytest.mark.cdn_grid
class TestPerformanceBenchmarks:
    """Performance benchmark tests for Proj4Sedona vs pyproj."""
    
    def test_performance_benchmark(self, java_runner, python_runner, test_scenarios, benchmark_iterations):
        """Performance benchmark comparing Java and Python implementations."""
        print("\n🚀 Running performance benchmark...")
        
        java_results = {}
        python_results = {}
        
        for scenario in test_scenarios:
            # Run Java benchmark (silent)
            java_result = java_runner.run_benchmark(scenario, benchmark_iterations)
            assert java_result["returncode"] == 0, f"Java benchmark failed: {java_result['error']}"
            java_metrics = self._parse_java_output(java_result["output"])
            java_results[scenario['name']] = java_metrics
            
            # Run Python benchmark (silent)
            python_result = python_runner.run_benchmark(scenario, benchmark_iterations)
            assert python_result["returncode"] == 0, f"Python benchmark failed: {python_result['error']}"
            python_metrics = self._parse_python_output(python_result["output"])
            python_results[scenario['name']] = python_metrics
        
        # Print only the summary table
        self._print_summary_table(java_results, python_results)
    
    def _parse_java_output(self, output: str) -> Dict[str, Any]:
        """Parse Java benchmark output to extract metrics."""
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
    
    def _parse_python_output(self, output: str) -> Dict[str, Any]:
        """Parse Python benchmark output to extract metrics."""
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
    
    def _print_summary_table(self, java_results: Dict[str, Any], python_results: Dict[str, Any]):
        """Print a summary table of all benchmark results."""
        print("\n" + "="*100)
        print("📊 PERFORMANCE BENCHMARK SUMMARY TABLE")
        print("="*100)
        print(f"{'Scenario':<40} {'Java TPS':<12} {'Python TPS':<12} {'Speedup':<8} {'Winner':<8}")
        print("-"*80)
        
        for scenario_name in java_results.keys():
            java_metrics = java_results[scenario_name]
            python_metrics = python_results[scenario_name]
            
            speedup = java_metrics["tps"] / python_metrics["tps"] if python_metrics["tps"] > 0 else 0
            winner = "Java" if speedup > 1.0 else "Python"
            
            # Truncate scenario name if too long
            display_name = scenario_name[:37] + "..." if len(scenario_name) > 40 else scenario_name
            
            print(f"{display_name:<40} {java_metrics['tps']:>11,.0f} {python_metrics['tps']:>11,.0f} {speedup:>7.2f}x {winner:>7}")
        
        print("="*80)
        print("💡 Legend: Speedup > 1.0 = Java faster")
        print("="*80)
    
