---
name: run-pockettechnician
description: Build, install, launch, screenshot, and drive the Pocket Technician Android app on a connected device or emulator via adb. Use when asked to run, start, build, install, screenshot, or test the Pocket Technician app, or to navigate its UI (Dashboard, Chat, Take Photo, Voice, HID).
---

# Run Pocket Technician

Pocket Technician is an **Android app** (Kotlin + Jetpack Compose, minSdk 33,
package `com.pockettechnician.app`). There is no headless window to open — the
only way to drive it is through **adb against a real device or emulator**. The
driver `.claude/skills/run-pockettechnician/driver.sh` wraps the adb verbs you
need: build, install, launch, screenshot, tap, type, key, and dump the UI
hierarchy to find tap targets.

All paths below are relative to the **repo root** (the unit). Run the driver
from there.

> A device must be attached (`adb devices` shows one as `device`). This repo's
> test device is a Galaxy Tab S9 FE connected over USB — the driver was verified
> against it. An emulator works too, but none is installed here and the
> container has **no `/dev/kvm`** (software emulation only, ~600 MB free RAM),
> so a real device is the practical path.

## Prerequisites

The Android toolchain (JDK 17, SDK platform-35, build-tools 35.0.0) lives in
`~/.toolchain` and `local.properties` points `sdk.dir` at it. The driver sources
`scripts/env.sh`, which puts `adb` on PATH and exports `JAVA_HOME`/`ANDROID_HOME`
— so you do **not** need adb on your own PATH. No `apt-get` packages were needed
in this container.

If starting from a bare SDK, the build only needs platform-35 + build-tools
35.0.0 (already present here).

## Run (agent path) — the driver

```bash
D=.claude/skills/run-pockettechnician/driver.sh

$D devices          # confirm a device is attached
$D build            # ./gradlew assembleDebug --no-daemon  (~40s when warm)
$D install          # adb install -r app/build/outputs/apk/debug/app-debug.apk
$D launch           # am start com.pockettechnician.app/.MainActivity
$D ss dashboard     # screenshot -> run-artifacts/dashboard.png
```

Then **read the PNG** in `run-artifacts/` to see the app. Verified output:
the Dashboard shows a left navigation rail (Dashboard, Conversations, Chat,
Take Photo, Gallery, Voice), HID status ("Registered as HID — ready to
connect"), a paired-computer list, masked API keys, and a model picker.

Drive the UI by finding tap targets in the UI dump, then tapping pixel coords:

```bash
$D dump                                                  # -> run-artifacts/ui.xml
grep -oE 'text="Conversations"[^>]*bounds="[^"]*"' run-artifacts/ui.xml
# bounds="[0,297][202,337]"  -> tap the center
$D tap 101 317
$D ss conversations                                      # confirm the screen changed
```

`bounds` from `uiautomator dump` are **real device pixels** — `input tap` uses
the same coordinate space, so tap the center of the bounds directly.

Full verb list (see the header of `driver.sh`): `build install launch stop
restart ss [name] tap X Y text "STR" key KEYCODE back home dump current devices`.

## Run (human path)

`./scripts/install-debug.sh` builds, installs, and launches on a USB-connected
device in one shot (`--no-launch` to skip the launch). That's the developer
loop; the driver above is the same path broken into pokeable steps.

## Test

No instrumented/unit test suite is wired up yet — driving the running app via
the driver is the verification path.

## Gotchas

- **`current` always reports `MainActivity`, even after changing tabs.** It's a
  single-Activity Compose app (`NavigationSuiteScaffold`). Do **not** use the
  activity name to confirm navigation — take a screenshot (`ss`) and look at it.
- **The nav rail vs bottom bar moves with orientation.** On the tablet in
  landscape it's a left rail (targets near x≈0–200); on a phone/portrait it
  renders as a bottom bar. Re-`dump` to get current coordinates — don't reuse
  the ones above blindly.
- **Tapping a computer in the Dashboard list (e.g. "Vaio") starts a real
  Bluetooth HID connection to that PC** and lets the app act as its
  keyboard/mouse. **Take Photo** opens the camera, **Voice** uses the mic, and
  **Chat** sends screen images to a third-party AI provider (costs money / needs
  a configured key). For a smoke test, stay on Dashboard / Conversations /
  Gallery — don't trigger live HID control, capture, or AI calls unless that's
  the explicit goal.
- **Cold start shows a splash screen (the wrench launcher icon).** If you `ss`
  immediately after `launch` you'll capture the splash, not the UI. `launch`
  already settles 3s; if a screenshot still shows only the centered wrench icon
  on a blank background, wait a beat and `ss` again.
- **adb is not on the outer shell's PATH.** Always go through the driver (it
  sources `scripts/env.sh`); a bare `adb ...` will fail with `command not found`.
- **Screenshots are scaled previews; tap coords are not.** The PNG from `ss`
  may be downscaled by the host viewer — never derive tap coordinates by eyeing
  the screenshot. Use `dump` bounds.

## Troubleshooting

- **`error: no authorized device found` / no device in `$D devices`** — attach
  the device over USB, unlock it, and accept the USB-debugging prompt. An
  emulator would also satisfy this, but none is installed here.
- **Build runs out of memory** — `gradle.properties` ships with conservative JVM
  heaps for low-RAM machines (this host has ~5.8 GB). The driver already uses
  `--no-daemon`; if it still OOMs, stop other processes or run
  `./scripts/stop-gradle.sh` to kill lingering daemons.
- **Emulator** — not viable in this container (no `/dev/kvm`, ~600 MB free).
  Installing `emulator` + a system image via `sdkmanager` is possible but will
  be software-rendered and is likely to OOM; use a real device instead.

## Artifacts

Screenshots and UI dumps land in `run-artifacts/` (git-ignored).
