# Is the destructive uninstall/reinstall in "Patch apps" actually necessary? — Analysis

> **UPDATE (resolved):** The destructive uninstall/reinstall path has been **removed**.
> "Patch apps" now uses the non-destructive `pm set-installer` path **only**, and it captures
> each app's original installer first so the change can be reverted per app (restoring the
> captured installer). No app is ever uninstalled, so the data-loss foot-gun is gone. The
> historical analysis below is retained for rationale; references to `patchAppDestructive`,
> the destructive `nextAction`/rollback/temp-APK logic, the split-APK guard, and the opt-in
> "non-destructive" toggle describe code that no longer exists. The known caveat still stands:
> `set-installer` changes `getInstallingPackageName()` but **not** the immutable
> `getInitiatingPackageName()`, so it is a strictly weaker spoof and relies on the Phenotype
> validation-bypass flags (`TweakRegistry.patchedAppsSpecs()`) to cover the rest.

**Status:** research note / design rationale. Not a spec.
**Question studied:** When a user picks third-party apps to use over Android Auto, AndroidAutoX
runs a **destructive** per-app loop that *uninstalls and reinstalls* each app so it is re-stamped
as Play-Store-installed. Do the ~11 Phenotype **validation-bypass flags** the app also sets already
make that destructive re-stamp **redundant** — i.e. could a non-destructive
`pm set-installer` (or nothing at all) suffice?
**Why it matters:** the destructive loop is a data-loss foot-gun (an app is briefly uninstalled; a
mis-step can leave it gone), and the failure mode of getting it wrong is *silent in-car breakage*
that only shows up on a real head unit.

> Scope note: this document is about *what the loop and the flags do and whether they overlap*. For
> the actual code, see `MainActivity.patchforapps()` / `patchAppDestructive()` /
> `patchAppSetInstaller()` and `PatchAppsPolicy` (the pure, testable decision seam), plus
> `TweakRegistry.patchedAppsSpecs()` for the flags.

---

## TL;DR — the verdict (Option C)

**The destructive uninstall/reinstall remains the DEFAULT** and will until a real head unit confirms
otherwise. The flags *plausibly* subsume the installer re-stamp, but **confidence is LOW to
LOW-MEDIUM**: the loop and the flags were inherited together from the upstream tooling lineage and,
as far as anyone can tell, **were never tested apart**. Nobody has a clean device report showing
"flags only, no reinstall, blocked app still appears on the head unit."

To make the decisive test runnable without forcing it on every user, this change adds an **opt-in,
default-OFF** experimental "non-destructive" mode (`pm set-installer` only). A maintainer with a head
unit can flip it on and run the test matrix below.

---

## What the destructive loop does

For each selected package (now validated against `^[A-Za-z0-9._]+$` first to close the shell
injection vector):

1. Resolve the base APK path via `PackageManager` (`ApplicationInfo.sourceDir`) — not by parsing
   `pm path` output, which returns multiple lines for split APKs and previously produced a wrong
   path / left the app uninstalled.
2. **Copy** the base APK to `/data/local/tmp` (copy, not move, so the original survives until the
   copy is confirmed).
3. `pm uninstall <pkg>`.
4. `pm install -t -i "com.android.vending" -r <tmp.apk>` — this is the load-bearing step: it
   re-stamps **both** `getInstallingPackageName()` **and** the immutable
   `getInitiatingPackageName()` to the Play Store.
5. On success, delete the temp APK. On any failure, **abort + best-effort rollback** (reinstall from
   the temp APK) so the app is never left uninstalled.

Split APKs are now **detected and skipped** (`ApplicationInfo.splitSourceDirs`) rather than
corrupted, because the single-base-APK reinstall can't faithfully restore a split app.

## What the ~11 bypass flags do

These flags (set elsewhere via the phixit engine, from `TweakRegistry.patchedAppsSpecs()`) operate
on three conceptually distinct surfaces:

- **Installer-source validation surface** — flags that relax or disable Gearhead's check that a
  projected app was installed by an approved installer (the Play Store). *This* is the surface the
  destructive re-stamp targets. If these flags fully disable that check, the re-stamp is redundant.
- **Package allow-list surface** — flags / overrides that add the chosen packages to the set
  Gearhead is willing to project at all (separate from *how* they were installed).
- **Projection surface** — flags governing whether non-AA-aware apps may draw on the projected
  display.

The open question is precisely whether the *installer-source* flags subsume step 4 of the loop. The
allow-list and projection surfaces are **not** substitutes for the re-stamp; they're orthogonal
prerequisites that are needed either way.

## The `pm set-installer` caveat (installing vs. initiating)

`pm set-installer <pkg> com.android.vending` changes only **`getInstallingPackageName()`**. It does
**not** change **`getInitiatingPackageName()`**, which is immutable after install. The destructive
reinstall sets **both** (via `-i`). So `set-installer` is a **strictly weaker spoof**:

- It is **safe only if** Gearhead's validation reads the *installing* package.
- If Gearhead reads the *initiating* package, `set-installer` will **not** fool it and only the
  destructive reinstall (or the flags) will work.

This is why the non-destructive mode is **experimental** and **opt-in**: it might silently fail to
make an app appear, and that failure is only observable in-car.

## Confidence and provenance

- **Confidence: LOW / LOW-MEDIUM** that the flags alone subsume the re-stamp.
- The loop and the flags came **together** from the upstream tooling lineage; there is no record of
  them being validated **independently**.
- The failure mode is **silent**: an app simply won't show on the head unit. No phone-side signal.

Hence Option C: keep the destructive loop as default, ship the opt-in seam, and let a device test
settle it.

## On-device test matrix (the decisive experiment)

Run on a real rooted device + head unit (or DHU). Use an app known to be *blocked* by Android Auto's
installer-source check when sideloaded (so a positive result is meaningful).

- **Test A — flags only.** Ensure the bypass flags are applied. **Do not** patch the app (leave it
  sideloaded; installer/initiator are *not* the Play Store). Connect the head unit.
  *Does the app appear / launch on the projected display?*
  - **Appears →** the flags subsume the installer-source check; the destructive loop is redundant for
    this app/build. Strong evidence to make non-destructive (or no-patch) the default.
  - **Does not appear →** the flags do **not** subsume it; the re-stamp is still doing real work.

- **Test B — flags + `pm set-installer`.** Same as A, but additionally run
  `pm set-installer <pkg> com.android.vending` (the non-destructive mode). Reconnect.
  *Does the app appear now?*
  - **Appears (when A did not) →** Gearhead reads the **installing** package; the non-destructive
    mode is sufficient and the destructive reinstall is unnecessary.
  - **Still does not appear →** Gearhead reads the **initiating** package (or another signal); only
    the destructive reinstall (or stronger flags) will work. Keep the default.

Record the GMS / Gearhead versions with any result — behaviour is version-dependent.

## Bottom line

Until a head unit confirms Test A (or B) passes, **the destructive uninstall/reinstall loop is the
default.** The non-destructive mode exists solely so that test can be run; it is not represented as a
working replacement.

---

*Mirrors the structure/tone of `docs/reboot-to-apply-analysis.md`. Treat the test matrix as the way
to convert the LOW-confidence "plausibly redundant" into a device-verified answer.*
