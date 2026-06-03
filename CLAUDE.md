# CLAUDE.md

See **@AGENTS.md** for the canonical project documentation (overview, repo layout, build/signing, runtime behavior, how to add a tweak, localization, versioning, and conventions). Treat it as the source of truth and avoid duplicating it here.

## Claude Code quick tips

- `MainActivity.java` is huge (~4300 lines). Prefer targeted reads (Grep for a button id, handler, or `runSuWithCmd` call) over reading the whole file.
- Tweak UI lives in `app/src/main/res/layout/scrollview.xml`; handlers are in `MainActivity.java`. Changing one usually means changing the other.
- Sanity-check changes with `./gradlew assembleDebug` before considering a Java/resource change done.
- Add user-facing strings only to `app/src/main/res/values/strings.xml` (English source); leave `values-*/` locales to Crowdin.
- Do not edit `local.properties`, keystores, or other secrets. Do not bump the version unless explicitly asked.
- Work on a feature branch and keep commits small and focused.
