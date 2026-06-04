# FOLLOWUPS

Open items and known risks for the Phenotype "phixit" migration + background re-apply
work. Ordered roughly by priority. Keep this updated as items are resolved.

## 0. BLOCKER — bundled `sqlite3` is too old for the new schema (found on-device)

`app/src/main/res/raw/sqlite3` is **SQLite 3.7.6.3 (2011-05-19)**, 32-bit ARM. The new
`phenotype.db` uses `WITHOUT ROWID` tables (e.g. `flag_overrides_to_commit`), a feature
added in SQLite **3.8.2 (2013)**. SQLite parses the whole schema before any query, so on
this device **every** engine read/write fails with:

```
Error: malformed database schema (flag_overrides_to_commit) - near "WITHOUT": syntax error
```

=> all tweaks report "no partitions matched"; nothing is actually applied. This blocks the
entire engine on-device regardless of the (correct) flag logic.

Fix options:
- **(A) Platform SQLite, no binary (recommended):** rewrite `PhixitEngine` DB I/O to copy
  `phenotype.db` (+ `-wal`/`-shm`) out via root into app storage, edit it with Android's
  built-in `android.database.sqlite.SQLiteDatabase` (modern, supports `WITHOUT ROWID`,
  reads/writes blobs directly — no hex round-trip, no shell-escaping), then copy back via
  root. Must capture & restore the original **owner/group and SELinux context** on copy-back
  (`stat`/`ls -Z` before; `chown` + `restorecon`/`chcon` after), and fold WAL before copy.
- **(B) Bundle a modern static `sqlite3`:** replace the binary with a current statically
  linked build for `arm64-v8a` (+ `armeabi-v7a`), select by ABI at runtime. Smallest code
  change, keeps the current engine — but requires sourcing/vetting a trusted bionic static
  binary.

## 1. patchforapps — reinstall loop is destructive and unguarded (pre-existing)

In `MainActivity.patchforapps()` the per-app `pm path -> mv -> pm uninstall -> pm install`
loop has no failure checks between steps:
- `pm path` returning empty or **multiple lines (split APKs)** -> wrong/garbage path -> app
  uninstalled but reinstall reads a bad file -> **app left uninstalled**.
- `mv` or `pm install` failure after uninstall -> **app gone**, temp APK stranded in
  `/data/local/tmp`.
Harden: validate `pm path` yields exactly one `package:` line, abort uninstall if `mv`
failed, verify install succeeded before deleting the temp APK, attempt rollback on failure.
Pre-existing; blast radius widened now that the tweak is background-eligible. Needs on-device
testing incl. a split-APK app and a system-updated app.

## 2. patchforapps — shell injection via package names (pre-existing)

Package names from `appsListPref` are interpolated unquoted into `su` command strings
(`mv`/`pm uninstall`/`pm install`). Normally `[A-Za-z0-9._]`, but unsanitized. Validate
against `^[A-Za-z0-9._]+$` (or quote/escape) before use. The whitelist *value* path is safe
(it goes through the engine, not the shell).

## 3. `runSuWithCmd` has no timeout

`MainActivity.runSuWithCmd` does an unbounded `su.waitFor()`. In the background JobService a
superuser policy that *prompts* (no UI present) can wedge the worker thread forever, so
`jobFinished` is never called. Add a bounded wait (`waitFor(timeout, unit)` / watchdog
`destroy()`), treat timeout as inconclusive.

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

## 9. Minor

- patchforapps shows two sequential `ProgressDialog`s (reinstall phase, then engine phase) ->
  brief flicker. Optional: pass the existing dialog into the helper.
- Empty `appsListPref` still applies empty-whitelist flags and restarts GMS. Optional guard.
- Once #0 is fixed: run a full on-device smoke test of apply + revert + background re-apply
  for a representative tweak of each value type (bool/long/double/string).
