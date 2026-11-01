# Progress log

The agent updates this at the end of every session. Newest entries at the top.

## Current step
Step 01: built, CI green; **waiting for the device gate** (phone test script in the Step 01
hand-off). Test phone: OnePlus 11R, Android 16.

## Completed steps

### 2026-11-01: Step 01, accessibility service skeleton (built, awaiting device test)
- Manifest: SPEC §6.1 permissions, `<queries>` for the six tracked packages, service declaration
  (§6.3) and `accessibility_service_config.xml` exactly as §6.4. Placeholder disclosure strings.
- `UnrotAccessibilityService`: §7.1 lifecycle; every entry point in try/catch; one detector thread
  (ADR-021); heartbeat writes `lastAliveAt` every 60 s; `connectedAt`/`disconnectedAt` recorded;
  `ScreenStateReceiver` (logs screen off/on/unlock); clean shutdown on unbind/destroy.
- `ForegroundTracker` + pure `ForegroundResolver` (§7.3); content-changed events subscribed only
  while a tracked app is in front. `DiagnosticLog` (500-entry ring buffer, files dir, §14.4).
  `HealthStore` (Preferences DataStore). `TrackedPackages` reads package names from the bundled
  rules asset.
- Debug screen (debug builds only): service state, connected-since, last heartbeat, foreground
  app, visible apps, window bounds, content-event state, last 20 log lines.
- 19 JVM unit tests (JUnit 5) in `:app`: foreground resolution (shade, keyboard, split screen,
  PiP, overlays), event-type bits, diagnostic log, tracked packages. CI run #11 green.

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
  Imports are one ASCII-sorted block (`java.*` is not grouped at the end).
- **Tests:** JUnit 5 only for now; the assertion library (Truth vs AssertK, SPEC §4) is decided in
  Step 03 when real tests arrive. `:detection` logs each test result in CI.
- **Supabase BuildConfig:** `SUPABASE_URL` / `SUPABASE_ANON_KEY` come from Gradle properties of the
  same name (CI maps the optional repo secrets), default `""`. `RULES_URL` points at the `main`
  branch raw file.
- **Backups:** `data_extraction_rules.xml` excludes `root`, `device_root` and `external` for both
  cloud backup and device transfer (plus `allowBackup="false"`, `fullBackupContent="false"`).
- **Window theme:** platform `Theme.Material(.Light).NoActionBar` (no AppCompat/MDC dependency);
  Compose `UnrotTheme` uses Material 3 defaults until Step 09.
- **Step 01, window events without a package name (refines SPEC §7.2):** the §7.2 pseudocode drops
  every event whose `packageName` is null. Window events (`TYPE_WINDOW_STATE_CHANGED`,
  `TYPE_WINDOWS_CHANGED`) are routed to `ForegroundTracker` even when the package is null,
  because Android can report focus changes (e.g. between split-screen halves) that way. The
  tracker only re-reads the window list; no screen content is read. Events from our own package
  are still dropped. Approved by the user on 2026-11-01.
- **Step 01, debug screen is debug-build only:** `MainActivity` shows it only when
  `BuildConfig.DEBUG`; release builds show the Today placeholder. It moves to Settings > Advanced
  in Step 11.
- **Step 01, no "open accessibility settings" button yet:** ADR-019 requires the full disclosure
  screen before any redirect to accessibility settings; that screen is Step 11. Until then the
  service is enabled manually in Settings.
- **Step 01, tracked packages:** read from the bundled rules asset (package names only, via
  `kotlinx-serialization-json` `JsonElement`); the full `RulesRepository` replaces this in Step 05.
  Loading rules into the detector and `DetectorState.initial()` (§7.1 steps 1, 4) wait for Step 03.
- **Step 01, error logging:** caught exceptions log the class name and top stack frame; the
  message only in debug builds (ADR-019, release logs stay structural).
- **Step 01, new dependency versions:** core-ktx 1.19.1, lifecycle-runtime-compose 2.11.0,
  DataStore 1.3.0, kotlinx-coroutines 1.11.0, kotlinx-serialization-json 1.12.0. `:app` unit tests
  use JUnit 5 like `:detection`.

## Known issues / follow-ups
- **No app icon yet:** the launcher shows Android's default icon (Step 09 / Step 13).
- **Updating a CI debug build needs an uninstall first:** each CI run signs the APK with a freshly
  generated debug key, so Android refuses to install it over the previous build. Uninstalling
  also clears the app's data. Consider a stable debug signing key before Step 04's repeated
  device tests (decide with the user; signing keys must not be committed, SPEC §19.6).
- **Heartbeat during deep sleep:** the 60 s heartbeat pauses while the CPU sleeps with the screen
  off; a gap in `lastAliveAt` alone does not mean the service died. Health checks (§14.1) must
  account for this.
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
