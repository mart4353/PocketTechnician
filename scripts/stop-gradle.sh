#!/usr/bin/env bash
# Stop Gradle/Kotlin daemons to free memory when you are done building.
set -euo pipefail

source "$(dirname "$0")/env.sh"
cd "$ROOT_DIR"
gradle_stop
echo "Done."