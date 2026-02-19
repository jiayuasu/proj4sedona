#!/usr/bin/env python3
"""
Verify PROJ_TO_WKT_METHOD mappings against the PROJ database.

The PROJ database has:
- coordinate_operation_method: EPSG method names
- conversion_table -> method_auth_name/method_code: links to methods used by conversions
- projected_crs -> conversion_auth_name/conversion_code: how projected CRS are defined

We also check against pyproj's own projection definitions.
"""

import sqlite3
import pyproj
import os
import json

db_path = os.path.join(pyproj.datadir.get_data_dir(), "proj.db")
db = sqlite3.connect(db_path)
cur = db.cursor()

# Current mappings from the code
PROJ_TO_WKT = {
    "longlat": "Geographic",
    "tmerc": "Transverse Mercator",
    "utm": "Transverse Mercator",
    "merc": "Mercator",
    "lcc": "Lambert Conformal Conic",
    "aea": "Albers Equal Area",
    "stere": "Stereographic",
    "sterea": "Oblique Stereographic",
    "omerc": "Oblique Mercator",
    "somerc": "Swiss Oblique Mercator",
    "krovak": "Krovak",
    "cass": "Cassini-Soldner",
    "laea": "Lambert Azimuthal Equal Area",
    "aeqd": "Azimuthal Equidistant",
    "eqdc": "Equidistant Conic",
    "poly": "Polyconic",
    "nzmg": "New Zealand Map Grid",
    "mill": "Miller Cylindrical",
    "sinu": "Sinusoidal",
    "moll": "Mollweide",
    "eqc": "Equirectangular",
    "cea": "Cylindrical Equal Area",
    "gnom": "Gnomonic",
    "ortho": "Orthographic",
    "vandg": "Van der Grinten",
    "robin": "Robinson",
    "etmerc": "Extended Transverse Mercator",
    "gstmerc": "Gauss-Schreiber Transverse Mercator",
}

# 1. Get all coordinate operation method names from EPSG
print("=== EPSG Coordinate Operation Methods (relevant subset) ===")
cur.execute("""
    SELECT code, name FROM coordinate_operation_method
    WHERE auth_name = 'EPSG'
    ORDER BY name
""")
epsg_methods = {row[1].lower(): (row[0], row[1]) for row in cur.fetchall()}

# 2. Check each mapping
print("\n=== Verification of PROJ_TO_WKT_METHOD ===")
print(f"{'PROJ':12} {'Current WKT name':45} {'Match in EPSG DB?':20} {'Closest EPSG name'}")
print("-" * 130)

for proj_name, wkt_name in PROJ_TO_WKT.items():
    wkt_lower = wkt_name.lower()
    
    # Direct match
    if wkt_lower in epsg_methods:
        code, canonical = epsg_methods[wkt_lower]
        status = "EXACT" if canonical == wkt_name else f"CASE: '{canonical}'"
        print(f"{proj_name:12} {wkt_name:45} {status:20}")
    else:
        # Fuzzy search
        matches = [(k, v) for k, v in epsg_methods.items() if wkt_lower.split()[0] in k]
        if matches:
            closest = matches[:3]
            print(f"{proj_name:12} {wkt_name:45} {'NOT FOUND':20} Closest: {[m[1][1] for m in closest]}")
        else:
            print(f"{proj_name:12} {wkt_name:45} {'NOT FOUND':20} (no close match)")

# 3. SPECIAL: Check what pyproj uses as the method name for each projection
print("\n=== pyproj PROJJSON verification (what pyproj actually emits) ===")
test_cases = [
    ("tmerc", "+proj=tmerc +lat_0=0 +lon_0=9 +k=0.9996 +x_0=500000 +y_0=0 +datum=WGS84 +units=m"),
    ("merc", "+proj=merc +lon_0=0 +k=1 +x_0=0 +y_0=0 +datum=WGS84 +units=m"),
    ("lcc", "+proj=lcc +lat_1=33 +lat_2=45 +lat_0=39 +lon_0=-96 +x_0=0 +y_0=0 +datum=WGS84 +units=m"),
    ("aea", "+proj=aea +lat_1=29.5 +lat_2=45.5 +lat_0=37.5 +lon_0=-96 +x_0=0 +y_0=0 +datum=WGS84 +units=m"),
    ("stere", "+proj=stere +lat_0=90 +lon_0=0 +k=0.994 +x_0=2000000 +y_0=2000000 +datum=WGS84 +units=m"),
    ("sterea", "+proj=sterea +lat_0=52.15616055555556 +lon_0=5.38763888888889 +k=0.9999079 +x_0=155000 +y_0=463000 +datum=WGS84 +units=m"),
    ("omerc", "+proj=omerc +lat_0=4 +lonc=115 +alpha=53.31582 +k=0.99984 +x_0=590476.87 +y_0=442857.65 +datum=WGS84 +units=m"),
    ("somerc", "+proj=somerc +lat_0=46.95240555555556 +lon_0=7.439583333333333 +k_0=1 +x_0=600000 +y_0=200000 +datum=WGS84 +units=m"),
    ("krovak", "+proj=krovak +lat_0=49.5 +lon_0=24.83333333333333 +alpha=30.28813975277778 +k=0.9999 +x_0=0 +y_0=0 +datum=WGS84 +units=m"),
    ("cass", "+proj=cass +lat_0=31.73409694444445 +lon_0=35.21208055555556 +x_0=170251.555 +y_0=126867.909 +datum=WGS84 +units=m"),
    ("laea", "+proj=laea +lat_0=52 +lon_0=10 +x_0=4321000 +y_0=3210000 +datum=WGS84 +units=m"),
    ("aeqd", "+proj=aeqd +lat_0=0 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m"),
    ("poly", "+proj=poly +lat_0=0 +lon_0=-54 +x_0=5000000 +y_0=10000000 +datum=WGS84 +units=m"),
    ("nzmg", "+proj=nzmg +lat_0=-41 +lon_0=173 +x_0=2510000 +y_0=6023150 +datum=WGS84 +units=m"),
    ("eqc", "+proj=eqc +lat_ts=0 +lat_0=0 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m"),
    ("cea", "+proj=cea +lon_0=0 +lat_ts=30 +x_0=0 +y_0=0 +datum=WGS84 +units=m"),
    ("gnom", "+proj=gnom +lat_0=90 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m"),
    ("ortho", "+proj=ortho +lat_0=0 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m"),
    ("robin", "+proj=robin +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m"),
]

for proj_name, proj4str in test_cases:
    try:
        crs = pyproj.CRS(proj4str)
        pj = crs.to_json_dict()
        # Extract the conversion method name
        if "conversion" in pj and "method" in pj["conversion"]:
            method_name = pj["conversion"]["method"]["name"]
        elif "method" in pj:
            method_name = pj["method"]["name"]
        else:
            method_name = "(no method found)"
        
        our_wkt = PROJ_TO_WKT.get(proj_name, "(not mapped)")
        match = "OK" if method_name.lower() == our_wkt.lower() else "MISMATCH"
        if match == "MISMATCH":
            print(f"  {proj_name:12} pyproj: {method_name:50} ours: {our_wkt:45} ** {match} **")
        else:
            print(f"  {proj_name:12} pyproj: {method_name:50} ours: {our_wkt:45} {match}")
    except Exception as e:
        print(f"  {proj_name:12} ERROR: {e}")

# 4. Check special cases
print("\n=== Special notes ===")
# longlat
try:
    crs = pyproj.CRS("+proj=longlat +datum=WGS84")
    pj = crs.to_json_dict()
    print(f"  longlat -> type={pj.get('type', 'unknown')}, no conversion method (it's geographic, not projected)")
except Exception as e:
    print(f"  longlat: {e}")

# utm 
try:
    crs = pyproj.CRS("+proj=utm +zone=32 +datum=WGS84")
    pj = crs.to_json_dict()
    method_name = pj.get("conversion", {}).get("method", {}).get("name", "?")
    print(f"  utm -> pyproj method: '{method_name}' (should be Transverse Mercator)")
except Exception as e:
    print(f"  utm: {e}")

# etmerc
try:
    crs = pyproj.CRS("+proj=etmerc +lat_0=0 +lon_0=9 +k=0.9996 +x_0=500000 +y_0=0 +datum=WGS84 +units=m")
    pj = crs.to_json_dict()
    method_name = pj.get("conversion", {}).get("method", {}).get("name", "?")
    print(f"  etmerc -> pyproj method: '{method_name}' (we have: 'Extended Transverse Mercator')")
except Exception as e:
    print(f"  etmerc: {e}")

# somerc
try:
    crs = pyproj.CRS("+proj=somerc +lat_0=46.95240555555556 +lon_0=7.439583333333333 +k_0=1 +x_0=600000 +y_0=200000 +datum=WGS84 +units=m")
    pj = crs.to_json_dict()
    method_name = pj.get("conversion", {}).get("method", {}).get("name", "?")
    print(f"  somerc -> pyproj method: '{method_name}' (we have: 'Swiss Oblique Mercator')")
except Exception as e:
    print(f"  somerc: {e}")

# gstmerc
try:
    crs = pyproj.CRS("+proj=gstmerc +lat_0=0 +lon_0=0 +k_0=0.9996 +x_0=500000 +y_0=0 +datum=WGS84 +units=m")
    pj = crs.to_json_dict()
    method_name = pj.get("conversion", {}).get("method", {}).get("name", "?")
    print(f"  gstmerc -> pyproj method: '{method_name}' (we have: 'Gauss-Schreiber Transverse Mercator')")
except Exception as e:
    print(f"  gstmerc: {e}")

db.close()
