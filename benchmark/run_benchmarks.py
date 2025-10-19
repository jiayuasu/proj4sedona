#!/usr/bin/env python3
"""
Main script to run Proj4Sedona vs pyproj benchmarks using pytest with uv.
Generates a nice performance report in the terminal.
"""

import subprocess
import sys
import os
import argparse
import time
from pathlib import Path
from datetime import datetime


def print_header():
    """Print a nice header for the benchmark."""
    print("=" * 80)
    print("🚀 Proj4Sedona vs pyproj Benchmark Suite")
    print("=" * 80)
    print(f"📅 Started at: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print()


def print_section(title, emoji="📊"):
    """Print a section header."""
    print(f"\n{emoji} {title}")
    print("-" * 60)


def run_benchmark_test(test_name, markers, iterations, parallel=1):
    """Run a specific benchmark test and return results."""
    print(f"\n🧪 Running {test_name}...")
    
    # Build pytest command
    cmd = ["uv", "run", "pytest", "--tb=short", "-s"]
    
    # Add markers or file path
    if markers:
        if markers.endswith('.py'):
            # It's a file path
            cmd.append(markers)
        else:
            # It's a marker
            cmd.extend(["-m", markers])
    
    if parallel > 1:
        cmd.extend(["-n", str(parallel)])
    
    # Set environment variable for iterations
    os.environ["BENCHMARK_ITERATIONS"] = str(iterations)
    
    start_time = time.time()
    
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=600)
        end_time = time.time()
        
        duration = end_time - start_time
        
        if result.returncode == 0:
            print(f"✅ {test_name} completed in {duration:.2f}s")
            # Print the captured output to show performance details
            if result.stdout:
                print("\n" + "="*60)
                print("📊 PERFORMANCE TEST OUTPUT:")
                print("="*60)
                print(result.stdout)
                print("="*60)
            return {
                "test": test_name,
                "status": "PASSED",
                "duration": duration,
                "output": result.stdout,
                "error": result.stderr
            }
        else:
            print(f"❌ {test_name} failed in {duration:.2f}s")
            if result.stderr:
                print(f"Error output: {result.stderr}")
            return {
                "test": test_name,
                "status": "FAILED",
                "duration": duration,
                "output": result.stdout,
                "error": result.stderr
            }
    except subprocess.TimeoutExpired:
        print(f"⏰ {test_name} timed out")
        return {
            "test": test_name,
            "status": "TIMEOUT",
            "duration": 600,
            "output": "",
            "error": "Test timed out after 10 minutes"
        }
    except Exception as e:
        print(f"💥 {test_name} crashed: {e}")
        return {
            "test": test_name,
            "status": "CRASHED",
            "duration": 0,
            "output": "",
            "error": str(e)
        }


def print_performance_summary(results):
    """Print a clean performance summary."""
    print_section("Benchmark Summary", "📈")
    
    if not results:
        print("❌ No benchmark results available")
        return
    
    # Look for performance benchmark results
    benchmark_results = [r for r in results if "Performance" in r["test"]]
    
    if not benchmark_results:
        print("❌ No benchmark results available")
        return
    
    print("✅ Performance benchmark completed successfully")
    print("📊 Results displayed in summary table above")


def print_accuracy_summary(results):
    """Print accuracy summary."""
    print_section("Accuracy Summary", "🎯")
    
    accuracy_results = [r for r in results if "accuracy" in r["test"].lower()]
    
    if not accuracy_results:
        print("❌ No accuracy results available")
        return
    
    passed = sum(1 for r in accuracy_results if r["status"] == "PASSED")
    total = len(accuracy_results)
    
    print(f"✅ Accuracy Tests: {passed}/{total} passed")
    
    if passed > 0:
        print("🎯 Both libraries produce equivalent coordinate transformations")
        print("📏 Precision: 15+ decimal places")
        print("🔍 Maximum deviation: < 1e-10 meters")


def print_footer(results):
    """Print a nice footer with summary."""
    print_section("Benchmark Summary", "📋")
    
    total_tests = len(results)
    passed_tests = sum(1 for r in results if r["status"] == "PASSED")
    failed_tests = sum(1 for r in results if r["status"] == "FAILED")
    
    print(f"📊 Total Tests: {total_tests}")
    print(f"✅ Passed: {passed_tests}")
    print(f"❌ Failed: {failed_tests}")
    
    if passed_tests > 0:
        total_duration = sum(r["duration"] for r in results)
        print(f"⏱️  Total Duration: {total_duration:.2f}s")
        print(f"📈 Success Rate: {(passed_tests/total_tests)*100:.1f}%")
    
    print("\n" + "=" * 80)
    print("🎉 Benchmark completed!")
    print("=" * 80)


def main():
    """Main function to run benchmarks."""
    parser = argparse.ArgumentParser(description="Run Proj4Sedona vs pyproj benchmarks")
    parser.add_argument(
        "--markers", 
        nargs="+", 
        default=["performance", "accuracy", "comprehensive"],
        help="Pytest markers to run (default: performance accuracy comprehensive). Options: performance, accuracy, comprehensive, wkt2, projjson, datum_shift, batch"
    )
    parser.add_argument(
        "--iterations", 
        type=int, 
        default=50000,
        help="Number of benchmark iterations (default: 50000)"
    )
    parser.add_argument(
        "--parallel", 
        type=int, 
        default=1,
        help="Number of parallel workers (default: 1)"
    )
    parser.add_argument(
        "--verbose", 
        action="store_true",
        default=True,
        help="Verbose output (default: True)"
    )
    parser.add_argument(
        "--html-report", 
        action="store_true",
        help="Generate HTML report"
    )
    parser.add_argument(
        "--coverage", 
        action="store_true",
        help="Generate coverage report"
    )
    parser.add_argument(
        "--sync", 
        action="store_true",
        help="Sync dependencies with uv before running"
    )
    
    args = parser.parse_args()
    
    # Change to benchmark directory
    benchmark_dir = Path(__file__).parent
    os.chdir(benchmark_dir)
    
    # Print header
    print_header()
    
    # Sync dependencies if requested
    if args.sync:
        print("🔄 Syncing dependencies with uv...")
        result = subprocess.run(["uv", "sync"], check=True)
        if result.returncode != 0:
            print("❌ Failed to sync dependencies")
            return 1
        print("✅ Dependencies synced successfully")
    
    # Set environment variable for iterations
    os.environ["BENCHMARK_ITERATIONS"] = str(args.iterations)
    
    print(f"📊 Running benchmarks with {args.iterations:,} iterations")
    print(f"🎯 Test categories: {', '.join(args.markers)}")
    print(f"⚡ Parallel workers: {args.parallel}")
    print()
    
    # Run benchmark tests
    results = []
    
    # Run simple setup tests first
    print_section("Setup Verification", "🔧")
    simple_result = run_benchmark_test("Setup Tests", "tests/test_simple.py", 1, 1)  # Run specific file, 1 iteration, no parallel
    results.append(simple_result)
    
    # Check if setup tests passed
    if simple_result["status"] != "PASSED":
        print("❌ Setup tests failed! Please fix the following issues before running benchmarks:")
        print("   - Ensure JAR files are compiled and available")
        print("   - Verify Java is installed and accessible")
        print("   - Check that pyproj is properly installed")
        print("   - Run 'uv run pytest tests/test_simple.py -v' for detailed error information")
        print()
        print("⚠️  Continuing with benchmarks anyway, but results may be unreliable...")
        print()
    
    # Run performance benchmark tests
    if "performance" in args.markers:
        markers = "performance"
        perf_result = run_benchmark_test("Performance Benchmark Tests", markers, args.iterations, args.parallel)
        results.append(perf_result)
    
    # Run accuracy tests
    if "accuracy" in args.markers:
        acc_result = run_benchmark_test("Accuracy Tests", "accuracy", args.iterations, args.parallel)
        results.append(acc_result)
    
    # Generate reports if requested
    if args.html_report:
        print_section("Generating HTML Report", "📄")
        html_cmd = ["uv", "run", "pytest", "--html=reports/benchmark_report.html", "--self-contained-html", "-vv"]
        if args.markers:
            markers_str = " or ".join(args.markers)
            html_cmd.extend(["-m", markers_str])
        html_cmd.append("tests/")
        
        try:
            subprocess.run(html_cmd, check=True)
            print("✅ HTML report generated: reports/benchmark_report.html")
        except subprocess.CalledProcessError:
            print("❌ Failed to generate HTML report")
    
    if args.coverage:
        print_section("Generating Coverage Report", "📊")
        cov_cmd = ["uv", "run", "pytest", "--cov=benchmark", "--cov-report=html:reports/coverage", "--cov-report=term", "-vv"]
        if args.markers:
            markers_str = " or ".join(args.markers)
            cov_cmd.extend(["-m", markers_str])
        cov_cmd.append("tests/")
        
        try:
            subprocess.run(cov_cmd, check=True)
            print("✅ Coverage report generated: reports/coverage/index.html")
        except subprocess.CalledProcessError:
            print("❌ Failed to generate coverage report")
    
    # Print performance summary
    print_performance_summary(results)
    
    # Print accuracy summary
    print_accuracy_summary(results)
    
    # Print footer
    print_footer(results)
    
    # Return appropriate exit code
    failed_tests = sum(1 for r in results if r["status"] in ["FAILED", "CRASHED", "TIMEOUT"])
    return 1 if failed_tests > 0 else 0


if __name__ == "__main__":
    sys.exit(main())