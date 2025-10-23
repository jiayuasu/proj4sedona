"""
Simple test to verify pytest setup and JAR file access.
"""

import pytest
import os
import subprocess
import sys


def test_jar_files_exist(proj4sedona_jar_path):
    """Test that required JAR files exist."""
    assert os.path.exists(proj4sedona_jar_path["core"]), f"Core JAR not found: {proj4sedona_jar_path['core']}"
    assert os.path.exists(proj4sedona_jar_path["mgrs"]), f"MGRS JAR not found: {proj4sedona_jar_path['mgrs']}"
    assert os.path.exists(proj4sedona_jar_path["wkt"]), f"WKT JAR not found: {proj4sedona_jar_path['wkt']}"


def test_pyproj_available():
    """Test that pyproj is available."""
    try:
        import pyproj
        assert pyproj.__version__ is not None
        print(f"pyproj version: {pyproj.__version__}")
    except ImportError:
        pytest.fail("pyproj not available")


def test_java_available():
    """Test that Java is available."""
    try:
        result = subprocess.run(['java', '-version'], capture_output=True, text=True)
        assert result.returncode == 0, "Java not available"
        print("Java is available")
    except FileNotFoundError:
        pytest.fail("Java not found")


def test_java_compilation(proj4sedona_jar_path):
    """Test that we can compile a simple Java class using the JARs."""
    java_code = '''
class SimpleTest {
    public static void main(String[] args) {
        System.out.println("Java compilation test successful");
    }
}
'''
    
    import tempfile
    with tempfile.NamedTemporaryFile(mode='w', suffix='.java', delete=False) as f:
        f.write(java_code)
        java_file = f.name
    
    try:
        # Compile
        classpath = ":".join(proj4sedona_jar_path.values())
        compile_cmd = ['javac', '-cp', classpath, java_file]
        result = subprocess.run(compile_cmd, capture_output=True, text=True)
        
        assert result.returncode == 0, f"Java compilation failed: {result.stderr}"
        
        # Run
        class_file = java_file.replace('.java', '.class')
        run_cmd = ['java', '-cp', f'{classpath}:{os.path.dirname(class_file)}', 'SimpleTest']
        result = subprocess.run(run_cmd, capture_output=True, text=True)
        
        assert result.returncode == 0, f"Java execution failed: {result.stderr}"
        assert "Java compilation test successful" in result.stdout
        
    finally:
        try:
            os.unlink(java_file)
            os.unlink(java_file.replace('.java', '.class'))
        except:
            pass


def test_python_runner_basic(python_runner):
    """Test basic Python runner functionality."""
    # Test with a simple scenario
    scenario = {
        "name": "Simple Test",
        "from_crs": "EPSG:4326",
        "to_crs": "EPSG:4326",  # Identity transformation
        "test_points": [(0.0, 0.0)]
    }
    
    result = python_runner.run_benchmark(scenario, 1)
    
    assert result["returncode"] == 0, f"Python runner failed: {result['error']}"
    assert "pyproj Benchmark" in result["output"]


def test_java_runner_basic(java_runner):
    """Test basic Java runner functionality."""
    # Test with a simple scenario
    scenario = {
        "name": "Simple Test",
        "from_crs": "EPSG:4326",
        "to_crs": "EPSG:4326",  # Identity transformation
        "test_points": [(0.0, 0.0)]
    }
    
    result = java_runner.run_benchmark(scenario, 1)
    
    assert result["returncode"] == 0, f"Java runner failed: {result['error']}"
    assert "Proj4Sedona Benchmark" in result["output"]


def test_scenario_structure(test_scenarios):
    """Test that test scenarios are properly structured."""
    assert len(test_scenarios) > 0, "No test scenarios defined"
    
    for scenario in test_scenarios:
        assert "name" in scenario, f"Scenario missing name: {scenario}"
        assert "epsg_from" in scenario, f"Scenario missing epsg_from: {scenario}"
        assert "epsg_to" in scenario, f"Scenario missing epsg_to: {scenario}"
        assert "test_points" in scenario, f"Scenario missing test_points: {scenario}"
        assert len(scenario["test_points"]) > 0, f"Scenario has no test points: {scenario}"
        
        # Validate test points
        for point in scenario["test_points"]:
            assert len(point) == 2, f"Test point should have 2 coordinates: {point}"
            assert isinstance(point[0], (int, float)), f"X coordinate should be numeric: {point}"
            assert isinstance(point[1], (int, float)), f"Y coordinate should be numeric: {point}"


def test_benchmark_iterations(benchmark_iterations):
    """Test that benchmark iterations are reasonable."""
    assert benchmark_iterations > 0, f"Benchmark iterations should be positive: {benchmark_iterations}"
    assert benchmark_iterations >= 1, f"Benchmark iterations should be at least 1: {benchmark_iterations}"
    assert benchmark_iterations <= 1000000, f"Benchmark iterations should be at most 1,000,000: {benchmark_iterations}"


def test_warmup_iterations(warmup_iterations, benchmark_iterations):
    """Test that warmup iterations are reasonable."""
    assert warmup_iterations > 0, f"Warmup iterations should be positive: {warmup_iterations}"
    assert warmup_iterations < benchmark_iterations, f"Warmup iterations should be less than benchmark iterations"
