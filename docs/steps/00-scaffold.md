# Step 00 — Project scaffold

## Prompt to paste

> Read docs/PROGRESS.md and docs/steps/00-scaffold.md. Then read only the SPEC.md and ADR.md sections that step lists.
> Show me a short plan (files you'll create or change, tests you'll write) and wait for my OK.
> After I approve: build it, run the tests, commit, push, and make sure CI is green.
> Then check every item in "Done when", update docs/PROGRESS.md, and tell me what to test on my phone (if this step has a device gate).
> Do not start any other step.

## Goal
An empty but correctly structured Android project that builds in GitHub Actions and produces an APK you can install.

## Read
- ADR: 001, 002, 011, 015, 016, 018
- SPEC: §0, §1.3, §3, §4, §5, §6.2, §7.6 (only the default rule numbers), §19.1, §19.2, §19.3

## Build
- [ ] Gradle project with modules `:app` and `:detection` (pure Kotlin/JVM), version catalog, wrapper.
- [ ] Look up current stable versions; no alphas.
- [ ] `UnrotApp`, empty `AppContainer`, `MainActivity` with a placeholder "Today" screen.
- [ ] Theme stub (colors/fonts come in Step 09).
- [ ] `LICENSE` (GPL-3.0 full text), README skeleton, PRIVACY.md placeholder, `.gitignore` per §19.6.
- [ ] `rules/detection-rules.json` with structural default rules for all five apps (§1.2, §7.6). No guessed view IDs.
- [ ] `copyRules` Gradle task into assets.
- [ ] CI workflow (§19.2) uploading `app-debug.apk`.
- [ ] Try a local build; if the Android SDK can't be downloaded, report the blocked domains and rely on CI.

## Don't
No accessibility service, no database, no screens beyond the placeholder.

## Done when
- [ ] CI is green and the APK artifact exists.
- [ ] `:detection` has one passing placeholder test.
- [ ] 🔶 You installed the APK on your phone and it opens.
