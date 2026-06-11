#!/usr/bin/env bash
# Build, install, and launch the debug app on a USB-connected device.
set -euo pipefail

source "$(dirname "$0")/env.sh"
cd "$ROOT_DIR"
trap gradle_stop EXIT

LAUNCH=1
if [[ "${1:-}" == "--no-launch" ]]; then
    LAUNCH=0
fi

echo "==> Checking connected devices"
if ! adb devices | awk 'NR>1 && $2=="device" { found=1 } END { exit !found }'; then
    echo "error: no authorized device found." >&2
    echo "Unlock the device and accept the USB debugging prompt, then retry." >&2
    adb devices -l >&2 || true
    exit 1
fi

adb devices -l

echo "==> Building and installing debug build"
./gradlew installDebug

if [[ "$LAUNCH" -eq 1 ]]; then
    echo "==> Launching Pocket Technician"
    adb shell am start -n com.pockettechnician.app/.MainActivity
fi

echo "Done."