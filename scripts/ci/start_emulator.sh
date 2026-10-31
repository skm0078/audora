#!/usr/bin/env bash
# File: scripts/ci/start_emulator.sh
# Purpose: Installs, creates and boots CI's x86_64 emulator, and returns once Android has finished booting. Shared by
#          the device tests (android.yml) and the Baseline Profile run (baseline-profile.yml).
#
# Usage: EMULATOR_IMAGE="system-images;android-34;aosp_atd;x86_64" [EMULATOR_SCREEN=1080x2400x420] \
#            scripts/ci/start_emulator.sh
#   EMULATOR_SCREEN, when set, is the screen's width x height in pixels x density in dpi; otherwise the image's own.
# Plain sdkmanager/avdmanager/emulator calls instead of a third-party emulator action (AGENTS.md: no new
# dependencies). Needs KVM enabled on the runner first. The emulator's own log is written to ./emulator.log.
set -euo pipefail

: "${EMULATOR_IMAGE:?Set EMULATOR_IMAGE to the system image to boot}"
BOOT_POLLS=120
BOOT_POLL_SECONDS=5

tools="$ANDROID_HOME/cmdline-tools/latest/bin"
adb="$ANDROID_HOME/platform-tools/adb"
emulator="$ANDROID_HOME/emulator/emulator"

fail() {
    echo "::error::$1"
    tail -n 80 emulator.log 2> /dev/null || true
    exit 1
}

yes | "$tools/sdkmanager" --licenses > /dev/null || true
# The system image download sometimes arrives broken ("Error on ZipFile unknown archive"); a fresh try fixes it.
for attempt in 1 2 3; do
    "$tools/sdkmanager" --install emulator platform-tools "$EMULATOR_IMAGE" > /dev/null && break
    [ "$attempt" -lt 3 ] || fail "sdkmanager could not install $EMULATOR_IMAGE after 3 tries"
    echo "::warning::sdkmanager failed (try $attempt of 3); trying again"
done
# Pin where the AVD lives: the runner's older avdmanager and the new emulator disagree on the default.
export ANDROID_AVD_HOME="$RUNNER_TEMP/avd"
mkdir -p "$ANDROID_AVD_HOME"
echo no | "$tools/avdmanager" create avd --force --name ci --package "$EMULATOR_IMAGE"
if [ -n "${EMULATOR_SCREEN:-}" ]; then
    IFS=x read -r width height density <<< "$EMULATOR_SCREEN"
    printf 'hw.lcd.width=%s\nhw.lcd.height=%s\nhw.lcd.density=%s\n' "$width" "$height" "$density" \
        >> "$ANDROID_AVD_HOME/ci.avd/config.ini"
fi
if ! "$emulator" -list-avds | grep -qx ci; then
    find "$HOME" "$RUNNER_TEMP" -name 'ci.ini' 2> /dev/null || true
    fail "AVD 'ci' was not created in $ANDROID_AVD_HOME"
fi
"$emulator" -accel-check || true
"$adb" start-server
nohup "$emulator" -avd ci -no-window -no-audio -no-boot-anim -no-snapshot \
    -gpu swiftshader_indirect -camera-back none -camera-front none -memory 4096 > emulator.log 2>&1 &
emulator_pid=$!
booted=""
for _ in $(seq 1 "$BOOT_POLLS"); do
    kill -0 "$emulator_pid" 2> /dev/null || fail "Emulator exited before booting"
    booted="$(timeout 10 "$adb" shell getprop sys.boot_completed 2> /dev/null | tr -d '\r' || true)"
    [ "$booted" = "1" ] && break
    sleep "$BOOT_POLL_SECONDS"
done
[ "$booted" = "1" ] || fail "Emulator did not finish booting"
echo "Emulator booted"
