#!/bin/bash
#
# Integration Test Runner for proj4sedona
#
# This script orchestrates the integration tests that compare proj4sedona with pyproj.
# It can regenerate reference data, run Java integration tests, and run benchmarks.
#
# Usage: ./scripts/run_integration_tests.sh [OPTIONS]
#
# Options:
#   --regenerate     Regenerate pyproj reference data (requires Python + pyproj)
#   --benchmark      Run performance benchmarks and generate comparison report
#   --skip-java      Skip Java tests (useful for regenerating reference data only)
#   --skip-python    Skip Python reference generation (use existing reference data)
#   --help           Show this help message
#
# Examples:
#   ./scripts/run_integration_tests.sh                    # Run integration tests only
#   ./scripts/run_integration_tests.sh --regenerate       # Regenerate reference data then test
#   ./scripts/run_integration_tests.sh --benchmark        # Run tests with benchmarks
#   ./scripts/run_integration_tests.sh --regenerate --benchmark  # Full run

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PYPROJ_SCRIPTS_DIR="$SCRIPT_DIR/pyproj-reference"
REFERENCE_DATA_DIR="$PROJECT_ROOT/src/test/resources/pyproj-reference"

# Default options
REGENERATE=false
BENCHMARK=false
SKIP_JAVA=false
SKIP_PYTHON=false

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --regenerate)
            REGENERATE=true
            shift
            ;;
        --benchmark)
            BENCHMARK=true
            shift
            ;;
        --skip-java)
            SKIP_JAVA=true
            shift
            ;;
        --skip-python)
            SKIP_PYTHON=true
            shift
            ;;
        --help)
            head -30 "$0" | tail -25
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Print banner
echo -e "${BLUE}================================================${NC}"
echo -e "${BLUE}  proj4sedona Integration Test Runner${NC}"
echo -e "${BLUE}================================================${NC}"
echo ""

# Function to check if a command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Function to print step header
print_step() {
    echo ""
    echo -e "${GREEN}>>> $1${NC}"
    echo ""
}

# Function to print warning
print_warning() {
    echo -e "${YELLOW}WARNING: $1${NC}"
}

# Function to print error
print_error() {
    echo -e "${RED}ERROR: $1${NC}"
}

# Check prerequisites
print_step "Checking prerequisites..."

if ! command_exists java; then
    print_error "Java is not installed. Please install Java 11 or higher."
    exit 1
fi

if ! command_exists mvn; then
    print_error "Maven is not installed. Please install Maven 3.6 or higher."
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
echo "Java version: $JAVA_VERSION"

if [[ "$REGENERATE" == true ]] || [[ "$BENCHMARK" == true ]]; then
    if ! command_exists python3; then
        print_error "Python 3 is not installed. Required for --regenerate and --benchmark."
        exit 1
    fi
    echo "Python version: $(python3 --version)"
fi

# Change to project root
cd "$PROJECT_ROOT"

# Step 1: Regenerate reference data if requested
if [[ "$REGENERATE" == true ]] && [[ "$SKIP_PYTHON" == false ]]; then
    print_step "Regenerating pyproj reference data..."
    
    # Check if pyproj is installed
    if ! python3 -c "import pyproj" 2>/dev/null; then
        print_warning "pyproj not installed. Installing dependencies..."
        pip3 install -r "$PYPROJ_SCRIPTS_DIR/requirements.txt"
    fi
    
    # Run the generator script
    python3 "$PYPROJ_SCRIPTS_DIR/generate_all.py" --output-dir "$REFERENCE_DATA_DIR"
    
    echo -e "${GREEN}Reference data generated successfully!${NC}"
fi

# Step 2: Run Java integration tests
if [[ "$SKIP_JAVA" == false ]]; then
    print_step "Running Java integration tests..."
    
    # Check if reference data exists
    if [[ ! -f "$REFERENCE_DATA_DIR/transform_reference.json" ]]; then
        print_warning "Reference data not found. Using pre-committed reference data or regenerate with --regenerate"
    fi
    
    # Run Maven with integration test profile
    mvn verify -Pintegration-tests -DskipUnitTests=true
    
    echo -e "${GREEN}Java integration tests completed!${NC}"
fi

# Step 3: Run benchmarks if requested
if [[ "$BENCHMARK" == true ]]; then
    print_step "Running performance benchmarks..."
    
    # Run pyproj benchmarks
    if [[ "$SKIP_PYTHON" == false ]]; then
        echo "Running pyproj benchmarks..."
        python3 "$PYPROJ_SCRIPTS_DIR/run_pyproj_benchmarks.py" --output "$REFERENCE_DATA_DIR/pyproj_benchmark_results.json"
    fi
    
    # Run Java JMH benchmarks
    if [[ "$SKIP_JAVA" == false ]]; then
        echo "Running Java JMH benchmarks..."
        mvn exec:java -Dexec.mainClass="org.datasyslab.proj4sedona.integration.IntegrationBenchmark" \
            -Dexec.classpathScope=test \
            -Dexec.args="--output $PROJECT_ROOT/target/java_benchmark_results.json"
    fi
    
    # Generate comparison report
    echo "Generating benchmark comparison report..."
    python3 "$PYPROJ_SCRIPTS_DIR/compare_benchmarks.py" \
        --pyproj "$REFERENCE_DATA_DIR/pyproj_benchmark_results.json" \
        --java "$PROJECT_ROOT/target/java_benchmark_results.json" \
        --output "$PROJECT_ROOT/target/benchmark_comparison_report.md"
    
    echo -e "${GREEN}Benchmark comparison report: target/benchmark_comparison_report.md${NC}"
fi

# Summary
echo ""
echo -e "${BLUE}================================================${NC}"
echo -e "${GREEN}  Integration tests completed successfully!${NC}"
echo -e "${BLUE}================================================${NC}"
echo ""
echo "Summary:"
echo "  - Reference data: $REFERENCE_DATA_DIR"
if [[ "$SKIP_JAVA" == false ]]; then
    echo "  - Test reports: target/failsafe-reports/"
fi
if [[ "$BENCHMARK" == true ]]; then
    echo "  - Benchmark report: target/benchmark_comparison_report.md"
fi
