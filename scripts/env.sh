# Shared toolchain paths for deploy scripts. Sourced by other scripts in this folder.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -z "${JAVA_HOME:-}" ]]; then
    for candidate in \
        "$HOME/.toolchain/jdk" \
        /usr/lib/jvm/java-17-openjdk-amd64 \
        /usr/lib/jvm/java-17-openjdk; do
        if [[ -x "$candidate/bin/java" ]]; then
            JAVA_HOME="$candidate"
            break
        fi
    done
fi

if [[ -z "${JAVA_HOME:-}" ]] && command -v java >/dev/null 2>&1; then
    JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
fi

if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/java" ]]; then
    echo "error: JDK 17 not found. Set JAVA_HOME or install Temurin 17." >&2
    exit 1
fi

if [[ -f "$ROOT_DIR/local.properties" ]]; then
    SDK_DIR="$(grep '^sdk.dir=' "$ROOT_DIR/local.properties" | cut -d= -f2- | tr -d '\r')"
fi

SDK_DIR="${SDK_DIR:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/.toolchain/android-sdk}}}"

if [[ ! -x "$SDK_DIR/platform-tools/adb" ]]; then
    echo "error: adb not found under $SDK_DIR/platform-tools" >&2
    echo "Set sdk.dir in local.properties or export ANDROID_HOME." >&2
    exit 1
fi

export JAVA_HOME
export ANDROID_HOME="$SDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"
export PATH="$JAVA_HOME/bin:$SDK_DIR/platform-tools:$ROOT_DIR:$PATH"

# Stop Gradle/Kotlin daemons after builds to free RAM on low-memory machines.
gradle_stop() {
    echo "==> Stopping Gradle daemons (freeing memory)"
    (cd "$ROOT_DIR" && ./gradlew --stop) >/dev/null 2>&1 || true
}