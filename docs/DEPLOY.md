# Deployment

Manual install flows for development, demos, and (later) Play Store. No CI — run everything locally.

## Prerequisites

| Requirement | Typical location on this machine |
|-------------|----------------------------------|
| JDK 17 | `~/.toolchain/jdk` or set `JAVA_HOME` |
| Android SDK | `local.properties` → `sdk.dir` (see repo root) |
| `adb` | `$ANDROID_HOME/platform-tools/adb` |

Device requirements:

- Android 13+ (`minSdk 33`)
- **USB debugging** enabled (Developer options)
- Accept the **Allow USB debugging?** prompt when connecting USB

Samsung tablets: also enable **Install via USB** in Developer options if `adb install` fails.

## Quick reference

| Goal | Command |
|------|---------|
| Dev install over USB | `./scripts/install-debug.sh` |
| Build release APK | `./scripts/build-release.sh` |
| Install release APK over USB | `./scripts/install-release.sh` |
| Sideload release APK (no PC) | Copy `app-release.apk` to device → tap to install |

---

## 1. USB debug install (daily development)

Use this while iterating on code. Builds a debug APK, installs it, and launches the app.

```bash
./scripts/install-debug.sh
```

Skip auto-launch:

```bash
./scripts/install-debug.sh --no-launch
```

### Troubleshooting

| Symptom | Fix |
|---------|-----|
| `unauthorized` in `adb devices` | Unlock tablet → tap **Allow** on USB debugging dialog |
| No device listed | Try another cable/port; set USB mode to **File transfer** |
| `JAVA_HOME is not set` | Export `JAVA_HOME` or install JDK 17 under `~/.toolchain/jdk` |
| Gradle can't find SDK | Ensure `local.properties` contains `sdk.dir=...` |

Manual equivalent:

```bash
source scripts/env.sh
./gradlew installDebug
adb shell am start -n com.pockettechnician.app/.MainActivity
```

---

## 2. Pre-installed release APK (demos & handoff)

Use this for hackathon demos, judges, or teammates who should not depend on debug tooling.

### One-time: create a release keystore

```bash
keytool -genkey -v \
  -keystore ~/pocket-technician-release.jks \
  -alias pockettechnician \
  -keyalg RSA -keysize 2048 -validity 10000
```

Back up the `.jks` file and passwords somewhere safe (not git). **Losing the keystore means you cannot update the app on Play Store.**

### One-time: configure signing

```bash
cp keystore.properties.example keystore.properties
# Edit keystore.properties with absolute storeFile path and passwords
```

### Build the release APK

```bash
./scripts/build-release.sh
```

Output: `app/build/outputs/apk/release/app-release.apk`

### Install on a demo tablet

**Option A — USB (same as debug):**

```bash
./scripts/install-release.sh
```

**Option B — no laptop at demo venue:**

1. Copy `app-release.apk` to the tablet (Drive, USB file copy, etc.).
2. On the tablet: Settings → Security → allow install from your file app.
3. Open the APK in Files → Install.

The app persists until uninstalled or replaced. Rebuild and reinstall only when you ship a new version.

### Before a live demo

- [ ] Release APK installed and opens cleanly
- [ ] API keys entered on device (BYOK — never in the repo)
- [ ] Bluetooth HID paired to the demo laptop
- [ ] Phone stand position tested against the screen
- [ ] Backup demo video recorded

---

## 3. Google Play (later, manual)

Play Store uploads use an **AAB**, not the release APK.

```bash
source scripts/env.sh
./gradlew bundleRelease
# → app/build/outputs/bundle/release/app-release.aab
```

Before each upload, bump in `app/build.gradle.kts`:

```kotlin
versionCode = 2        // must increase every upload
versionName = "0.2.0"
```

Upload the AAB in [Play Console](https://play.google.com/console) → Testing → Internal testing (or Production).

You will also need: privacy policy URL, Data safety form, store screenshots, and permission justifications (camera, microphone, Bluetooth). See the architecture and safety docs for what the app does.

---

## Versioning

| Field | File | Rule |
|-------|------|------|
| `versionCode` | `app/build.gradle.kts` | Integer; increase on every Play upload |
| `versionName` | `app/build.gradle.kts` | Human-readable label shown in the app info screen |

Debug installs do not require version bumps during development.