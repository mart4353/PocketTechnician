# Disclaimer

**Pocket Technician is experimental software. Use it at your own risk.**

Please read this before installing or running the app.

## What this software does

Pocket Technician turns an Android phone into a tool that can **observe and
physically control another computer**. It does this by:

- using the phone camera to look at the target computer's screen;
- pairing as a **Bluetooth keyboard and mouse (HID)** to send real keystrokes
  and pointer movements to that computer;
- sending captured screen images to a **third-party AI provider** (for example
  Anthropic Claude or xAI Grok, using API keys that *you* supply) so the AI can
  interpret the screen and plan actions.

Because it can type, click, enter credentials, and launch or install software
on the target machine, **incorrect actions can cause data loss, security
exposure, misconfiguration, or other harm.**

## No warranty

This software is provided **"AS IS", without warranty of any kind**, express or
implied, including but not limited to the warranties of merchantability,
fitness for a particular purpose, and non-infringement. See the
[LICENSE](LICENSE) (Apache License 2.0) for the full terms. To the maximum
extent permitted by law, the authors and contributors are **not liable** for
any damages or losses arising from the use or inability to use this software.

## Your responsibilities

By using Pocket Technician, you agree that you are responsible for:

- **Supervision.** Stay in control. Watch what the AI does and use the
  emergency stop. Do not leave it running autonomously on an unattended
  machine.
- **Authorization.** Only use it on computers you own or have explicit
  permission to operate. Using a device to control a computer without consent
  may be illegal in your jurisdiction.
- **Sensitive data.** Screen contents — which may include passwords, personal
  information, or confidential material — are transmitted to the third-party AI
  provider you configure. Review that provider's privacy and data-retention
  policies, and avoid pointing the camera at sensitive information you do not
  want sent off-device.
- **Your own keys and costs.** You supply your own AI provider API keys and are
  responsible for any usage charges and for complying with that provider's
  terms of service.

## Not affiliated

Pocket Technician is an independent project. It is **not affiliated with,
endorsed by, or sponsored by** Anthropic, xAI, Google, Android, or any other
company whose products, services, or trademarks may be referenced. All
trademarks are the property of their respective owners; references are made
only to describe interoperability.

## Safety model

See [docs/SAFETY.md](docs/SAFETY.md) for the intended safety controls
(visible control indicator, emergency stop, action history, and explicit
approval for sensitive operations). These controls are part of the project's
design goals and do not constitute a guarantee of safe operation.
