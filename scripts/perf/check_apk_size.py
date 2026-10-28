#!/usr/bin/env python3
# File: scripts/perf/check_apk_size.py
# Purpose: Fails the build when an APK is bigger than its budget (scripts/perf/apk-budget.json, ADR
#          2026-10-28-performance-budget), and prints a Markdown row with its size and the change from a baseline
#          (the published dev build) so every PR states what it costs in download size.
#
# Usage: check_apk_size.py --budget apk-budget.json --variant dev [--baseline-bytes N] app.apk
#   --baseline-bytes: the size to compare with (CI passes the dev-build pre-release's APK); without it, or with 0,
#   the change is "n/a". Exit 1: over budget (or a bad budget file). A growth past the budget's warnGrowthMb is a
#   warning only: a deliberate addition (a font, a voice) raises the budget in the same PR, a surprise gets seen.
# Standard library only, so it runs the same in CI and on a Windows laptop.

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass
from pathlib import Path

MB = 1_000_000


@dataclass(frozen=True)
class Budget:
    max_bytes: int
    warn_growth_bytes: int


@dataclass(frozen=True)
class Verdict:
    over_budget: bool
    grew_too_much: bool
    row: str


def load_budget(path: Path, variant: str) -> Budget:
    """The budget for [variant]; raises ValueError for a missing variant or a value that is not a positive number."""
    data = json.loads(path.read_text(encoding="utf-8"))
    variants = data.get("variants", {})
    if variant not in variants:
        raise ValueError(f"no budget for variant '{variant}' in {path} (has: {', '.join(sorted(variants))})")
    max_mb = variants[variant].get("maxMb")
    warn_mb = data.get("warnGrowthMb")
    for name, value in (("maxMb", max_mb), ("warnGrowthMb", warn_mb)):
        if not isinstance(value, (int, float)) or isinstance(value, bool) or value <= 0:
            raise ValueError(f"{name} must be a positive number in {path}, was {value!r}")
    return Budget(max_bytes=int(max_mb * MB), warn_growth_bytes=int(warn_mb * MB))


def judge(variant: str, size_bytes: int, budget: Budget, baseline_bytes: int | None) -> Verdict:
    """Whether [size_bytes] fits [budget], and its summary row (sizes in MB, 1 MB = 1,000,000 bytes)."""
    change = None if not baseline_bytes else size_bytes - baseline_bytes
    change_text = "n/a" if change is None else f"{change / MB:+.2f}"
    over = size_bytes > budget.max_bytes
    grew = change is not None and change > budget.warn_growth_bytes
    status = "over budget" if over else ("grew more than expected" if grew else "ok")
    row = f"| {variant} | {size_bytes / MB:.2f} | {change_text} | {budget.max_bytes / MB:.0f} | {status} |"
    return Verdict(over_budget=over, grew_too_much=grew, row=row)


HEADER = "| APK | Size (MB) | Change from dev build (MB) | Budget (MB) | Result |\n|---|---|---|---|---|"


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--budget", type=Path, required=True)
    parser.add_argument("--variant", required=True)
    parser.add_argument("--baseline-bytes", type=int, default=0)
    parser.add_argument("--header", action="store_true", help="print the table header first")
    parser.add_argument("apk", type=Path)
    args = parser.parse_args(argv)
    try:
        budget = load_budget(args.budget, args.variant)
    except (OSError, ValueError) as error:
        print(f"::error::APK budget: {error}")
        return 1
    if not args.apk.is_file():
        print(f"::error::APK budget: no APK at {args.apk}")
        return 1
    verdict = judge(args.variant, args.apk.stat().st_size, budget, args.baseline_bytes or None)
    if args.header:
        print(HEADER)
    print(verdict.row)
    if verdict.over_budget:
        print(f"::error::{args.variant} APK is over its budget ({args.budget}): {verdict.row}", file=sys.stderr)
        return 1
    if verdict.grew_too_much:
        print(f"::warning::{args.variant} APK grew past warnGrowthMb: {verdict.row}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
