import pyproj
import time
import sys
import json

def run_pyproj_batch_benchmark():
    from_crs = {FROM_CRS}
    to_crs = {TO_CRS}
    batch_size = {BATCH_SIZE}
    iterations = {ITERATIONS}
    
    # Test points
    test_points = {TEST_POINTS}
    
    print("=== pyproj Batch Benchmark ===")
    print(f"Scenario: {SCENARIO_NAME}")
    print(f"CRS Format: {CRS_FORMAT}")
    print(f"From CRS: {from_crs}")
    print(f"To CRS: {to_crs}")
    print(f"Batch Size: {batch_size}")
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
        
        # Batch transformation test
        print("\nBatch Performance Test:")
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
        
        print(f"Total time: {total_time_ms:.2f} ms")
        print(f"Average per transformation: {avg_time_ms:.6f} ms")
        print(f"Transformations per second: {tps:.0f}")
        print(f"Success rate: {success_count / (iterations * batch_size) * 100:.2f}%")
        
    except Exception as e:
        print(f"Error in batch benchmark: {e}")

if __name__ == "__main__":
    run_pyproj_batch_benchmark()

