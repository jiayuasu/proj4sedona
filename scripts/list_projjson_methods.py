#!/usr/bin/env python3
"""Get all unique PROJJSON method names used by EPSG projected CRS via pyproj."""
import pyproj
import json

# Get all EPSG projected CRS codes and their PROJJSON method names
from pyproj.database import query_crs_info

methods = {}
for crs_info in query_crs_info(auth_name="EPSG", pj_types="PROJECTED_CRS"):
    code = crs_info.code
    try:
        crs = pyproj.CRS(f"EPSG:{code}")
        pj = crs.to_json_dict()
        if "conversion" in pj and "method" in pj["conversion"]:
            method_name = pj["conversion"]["method"]["name"]
            if method_name not in methods:
                methods[method_name] = code  # first example code
    except Exception:
        pass

print(f"Total unique method names: {len(methods)}")
for name in sorted(methods.keys()):
    print(f"  {name}  (e.g. EPSG:{methods[name]})")
