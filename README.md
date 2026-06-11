# Pocket Technician

**A computer support expert in your phone.**

Pocket Technician turns an Android phone into an AI-powered computer support technician. The user points the phone camera at the computer screen, pairs the phone as a Bluetooth keyboard and mouse, and explains the problem through chat. The AI can then observe the screen, operate the computer, provide temporary internet if needed, and verify that the issue has been resolved — **without installing any support software on the computer**.

> AI support that can see the problem, operate the computer, and verify that the issue has been resolved.

## How it works

1. Open the Pocket Technician app on an Android phone.
2. Place the phone in a stand facing the computer screen.
3. Pair the phone with the computer as a Bluetooth keyboard and mouse (HID — Human Interface Device).
4. Describe the problem through chat or voice.
5. Let the AI technician observe and control the computer under your supervision.

The phone acts as the technician's:

| Role | Mechanism |
|------|-----------|
| **Eyes** | The camera observes the computer screen |
| **Hands** | Bluetooth keyboard and mouse (HID) control |
| **Brain** | A multimodal AI model interprets the screen, plans actions, and checks results |
| **Connection** | The phone's own Wi-Fi or mobile data for AI access |
| **Emergency internet** | USB tethering can share the phone's internet with the computer |

## Why it's different

Traditional remote support depends on working internet on the computer, pre-installed support software, a functioning operating system, and a user who can describe the problem. Pocket Technician works **from outside the computer**: it uses the physical screen and standard keyboard/mouse input, so nothing needs to be installed on the target machine.

Unlike dedicated KVM (Keyboard, Video, Mouse) hardware with AI agents (e.g. NanoKVM Pro's experimental Computer Use Agent), Pocket Technician needs **no dedicated hardware** — only an Android phone, the app, Bluetooth pairing, a phone stand, and optionally a USB cable.

## Platform scope (v1)

- **Phone:** Android (iOS cannot currently emulate a Bluetooth keyboard/mouse from a normal app)
- **Computer:** Windows or Linux
- **Control:** Bluetooth HID keyboard and mouse
- **Observation:** phone camera
- **AI:** multimodal model accessed over the phone's own connection
- **Extra:** optional USB internet tethering

Desktop control is the main target. Login and recovery screens are experimental. BIOS/UEFI control (before the operating system loads) is a bonus, not a requirement.

## Safety

The user must remain in control at all times:

- visible indicator when AI control is active;
- immediate emergency stop button;
- full action history;
- explicit approval before installing software, changing security settings, or entering credentials;
- no autonomous deletion, formatting, account removal, or factory reset.

See [docs/SAFETY.md](docs/SAFETY.md) for the full safety model.

## Documentation

- [docs/PLAN.md](docs/PLAN.md) — hackathon MVP plan, milestones, and task breakdown
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — system architecture and the agent control loop
- [docs/SAFETY.md](docs/SAFETY.md) — safety requirements and restricted action set

## Status

Pre-hackathon planning. The MVP goal is to prove one complete support loop:

> **See the problem → understand it → act on the computer → verify the result.**
