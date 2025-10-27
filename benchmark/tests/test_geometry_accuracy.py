"""
Comprehensive geometry accuracy benchmark comparing shapely+pyproj vs JTS+Proj4Sedona.
Focuses on non-point geometries (polygons, linestrings, multi-geometries).
"""

import pytest
import subprocess
import os
import sys
import tempfile
from typing import Dict, List, Tuple, Any


@pytest.mark.accuracy
class TestGeometryAccuracy:
    """Compare shapely+pyproj geometry transformations with JTS+Proj4Sedona."""

    @pytest.fixture(scope="class")
    def geometry_scenarios(self):
        """Define test scenarios for different geometry types."""
        return [
            {
                "name": "Simple Polygon - Boston Area",
                "type": "polygon",
                "coordinates": [
                    [
                        [-71.0, 41.0],
                        [-71.0, 42.0],
                        [-70.0, 42.0],
                        [-70.0, 41.0],
                        [-71.0, 41.0],
                    ]
                ],
                "from_epsg": 4326,
                "to_epsg": 3857,
            },
            {
                "name": "LineString - East Coast",
                "type": "linestring",
                "coordinates": [
                    [-71.0, 41.0],  # Boston
                    [-74.0, 40.7],  # New York
                    [-77.0, 38.9],  # Washington DC
                    [-80.2, 25.8],  # Miami
                ],
                "from_epsg": 4326,
                "to_epsg": 3857,
            },
            {
                "name": "Polygon with Hole - Massachusetts",
                "type": "polygon_with_hole",
                "coordinates": [
                    # Outer ring
                    [
                        [-72.0, 41.0],
                        [-72.0, 43.0],
                        [-70.0, 43.0],
                        [-70.0, 41.0],
                        [-72.0, 41.0],
                    ],
                    # Inner hole
                    [
                        [-71.5, 41.5],
                        [-71.5, 42.5],
                        [-70.5, 42.5],
                        [-70.5, 41.5],
                        [-71.5, 41.5],
                    ],
                ],
                "from_epsg": 4326,
                "to_epsg": 3857,
            },
            {
                "name": "MultiPolygon - New England States",
                "type": "multipolygon",
                "coordinates": [
                    # First polygon (Massachusetts)
                    [
                        [
                            [-71.0, 41.0],
                            [-71.0, 42.0],
                            [-70.0, 42.0],
                            [-70.0, 41.0],
                            [-71.0, 41.0],
                        ]
                    ],
                    # Second polygon (Vermont)
                    [
                        [
                            [-73.5, 42.5],
                            [-73.5, 43.5],
                            [-72.5, 43.5],
                            [-72.5, 42.5],
                            [-73.5, 42.5],
                        ]
                    ],
                ],
                "from_epsg": 4326,
                "to_epsg": 3857,
            },
            {
                "name": "Complex Polygon - Irregular Shape",
                "type": "polygon",
                "coordinates": [
                    [
                        [-71.0, 41.0],
                        [-71.2, 41.5],
                        [-70.8, 41.8],
                        [-70.5, 42.0],
                        [-70.2, 41.7],
                        [-70.0, 41.3],
                        [-70.5, 41.1],
                        [-71.0, 41.0],
                    ]
                ],
                "from_epsg": 4326,
                "to_epsg": 3857,
            },
            {
                "name": "LineString to UTM - Transect",
                "type": "linestring",
                "coordinates": [
                    [-71.0, 41.0],
                    [-70.5, 41.5],
                    [-70.0, 42.0],
                    [-69.5, 42.5],
                    [-69.0, 43.0],
                ],
                "from_epsg": 4326,
                "to_epsg": 32619,  # UTM Zone 19N
            },
            {
                "name": "Polygon WGS84 to NAD83 Vermont",
                "type": "polygon",
                "coordinates": [
                    [
                        [-72.5, 43.5],
                        [-72.5, 44.5],
                        [-71.5, 44.5],
                        [-71.5, 43.5],
                        [-72.5, 43.5],
                    ]
                ],
                "from_epsg": 4326,
                "to_epsg": 32145,  # NAD83 Vermont
            },
        ]

    def test_polygon_accuracy(self, geometry_scenarios, java_runner, python_runner):
        """Compare polygon transformation accuracy between shapely+pyproj and JTS+Proj4Sedona."""
        print("\n🔷 Testing Polygon Geometry Accuracy")

        for scenario in geometry_scenarios:
            if scenario["type"] not in ["polygon", "polygon_with_hole"]:
                continue

            print(f"\n📊 Testing: {scenario['name']}")

            # Run Python (shapely + pyproj)
            python_result = self._run_python_geometry_transform(scenario)

            # Run Java (JTS + Proj4Sedona)
            java_result = self._run_java_geometry_transform(scenario, java_runner)

            # Compare results
            self._compare_geometry_results(python_result, java_result, scenario)

    def test_linestring_accuracy(self, geometry_scenarios, java_runner, python_runner):
        """Compare linestring transformation accuracy between shapely+pyproj and JTS+Proj4Sedona."""
        print("\n🔷 Testing LineString Geometry Accuracy")

        for scenario in geometry_scenarios:
            if scenario["type"] != "linestring":
                continue

            print(f"\n📊 Testing: {scenario['name']}")

            # Run Python (shapely + pyproj)
            python_result = self._run_python_geometry_transform(scenario)

            # Run Java (JTS + Proj4Sedona)
            java_result = self._run_java_geometry_transform(scenario, java_runner)

            # Compare results
            self._compare_geometry_results(python_result, java_result, scenario)

    def test_multipolygon_accuracy(self, geometry_scenarios, java_runner, python_runner):
        """Compare multipolygon transformation accuracy between shapely+pyproj and JTS+Proj4Sedona."""
        print("\n🔷 Testing MultiPolygon Geometry Accuracy")

        for scenario in geometry_scenarios:
            if scenario["type"] != "multipolygon":
                continue

            print(f"\n📊 Testing: {scenario['name']}")

            # Run Python (shapely + pyproj)
            python_result = self._run_python_geometry_transform(scenario)

            # Run Java (JTS + Proj4Sedona)
            java_result = self._run_java_geometry_transform(scenario, java_runner)

            # Compare results
            self._compare_geometry_results(python_result, java_result, scenario)

    def _run_python_geometry_transform(self, scenario: Dict[str, Any]) -> Dict[str, Any]:
        """Run shapely + pyproj transformation."""
        python_code = f'''
import pyproj
from shapely.geometry import Polygon, LineString, MultiPolygon
from shapely.ops import transform
import json

# Create transformer
transformer = pyproj.Transformer.from_crs({scenario["from_epsg"]}, {scenario["to_epsg"]}, always_xy=True)

# Create geometry
geom_type = "{scenario["type"]}"
coords = {scenario["coordinates"]}

if geom_type == "polygon":
    geom = Polygon(coords[0])
elif geom_type == "polygon_with_hole":
    geom = Polygon(coords[0], [coords[1]])
elif geom_type == "linestring":
    geom = LineString(coords)
elif geom_type == "multipolygon":
    geom = MultiPolygon([Polygon(poly[0]) for poly in coords])

# Transform using shapely.ops.transform
transformed = transform(transformer.transform, geom)

# Extract coordinates from transformed geometry
result = {{}}
result["type"] = geom_type

if geom_type in ["polygon", "polygon_with_hole"]:
    result["exterior"] = list(transformed.exterior.coords)
    result["num_holes"] = len(transformed.interiors)
    if result["num_holes"] > 0:
        result["holes"] = [list(hole.coords) for hole in transformed.interiors]
elif geom_type == "linestring":
    result["coords"] = list(transformed.coords)
elif geom_type == "multipolygon":
    result["polygons"] = []
    for poly in transformed.geoms:
        result["polygons"].append({{
            "exterior": list(poly.exterior.coords),
            "num_holes": len(poly.interiors)
        }})

print(json.dumps(result))
'''

        with tempfile.NamedTemporaryFile(mode="w", suffix=".py", delete=False) as f:
            f.write(python_code)
            python_file = f.name

        try:
            result = subprocess.run(
                [sys.executable, python_file], capture_output=True, text=True, timeout=10
            )

            if result.returncode != 0:
                raise RuntimeError(f"Python transform failed: {result.stderr}")

            import json

            return json.loads(result.stdout.strip())

        finally:
            try:
                os.unlink(python_file)
            except OSError:
                pass

    def _run_java_geometry_transform(
        self, scenario: Dict[str, Any], java_runner
    ) -> Dict[str, Any]:
        """Run JTS + Proj4Sedona transformation using java_runner infrastructure."""
        # Generate Java code
        java_code = self._generate_java_transform_code(scenario)

        # Compile and run using existing java_runner pattern
        with tempfile.NamedTemporaryFile(mode="w", suffix=".java", delete=False, dir=".") as f:
            java_file_path = f.name
            java_file = os.path.basename(java_file_path)
            class_name = java_file.replace(".java", "")

            final_java_code = java_code.replace("{CLASS_NAME}", class_name)
            f.write(final_java_code)

        try:
            # Compile using java_runner classpath
            compile_cmd = ["javac", "-cp", java_runner.classpath, java_file]
            result = subprocess.run(compile_cmd, capture_output=True, text=True)

            if result.returncode != 0:
                raise RuntimeError(f"Java compilation failed: {result.stderr}")

            # Run
            class_file = java_file.replace(".java", ".class")
            run_cmd = ["java", "-cp", f"{java_runner.classpath}:.", class_name]

            result = subprocess.run(run_cmd, capture_output=True, text=True)

            if result.returncode != 0:
                raise RuntimeError(f"Java execution failed: {result.stderr}")

            import json

            return json.loads(result.stdout.strip())

        finally:
            # Clean up
            try:
                os.unlink(java_file)
                class_file = java_file.replace(".java", ".class")
                if os.path.exists(class_file):
                    os.unlink(class_file)
            except OSError:
                pass

    def _generate_java_transform_code(self, scenario: Dict[str, Any]) -> str:
        """Generate Java code for geometry transformation."""
        coords = scenario["coordinates"]
        geom_type = scenario["type"]

        java_code = f'''
import org.apache.sedona.proj.jts.JTSTransform;
import org.locationtech.jts.geom.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

public class {{CLASS_NAME}} {{
    public static void main(String[] args) throws Exception {{
        GeometryFactory gf = new GeometryFactory();
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode result = mapper.createObjectNode();
        
        result.put("type", "{geom_type}");
        
'''

        if geom_type == "polygon":
            # Simple polygon
            coords_str = self._format_java_coords(coords[0])
            java_code += f'''
        // Create polygon
        Coordinate[] coords = {coords_str};
        Polygon polygon = gf.createPolygon(coords);
        
        // Transform
        Polygon transformed = (Polygon) JTSTransform.transform(polygon, {scenario["from_epsg"]}, {scenario["to_epsg"]}, gf);
        
        // Extract coordinates
        ArrayNode exterior = mapper.createArrayNode();
        for (Coordinate c : transformed.getExteriorRing().getCoordinates()) {{
            ArrayNode coord = mapper.createArrayNode();
            coord.add(c.x);
            coord.add(c.y);
            exterior.add(coord);
        }}
        result.set("exterior", exterior);
        result.put("num_holes", transformed.getNumInteriorRing());
'''

        elif geom_type == "polygon_with_hole":
            # Polygon with hole
            shell_str = self._format_java_coords(coords[0])
            hole_str = self._format_java_coords(coords[1])
            java_code += f'''
        // Create polygon with hole
        Coordinate[] shell = {shell_str};
        Coordinate[] hole = {hole_str};
        Polygon polygon = gf.createPolygon(
            gf.createLinearRing(shell),
            new LinearRing[] {{ gf.createLinearRing(hole) }}
        );
        
        // Transform
        Polygon transformed = (Polygon) JTSTransform.transform(polygon, {scenario["from_epsg"]}, {scenario["to_epsg"]}, gf);
        
        // Extract coordinates
        ArrayNode exterior = mapper.createArrayNode();
        for (Coordinate c : transformed.getExteriorRing().getCoordinates()) {{
            ArrayNode coord = mapper.createArrayNode();
            coord.add(c.x);
            coord.add(c.y);
            exterior.add(coord);
        }}
        result.set("exterior", exterior);
        result.put("num_holes", transformed.getNumInteriorRing());
        
        // Extract hole coordinates
        if (transformed.getNumInteriorRing() > 0) {{
            ArrayNode holesArray = mapper.createArrayNode();
            for (int i = 0; i < transformed.getNumInteriorRing(); i++) {{
                ArrayNode holeCoords = mapper.createArrayNode();
                for (Coordinate c : transformed.getInteriorRingN(i).getCoordinates()) {{
                    ArrayNode coord = mapper.createArrayNode();
                    coord.add(c.x);
                    coord.add(c.y);
                    holeCoords.add(coord);
                }}
                holesArray.add(holeCoords);
            }}
            result.set("holes", holesArray);
        }}
'''

        elif geom_type == "linestring":
            coords_str = self._format_java_coords(coords)
            java_code += f'''
        // Create linestring
        Coordinate[] coords = {coords_str};
        LineString linestring = gf.createLineString(coords);
        
        // Transform
        LineString transformed = (LineString) JTSTransform.transform(linestring, {scenario["from_epsg"]}, {scenario["to_epsg"]}, gf);
        
        // Extract coordinates
        ArrayNode coordsArray = mapper.createArrayNode();
        for (Coordinate c : transformed.getCoordinates()) {{
            ArrayNode coord = mapper.createArrayNode();
            coord.add(c.x);
            coord.add(c.y);
            coordsArray.add(coord);
        }}
        result.set("coords", coordsArray);
'''

        elif geom_type == "multipolygon":
            java_code += f'''
        // Create multipolygon
        Polygon[] polygons = new Polygon[{len(coords)}];
'''
            for i, poly_coords in enumerate(coords):
                poly_str = self._format_java_coords(poly_coords[0])
                java_code += f'''
        polygons[{i}] = gf.createPolygon({poly_str});
'''

            java_code += f'''
        MultiPolygon multipolygon = gf.createMultiPolygon(polygons);
        
        // Transform
        MultiPolygon transformed = (MultiPolygon) JTSTransform.transform(multipolygon, {scenario["from_epsg"]}, {scenario["to_epsg"]}, gf);
        
        // Extract coordinates
        ArrayNode polygonsArray = mapper.createArrayNode();
        for (int i = 0; i < transformed.getNumGeometries(); i++) {{
            Polygon poly = (Polygon) transformed.getGeometryN(i);
            ObjectNode polyNode = mapper.createObjectNode();
            
            ArrayNode exterior = mapper.createArrayNode();
            for (Coordinate c : poly.getExteriorRing().getCoordinates()) {{
                ArrayNode coord = mapper.createArrayNode();
                coord.add(c.x);
                coord.add(c.y);
                exterior.add(coord);
            }}
            polyNode.set("exterior", exterior);
            polyNode.put("num_holes", poly.getNumInteriorRing());
            polygonsArray.add(polyNode);
        }}
        result.set("polygons", polygonsArray);
'''

        java_code += '''
        
        // Output JSON
        System.out.println(mapper.writeValueAsString(result));
    }
}
'''

        return java_code

    def _format_java_coords(self, coords: List[List[float]]) -> str:
        """Format coordinates as Java Coordinate array."""
        coord_strings = []
        for coord in coords:
            coord_strings.append(f"new Coordinate({coord[0]}, {coord[1]})")
        return (
            "new Coordinate[] {\n            "
            + ",\n            ".join(coord_strings)
            + "\n        }"
        )

    def _compare_geometry_results(
        self, python_result: Dict, java_result: Dict, scenario: Dict
    ):
        """Compare Python and Java geometry transformation results."""
        geom_type = scenario["type"]

        print(f"  Python type: {python_result.get('type')}")
        print(f"  Java type: {java_result.get('type')}")
        assert python_result["type"] == java_result["type"]

        if geom_type in ["polygon", "polygon_with_hole"]:
            self._compare_polygon_coords(python_result, java_result, scenario)
        elif geom_type == "linestring":
            self._compare_linestring_coords(python_result, java_result, scenario)
        elif geom_type == "multipolygon":
            self._compare_multipolygon_coords(python_result, java_result, scenario)

    def _compare_polygon_coords(
        self, python_result: Dict, java_result: Dict, scenario: Dict
    ):
        """Compare polygon coordinates."""
        py_exterior = python_result["exterior"]
        java_exterior = java_result["exterior"]

        print(f"  Exterior vertices: Python={len(py_exterior)}, Java={len(java_exterior)}")
        assert len(py_exterior) == len(java_exterior), "Different number of exterior vertices"

        # Compare each vertex
        max_diff = 0.0
        total_diff = 0.0
        vertex_diffs = []

        for i, (py_coord, java_coord) in enumerate(zip(py_exterior, java_exterior)):
            x_diff = abs(py_coord[0] - java_coord[0])
            y_diff = abs(py_coord[1] - java_coord[1])
            diff = max(x_diff, y_diff)
            max_diff = max(max_diff, diff)
            total_diff += diff
            vertex_diffs.append(
                {"vertex": i, "x_diff": x_diff, "y_diff": y_diff, "max_diff": diff}
            )

        avg_diff = total_diff / len(py_exterior)

        # Print summary table
        print(f"\n{'='*120}")
        print(f"📊 POLYGON ACCURACY COMPARISON - {scenario['name']}")
        print(f"{'='*120}")
        print(
            f"| {'Vertex':^8} | {'Python X':>18} | {'Python Y':>18} | {'Java X':>18} | {'Java Y':>18} | {'X Diff':>12} | {'Y Diff':>12} | {'Max Diff':>12} |"
        )
        print(f"|{'-'*10}|{'-'*20}|{'-'*20}|{'-'*20}|{'-'*20}|{'-'*14}|{'-'*14}|{'-'*14}|")

        tolerance = 1e-5  # 10 micrometers
        all_pass = True

        for i, (py_coord, java_coord, diff_data) in enumerate(
            zip(py_exterior, java_exterior, vertex_diffs)
        ):
            status = "✅" if diff_data["max_diff"] < tolerance else "❌"
            if diff_data["max_diff"] >= tolerance:
                all_pass = False

            print(
                f"| {i:^8} | {py_coord[0]:>18.6f} | {py_coord[1]:>18.6f} | {java_coord[0]:>18.6f} | {java_coord[1]:>18.6f} | "
                f"{diff_data['x_diff']:>12.2e} | {diff_data['y_diff']:>12.2e} | {diff_data['max_diff']:>12.2e} {status} |"
            )

        print(f"|{'-'*10}|{'-'*20}|{'-'*20}|{'-'*20}|{'-'*20}|{'-'*14}|{'-'*14}|{'-'*14}|")
        overall = "✅ PASS" if all_pass else "❌ FAIL"
        print(
            f"| {'SUMMARY':^8} | {'Max Diff:':>18} | {max_diff:>18.2e} | {'Avg Diff:':>18} | {avg_diff:>18.2e} | {'Tolerance:':>12} | {tolerance:>12.2e} | {overall:>14} |"
        )
        print(f"{'='*120}\n")

        # Assert accuracy
        assert (
            max_diff < tolerance
        ), f"Maximum coordinate difference too large: {max_diff:.10f} > {tolerance}"
        assert (
            avg_diff < tolerance / 10
        ), f"Average coordinate difference too large: {avg_diff:.10f} > {tolerance/10}"

        # Compare holes if present
        if scenario["type"] == "polygon_with_hole":
            assert python_result["num_holes"] == java_result["num_holes"]
            if python_result["num_holes"] > 0:
                print(f"  ✅ Holes preserved: {python_result['num_holes']}")

    def _compare_linestring_coords(
        self, python_result: Dict, java_result: Dict, scenario: Dict
    ):
        """Compare linestring coordinates."""
        py_coords = python_result["coords"]
        java_coords = java_result["coords"]

        print(f"  Vertices: Python={len(py_coords)}, Java={len(java_coords)}")
        assert len(py_coords) == len(java_coords), "Different number of vertices"

        # Compare each vertex
        max_diff = 0.0
        total_diff = 0.0

        for i, (py_coord, java_coord) in enumerate(zip(py_coords, java_coords)):
            x_diff = abs(py_coord[0] - java_coord[0])
            y_diff = abs(py_coord[1] - java_coord[1])
            diff = max(x_diff, y_diff)
            max_diff = max(max_diff, diff)
            total_diff += diff

        avg_diff = total_diff / len(py_coords)

        print(f"  Max difference: {max_diff:.2e}")
        print(f"  Avg difference: {avg_diff:.2e}")

        tolerance = 1e-5
        assert max_diff < tolerance, f"Max diff too large: {max_diff:.10f} > {tolerance}"
        assert (
            avg_diff < tolerance / 10
        ), f"Avg diff too large: {avg_diff:.10f} > {tolerance/10}"

        print(f"  ✅ LineString accuracy test passed")

    def _compare_multipolygon_coords(
        self, python_result: Dict, java_result: Dict, scenario: Dict
    ):
        """Compare multipolygon coordinates."""
        py_polygons = python_result["polygons"]
        java_polygons = java_result["polygons"]

        print(f"  Polygons: Python={len(py_polygons)}, Java={len(java_polygons)}")
        assert len(py_polygons) == len(java_polygons), "Different number of polygons"

        # Compare each polygon
        for poly_idx, (py_poly, java_poly) in enumerate(zip(py_polygons, java_polygons)):
            py_exterior = py_poly["exterior"]
            java_exterior = java_poly["exterior"]

            assert len(py_exterior) == len(java_exterior)

            max_diff = 0.0
            for py_coord, java_coord in zip(py_exterior, java_exterior):
                x_diff = abs(py_coord[0] - java_coord[0])
                y_diff = abs(py_coord[1] - java_coord[1])
                max_diff = max(max_diff, max(x_diff, y_diff))

            tolerance = 1e-5
            assert max_diff < tolerance

            print(f"  Polygon {poly_idx}: max diff = {max_diff:.2e} ✅")

        print(f"  ✅ MultiPolygon accuracy test passed")



# Note: java_runner and python_runner fixtures are automatically available from conftest.py
# No need to redefine them here - they're session-scoped and shared across all tests