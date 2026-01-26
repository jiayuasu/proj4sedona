#!/usr/bin/env python3
"""
Master script to generate all pyproj reference data for proj4sedona integration tests.

This script orchestrates the generation of all reference data files:
- transform_reference.json: Coordinate transformation test cases
- parsing_reference.json: CRS parsing test cases
- format_export_reference.json: CRS export format test cases
- grid_transform_reference.json: Grid-based transformation test cases

Usage:
    python generate_all.py --output-dir /path/to/output
"""

import argparse
import os
import sys
from pathlib import Path

# Import generator modules
from generate_transform_reference import generate_transform_reference
from generate_parsing_reference import generate_parsing_reference
from generate_format_reference import generate_format_reference
from generate_grid_reference import generate_grid_reference


def main():
    parser = argparse.ArgumentParser(
        description="Generate all pyproj reference data for integration tests"
    )
    parser.add_argument(
        "--output-dir",
        type=str,
        required=True,
        help="Output directory for reference data files"
    )
    parser.add_argument(
        "--verbose",
        "-v",
        action="store_true",
        help="Enable verbose output"
    )
    
    args = parser.parse_args()
    
    # Create output directory if it doesn't exist
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    
    print("=" * 60)
    print("Generating pyproj reference data")
    print("=" * 60)
    print(f"Output directory: {output_dir}")
    print()
    
    # Generate each reference file
    generators = [
        ("transform_reference.json", generate_transform_reference),
        ("parsing_reference.json", generate_parsing_reference),
        ("format_export_reference.json", generate_format_reference),
        ("grid_transform_reference.json", generate_grid_reference),
    ]
    
    for filename, generator_func in generators:
        output_file = output_dir / filename
        print(f"Generating {filename}...")
        try:
            generator_func(str(output_file), verbose=args.verbose)
            print(f"  -> {output_file}")
        except Exception as e:
            print(f"  ERROR: {e}", file=sys.stderr)
            if args.verbose:
                import traceback
                traceback.print_exc()
            sys.exit(1)
    
    print()
    print("=" * 60)
    print("Reference data generation complete!")
    print("=" * 60)


if __name__ == "__main__":
    main()
