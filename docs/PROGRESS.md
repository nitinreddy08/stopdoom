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
- **Step 01, panels that are never the foreground app (refines SPEC §7.3 step 3):**
  `ForegroundResolver.NEVER_FOREGROUND` = `com.android.systemui`, `com.coloros.assistantscreen`
  (OnePlus/OPPO/realme Shelf) and `com.oplus.pscanvas` (OxygenOS split-screen container). Their windows are ignored even when Android reports them as
  application windows; the previous app stays in front. Reason: on the user's OnePlus 11R
  (OxygenOS 16) the Shelf, a panel opened by swiping down on the home screen, is reported as an
  application window and showed up as a foreground "app". (It cannot open over other apps; an
  earlier log line that went straight from Instagram to the Shelf was the home screen passing too
  quickly to register.) More phone-maker panels get added here as device tests find them (a
  candidate for the rules file in Step 05). The diagnostic log now says what it ignored
  (`kept <app>; focus on <TYPE> <package>`); moments with no focused window are not logged.
- **Step 01, fixed debug signing key (user's choice "a", 2026-11-01):** `app/debug.keystore`
  (standard debug alias/passwords) is committed on purpose and `.gitignore` makes an exception for
  it, so each CI build installs as an update. Debug builds only; release signing is Step 13 and
  must never be committed.
- **Step 01, new dependency versions:** core-ktx 1.19.1, lifecycle-runtime-compose 2.11.0,
  DataStore 1.3.0, kotlinx-coroutines 1.11.0, kotlinx-serialization-json 1.12.0. `:app` unit tests
  use JUnit 5 like `:detection`.

## Known issues / follow-ups
- **No app icon yet:** the launcher shows Android's default icon (Step 09 / Step 13).
- **Window bounds flicker during gestures:** app-open, back and app-switch animations report a
  moved window for a moment (e.g. `[0,70][1240,2842]`, `[-371,0][868,2772]`, `[1240,0][2480,2772]`)
  before it settles at full screen. Harmless now; the counting steps (03/04) must ignore these
  transient bounds rather than treat them as a new surface size.
- **Play Protect blocks sideloaded installs (seen 2026-11-01, OnePlus 11R, Android 16):** "App
  blocked to protect your device" with only an OK button, because the APK declares an
  accessibility service and was opened from a file manager. Workarounds for testing: pause Play
  Protect scanning while installing, or `adb install`. Product impact: users installing from
  GitHub Releases in regions with this protection will hit the same wall; the README and the
  distribution plan (ADR-017: Google Play, F-Droid) must account for it. Never try to evade the
  check in code.
- **No unlock broadcast on OxygenOS 16:** `ACTION_USER_PRESENT` was never received on the user's
  OnePlus 11R (screen on/off were). Later steps must not rely on it alone; e.g. treat the first
  app window event after "screen on" as the unlock.
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
- **2026-11-01, Step 01 (attempt 1).** OnePlus 11R, Android 16. APK: `app-debug` from CI run #12.
  Blocked at install by Google Play Protect ("App blocked to protect your device"). Installed after
  pausing Play Protect scanning; the accessibility toggle then needed "Allow restricted settings".
  Passed: service connected (no restart from 17:20 to 17:48+), heartbeat every 60 s, foreground
  changes for Instagram, Snapchat, Chrome, Brave, Claude, launcher (`com.android.launcher`);
  content-changed events on/off when entering/leaving Instagram and Snapchat; no
  `com.android.systemui` line ever appeared. Issue: the OnePlus Shelf (`com.coloros.assistantscreen`,
  home screen only) was reported as a foreground app.
- **2026-11-01, Step 01 (attempt 2).** OnePlus 11R, Android 16. APK: CI run #16 (fixed debug key,
  panel fix). Passed: Shelf opened from the home screen is ignored (`kept com.android.launcher;
  focus on APPLICATION com.coloros.assistantscreen`); the screenshot tool is ignored too (`focus
  on OTHER com.oplus.screenshot`); Instagram on/off still correct. Notification shade inside
  Instagram: kept Instagram (`focus on SYSTEM com.android.systemui`), passed. Keyboard (Instagram
  search): Instagram stayed in front, passed. Split screen (Claude top, Instagram bottom):
  Instagram detected with half-screen bounds `[0,1491][1240,2771]` and both apps visible; the
  container `com.oplus.pscanvas` also listed as visible (now ignored); a switch to the Claude
  half was not seen in the log. Lock: screen off three times (about 5.5 min total, longest
  about 2 min 46 s), `Connected since` unchanged (no restart); no "unlocked" line. The
  Emergency SOS app (`com.oplus.sos`) appeared after rapid power presses.
