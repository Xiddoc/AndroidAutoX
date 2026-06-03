# AGENTS.md

Canonical project documentation for **AA AIO TWEAKER**. This is the source of truth for both human contributors and AI coding agents working in this repository.

## Project overview

AA AIO TWEAKER is a native Android app that applies "tweaks" to Google's Android Auto. It works by toggling Phenotype feature flags and Gearhead settings on a rooted device, exposing them through a simple button-per-tweak UI. Tweaks cover things like screen layout, appearance, video quality, and pre-activation of experimental Android Auto features.

- Application ID / package: `sksa.aa.tweaker`
- App name: "AA AIO TWEAKER"
- Repository: https://github.com/Xiddoc/AA-Tweaker
- Requires a **rooted** device.

## Tech stack

- Language: Java
- Build system: Gradle with Android Gradle Plugin 4.0.1 (Gradle wrapper included)
- minSdk 23, targetSdk 29, compileSdk 29
- No `libsu` — root commands are executed directly via `Runtime.getRuntime().exec("su")`
- Ships a bundled `sqlite3` binary (in `res/raw`) that is copied to app data and executed at runtime

## Repository layout

| Path | Description |
| --- | --- |
| `app/build.gradle` | Module build config: build types, signing, versioning, archive naming |
| `build.gradle`, `settings.gradle`, `gradle.properties` | Top-level Gradle config |
| `gradlew`, `gradlew.bat`, `gradle/` | Gradle wrapper |
| `app/src/main/AndroidManifest.xml` | App manifest / declared activities |
| `app/src/main/java/sksa/aa/tweaker/` | All Java source |
| `app/src/main/java/sksa/aa/tweaker/SplashActivity.java` | Entry point: disclaimer, root check, asset copy |
| `app/src/main/java/sksa/aa/tweaker/MainActivity.java` | Main UI + all tweak handlers (~4300 lines) |
| `app/src/main/java/sksa/aa/tweaker/AppsList.java` | App list helper |
| `app/src/main/java/sksa/aa/tweaker/StreamLogs.java` | Log streaming view |
| `app/src/main/java/sksa/aa/tweaker/AboutDialog.java` | About dialog |
| `app/src/main/java/sksa/aa/tweaker/NoRootDialog.java` | Shown when root is unavailable |
| `app/src/main/java/sksa/aa/tweaker/NotSuccessfulDialog.java` | Shown when a tweak fails |
| `app/src/main/java/sksa/aa/tweaker/RebootDialog.java` | Prompts a reboot after a tweak |
| `app/src/main/java/sksa/aa/tweaker/Utils/` | `UtilsLibrary`, `Version`, `BottomDialog` helpers |
| `app/src/main/res/layout/scrollview.xml` | Tweak buttons UI (the main tweak list) |
| `app/src/main/res/layout/activity_splash.xml` | Splash / disclaimer layout |
| `app/src/main/res/raw/sqlite3` | Bundled sqlite3 binary used at runtime |
| `app/src/main/res/values/strings.xml` | English source strings (localization source of truth) |
| `app/src/main/res/values-*/strings.xml` | ~33 translated locales (Crowdin-managed) |

## Building

The Gradle wrapper drives all builds.

```bash
./gradlew assembleDebug      # quick sanity-check build
./gradlew assembleRelease    # release build
```

Build types include `debug`, `release`, plus `RC` and `daily` variants. The release APK is archived as:

```
AA-AIO-TWEAKER-<versionName>.apk
```

### Signing & local configuration

Signing reads keystore details from `local.properties` (not committed):

- `STORELOCATION` — path to the keystore
- `STOREPASSWORD` — keystore password
- `KEYPASSWORD` — key password

The build also injects `PASTEBIN_API_KEY` (used at runtime for sharing logs/pastes).

### CI

A CI pipeline using GitHub Actions is being added. It builds and signs the app using repository **secrets** rather than `local.properties`. Required secrets (placeholders):

- `SIGNING_KEYSTORE_BASE64` — base64-encoded keystore file
- `SIGNING_STORE_PASSWORD` — keystore password
- `SIGNING_KEY_PASSWORD` — key password
- `SIGNING_KEY_ALIAS` — key alias
- `PASTEBIN_API_KEY` — Pastebin API key injected at build time

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

## Localization / Crowdin workflow

- English is the source of truth: `app/src/main/res/values/strings.xml`.
- Translations live under `app/src/main/res/values-<locale>/strings.xml` (~33 locales).
- Translations are managed through Crowdin (project `aa-aio-tweaker`). Add new strings to the English source only; translated locales are populated/synced via Crowdin rather than edited by hand.

## Versioning

Version is defined in `app/build.gradle` (`versionCode` and `versionName`, around lines 39-40). Bump it there; the `versionName` flows into the release APK archive name.

## Conventions for contributors and agents

- Work on **feature branches** (agents use `claude/...` branch names); do not commit directly to the default branch.
- Keep commits **small and focused**, with clear messages.
- Do not commit secrets or `local.properties`.
- Touch only what the task requires; avoid unrelated refactors.
