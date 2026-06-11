#!/usr/bin/env bash
# Build a signed release APK for sideloading or demo handoff.
set -euo pipefail

source "$(dirname "$0")/env.sh"
cd "$ROOT_DIR"

OUTPUT_APK="$ROOT_DIR/app/build/outputs/apk/release/app-release.apk"

if [[ ! -f "$ROOT_DIR/keystore.properties" ]]; then
    echo "error: keystore.properties not found." >&2
    echo "Copy keystore.properties.example to keystore.properties and create a release keystore first." >&2
    echo "See docs/DEPLOY.md for the full release setup." >&2
    exit 1
fi

echo "==> Building signed release APK"
./gradlew assembleRelease

if [[ ! -f "$OUTPUT_APK" ]]; then
    echo "error: expected APK at $OUTPUT_APK" >&2
    exit 1
fi

ls -lh "$OUTPUT_APK"
echo ""
echo "Release APK ready:"
echo "  $OUTPUT_APK"
echo ""
echo "Install on a connected device: ./scripts/install-release.sh"
echo "Or copy the APK to the tablet and install from Files (enable install unknown apps)."