# Proj4Sedona vs pyproj Benchmark Suite

A comprehensive pytest-based benchmark suite to compare the performance and accuracy of **Proj4Sedona** (Java) with **pyproj** (Python) for coordinate system transformations.

## 🎯 Overview

This benchmark suite provides:
- **Performance Testing**: Speed and throughput comparison
- **Accuracy Testing**: Coordinate transformation precision validation
- **Memory Analysis**: RAM consumption during operations
- **Integration Testing**: Full workflow validation
- **Concurrent Testing**: Multi-threaded execution validation

## 📁 Structure

```
benchmark/
├── conftest.py              # Pytest configuration and fixtures
├── pytest.ini              # Pytest settings
├── requirements.txt         # Python dependencies
├── run_benchmarks.py        # Main benchmark runner
├── README.md               # This file
├── tests/                  # Test modules
│   ├── test_performance.py # Performance benchmarks
│   ├── test_accuracy.py    # Accuracy benchmarks
├── fixtures/               # Test fixtures and data
├── data/                   # Test data files
└── reports/                # Generated reports
```

## 🚀 Quick Start

### Prerequisites

1. **Java 11+** (for Proj4Sedona)
2. **Python 3.11+** (for pyproj)
3. **uv** (fast Python package manager)
4. **Maven** (to build Proj4Sedona)
5. **Compiled JAR files** (see below)

### Setup

1. **Build Proj4Sedona JARs with shaded profile**:
   ```bash
   cd /path/to/proj4sedona
   mvn clean package -Pshaded -DskipTests
   ```
   
   **Note**: The `-Pshaded` profile creates a shaded JAR that includes all dependencies, which is required for the benchmarks to work properly.

2. **Install Python dependencies with uv**:
   ```bash
   cd benchmark
   uv sync
   ```

3. **Run benchmarks**:
   ```bash
   uv run python run_benchmarks.py
   # or use the script directly
   uv run run_benchmarks.py
   ```

## 🧪 Test Categories

### Performance Tests (`test_performance.py`)
- **Basic Performance**: Speed and throughput comparison
- **Detailed Performance**: Extended testing with more iterations
- **Performance Comparison**: Side-by-side analysis

### Accuracy Tests (`test_accuracy.py`)
- **Coordinate Precision**: Decimal place accuracy validation
- **Accuracy Comparison**: Direct coordinate comparison
- **Edge Case Testing**: Boundary condition testing


## 🎛️ Running Benchmarks

### Default Behavior
- **50,000 iterations** by default for comprehensive testing
- **Verbose output** by default for detailed results
- **All test types** by default: performance, accuracy
- **Single worker** by default (use `--parallel N` for parallel execution)

### Basic Usage

```bash
# Run all benchmarks (default: 50,000 iterations, verbose output, all test types)
uv run python run_benchmarks.py

# Run only performance tests
uv run python run_benchmarks.py --markers performance

# Run only accuracy tests
uv run python run_benchmarks.py --markers accuracy

# Run with custom iterations
uv run python run_benchmarks.py --iterations 100000

# Run in parallel
uv run python run_benchmarks.py --parallel 4

# Run accuracy tests
uv run python run_benchmarks.py --markers accuracy
uv run python run_benchmarks.py --markers wkt2
uv run python run_benchmarks.py --markers projjson
uv run python run_benchmarks.py --markers datum_shift
uv run python run_benchmarks.py --markers batch
```

### Sample Output

The benchmark runner now generates a clean, informative terminal report:

```
================================================================================
🚀 Proj4Sedona vs pyproj Benchmark Suite
================================================================================
📅 Started at: 2025-10-19 00:21:11

📊 Running benchmarks with 10000 iterations
🎯 Test categories: performance, accuracy
⚡ Parallel workers: 1

🧪 Running Performance Tests...
✅ Performance Tests completed in 2.34s

🧪 Running Accuracy Tests...
✅ Accuracy Tests completed in 0.91s

📈 Performance Summary
------------------------------------------------------------

📊 Performance Comparison Table
====================================================================================================
Test Scenario             Proj4Sedona (Java)  pyproj (Python)     Speed Ratio      Winner    
====================================================================================================
Wgs84 To Web Mercator     1,234,567 TPS       456,789 TPS         2.7x faster      🔵 Java   
Wgs84 To Utm Zone 32n     987,654 TPS         321,456 TPS         3.1x faster      🔵 Java   
Utm Zone 32n To Wgs84     876,543 TPS         234,567 TPS         3.7x faster      🔵 Java   
Web Mercator To Wgs84     654,321 TPS         123,456 TPS         5.3x faster      🔵 Java   
Wgs84 To Lambert Conic    543,210 TPS         98,765 TPS          5.5x faster      🔵 Java   
====================================================================================================

📊 Overall Performance Summary:
   🔵 Proj4Sedona Average: 859,259 TPS
   🐍 pyproj Average: 246,806 TPS
   🏆 Proj4Sedona is 3.5x faster overall

📋 Detailed Results:
------------------------------------------------------------

🔵 Proj4Sedona (Java) Details:
  WGS84_to_WebMercator_java:
    🚀 Throughput: 1,234,567 TPS
    ⏱️  Avg Time: 0.000810 ms
    ✅ Success Rate: 100.0%
    💾 Memory: 45.2 MB

🐍 pyproj (Python) Details:
  WGS84_to_WebMercator_python:
    🚀 Throughput: 456,789 TPS
    ⏱️  Avg Time: 0.002190 ms
    ✅ Success Rate: 100.0%
    💾 Memory: 78.5 MB

🎯 Accuracy Summary
------------------------------------------------------------
✅ Accuracy Tests: 1/1 passed
🎯 Both libraries produce equivalent coordinate transformations
📏 Precision: 15+ decimal places
🔍 Maximum deviation: < 1e-10 meters

📋 Benchmark Summary
------------------------------------------------------------
📊 Total Tests: 2
✅ Passed: 2
❌ Failed: 0
⏱️  Total Duration: 3.25s
📈 Success Rate: 100.0%

================================================================================
🎉 Benchmark completed!
================================================================================
```

### Advanced Usage

```bash
# Verbose output
uv run python run_benchmarks.py --verbose

# Generate HTML report
uv run python run_benchmarks.py --html-report

# Generate coverage report
uv run python run_benchmarks.py --coverage
```

### Direct pytest Usage

```bash
# Run all tests
uv run pytest tests/ -v

# Run specific test file
uv run pytest tests/test_performance.py -v

# Run with markers
uv run pytest -m performance -v

# Run in parallel
uv run pytest -n 4 tests/ -v
```

## 📊 Test Scenarios

The benchmark tests **5 coordinate transformation scenarios**:

1. **WGS84 → Web Mercator (EPSG:3857)**
   - Web mapping projection
   - 7 global test points

2. **WGS84 → UTM Zone 19N (EPSG:32619)**
   - Universal Transverse Mercator
   - 4 US East Coast points

3. **WGS84 → Lambert Conformal Conic (EPSG:32145)**
   - Conic projection for mid-latitudes
   - 4 New York area points

4. **NAD83 → WGS84 (EPSG:4269 → EPSG:4326)**
   - Datum transformation
   - 4 US locations

5. **UTM → WGS84 (EPSG:32619 → EPSG:4326)**
   - Inverse UTM transformation
   - 4 UTM coordinate points

## 📈 Performance Metrics

### Key Performance Indicators
- **Transformation Speed**: Milliseconds per coordinate transformation
- **Throughput**: Transformations per second (TPS)
- **Memory Usage**: RAM consumption in MB
- **Success Rate**: Percentage of successful transformations
- **Execution Time**: Total benchmark execution time

### Accuracy Metrics
- **Coordinate Precision**: Decimal places of accuracy
- **Maximum Deviation**: Largest difference between libraries
- **Average Deviation**: Mean difference across all points
- **Systematic Bias**: Consistent offset patterns

## 🎯 Expected Results

Based on typical benchmarks:

| Metric | Proj4Sedona (Java) | pyproj (Python) | Winner |
|--------|-------------------|------------------|---------|
| **Speed** | ~0.001-0.005 ms/transform | ~0.002-0.008 ms/transform | **Java (2-4x faster)** |
| **Throughput** | ~200,000-1,000,000 tps | ~125,000-500,000 tps | **Java (2-4x higher)** |
| **Memory** | ~50-200 MB | ~100-400 MB | **Java (lower usage)** |
| **Accuracy** | 15+ decimal places | 15+ decimal places | **Tie** |

## 🔧 Configuration

### Project Configuration
The project uses `pyproject.toml` for configuration:

```toml
[project]
name = "proj4sedona-benchmark"
version = "1.0.0"
description = "Comprehensive benchmark suite to compare Proj4Sedona (Java) with pyproj (Python)"
requires-python = ">=3.11"
dependencies = [
    "psutil>=7.1.0",
    "pyproj>=3.7.2", 
    "pytest>=8.4.2",
    "pytest-cov>=7.0.0",
    "pytest-html>=4.1.1",
    "pytest-timeout>=2.4.0",
    "pytest-xdist>=3.8.0",
]
```

### Environment Variables
- `BENCHMARK_ITERATIONS`: Number of benchmark iterations (default: 10000)
- `JAVA_OPTS`: Java virtual machine options
- `PYTHONPATH`: Python module search path

### Pytest Configuration
Edit `pyproject.toml` under `[tool.pytest.ini_options]` to customize:
- Test discovery patterns
- Output formatting
- Timeout settings
- Logging configuration

### Test Markers
- `@pytest.mark.performance`: Performance benchmark tests
- `@pytest.mark.accuracy`: Accuracy benchmark tests
- `@pytest.mark.slow`: Slow running tests
- `@pytest.mark.concurrent`: Concurrent execution tests
- `@pytest.mark.local_cache`: Tests using local cache (no external downloads)
- `@pytest.mark.cdn_grid`: Tests using PROJ CDN grid files (datum shift transformations)

## 📋 Reports

### HTML Reports
```bash
python run_benchmarks.py --html-report
```
Generates: `reports/benchmark_report.html`

### Coverage Reports
```bash
python run_benchmarks.py --coverage
```
Generates: `reports/coverage/index.html`

### JSON Reports
```bash
pytest --json-report --json-report-file=reports/benchmark_results.json
```

## 🐛 Troubleshooting

### Common Issues

1. **JAR Files Not Found**
   ```bash
   # Ensure JARs are built
   mvn clean package -DskipTests
   ```

2. **Python Dependencies Missing**
   ```bash
   pip install -r requirements.txt
   ```

3. **Java Not Found**
   ```bash
   # Check Java version
   java -version
   # Set JAVA_HOME if needed
   export JAVA_HOME=/path/to/java
   ```

4. **Memory Issues**
   ```bash
   # Increase Java heap size
   export JAVA_OPTS="-Xmx4g"
   ```

5. **Timeout Errors**
   ```bash
   # Increase timeout in pytest.ini
   timeout = 600
   ```

### Debug Mode

```bash
# Run with debug output
pytest tests/ -v -s --tb=long

# Run single test
pytest tests/test_performance.py::TestPerformanceBenchmarks::test_java_performance -v

# Run with pdb debugger
pytest tests/ --pdb
```

## 🔄 CI/CD Integration

### GitHub Actions Example

```yaml
name: Benchmark Tests
on: [push, pull_request]
jobs:
  benchmark:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    - uses: actions/setup-java@v3
      with:
        java-version: '11'
    - uses: actions/setup-python@v4
      with:
        python-version: '3.9'
    - name: Build Proj4Sedona
      run: mvn clean package -DskipTests
    - name: Install Python dependencies
      run: pip install -r benchmark/requirements.txt
    - name: Run benchmarks
      run: cd benchmark && python run_benchmarks.py --html-report
    - name: Upload reports
      uses: actions/upload-artifact@v3
      with:
        name: benchmark-reports
        path: benchmark/reports/
```

## 📚 API Reference

### Fixtures

- `proj4sedona_jar_path`: Paths to compiled JAR files
- `pyproj_version`: pyproj library version
- `test_scenarios`: Coordinate transformation test scenarios
- `benchmark_iterations`: Number of benchmark iterations
- `java_runner`: Java benchmark runner
- `python_runner`: Python benchmark runner

### Test Classes

- `TestPerformanceBenchmarks`: Performance testing
- `TestAccuracyBenchmarks`: Accuracy validation
- `TestIntegrationBenchmarks`: Integration testing
- `TestEdgeCaseAccuracy`: Edge case testing

## 🤝 Contributing

1. **Add New Test Scenarios**: Edit `conftest.py` to add new coordinate transformations
2. **Add New Metrics**: Extend test classes to measure additional performance indicators
3. **Add New Test Cases**: Create new test methods for specific use cases
4. **Improve Reporting**: Enhance report generation and visualization

## 📄 License

This benchmark suite is part of the Proj4Sedona project and follows the same Apache 2.0 license.

## 🆘 Support

For issues with the benchmark suite:
1. Check the troubleshooting section above
2. Review pytest output for error messages
3. Ensure all dependencies are properly installed
4. Verify the Proj4Sedona project builds successfully

For Proj4Sedona-specific issues, refer to the main project documentation.
