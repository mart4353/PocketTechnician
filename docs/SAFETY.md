# Safety model

The user must remain in control at all times. Pocket Technician operates a real computer with real consequences, so safety is a product requirement, not a feature.

## Required in every prototype

1. **Visible control indicator** — the app clearly shows when AI control is active.
2. **Emergency stop** — a single, always-visible button that halts the agent loop and all HID output immediately.
3. **Action history** — every executed action is logged and visible to the user.
4. **Approval gates** — explicit user approval is required before:
   - installing any software;
   - changing security settings;
   - entering credentials.
5. **Hard prohibitions** — never available as autonomous actions, with or without approval flow:
   - deleting files or data;
   - formatting drives;
   - removing user accounts;
   - factory reset of any device.

## Enforcement point

Safety is enforced by the **restricted action engine on the phone**, not by prompt instructions alone. The AI model can only request actions from the approved schema (see [ARCHITECTURE.md](ARCHITECTURE.md)); the engine validates each action before execution and routes sensitive ones through the approval flow. A prompt-only safety layer would be bypassable; the schema validator is the real gate.

## Verification over confidence

The system must not claim success because the model believes the repair probably worked. `finish` requires observable evidence captured by the camera, such as:

- the operating system reports "Connected";
- a webpage loads;
- the original error message is gone.

## Trust framing for the demo

During the demo, narrate the safety features: show the active-control indicator, trigger the emergency stop once, and show the action log. The pitch claim is supervised autonomy — the technician works, the user watches and can always pull the plug.
