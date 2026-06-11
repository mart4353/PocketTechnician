#!/usr/bin/env bash
# Install a release APK on a USB-connected device.
set -euo pipefail

source "$(dirname "$0")/env.sh"
cd "$ROOT_DIR"

APK="${1:-$ROOT_DIR/app/build/outputs/apk/release/app-release.apk}"
LAUNCH=1
if [[ "${2:-}" == "--no-launch" || "${1:-}" == "--no-launch" ]]; then
    LAUNCH=0
fi

if [[ ! -f "$APK" ]]; then
    echo "error: APK not found: $APK" >&2
    echo "Run ./scripts/build-release.sh first, or pass the APK path as the first argument." >&2
    exit 1
fi

echo "==> Checking connected devices"
if ! adb devices | awk 'NR>1 && $2=="device" { found=1 } END { exit !found }'; then
    echo "error: no authorized device found." >&2
    adb devices -l >&2 || true
    exit 1
fi

adb devices -l

echo "==> Installing $APK"
adb install -r "$APK"

if [[ "$LAUNCH" -eq 1 ]]; then
    echo "==> Launching Pocket Technician"
    adb shell am start -n com.pockettechnician.app/.MainActivity
fi

echo "Done."