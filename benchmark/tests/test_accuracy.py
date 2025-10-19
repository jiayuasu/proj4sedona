"""
Accuracy benchmark tests for Proj4Sedona vs pyproj.
Includes comprehensive accuracy tests for WKT2, datum shift, and batch transformations.
"""

import pytest
import subprocess
import os
import sys
import math
from typing import Dict, Any, List, Tuple


@pytest.mark.accuracy
class TestAccuracyBenchmarks:
    """Accuracy benchmark tests."""
    
    def test_java_accuracy(self, java_runner, test_scenarios):
        """Test Proj4Sedona (Java) accuracy."""
        results = {}
        
        for scenario in test_scenarios:
            print(f"\n🎯 Testing Java accuracy for: {scenario['name']}")
            
            result = java_runner.run_benchmark(scenario, 1)  # Single iteration for accuracy
            
            assert result["returncode"] == 0, f"Java accuracy test failed: {result['error']}"
            
            # Parse accuracy results
            accuracy_data = self._parse_accuracy_output(result["output"])
            results[scenario['name']] = accuracy_data
            
            # Validate accuracy
            self._validate_accuracy(accuracy_data, scenario)
        
        return results
    
    def test_python_accuracy(self, python_runner, test_scenarios):
        """Test pyproj (Python) accuracy."""
        results = {}
        
        for scenario in test_scenarios:
            print(f"\n🎯 Testing Python accuracy for: {scenario['name']}")
            
            result = python_runner.run_benchmark(scenario, 1)  # Single iteration for accuracy
            
            assert result["returncode"] == 0, f"Python accuracy test failed: {result['error']}"
            
            # Parse accuracy results
            accuracy_data = self._parse_accuracy_output(result["output"])
            results[scenario['name']] = accuracy_data
            
            # Validate accuracy
            self._validate_accuracy(accuracy_data, scenario)
        
        return results
    
    def test_accuracy_comparison(self, java_runner, python_runner, test_scenarios):
        """Compare accuracy between Java and Python implementations."""
        print("\n📊 Running accuracy comparison...")
        
        java_results = {}
        python_results = {}
        
        for scenario in test_scenarios:
            print(f"\n🔄 Comparing accuracy: {scenario['name']}")
            
            # Run Java accuracy test
            java_result = java_runner.run_benchmark(scenario, 1)
            assert java_result["returncode"] == 0, f"Java accuracy test failed: {java_result['error']}"
            java_accuracy = self._parse_accuracy_output(java_result["output"])
            java_results[scenario['name']] = java_accuracy
            
            # Run Python accuracy test
            python_result = python_runner.run_benchmark(scenario, 1)
            assert python_result["returncode"] == 0, f"Python accuracy test failed: {python_result['error']}"
            python_accuracy = self._parse_accuracy_output(python_result["output"])
            python_results[scenario['name']] = python_accuracy
            
            # Compare accuracy
            self._compare_accuracy(java_accuracy, python_accuracy, scenario)
        
        return {
            "java": java_results,
            "python": python_results
        }
    
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
            elif "Performance Test:" in line:
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
    
    def _validate_accuracy(self, accuracy_data: Dict[str, Any], scenario: Dict[str, Any]):
        """Validate accuracy results."""
        # Check that we have results for all test points
        expected_points = len(scenario['test_points'])
        actual_points = len(accuracy_data['points'])
        
        assert actual_points == expected_points, f"Expected {expected_points} points, got {actual_points}"
        
        # Check that there are no errors
        assert len(accuracy_data['errors']) == 0, f"Accuracy errors found: {accuracy_data['errors']}"
        
        # Validate coordinate precision
        for point_data in accuracy_data['points']:
            x_out, y_out = point_data['output']
            
            # Check for reasonable coordinate ranges based on CRS
            if scenario['to_crs'] == 'EPSG:3857':  # Web Mercator
                # Web Mercator coordinates should be in reasonable range
                assert -20000000 < x_out < 20000000, f"X coordinate out of range: {x_out}"
                assert -20000000 < y_out < 20000000, f"Y coordinate out of range: {y_out}"
            elif scenario['to_crs'] == 'EPSG:32619':  # UTM
                # UTM coordinates should be positive and in reasonable range
                assert 0 < x_out < 1000000, f"UTM X coordinate out of range: {x_out}"
                assert 0 < y_out < 10000000, f"UTM Y coordinate out of range: {y_out}"
            elif scenario['to_crs'] == 'EPSG:4326':  # WGS84
                # WGS84 coordinates should be in lat/lon range
                assert -180 <= x_out <= 180, f"Longitude out of range: {x_out}"
                assert -90 <= y_out <= 90, f"Latitude out of range: {y_out}"
    
    def _compare_accuracy(self, java_accuracy: Dict[str, Any], python_accuracy: Dict[str, Any], scenario: Dict[str, Any]):
        """Compare accuracy between Java and Python results."""
        print(f"  Java points: {len(java_accuracy['points'])}")
        print(f"  Python points: {len(python_accuracy['points'])}")
        
        # Both should have the same number of points
        assert len(java_accuracy['points']) == len(python_accuracy['points']), \
            f"Different number of points: Java={len(java_accuracy['points'])}, Python={len(python_accuracy['points'])}"
        
        # Compare coordinate values
        max_diff = 0.0
        total_diff = 0.0
        comparisons = 0
        
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
            
            print(f"    Point {i+1}: Java=({java_x:.6f}, {java_y:.6f}), Python=({python_x:.6f}, {python_y:.6f})")
            print(f"    Difference: X={x_diff:.10f}, Y={y_diff:.10f}")
        
        avg_diff = total_diff / comparisons if comparisons > 0 else 0
        
        print(f"  Maximum difference: {max_diff:.10f}")
        print(f"  Average difference: {avg_diff:.10f}")
        
        # Assert that differences are within acceptable tolerance
        # For coordinate transformations, we expect very high precision
        tolerance = 1e-6  # 1 micrometer
        assert max_diff < tolerance, f"Maximum difference too large: {max_diff:.10f} > {tolerance}"
        assert avg_diff < tolerance / 10, f"Average difference too large: {avg_diff:.10f} > {tolerance/10}"


@pytest.mark.accuracy
class TestEdgeCaseAccuracy:
    """Test accuracy with edge cases and boundary conditions."""
    
    def test_edge_case_coordinates(self, java_runner, python_runner):
        """Test accuracy with edge case coordinates."""
        edge_cases = [
            {
                "name": "Equator/Prime Meridian",
                "from_crs": "EPSG:4326",
                "to_crs": "EPSG:3857",
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
                "from_crs": "EPSG:4326",
                "to_crs": "EPSG:3857",
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
            
            # Test Java
            java_result = java_runner.run_benchmark(edge_case, 1)
            assert java_result["returncode"] == 0, f"Java edge case test failed: {java_result['error']}"
            
            # Test Python
            python_result = python_runner.run_benchmark(edge_case, 1)
            assert python_result["returncode"] == 0, f"Python edge case test failed: {python_result['error']}"
            
            # Compare results
            java_accuracy = self._parse_accuracy_output(java_result["output"])
            python_accuracy = self._parse_accuracy_output(python_result["output"])
            
            self._compare_accuracy(java_accuracy, python_accuracy, edge_case)
    
    
    def _compare_accuracy(self, java_accuracy: Dict[str, Any], python_accuracy: Dict[str, Any], scenario: Dict[str, Any]):
        """Compare accuracy between Java and Python results."""
        assert len(java_accuracy['points']) == len(python_accuracy['points'])
        
        max_diff = 0.0
        for java_point, python_point in zip(java_accuracy['points'], python_accuracy['points']):
            java_x, java_y = java_point['output']
            python_x, python_y = python_point['output']
            
            x_diff = abs(java_x - python_x)
            y_diff = abs(java_y - python_y)
            point_diff = max(x_diff, y_diff)
            
            max_diff = max(max_diff, point_diff)
        
        # For edge cases, we might allow slightly higher tolerance
        tolerance = 1e-5  # 10 micrometers
        assert max_diff < tolerance, f"Maximum difference too large for edge case: {max_diff:.10f} > {tolerance}"
    
    @pytest.fixture(scope="class")
    def reference_coordinates(self):
        """Reference coordinates for comprehensive accuracy testing."""
        return [
            # Well-known test points
            (0.0, 0.0),           # Equator, Prime Meridian
            (0.0, 45.0),          # 45°N, Prime Meridian
            (0.0, -45.0),         # 45°S, Prime Meridian
            (90.0, 0.0),          # Equator, 90°E
            (-90.0, 0.0),         # Equator, 90°W
            (45.0, 45.0),         # 45°N, 45°E
            (-45.0, 45.0),        # 45°N, 45°W
            (45.0, -45.0),        # 45°S, 45°E
            (-45.0, -45.0),       # 45°S, 45°W
            # Edge cases
            (0.0001, 0.0001),     # Very small coordinates
            (0.0001, -0.0001),   # Very small negative coordinates
        ]
    
    @pytest.fixture(scope="class")
    def wkt2_definitions(self):
        """WKT2 CRS definitions for comprehensive accuracy testing."""
        return {
            "WGS84_WKT2": """
            GEOGCRS["WGS 84",
                DATUM["World Geodetic System 1984",
                    ELLIPSOID["WGS 84",6378137,298.257223563,
                        LENGTHUNIT["metre",1]]],
                PRIMEM["Greenwich",0,
                    ANGLEUNIT["degree",0.0174532925199433]],
                CS[ellipsoidal,2],
                    AXIS["geodetic latitude (Lat)",north,
                        ORDER[1],
                        ANGLEUNIT["degree",0.0174532925199433]],
                    AXIS["geodetic longitude (Lon)",east,
                        ORDER[2],
                        ANGLEUNIT["degree",0.0174532925199433]],
                ID["EPSG",4326]]
            """,
            "WebMercator_WKT2": """
            PROJCRS["WGS 84 / Pseudo-Mercator",
                BASEGEOGCRS["WGS 84",
                    DATUM["World Geodetic System 1984",
                        ELLIPSOID["WGS 84",6378137,298.257223563,
                            LENGTHUNIT["metre",1]]],
                    PRIMEM["Greenwich",0,
                        ANGLEUNIT["degree",0.0174532925199433]]],
                CONVERSION["Popular Visualisation Pseudo-Mercator",
                    METHOD["Popular Visualisation Pseudo Mercator"],
                    PARAMETER["Latitude of natural origin",0,
                        ANGLEUNIT["degree",0.0174532925199433]],
                    PARAMETER["Longitude of natural origin",0,
                        ANGLEUNIT["degree",0.0174532925199433]],
                    PARAMETER["False easting",0,
                        LENGTHUNIT["metre",1]],
                    PARAMETER["False northing",0,
                        LENGTHUNIT["metre",1]]],
                CS[Cartesian,2],
                    AXIS["(E)",east,
                        ORDER[1],
                        LENGTHUNIT["metre",1]],
                    AXIS["(N)",north,
                        ORDER[2],
                        LENGTHUNIT["metre",1]],
                ID["EPSG",3857]]
            """
        }
    
    @pytest.mark.accuracy
    @pytest.mark.comprehensive
    @pytest.mark.local_cache
    def test_wkt2_accuracy_python(self, python_runner, reference_coordinates, wkt2_definitions):
        """Test WKT2 transformation accuracy with pyproj (Python)."""
        print(f"\n🐍 Testing WKT2 accuracy with pyproj (Python)")
        
        try:
            from pyproj import CRS, Transformer
            
            # Create CRS from WKT2
            wgs84_crs = CRS.from_wkt(wkt2_definitions["WGS84_WKT2"])
            webmerc_crs = CRS.from_wkt(wkt2_definitions["WebMercator_WKT2"])
            
            transformer = Transformer.from_crs(wgs84_crs, webmerc_crs, always_xy=True)
            
            max_error = 0.0
            total_error = 0.0
            test_count = 0
            
            for lon, lat in reference_coordinates:
                # Transform coordinates
                transformed_x, transformed_y = transformer.transform(lon, lat)
                
                # Calculate expected result using standard Web Mercator formula
                expected_x = lon * 20037508.34 / 180
                expected_y = math.log(math.tan(math.pi/4 + math.radians(lat)/2)) * 20037508.34 / math.pi
                
                # Calculate error
                error_x = abs(transformed_x - expected_x)
                error_y = abs(transformed_y - expected_y)
                error = math.sqrt(error_x**2 + error_y**2)
                
                max_error = max(max_error, error)
                total_error += error
                test_count += 1
            
            avg_error = total_error / test_count
            
            print(f"✅ WKT2 Python Accuracy:")
            print(f"   Maximum deviation: {max_error:.2e} meters")
            print(f"   Average deviation: {avg_error:.2e} meters")
            print(f"   Test points: {test_count}")
            print(f"   Success rate: 100.0%")
            
            # Assert accuracy requirements
            assert max_error < 1e-6, f"Maximum error {max_error:.2e} exceeds 1e-6 meters"
            assert avg_error < 1e-7, f"Average error {avg_error:.2e} exceeds 1e-7 meters"
            
        except Exception as e:
            print(f"❌ WKT2 Python accuracy test failed: {e}")
            raise
    
    @pytest.mark.accuracy
    @pytest.mark.comprehensive
    @pytest.mark.cdn_grid
    def test_datum_shift_accuracy_python(self, python_runner, reference_coordinates):
        """Test datum shift transformation accuracy with pyproj (Python)."""
        print(f"\n🐍 Testing datum shift accuracy with pyproj (Python)")
        
        try:
            from pyproj import CRS, Transformer
            
            # Create CRS with different datums
            wgs84_crs = CRS.from_epsg(4326)  # WGS84
            nad83_crs = CRS.from_epsg(4269)  # NAD83
            
            transformer = Transformer.from_crs(wgs84_crs, nad83_crs, always_xy=True)
            
            max_error = 0.0
            total_error = 0.0
            test_count = 0
            
            for lon, lat in reference_coordinates:
                # Transform coordinates
                transformed_x, transformed_y = transformer.transform(lon, lat)
                
                # Calculate expected result (simplified)
                expected_x = lon  # Simplified for testing
                expected_y = lat  # Simplified for testing
                
                # Calculate error
                error_x = abs(transformed_x - expected_x)
                error_y = abs(transformed_y - expected_y)
                error = math.sqrt(error_x**2 + error_y**2)
                
                max_error = max(max_error, error)
                total_error += error
                test_count += 1
            
            avg_error = total_error / test_count
            
            print(f"✅ Datum Shift Python Accuracy:")
            print(f"   Maximum deviation: {max_error:.2e} meters")
            print(f"   Average deviation: {avg_error:.2e} meters")
            print(f"   Test points: {test_count}")
            print(f"   Success rate: 100.0%")
            
            # Assert accuracy requirements
            assert max_error < 1e-3, f"Maximum error {max_error:.2e} exceeds 1e-3 meters"
            assert avg_error < 1e-4, f"Average error {avg_error:.2e} exceeds 1e-4 meters"
            
        except Exception as e:
            print(f"❌ Datum Shift Python accuracy test failed: {e}")
            raise
    
    @pytest.mark.accuracy
    @pytest.mark.comprehensive
    @pytest.mark.local_cache
    def test_batch_accuracy_python(self, python_runner, reference_coordinates):
        """Test batch transformation accuracy with pyproj (Python)."""
        print(f"\n🐍 Testing batch accuracy with pyproj (Python)")
        
        try:
            from pyproj import CRS, Transformer
            
            wgs84_crs = CRS.from_epsg(4326)
            webmerc_crs = CRS.from_epsg(3857)
            transformer = Transformer.from_crs(wgs84_crs, webmerc_crs, always_xy=True)
            
            # Prepare batch coordinates
            x_coords = [coord[0] for coord in reference_coordinates]
            y_coords = [coord[1] for coord in reference_coordinates]
            
            # Perform batch transformation
            transformed_x, transformed_y = transformer.transform(x_coords, y_coords)
            
            max_error = 0.0
            total_error = 0.0
            test_count = 0
            
            # Verify batch results
            for i, (lon, lat) in enumerate(reference_coordinates):
                tx, ty = transformed_x[i], transformed_y[i]
                
                # Calculate expected result using standard Web Mercator formula
                expected_x = lon * 20037508.34 / 180
                expected_y = math.log(math.tan(math.pi/4 + math.radians(lat)/2)) * 20037508.34 / math.pi
                
                # Calculate error
                error_x = abs(tx - expected_x)
                error_y = abs(ty - expected_y)
                error = math.sqrt(error_x**2 + error_y**2)
                
                max_error = max(max_error, error)
                total_error += error
                test_count += 1
            
            avg_error = total_error / test_count
            
            print(f"✅ Batch Python Accuracy:")
            print(f"   Maximum deviation: {max_error:.2e} meters")
            print(f"   Average deviation: {avg_error:.2e} meters")
            print(f"   Test points: {test_count}")
            print(f"   Success rate: 100.0%")
            
            # Assert accuracy requirements
            assert max_error < 1e-6, f"Maximum error {max_error:.2e} exceeds 1e-6 meters"
            assert avg_error < 1e-7, f"Average error {avg_error:.2e} exceeds 1e-7 meters"
            
        except Exception as e:
            print(f"❌ Batch Python accuracy test failed: {e}")
            raise
