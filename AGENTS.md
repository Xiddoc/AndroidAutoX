# AGENTS.md

Canonical project documentation for **AndroidAutoX**. This is the source of truth for both human contributors and AI coding agents working in this repository.

## Project overview

AndroidAutoX is a native Android app that applies "tweaks" to Google's Android Auto. It works by toggling Phenotype feature flags and Gearhead settings on a rooted device, exposing them through a simple button-per-tweak UI. Tweaks cover things like screen layout, appearance, video quality, and pre-activation of experimental Android Auto features.

- Application ID / package: `com.xiddoc.androidautox`
- App name: "AndroidAutoX"
- Repository: https://github.com/Xiddoc/AndroidAutoX
- Requires a **rooted** device.

## Tech stack

- Language: Java
- Build system: Gradle 8.7 with Android Gradle Plugin 8.5.2 (Gradle wrapper included); AndroidX
- minSdk 31 (Android 12), targetSdk 34, compileSdk 34
- No `libsu` — root commands are executed directly via `Runtime.getRuntime().exec("su")`
- Ships a bundled `sqlite3` binary (in `res/raw`) that is copied to app data and executed at runtime

## Repository layout

| Path | Description |
| --- | --- |
| `app/build.gradle` | Module build config: build types, signing, versioning, archive naming |
| `build.gradle`, `settings.gradle`, `gradle.properties` | Top-level Gradle config |
| `gradlew`, `gradlew.bat`, `gradle/` | Gradle wrapper |
| `app/src/main/AndroidManifest.xml` | App manifest / declared activities |
| `app/src/main/java/com/xiddoc/androidautox/` | All Java source |
| `app/src/main/java/com/xiddoc/androidautox/SplashActivity.java` | Entry point: disclaimer, root check, asset copy |
| `app/src/main/java/com/xiddoc/androidautox/MainActivity.java` | Main UI + all tweak handlers (~4300 lines) |
| `app/src/main/java/com/xiddoc/androidautox/AppsList.java` | App list helper |
| `app/src/main/java/com/xiddoc/androidautox/StreamLogs.java` | Log streaming view |
| `app/src/main/java/com/xiddoc/androidautox/AboutDialog.java` | About dialog |
| `app/src/main/java/com/xiddoc/androidautox/NoRootDialog.java` | Shown when root is unavailable |
| `app/src/main/java/com/xiddoc/androidautox/NotSuccessfulDialog.java` | Shown when a tweak fails |
| `app/src/main/java/com/xiddoc/androidautox/RebootDialog.java` | Prompts a reboot after a tweak |
| `app/src/main/java/com/xiddoc/androidautox/Utils/` | `UtilsLibrary`, `Version`, `BottomDialog` helpers |
| `app/src/main/res/layout/scrollview.xml` | Tweak buttons UI (the main tweak list) |
| `app/src/main/res/layout/activity_splash.xml` | Splash / disclaimer layout |
| `app/src/main/res/raw/sqlite3` | Bundled sqlite3 binary used at runtime |
| `app/src/main/res/values/strings.xml` | English source strings (localization source of truth) |
| `app/src/main/res/values-*/strings.xml` | ~33 translated locales (edited via PRs) |

## Building

The Gradle wrapper drives all builds.

```bash
./gradlew assembleDebug      # quick sanity-check build
./gradlew assembleRelease    # release build
```

Build types include `debug`, `release`, plus `RC` and `daily` variants. The release APK is archived as:

```
AndroidAutoX-<versionName>.apk
```

### Toolchain in a fresh / cloud environment (READ THIS — common gotcha)

This project uses **Gradle 8.7** + **Android Gradle Plugin 8.5.2**, which need
**JDK 17+** to run (JDK 21 is fine). Fresh containers (e.g. Claude Code on the
web) usually ship a modern JDK (17/21) already but have **no Android SDK**, so a
bare `./gradlew assembleDebug` fails until you install the SDK. Provision it once
per container:

```bash
# 1. Install the Android SDK (compileSdk 34). cmdline-tools + sdkmanager run on JDK 17+.
export ANDROID_SDK_ROOT=$HOME/android-sdk
mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"
curl -s -o /tmp/cmdtools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q -o /tmp/cmdtools.zip -d "$ANDROID_SDK_ROOT/cmdline-tools"
mv "$ANDROID_SDK_ROOT/cmdline-tools/cmdline-tools" "$ANDROID_SDK_ROOT/cmdline-tools/latest"
SDKMGR="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
yes | "$SDKMGR" --sdk_root="$ANDROID_SDK_ROOT" --licenses
"$SDKMGR" --sdk_root="$ANDROID_SDK_ROOT" \
    "platform-tools" "platforms;android-34" "build-tools;34.0.0"

# 2. Build (JDK 21 + the SDK via env; no local.properties needed).
export ANDROID_HOME=$ANDROID_SDK_ROOT
./gradlew assembleDebug --no-daemon
```

The Gradle plugin reads `ANDROID_HOME`/`ANDROID_SDK_ROOT` from the environment,
so you do **not** need to create `local.properties` just to build (and per the
conventions below, don't). The first build downloads Gradle 8.7 and the AGP/
AndroidX artifacts.

### Signing & local configuration

Signing reads keystore details from `local.properties` (not committed):

- `STORELOCATION` — path to the keystore
- `STOREPASSWORD` — keystore password
- `KEYPASSWORD` — key password

### CI

A CI pipeline using GitHub Actions is being added. It builds and signs the app using repository **secrets** rather than `local.properties`. Required secrets (placeholders):

- `SIGNING_KEYSTORE_BASE64` — base64-encoded keystore file
- `SIGNING_STORE_PASSWORD` — keystore password
- `SIGNING_KEY_PASSWORD` — key password
- `SIGNING_KEY_ALIAS` — key alias

## How the app works at runtime

1. **`SplashActivity`** launches first. It shows a safety-warning disclaimer; a 5-second `CountDownTimer` gates the "Proceed" / "I understand" button (`activity_splash.xml`).
2. It checks for root with `MainActivity.runSuWithCmd("echo 1")`. If root is unavailable, `NoRootDialog` is shown.
3. `copyAssets()` copies the bundled `R.raw.sqlite3` binary into the app data directory and `chmod`s it to be executable.
4. **`MainActivity`** hosts the tweak buttons. Each tweak runs root commands via `MainActivity.runSuWithCmd(String cmd)`, which pipes commands into `Runtime.getRuntime().exec("su")`.
5. Tweaks are applied by running the copied `sqlite3` against Android Auto's databases:
   - Phenotype flags: `/data/data/com.google.android.gms/databases/phenotype.db`
   - Gearhead car service settings: the gearhead `carservicedata.db`
6. Failures surface via `NotSuccessfulDialog`; some tweaks prompt a reboot via `RebootDialog`.

## Adding a new tweak button

1. In `app/src/main/res/layout/scrollview.xml`, add a `Button` (and its companion status `ImageView`) inside the appropriate section: `GENERAL`, `SCREEN SETUP`, `APPEARANCE`, `VIDEO QUALITY`, or `PRE-ACTIVATE`.
2. In `MainActivity.java`, wire a click handler for the new button that runs the required root/`sqlite3` commands via `runSuWithCmd(...)`, and update the status `ImageView` to reflect applied state.
3. Add any user-facing text to `app/src/main/res/values/strings.xml` (English source) so it can be translated.

## Localization workflow

- English is the source of truth: `app/src/main/res/values/strings.xml`.
- Translations live under `app/src/main/res/values-<locale>/strings.xml` (~33 locales).
- Localization is PR-based: add new strings to the English source first, then contributors translate by editing (or adding) the matching `values-<locale>/strings.xml` and opening a pull request. The app name is a non-translatable proper noun (`AndroidAutoX`) and should not be localized.

## Versioning

Version is defined in `app/build.gradle` (`versionCode` and `versionName`, around lines 39-40). Bump it there; the `versionName` flows into the release APK archive name.

## Conventions for contributors and agents

- Work on **feature branches** (agents use `claude/...` branch names); do not commit directly to the default branch.
- Keep commits **small and focused**, with clear messages.
- Do not commit secrets or `local.properties`.
- Touch only what the task requires; avoid unrelated refactors.
