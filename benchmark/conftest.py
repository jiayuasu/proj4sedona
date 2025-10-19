"""
Pytest configuration and fixtures for Proj4Sedona vs pyproj benchmarks.
"""

import pytest
import subprocess
import os
import sys
import tempfile
import time
from typing import List, Dict, Tuple, Any
import pyproj


@pytest.fixture(scope="session")
def proj4sedona_jar_path():
    """Get the path to the compiled Proj4Sedona JAR files."""
    core_jar = "../core/target/proj4sedona-core-1.0.0-SNAPSHOT.jar"
    mgrs_jar = "../mgrs/target/proj4sedona-mgrs-1.0.0-SNAPSHOT.jar"
    wkt_jar = "../wkt-parser/target/wkt-parser-1.0.0-SNAPSHOT.jar"
    
    # Check if JARs exist
    for jar in [core_jar, mgrs_jar, wkt_jar]:
        if not os.path.exists(jar):
            pytest.skip(f"Required JAR file not found: {jar}")
    
    return {
        "core": core_jar,
        "mgrs": mgrs_jar,
        "wkt": wkt_jar
    }


@pytest.fixture(scope="session")
def pyproj_version():
    """Get pyproj version."""
    return pyproj.__version__


@pytest.fixture(scope="session")
def test_scenarios():
    """Define test scenarios for coordinate transformations."""
    return [
        {
            "name": "WGS84 to Web Mercator (Local Cache)",
            "from_crs": "EPSG:4326",
            "to_crs": "EPSG:3857",
            "cache_type": "local",
            "test_points": [
                (-71.0, 41.0),   # Boston, MA
                (-74.0, 40.7),   # New York, NY
                (-87.6, 41.9),   # Chicago, IL
                (-122.4, 37.8),  # San Francisco, CA
                (0.0, 0.0),      # Equator/Prime Meridian
                (2.3, 48.9),     # Paris, France
                (139.7, 35.7),   # Tokyo, Japan
            ]
        },
        {
            "name": "WGS84 to UTM Zone 19N (Local Cache)",
            "from_crs": "EPSG:4326",
            "to_crs": "EPSG:32619",
            "cache_type": "local",
            "test_points": [
                (-71.0, 41.0),   # Boston, MA
                (-70.0, 42.0),   # New Hampshire
                (-69.0, 43.0),   # Maine
                (-72.0, 40.0),   # Connecticut
            ]
        },
        {
            "name": "WGS84 to Lambert Conformal Conic (Local Cache)",
            "from_crs": "EPSG:4326",
            "to_crs": "EPSG:32145",
            "cache_type": "local",
            "test_points": [
                (-74.0, 40.7),   # New York, NY
                (-73.9, 40.8),   # Brooklyn, NY
                (-73.8, 40.7),   # Queens, NY
                (-74.2, 40.6),   # Staten Island, NY
            ]
        },
        {
            "name": "NAD83 to WGS84 (CDN Grid Files)",
            "from_crs": "EPSG:4269",
            "to_crs": "EPSG:4326",
            "cache_type": "cdn_grid",
            "test_points": [
                (-71.0, 41.0),   # Boston, MA
                (-74.0, 40.7),   # New York, NY
                (-87.6, 41.9),   # Chicago, IL
                (-122.4, 37.8),  # San Francisco, CA
            ]
        },
        {
            "name": "UTM to WGS84 (Local Cache)",
            "from_crs": "EPSG:32619",
            "to_crs": "EPSG:4326",
            "cache_type": "local",
            "test_points": [
                (300000, 4500000),  # Approximate Boston in UTM
                (400000, 4500000),  # Approximate New Hampshire
                (500000, 4500000),  # Approximate Maine
                (200000, 4500000),  # Approximate Connecticut
            ]
        }
    ]


@pytest.fixture(scope="session")
def benchmark_iterations():
    """Number of iterations for performance benchmarks."""
    return 10000


@pytest.fixture(scope="session")
def warmup_iterations():
    """Number of warmup iterations."""
    return 100


@pytest.fixture
def java_runner(proj4sedona_jar_path):
    """Create a Java runner for Proj4Sedona benchmarks."""
    
    class JavaRunner:
        def __init__(self, jar_paths):
            self.jar_paths = jar_paths
            self.classpath = ":".join(jar_paths.values())
        
        def run_benchmark(self, scenario, iterations=10000):
            """Run a Java benchmark for a given scenario."""
            
            java_code = f'''
import org.apache.sedona.proj.Proj4Sedona;
import org.apache.sedona.proj.core.Point;
import java.util.List;
import java.util.ArrayList;

public class BenchmarkRunner {{
    public static void main(String[] args) {{
        String fromCrs = "{scenario['from_crs']}";
        String toCrs = "{scenario['to_crs']}";
        int iterations = {iterations};
        
        // Test points
        Point[] testPoints = {{
'''
            
            # Add test points
            for i, (x, y) in enumerate(scenario['test_points']):
                java_code += f'            new Point({x}, {y})'
                if i < len(scenario['test_points']) - 1:
                    java_code += ','
                java_code += '\n'
            
            java_code += f'''        }};
        
        System.out.println("=== Proj4Sedona Benchmark ===");
        System.out.println("Scenario: {scenario['name']}");
        System.out.println("From CRS: " + fromCrs);
        System.out.println("To CRS: " + toCrs);
        System.out.println("Test Points: " + testPoints.length);
        System.out.println("Iterations: " + iterations);
        
        // Accuracy test - single transformation
        System.out.println("\\nAccuracy Test Results:");
        for (int i = 0; i < testPoints.length; i++) {{
            try {{
                Point result = Proj4Sedona.transform(fromCrs, toCrs, testPoints[i]);
                System.out.println(String.format("Point %d: (%.6f, %.6f) -> (%.6f, %.6f)", 
                    i+1, testPoints[i].x, testPoints[i].y, result.x, result.y));
            }} catch (Exception e) {{
                System.err.println("Error transforming point " + testPoints[i] + ": " + e.getMessage());
            }}
        }}
        
        // Performance test
        System.out.println("\\nPerformance Test:");
        long startTime = System.nanoTime();
        int successCount = 0;
        
        for (int i = 0; i < iterations; i++) {{
            for (Point point : testPoints) {{
                try {{
                    Proj4Sedona.transform(fromCrs, toCrs, point);
                    successCount++;
                }} catch (Exception e) {{
                    // Count failures
                }}
            }}
        }}
        
        long endTime = System.nanoTime();
        double totalTimeMs = (endTime - startTime) / 1_000_000.0;
        double avgTimeMs = totalTimeMs / (iterations * testPoints.length);
        double tps = 1000.0 / avgTimeMs;
        
        System.out.println(String.format("Total time: %.2f ms", totalTimeMs));
        System.out.println(String.format("Average per transformation: %.6f ms", avgTimeMs));
        System.out.println(String.format("Transformations per second: %.0f", tps));
        System.out.println(String.format("Success rate: %.2f%%", (double)successCount / (iterations * testPoints.length) * 100));
        
    }}
}}
'''
            
            # Write Java code to temporary file with proper class name
            import tempfile
            import os
            temp_dir = tempfile.mkdtemp()
            java_file = os.path.join(temp_dir, 'BenchmarkRunner.java')
            with open(java_file, 'w') as f:
                f.write(java_code)
            
            try:
                # Compile
                compile_cmd = ['javac', '-cp', self.classpath, java_file]
                result = subprocess.run(compile_cmd, capture_output=True, text=True)
                
                if result.returncode != 0:
                    raise RuntimeError(f"Java compilation failed: {result.stderr}")
                
                # Run
                class_file = java_file.replace('.java', '.class')
                run_cmd = ['java', '-cp', f'{self.classpath}:{os.path.dirname(class_file)}', 'BenchmarkRunner']
                
                result = subprocess.run(run_cmd, capture_output=True, text=True)
                
                if result.returncode != 0:
                    raise RuntimeError(f"Java execution failed: {result.stderr}")
                
                return {
                    "output": result.stdout,
                    "error": result.stderr,
                    "returncode": result.returncode
                }
                
            finally:
                try:
                    os.unlink(java_file)
                    os.unlink(java_file.replace('.java', '.class'))
                except:
                    pass
    
    return JavaRunner(proj4sedona_jar_path)


@pytest.fixture
def python_runner():
    """Create a Python runner for pyproj benchmarks."""
    
    class PythonRunner:
        def __init__(self):
            pass
        
        def run_benchmark(self, scenario, iterations=10000):
            """Run a Python benchmark for a given scenario."""
            
            python_code = f'''
import pyproj
import time
import sys

def run_pyproj_benchmark():
    from_crs = "{scenario['from_crs']}"
    to_crs = "{scenario['to_crs']}"
    iterations = {iterations}
    
    # Test points
    test_points = {scenario['test_points']}
    
    print("=== pyproj Benchmark ===")
    print(f"Scenario: {scenario['name']}")
    print(f"From CRS: {{from_crs}}")
    print(f"To CRS: {{to_crs}}")
    print(f"Test Points: {{len(test_points)}}")
    print(f"Iterations: {{iterations}}")
    
    try:
        # Create transformer
        transformer = pyproj.Transformer.from_crs(from_crs, to_crs, always_xy=True)
        
        # Accuracy test - single transformation
        print("\\nAccuracy Test Results:")
        for i, (x, y) in enumerate(test_points):
            try:
                x_out, y_out = transformer.transform(x, y)
                print(f"Point {{i+1}}: ({{x:.6f}}, {{y:.6f}}) -> ({{x_out:.6f}}, {{y_out:.6f}})")
            except Exception as e:
                print(f"Error transforming point ({{x}}, {{y}}): {{e}}")
        
        # Performance test
        print("\\nPerformance Test:")
        start_time = time.time()
        success_count = 0
        
        for _ in range(iterations):
            for x, y in test_points:
                try:
                    transformer.transform(x, y)
                    success_count += 1
                except Exception:
                    pass
        
        end_time = time.time()
        total_time_ms = (end_time - start_time) * 1000
        avg_time_ms = total_time_ms / (iterations * len(test_points))
        tps = 1000.0 / avg_time_ms
        
        print(f"Total time: {{total_time_ms:.2f}} ms")
        print(f"Average per transformation: {{avg_time_ms:.6f}} ms")
        print(f"Transformations per second: {{tps:.0f}}")
        print(f"Success rate: {{success_count / (iterations * len(test_points)) * 100:.2f}}%")
        
        
    except Exception as e:
        print(f"Error in benchmark: {{e}}")

if __name__ == "__main__":
    run_pyproj_benchmark()
'''
            
            # Write and run Python code
            with tempfile.NamedTemporaryFile(mode='w', suffix='.py', delete=False) as f:
                f.write(python_code)
                python_file = f.name
            
            try:
                result = subprocess.run([sys.executable, python_file], capture_output=True, text=True)
                
                return {
                    "output": result.stdout,
                    "error": result.stderr,
                    "returncode": result.returncode
                }
                
            finally:
                try:
                    os.unlink(python_file)
                except:
                    pass
    
    return PythonRunner()


@pytest.fixture
def benchmark_results():
    """Store benchmark results for comparison."""
    return {
        "java": {},
        "python": {}
    }


def pytest_configure(config):
    """Configure pytest with custom markers."""
    config.addinivalue_line(
        "markers", "performance: mark test as performance benchmark"
    )
    config.addinivalue_line(
        "markers", "accuracy: mark test as accuracy benchmark"
    )
    config.addinivalue_line(
        "markers", "slow: mark test as slow running"
    )
