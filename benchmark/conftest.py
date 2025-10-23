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
    """Define test scenarios for coordinate transformations with multiple CRS formats."""
    return [
        {
            "name": "WGS84 to Web Mercator",
            "epsg_from": "EPSG:4326",
            "epsg_to": "EPSG:3857",
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
            "name": "WGS84 to UTM Zone 19N",
            "epsg_from": "EPSG:4326",
            "epsg_to": "EPSG:32619",
            "cache_type": "local",
            "test_points": [
                (-71.0, 41.0),   # Boston, MA
                (-70.0, 42.0),   # New Hampshire
                (-69.0, 43.0),   # Maine
                (-72.0, 40.0),   # Connecticut
            ]
        },
        {
            "name": "WGS84 to Lambert Conformal Conic",
            "epsg_from": "EPSG:4326",
            "epsg_to": "EPSG:32145",
            "cache_type": "local",
            "test_points": [
                (-74.0, 40.7),   # New York, NY
                (-73.9, 40.8),   # Brooklyn, NY
                (-73.8, 40.7),   # Queens, NY
                (-74.2, 40.6),   # Staten Island, NY
            ]
        },
        {
            "name": "NAD83 to WGS84",
            "epsg_from": "EPSG:4269",
            "epsg_to": "EPSG:4326",
            "cache_type": "cdn_grid",
            "test_points": [
                (-71.0, 41.0),   # Boston, MA
                (-74.0, 40.7),   # New York, NY
                (-87.6, 41.9),   # Chicago, IL
                (-122.4, 37.8),  # San Francisco, CA
            ]
        },
        {
            "name": "UTM to WGS84",
            "epsg_from": "EPSG:32619",
            "epsg_to": "EPSG:4326",
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
def wkt2_definitions():
    """WKT2 CRS definitions for all test scenarios."""
    return {
        "WGS84": """
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
        """.strip(),
        "WebMercator": """
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
        """.strip(),
        "UTM_19N": """
PROJCRS["WGS 84 / UTM zone 19N",
    BASEGEOGCRS["WGS 84",
        DATUM["World Geodetic System 1984",
            ELLIPSOID["WGS 84",6378137,298.257223563,
                LENGTHUNIT["metre",1]]],
        PRIMEM["Greenwich",0,
            ANGLEUNIT["degree",0.0174532925199433]]],
    CONVERSION["UTM zone 19N",
        METHOD["Transverse Mercator"],
        PARAMETER["Latitude of natural origin",0,
            ANGLEUNIT["degree",0.0174532925199433]],
        PARAMETER["Longitude of natural origin",-69,
            ANGLEUNIT["degree",0.0174532925199433]],
        PARAMETER["Scale factor at natural origin",0.9996,
            SCALEUNIT["unity",1]],
        PARAMETER["False easting",500000,
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
    ID["EPSG",32619]]
        """.strip(),
        "Lambert_Conic": """
PROJCRS["NAD83 / New York Long Island",
    BASEGEOGCRS["NAD83",
        DATUM["North American Datum 1983",
            ELLIPSOID["GRS 1980",6378137,298.257222101,
                LENGTHUNIT["metre",1]]],
        PRIMEM["Greenwich",0,
            ANGLEUNIT["degree",0.0174532925199433]]],
    CONVERSION["SPCS83 New York Long Island zone (meters)",
        METHOD["Lambert Conic Conformal (2SP)"],
        PARAMETER["Latitude of false origin",40.1666666666667,
            ANGLEUNIT["degree",0.0174532925199433]],
        PARAMETER["Longitude of false origin",-74,
            ANGLEUNIT["degree",0.0174532925199433]],
        PARAMETER["Latitude of 1st standard parallel",40.6666666666667,
            ANGLEUNIT["degree",0.0174532925199433]],
        PARAMETER["Latitude of 2nd standard parallel",41.0333333333333,
            ANGLEUNIT["degree",0.0174532925199433]],
        PARAMETER["Easting at false origin",300000,
            LENGTHUNIT["metre",1]],
        PARAMETER["Northing at false origin",0,
            LENGTHUNIT["metre",1]]],
    CS[Cartesian,2],
        AXIS["(E)",east,
            ORDER[1],
            LENGTHUNIT["metre",1]],
        AXIS["(N)",north,
            ORDER[2],
            LENGTHUNIT["metre",1]],
    ID["EPSG",32145]]
        """.strip(),
        "NAD83": """
GEOGCRS["NAD83",
    DATUM["North American Datum 1983",
        ELLIPSOID["GRS 1980",6378137,298.257222101,
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
    ID["EPSG",4269]]
        """.strip()
    }


@pytest.fixture(scope="session")
def projjson_definitions():
    """PROJJSON CRS definitions for all test scenarios.
    
    Uses pyproj to generate correct PROJJSON from EPSG codes.
    """
    import pyproj
    
    crs_codes = {
        "WGS84": "EPSG:4326",
        "WebMercator": "EPSG:3857",
        "UTM_19N": "EPSG:32619",
        "Lambert_Conic": "EPSG:32145",
        "NAD83": "EPSG:4269"
    }
    
    projjson_defs = {}
    for name, epsg in crs_codes.items():
        crs = pyproj.CRS(epsg)
        projjson_defs[name] = crs.to_json_dict()
    
    return projjson_defs



@pytest.fixture(scope="session")
def batch_test_scenarios(benchmark_iterations):
    """Batch transformation test scenarios with caching and datum shift."""
    # Calculate batch sizes based on iterations to ensure largest batch doesn't exceed iterations
    max_batch_size = min(100000, benchmark_iterations)
    batch_sizes = []
    
    # Generate batch sizes that don't exceed iterations
    for size in [100, 1000, 10000, 100000]:
        if size <= max_batch_size:
            batch_sizes.append(size)
    
    # If no standard sizes fit, use a smaller size
    if not batch_sizes:
        batch_sizes = [min(100, benchmark_iterations)]
    
    return [
        {
            "name": "Batch WGS84 to Web Mercator (Local Cache)",
            "epsg_from": "EPSG:4326",
            "epsg_to": "EPSG:3857",
            "cache_type": "local",
            "batch_sizes": batch_sizes,
            "test_points": [
                (-71.0, 41.0), (-74.0, 40.7), (-87.6, 41.9), (-122.4, 37.8),
                (0.0, 0.0), (2.3, 48.9), (139.7, 35.7), (-80.0, 25.0),
                (120.0, 30.0), (-100.0, 50.0), (0.0, 60.0), (-30.0, -30.0)
            ]
        },
        {
            "name": "Batch NAD83 to WGS84 (CDN Grid Files)",
            "epsg_from": "EPSG:4269", 
            "epsg_to": "EPSG:4326",
            "cache_type": "cdn_grid",
            "batch_sizes": [size for size in batch_sizes if size <= 10000],  # Limit datum shift to smaller batches
            "test_points": [
                (-71.0, 41.0), (-74.0, 40.7), (-87.6, 41.9), (-122.4, 37.8),
                (-80.0, 25.0), (-100.0, 50.0), (-120.0, 45.0), (-95.0, 30.0)
            ]
        }
    ]


@pytest.fixture(scope="session")
def benchmark_iterations():
    """Number of iterations for performance benchmarks."""
    import os
    return int(os.environ.get("BENCHMARK_ITERATIONS", 10000))


@pytest.fixture(scope="session")
def warmup_iterations(benchmark_iterations):
    """Number of warmup iterations."""
    # Set warmup to be 10% of benchmark iterations, but at least 1 and at most 100
    # Ensure warmup is always less than benchmark_iterations
    warmup = max(1, min(100, benchmark_iterations // 10))
    if warmup >= benchmark_iterations:
        warmup = max(1, benchmark_iterations - 1)
    return warmup


def _escape_java_string(s):
    """Escape a string for use in Java string literals."""
    return '"' + s.replace('\\', '\\\\').replace('"', '\\"').replace('\n', '\\n') + '"'


def _get_crs_strings(scenario, crs_format, wkt2_defs=None, projjson_defs=None):
    """Get CRS strings based on format (epsg, wkt2, or projjson)."""
    if crs_format == "epsg":
        from_crs = scenario.get('epsg_from', scenario.get('from_crs', ''))
        to_crs = scenario.get('epsg_to', scenario.get('to_crs', ''))
    elif crs_format == "wkt2":
        from_crs = wkt2_defs.get(scenario.get('wkt2_from', 'WGS84'), '')
        to_crs = wkt2_defs.get(scenario.get('wkt2_to', 'WebMercator'), '')
    elif crs_format == "projjson":
        from_crs = projjson_defs.get(scenario.get('projjson_from', 'WGS84'), {})
        to_crs = projjson_defs.get(scenario.get('projjson_to', 'WebMercator'), {})
    else:
        raise ValueError(f"Unsupported CRS format: {crs_format}")
    
    return from_crs, to_crs


@pytest.fixture
def java_runner(proj4sedona_jar_path):
    """Create a Java runner for Proj4Sedona benchmarks."""
    
    class JavaRunner:
        def __init__(self, jar_paths):
            self.jar_paths = jar_paths
            self.classpath = ":".join(jar_paths.values())
        
        def run_benchmark(self, scenario, iterations=10000, crs_format="epsg", wkt2_defs=None, projjson_defs=None):
            """Run a Java benchmark for a given scenario."""
            
            # Get CRS strings based on format
            from_crs, to_crs = _get_crs_strings(scenario, crs_format, wkt2_defs, projjson_defs)
            
            # Convert projjson dict to string for Java
            if crs_format == "projjson":
                from_crs = str(from_crs)
                to_crs = str(to_crs)
            
            java_code = f'''
import org.apache.sedona.proj.Proj4Sedona;
import org.apache.sedona.proj.core.Point;
import java.util.List;
import java.util.ArrayList;

public class BenchmarkRunner {{
    public static void main(String[] args) {{
        String fromCrs = {_escape_java_string(from_crs)};
        String toCrs = {_escape_java_string(to_crs)};
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
                except OSError:
                    pass
    
    return JavaRunner(proj4sedona_jar_path)


@pytest.fixture
def batch_java_runner(proj4sedona_jar_path):
    """Create a Java runner for batch transformation benchmarks."""
    
    class BatchJavaRunner:
        def __init__(self, jar_paths):
            self.jar_paths = jar_paths
            self.classpath = ":".join(jar_paths.values())
        
        def run_batch_benchmark(self, scenario, batch_size, iterations=1000, crs_format="epsg", wkt2_defs=None, projjson_defs=None):
            """Run a Java batch transformation benchmark."""
            
            # Get CRS strings based on format
            from_crs, to_crs = _get_crs_strings(scenario, crs_format, wkt2_defs, projjson_defs)
            
            # Convert projjson dict to string for Java
            if crs_format == "projjson":
                from_crs = str(from_crs)
                to_crs = str(to_crs)
            
            # Use base test points and generate batch dynamically in Java
            base_points = scenario['test_points']
            
            java_code = f'''
import org.apache.sedona.proj.Proj4Sedona;
import org.apache.sedona.proj.core.Point;
import java.util.List;
import java.util.ArrayList;

public class BatchBenchmarkRunner {{
    public static void main(String[] args) {{
        String fromCrs = {_escape_java_string(from_crs)};
        String toCrs = {_escape_java_string(to_crs)};
        int batchSize = {batch_size};
        int iterations = {iterations};
        
        // Base test points to cycle through
        Point[] basePoints = {{
'''
            
            # Add only base test points
            for i, (x, y) in enumerate(base_points):
                java_code += f'            new Point({x}, {y})'
                if i < len(base_points) - 1:
                    java_code += ','
                java_code += '\n'
            
            java_code += f'''        }};
        
        // Generate full batch by cycling through base points
        Point[] testPoints = new Point[batchSize];
        for (int i = 0; i < batchSize; i++) {{
            testPoints[i] = basePoints[i % basePoints.length];
        }}
        
        System.out.println("=== Proj4Sedona Batch Benchmark ===");
        System.out.println("Scenario: {scenario['name']}");
        System.out.println("CRS Format: {crs_format}");
        System.out.println("From CRS: " + fromCrs);
        System.out.println("To CRS: " + toCrs);
        System.out.println("Batch Size: " + batchSize);
        System.out.println("Iterations: " + iterations);
        
        // Batch transformation test
        System.out.println("\\nBatch Performance Test:");
        long startTime = System.nanoTime();
        int successCount = 0;
        
        for (int i = 0; i < iterations; i++) {{
            try {{
                // Create batch of points
                List<Point> batch = new ArrayList<>();
                for (Point point : testPoints) {{
                    batch.add(point);
                }}
                
                // Transform batch
                List<Point> results = new ArrayList<>();
                for (Point point : batch) {{
                    Point result = Proj4Sedona.transform(fromCrs, toCrs, point);
                    results.add(result);
                    successCount++;
                }}
            }} catch (Exception e) {{
                // Count failures
            }}
        }}
        
        long endTime = System.nanoTime();
        double totalTimeMs = (endTime - startTime) / 1_000_000.0;
        double avgTimeMs = totalTimeMs / (iterations * batchSize);
        double tps = 1000.0 / avgTimeMs;
        
        System.out.println(String.format("Total time: %.2f ms", totalTimeMs));
        System.out.println(String.format("Average per transformation: %.6f ms", avgTimeMs));
        System.out.println(String.format("Transformations per second: %.0f", tps));
        System.out.println(String.format("Success rate: %.2f%%", (double)successCount / (iterations * batchSize) * 100));
        
    }}
}}
'''
            
            # Write Java code to temporary file
            import tempfile
            import os
            temp_dir = tempfile.mkdtemp()
            java_file = os.path.join(temp_dir, 'BatchBenchmarkRunner.java')
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
                run_cmd = ['java', '-cp', f'{self.classpath}:{os.path.dirname(class_file)}', 'BatchBenchmarkRunner']
                
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
                except OSError:
                    pass
    
    return BatchJavaRunner(proj4sedona_jar_path)


@pytest.fixture
def python_runner():
    """Create a Python runner for pyproj benchmarks."""
    
    class PythonRunner:
        def __init__(self):
            pass
        
        def run_benchmark(self, scenario, iterations=10000, crs_format="epsg", wkt2_defs=None, projjson_defs=None):
            """Run a Python benchmark for a given scenario."""
            
            # Get CRS strings based on format
            from_crs, to_crs = _get_crs_strings(scenario, crs_format, wkt2_defs, projjson_defs)
            
            # For PROJJSON, we need to pass the dict as JSON string
            if crs_format == "projjson":
                import json
                from_crs_json = json.dumps(from_crs)
                to_crs_json = json.dumps(to_crs)
            else:
                from_crs_json = None
                to_crs_json = None
            
            python_code = f'''
import pyproj
import time
import sys
import json

def run_pyproj_benchmark():
    from_crs = {repr(from_crs)}
    to_crs = {repr(to_crs)}
    iterations = {iterations}
    
    # Test points
    test_points = {scenario['test_points']}
    
    print("=== pyproj Benchmark ===")
    print(f"Scenario: {scenario['name']}")
    print(f"CRS Format: {crs_format}")
    print(f"From CRS: {{str(from_crs)[:100]}}...")  # Truncate for display
    print(f"To CRS: {{str(to_crs)[:100]}}...")
    print(f"Test Points: {{len(test_points)}}")
    print(f"Iterations: {{iterations}}")
    
    try:
        # Create transformer based on CRS format
        if "{crs_format}" == "epsg":
            transformer = pyproj.Transformer.from_crs(from_crs, to_crs, always_xy=True)
        elif "{crs_format}" == "wkt2":
            from_crs_obj = pyproj.CRS.from_wkt(from_crs)
            to_crs_obj = pyproj.CRS.from_wkt(to_crs)
            transformer = pyproj.Transformer.from_crs(from_crs_obj, to_crs_obj, always_xy=True)
        elif "{crs_format}" == "projjson":
            # Try to create CRS from PROJJSON dict
            try:
                from_crs_obj = pyproj.CRS.from_json_dict(from_crs)
                to_crs_obj = pyproj.CRS.from_json_dict(to_crs)
                transformer = pyproj.Transformer.from_crs(from_crs_obj, to_crs_obj, always_xy=True)
            except Exception as e:
                print(f"\\nError creating CRS from PROJJSON: {{e}}")
                print("PROJJSON format may be incomplete or not fully supported by pyproj")
                print("\\nPerformance Test:")
                print("Total time: 0.00 ms")
                print("Average per transformation: 0.000000 ms")
                print("Transformations per second: 0")
                print("Success rate: 0.00%")
                sys.exit(0)
        
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
                except OSError:
                    pass
    
    return PythonRunner()


@pytest.fixture
def batch_python_runner():
    """Create a Python runner for batch transformation benchmarks."""
    
    class BatchPythonRunner:
        def __init__(self):
            pass
        
        def run_batch_benchmark(self, scenario, batch_size, iterations=1000, crs_format="epsg", wkt2_defs=None, projjson_defs=None):
            """Run a Python batch transformation benchmark."""
            
            # Get CRS strings based on format
            from_crs, to_crs = _get_crs_strings(scenario, crs_format, wkt2_defs, projjson_defs)
            
            # Generate test points for batch
            test_points = scenario['test_points'] * (batch_size // len(scenario['test_points']) + 1)
            test_points = test_points[:batch_size]
            
            python_code = f'''
import pyproj
import time
import sys
import json

def run_pyproj_batch_benchmark():
    from_crs = {repr(from_crs)}
    to_crs = {repr(to_crs)}
    batch_size = {batch_size}
    iterations = {iterations}
    
    # Test points
    test_points = {test_points}
    
    print("=== pyproj Batch Benchmark ===")
    print(f"Scenario: {scenario['name']}")
    print(f"CRS Format: {crs_format}")
    print(f"From CRS: {{from_crs}}")
    print(f"To CRS: {{to_crs}}")
    print(f"Batch Size: {{batch_size}}")
    print(f"Iterations: {{iterations}}")
    
    try:
        # Create transformer based on CRS format
        if "{crs_format}" == "epsg":
            transformer = pyproj.Transformer.from_crs(from_crs, to_crs, always_xy=True)
        elif "{crs_format}" == "wkt2":
            from_crs_obj = pyproj.CRS.from_wkt(from_crs)
            to_crs_obj = pyproj.CRS.from_wkt(to_crs)
            transformer = pyproj.Transformer.from_crs(from_crs_obj, to_crs_obj, always_xy=True)
        elif "{crs_format}" == "projjson":
            # Try to create CRS from PROJJSON dict
            try:
                from_crs_obj = pyproj.CRS.from_json_dict(from_crs)
                to_crs_obj = pyproj.CRS.from_json_dict(to_crs)
                transformer = pyproj.Transformer.from_crs(from_crs_obj, to_crs_obj, always_xy=True)
            except Exception as e:
                print(f"\\nError creating CRS from PROJJSON: {{e}}")
                print("PROJJSON format may be incomplete or not fully supported by pyproj")
                print("\\nPerformance Test:")
                print("Total time: 0.00 ms")
                print("Average per transformation: 0.000000 ms")
                print("Transformations per second: 0")
                print("Success rate: 0.00%")
                sys.exit(0)
        
        # Batch transformation test
        print("\\nBatch Performance Test:")
        start_time = time.time()
        success_count = 0
        
        for _ in range(iterations):
            try:
                # Prepare batch coordinates
                x_coords = [point[0] for point in test_points]
                y_coords = [point[1] for point in test_points]
                
                # Transform batch
                transformed_x, transformed_y = transformer.transform(x_coords, y_coords)
                success_count += len(transformed_x)
            except Exception:
                pass
        
        end_time = time.time()
        total_time_ms = (end_time - start_time) * 1000
        avg_time_ms = total_time_ms / (iterations * batch_size)
        tps = 1000.0 / avg_time_ms
        
        print(f"Total time: {{total_time_ms:.2f}} ms")
        print(f"Average per transformation: {{avg_time_ms:.6f}} ms")
        print(f"Transformations per second: {{tps:.0f}}")
        print(f"Success rate: {{success_count / (iterations * batch_size) * 100:.2f}}%")
        
    except Exception as e:
        print(f"Error in batch benchmark: {{e}}")

if __name__ == "__main__":
    run_pyproj_batch_benchmark()
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
                except OSError:
                    pass
    
    return BatchPythonRunner()


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
