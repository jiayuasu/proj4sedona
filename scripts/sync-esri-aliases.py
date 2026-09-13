#!/usr/bin/env python3
"""Generate the Esri name index from Esri's projection engine database documentation.

Esri-style WKT names its objects differently from EPSG: datums carry a ``D_`` prefix
(``D_North_American_1983``), geographic systems a ``GCS_`` prefix, and projected systems
use Esri's own names (``NAD_1983_UTM_Zone_19N``). Esri publishes the contents of its
projection engine database, Apache License 2.0, at
https://github.com/Esri/projection-engine-db-doc; every object there carries its WKID,
which is the EPSG code wherever one exists and an Esri code otherwise. This script
extracts an index of names to codes from that checkout.

The index deliberately carries no coordinate system parameters: identification by name
resolves the definition behind a code through the library's providers (bundled, cached,
or fetched on demand), so no geodetic parameter dataset is bundled with the library.

Usage:
    scripts/sync-esri-aliases.py [--check] [--output FILE] ESRI_CHECKOUT

ESRI_CHECKOUT is a clone of Esri/projection-engine-db-doc. ``--check`` regenerates to a
buffer and fails if the committed file differs.

Output columns (tab-separated, ``#`` lines are comments):
    type          geodetic_datum | geodetic_crs | projected_crs | ellipsoid
    esri_name     the Esri name, exactly as spelled by Esri
    code          <AUTHORITY>:<latest WKID>, e.g. EPSG:26919 or ESRI:102001
    base_crs      the geographic CRS of the object's datum: the base of a projected CRS
                  (from the GEOGCS embedded in its WKT), a geographic CRS itself, and for
                  a datum the lowest current geographic CRS on it; empty for ellipsoids
    deprecated    1 when Esri marks the object deprecated, else 0

When several objects share a name (Esri keeps the old row after a code change), the
current one wins: not deprecated first, then the row whose WKID is already the latest,
then the lowest latest WKID.
"""
import csv
import re
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = SCRIPT_DIR.parent
DEFAULT_OUTPUT = (
    PROJECT_ROOT / "src/main/resources/org/datasyslab/proj4sedona/constants/esri-aliases.tsv"
)
SOURCES = (
    ("geodetic_datum", "csv/pe_list_datum.csv"),
    ("geodetic_crs", "csv/pe_list_geogcs.csv"),
    ("projected_crs", "csv/pe_list_projcs.csv"),
    ("ellipsoid", "csv/pe_list_spheroid.csv"),
)
GEOGCS_NAME = re.compile(r'GEOGCS\["([^"]+)"')
DATUM_NAME = re.compile(r'DATUM\["([^"]+)"')


def usage(code=2):
    print("Usage: scripts/sync-esri-aliases.py [--check] [--output FILE] ESRI_CHECKOUT", file=sys.stderr)
    sys.exit(code)


def authority_code(row):
    authority = row["authority"].strip().upper()
    if authority not in ("EPSG", "ESRI"):
        raise SystemExit(f"unexpected authority {row['authority']!r} for {row['name']!r}")
    return f"{authority}:{int(row['latestWkid'])}"


def is_deprecated(row):
    return row["deprecated"].strip().lower() == "yes"


def rank(row):
    return (
        1 if is_deprecated(row) else 0,
        0 if row["wkid"] == row["latestWkid"] else 1,
        int(row["latestWkid"]),
    )


def read_rows(checkout, relative):
    path = checkout / relative
    if not path.is_file():
        raise SystemExit(f"{path} not found; is {checkout} a clone of Esri/projection-engine-db-doc?")
    with path.open(newline="", encoding="utf-8") as handle:
        rows = list(csv.DictReader(handle))
    for column in ("wkid", "latestWkid", "name", "wkt", "authority", "deprecated"):
        if rows and column not in rows[0]:
            raise SystemExit(f"{path} has no {column!r} column")
    return rows


def collapse_by_name(rows):
    """One row per name (case-insensitive): the current object wins."""
    best = {}
    for row in rows:
        key = row["name"].strip().lower()
        if key not in best or rank(row) < rank(best[key]):
            best[key] = row
    return best


def upstream_commit(checkout):
    try:
        sha = subprocess.run(["git", "-C", str(checkout), "rev-parse", "HEAD"],
                             capture_output=True, text=True, check=True).stdout.strip()
        date = subprocess.run(["git", "-C", str(checkout), "log", "-1", "--format=%cs"],
                              capture_output=True, text=True, check=True).stdout.strip()
        return sha, date
    except (OSError, subprocess.CalledProcessError):
        return "unknown", "unknown"


def generate(checkout):
    tables = {kind: read_rows(checkout, relative) for kind, relative in SOURCES}
    current = {kind: collapse_by_name(rows) for kind, rows in tables.items()}

    geogcs_code_by_name = {key: authority_code(row) for key, row in current["geodetic_crs"].items()}

    # The lowest current geographic CRS on each datum, for the datum rows' base_crs.
    geogcs_by_datum = defaultdict(list)
    for row in tables["geodetic_crs"]:
        if is_deprecated(row):
            continue
        match = DATUM_NAME.search(row["wkt"])
        if match:
            geogcs_by_datum[match.group(1).strip().lower()].append(row)

    records = []
    for kind, _ in SOURCES:
        for key in sorted(current[kind]):
            row = current[kind][key]
            code = authority_code(row)
            if kind == "geodetic_crs":
                base = code
            elif kind == "projected_crs":
                match = GEOGCS_NAME.search(row["wkt"])
                base = geogcs_code_by_name.get(match.group(1).strip().lower(), "") if match else ""
            elif kind == "geodetic_datum":
                candidates = geogcs_by_datum.get(key, [])
                base = authority_code(min(candidates, key=rank)) if candidates else ""
            else:
                base = ""
            records.append((kind, row["name"].strip(), code, base, 1 if is_deprecated(row) else 0))

    sha, date = upstream_commit(checkout)
    counts = {kind: len(current[kind]) for kind, _ in SOURCES}
    lines = [
        "# Esri object names mapped to their codes. Generated by scripts/sync-esri-aliases.py from",
        f"# Esri/projection-engine-db-doc (Apache License 2.0), commit {sha} ({date}); do not edit by hand.",
        "# " + ", ".join(f"{kind}: {n}" for kind, n in counts.items())
        + f" (collapsed from {sum(len(r) for r in tables.values())} rows)",
        "# type\tesri_name\tcode\tbase_crs\tdeprecated",
    ]
    for record in records:
        lines.append("\t".join(str(field) for field in record))
    return "\n".join(lines) + "\n", counts


def main(argv):
    check = False
    output = DEFAULT_OUTPUT
    checkout = None
    i = 0
    while i < len(argv):
        arg = argv[i]
        if arg == "--check":
            check = True
        elif arg == "--output":
            i += 1
            if i >= len(argv):
                usage()
            output = Path(argv[i])
        elif arg.startswith("-") or checkout is not None:
            usage()
        else:
            checkout = Path(arg).resolve()
        i += 1
    if checkout is None:
        usage()

    text, counts = generate(checkout)
    summary = ", ".join(f"{n} {kind}" for kind, n in counts.items())
    if check:
        current = output.read_text(encoding="utf-8") if output.exists() else ""
        if current != text:
            print(f"{output} is out of date with {checkout}", file=sys.stderr)
            sys.exit(1)
        print(f"{output} is up to date ({summary})")
        return
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(text, encoding="utf-8")
    print(f"wrote {output}: {summary}")


if __name__ == "__main__":
    main(sys.argv[1:])
