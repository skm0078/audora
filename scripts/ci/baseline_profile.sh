#!/usr/bin/env bash
# File: scripts/ci/baseline_profile.sh
# Purpose: Records the app's Baseline Profile on a booted emulator or phone (Android 13+) and writes it to
#          app/src/main/baseline-prof.txt, where the build compiles it into every APK (ADR 2026-10-30-baseline-profile).
#
# Usage: scripts/ci/baseline_profile.sh
# Runs :baselineprofile's generator against :app's benchmark build (x86_64: the emulator CI boots). Commit the
# written file when it changed: the profile is source, reviewed like code, never made during a normal build.
set -euo pipefail

PROFILE="app/src/main/baseline-prof.txt"
OUTPUT_DIR="baselineprofile/build/outputs/connected_android_test_additional_output"

./gradlew :baselineprofile:connectedBenchmarkAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile --stacktrace

recorded="$(find "$OUTPUT_DIR" -name '*-baseline-prof.txt' | head -n 1)"
[ -n "$recorded" ] || { echo "::error::The generator ran but wrote no *-baseline-prof.txt under $OUTPUT_DIR"; exit 1; }

# Every rule, the libraries' too: not every library ships its own profile, and the build merges duplicates.
cp "$recorded" "$PROFILE"
echo "Wrote $(wc -l < "$PROFILE") rules to $PROFILE ($(grep -c 'io/github/skm0078/audora/' "$PROFILE" || true) the app's own)"
git --no-pager diff --stat -- "$PROFILE" || true
