import pyproj
import time
import sys
import json

def run_pyproj_benchmark():
    from_crs = {FROM_CRS}
    to_crs = {TO_CRS}
    iterations = {ITERATIONS}
    
    # Test points
    test_points = {TEST_POINTS}
    
    print("=== pyproj Benchmark ===")
    print(f"Scenario: {SCENARIO_NAME}")
    print(f"CRS Format: {CRS_FORMAT}")
    print(f"From CRS: {str(from_crs)[:100]}...")  # Truncate for display
    print(f"To CRS: {str(to_crs)[:100]}...")
    print(f"Test Points: {len(test_points)}")
    print(f"Iterations: {iterations}")
    
    try:
        # Create transformer based on CRS format
        if "{CRS_FORMAT}" == "epsg":
            transformer = pyproj.Transformer.from_crs(from_crs, to_crs, always_xy=True)
        elif "{CRS_FORMAT}" == "wkt2":
            # Map WKT2 keys to filenames
            wkt2_files = {
                "WGS84": "data/wkt2/wgs84.wkt",
                "WebMercator": "data/wkt2/webmercator.wkt",
                "UTM_19N": "data/wkt2/utm_19n.wkt",
                "NAD83_Vermont": "data/wkt2/nad83_vermont.wkt",
                "NAD83": "data/wkt2/nad83.wkt"
            }
            
            # Read WKT2 from files
            with open(wkt2_files.get(from_crs, "data/wkt2/wgs84.wkt"), 'r') as f:
                from_wkt2 = f.read()
            with open(wkt2_files.get(to_crs, "data/wkt2/webmercator.wkt"), 'r') as f:
                to_wkt2 = f.read()
            
            from_crs_obj = pyproj.CRS.from_wkt(from_wkt2)
            to_crs_obj = pyproj.CRS.from_wkt(to_wkt2)
            transformer = pyproj.Transformer.from_crs(from_crs_obj, to_crs_obj, always_xy=True)
        elif "{CRS_FORMAT}" == "projjson":
            # Try to create CRS from PROJJSON dict
            try:
                from_crs_obj = pyproj.CRS.from_json_dict(from_crs)
                to_crs_obj = pyproj.CRS.from_json_dict(to_crs)
                transformer = pyproj.Transformer.from_crs(from_crs_obj, to_crs_obj, always_xy=True)
            except Exception as e:
                print(f"\nError creating CRS from PROJJSON: {e}")
                print("PROJJSON format may be incomplete or not fully supported by pyproj")
                print("\nPerformance Test:")
                print("Total time: 0.00 ms")
                print("Average per transformation: 0.000000 ms")
                print("Transformations per second: 0")
                print("Success rate: 0.00%")
                sys.exit(0)
        
        # Accuracy test - single transformation
        print("\nAccuracy Test Results:")
        for i, (x, y) in enumerate(test_points):
            try:
                x_out, y_out = transformer.transform(x, y)
                print(f"Point {i+1}: ({x:.6f}, {y:.6f}) -> ({x_out:.6f}, {y_out:.6f})")
            except Exception as e:
                print(f"Error transforming point ({x}, {y}): {e}")
        
        # Performance test
        print("\nPerformance Test:")
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
        
        print(f"Total time: {total_time_ms:.2f} ms")
        print(f"Average per transformation: {avg_time_ms:.6f} ms")
        print(f"Transformations per second: {tps:.0f}")
        print(f"Success rate: {success_count / (iterations * len(test_points)) * 100:.2f}%")
        
        
    except Exception as e:
        print(f"Error in benchmark: {e}")

if __name__ == "__main__":
    run_pyproj_benchmark()

