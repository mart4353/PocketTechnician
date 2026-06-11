# Pocket Technician — Hackathon MVP Plan

## Goal

Real development time is ~6–8 hours. The goal is **not** to solve every computer problem. The goal is to prove one complete support loop:

> See the problem → understand it → act on the computer → verify the result.

## Demonstration scenario

A Windows or Linux computer has lost internet access. The user tells Pocket Technician: *"My computer has no internet."*

Pocket Technician then:

1. Observes the screen through the phone camera.
2. Identifies the visible network state.
3. Uses Bluetooth keyboard and mouse actions to open network settings.
4. Attempts to restore the normal connection.
5. If necessary, offers temporary internet through USB tethering.
6. **Verifies** that the computer is online.
7. Explains what was changed.

Success must be an **observable result** (OS reports "Connected", a webpage loads, the network error disappears) — never just the AI's belief that the repair probably worked.

## MVP components

| # | Component | Description |
|---|-----------|-------------|
| 1 | Split-screen UI (Compose) | Dashboard (model field, Automation slider, Effort slider, STOP, Share internet) + chat with voice input, approval buttons |
| 2 | User-initiated photos | Shutter button + subject chips (Screen / Wi-Fi info / Device label / Other); agent requests photos via `request_photo` |
| 3 | Bluetooth control | Keyboard shortcuts, Tab/Shift+Tab, arrows, Enter/Escape, limited mouse movement and clicking |
| 4 | Restricted action engine | The AI returns only approved action types; automation level gates execution; all actions are logged |
| 5 | Outcome verification | Loop finishes only when a user photo shows the observable success signal |

Stack: Kotlin, Jetpack Compose, minSdk 33, CameraX, `BluetoothHidDevice`, `SpeechRecognizer`, Anthropic Java SDK with `claude-opus-4-8` (BYOK API key in app settings). See [ARCHITECTURE.md](ARCHITECTURE.md).

## Milestones

### M0 — Foundations (hour 0–1)
- [ ] Android project skeleton (Kotlin, Compose, minSdk 33; camera + Bluetooth permissions)
- [ ] Confirm test laptop accepts Bluetooth HID pairing from the dev phone
- [ ] Settings screen: paste API key → `EncryptedSharedPreferences`
- [ ] Claude call working from the phone via Anthropic Java SDK (image in → structured-output action JSON out)

### M1 — Hands: Bluetooth HID (hour 1–3)
- [ ] Register the phone as a Bluetooth HID keyboard/mouse (`BluetoothHidDevice` API)
- [ ] Send single keys, key combinations, and typed text
- [ ] Send relative mouse movement and clicks
- [ ] Hardcoded "smoke test" macro: open network settings on the test laptop via keyboard only

### M2 — Eyes + ears: photos and voice (hour 3–4)
- [ ] Shutter button: one photo per press, attached to the conversation (CameraX)
- [ ] Subject chips: Screen / Wi-Fi info / Device label / Other
- [ ] Voice input via `SpeechRecognizer` (mic button in chat)
- [ ] Photo (downscaled, base64) + task context sent to Claude; structured-output action returned

### M3 — Brain: agent loop + dashboard (hour 4–6)
- [ ] Loop: propose action → automation gate → execute / `request_photo` pause → verify
- [ ] Restricted action set only: press key, key combo, move pointer, click, type text, wait, request photo, ask user, request approval, finish
- [ ] Dashboard: model field, Automation slider (Manual/Step/Standard/Autonomous), Effort slider (→ `output_config.effort`)
- [ ] Action log visible in the app
- [ ] Emergency stop halts the loop instantly

### M4 — Demo scenario + verification (hour 6–8)
- [ ] End-to-end run of the "no internet" scenario on the test laptop
- [ ] Verification step: loop only finishes when an observable success signal is seen
- [ ] Fallback path: offer USB tethering if the normal connection cannot be restored
- [ ] Rehearse the demo twice; record a backup video

### Stretch (only if time remains)
- Login-screen control (experimental)
- BIOS/UEFI keyboard input test (bonus)

## Task breakdown by role

| Role | Owns |
|------|------|
| Android / Bluetooth HID | M1; HID pairing, key/mouse reports |
| Multimodal AI / agent loop | M2, M3; prompting, action schema, verification logic |
| UI | Chat screen, approval buttons, stop button, action log |
| Product Lead (Mart) | Concept, MVP priorities, UX, testing, team coordination, final pitch |
| Windows/Linux troubleshooting | Demo scenario design, test laptop preparation, failure-mode rehearsal |

## Risks and mitigations

| Risk | Mitigation |
|------|------------|
| Test laptop refuses HID pairing or input | Verify pairing in M0, before building anything else; keep a second laptop ready |
| Camera can't read the screen reliably (glare, angle, focus) | Fix phone stand position early; test capture quality in M0/M2; prefer high-contrast OS theme |
| Mouse positioning too imprecise from camera coordinates | Bias toward keyboard-only navigation (Tab, arrows, shortcuts) for the demo |
| AI loop too slow per step | Cache context, lower image resolution, keep prompts short; scripted demo path as fallback |
| Live demo failure | Backup video recorded in M4 |

## Out of scope for the MVP

- Solving arbitrary computer problems
- iOS support
- BIOS/UEFI guarantees
- Autonomous destructive actions of any kind (see [SAFETY.md](SAFETY.md))
