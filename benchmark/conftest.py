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
from benchmark_config import PROJECT_VERSION
from template_loader import TemplateLoader


@pytest.fixture(scope="session")
def proj4sedona_jar_path():
    """Get the path to the compiled Proj4Sedona JAR files.
    
    Note: For geometry tests (JTS), you need to build with shaded profile:
        mvn clean package -Pshaded -DskipTests
    This creates an uber JAR with all dependencies (JTS, Jackson) included.
    """
    # Check for shaded JAR first (includes all dependencies)
    shaded_core_jar = f"../core/target/proj4sedona-{PROJECT_VERSION}.jar"
    original_core_jar = f"../core/target/original-proj4sedona-{PROJECT_VERSION}.jar"
    mgrs_jar = f"../mgrs/target/proj4sedona-mgrs-{PROJECT_VERSION}.jar"
    wkt_jar = f"../wkt-parser/target/wkt-parser-{PROJECT_VERSION}.jar"
    
    # Use shaded JAR if available (has original- prefix when shaded)
    if os.path.exists(original_core_jar):
        # Shaded build - use the shaded JAR (includes all dependencies)
        core_jar = shaded_core_jar
    else:
        # Regular build - use standard JAR
        core_jar = shaded_core_jar
    
    # Check if JARs exist
    for jar in [core_jar, mgrs_jar, wkt_jar]:
        if not os.path.exists(jar):
            pytest.skip(f"Required JAR file not found: {jar}. Run 'mvn clean package -Pshaded -DskipTests'")
    
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
            "name": "WGS84 to NAD83 Vermont (TM)",
            "epsg_from": "EPSG:4326",
            "epsg_to": "EPSG:32145",
            "cache_type": "local",
            "test_points": [
                (-72.5, 44.0),   # Montpelier, VT (capital)
                (-73.2, 44.5),   # Burlington area, VT
                (-72.0, 43.5),   # Southern VT
                (-72.8, 42.7),   # Bennington, VT
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
        "NAD83_Vermont": """
PROJCRS["NAD83 / Vermont",
    BASEGEOGCRS["NAD83",
        DATUM["North American Datum 1983",
            ELLIPSOID["GRS 1980",6378137,298.257222101,
                LENGTHUNIT["metre",1]]],
        PRIMEM["Greenwich",0,
            ANGLEUNIT["degree",0.0174532925199433]]],
    CONVERSION["SPCS83 Vermont zone (meter)",
        METHOD["Transverse Mercator"],
        PARAMETER["Latitude of natural origin",42.5,
            ANGLEUNIT["degree",0.0174532925199433]],
        PARAMETER["Longitude of natural origin",-72.5,
            ANGLEUNIT["degree",0.0174532925199433]],
        PARAMETER["Scale factor at natural origin",0.999964286,
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
        "NAD83_Vermont": "EPSG:32145",
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
        # For file-based approach, return the key names instead of full WKT2 strings
        from_crs = scenario.get('wkt2_from', 'WGS84')
        to_crs = scenario.get('wkt2_to', 'WebMercator')
    elif crs_format == "projjson":
        from_crs = projjson_defs.get(scenario.get('projjson_from', 'WGS84'), {})
        to_crs = projjson_defs.get(scenario.get('projjson_to', 'WebMercator'), {})
    else:
        raise ValueError(f"Unsupported CRS format: {crs_format}")
    
    return from_crs, to_crs


def map_scenario_to_crs_format(scenario, crs_format):
    """Map scenario EPSG codes to WKT2/PROJJSON format keys.
    
    Shared helper function used by both performance and accuracy tests.
    """
    mapped_scenario = scenario.copy()
    
    # Map EPSG codes to CRS format keys
    epsg_mapping = {
        "EPSG:4326": "WGS84",
        "EPSG:3857": "WebMercator",
        "EPSG:32619": "UTM_19N",
        "EPSG:32145": "NAD83_Vermont",
        "EPSG:4269": "NAD83"
    }
    
    if crs_format == "wkt2":
        mapped_scenario['wkt2_from'] = epsg_mapping.get(scenario['epsg_from'], 'WGS84')
        mapped_scenario['wkt2_to'] = epsg_mapping.get(scenario['epsg_to'], 'WebMercator')
    elif crs_format == "projjson":
        mapped_scenario['projjson_from'] = epsg_mapping.get(scenario['epsg_from'], 'WGS84')
        mapped_scenario['projjson_to'] = epsg_mapping.get(scenario['epsg_to'], 'WebMercator')
    
    return mapped_scenario


@pytest.fixture
def java_runner(proj4sedona_jar_path):
    """Create a unified Java runner for Proj4Sedona benchmarks."""
    
    class JavaRunner:
        def __init__(self, jar_paths):
            self.jar_paths = jar_paths
            self.classpath = ":".join(jar_paths.values())
            self.template_loader = TemplateLoader()
        
        def run_benchmark(self, scenario, iterations=10000, crs_format="epsg", wkt2_defs=None, projjson_defs=None):
            """Run a Java benchmark for a given scenario."""
            
            # Get CRS strings based on format
            from_crs, to_crs = _get_crs_strings(scenario, crs_format, wkt2_defs, projjson_defs)
            
            # Convert projjson dict to JSON string for Java
            if crs_format == "projjson":
                import json
                from_crs = json.dumps(from_crs)
                to_crs = json.dumps(to_crs)
            
            # Build test points string
            test_points_str = ""
            for i, (x, y) in enumerate(scenario['test_points']):
                test_points_str += f'            new Point({x}, {y})'
                if i < len(scenario['test_points']) - 1:
                    test_points_str += ','
                test_points_str += '\n'
            
            # Load and fill template
            template = self.template_loader.load_java_benchmark_template()
            java_code = self.template_loader.fill_template(
                template,
                FROM_CRS=_escape_java_string(from_crs),
                TO_CRS=_escape_java_string(to_crs),
                ITERATIONS=iterations,
                TEST_POINTS=test_points_str,
                SCENARIO_NAME=scenario['name'],
                CRS_FORMAT=crs_format
            )
            
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
def python_runner():
    """Create a Python runner for pyproj benchmarks."""
    
    class PythonRunner:
        def __init__(self):
            self.template_loader = TemplateLoader()
        
        def run_benchmark(self, scenario, iterations=10000, crs_format="epsg", wkt2_defs=None, projjson_defs=None):
            """Run a Python benchmark for a given scenario."""
            
            # Get CRS strings based on format
            from_crs, to_crs = _get_crs_strings(scenario, crs_format, wkt2_defs, projjson_defs)
            
            # Load and fill template
            template = self.template_loader.load_python_benchmark_template()
            python_code = self.template_loader.fill_template(
                template,
                FROM_CRS=repr(from_crs),
                TO_CRS=repr(to_crs),
                ITERATIONS=iterations,
                TEST_POINTS=scenario['test_points'],
                SCENARIO_NAME=scenario['name'],
                CRS_FORMAT=crs_format
            )
            
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
def batch_java_runner(proj4sedona_jar_path):
    """Create a Java runner for batch transformation benchmarks."""
    
    class BatchJavaRunner:
        def __init__(self, jar_paths):
            self.jar_paths = jar_paths
            self.classpath = ":".join(jar_paths.values())
            self.template_loader = TemplateLoader()
        
        def run_batch_benchmark(self, scenario, batch_size, iterations=1000, crs_format="epsg", wkt2_defs=None, projjson_defs=None):
            """Run a Java batch transformation benchmark."""
            
            # Get CRS strings based on format
            from_crs, to_crs = _get_crs_strings(scenario, crs_format, wkt2_defs, projjson_defs)
            
            # Convert projjson dict to JSON string for Java
            if crs_format == "projjson":
                import json
                from_crs = json.dumps(from_crs)
                to_crs = json.dumps(to_crs)
            
            # Prepare base points array for efficient initialization
            base_points = scenario['test_points']
            
            # Build base points string
            base_points_str = ""
            for i, (x, y) in enumerate(base_points):
                base_points_str += f'            new Point({x}, {y})'
                if i < len(base_points) - 1:
                    base_points_str += ','
                base_points_str += '\n'
            
            # Load and fill template
            template = self.template_loader.load_java_batch_benchmark_template()
            java_code = self.template_loader.fill_template(
                template,
                CLASS_NAME="{CLASS_NAME}",  # Will be replaced later with actual class name
                FROM_CRS=_escape_java_string(from_crs),
                TO_CRS=_escape_java_string(to_crs),
                BATCH_SIZE=batch_size,
                ITERATIONS=iterations,
                BASE_POINTS=base_points_str,
                SCENARIO_NAME=scenario['name'],
                CRS_FORMAT=crs_format
            )
            
            # Write and compile Java code
            with tempfile.NamedTemporaryFile(mode='w', suffix='.java', delete=False, dir='.') as f:
                java_file_path = f.name
                java_file = os.path.basename(java_file_path)  # Get just the filename
                class_name = java_file.replace('.java', '')
                
                # Substitute class name in the code
                final_java_code = java_code.replace('{CLASS_NAME}', class_name)
                f.write(final_java_code)
            
            try:
                
                # Compile
                compile_result = subprocess.run(
                    ['javac', '-cp', self.classpath, java_file],
                    capture_output=True,
                    text=True
                )
                
                if compile_result.returncode != 0:
                    return {
                        "output": "",
                        "error": f"Compilation failed: {compile_result.stderr}",
                        "returncode": compile_result.returncode
                    }
                
                # Run
                run_result = subprocess.run(
                    ['java', '-cp', f'{self.classpath}:.', class_name],
                    capture_output=True,
                    text=True
                )
                
                return {
                    "output": run_result.stdout,
                    "error": run_result.stderr,
                    "returncode": run_result.returncode
                }
                
            finally:
                # Clean up
                try:
                    os.unlink(java_file)
                    class_file = java_file.replace('.java', '.class')
                    if os.path.exists(class_file):
                        os.unlink(class_file)
                except OSError:
                    pass
    
    return BatchJavaRunner(proj4sedona_jar_path)


@pytest.fixture
def batch_python_runner():
    """Create a Python runner for batch transformation benchmarks."""
    
    class BatchPythonRunner:
        def __init__(self):
            self.template_loader = TemplateLoader()
        
        def run_batch_benchmark(self, scenario, batch_size, iterations=1000, crs_format="epsg", wkt2_defs=None, projjson_defs=None):
            """Run a Python batch transformation benchmark."""
            
            # Get CRS strings based on format
            from_crs, to_crs = _get_crs_strings(scenario, crs_format, wkt2_defs, projjson_defs)
            
            # Generate test points for batch
            test_points = scenario['test_points'] * (batch_size // len(scenario['test_points']) + 1)
            test_points = test_points[:batch_size]
            
            # Load and fill template
            template = self.template_loader.load_python_batch_benchmark_template()
            python_code = self.template_loader.fill_template(
                template,
                FROM_CRS=repr(from_crs),
                TO_CRS=repr(to_crs),
                BATCH_SIZE=batch_size,
                ITERATIONS=iterations,
                TEST_POINTS=test_points,
                SCENARIO_NAME=scenario['name'],
                CRS_FORMAT=crs_format
            )
            
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


def pytest_configure(config):
    """Configure pytest with custom markers."""
    config.addinivalue_line(
        "markers", "performance: mark test as performance benchmark"
    )
    config.addinivalue_line(
        "markers", "accuracy: mark test as accuracy benchmark"
    )
