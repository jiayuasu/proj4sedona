#!/usr/bin/env python3
"""
Generate the DATUM_NAME_TO_EPSG mapping for CRSSerializer.java
from the authoritative PROJ SQLite database (EPSG registry).

Strategy:
- Join geodetic_datum -> geodetic_crs (type='geographic 2D', not deprecated, EPSG authority)
- For datums with multiple 2D CRS codes, pick the lowest code (most established)
- Also include the alias_name table for alternate spellings
- Normalize names to lowercase for case-insensitive matching
"""

import sqlite3
import pyproj
import os
from collections import defaultdict

db_path = os.path.join(pyproj.datadir.get_data_dir(), "proj.db")
db = sqlite3.connect(db_path)
cur = db.cursor()

# 1. Get all datum_name -> geographic 2D CRS mappings
cur.execute("""
    SELECT d.name, c.code, c.name
    FROM geodetic_datum d
    JOIN geodetic_crs c
      ON c.datum_auth_name = d.auth_name AND c.datum_code = d.code
    WHERE d.auth_name = 'EPSG' AND d.deprecated = 0
      AND c.auth_name = 'EPSG' AND c.deprecated = 0
      AND c.type = 'geographic 2D'
    ORDER BY d.name, c.code
""")
rows = cur.fetchall()

# Group by datum name, pick the lowest CRS code per datum
datum_to_crs = {}  # datum_name -> (crs_code, crs_name)
datum_codes_all = defaultdict(list)
for datum_name, crs_code, crs_name in rows:
    datum_codes_all[datum_name].append((crs_code, crs_name))
    if datum_name not in datum_to_crs:
        datum_to_crs[datum_name] = (crs_code, crs_name)

# Report datums with multiple CRS codes
multi = {k: v for k, v in datum_codes_all.items() if len(v) > 1}
print(f"Datums with multiple 2D CRS: {len(multi)}")
for d, codes in sorted(multi.items()):
    selected = datum_to_crs[d][0]
    print(f"  {d}: {[c[0] for c in codes]} -> selected {selected}")

# 2. Get alias names for geodetic datums
cur.execute("""
    SELECT a.alt_name, d.name
    FROM alias_name a
    JOIN geodetic_datum d
      ON a.auth_name = d.auth_name AND a.code = d.code
    WHERE a.table_name = 'geodetic_datum'
      AND d.auth_name = 'EPSG' AND d.deprecated = 0
    ORDER BY a.alt_name
""")
alias_rows = cur.fetchall()

alias_to_datum = {}
for alt_name, datum_name in alias_rows:
    if datum_name in datum_to_crs:
        alias_to_datum[alt_name] = datum_name

print(f"\nAlias names: {len(alias_to_datum)}")
for alt, datum in sorted(alias_to_datum.items())[:20]:
    print(f"  '{alt}' -> '{datum}' -> EPSG:{datum_to_crs[datum][0]}")
print(f"  ... (showing 20 of {len(alias_to_datum)})")

db.close()

# 3. Build the combined map (normalized lowercase)
combined = {}  # lowercase name -> EPSG code

for datum_name, (crs_code, _) in datum_to_crs.items():
    key = datum_name.lower()
    if key not in combined:
        combined[key] = crs_code

for alt_name, datum_name in alias_to_datum.items():
    key = alt_name.lower()
    crs_code = datum_to_crs[datum_name][0]
    if key not in combined:
        combined[key] = crs_code

# 4. No need for underscore variants — lookupByDatumName already normalizes
#    underscores to spaces at lookup time.

# 5. Generate Java code
print(f"\n=== Total entries: {len(combined)} ===")
print("\n// --- BEGIN GENERATED CODE (from scripts/gen_datum_map.py) ---")
print("// Source: PROJ database (EPSG registry) via pyproj")
print("// Maps normalized (lowercase) datum names to their geographic 2D CRS EPSG code")
print("private static final Map<String, String> DATUM_NAME_TO_EPSG = new HashMap<>();")
print("static {")

for name in sorted(combined.keys()):
    code = combined[name]
    # Escape any quotes in the name
    escaped = name.replace('"', '\\"')
    print(f'    DATUM_NAME_TO_EPSG.put("{escaped}", "EPSG:{code}");')

print("}")
print("// --- END GENERATED CODE ---")
