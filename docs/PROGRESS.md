# Progress log

The agent updates this at the end of every session. Newest entries at the top.

## Current step
Step 01 (not started). Test phone: OnePlus 11R, Android 16.

## Completed steps

### 2026-11-01: Step 00, project scaffold (done)
All "Done when" items passed: CI green with the `app-debug` artifact, `:detection` placeholder
test passing, and the APK installed and opened on the user's phone.
- Gradle project: `:app` (Compose; `UnrotApp`, empty `AppContainer`, `MainActivity`, placeholder
  "Today" screen, theme stub) and `:detection` (pure Kotlin/JVM, one placeholder JUnit 5 test).
- Version catalog `gradle/libs.versions.toml`, Gradle 9.8.2 wrapper (distribution checksum pinned).
- `rules/detection-rules.json` (structural default surface for all five apps, no view ids) and
  `rules/schema.md`; `copyRules` copies it into `app/src/main/assets/` before `preBuild`.
- CI `.github/workflows/ci.yml`: `spotlessCheck lint :detection:test testDebugUnitTest
  assembleDebug`, uploads `app-debug` (14-day retention). First run green (run #1).
- `LICENSE` (GPL-3.0 full text), `README.md` skeleton, `PRIVACY.md` placeholder, `.gitignore`,
  `.editorconfig`.

## Decisions made during building
- **Versions (latest stable on 2026-11-01):** AGP 9.5.0, Gradle 9.8.2, Kotlin 2.4.21,
  Compose BOM 2026.09.00, activity-compose 1.13.0, JUnit 5.14.4, Spotless 8.10.4, ktlint 1.8.0.
  `compileSdk`/`targetSdk` = 37 (Android 17; AGP 9.5 supports up to 37). `minSdk` 29.
- **AGP 9 built-in Kotlin:** `:app` does not apply `org.jetbrains.kotlin.android` (AGP 9 rejects
  it). SPEC §19.1's `kotlinOptions.allWarningsAsErrors = false` is written as
  `kotlin { compilerOptions { allWarningsAsErrors = false } }`.
- **Rules file format:** exact shape agreed with the user (apps → `surfaces`, `testedVersionCodes`
  `null` or `{min,max}`, `strategy` ∈ AUTO/INDEX/SIGNATURE/DELTA). `rules/schema.md` is the
  reference; SPEC.md had only scattered field names.
- **Formatting:** ktlint `android_studio` style, trailing commas allowed (Compose convention),
  `@Composable` functions exempt from the lowercase function-name rule (`.editorconfig`).
- **Tests:** JUnit 5 only for now; the assertion library (Truth vs AssertK, SPEC §4) is decided in
  Step 03 when real tests arrive. `:detection` logs each test result in CI.
- **Supabase BuildConfig:** `SUPABASE_URL` / `SUPABASE_ANON_KEY` come from Gradle properties of the
  same name (CI maps the optional repo secrets), default `""`. `RULES_URL` points at the `main`
  branch raw file.
- **Backups:** `data_extraction_rules.xml` excludes `root`, `device_root` and `external` for both
  cloud backup and device transfer (plus `allowBackup="false"`, `fullBackupContent="false"`).
- **Window theme:** platform `Theme.Material(.Light).NoActionBar` (no AppCompat/MDC dependency);
  Compose `UnrotTheme` uses Material 3 defaults until Step 09.

## Known issues / follow-ups
- **No app icon yet:** the launcher shows Android's default icon (Step 09 / Step 13).
- **Agent environment can't build locally:** `dl.google.com` (Android SDK and Google Maven, incl.
  AGP/AndroidX) and `downloads.gradle.org` (Gradle distributions) are blocked; Maven Central
  answers HTTP 429. CI is the only build. The wrapper jar was generated with Gradle 8.14.3 (an
  official jar; CI's wrapper validation passes); refresh it with `./gradlew wrapper` when a
  machine can.
- **CI caching provider:** `gradle/actions/setup-gradle@v6` uses its proprietary "Enhanced
  Caching" provider by default (CI only; nothing ships in the APK). Decide whether to opt out per
  its DISTRIBUTION.md before going public (ADR-015 spirit).
- **Before the repo goes public (SPEC §19.6):** add `THIRD_PARTY_NOTICES.md` (today `SOURCES.md`
  covers the motion-design skill, MIT, LICENSE present).

## Device test results
(date, step, phone model, Android version, what passed, what failed)

- **2026-11-01, Step 00.** OnePlus 11R, Android 16. APK: `app-debug` from
  CI run #2. Passed: installed, opened, showed the "Today" placeholder (dark theme, edge-to-edge,
  status bar readable), no crash. Failed: nothing.
