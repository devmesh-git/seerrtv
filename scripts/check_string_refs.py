#!/usr/bin/env python3
"""
Check string resource usage in both directions:
  1. Orphaned: strings defined in strings.xml but not referenced in code
  2. Undefined: strings referenced in code but not in strings.xml

Usage:
  python scripts/check_string_refs.py           # run both checks
  python scripts/check_string_refs.py --orphaned   # only orphaned
  python scripts/check_string_refs.py --undefined  # only undefined

Run from project root.
"""

import argparse
import re
import sys
from pathlib import Path

# Project root (parent of scripts/)
SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = SCRIPT_DIR.parent
TV_SRC = PROJECT_ROOT / "tv" / "src" / "main"
STRINGS_FILE = TV_SRC / "res" / "values" / "strings.xml"
RES_DIR = TV_SRC / "res"
VALUES_DIR = TV_SRC / "res" / "values"


def extract_string_names(xml_path: Path) -> set[str]:
    """Extract all string and plurals names from a strings.xml file."""
    names = set()
    content = xml_path.read_text()
    # Match <string name="xxx"> and <plurals name="xxx">
    for match in re.finditer(r'<(?:string|plurals)\s+name="([^"]+)"', content):
        names.add(match.group(1))
    return names


def search_codebase_for_reference(name: str, search_paths: list[Path]) -> bool:
    """
    Search if a string/plurals resource is referenced anywhere.
    Patterns: R.string.name, R.plurals.name, @string/name, @plurals/name
    """
    # Escape for regex (name may contain special chars like _)
    escaped = re.escape(name)
    patterns = [
        rf"R\.string\.{escaped}\b",
        rf"R\.plurals\.{escaped}\b",
        rf"@string/{escaped}\b",
        rf"@plurals/{escaped}\b",
    ]
    combined = "|".join(patterns)

    for path in search_paths:
        if not path.exists():
            continue
        if path.is_file():
            files = [path]
        else:
            files = list(path.rglob("*"))

        for f in files:
            if f.suffix not in (".kt", ".java", ".xml"):
                continue
            # Skip strings.xml itself - we're checking references outside it
            if "strings.xml" in str(f) or "strings.xml" in f.name:
                continue
            try:
                text = f.read_text(errors="replace")
                if re.search(combined, text):
                    return True
            except Exception:
                pass
    return False


def extract_string_refs_from_strings(xml_path: Path) -> set[str]:
    """
    Extract string names that are referenced via @string/ in strings.xml
    (e.g. string value="@string/other_string" or @string/foo)
    """
    refs = set()
    content = xml_path.read_text()
    for match in re.finditer(r"@string/([a-zA-Z0-9_]+)", content):
        refs.add(match.group(1))
    return refs


def extract_referenced_strings(search_paths: list[Path]) -> set[str]:
    """
    Extract all string/plurals names referenced in the codebase.
    Matches: R.string.xxx, R.plurals.xxx, @string/xxx, @plurals/xxx
    """
    referenced = set()
    # Match R.string.name, R.plurals.name, @string/name, @plurals/name
    pattern = re.compile(
        r"R\.(?:string|plurals)\.([a-zA-Z0-9_]+)|" r"@(?:string|plurals)/([a-zA-Z0-9_]+)"
    )

    for path in search_paths:
        if not path.exists():
            continue
        files = [path] if path.is_file() else list(path.rglob("*"))
        for f in files:
            if f.suffix not in (".kt", ".java", ".xml"):
                continue
            if "strings.xml" in str(f):
                continue
            try:
                text = f.read_text(errors="replace")
                for m in pattern.finditer(text):
                    name = m.group(1) or m.group(2)
                    if name:
                        referenced.add(name)
            except Exception:
                pass
    return referenced


def get_search_paths() -> list[Path]:
    """Return paths to search (java, res, AndroidManifest)."""
    paths = [TV_SRC / "java", RES_DIR, TV_SRC]
    if not (TV_SRC / "java").exists():
        paths = [PROJECT_ROOT]
    return paths


def check_orphaned(defined: set[str], internal_refs: set[str], search_paths: list[Path]) -> list[str]:
    """Find strings defined in strings.xml but not referenced in code."""
    orphaned = []
    for name in sorted(defined):
        if search_codebase_for_reference(name, search_paths):
            continue
        if name in internal_refs:
            continue
        orphaned.append(name)
    return orphaned


def check_undefined(defined: set[str], search_paths: list[Path]) -> list[str]:
    """Find strings referenced in code but not defined in strings.xml."""
    referenced = extract_referenced_strings(search_paths)
    # Also include @string/ refs from strings.xml (transitive - the target must exist)
    for strings_path in Path(VALUES_DIR).glob("**/strings.xml"):
        internal_refs = extract_string_refs_from_strings(strings_path)
        referenced.update(internal_refs)
    return sorted(referenced - defined)


def main() -> int:
    parser = argparse.ArgumentParser(description="Check string resource usage (orphaned or undefined)")
    parser.add_argument(
        "--orphaned",
        action="store_true",
        help="Only check for orphaned strings (defined but not referenced)",
    )
    parser.add_argument(
        "--undefined",
        action="store_true",
        help="Only check for undefined strings (referenced but not in strings.xml)",
    )
    args = parser.parse_args()

    run_orphaned = args.orphaned or (not args.orphaned and not args.undefined)
    run_undefined = args.undefined or (not args.orphaned and not args.undefined)

    if not STRINGS_FILE.exists():
        print(f"Error: {STRINGS_FILE} not found", file=sys.stderr)
        return 1

    print("Extracting string names from strings.xml...")
    defined_strings = extract_string_names(STRINGS_FILE)
    string_internal_refs = extract_string_refs_from_strings(STRINGS_FILE)
    search_paths = get_search_paths()

    print(f"Found {len(defined_strings)} string/plurals resources.")
    print("Searching codebase for references...\n")

    exit_code = 0

    if run_orphaned:
        orphaned = check_orphaned(defined_strings, string_internal_refs, search_paths)
        if orphaned:
            print(f"⚠️  ORPHANED: {len(orphaned)} string(s) defined but not referenced:\n")
            for name in orphaned:
                print(f"  - {name}")
            print("\nNote: Some may be used dynamically (e.g. getIdentifier) or in Gradle/Kotlin DSL.")
            exit_code = 1
        else:
            print("✓ Orphaned: All defined strings appear to be referenced.")

    if run_undefined:
        undefined = check_undefined(defined_strings, search_paths)
        if undefined:
            if run_orphaned:
                print()
            print(f"⚠️  UNDEFINED: {len(undefined)} string(s) referenced but not in strings.xml:\n")
            for name in undefined:
                print(f"  - {name}")
            print("\nThese will cause build/runtime errors. Add them to strings.xml.")
            exit_code = 1
        else:
            print("✓ Undefined: All referenced strings exist in strings.xml.")

    return exit_code


if __name__ == "__main__":
    sys.exit(main())
