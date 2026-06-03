[![Crowdin](https://badges.crowdin.net/aa-aio-tweaker/localized.svg)](https://crowdin.com/project/aa-aio-tweaker)

# AA-AIO-TWEAKER 🚗⚙️

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

## 🚀 Usage

1. Allow root access
2. Pick the tweaks you want
3. Reboot, then forget about it :)

## 📥 Download

Grab the latest APK from the [Releases page](https://github.com/Xiddoc/AA-Tweaker/releases).

## 🔧 How it works

On a rooted device, the app runs `sqlite3` against Google Play Services' Phenotype database to toggle Android Auto flags. Many Android Auto features — including some not yet officially released — are controlled by these flags, and overriding them changes the app's behaviour.

## 🌍 Translations

AA AIO TWEAKER is open to translations — [join us on Crowdin](https://crwd.in/aa-aio-tweaker). Please don't reupload the APK elsewhere; just link people to the download page.

## 🙏 Credits

- Originally created by [Shmykelsa](https://github.com/shmykelsa)
- [Jen94](https://github.com/jen94) for the original app whitelist hack
- [SAAX by agentdr8](https://gitlab.com/agentdr8/saax), which inspired several features
- [AA Phenotype Patcher by Eselter](https://github.com/Eselter/AA-Phenotype-Patcher)
- Icon by [FreePik](http://www.freepik.com/) / [FlatIcon](https://www.flaticon.com/)
