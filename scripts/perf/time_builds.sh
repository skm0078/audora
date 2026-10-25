#!/usr/bin/env bash
# File: scripts/perf/time_builds.sh
# Purpose: Times the app's cold start, first-screen frames and memory for each APK given, on one emulator or phone,
#          and prints a Markdown table (ADR-072). CI compares the debug build with the optimised dev build this way.
#
# Usage: scripts/perf/time_builds.sh <label>=<apk>[=<package>] [...]
#   RUNS (default 10) cold starts per APK; ADB (default: adb on PATH); without a package, AAPT2 (default: the newest
#   in $ANDROID_HOME/build-tools) reads it from the APK.
# Each APK is installed fresh, launched once to settle, then started cold RUNS times (am start -S -W: the process is
# killed first). The table gives the median and worst TotalTime: launch to first frame drawn, as Android reports it.
# Frames: gfxinfo over swipes on whatever the first screen is (the first-run screens on a fresh install).
set -euo pipefail

ADB="${ADB:-adb}"
RUNS="${RUNS:-10}"
ACTIVITY="io.github.skm0078.audora.MainActivity"
SETTLE_SECONDS=5
SWIPES=8

median() { sort -n | awk '{ a[NR] = $1 } END { if (NR == 0) print "n/a"; else print a[int((NR + 1) / 2)] }'; }
worst() { sort -n | tail -n 1; }

swipe_screen() {
    local size w h
    size="$("$ADB" shell wm size | tail -n 1 | awk '{print $NF}' | tr -d '\r')"
    w="${size%x*}"
    h="${size#*x}"
    for _ in $(seq 1 "$SWIPES"); do
        "$ADB" shell input swipe $((w / 2)) $((h * 3 / 4)) $((w / 2)) $((h / 4)) 250
        "$ADB" shell input swipe $((w / 2)) $((h / 4)) $((w / 2)) $((h * 3 / 4)) 250
    done
}

frame_stat() { # <gfxinfo text> <label as gfxinfo prints it>
    printf '%s\n' "$1" | grep -m 1 "$2" | sed -E "s/.*$2:? *//" | tr -d '\r' || true
}

package_of() {
    local aapt2="${AAPT2:-$(find "$ANDROID_HOME/build-tools" -name aapt2 -type f | sort -V | tail -n 1)}"
    "$aapt2" dump packagename "$1"
}

rows=()
for spec in "$@"; do
    IFS='=' read -r label apk package <<< "$spec"
    package="${package:-$(package_of "$apk")}"
    echo "== $label: $apk ($package)"
    "$ADB" uninstall "$package" > /dev/null 2>&1 || true
    "$ADB" install -g "$apk" > /dev/null
    "$ADB" shell am start -S -W -n "$package/$ACTIVITY" > /dev/null
    sleep "$SETTLE_SECONDS"
    # A missing keep rule in an R8 build usually crashes at start: say so loudly instead of timing a dead app.
    if [ -z "$("$ADB" shell pidof "$package" | tr -d '\r')" ]; then
        echo "::error::$label ($package) is not running ${SETTLE_SECONDS}s after start; crash log:"
        "$ADB" logcat -d -b crash | tail -n 60 || true
    fi

    times=()
    for run in $(seq 1 "$RUNS"); do
        total="$("$ADB" shell am start -S -W -n "$package/$ACTIVITY" | grep -E '^TotalTime' | awk '{print $2}' | tr -d '\r')"
        echo "cold start $run: ${total:-?} ms"
        [ -n "$total" ] && times+=("$total")
        sleep 2
    done

    sleep "$SETTLE_SECONDS"
    "$ADB" shell dumpsys gfxinfo "$package" reset > /dev/null
    swipe_screen
    gfx="$("$ADB" shell dumpsys gfxinfo "$package")"
    frames="$(frame_stat "$gfx" "Total frames rendered")"
    janky="$(frame_stat "$gfx" "Janky frames")"
    p50="$(frame_stat "$gfx" "50th percentile")"
    p90="$(frame_stat "$gfx" "90th percentile")"
    p99="$(frame_stat "$gfx" "99th percentile")"
    pss="$("$ADB" shell dumpsys meminfo "$package" | grep -m 1 -E 'TOTAL PSS|TOTAL:' | awk '{print $3}' | tr -d '\r')"
    "$ADB" uninstall "$package" > /dev/null 2>&1 || true

    cold_median="$(printf '%s\n' "${times[@]}" | median)"
    cold_worst="$(printf '%s\n' "${times[@]}" | worst)"
    rows+=("| $label | $cold_median | $cold_worst | ${frames:-?} | ${janky:-?} | ${p50:-?} | ${p90:-?} | ${p99:-?} | ${pss:-?} |")
done

echo
echo "| Build | Cold start median (ms) | Cold start worst (ms) | Frames | Janky frames | Frame p50 | Frame p90 | Frame p99 | Memory PSS (KB) |"
echo "|---|---|---|---|---|---|---|---|---|"
printf '%s\n' "${rows[@]}"
