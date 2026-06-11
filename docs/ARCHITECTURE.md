# Architecture

## Tech stack (decided)

| Layer | Choice | Why |
|---|---|---|
| Language | **Kotlin** | `BluetoothHidDevice`, CameraX, Compose, and the Anthropic Java SDK are all Kotlin-native or Kotlin-first |
| UI | **Jetpack Compose** | Fastest path to chat + dashboard split screen |
| Min Android | **API 33 (Android 13)** | Modern permission model, no legacy Bluetooth permission code |
| Camera | CameraX | Simple single-shot capture |
| HID | `BluetoothHidDevice` profile | Phone registers as a combined Bluetooth keyboard/mouse |
| Voice input | Android `SpeechRecognizer` | Built-in, on-device, in MVP from the start |
| AI | **Claude `claude-opus-4-8`** via the official **Anthropic Java SDK** (`com.anthropic:anthropic-java`) | Strong vision + agentic reasoning; SDK is fully Kotlin-compatible |
| Key storage | `EncryptedSharedPreferences` (Keystore-backed) | BYOK: user pastes their API key in Settings; never committed, never leaves the phone except to the API |

## UI: split screen — Dashboard + Chat

```
┌────────────────────────────────────────────────┐
│  DASHBOARD                                     │
│  Model: claude-opus-4-8            ● AI ACTIVE │
│  Automation  [Manual ──●────── Autonomous]     │
│  Effort      [low ── medium ──●── high ── max] │
│  [ STOP ]                [ Share internet ]    │
├────────────────────────────────────────────────┤
│  CHAT                                          │
│  user/technician messages, action log,         │
│  approval buttons                              │
│  [🎤 voice]  [📷 shutter]  chips: Screen │     │
│  Wi-Fi info │ Device label │ Other             │
└────────────────────────────────────────────────┘
```

### Dashboard controls

- **Model field** — shows the active model ID (config value; swappable without code changes).
- **Effort slider** — maps 1:1 to Claude's `output_config.effort` (`low` / `medium` / `high` / `max`). Cheap quick fixes on low, hard diagnoses on high/max.
- **Automation slider** — how independently the technician acts:

| Level | Behavior |
|---|---|
| Manual | Every single action needs a tap |
| Step | AI proposes a step ("open network settings"), one approval executes that step's actions |
| Standard | Safe actions (keys, clicks, typing, photos) run automatically; sensitive ones ask |
| Autonomous | Everything runs automatically **except the safety gates below** |

Regardless of slider position: installing software, changing security settings, and entering credentials **always** require approval, and the hard prohibitions in [SAFETY.md](SAFETY.md) are never available at any level.

- **STOP** — always visible, halts the loop and all HID output instantly.
- **Share internet** — see Tethering below.

## Camera: user-initiated photos only

There is no periodic capture. The user is the camera trigger:

- The **shutter button** takes one photo per press and attaches it to the conversation.
- **Subject chips** (Screen / Wi-Fi info / Device label / Other) optionally tag what the photo shows — the computer screen, a router sticker on the office wall, the serial plate on the back of a PC, keyboard lock LEDs, a BIOS screen.
- When the agent loop needs to see something, it emits a **`request_photo`** action and pauses until the user presses the shutter. Verification works the same way: the loop only finishes after a user-supplied photo shows the observable success signal.

This gives a clean privacy story (nothing is captured without a press), cuts image-token cost, and supports non-screen subjects naturally.

## Agent control loop

```
user describes problem (text or voice)
   → model proposes next action (restricted schema only)
   → automation level decides: auto-run or wait for approval
   → HID executes / request_photo pauses for the user
   → user takes photo when asked
   → model verifies against observable evidence
   → finish (success explained) or continue / ask user
```

The loop **never finishes on belief alone** — `finish` requires observable evidence in a photo (e.g. "Connected" status, a loaded webpage, the error gone).

## Restricted action schema

| Action | Parameters | Sensitive? |
|--------|-----------|------------|
| `press_key` | key | no |
| `key_combo` | keys[] | no |
| `move_pointer` | dx, dy | no |
| `click` | button | no |
| `type_text` | text | yes if credentials |
| `wait` | seconds | no |
| `request_photo` | what to photograph | no (pauses loop) |
| `ask_user` | question | n/a (pauses loop) |
| `request_approval` | description | n/a (pauses loop) |
| `finish` | summary, evidence | no |

Anything outside the schema is rejected by the action engine on the phone — safety is enforced by the schema validator, not by prompt instructions.

## AI layer: model-agnostic, Claude-first

Everything in the app depends on one small interface:

```kotlin
interface TechnicianBrain {
    suspend fun nextTurn(
        task: String,
        history: List<Turn>,
        photos: List<TaggedPhoto>,
        effort: Effort,
    ): TechnicianTurn   // = restricted action schema + explanation text
}
```

`ClaudeBrain` is the first (and for the MVP, only) implementation, using the Anthropic Java SDK from Kotlin:

- Model: `claude-opus-4-8` (config string — swappable).
- **Structured outputs** (`output_config.format` with a JSON schema of the action types) so every response is a guaranteed-valid action — no JSON parsing roulette in the loop.
- **Adaptive thinking** (`thinking: {type: "adaptive"}`) with `effort` taken from the dashboard slider.
- Photos sent as base64 image blocks, downscaled to ~1568 px long edge to control token cost.
- Conversation history resent each turn (the API is stateless); stable system prompt first for prompt caching.

A future provider (local model, another vendor) implements the same interface; the action schema, loop, UI, and safety gates never change. Per project policy, the Claude implementation uses the official Anthropic SDK directly — no OpenAI-compatible shim.

## Tethering ("Share internet" button)

Android has **no public API to toggle USB tethering programmatically** — only system Settings can. The floor the OS allows is two taps:

1. The **Share internet** button deep-links to the system tethering screen (fallback: wireless settings on OEM skins that block the direct intent) and shows a 2-step overlay: plug in the USB cable → flip "USB tethering".
2. The app **detects success itself** by watching for the `rndis`/`usb` network interface to come up, and informs the agent loop so verification can continue on the computer side.

## API key setup (MVP: bring-your-own-key)

- Settings screen with a "Paste API key" field; stored in `EncryptedSharedPreferences`.
- Each team member creates their own key at platform.claude.com (Console → API keys).
- `.gitignore` already blocks key files; keys are never hardcoded.
- Post-hackathon: a small proxy backend so keys never live on devices (out of MVP scope).

## Why the phone, not the computer

Everything runs on the phone. The target computer sees only a standard Bluetooth keyboard and mouse — no agent, no driver, no network requirement. This is what lets Pocket Technician work on machines with broken internet, missing software, or (experimentally) before the OS has fully started.
