#!/usr/bin/env python3
"""
Generate exhaustive projected-CRS meridian-axis reference data using pyproj.

The generator discovers every active EPSG projected CRS whose PROJJSON
coordinate system contains a meridian-qualified axis. Each row embeds the full
PROJJSON definition so the Java benchmark remains independent of online CRS
providers after generation.
"""

import argparse
import json
import math
import warnings
from pathlib import Path
from typing import Any, Dict, List, Optional

import pyproj
from pyproj import CRS, database, network
from pyproj.enums import PJType
from pyproj.exceptions import CRSError


SCHEMA_VERSION = "1.0"
EXPECTED_FORMATS = ["wkt2", "projjson", "proj_string"]
BASELINE_CODES = [
    "2985", "2986", "3031", "3032", "3275", "3276", "3277", "3278",
    "3279", "3280", "3281", "3282", "3283", "3284", "3285", "3286",
    "3287", "3288", "3289", "3290", "3291", "3292", "3293", "3408",
    "3409", "3411", "3412", "3413", "3571", "3572", "3573", "3574",
    "3575", "3576", "3976", "3995", "3996", "5041", "5042", "5482",
    "5936", "5937", "5938", "5939", "5940", "6931", "6932", "9354",
    "27702", "32661", "32761",
]


def _canonical_unit_type(unit_type: Optional[str], default_type: str) -> str:
    if not unit_type:
        return default_type
    canonical = {
        "linearunit": "LinearUnit",
        "angularunit": "AngularUnit",
        "scaleunit": "ScaleUnit",
        "timeunit": "TimeUnit",
        "parametricunit": "ParametricUnit",
        "unit": default_type,
    }
    return canonical.get(str(unit_type).lower(), str(unit_type))


def _known_unit_factor(name: str) -> Optional[float]:
    factors = {
        "metre": 1.0,
        "meter": 1.0,
        "degree": math.pi / 180.0,
        "radian": 1.0,
        "grad": math.pi / 200.0,
        "gon": math.pi / 200.0,
        "arc-second": math.pi / 648000.0,
    }
    return factors.get(name.lower())


def _normalize_unit(
        value: Any,
        default_type: str,
        fallback_name: Optional[str] = None,
        fallback_factor: Optional[float] = None) -> Dict[str, Any]:
    unit_type = None
    name = None
    factor = None
    if isinstance(value, dict):
        unit_type = value.get("type")
        name = value.get("name")
        factor = value.get("conversion_factor")
    elif value is not None:
        name = str(value)

    if name is None:
        name = fallback_name
    if name is None:
        raise ValueError("axis unit has no name")
    if factor is None:
        factor = fallback_factor
    if factor is None:
        factor = _known_unit_factor(str(name))
    if factor is None or not math.isfinite(float(factor)) or float(factor) <= 0:
        raise ValueError("axis unit has no positive finite conversion factor: "
                         + str(value))
    return {
        "type": _canonical_unit_type(unit_type, default_type),
        "name": str(name),
        "conversion_factor": float(factor),
    }


def _normalize_meridian(value: Any) -> Optional[Dict[str, Any]]:
    if value is None:
        return None
    if not isinstance(value, dict):
        raise ValueError("axis meridian must be an object")

    longitude = value.get("longitude")
    unit_value = value.get("unit")
    if isinstance(longitude, dict):
        if "unit" in longitude:
            unit_value = longitude["unit"]
        longitude = longitude.get("value")
    if longitude is None or not math.isfinite(float(longitude)):
        raise ValueError("axis meridian has no finite longitude")

    unit = _normalize_unit(
        unit_value,
        "AngularUnit",
        fallback_name="degree",
        fallback_factor=math.pi / 180.0,
    )
    return {
        "longitude": float(longitude),
        "unit": unit,
    }


def _axis_signature(crs: CRS) -> List[Dict[str, Any]]:
    projjson = crs.to_json_dict()
    coordinate_system = projjson.get("coordinate_system")
    if not isinstance(coordinate_system, dict):
        raise ValueError("CRS has no PROJJSON coordinate_system object")
    axes = coordinate_system.get("axis")
    if not isinstance(axes, list) or not axes:
        raise ValueError("CRS has no PROJJSON coordinate axes")

    subtype = str(coordinate_system.get("subtype", "")).lower()
    default_unit_type = (
        "AngularUnit" if subtype in ("ellipsoidal", "spherical")
        else "LinearUnit"
    )
    shared_unit = coordinate_system.get("unit")
    axis_info = list(crs.axis_info)
    normalized = []
    for index, axis in enumerate(axes):
        if not isinstance(axis, dict):
            raise ValueError("coordinate axis must be an object")
        if axis.get("name") is None or axis.get("direction") is None:
            raise ValueError("coordinate axis lacks name or direction")

        fallback_name = None
        fallback_factor = None
        if index < len(axis_info):
            fallback_name = axis_info[index].unit_name
            fallback_factor = axis_info[index].unit_conversion_factor
        unit_value = axis.get("unit", shared_unit)
        order = axis.get("order", index + 1)
        try:
            order = int(order)
        except (TypeError, ValueError) as exc:
            raise ValueError("coordinate axis order is not an integer") from exc

        normalized.append({
            "name": str(axis["name"]),
            "abbreviation": (
                None if axis.get("abbreviation") is None
                else str(axis["abbreviation"])
            ),
            "direction": str(axis["direction"]),
            "order": order,
            "unit": _normalize_unit(
                unit_value,
                default_unit_type,
                fallback_name=fallback_name,
                fallback_factor=fallback_factor,
            ),
            "meridian": _normalize_meridian(axis.get("meridian")),
        })
    return normalized


def _has_meridian_axis(projjson: Dict[str, Any]) -> bool:
    coordinate_system = projjson.get("coordinate_system")
    if not isinstance(coordinate_system, dict):
        return False
    axes = coordinate_system.get("axis")
    return isinstance(axes, list) and any(
        isinstance(axis, dict) and axis.get("meridian") is not None
        for axis in axes
    )


def _proj_string(crs: CRS) -> Optional[str]:
    with warnings.catch_warnings():
        warnings.simplefilter("ignore", UserWarning)
        try:
            value = crs.to_proj4()
        except CRSError:
            return None
    if not value or not value.strip():
        return None
    return value


def _discover_cases(verbose: bool) -> List[Dict[str, Any]]:
    infos = database.query_crs_info(
        auth_name="EPSG",
        pj_types=PJType.PROJECTED_CRS,
        allow_deprecated=False,
    )
    cases = []
    for info in sorted(infos, key=lambda item: int(item.code)):
        crs = CRS.from_authority(info.auth_name, info.code)
        source_projjson = crs.to_json_dict()
        if not _has_meridian_axis(source_projjson):
            continue

        expected_axes = _axis_signature(crs)
        if not any(axis["meridian"] is not None for axis in expected_axes):
            raise ValueError(
                "EPSG:{} was selected without a normalized meridian".format(info.code)
            )

        wkt_roundtrip = CRS.from_wkt(crs.to_wkt(version="WKT2_2019"))
        if _axis_signature(wkt_roundtrip) != expected_axes:
            raise ValueError(
                "EPSG:{} changes axis metadata in pyproj WKT2 round trip".format(
                    info.code
                )
            )
        json_roundtrip = CRS.from_json_dict(source_projjson)
        if _axis_signature(json_roundtrip) != expected_axes:
            raise ValueError(
                "EPSG:{} changes axis metadata in pyproj PROJJSON round trip".format(
                    info.code
                )
            )

        legacy_proj_string = _proj_string(crs)
        if legacy_proj_string is not None and any(
                token.startswith("+axis=")
                for token in legacy_proj_string.split()):
            raise ValueError(
                "EPSG:{} unexpectedly exports a legacy +axis token".format(info.code)
            )

        case = {
            "case_id": "epsg_{}".format(info.code),
            "authority": str(info.auth_name),
            "code": str(info.code),
            "name": str(info.name),
            "source_projjson": source_projjson,
            "expected_axes": expected_axes,
            "legacy_proj_string": legacy_proj_string,
            "pyproj_legacy_proj_exported": legacy_proj_string is not None,
            "legacy_proj_axis_omitted": True,
        }
        cases.append(case)
        if verbose:
            print("  EPSG:{} {}".format(info.code, info.name))

    discovered_codes = [case["code"] for case in cases]
    missing_baseline = sorted(
        set(BASELINE_CODES) - set(discovered_codes), key=int
    )
    if missing_baseline:
        raise ValueError(
            "active meridian-axis discovery lost baseline EPSG codes: "
            + ", ".join(missing_baseline)
        )
    return cases


def generate_meridian_axis_reference(
        output_file: str, verbose: bool = False) -> None:
    """Generate the exhaustive meridian-axis parity fixture."""
    network.set_network_enabled(False)
    cases = _discover_cases(verbose)
    reference_data = {
        "version": SCHEMA_VERSION,
        "generator": "pyproj",
        "pyproj_version": pyproj.__version__,
        "proj_version": pyproj.proj_version_str,
        "epsg_version": database.get_database_metadata("EPSG.VERSION"),
        "epsg_date": database.get_database_metadata("EPSG.DATE"),
        "proj_data_version": database.get_database_metadata("PROJ_DATA.VERSION"),
        "baseline_case_count": len(BASELINE_CODES),
        "baseline_codes": BASELINE_CODES,
        "expected_case_count": len(cases),
        "expected_formats": EXPECTED_FORMATS,
        "expected_comparison_count": len(cases) * len(EXPECTED_FORMATS),
        "test_cases": cases,
    }

    output_path = Path(output_file)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", encoding="utf-8") as output:
        json.dump(reference_data, output, indent=2, ensure_ascii=False)
        output.write("\n")

    if verbose:
        print(
            "  Generated {} active EPSG cases and {} comparisons".format(
                len(cases), reference_data["expected_comparison_count"]
            )
        )


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Generate active EPSG meridian-axis reference data"
    )
    parser.add_argument(
        "--output",
        "-o",
        default="meridian_axis_reference.json",
        help="Output JSON file",
    )
    parser.add_argument(
        "--verbose",
        "-v",
        action="store_true",
        help="Print every discovered CRS",
    )
    args = parser.parse_args()
    generate_meridian_axis_reference(args.output, verbose=args.verbose)


if __name__ == "__main__":
    main()
