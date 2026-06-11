# Architecture

## System overview

```
┌─────────────────────────── Android phone ───────────────────────────┐
│                                                                      │
│  ┌──────────┐   ┌──────────────┐   ┌──────────────────────────┐      │
│  │ Chat UI  │──▶│  Agent loop  │──▶│ Restricted action engine │      │
│  │ approve/ │◀──│ (orchestrator│   │  (validates + logs every │      │
│  │   stop   │   │  + verifier) │   │       action)            │      │
│  └──────────┘   └──────┬───────┘   └────────────┬─────────────┘      │
│                        │                        │                    │
│                 ┌──────▼───────┐         ┌──────▼───────┐            │
│                 │   Camera     │         │ Bluetooth HID │           │
│                 │  capture     │         │ keyboard +    │           │
│                 └──────┬───────┘         │ mouse         │           │
│                        │                 └──────┬───────┘            │
│                 ┌──────▼───────┐                │                    │
│                 │ Multimodal   │                │                    │
│                 │ AI (cloud,   │                │                    │
│                 │ via phone's  │                │                    │
│                 │ own network) │                │                    │
│                 └──────────────┘                │                    │
└─────────────────────────────────────────────────┼────────────────────┘
                                                  │ Bluetooth
                  phone camera ───── sees ─────┐  │
                                               ▼  ▼
                                    ┌──────────────────────┐
                                    │  Target computer     │
                                    │  (Windows / Linux)   │
                                    └──────────────────────┘
                                               ▲
                              optional USB tethering (emergency internet)
```

## Agent control loop

```
capture screen frame
   → multimodal model interprets frame + task context
   → model proposes next action (restricted schema only)
   → engine validates action type; sensitive actions require user approval
   → HID executes action
   → wait / re-capture
   → verifier checks for observable success signal
   → finish (success explained) or continue / ask user
```

The loop **never finishes on belief alone** — it finishes only when the verifier sees an observable success signal on screen (e.g. "Connected" status, a loaded webpage, the error gone).

## Restricted action schema

The model may only return actions of these types:

| Action | Parameters | Needs approval? |
|--------|-----------|-----------------|
| `press_key` | key | no |
| `key_combo` | keys[] | no |
| `move_pointer` | dx, dy | no |
| `click` | button | no |
| `type_text` | text | no (yes if credentials) |
| `wait` | seconds | no |
| `ask_user` | question | n/a (pauses loop) |
| `request_approval` | description | n/a (pauses loop) |
| `finish` | summary, evidence | no |

Anything outside the schema is rejected by the action engine. Installing software, changing security settings, and entering credentials always go through `request_approval`. Deletion, formatting, account removal, and factory reset are never available as autonomous actions.

## Key Android pieces

- **Bluetooth HID:** `BluetoothHidDevice` profile (API 28+) to register the phone as a combined keyboard/mouse and send HID reports.
- **Camera:** CameraX periodic capture; frames downscaled before upload.
- **AI:** multimodal model called over the phone's own Wi-Fi/mobile data, so the target computer's connectivity doesn't matter.
- **Tethering:** user-initiated USB tethering as the emergency internet path (Android cannot enable it fully programmatically; the app guides the user).

## Why the phone, not the computer

Everything runs on the phone. The target computer sees only a standard Bluetooth keyboard and mouse — no agent, no driver, no network requirement. This is what lets Pocket Technician work on machines with broken internet, missing software, or (experimentally) before the OS has fully started.
