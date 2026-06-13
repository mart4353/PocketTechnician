#!/usr/bin/env bash
# Driver for Pocket Technician — build, install, launch and DRIVE the Android
# app on a connected device (or emulator) via adb.
#
# Pocket Technician is an Android app: there is no headless "window" to open.
# The only way to drive it programmatically is through adb against a real
# device (a Galaxy Tab is the project's test device) or an emulator. This
# script wraps the adb verbs an agent needs: install, launch, screenshot,
# tap/type/key, and dump the UI hierarchy so you can find tap targets.
#
# Usage:  ./driver.sh <command> [args]
# Run from the unit root (the repo root). Sources scripts/env.sh for the
# toolchain (JAVA_HOME / ANDROID_HOME / adb on PATH).
#
# Commands:
#   build              assembleDebug (no daemon — RAM-friendly)
#   install            install the freshly built debug APK (-r, keeps data)
#   launch             start MainActivity
#   stop               force-stop the app
#   restart            stop + launch
#   ss [name]          screenshot -> run-artifacts/<name>.png (default: shot)
#   tap X Y            tap at pixel coords
#   text "STR"        type a string into the focused field
#   key KEYCODE        send a key event (e.g. KEYCODE_BACK, 4 = back, 3 = home)
#   back | home        shortcuts for the obvious keys
#   dump               dump UI hierarchy -> run-artifacts/ui.xml (find tap targets)
#   current            print the currently focused activity
#   devices            list adb devices
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
cd "$ROOT_DIR"
# shellcheck source=/dev/null
source scripts/env.sh   # puts adb on PATH, sets JAVA_HOME/ANDROID_HOME

PKG="com.pockettechnician.app"
ACT="$PKG/.MainActivity"
APK="app/build/outputs/apk/debug/app-debug.apk"
ART="run-artifacts"
mkdir -p "$ART"

cmd="${1:-}"; shift || true
case "$cmd" in
  build)    ./gradlew assembleDebug --no-daemon ;;
  install)  adb install -r "$APK" ;;
  launch)   adb shell am start -n "$ACT" >/dev/null && sleep 3 && echo "launched $ACT (settled 3s)" ;;
  stop)     adb shell am force-stop "$PKG" && echo "stopped $PKG" ;;
  restart)  adb shell am force-stop "$PKG"; adb shell am start -n "$ACT" >/dev/null && echo "restarted" ;;
  ss)
    name="${1:-shot}"
    adb exec-out screencap -p > "$ART/$name.png"
    echo "$ART/$name.png ($(wc -c < "$ART/$name.png") bytes)"
    ;;
  tap)      adb shell input tap "$1" "$2" && echo "tap $1 $2" ;;
  text)     adb shell input text "${1// /%s}" && echo "typed" ;;   # %s = space
  key)      adb shell input keyevent "$1" && echo "key $1" ;;
  back)     adb shell input keyevent 4 && echo "back" ;;
  home)     adb shell input keyevent 3 && echo "home" ;;
  dump)
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null
    adb pull /sdcard/ui.xml "$ART/ui.xml" >/dev/null && echo "$ART/ui.xml"
    ;;
  current)  adb shell dumpsys activity activities | grep -m1 -E "mResumedActivity|ResumedActivity" || true ;;
  devices)  adb devices -l ;;
  *)
    echo "unknown command: '$cmd'" >&2
    sed -n '2,40p' "$0" >&2
    exit 2
    ;;
esac
