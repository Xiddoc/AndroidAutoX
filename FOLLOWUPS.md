# FOLLOWUPS

Open items and known risks for the Phenotype "phixit" migration + background re-apply
work. Ordered roughly by priority. Keep this updated as items are resolved.

## 0. Bundled `sqlite3` binary — REMOVED (migrated to libsu RootService + platform SQLite)

The original "malformed database schema ... near WITHOUT" failure was a stale 2011 SQLite
binary (3.7.6.3) that predates `WITHOUT ROWID`. Rather than keep shipping a binary, the whole
approach was replaced: the app now uses **libsu** (`com.github.topjohnwu.libsu`) — a persistent
root shell for shell commands, and a **`RootService`** (`PhixitRootService`) that opens GMS's
databases with the **platform `android.database.sqlite` API inside a root process**. The
platform SQLite is modern (handles `WITHOUT ROWID`), blobs cross the binder as real `byte[]`
(no hex), and DB SQL is parameterized (no SQL built into shell strings). The `res/raw/sqlite3`
binary, `scripts/update-sqlite3.sh`, and the CI "Refresh sqlite3" steps are gone. This closes
the ABI / supply-chain / local-build-freshness concerns that the binary carried, and also
resolves the SQL-injection (#2, DB path) and `runSuWithCmd` timeout (#3) items.

**Needs on-device validation (not exercisable in CI):**
- Apply + revert + background re-apply of a tweak of each value type (bool/long/double/string)
  against a live `phenotype.db`, confirming GMS picks up the edit after restart.
- That libsu can bind the `RootService` and open GMS's private DB under the device's root
  solution (Magisk/KernelSU) — including whether the `setenforce 0` window is still needed.
- `carservicedata.db` paths (`CarRemover`): the original ran `sqlite3` without toggling
  SELinux; the RootService now opens it in a root process. Verify it can read/write under
  enforcing, and add a SELinux toggle there if not.
- First-run Magisk grant prompt still appears and `NoRootDialog` shows on denial.
- The conservative R8 release build runs correctly (reflection-based libs, custom views).


## 1. patchforapps — reinstall loop hardened — RESOLVED

The destructive loop in `MainActivity.patchforapps()` is now guarded:
- APK path comes from `PackageManager.getApplicationInfo().sourceDir` (no `pm path` parse);
  split-APK apps are detected (`PatchAppsPolicy.isSplitApk`) and **skipped** before any
  destructive step instead of being corrupted.
- The flow copies (not moves) the APK aside, only uninstalls after the copy succeeds, judges
  each `pm` step by parsing its output for `Success` (NOT the unreliable exit code —
  `PatchAppsPolicy.pmSucceeded`), confirms the package is actually installed via
  `PackageManager` before deleting the only APK copy, and attempts best-effort rollback on
  failure. The rollback decision tree is a pure, unit-tested seam (`PatchAppsPolicy.nextAction`).
- Any app that still ends up lost is surfaced to the user via `NotSuccessfulDialog` naming the
  package and its `/data/local/tmp/tmpapk<pkg>.apk` recovery path (no longer buried in a log).
- Additionally, every GMS DB edit is now auto-backed-up first (see "DB backup safety net"),
  so even a worst-case DB corruption is recoverable.
Still needs on-device testing incl. a split-APK app and a system-updated app.

## 2. patchforapps — shell injection via package names — RESOLVED

Package names are now validated against `^[A-Za-z0-9._]+$` (`PatchAppsPolicy.isValidPackageName`)
and skipped if invalid before any interpolation, and the OS-derived APK paths are
single-quoted (`PatchAppsPolicy.quoteShellArg`) in every `su` command. The whitelist *value*
path and all DB SQL were already safe (engine / parameterized queries).

## DB backup safety net (default-on) — ADDED

Every mutating call through `RootDb` (`writePartitions`/`exec`/`execStatements`) now takes a
backup of the target GMS database BEFORE editing, gated by the default-on `auto_backup_dbs`
pref (toggle in the menu; `DbBackup`). This covers `phenotype.db` (engine) AND
`carservicedata.db` (`CarRemover`) and the raw phenotype edits — "the DBs we mess with",
plural. Backups are WAL-consistent (`wal_checkpoint(TRUNCATE)` + sidecars), written atomically
(temp -> fsync -> rename), chowned to the app uid, kept newest-N per DB, and non-blocking on
failure (logged). Root FS primitives (`statOwner`/`chownPath`/`deleteRecursive`/`backupFile`)
replaced the `stat`/`chown`/`rm -rf` shell-outs; `deleteRecursive` is symlink-safe and
allowlist-guarded.

**Remaining cleanup:** `MainActivity` still holds its own duplicate `PHENO_DB` literal — migrate
it to `GmsPaths.PHENO_DB` (the new single source of truth used by `PhixitEngine`/`DbBackup`/
`PhixitRootService`/`CarRemover`).

## 3. `runSuWithCmd` timeout — RESOLVED (libsu)

`runSuWithCmd` now runs on libsu's shell, which is configured with a timeout
(`Shell.Builder.setTimeout(30)` in `AaxApp`) instead of the old unbounded
`Runtime.exec("su").waitFor()`. A prompting/denied superuser policy no longer wedges the
background worker forever.

## 4. patchforapps — `temp` not reset on the warning dialog's "No"/cancel (pre-existing)

After tapping "No" on the patch warning, `temp` stays `true`, so the next
`patchforapps()` invocation hits the top guard and silently returns. Reset `temp = false` in
the negative-button and cancel handlers.

## 5. `isApplied()` conflates real drift with transient read failure

`PhixitEngine.isApplied()` returns `false` on empty/unreadable output, which the re-apply job
treats as "drift." Under flaky root this causes unnecessary re-apply/defer cycles. Consider a
tri-state (applied / drifted / unknown) so "couldn't read" doesn't force action.

## 6. Projection detector — version fragility / on-device validation needed

`PhixitEngine.isAndroidAutoProjecting()` parses `dumpsys activity services gearhead`. The
format is not a stable API (`foregroundServiceType` is API 29+; `isForeground=` formatting
varies). It is deliberately conservative (only "no services" -> not projecting; everything
ambiguous -> defer), so it can **over-defer** on devices where gearhead keeps idle services
alive. Validate on-device across: idle/disconnected, projecting screen-on, projecting
screen-off/locked — on at least one old (<=8) and one new (>=12) Android build; capture real
dumpsys output and adjust markers. Also confirm a deferred re-apply actually fires after the
drive ends (Doze/standby).

## 7. Persistence assumption is still unverified

The entire background re-apply layer assumes GMS re-syncs and overwrites the new-schema blob.
That has **not** been observed on-device yet. Confirm whether/how often drift actually happens
before relying on the job (and before worrying about its cadence/battery cost).

## 8. Duplicated flag-name lists (maintenance hazard)

5 tweaks (HUN, media-HUN, USB/wifi bitrate, patched-apps) list their flag (pkg, name) pairs
twice: real values in `TweakRegistry`, placeholder values in `PhixitTweaks.specs()` for
revert. They must stay in lockstep or revert silently misses a baseline. Consider a single
shared name list both derive from.

## 10. `MyAdapter.onBindViewHolder` binds from `getAdapterPosition()` not its `position` arg

`onBindViewHolder(holder, i)` ignores `i` and does `mAppInfo.get(holder.getAdapterPosition())`.
`getAdapterPosition()` returns `NO_POSITION` (-1) for a holder the RecyclerView hasn't attached
(e.g. mid-recompute, or if `bindViewHolder` is ever called on a detached holder) -> latent
`IndexOutOfBoundsException`. It also forces tests to drive a full measure/layout pass instead
of binding a holder directly. Bind from the `position` parameter instead.

## 11. Two different position sources for the same row (row-click vs checkbox)

The row `OnClickListener` (set in `onCreateViewHolder`) captures the creation-time `i`, while
the checkbox (`R.id.checkbox_app`) listener uses `getAdapterPosition()`. Two listeners on one
row resolve their target index two different ways; after any insert/remove/move they can
disagree. Unify on a single source (prefer `getBindingAdapterPosition()` resolved at click
time).

## 12. `onClickSaveAppsWhiteList` mixes `apply()` (remove) and `commit()` (add)

The remove branch calls `editor.apply()` (async) and the add branch calls `editor.commit()`
(sync). Inconsistent durability/threading for the same pref edit; pick one (prefer `apply()`)
so behavior is uniform.

## 13. Car filter uses a caught `NullPointerException` as control flow

`AppsList.onCreate` reads `packageInfo.metaData.getInt(...)` inside a `try`/`catch
(NullPointerException)` to skip apps with no metadata, and the catch does `printStackTrace()`
(log spam for every metadata-less app, which is the common case). Replace with an explicit
`if (bundle != null)` guard and drop the stack-trace print.

## 14. Per-comparison allocations + duplicated string constants

- `AppInfo.compareTo` rebuilds a ~9-element "known Android-Auto apps" `ArrayList` on every
  single comparison (O(n log n) allocations during the sort). Hoist it to a
  `static final Set<String>`.
- The `"appsListPref"` pref name and the `"com.google.android.gms.car.application"` car marker
  are string literals duplicated across `AppsList`, `MyAdapter`, and `TweakRegistry` with no
  shared constant. (Distinct from #8, which is about the flag-name *lists*.) Extract shared
  constants.

## 9. Minor

- patchforapps shows two sequential `ProgressDialog`s (reinstall phase, then engine phase) ->
  brief flicker. Optional: pass the existing dialog into the helper.
- Empty `appsListPref` still applies empty-whitelist flags and restarts GMS. Optional guard.
- Once #0 is fixed: run a full on-device smoke test of apply + revert + background re-apply
  for a representative tweak of each value type (bool/long/double/string).
