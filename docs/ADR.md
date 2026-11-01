# Architecture Decision Records (ADR)

Project codename: **Unrot** (working name; see SPEC.md §1.3 for how to rename).
Status of this file: **Accepted** as of 2026-11-01. Every ADR below is binding for the coding agent.
Companion document: `docs/SPEC.md` (the build specification). If SPEC.md and this file ever disagree, **this file wins on the "why" and the boundaries; SPEC.md wins on exact numbers and step order.** If a conflict cannot be resolved by that rule, stop and ask the user.

## How to read and maintain this file

Each ADR has: **Context** (the forces), **Decision** (what we do), **Rejected alternatives** (and why), **Consequences** (what this costs us and what it forces elsewhere).

Rules for the coding agent:
1. Never silently deviate from an ADR. If implementation reality contradicts an ADR (an API does not behave as described, a library is abandoned), write a new ADR at the bottom with status `Proposed`, explain the evidence, and ask the user before acting on it.
2. Never delete an ADR. Superseded ADRs get `Status: Superseded by ADR-0NN` and stay.
3. Number new ADRs sequentially.

---

## ADR-001: Android only, fully native (Kotlin + Jetpack Compose)

**Status:** Accepted

**Context.** The product's core feature (counting individual reels/shorts as they are swiped) is only possible through Android's AccessibilityService. iOS sandboxes this completely; iOS is explicitly out of scope. Nearly every hard part of the app (accessibility service, overlays, haptics, sensors, shaders) is native Android API.

**Decision.** Single-platform Android app written in Kotlin. UI in Jetpack Compose. No cross-platform framework.

**Rejected alternatives.**
- Flutter / React Native: would add a bridge layer between the UI and the accessibility service, which is where all the complexity lives. No benefit without iOS.
- XML Views: Compose is the current standard and is faster to iterate. (Overlays are still `View`-hosted; see ADR-008.)

**Consequences.** All code is Kotlin. Compose must be hosted inside accessibility overlay windows, which requires manual lifecycle wiring (SPEC §9.4).

---

## ADR-002: minSdk 29 (Android 10), target/compile = latest stable

**Status:** Accepted

**Context.** We need `AccessibilityEvent.getScrollDeltaY()` (API 28), hardware-accelerated `Canvas.drawVertices` (expected API 29; verify, see ADR-012), and predictable background-activity rules. The main competitor (BrainPal) also requires Android 10+. Android 9 and older are a negligible share of active devices in late 2026.

**Decision.** `minSdk = 29`. `compileSdk` and `targetSdk` = the latest stable API level at implementation time (Google Play requires targeting a recent level). APIs above 29 are guarded with `Build.VERSION.SDK_INT` checks and documented fallbacks.

**Rejected alternatives.** minSdk 26: would require fallbacks for scroll deltas and mesh rendering for a tiny audience.

**Consequences.** Version-guarded features: AGSL shaders (33+), `VibratorManager` (31+), `POST_NOTIFICATIONS` runtime permission (33+), `isAccessibilityTool` manifest attribute (33+), restricted-settings flow (13+).

---

## ADR-003: The AccessibilityService is the single engine for tracking AND enforcement

**Status:** Accepted

**Context.** We need (a) per-reel detection, (b) knowledge of the foreground app, (c) the ability to block or cover reels, (d) launching UI from the background. An AccessibilityService provides all four: it receives scroll events with indices and deltas, it can enumerate windows, it can add `TYPE_ACCESSIBILITY_OVERLAY` windows without `SYSTEM_ALERT_WINDOW`, it can call `performGlobalAction`, and it is exempt from Android 10+ background-activity-launch restrictions.

**Decision.** One service class, `UnrotAccessibilityService`, is the only component that observes other apps and the only component that enforces limits. No `UsageStatsManager`, no VPN/`NetworkExtension`-style traffic sniffing, no `SYSTEM_ALERT_WINDOW`, no device admin.

**Rejected alternatives.**
- Network traffic estimation (Gemini's proposal): requires a VPN service, is inaccurate, is a far bigger privacy ask, and is a Play policy risk.
- `UsageStatsManager` for foreground detection: an extra special permission to grant, delayed data, adds nothing the service doesn't already know.
- `SYSTEM_ALERT_WINDOW` overlays: an extra permission; `TYPE_ACCESSIBILITY_OVERLAY` does the same job from inside the service.

**Consequences.** If the user disables the service, the app does nothing; the app must detect this and say so clearly (SPEC §14). Google Play's Accessibility API policy applies in full (ADR-019). Service robustness is critical: a crash loop makes Android disable the service.

---

## ADR-004: Event-driven detection with dynamic event subscription; never poll the node tree

**Status:** Accepted

**Context.** `TYPE_WINDOW_CONTENT_CHANGED` fires many times per second during video playback (progress bars, like counters, captions). Each `AccessibilityNodeInfo` read is a cross-process (binder) call into the other app. Walking the whole tree on every event would cost battery and cause jank. But if we restrict `packageNames` in the service config, we stop receiving the window events that tell us the user left the target app.

**Decision.**
1. Static config subscribes to `typeWindowStateChanged | typeWindowsChanged | typeViewScrolled` for **all packages** (no `packageNames` filter). Non-target packages are dropped in the first lines of `onAccessibilityEvent` (cheap string compare).
2. `typeWindowContentChanged` is **added at runtime** via `setServiceInfo()` only while a target package is in the foreground and **removed** when it leaves.
3. Counting is driven by `TYPE_VIEW_SCROLLED` events plus a settle timer. The node tree is only touched (a) to resolve the scrolled container's bounds and direct children at settle time, and (b) in Inspector mode.
4. `notificationTimeout = 100` ms to coalesce bursts.

**Rejected alternatives.** Polling `rootInActiveWindow` on a timer (wasteful); Gemini's "measure bounding boxes on every content change" (expensive and unnecessary since scroll events already carry indices).

**Consequences.** Battery cost is near zero outside target apps and low inside them. Detection logic must tolerate coalesced/dropped intermediate events (it only needs the settled state).

---

## ADR-005: Detection logic is a pure-Kotlin state machine in a JVM module, tested by replaying recorded traces

**Status:** Accepted

**Context.** Detection will break whenever Instagram/YouTube/TikTok ship UI changes. We need fast, deterministic tests that don't need a phone, and we need contributors to be able to add test cases by recording real sessions.

**Decision.**
- Gradle module `:detection` is a **pure Kotlin/JVM** module with no Android dependencies.
- The Android service converts each `AccessibilityEvent` into an immutable `Signal` data class immediately inside `onAccessibilityEvent` (events are recycled after the callback returns and must not be retained).
- The detector is a reducer: `fun step(state: DetectorState, input: Signal, nowMs: Long): StepResult(newState, outputs: List<DetectorOutput>)`. Time is injected; timers are modeled as explicit `Tick` signals.
- Recorded traces (JSON Lines, SPEC §15.3) live in `detection/src/test/resources/traces/` with an expected-count sidecar file. CI replays every trace.

**Rejected alternatives.** Logic inside the service class (untestable without a device); Robolectric tests (slow, and Robolectric does not emulate other apps' accessibility trees anyway).

**Consequences.** A thin, boring Android adapter layer; all smarts in `:detection`. Every detection bug fix must come with a trace that reproduces it.

---

## ADR-006: Definition of "one reel" and the three counting strategies

**Status:** Accepted

**Context.** "Count reels" sounds obvious but is not. We must decide what counts, and we need multiple detection strategies because apps expose different data.

**Decision — what counts:**
1. A **reel surface** is a vertically scrolling container whose bounds cover ≥ 75% of the app window height and ≥ 90% of its width, inside a tracked package, and whose settled state shows exactly one full-height item (exact thresholds in rules JSON; SPEC §7.6).
2. The **first reel** shown when a reel surface is entered counts after a dwell of 1000 ms on that surface.
3. Each subsequent **forward move to a reel not yet seen in the current surface session** counts as one. Swiping back up to an already-seen reel and down again does not count again.
4. **Auto-advance** (the app scrolls itself) counts; the user watched another reel.
5. Half swipes that bounce back do not count (no settled index change).
6. Inline reels in normal feeds, stories (horizontal), comment sheets, and share sheets do not count (they fail the reel-surface test).
7. A **surface session** ends when: the surface leaves the screen, the package leaves the foreground, the screen turns off, or the container identity changes. A new session restarts "seen" tracking.

**Decision — strategies** (chosen per surface by rules JSON, with a default chain):
- `INDEX`: use `fromIndex`/`toIndex`/`itemCount` from `TYPE_VIEW_SCROLLED`. Settled when `fromIndex == toIndex` (or the single fully-visible child) after `settleMs` without new scroll events. Count = new max index.
- `SIGNATURE`: at settle, find the child node containing the container's center point; build an in-memory hash from structural properties plus up to 64 characters of its visible text/contentDescription; a new hash = a new reel. **The raw text is never stored, logged, or transmitted;** only an in-RAM hash ring of the last 64 signatures exists.
- `DELTA`: accumulate `scrollDeltaY`; at settle, if accumulated downward delta ≥ `deltaThresholdRatio × containerHeight`, count one.
- Default chain: `INDEX` if indices are populated (`itemCount > 0 && fromIndex >= 0`), else `SIGNATURE`, else `DELTA`.

Index-jump guard: if the new max index exceeds the previous max by 2–3, count the difference (fast flings); if by more than 3, count 1 and emit an `Anomaly` output (likely list re-indexing).

**Rejected alternatives.** Counting every scroll event (massive overcount); counting time instead of reels (that's not the product); counting re-watched reels (inflates counts and punishes looking back).

**Consequences.** Counts are "reels newly seen," which we state in the UI. Accuracy will not be 100%; the UI says "reels," never "exactly N." The SIGNATURE strategy reads visible text in memory; this is disclosed (ADR-019).

---

## ADR-007: Detection rules are data (JSON) shipped in the APK and updatable from the repo; rules can never execute code

**Status:** Accepted

**Context.** Target apps update every 1–2 weeks. A code release per breakage is too slow, and the open-source community can help maintain rules if they're plain data.

**Decision.**
- `rules/detection-rules.json` lives in the repo root (single source of truth). A Gradle task copies it into `app/src/main/assets/` at build time so every APK has a bundled copy.
- The app fetches the latest copy from `https://raw.githubusercontent.com/<OWNER>/<REPO>/main/rules/detection-rules.json` at most every 12 hours (WorkManager, network-connected constraint) and on app open if older than 12 h. User can disable remote updates ("Offline mode").
- Validation: strict schema check (kotlinx.serialization, `ignoreUnknownKeys = true`), `schemaVersion` must be ≤ the app's supported version, regexes must compile, numeric thresholds must be in sane ranges. Invalid → keep last-known-good. Last-known-good stored in app files dir.
- Rules contain only matchers and numbers. No scripting, no expressions, no URLs that the app follows.

**Rejected alternatives.** Firebase Remote Config (proprietary dependency, breaks F-Droid; ADR-015); hardcoded rules (slow fixes).

**Consequences.** Network permission is required (only for rules + optional friend battles). Need a `rulesVersion` shown in Settings → About and attached to bug reports.

---

## ADR-008: Enforcement = accessibility overlay over reel surfaces only; whole-app blocking only in Strict mode

**Status:** Accepted

**Context.** Users still need Instagram DMs, YouTube tutorials, etc. The harm is the short-form feed. Gemini's proposed `SYSTEM_ALERT_WINDOW` overlay with `FLAG_NOT_FOCUSABLE` cannot intercept the back key anyway.

**Decision.**
- When the user's allowance is exhausted and a reel surface is on screen, the service adds a full-screen, touch-consuming `TYPE_ACCESSIBILITY_OVERLAY` window ("Block overlay") hosted in a `ComposeView`. It is **not focusable**, so the system Back gesture passes through to the underlying app; that is intended: pressing Back leaves the reels surface, the surface-exit signal fires, and the overlay is removed.
- While the overlay is shown, the service requests transient audio focus (`AUDIOFOCUS_GAIN_TRANSIENT`) so the video underneath pauses in apps that respect audio focus.
- Overlay buttons: "Leave reels" (performs `GLOBAL_ACTION_BACK`, then `GLOBAL_ACTION_HOME` if the surface is still present after 600 ms) and "Earn more reels" (launches a challenge, ADR-013).
- Fallback if `addView` throws: `performGlobalAction(GLOBAL_ACTION_HOME)` then launch `BlockActivity` (allowed from an accessibility service context despite background-launch restrictions).
- **Strict mode** (opt-in): blocks the entire tracked package once the allowance is exhausted, not just reel surfaces.
- A separate non-touchable **Live Pill** overlay shows the running count on reel surfaces (opt-out).

**Rejected alternatives.** `SYSTEM_ALERT_WINDOW` (extra permission, cannot capture Back); always sending HOME (jarring, kills legitimate app use); blocking whole apps by default (users would uninstall).

**Consequences.** Overlays must survive configuration changes and be removed reliably on every exit path (SPEC §9.6). Compose-in-overlay needs a custom `LifecycleOwner`/`SavedStateRegistryOwner`/`ViewModelStoreOwner`.

---

## ADR-009: We do not try to make the app impossible to bypass

**Status:** Accepted

**Context.** The user can always disable the service in system settings, uninstall, use safe mode, or use a web browser. Making these hard requires device-admin tricks or blocking the Settings app, which is hostile, fragile across OEMs, and a Play policy risk.

**Decision.**
- No device admin, no blocking of Settings screens, no uninstall prevention.
- Instead, **commitment friction inside our own app**: any change that loosens restrictions (raise limit, disable blocking, disable strict mode, untrack an app, increase unlock budget) requires a 60-second "cool-down" screen, plus a completed challenge when Strict mode is on. Tightening changes apply instantly.
- If the service is disabled, the app shows it plainly the next time it opens, records the gap ("protection was off from X to Y"), and (if friend battles are on) marks that day as "Protection off" on the leaderboard.

**Rejected alternatives.** Device admin "uninstall protection" (Play has cracked down; hostile UX); detecting the Accessibility settings page and kicking the user out (fragile, hostile).

**Consequences.** Marketing/README must never promise "unbypassable." The honest framing is "a commitment device with friction."

---

## ADR-010: Local storage = Room (SQLite) + DataStore; no database encryption; backups disabled

**Status:** Accepted

**Context.** We store counts, timestamps, app IDs, challenge results, and settings. No screen content is ever stored (ADR-019). App-private storage is sandboxed by Android. SQLCipher adds ~10 MB of native libraries and complexity for little gain.

**Decision.**
- Room for event/history data; Preferences DataStore for settings.
- No SQLCipher in v1.
- `android:allowBackup="false"` and empty `dataExtractionRules` (privacy-first; counts are not worth syncing through Google backup).
- Retention: per-reel events kept 90 days, then rolled into daily summaries (kept forever, they're tiny).

**Rejected alternatives.** SQLCipher (cost > benefit for count data); ObjectBox (proprietary-adjacent licensing concerns for F-Droid, unnecessary).

**Consequences.** Users lose history on uninstall or phone change. Acceptable for v1; an export/import JSON feature covers power users (SPEC §8.5).

---

## ADR-011: Manual dependency injection

**Status:** Accepted

**Context.** The object graph is small (DB, repositories, rules store, settings, clock). Hilt adds annotation processing, build time, and a learning surface for contributors, and needs special handling for the accessibility service.

**Decision.** A single `AppContainer` created in `UnrotApp.onCreate()`; the service and activities read it via `(applicationContext as UnrotApp).container`. Constructor injection everywhere else.

**Rejected alternatives.** Hilt/Dagger, Koin.

**Consequences.** Container must be lazily initialized and thread-safe (`by lazy` with default synchronization).

---

## ADR-012: Brain visual = CPU-deformed vertex mesh drawn with `Canvas.drawVertices`, AGSL post-effect on API 33+

**Status:** Accepted

**Context.** The degrading brain is the emotional core of the product and the main visual differentiator versus BrainPal. It must look alive (deforming, drooping, cracking) and shift from vivid to grey and pixelated.

**Decision.**
- Brain silhouette defined as a star-shaped closed outline in normalized coordinates; a polar mesh (1 center + 12 rings × 48 spokes = 577 vertices) is built inside it.
- Each frame, vertices are deformed on the CPU in preallocated `FloatArray`s (no per-frame allocation) and drawn with `android.graphics.Canvas.drawVertices` (triangles, per-vertex colors) via `drawIntoCanvas { it.nativeCanvas }`.
- Fold lines (sulci) are defined in mesh parameter space so they deform with the mesh.
- On API 33+, an AGSL `RuntimeShader` applied as a `RenderEffect` (`graphicsLayer { renderEffect = ... }`) does desaturation + pixelation by severity. On API 29–32, a `ColorMatrix` saturation filter is used (no pixelation).
- Animation runs only while the composable is on screen and the lifecycle is RESUMED; frozen when system animations are disabled (reduced motion).
- **Verification step:** `drawVertices` hardware-acceleration support from API 29 must be verified on the first device test. If it renders blank or wrong, set a software layer on that composable only.

**Rejected alternatives.** Lottie/Rive (would need pre-authored animations per state; less "alive"; Rive runtime adds weight); static images per stage (what competitors do); OpenGL ES (overkill).

**Consequences.** The renderer is custom code that needs care, but it's self-contained (`ui/brain/`).

---

## ADR-013: Challenges = Haptic Breathing, Face-Down, Steady Timer. Camera gaze and biometric gates are dropped.

**Status:** Accepted

**Context.** Gemini proposed a camera "look 2 meters away" gaze check and a biometric gate. Face detection can tell if a face is present or turned, not where the eyes focus or how far. Camera permission is a heavy ask and ML Kit is proprietary (ADR-015). Biometric auth adds no friction value.

**Decision.** Three challenges:
1. **Haptic Breathing:** hold thumb on screen for 30 s while following vibration-guided breathing; release resets.
2. **Face-Down:** phone face-down and still for 15 continuous seconds (gravity/accelerometer + proximity when available).
3. **Steady Timer:** 60-second timer that slows/pauses when the phone shakes.
Completing a challenge grants extra reels (the "unlock"), within a daily unlock budget.

**Rejected alternatives.** Camera gaze (infeasible as specified, privacy cost, proprietary SDK); biometric delay (no value).

**Consequences.** No camera permission, no biometric permission. Every challenge needs a no-haptics / no-sensor fallback.

---

## ADR-014: Friend Battles via Supabase (anonymous auth, RLS, daily summaries only); optional and off by default

**Status:** Accepted

**Context.** Friend competition needs a shared server. Gemini proposed gRPC/WebSockets with hourly sync, which is overkill. The project is free and open source, so the backend must be free-tier friendly and forks must not hit the official backend by accident.

**Decision.**
- Supabase (Postgres + PostgREST + Auth) with **anonymous sign-in**, a `profiles` table with a random friend code, `friendships`, and `daily_scores` (day, reel count, challenges failed, protection-off flag). Row Level Security ensures a user sees only themselves and accepted friends.
- Friend requests go through `SECURITY DEFINER` RPC functions so nobody can enumerate profiles.
- Client sync: upsert today's and yesterday's rows on app open and every 60 minutes via WorkManager (network constraint).
- Supabase URL and anon key are injected at build time from `local.properties` / CI secrets. If absent, the Battles tab shows "not configured in this build" and the rest of the app works.
- Off by default; the user opts in.

**Rejected alternatives.** Firebase (proprietary SDK, ADR-015); a custom server (hosting cost and maintenance); peer-to-peer (no reliable transport between phones).

**Consequences.** Supabase's free tier pauses inactive projects; the maintainer must keep it active or upgrade. Anonymous accounts are lost on uninstall (stated in the UI). Scores are self-reported and can be faked by modified clients; acceptable for a friendly game.

---

## ADR-015: No proprietary dependencies (F-Droid compatible)

**Status:** Accepted

**Context.** The app is free and open source. F-Droid, the main FOSS store, rejects or flags apps with proprietary libraries (Google Play Services, Firebase, ML Kit, closed analytics/crash SDKs).

**Decision.** Allowed: AndroidX, Jetpack Compose, Kotlin/kotlinx libraries, Room, WorkManager, DataStore, OkHttp, supabase-kt (MIT) and its Ktor client. Forbidden: anything from `com.google.android.gms`, `com.google.firebase`, `com.google.mlkit`, any analytics or crash-reporting SDK, any ads SDK. Fonts are bundled files under the SIL Open Font License, not downloaded via Google Fonts providers.

**Rejected alternatives.** Crashlytics (proprietary); Firebase Remote Config (proprietary).

**Consequences.** No remote crash reports. Instead: a local diagnostic log the user can view and choose to share (SPEC §14.4).

---

## ADR-016: License = GPL-3.0-or-later

**Status:** Accepted

**Context.** The motivation for this project is that competitors charge money for this functionality. A permissive license would let anyone take the code, close it, and sell it.

**Decision.** `GPL-3.0-or-later` for all code. Detection rules JSON and docs under the same license. Bundled fonts keep their OFL license (in `app/src/main/res/font/LICENSES/`). Third-party skill files inside `.claude/skills/` keep their original licenses; their license files must be present before the repo goes public (SPEC §19.6).

**Consequences.** Derivatives must remain open. Some companies won't reuse the code; that's intended.

---

## ADR-017: Distribution = GitHub Releases + Google Play + F-Droid; releases signed with the developer's own key by a verified developer

**Status:** Accepted

**Context.** Google's Android developer verification program requires apps installed on certified devices (including sideloaded ones) to come from identity-verified developers, phasing in during 2026–2027. Sideloaded APKs on Android 13+ also hit the "restricted settings" block for accessibility services.

**Decision.**
- The maintainer registers as a verified developer and signs all release APKs with one release key (kept out of the repo; CI secret).
- Google Play: primary channel for non-technical users (no restricted-settings friction).
- GitHub Releases: signed APK for everyone else; README documents the restricted-settings unlock.
- F-Droid: submit after v1; prefer reproducible builds so F-Droid can ship the developer-signed APK.

**Consequences.** Builds must be reproducible (no timestamps or machine-specific data embedded; SPEC §19.4).

---

## ADR-018: Build in the cloud; CI produces installable APKs

**Status:** Accepted

**Context.** The coding agent runs in a cloud environment, not on the user's laptop. The cloud environment may or may not be able to download the Android SDK, depending on its network settings. The user tests on a physical phone.

**Decision.**
- The agent attempts a local Gradle build in its environment. If the Android SDK cannot be installed (network blocked), the agent does not fight it: GitHub Actions is the authoritative build. Every push builds `assembleDebug`, runs unit tests, and uploads the debug APK as a workflow artifact the user downloads to the phone.
- Gradle wrapper committed; versions in `gradle/libs.versions.toml`.

**Consequences.** Each phase in SPEC ends with "CI green + artifact downloadable." The user's phone is the only real test device; the agent must write precise manual test steps for the user.

---

## ADR-019: Privacy and Play-policy posture

**Status:** Accepted

**Context.** The accessibility permission can see everything on screen. Trust is the main reason a user would pick a FOSS app over BrainPal. Google Play requires a prominent in-app disclosure and affirmative consent before enabling a non-accessibility-tool service.

**Decision.**
- **Never persist, log (in release builds), or transmit** any text, content description, username, caption, or image from other apps. Allowed to persist: package name, our own app/surface IDs, timestamps, counts, the target app's versionCode, and structural trace data (class names, view IDs, bounds, indices) **only** when the user explicitly records a trace in Inspector mode.
- `android:isAccessibilityTool="false"` declared (API 33+ attribute; ignored below).
- A full-screen in-app disclosure precedes any redirect to accessibility settings, with "Agree" and "Not now" buttons; no pre-checked consent; the text states what is read and what is never stored (SPEC §13.3).
- `PRIVACY.md` in repo, linked from the app and the store listing.
- The brain is labeled as a playful visual, not a medical claim.

**Consequences.** Signature strategy (ADR-006) is the only place text is read, in memory only; the disclosure mentions it.

---

## ADR-020: Time and day boundaries

**Status:** Accepted

**Context.** "Today" must behave sensibly across midnight, time-zone travel, DST, and manual clock changes.

**Decision.**
- Every reel event stores `epochMs` (UTC) and `localDate` (ISO `yyyy-MM-dd`) computed from the device's current `ZoneId` at the moment of the event. The day a reel belongs to never changes afterwards.
- "Today's count" = count of events where `localDate == LocalDate.now(currentZone)`. Computed lazily on each event and each UI read; no reliance on a midnight alarm.
- If a surface session spans midnight, reels after midnight belong to the new day; the allowance resets at that moment (the block overlay disappears on the next settle check).
- Clock moved backwards/forwards manually: accepted as-is. No anti-cheat.

**Consequences.** Daily-summary rollups key on `localDate` strings.

---

## ADR-021: Threading model

**Status:** Accepted

**Context.** `onAccessibilityEvent` runs on the main thread. Events are recycled when it returns. Node methods do binder IPC and can block.

**Decision.**
- Main thread: convert `AccessibilityEvent` → `Signal` (copy primitives, call `event.source` once if needed for that event type) and hand off. Budget: < 1 ms typical.
- Detector thread: a single-threaded coroutine dispatcher (`Dispatchers.Default.limitedParallelism(1)`) owns `DetectorState`, runs `step()`, does settle-time node inspection, and schedules `Tick`s.
- Overlay add/remove and `performGlobalAction` hop back to `Dispatchers.Main`.
- DB writes on `Dispatchers.IO` via repository; increments are write-through and idempotent (SPEC §8.3).

**Consequences.** All detector state is confined to one thread: no locks inside `:detection`.
