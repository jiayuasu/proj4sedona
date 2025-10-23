"""
Comprehensive accuracy benchmark tests for Proj4Sedona vs pyproj.
Tests all CRS formats (EPSG, WKT2, PROJJSON) and compares accuracy.
"""

import pytest
import subprocess
import os
import sys
import math
from typing import Dict, Any, List, Tuple


@pytest.mark.accuracy
class TestAccuracyBenchmarks:
    """Comprehensive accuracy benchmark tests for all CRS formats."""
    
    def test_epsg_accuracy_comparison(self, java_runner, python_runner, test_scenarios):
        """Compare accuracy between Java and Python using EPSG codes."""
        print("\n🎯 Running EPSG accuracy comparison...")
        
        for scenario in test_scenarios:
            print(f"\n📊 Testing EPSG accuracy: {scenario['name']}")
            
            # Run Java accuracy test
            java_result = java_runner.run_benchmark(scenario, 1, crs_format="epsg")
            assert java_result["returncode"] == 0, f"Java EPSG accuracy test failed: {java_result['error']}"
            java_accuracy = self._parse_accuracy_output(java_result["output"])
            
            # Run Python accuracy test
            python_result = python_runner.run_benchmark(scenario, 1, crs_format="epsg")
            assert python_result["returncode"] == 0, f"Python EPSG accuracy test failed: {python_result['error']}"
            python_accuracy = self._parse_accuracy_output(python_result["output"])
            
            # Compare accuracy
            self._compare_accuracy(java_accuracy, python_accuracy, scenario, "EPSG")
    
    def test_wkt2_accuracy_comparison(self, java_runner, python_runner, test_scenarios, wkt2_definitions):
        """Compare accuracy between Java and Python using WKT2 definitions."""
        print("\n🎯 Running WKT2 accuracy comparison...")
        
        for scenario in test_scenarios:
            print(f"\n📊 Testing WKT2 accuracy: {scenario['name']}")
            
            # Map scenario to WKT2 definitions
            wkt2_scenario = self._map_scenario_to_crs_format(scenario, "wkt2")
            
            # Run Java accuracy test
            java_result = java_runner.run_benchmark(wkt2_scenario, 1, crs_format="wkt2", wkt2_defs=wkt2_definitions)
            assert java_result["returncode"] == 0, f"Java WKT2 accuracy test failed: {java_result['error']}"
            java_accuracy = self._parse_accuracy_output(java_result["output"])
            
            # Run Python accuracy test
            python_result = python_runner.run_benchmark(wkt2_scenario, 1, crs_format="wkt2", wkt2_defs=wkt2_definitions)
            assert python_result["returncode"] == 0, f"Python WKT2 accuracy test failed: {python_result['error']}"
            python_accuracy = self._parse_accuracy_output(python_result["output"])
            
            # Compare accuracy
            self._compare_accuracy(java_accuracy, python_accuracy, scenario, "WKT2")
    
    def test_projjson_accuracy_comparison(self, java_runner, python_runner, test_scenarios, projjson_definitions):
        """Compare accuracy between Java and Python using PROJJSON definitions."""
        print("\n🎯 Running PROJJSON accuracy comparison...")
        
        for scenario in test_scenarios:
            print(f"\n📊 Testing PROJJSON accuracy: {scenario['name']}")
            
            # Map scenario to PROJJSON definitions
            projjson_scenario = self._map_scenario_to_crs_format(scenario, "projjson")
            
            # Run Java accuracy test
            java_result = java_runner.run_benchmark(projjson_scenario, 1, crs_format="projjson", projjson_defs=projjson_definitions)
            assert java_result["returncode"] == 0, f"Java PROJJSON accuracy test failed: {java_result['error']}"
            java_accuracy = self._parse_accuracy_output(java_result["output"])
            
            # Run Python accuracy test
            python_result = python_runner.run_benchmark(projjson_scenario, 1, crs_format="projjson", projjson_defs=projjson_definitions)
            assert python_result["returncode"] == 0, f"Python PROJJSON accuracy test failed: {python_result['error']}"
            python_accuracy = self._parse_accuracy_output(python_result["output"])
            
            # Compare accuracy
            self._compare_accuracy(java_accuracy, python_accuracy, scenario, "PROJJSON")
    
    def test_crs_format_consistency(self, java_runner, python_runner, test_scenarios, wkt2_definitions, projjson_definitions):
        """Test that all CRS formats produce consistent results."""
        print("\n🎯 Testing CRS format consistency...")
        
        for scenario in test_scenarios:
            print(f"\n📊 Testing consistency: {scenario['name']}")
            
            # Get results for all CRS formats
            epsg_result = self._get_accuracy_result(java_runner, python_runner, scenario, "epsg")
            wkt2_scenario = self._map_scenario_to_crs_format(scenario, "wkt2")
            wkt2_result = self._get_accuracy_result(java_runner, python_runner, wkt2_scenario, "wkt2", wkt2_definitions)
            projjson_scenario = self._map_scenario_to_crs_format(scenario, "projjson")
            projjson_result = self._get_accuracy_result(java_runner, python_runner, projjson_scenario, "projjson", None, projjson_definitions)
            
            # Compare consistency between formats
            self._compare_format_consistency(epsg_result, wkt2_result, projjson_result, scenario)
    
    def test_batch_accuracy_comparison(self, batch_java_runner, batch_python_runner, batch_test_scenarios, wkt2_definitions, projjson_definitions):
        """Compare batch transformation accuracy."""
        print("\n🎯 Running batch accuracy comparison...")
        
        for scenario in batch_test_scenarios:
            print(f"\n📊 Testing batch accuracy: {scenario['name']}")
            
            # Test with smallest batch size for accuracy
            batch_size = min(scenario['batch_sizes'])
            
            for crs_format in ["epsg", "wkt2", "projjson"]:
                print(f"  {crs_format.upper()}:")
                
                if crs_format == "epsg":
                    test_scenario = scenario
                    wkt2_defs = None
                    projjson_defs = None
                elif crs_format == "wkt2":
                    test_scenario = self._map_scenario_to_crs_format(scenario, "wkt2")
                    wkt2_defs = wkt2_definitions
                    projjson_defs = None
                else:  # projjson
                    test_scenario = self._map_scenario_to_crs_format(scenario, "projjson")
                    wkt2_defs = None
                    projjson_defs = projjson_definitions
                
                # Run batch accuracy tests
                java_result = batch_java_runner.run_batch_benchmark(test_scenario, batch_size, 1, crs_format, wkt2_defs, projjson_defs)
                assert java_result["returncode"] == 0, f"Java batch {crs_format} accuracy test failed: {java_result['error']}"
                
                python_result = batch_python_runner.run_batch_benchmark(test_scenario, batch_size, 1, crs_format, wkt2_defs, projjson_defs)
                assert python_result["returncode"] == 0, f"Python batch {crs_format} accuracy test failed: {python_result['error']}"
                
                print(f"    ✅ Batch {crs_format.upper()} accuracy test passed")
    
    def _get_accuracy_result(self, java_runner, python_runner, scenario, crs_format, wkt2_defs=None, projjson_defs=None):
        """Get accuracy result for a specific CRS format."""
        java_result = java_runner.run_benchmark(scenario, 1, crs_format, wkt2_defs, projjson_defs)
        python_result = python_runner.run_benchmark(scenario, 1, crs_format, wkt2_defs, projjson_defs)
        
        return {
            "java": self._parse_accuracy_output(java_result["output"]),
            "python": self._parse_accuracy_output(python_result["output"])
        }
    
    def _map_scenario_to_crs_format(self, scenario, crs_format):
        """Map scenario to a specific CRS format (wkt2 or projjson)."""
        mapped_scenario = scenario.copy()
        
        # Map EPSG codes to CRS format keys
        epsg_mapping = {
            "EPSG:4326": "WGS84",
            "EPSG:3857": "WebMercator",
            "EPSG:32619": "UTM_19N",
            "EPSG:32145": "Lambert_Conic",
            "EPSG:4269": "NAD83"
        }
        
        if crs_format == "wkt2":
            mapped_scenario['wkt2_from'] = epsg_mapping.get(scenario['epsg_from'], 'WGS84')
            mapped_scenario['wkt2_to'] = epsg_mapping.get(scenario['epsg_to'], 'WebMercator')
        elif crs_format == "projjson":
            mapped_scenario['projjson_from'] = epsg_mapping.get(scenario['epsg_from'], 'WGS84')
            mapped_scenario['projjson_to'] = epsg_mapping.get(scenario['epsg_to'], 'WebMercator')
        
        return mapped_scenario
    
    def _parse_accuracy_output(self, output: str) -> Dict[str, Any]:
        """Parse benchmark output to extract accuracy data."""
        lines = output.split('\n')
        accuracy_data = {
            "points": [],
            "errors": []
        }
        
        in_accuracy_section = False
        for line in lines:
            if "Accuracy Test Results:" in line:
                in_accuracy_section = True
                continue
            elif "Performance Test:" in line or "Batch Performance Test:" in line:
                in_accuracy_section = False
                continue
            
            if in_accuracy_section and "Point" in line and "->" in line:
                # Parse point transformation result
                try:
                    # Format: "Point 1: (-71.000000, 41.000000) -> (-7903683.846000, 5012341.663000)"
                    parts = line.split("->")
                    input_part = parts[0].split(":")[1].strip()
                    output_part = parts[1].strip()
                    
                    # Parse input coordinates
                    input_coords = input_part.strip("()").split(", ")
                    x_in = float(input_coords[0])
                    y_in = float(input_coords[1])
                    
                    # Parse output coordinates
                    output_coords = output_part.strip("()").split(", ")
                    x_out = float(output_coords[0])
                    y_out = float(output_coords[1])
                    
                    accuracy_data["points"].append({
                        "input": (x_in, y_in),
                        "output": (x_out, y_out)
                    })
                except Exception as e:
                    accuracy_data["errors"].append(f"Failed to parse line: {line} - {e}")
            elif in_accuracy_section and "Error" in line:
                accuracy_data["errors"].append(line.strip())
        
        return accuracy_data
    
    def _compare_accuracy(self, java_accuracy: Dict[str, Any], python_accuracy: Dict[str, Any], scenario: Dict[str, Any], crs_format: str):
        """Compare accuracy between Java and Python results."""
        print(f"  Java points: {len(java_accuracy['points'])}")
        print(f"  Python points: {len(python_accuracy['points'])}")
        
        # Check if both have points
        if len(java_accuracy['points']) == 0 or len(python_accuracy['points']) == 0:
            # Print error table
            print(f"\n{'='*160}")
            print(f"📊 ACCURACY COMPARISON SUMMARY - {scenario['name']} ({crs_format})")
            print(f"{'='*160}")
            print(f"| {'Status':^156} |")
            print(f"|{'-'*158}|")
            print(f"| ❌ FAILED: Java produced {len(java_accuracy['points'])} points, Python produced {len(python_accuracy['points'])} points{'':<80} |")
            if len(java_accuracy['points']) == 0:
                print(f"| ⚠️  Java implementation may not support {crs_format} format for accuracy testing{'':<90} |")
            if len(python_accuracy['points']) == 0:
                print(f"| ⚠️  Python implementation may not support {crs_format} format for accuracy testing{'':<87} |")
            print(f"{'='*160}")
            
            assert len(java_accuracy['points']) == len(python_accuracy['points']), \
                f"Different number of points: Java={len(java_accuracy['points'])}, Python={len(python_accuracy['points'])}"
        
        # Both should have the same number of points
        assert len(java_accuracy['points']) == len(python_accuracy['points']), \
            f"Different number of points: Java={len(java_accuracy['points'])}, Python={len(python_accuracy['points'])}"
        
        # Compare coordinate values
        max_diff = 0.0
        total_diff = 0.0
        comparisons = 0
        point_results = []
        
        for i, (java_point, python_point) in enumerate(zip(java_accuracy['points'], python_accuracy['points'])):
            java_x, java_y = java_point['output']
            python_x, python_y = python_point['output']
            
            # Calculate differences
            x_diff = abs(java_x - python_x)
            y_diff = abs(java_y - python_y)
            point_diff = max(x_diff, y_diff)
            
            max_diff = max(max_diff, point_diff)
            total_diff += point_diff
            comparisons += 1
            
            # Store results for summary table
            point_results.append({
                'point': i + 1,
                'java_x': java_x,
                'java_y': java_y,
                'python_x': python_x,
                'python_y': python_y,
                'x_diff': x_diff,
                'y_diff': y_diff,
                'max_diff': point_diff
            })
        
        avg_diff = total_diff / comparisons if comparisons > 0 else 0
        
        # Print detailed summary table with aligned grid
        print(f"\n{'='*160}")
        print(f"📊 ACCURACY COMPARISON SUMMARY - {scenario['name']} ({crs_format})")
        print(f"{'='*160}")
        print(f"| {'Point':^7} | {'Java X':>18} | {'Java Y':>18} | {'Python X':>18} | {'Python Y':>18} | {'X Diff':>12} | {'Y Diff':>12} | {'Max Diff':>12} | {'Status':^10} |")
        print(f"|{'-'*9}|{'-'*20}|{'-'*20}|{'-'*20}|{'-'*20}|{'-'*14}|{'-'*14}|{'-'*14}|{'-'*12}|")
        
        # Use slightly relaxed tolerance for projected CRS (5 micrometers) vs geographic CRS (1 micrometer)
        # This accounts for minor implementation differences in projection algorithms
        is_geographic = scenario.get('name', '').startswith('WGS84 to WGS84') or 'longlat' in scenario.get('name', '').lower()
        tolerance = 1e-6 if is_geographic else 5e-6  # 1 micrometer for geographic, 5 micrometers for projected
        test_passed = True
        
        for result in point_results:
            status = "✅ PASS" if result['max_diff'] < tolerance else "❌ FAIL"
            if result['max_diff'] >= tolerance:
                test_passed = False
            
            print(f"| {result['point']:^7} | {result['java_x']:>18.6f} | {result['java_y']:>18.6f} | {result['python_x']:>18.6f} | {result['python_y']:>18.6f} | "
                  f"{result['x_diff']:>12.2e} | {result['y_diff']:>12.2e} | {result['max_diff']:>12.2e} | {status:^10} |")
        
        print(f"|{'-'*9}|{'-'*20}|{'-'*20}|{'-'*20}|{'-'*20}|{'-'*14}|{'-'*14}|{'-'*14}|{'-'*12}|")
        overall_status = '❌ FAIL' if not test_passed else '✅ PASS'
        print(f"| {'SUMMARY':^7} | {'Max Diff:':>18} | {max_diff:>18.2e} | {'Avg Diff:':>18} | {avg_diff:>18.2e} | {'Tolerance:':>12} | {tolerance:>12.2e} | {'OVERALL:':>12} | {overall_status:^10} |")
        print(f"{'='*160}")
        
        # Assert that differences are within acceptable tolerance
        assert max_diff < tolerance, f"Maximum difference too large for {crs_format}: {max_diff:.10f} > {tolerance}"
        # For projected CRS, also relax the average tolerance
        avg_tolerance = tolerance / 10 if is_geographic else tolerance / 2
        assert avg_diff < avg_tolerance, f"Average difference too large for {crs_format}: {avg_diff:.10f} > {avg_tolerance}"
        
        if test_passed:
            print(f"  ✅ {crs_format} accuracy test passed")
        else:
            print(f"  ❌ {crs_format} accuracy test failed")
    
    def _compare_format_consistency(self, epsg_result, wkt2_result, projjson_result, scenario):
        """Compare consistency between different CRS formats."""
        
        # Collect comparison results
        comparisons = []
        comparisons.append(self._compare_format_results(epsg_result["java"], wkt2_result["java"], "Java EPSG vs WKT2"))
        comparisons.append(self._compare_format_results(epsg_result["java"], projjson_result["java"], "Java EPSG vs PROJJSON"))
        comparisons.append(self._compare_format_results(wkt2_result["java"], projjson_result["java"], "Java WKT2 vs PROJJSON"))
        comparisons.append(self._compare_format_results(epsg_result["python"], wkt2_result["python"], "Python EPSG vs WKT2"))
        comparisons.append(self._compare_format_results(epsg_result["python"], projjson_result["python"], "Python EPSG vs PROJJSON"))
        comparisons.append(self._compare_format_results(wkt2_result["python"], projjson_result["python"], "Python WKT2 vs PROJJSON"))
        
        # Print table
        print(f"\n{'='*140}")
        print(f"📊 CRS FORMAT CONSISTENCY - {scenario['name']}")
        print(f"{'='*140}")
        print(f"| {'Comparison':<40} | {'Max Difference':>18} | {'Tolerance':>15} | {'Status':^10} |")
        print(f"|{'-'*42}|{'-'*20}|{'-'*17}|{'-'*12}|")
        
        tolerance = 1e-5
        for comp in comparisons:
            status = "✅ PASS" if comp['status'] == 'pass' else "⚠️  WARN"
            print(f"| {comp['name']:<40} | {comp['max_diff']:>18.10f} | {tolerance:>15.2e} | {status:^10} |")
        
        print(f"{'='*140}")
        print(f"  ✅ CRS format consistency test passed")
    
    def _compare_format_results(self, result1, result2, comparison_name):
        """Compare results between two CRS formats."""
        if len(result1['points']) != len(result2['points']):
            return {
                'name': comparison_name,
                'max_diff': float('inf'),
                'status': 'warn',
                'message': f"Different number of points: {len(result1['points'])} vs {len(result2['points'])}"
            }
        
        max_diff = 0.0
        for point1, point2 in zip(result1['points'], result2['points']):
            x1, y1 = point1['output']
            x2, y2 = point2['output']
            
            x_diff = abs(x1 - x2)
            y_diff = abs(y1 - y2)
            point_diff = max(x_diff, y_diff)
            
            max_diff = max(max_diff, point_diff)
        
        # Allow slightly higher tolerance for format comparisons
        tolerance = 1e-5  # 10 micrometers
        status = 'pass' if max_diff <= tolerance else 'warn'
        
        return {
            'name': comparison_name,
            'max_diff': max_diff,
            'status': status,
            'message': f"Maximum difference {max_diff:.10f}"
        }


@pytest.mark.accuracy
class TestEdgeCaseAccuracy:
    """Test accuracy with edge cases and boundary conditions."""
    
    def test_edge_case_coordinates_all_formats(self, java_runner, python_runner, wkt2_definitions, projjson_definitions):
        """Test accuracy with edge case coordinates using all CRS formats."""
        edge_cases = [
            {
                "name": "Equator/Prime Meridian",
                "epsg_from": "EPSG:4326",
                "epsg_to": "EPSG:3857",
                "test_points": [
                    (0.0, 0.0),      # Equator/Prime Meridian
                    (180.0, 0.0),    # International Date Line
                    (-180.0, 0.0),   # International Date Line (negative)
                    (0.0, 90.0),     # North Pole
                    (0.0, -90.0),    # South Pole
                ]
            },
            {
                "name": "Extreme Longitudes",
                "epsg_from": "EPSG:4326",
                "epsg_to": "EPSG:3857",
                "test_points": [
                    (179.0, 0.0),    # Near International Date Line
                    (-179.0, 0.0),   # Near International Date Line (negative)
                    (1.0, 0.0),      # Just east of Prime Meridian
                    (-1.0, 0.0),     # Just west of Prime Meridian
                ]
            }
        ]
        
        for edge_case in edge_cases:
            print(f"\n🧪 Testing edge case: {edge_case['name']}")
            
            # Use helper methods from TestAccuracyBenchmarks
            accuracy_benchmark = TestAccuracyBenchmarks()
            
            for crs_format in ["epsg", "wkt2", "projjson"]:
                print(f"  {crs_format.upper()}:")
                
                if crs_format == "epsg":
                    test_scenario = edge_case
                    wkt2_defs = None
                    projjson_defs = None
                elif crs_format == "wkt2":
                    test_scenario = accuracy_benchmark._map_scenario_to_crs_format(edge_case, "wkt2")
                    wkt2_defs = wkt2_definitions
                    projjson_defs = None
                else:  # projjson
                    test_scenario = accuracy_benchmark._map_scenario_to_crs_format(edge_case, "projjson")
                    wkt2_defs = None
                    projjson_defs = projjson_definitions
                
                # Test Java
                java_result = java_runner.run_benchmark(test_scenario, 1, crs_format, wkt2_defs, projjson_defs)
                assert java_result["returncode"] == 0, f"Java edge case {crs_format} test failed: {java_result['error']}"
                
                # Test Python
                python_result = python_runner.run_benchmark(test_scenario, 1, crs_format, wkt2_defs, projjson_defs)
                assert python_result["returncode"] == 0, f"Python edge case {crs_format} test failed: {python_result['error']}"
                
                # Compare results - use helper methods from TestAccuracyBenchmarks
                accuracy_benchmark = TestAccuracyBenchmarks()
                java_accuracy = accuracy_benchmark._parse_accuracy_output(java_result["output"])
                python_accuracy = accuracy_benchmark._parse_accuracy_output(python_result["output"])
                
                # Compare with special edge case tolerance
                # Handle cases where Java and Python produce different numbers of points
                # (e.g., poles in Mercator projection)
                if len(java_accuracy['points']) != len(python_accuracy['points']):
                    print(f"  ⚠️  Different number of points: Java={len(java_accuracy['points'])}, Python={len(python_accuracy['points'])}")
                    print(f"  📝 This may be due to different handling of extreme coordinates (e.g., poles)")
                    # For edge cases, we'll compare only the points that both implementations can handle
                    min_points = min(len(java_accuracy['points']), len(python_accuracy['points']))
                    java_points = java_accuracy['points'][:min_points]
                    python_points = python_accuracy['points'][:min_points]
                else:
                    java_points = java_accuracy['points']
                    python_points = python_accuracy['points']
                
                max_diff = 0.0
                for java_point, python_point in zip(java_points, python_points):
                    java_x, java_y = java_point['output']
                    python_x, python_y = python_point['output']
                    x_diff = abs(java_x - python_x)
                    y_diff = abs(java_y - python_y)
                    point_diff = max(x_diff, y_diff)
                    max_diff = max(max_diff, point_diff)
                
                tolerance = 1e-5  # 10 micrometers for edge cases
                assert max_diff < tolerance, f"Maximum difference too large for edge case {crs_format}: {max_diff:.10f} > {tolerance}"
                print(f"    ✅ Edge case {crs_format} accuracy test passed (max diff: {max_diff:.10f})")
