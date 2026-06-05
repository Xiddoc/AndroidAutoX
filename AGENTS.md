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
- minSdk 31 (Android 12), targetSdk 34, compileSdk 34 (Java 17 source/target)
- Root via **libsu** (`com.github.topjohnwu.libsu`): one persistent root shell for shell
  commands (`MainActivity.runSuWithCmd`), plus a **`RootService`** (`PhixitRootService`) that
  runs the platform `android.database.sqlite` API inside a root process to edit GMS's
  databases directly. No bundled `sqlite3` binary, and DB SQL is parameterized rather than
  built into shell strings.
- Release builds are minified/shrunk with R8 (`minifyEnabled` + `shrinkResources`); keep rules
  for libsu, the AIDL bridge, and reflection-based libs are in `app/proguard-rules.pro`.

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
| `app/src/main/java/com/xiddoc/androidautox/AaxApp.java` | Application: configures the libsu root shell, inits `RootDb` |
| `app/src/main/java/com/xiddoc/androidautox/PhixitRootService.java` | libsu `RootService`: opens GMS DBs as root via the platform SQLite API |
| `app/src/main/java/com/xiddoc/androidautox/RootDb.java` | App-side handle that binds `PhixitRootService` and exposes `IPhixitRoot` |
| `app/src/main/aidl/com/xiddoc/androidautox/IPhixitRoot.aidl` | Root SQLite bridge interface (read/write partitions, query, exec) |
| `app/src/main/aidl/com/xiddoc/androidautox/Partition.aidl` + `Partition.java` | Parcelable {id, blob} crossing the binder |
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

### Running tests

```bash
./gradlew testDebugUnitTest   # off-device unit tests (Robolectric + JUnit)
```

This runs the JVM unit tests under `app/src/test/` with no device/emulator. The
suite mixes plain JUnit (pure logic, e.g. `RootGateTest`) with Robolectric tests
that use a simulated Android runtime and the app's Android resources. Robolectric
is pinned to SDK 34 via `app/src/test/resources/robolectric.properties`. CI runs
this on every push/PR (`.github/workflows/build.yml`).

### Code coverage (JaCoCo — 100% gate)

Coverage is enforced with JaCoCo (pinned `toolVersion = '0.8.12'`, JDK 21 / AGP
8.5.2 compatible). The gate requires **100% LINE and BRANCH** coverage on all
*included* classes.

```bash
./gradlew jacocoTestReport                  # report only (xml + html)
./gradlew jacocoTestCoverageVerification     # fails the build if < 100%
./gradlew check                              # runs the verification (and so the report + tests)
```

- HTML report: `app/build/reports/jacoco/jacocoTestReport/html/index.html`
- XML report:  `app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml`
- CI runs `jacocoTestReport jacocoTestCoverageVerification` after the unit tests
  and uploads the HTML report as the `coverage-report` artifact, so a drop below
  100% fails the build.
- A live gap analysis (every class below 100%, with missed lines/branches and
  uncovered methods, bucketed by test strategy) lives in `docs/coverage-gap.md`.

**Exclusion policy.** We exclude **only** irreducible Android framework glue and
generated code; everything else must reach 100%. The authoritative list is the
`jacocoExclusions` Groovy variable in `app/build.gradle` (commented inline). Two
groups are excluded:

1. **Generated code** — `R`/`R$*`, `BuildConfig`, `Manifest*`, `*_*Factory`, and
   the AIDL-generated bridge stubs (`IPhixitRoot*`, `Partition$*`). The
   hand-written `Partition` POJO itself is *not* excluded.
2. **Framework-entry classes that need instrumentation** — the Activities
   (`MainActivity`, `SplashActivity`, `AccountsChooser`, `CarRemover`,
   `StreamLogs`), Services (`PhixitRootService`, `ReapplyJobService`),
   `BootReceiver`, the `AaxApp` Application, and the view-only dialog wrappers
   (`AboutDialog`, `NoRootDialog`, `NotSuccessfulDialog`, `RebootDialog`).

Logic / POJO / helper classes (e.g. `RootGate`, `TweakRegistry`, `PhixitTweaks`,
`PhixitEngine`, `PhixitSnapshot`, `FlagSpec`, `RootDb`, `Version`, `UtilsLibrary`,
the adapters, `RebootFabController`, `ReapplyScheduler`, the data POJOs) are **in
scope** and must hit 100%. When you extract logic out of an excluded framework
class into a new helper, that helper is automatically in scope (it won't match the
exclusion globs) — add tests for it.

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
2. It requests root **asynchronously** off the main thread (`requestRootAsync()`), since acquisition blocks on Magisk's grant prompt and would otherwise ANR. Root is judged by `MainActivity.hasRootAccess()` → `Shell.getShell().isRoot()` (the shell's *real* root status, because a non-root fallback shell still echoes output, so command output can't be trusted). The result is stored as a tri-state (`pending` / `granted` / `denied`).
   - **Event-driven wait, no polling.** The pure `RootGate` decision class maps that tri-state to `PROCEED` / `WAIT` / `SHOW_RETRY`. Tapping "Proceed" while the request is still pending shows a brief "requesting root" toast (`WAIT`) instead of falsely dead-ending; the worker thread posts its result back via `runOnUiThread`, which re-runs the gate and advances automatically. A single-flight guard prevents duplicate request threads, and an `onDestroy` flag drops any result that arrives after the activity is gone (no work posted to a destroyed activity).
   - **"Request again" retry.** If the request finished without root, `NoRootDialog` is shown with a "Request again" action (`retryRootRequest()`). Because libsu caches a single shell singleton (so a warm/denied shell would never re-run `su`), retry first calls `MainActivity.resetRootShell()` — which closes the cached shell via `Shell.getCachedShell().close()` so the next `getShell()` builds a fresh one and genuinely re-issues `su`, re-prompting Magisk. (Caveat: if the user told Magisk to *remember* a deny, Magisk may auto-deny a fresh `su` until the rule is changed.)
3. Once root is confirmed it pre-binds `PhixitRootService` off the main thread (`RootDb.get()`) so later DB work is ready immediately.
4. **`MainActivity`** hosts the tweak buttons. Shell commands (`am`, `pm`, `rm`, `chown`, `dumpsys`, `setenforce`, …) run via `MainActivity.runSuWithCmd(String cmd)` on libsu's persistent root shell.
5. DB edits go through `RootDb`/`PhixitRootService`, which opens Android Auto's databases with the platform SQLite API **inside a root process**:
   - Phenotype flags: `/data/data/com.google.android.gms/databases/phenotype.db` (edits the compressed `param_partitions.flags_content` blobs via `PhixitEngine`)
   - Gearhead car service settings: the gearhead `carservicedata.db`
   - The engine drops SELinux to permissive (only if enforcing), force-stops GMS, edits in place, restores ownership/context, clears the phenotype cache, and restores SELinux.
6. Failures surface via `NotSuccessfulDialog`; some tweaks prompt a reboot via `RebootDialog`.

> Why the reboot? See `docs/reboot-to-apply-analysis.md` for an independent peer review of whether a
> full reboot is actually required (consensus: it's a conservative "always-works" fallback, not a
> platform mandate — the real requirement is committing the override and restarting the consuming
> processes / projection session).

> Why uninstall/reinstall apps to patch them? See `docs/patch-apps-installer-analysis.md` for the
> rationale behind the destructive reinstall (re-stamping the Play Store as installer + initiator),
> the experimental non-destructive `pm set-installer` alternative, and the installing-vs-initiating
> package caveat that makes set-installer a strictly weaker spoof.

## Adding a new tweak button

1. In `app/src/main/res/layout/scrollview.xml`, add a `Button` (and its companion status `ImageView`) inside the appropriate section: `GENERAL`, `SCREEN SETUP`, `APPEARANCE`, `VIDEO QUALITY`, or `PRE-ACTIVATE`.
2. In `MainActivity.java`, wire a click handler for the new button. Prefer the `PhixitTweaks`/`TweakRegistry` + `PhixitEngine` path (declare the flags, apply via `applyPhixitTweak…`); use `runSuWithCmd(...)` for non-DB shell actions and `RootDb` for any direct SQL. Update the status `ImageView` to reflect applied state.
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
