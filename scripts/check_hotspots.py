#!/usr/bin/env python3
# File: scripts/check_hotspots.py
# Purpose: Fails a branch that edits a file every PR used to fight over, so independent PRs merge in any order
#          without conflicts (ADR 2026-10-25-parallel-prs-merge-cleanly). Each problem says what to do instead.
#
# Usage: python3 scripts/check_hotspots.py [--base origin/dev]
# Compares HEAD with its merge base on --base. A change to this script itself skips the check: changing the rules
# is the maintainer's deliberate call, reviewed in that PR.

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from collections.abc import Callable, Iterable

SELF = "scripts/check_hotspots.py"

# Shared files that are frozen: history stays, new entries go into their own files.
FROZEN = {
    "docs/PROGRESS.md": "add docs/progress/<yyyy-mm-dd>-<slug>.md instead (docs/progress/README.md)",
}

# Folders where each change adds its own file, named so no two branches pick the same name.
DATED = re.compile(r"\d{4}-\d{2}-\d{2}-[a-z0-9]+(-[a-z0-9]+)*")
NAMED_FOLDERS = {
    "docs/adr/": ".md",
    "docs/progress/": ".md",
    "app/src/main/assets/whatsnew/": ".txt",
}
FOLDER_INDEXES = {"README.md"}

# The shared string files: existing strings may change, new ones go into values*/strings_<feature>.xml.
SHARED_STRINGS = (
    "app/src/main/res/values/strings.xml",
    "app/src/main/res/values-hi/strings.xml",
    "app/src/main/res/values-bn/strings.xml",
)
STRING_NAME = re.compile(r'<(?:string|string-array|plurals)\s+name="([^"]+)"')

# Retired shared counters and lists: a stale branch must not bring them back while resolving a merge.
RETIRED_TOKENS = {
    "WHATS_NEW_ID": "add app/src/main/assets/whatsnew/<yyyy-mm-dd>-<slug>.txt; nothing is numbered",
    'name="whats_new_items"': "add app/src/main/assets/whatsnew/<yyyy-mm-dd>-<slug>.txt instead of a list",
}
RETIRED_SUFFIXES = (".kt", ".kts", ".xml")

Reader = Callable[[str], str]


def parse_name_status(output: str) -> list[tuple[str, str]]:
    """`git diff --name-status -M` lines as (A|M|D, path); a rename is a delete plus an add."""
    changes: list[tuple[str, str]] = []
    for line in output.splitlines():
        parts = line.split("\t")
        if not parts or not parts[0]:
            continue
        status = parts[0][0]
        if status in "RC" and len(parts) == 3:
            if status == "R":
                changes.append(("D", parts[1]))
            changes.append(("A", parts[2]))
        elif len(parts) == 2:
            changes.append((status, parts[1]))
    return changes


def _named_folder_problem(path: str) -> str | None:
    for folder, extension in NAMED_FOLDERS.items():
        if not path.startswith(folder):
            continue
        name = path[len(folder):]
        if "/" in name or name in FOLDER_INDEXES:
            return None
        stem = name[: -len(extension)] if name.endswith(extension) else None
        if stem is None or not DATED.fullmatch(stem):
            return (
                f"{path}: name it {folder}<yyyy-mm-dd>-<slug>{extension} (date of the change, lowercase words); "
                "numbers clash when two branches pick the same one"
            )
    return None


def _string_names(content: str) -> set[str]:
    return set(STRING_NAME.findall(content))


def find_problems(changes: Iterable[tuple[str, str]], read_base: Reader, read_head: Reader) -> list[str]:
    """Every hotspot rule this branch breaks. [read_base] and [read_head] return a file's text ('' if absent)."""
    changes = list(changes)
    if any(path == SELF for _, path in changes):
        return []
    problems: list[str] = []
    for status, path in changes:
        if path in FROZEN and status != "D":
            problems.append(f"{path} is frozen: {FROZEN[path]}")
        if status == "A":
            named = _named_folder_problem(path)
            if named:
                problems.append(named)
        if status == "D":
            continue
        if path in SHARED_STRINGS:
            added = sorted(_string_names(read_head(path)) - _string_names(read_base(path)))
            if added:
                problems.append(
                    f"{path}: new strings {', '.join(added)}: put them in strings_<feature>.xml next to it "
                    "(with the Hindi and Bengali copies), so two branches never append to the same file"
                )
        if path.endswith(RETIRED_SUFFIXES):
            head, base = read_head(path), read_base(path)
            for token, instead in RETIRED_TOKENS.items():
                if head.count(token) > base.count(token):
                    problems.append(f"{path}: {token} is retired: {instead}")
    return problems


def _git(*args: str) -> str:
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True).stdout


def _reader(revision: str) -> Reader:
    def read(path: str) -> str:
        result = subprocess.run(["git", "show", f"{revision}:{path}"], capture_output=True, text=True)
        return result.stdout if result.returncode == 0 else ""

    return read


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", default="origin/dev", help="branch the PR merges into (default origin/dev)")
    args = parser.parse_args(argv)

    base = _git("merge-base", args.base, "HEAD").strip()
    changes = parse_name_status(_git("diff", "--name-status", "-M", base, "HEAD"))
    if any(path == SELF for _, path in changes):
        print(f"{SELF} changed on this branch: hotspot rules are under review here, check skipped.")
        return 0
    problems = find_problems(changes, _reader(base), _reader("HEAD"))
    if not problems:
        print(f"No hotspot files touched ({len(changes)} changed files checked against {args.base}).")
        return 0
    print("This branch edits files that make parallel PRs conflict (AGENTS.md, Parallel PRs):", file=sys.stderr)
    for problem in problems:
        print(f"  - {problem}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    sys.exit(main())
