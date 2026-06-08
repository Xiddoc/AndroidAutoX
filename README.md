# AndroidAutoX 🚗⚙️

![AndroidAutoX](assets/banner.png)

[![License: GPL v2](https://img.shields.io/badge/License-GPLv2-blue.svg)](LICENSE)
[![Platform: Android](https://img.shields.io/badge/Platform-Android-3DDC84.svg)](https://github.com/Xiddoc/AndroidAutoX)
[![Build](https://github.com/Xiddoc/AndroidAutoX/actions/workflows/build.yml/badge.svg)](https://github.com/Xiddoc/AndroidAutoX/actions/workflows/build.yml)
[![Coverage: 100%](https://img.shields.io/badge/coverage-100%25-brightgreen.svg)](https://github.com/Xiddoc/AndroidAutoX/actions/workflows/build.yml)

The ultimate All-In-One utility to tweak Android Auto behaviour.

## ✨ Features

- Patch custom Android Auto apps
- Disable speed restrictions while driving
- Disable the six-tap "pay attention to road" limit
- Disable Bluetooth auto-connection
- Force (or force-disable) widescreen
- Force portrait mode
- Enable MultiDisplay / cluster-sim support
- Force-enable the Coolwalk UI
- Tune notification & media notification duration
- Set video bitrate, and disable unnecessary telemetry

…and much more 🎛️

## 📋 Requirements

- A rooted Android device (Magisk)
- **For the AutoX projection feature only:** [LSPosed](https://github.com/JingMatrix/LSPosed)
  in addition to root (see below)

### AutoX projection requires LSPosed

The Phenotype/Gearhead tweaks need only root. The **AutoX** virtual-display projection feature
additionally requires the AndroidAutoX **LSPosed** module — there is no root-only path for its
trusted-display flag and cross-display input injection. Without LSPosed, AutoX is blocked
cleanly (you'll see a "Requires LSPosed" message); it never silently degrades. To enable it:

1. Install AndroidAutoX as an LSPosed module (it ships the module metadata).
2. In your LSPosed manager, enable the module and set its scope to **system_server** (and any
   target apps you want to project).
3. **Reboot** for the module to take effect.

## 🚀 Usage

1. Allow root access
2. Pick the tweaks you want
3. Reboot, then forget about it :)

## 📥 Download

Grab the latest APK from the [Releases page](https://github.com/Xiddoc/AndroidAutoX/releases).

## 🔧 How it works

On a rooted device, the app runs `sqlite3` against Google Play Services' Phenotype database to toggle Android Auto flags. Many Android Auto features — including some not yet officially released — are controlled by these flags, and overriding them changes the app's behaviour.

## 🧪 Tests & coverage

Off-device unit tests (Robolectric + JUnit) run on every push and pull request. The
build enforces a **100% line and branch coverage gate** (JaCoCo
`jacocoTestCoverageVerification`) over all testable classes — irreducible Android
framework shells (Activities, Services, the `BroadcastReceiver`, generated/AIDL code)
are the only exclusions, and their logic is extracted into covered helpers. CI fails
if coverage drops below 100%, so the badge above stays honest. See the *Code coverage*
section in [`AGENTS.md`](AGENTS.md) and the per-class map in
[`docs/coverage-gap.md`](docs/coverage-gap.md).

```bash
./gradlew testDebugUnitTest jacocoTestReport jacocoTestCoverageVerification
```

## 🌍 Translations

AndroidAutoX is open to translations. See [`TRANSLATIONS.md`](TRANSLATIONS.md) for how to add or fix a language.

## 🙏 Credits

Credits and the full list of translators live in [`THANKS.md`](THANKS.md).
