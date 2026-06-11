# Architecture

## Tech stack (decided)

| Layer | Choice | Why |
|---|---|---|
| Language | **Kotlin** | `BluetoothHidDevice`, CameraX, Compose, and the Anthropic Java SDK are all Kotlin-native or Kotlin-first |
| UI | **Jetpack Compose** | Fastest path to the 6-tab app |
| Min Android | **API 33 (Android 13)** | Modern permission model, no legacy Bluetooth permission code |
| Camera | CameraX | Simple single-shot capture |
| HID | `BluetoothHidDevice` profile | Phone registers as a combined Bluetooth keyboard/mouse |
| Voice input | Android `SpeechRecognizer` | Built-in, on-device, in MVP from the start |
| Local storage | Room | Conversations and action history stored on the device |
| AI | **Claude `claude-opus-4-8`** via the official **Anthropic Java SDK** (`com.anthropic:anthropic-java`) | Strong vision + agentic reasoning; SDK is fully Kotlin-compatible |
| Key storage | `EncryptedSharedPreferences` (Keystore-backed) | BYOK: user pastes their API key in Settings; never committed, never leaves the phone except to the API |

## UI: 6-tab bottom navigation

| Tab | Purpose |
|------------------|---------------------------------------------------|
| **Dashboard** | Status, controls, and settings |
| **Conversations** | List of past conversations + New Conversation |
| **Chat** | Active conversation |
| **Take Photo** | Camera capture |
| **Gallery** | Select photos from device storage |
| **Voice** | Voice recording and text prompt submission |

### Dashboard tab

- **Model field** — current model ID (config value, fully swappable).
- **Automation slider** — how independently the technician acts:

| Level | Behavior |
|---|---|
| Manual | Every single action needs a tap |
| Step | AI proposes a step ("open network settings"), one approval executes that step's actions |
| Standard | Safe actions (keys, clicks, typing, photos) run automatically; sensitive ones ask |
| Autonomous | Everything runs automatically **except the safety gates below** |

Regardless of slider position: installing software, changing security settings, and entering credentials **always** require approval, and the hard prohibitions in [SAFETY.md](SAFETY.md) are never available at any level.

- **Effort slider** — maps 1:1 to Claude's `output_config.effort` (`low` / `medium` / `high` / `max`).
- **Photo Resolution** setting — default **MAX** (see Photos & Vision below).
- **STOP** — always visible, halts the loop and all HID output instantly.
- **Share internet** — see Tethering below.

### Conversations tab

- List of all stored conversations, **New Conversation** button at the top.
- On opening this tab, if the current conversation is not empty, the model is asked in the background to generate a short, descriptive name for it; the name is saved and shown in the list. Manual rename is still possible.
- Tapping a conversation loads it into the **Chat** tab.

### Chat tab

- The **active conversation**: messages, action history, and approval buttons when required.
- This is where the user interacts with the AI during an ongoing session.

### Take Photo tab

- Large **Take Picture** button (CameraX).
- Captured photos are automatically attached to the current (active) conversation.
- No subject chips — the model identifies what the photo shows on its own.

### Gallery tab

- Browse and select existing photos from the phone's gallery.
- Selected photos are attached to the current (active) conversation.

### Voice tab

- Text input field.
- **Record** button — starts voice recording and clears the text field.
- **Submit** button — sends the text as a prompt to the AI.

## Photos & Vision

Photos are only captured when the user explicitly takes them through the **Take Photo** tab (or attaches them via **Gallery**) — including when the agent asks for one via `request_photo`. A photo can be anything: the computer screen, Wi-Fi details on the office wall, a PC serial plate, keyboard lock LEDs, a BIOS screen.

**Photo Resolution setting** (Dashboard, default **MAX**):

- At **MAX**, photos are sent at **full original resolution with no downscaling** — maximum readability of small on-screen text, error messages, serial numbers, and device labels.
- Lower resolution options can be added later for cost optimization; MAX is the MVP default.

Photos are attached to the conversation as base64 image blocks. The AI interprets the content with its vision capabilities; if clarification is needed, it asks via the `ask_user` action.

## Agent control loop

```
user describes problem (text or voice)
   → model proposes next action (restricted schema only)
   → automation level decides: auto-run or wait for approval
   → HID executes / request_photo pauses for the user
   → user takes or selects a photo when asked
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

## AI layer: model-agnostic with swappable providers (Claude-first for MVP)

Everything in the app depends on one small, stable interface:

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

**`ClaudeBrain`** is the first implementation:

- Official Anthropic Java SDK; model ID is a **config string** (e.g. `claude-opus-4-8`) — fully swappable without code changes.
- **Structured outputs** (`output_config.format` with a JSON schema of the action types) so every response is a guaranteed-valid action — no JSON parsing roulette in the loop.
- **Adaptive thinking** (`thinking: {type: "adaptive"}`) with `effort` taken from the dashboard slider.
- Photos sent as base64 at the resolution selected in the Dashboard (default = full resolution).
- Conversation history resent each turn (the API is stateless); stable system prompt first for prompt caching.

**Model swappability is a core design goal.** Any future provider only needs to implement the same interface — the action schema, agent control loop, UI, and safety gates remain unchanged. A `GrokBrain` implementation can later be added using the official OpenAI Java SDK pointed at `https://api.x.ai/v1`. (The Claude implementation always uses the official Anthropic SDK directly — no OpenAI-compatible shim.)

## Conversation storage and naming

- All conversations are stored locally on the device (Room).
- When the user opens the **Conversations** tab and the current conversation has any messages or actions, the model generates a short, meaningful name in the background; it is saved and displayed in the list.
- Manual rename remains available.

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
