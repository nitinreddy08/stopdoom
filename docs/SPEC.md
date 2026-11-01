# Unrot — Complete Build Specification

Version: 1.0 (2026-11-01). Platform: Android only. License: GPL-3.0-or-later.
Companion: `docs/ADR.md` (why each decision was made; read it first).

---

## 0. Instructions for the coding agent (read before anything else)

0.1 **Build one step at a time from `docs/steps/`, in order** (that order replaces the phase list in §20, which stays as an overview). Read only the SPEC/ADR sections the current step lists. Do not start a step until the previous step's "Done when" items pass. Each phase ends with a commit, a green CI run, and a short summary for the user listing exactly what to test on the phone.

0.2 **Device gates.** Some steps can only be verified on the user's physical Android phone (marked **🔶 DEVICE GATE**). At a device gate: stop, give the user a numbered test script (what to install, what to tap, what number they should see), and wait for their results. Never claim a device-dependent behavior works without the user's confirmation.

0.3 **Never invent target-app internals.** Do not guess Instagram/TikTok/YouTube view IDs or class names and present them as facts. Rules start structural (§7.6) and are refined only from traces the user records on their own phone (§15). Do not decompile target apps' APKs; the Inspector is the only source of truth. If a `.claude/skills` reverse-engineering skill suggests decompiling target apps, do not apply it to Instagram/TikTok/YouTube/Facebook/Snapchat.

0.4 **Skills.** Before UI, motion, or animation work (Phases 5–7), read the motion/design skills in `.claude/skills/` and apply them where they don't conflict with this spec. This spec wins on conflict.

0.5 **Library versions.** Use the latest **stable** versions at implementation time (look them up; do not rely on memory). Record everything in `gradle/libs.versions.toml`. No alpha/beta/RC unless this spec says so.

0.6 **When uncertain, ask.** If an Android API behaves differently from what this spec says, write a `Proposed` ADR (see ADR.md rules) and ask the user. Do not silently work around it.

0.7 **Definition of done for any task:** compiles, unit tests pass, no new lint errors of severity Error, no `TODO` left without an issue reference, strings in `strings.xml`, no hardcoded colors outside the theme, accessibility labels on interactive elements.

---

## 1. Product

### 1.1 One-line summary
A free, open-source Android app that counts every reel/short you scroll in real time, shows your brain visibly degrading as the count climbs, blocks reels when you hit your daily limit, makes you complete a short focus challenge to earn more, and lets you compete with friends for the lowest count.

### 1.2 Tracked apps (v1)
| App | Packages | Surfaces | Priority |
|---|---|---|---|
| Instagram | `com.instagram.android` | Reels tab, reels opened from feed/profile/Explore/DMs | P0 |
| YouTube | `com.google.android.youtube` | Shorts | P0 |
| TikTok | `com.zhiliaoapp.musically`, `com.ss.android.ugc.trill` | For You / Following feeds | P1 |
| Facebook | `com.facebook.katana` | Reels | P1 |
| Snapchat | `com.snapchat.android` | Spotlight | P2 |

Not tracked in v1: web browsers, Instagram Lite, modded clients (users may add packages later; §22).

### 1.3 Naming
Working name **Unrot**, application ID `app.unrot`. All names are defined once: `applicationId`/`namespace` in `app/build.gradle.kts`, `app_name` in `strings.xml`, `OWNER`/`REPO` constants in `RulesConfig.kt`. Renaming = change those places plus the package directory rename (`git mv`).

### 1.4 Differentiators vs. paid competitors (keep these excellent)
1. Free and open source; privacy is verifiable.
2. Pattern-interrupt challenges instead of only hard stops.
3. A genuinely alive, deforming brain visual; the whole UI desaturates with it.
4. Blocks reels only (DMs and normal videos still work) unless the user chooses Strict mode.
5. Community-maintained detection rules.

---

## 2. Glossary
- **Tracked package:** a package listed in the rules.
- **Surface:** a screen region type defined in the rules (e.g., `instagram_reels`).
- **Reel surface (on screen):** a container currently on screen that matches a surface rule.
- **Surface session:** continuous presence of one reel surface instance; ends per ADR-006 §7.
- **Settle:** no `TYPE_VIEW_SCROLLED` event for `settleMs` from the active container.
- **Allowance:** `dailyLimit + reelsGrantedByChallengesToday`.
- **Unlock:** a completed challenge granting extra reels.
- **Severity (s):** 0.0–1.0 value that drives the brain and the UI theme.

---

## 3. Non-negotiable rules
1. No screen content (text, captions, usernames, images) from other apps is ever written to disk, logged in release builds, or sent over the network (ADR-019).
2. No proprietary dependencies (ADR-015).
3. The accessibility service must never crash: every entry point is wrapped in `try/catch (t: Throwable)` that records to the diagnostic log and continues.
4. No promises of "unbypassable" anywhere in UI, README, or store text.
5. The brain is presented as a playful metaphor; About screen states it is not a medical measure.
6. Everything works with friend battles disabled and with no network.

---

## 4. Tech stack
| Concern | Choice |
|---|---|
| Language | Kotlin 2.x, JVM target 17 |
| UI | Jetpack Compose (BOM), Material 3 as a base, heavily themed |
| Navigation | Navigation Compose with type-safe routes |
| Async | Kotlin coroutines + Flow |
| Serialization | kotlinx.serialization |
| Local DB | Room (KSP) |
| Settings | Preferences DataStore |
| Background | WorkManager |
| HTTP | OkHttp (rules fetch) |
| Battles backend | Supabase via supabase-kt (Auth, Postgrest), Ktor OkHttp engine |
| DI | Manual `AppContainer` (ADR-011) |
| Tests | JUnit 5 (or JUnit 4 if Android tooling forces it) + Truth/AssertK in `:detection`; Room in-memory tests as instrumented tests (optional) |
| Lint/format | ktlint via Spotless; Android Lint |
| Build | Gradle Kotlin DSL, version catalog, Gradle wrapper |
| CI | GitHub Actions |

---

## 5. Repository layout

```
/
├─ .claude/skills/...            (already present; keep licenses, §19.6)
├─ .github/workflows/ci.yml
├─ docs/ADR.md
├─ docs/SPEC.md
├─ docs/TESTING_ON_DEVICE.md     (created in Phase 1, updated each phase)
├─ rules/detection-rules.json
├─ rules/schema.md               (human description of the schema)
├─ supabase/migrations/0001_init.sql
├─ fastlane/metadata/android/en-US/ (F-Droid/Play listing text, Phase 9)
├─ app/                          (Android application module)
│  └─ src/main/java/app/unrot/
│     ├─ UnrotApp.kt
│     ├─ AppContainer.kt
│     ├─ service/                (UnrotAccessibilityService, SignalMapper, ForegroundTracker,
│     │                           NodeInspector, OverlayManager, OverlayLifecycleOwner,
│     │                           ScreenStateReceiver, AudioFocusHelper, TickScheduler)
│     ├─ enforcement/            (AllowanceCalculator, EnforcementController)
│     ├─ data/                   (db/, dao/, entity/, repo/, settings/)
│     ├─ rules/                  (RulesRepository, RulesFetchWorker, RulesValidator, RulesConfig)
│     ├─ battles/                (SupabaseClientProvider, BattlesRepository, SyncWorker)
│     ├─ challenges/             (breathing/, facedown/, steady/, ChallengeResult)
│     ├─ health/                 (ServiceHealthMonitor, BreakageDetector, DiagnosticLog)
│     ├─ inspector/              (TraceRecorder, InspectorScreen)
│     └─ ui/                     (theme/, brain/, home/, stats/, battles/, settings/,
│                                 onboarding/, block/, pill/, commitment/, about/, nav/)
├─ detection/                    (pure Kotlin/JVM module, ADR-005)
│  └─ src/main/kotlin/app/unrot/detection/
│     ├─ Signal.kt  DetectorState.kt  Detector.kt  DetectorOutput.kt
│     ├─ rules/ (RuleModels.kt, SurfaceMatcher.kt)
│     └─ strategies/ (IndexStrategy.kt, SignatureStrategy.kt, DeltaStrategy.kt)
│  └─ src/test/resources/traces/  (*.jsonl + *.expected.json)
├─ LICENSE  (GPL-3.0 full text)
├─ PRIVACY.md
├─ README.md
└─ CONTRIBUTING.md (how to record traces and submit rule fixes)
```

---

## 6. Manifest, permissions, service config

### 6.1 Permissions
| Permission | Why | When requested |
|---|---|---|
| `INTERNET` | rules updates, battles | install-time |
| `ACCESS_NETWORK_STATE` | WorkManager network constraint | install-time |
| `POST_NOTIFICATIONS` (33+) | limit warnings | onboarding step, optional |
| `VIBRATE` | challenges, haptics | install-time |
| `RECEIVE_BOOT_COMPLETED` | not needed (the service is rebound by the system); **do not add** | — |

Not used (and must not be added): `SYSTEM_ALERT_WINDOW`, `PACKAGE_USAGE_STATS`, `CAMERA`, `USE_BIOMETRIC`, `QUERY_ALL_PACKAGES`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (restricted on Play; we open the settings list instead, §13.6).

Package visibility: to read installed versions of tracked apps, add a `<queries>` block listing each tracked package explicitly.

### 6.2 Application element
- `android:allowBackup="false"`, `android:dataExtractionRules="@xml/data_extraction_rules"` (excludes everything), `android:fullBackupContent="false"`.
- All activities `exported="false"` except `MainActivity` (launcher).
- `BlockActivity`: `excludeFromRecents="true"`, `taskAffinity=""`, `launchMode="singleTask"`.

### 6.3 Service declaration
```xml
<service
    android:name=".service.UnrotAccessibilityService"
    android:exported="true"
    android:label="@string/accessibility_service_label"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>
```

### 6.4 `res/xml/accessibility_service_config.xml`
```xml
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/accessibility_service_description"
    android:summary="@string/accessibility_service_summary"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowsChanged|typeViewScrolled"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagReportViewIds|flagRetrieveInteractiveWindows|flagIncludeNotImportantViews"
    android:canRetrieveWindowContent="true"
    android:canPerformGestures="false"
    android:isAccessibilityTool="false"
    android:notificationTimeout="100" />
```
No `packageNames` attribute (ADR-004). `typeWindowContentChanged` is toggled at runtime (§7.3).

---

## 7. Detection engine

### 7.1 Service lifecycle
`onServiceConnected()`:
1. Obtain `AppContainer`. Load rules (bundled → last-known-good → remote cache, newest valid wins).
2. Register `ScreenStateReceiver` (dynamic) for `ACTION_SCREEN_OFF`, `ACTION_SCREEN_ON`, `ACTION_USER_PRESENT`.
3. Record `serviceConnectedAt` in health store; start heartbeat (write `lastAliveAt` every 60 s while connected, via coroutine, cheap DataStore write).
4. Initialize `DetectorState.initial()`.

`onAccessibilityEvent(event)`: wrapped in try/catch. Steps in §7.2.

`onInterrupt()`: no-op except log.

`onUnbind()` / `onDestroy()`: remove all overlays, abandon audio focus, unregister receiver, cancel detector coroutine scope, flush pending DB writes, record `serviceDisconnectedAt`.

### 7.2 Event intake (main thread)
```
val pkg = event.packageName?.toString() ?: return
if (pkg == OUR_PACKAGE) return
when (event.eventType) {
  TYPE_WINDOW_STATE_CHANGED, TYPE_WINDOWS_CHANGED -> foregroundTracker.onWindowsEvent(...)  // always
  TYPE_VIEW_SCROLLED -> if (pkg in trackedPackages) detector.offer(SignalMapper.scrolled(event, nowMs))
  TYPE_WINDOW_CONTENT_CHANGED -> if (pkg in trackedPackages && inspector.recording) inspector.offer(...)
}
```
`SignalMapper.scrolled` copies: `packageName`, `className`, `source?.viewIdResourceName`, `source bounds in screen` (via `getBoundsInScreen(Rect)`), `fromIndex`, `toIndex`, `itemCount`, `scrollDeltaY` (`-1`/undefined → `null`), `windowId`, and `sourceIdentity` = `System.identityHashCode`-independent key: `"$windowId:$viewId:$className:${bounds.flattenToString()}"`. The `AccessibilityNodeInfo` from `event.source` is passed alongside (not inside the pure Signal) to the detector thread for settle-time inspection; it is dropped after settle.

### 7.3 Foreground tracking
`ForegroundTracker` determines the **foreground app package** robustly:
1. On any window event, call `service.windows` (requires `flagRetrieveInteractiveWindows`).
2. Pick the window with `type == TYPE_APPLICATION` and `isFocused`; if none focused, the one with `isActive`; if multiple application windows (split screen), prefer the focused one; record all visible application packages.
3. Ignore windows of type `TYPE_INPUT_METHOD`, `TYPE_SYSTEM` (notification shade, volume UI), `TYPE_ACCESSIBILITY_OVERLAY` (ours), `TYPE_SPLIT_SCREEN_DIVIDER`. Pulling down the notification shade does **not** change the foreground package.
4. Emit `Signal.Foreground(pkg, appWindowBounds, visiblePackages, nowMs)` only when the focused package or app window bounds change.
5. When the foreground becomes a tracked package: `setServiceInfo` adding `TYPE_WINDOW_CONTENT_CHANGED`; when it leaves: remove it. Always copy the current `serviceInfo`, modify `eventTypes`, and set it back.
6. `appWindowBounds` (not display metrics) is the reference for ratio checks, so split screen, freeform, foldables, and nav-bar modes all work.

Picture-in-picture: a PiP window is not focused; it does not count as foreground and is not a reel surface.

### 7.4 Signals (`:detection`)
```kotlin
sealed interface Signal {
  val nowMs: Long
  data class Foreground(val pkg: String?, val appWindow: Box, val visiblePkgs: Set<String>, override val nowMs: Long): Signal
  data class Scrolled(val pkg: String, val className: String?, val viewId: String?, val bounds: Box,
                      val fromIndex: Int, val toIndex: Int, val itemCount: Int, val deltaY: Int?,
                      val containerKey: String, override val nowMs: Long): Signal
  data class SettleSnapshot(val containerKey: String, val children: List<ChildInfo>,
                            val centerSignature: String?, override val nowMs: Long): Signal
  data class Tick(override val nowMs: Long): Signal
  data class ScreenOff(override val nowMs: Long): Signal
  data class ScreenOn(override val nowMs: Long): Signal
  data class RulesChanged(val rules: Rules, override val nowMs: Long): Signal
}
data class Box(val l: Int, val t: Int, val r: Int, val b: Int) { val w get() = r - l; val h get() = b - t }
data class ChildInfo(val bounds: Box, val className: String?)
```
`centerSignature` is computed on the Android side (it needs node text) as: SHA-256 of `className|childCount|first 64 chars of concatenated non-blank text+contentDescription of the center child subtree (depth ≤ 3, max 20 nodes)`, hex-truncated to 16 chars. The text never leaves that function.

### 7.5 Detector outputs
```kotlin
sealed interface DetectorOutput {
  data class SurfaceEntered(val appId: String, val surfaceId: String, val sessionId: Long, val nowMs: Long)
  data class SurfaceExited(val appId: String, val surfaceId: String, val sessionId: Long, val reason: ExitReason, val nowMs: Long)
  data class ReelCounted(val appId: String, val surfaceId: String, val sessionId: Long, val strategy: Strategy, val nowMs: Long)
  data class RequestSettleSnapshot(val containerKey: String)   // Android side gathers children + signature
  data class ScheduleTick(val atMs: Long)
  data class Anomaly(val code: String, val detail: String, val nowMs: Long) // detail: structural only
}
enum class ExitReason { LEFT_SURFACE, LEFT_APP, SCREEN_OFF, CONTAINER_CHANGED, RULES_CHANGED }
```
`ReelCounted` is emitted once per reel (multiple outputs for multi-step jumps).

### 7.6 Surface matching
A `Scrolled` signal matches surface rule `R` when **all** hold:
1. `pkg ∈ app.packages`.
2. If `R.viewIdRegex` non-empty: `viewId` matches one. If `R.classNameRegex` non-empty: `className` matches one. (Empty lists = no constraint.)
3. `bounds.h ≥ R.minHeightRatio × appWindow.h` and `bounds.w ≥ R.minWidthRatio × appWindow.w`.
4. Vertical scroll: `deltaY != 0` for at least one event in the session, OR indices changed, OR `R.requireVertical == false`.
5. At settle (`SettleSnapshot`): there exists exactly one child whose visible height ≥ `R.fullItemRatio × bounds.h` (default 0.9). This excludes normal feeds (several shorter items) and comment sheets (fail rule 3).
If several rules match, the first in file order wins.

Default structural rule for every tracked app (used until refined by traces):
`viewIdRegex = []`, `classNameRegex = []`, `minHeightRatio = 0.75`, `minWidthRatio = 0.9`, `fullItemRatio = 0.9`, `settleMs = 300`, `entryDwellMs = 1000`, `strategy = AUTO`, `deltaThresholdRatio = 0.6`.

Instagram's home feed is excluded by rule 5. YouTube's home feed is excluded by rule 5. TikTok's feeds match (correct: all TikTok feed content is short-form).

### 7.7 Counting state machine
```kotlin
data class DetectorState(
  val rules: Rules,
  val foregroundPkg: String?,
  val appWindow: Box?,
  val screenOn: Boolean,
  val session: Session?,          // null when no reel surface on screen
  val pending: Pending?,          // a candidate surface awaiting first settle/dwell
  val nextSessionId: Long
)
data class Session(
  val id: Long, val appId: String, val surfaceId: String, val containerKey: String,
  val enteredAtMs: Long, val firstReelCounted: Boolean,
  val lastScrollMs: Long, val strategy: Strategy,
  val maxIndex: Int, val lastSettledIndex: Int,
  val deltaAccum: Int, val containerHeight: Int,
  val seenSignatures: ArrayDeque<String>  // capacity 64
)
```
Transitions:
1. **Foreground change** to a non-tracked package, or `ScreenOff`: if session → emit `SurfaceExited` (LEFT_APP / SCREEN_OFF), clear session and pending.
2. **Scrolled** matching a surface rule:
   - No session, no pending → create `pending` (record containerKey), emit `RequestSettleSnapshot` after `settleMs` via `ScheduleTick(now + settleMs)`.
   - Session exists with same `containerKey` → update `lastScrollMs`, accumulate `deltaY` (positive = down), store latest indices, schedule tick at `now + settleMs`.
   - Session exists with different `containerKey` but same app & surface → if the old container is no longer on screen (snapshot will confirm), end old session (CONTAINER_CHANGED) and start pending for the new one. Instagram sometimes recreates the pager when switching tabs; this is expected.
3. **Scrolled not matching** (e.g., comments sheet inside reels): ignore for counting; do not end the session.
4. **Tick** at/after `lastScrollMs + settleMs` → emit `RequestSettleSnapshot(containerKey)`.
5. **SettleSnapshot**:
   - If the snapshot fails rule 5 (no full-height single item) → if pending: discard; if session: treat as mid-transition, ignore.
   - If pending and snapshot passes → create `Session` (strategy resolved: AUTO → INDEX if `itemCount > 0 && fromIndex >= 0`, else SIGNATURE if `centerSignature != null`, else DELTA), emit `SurfaceEntered`, schedule tick at `now + entryDwellMs` for the first-reel count, record initial index/signature as seen.
   - If session: apply strategy:
     - INDEX: `idx = the index of the full-height child` (= `fromIndex` when `fromIndex == toIndex`; else `fromIndex + position of full-height child among children`). If `idx > maxIndex`: `jump = idx - maxIndex`; if `jump ≤ 3` emit `jump` × `ReelCounted`; else emit 1 × `ReelCounted` + `Anomaly("INDEX_JUMP", "from=$maxIndex to=$idx count=$itemCount")`. Set `maxIndex = idx`. If `idx < maxIndex`: no count (going back). `lastSettledIndex = idx`.
     - SIGNATURE: if signature not in `seenSignatures` → count 1, add (evict oldest at 64).
     - DELTA: if `deltaAccum ≥ deltaThresholdRatio × containerHeight` → count 1. Reset `deltaAccum = 0` at every settle regardless.
6. **Tick** at `enteredAtMs + entryDwellMs` while session still alive and `!firstReelCounted` → emit `ReelCounted` for the first reel; `firstReelCounted = true`. If the user swiped before the dwell elapsed, the first reel still counts (they saw it) when the first swipe settles: handle by counting it at that settle before applying the strategy.
7. **Leaving the surface without leaving the app** (user taps Home tab inside Instagram): detected when a `Foreground` signal shows the same package but no `Scrolled` matching for the container AND a snapshot request for the container returns "container not found" (Android side returns an empty snapshot with flag `missing = true`). Then emit `SurfaceExited(LEFT_SURFACE)`. To avoid needing constant snapshots: on every `TYPE_WINDOW_STATE_CHANGED` / `TYPE_WINDOWS_CHANGED` within a tracked package while a session is active, request one snapshot (debounced 300 ms).
8. **RulesChanged** → end session (RULES_CHANGED), replace rules.

All numbers are rule-driven with the defaults above.

### 7.8 Android-side snapshot (`NodeInspector`, detector thread)
Given the retained source node for `containerKey`:
1. `node.refresh()`; if it returns false → `missing = true`.
2. Read bounds; read up to 6 direct children (`getChild(i)`), their bounds and class names; mark which child's visible height ≥ `fullItemRatio × container height`.
3. Compute `centerSignature` from the child containing the container center (§7.4) only if the resolved strategy is SIGNATURE or AUTO-undecided.
4. Hard time budget 30 ms; if exceeded, return what is gathered and emit `Anomaly("SNAPSHOT_SLOW")`.
5. Never hold node references after the snapshot except the container node for the session (released on session end).

### 7.9 Edge cases (must each be covered by a unit test with a synthetic or recorded trace)
| # | Case | Expected |
|---|---|---|
| E1 | Half swipe that snaps back | 0 counted |
| E2 | Fast fling over 2 reels | 2 counted |
| E3 | Swipe down 5, up 2, down 2 | 5 counted (+first reel) |
| E4 | Auto-advance with no touch | counted |
| E5 | Open reels, leave within 500 ms | 0 counted |
| E6 | Comments sheet opened and scrolled inside reels | 0 extra, session continues |
| E7 | Notification shade pulled during reels | session continues |
| E8 | Keyboard opened (comment typing) | session continues |
| E9 | Screen off mid-session, on again on same reel | new session; the current reel counts again after dwell (accepted) |
| E10 | Switch to DMs and back to reels (pager recreated) | old session ends, new session; new reels count |
| E11 | Instagram home feed scrolling | 0 counted |
| E12 | Stories (horizontal) | 0 counted |
| E13 | Split screen: reels in one half (≥ 75% of *its* window height) | counted |
| E14 | YouTube regular video in landscape | 0 counted |
| E15 | PiP YouTube while on home screen | 0 counted |
| E16 | Index jump of 40 (re-index) | 1 counted + Anomaly |
| E17 | `fromIndex == -1` everywhere | SIGNATURE (or DELTA) used |
| E18 | Rules updated mid-session | session ends cleanly, no double count |
| E19 | Midnight crossing mid-session | reels after midnight belong to new day (handled in repository, ADR-020) |
| E20 | Our own overlay events | ignored (own package filter) |
| E21 | Event source null | Scrolled signal still created without node; snapshot returns `missing` → treated as non-match unless indices alone suffice (INDEX with `fromIndex == toIndex`) |
| E22 | 300 scroll events in 1 s (event storm) | stable, ≤ 1 snapshot per settle |

### 7.10 Known limitations (document in README)
- Clone/"dual app" Instagram on some OEMs (separate user profile) may not deliver events.
- Work-profile copies of apps run in another user; not tracked.
- Accuracy depends on current rules; counts are "reels newly seen."

---

## 8. Data layer

### 8.1 Room entities
```kotlin
@Entity(tableName = "reel_event", indices = [Index("localDate"), Index("appId")])
data class ReelEventEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0,
  val epochMs: Long, val localDate: String, val appId: String, val surfaceId: String,
  val strategy: String, val sessionId: Long, val dedupKey: String /* unique */)

@Entity(tableName = "daily_summary", primaryKeys = ["localDate", "appId"])
data class DailySummaryEntity(val localDate: String, val appId: String, val reelCount: Int,
  val surfaceMs: Long, val packageForegroundMs: Long)

@Entity(tableName = "challenge_attempt")
data class ChallengeAttemptEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0,
  val epochMs: Long, val localDate: String, val type: String, val result: String /* COMPLETED|FAILED|ABANDONED */,
  val durationMs: Long, val reelsGranted: Int)

@Entity(tableName = "protection_gap")
data class ProtectionGapEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0,
  val fromEpochMs: Long, val toEpochMs: Long?)

@Entity(tableName = "anomaly")
data class AnomalyEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0,
  val epochMs: Long, val code: String, val detail: String, val appId: String?, val appVersionCode: Long?, val rulesVersion: String)
```
`dedupKey` = `"$sessionId:$ordinalInSession"`, unique index, insert with `OnConflictStrategy.IGNORE` → write-through is idempotent if a write is retried.

### 8.2 Time accounting
`surfaceMs` and `packageForegroundMs` are accumulated by the repository from `SurfaceEntered/Exited` and `Foreground` signals, flushed every 30 s and on exit. Needed for breakage detection (§14.2) and stats.

### 8.3 Repository API (`CountRepository`)
- `suspend fun recordReel(out: ReelCounted)` — computes `localDate` at `out.nowMs` with current zone, inserts event, upserts `daily_summary` in one transaction.
- `fun todayCount(): Flow<Int>`, `fun todayByApp(): Flow<Map<String, Int>>` — re-evaluate "today" on date change (combine with a `currentDateFlow()` that emits at next local midnight and on `ACTION_TIMEZONE_CHANGED`/`ACTION_TIME_CHANGED`).
- `fun history(days: Int): Flow<List<DaySummary>>`, `fun hourly(localDate): Flow<IntArray(24)>`.
- The service keeps an in-memory `todayCount` mirror for enforcement decisions (no DB read on the hot path); initialized from DB on connect.

### 8.4 Retention
`RetentionWorker` daily: delete `reel_event` older than 90 days (summaries keep totals). Delete `anomaly` older than 30 days.

### 8.5 Export/import
Settings → Data → "Export history" writes JSON (summaries + challenge attempts + settings) via Storage Access Framework (`ACTION_CREATE_DOCUMENT`). Import validates schema and merges by `localDate`/`appId` (max of counts).

### 8.6 Settings (DataStore keys, defaults, bounds)
| Key | Default | Bounds |
|---|---|---|
| `dailyLimit` | 60 | 5–1000 |
| `blockingEnabled` | true | — |
| `strictMode` | false | — |
| `reelsPerUnlock` | 5 | 1–20 (strict: forced 3) |
| `maxUnlocksPerDay` | 3 | 0–10 (strict: forced 1) |
| `trackedApps` | all P0+P1+P2 installed | set |
| `livePillEnabled` | true | — |
| `livePillPosition` | TOP_END | TOP_START, TOP_END |
| `warnAtPercent` | 80 | 50–95 |
| `remoteRulesEnabled` | true | — |
| `battlesEnabled` | false | — |
| `themeMode` | SYSTEM | SYSTEM/DARK/LIGHT |
| `onboardingComplete` | false | — |
Loosening changes go through the Commitment flow (§11.5).

---

## 9. Limits and enforcement

### 9.1 Allowance
`allowance(today) = dailyLimit + sum(reelsGranted where localDate == today)`. `remaining = allowance - todayCount`.

### 9.2 Decisions (EnforcementController, on detector outputs)
- On `ReelCounted`: if `blockingEnabled && todayCount ≥ allowance` → this reel is still recorded (it was seen), then show Block overlay immediately.
- On `SurfaceEntered`: if `blockingEnabled && todayCount ≥ allowance` → show Block overlay immediately (before dwell; the first reel is not counted while blocked).
- On `SurfaceExited` → remove Block overlay; abandon audio focus.
- Strict mode: on `Foreground` to a tracked package with `todayCount ≥ allowance` → overlay over the whole app (same overlay, different copy) until the package leaves.
- Warning notification once per day when `todayCount ≥ warnAtPercent% of dailyLimit`; second notification at 100%.

### 9.3 Block overlay
- `TYPE_ACCESSIBILITY_OVERLAY`, `MATCH_PARENT` × `MATCH_PARENT`, flags `FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_NO_LIMITS` (touchable, consumes all touches), `PixelFormat.TRANSLUCENT`, `layoutInDisplayCutoutMode = SHORT_EDGES`.
- Content: grey, fried brain (static frame at s = 1), "You've hit today's limit: N reels", remaining unlocks "2 unlocks left today", buttons "Leave reels" and "Earn 5 more reels" (hidden when no unlocks left; replaced by "Come back tomorrow").
- "Earn more" launches `ChallengeActivity` (`FLAG_ACTIVITY_NEW_TASK`) with a random challenge type not used in the last attempt; overlay is removed while the challenge is in front, and re-evaluated on return.
- Audio: `AudioFocusHelper.requestTransient()` on show; `abandon()` on hide.
- Re-entry debounce: after the overlay is removed, ignore re-show requests for 800 ms to avoid flicker loops during back navigation.

### 9.4 Compose in overlays
Create `OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner`:
- `LifecycleRegistry` moved to RESUMED on attach, DESTROYED on remove.
- `SavedStateRegistryController.performRestore(null)` before RESUMED.
- Set on the `ComposeView` with `setViewTreeLifecycleOwner`, `setViewTreeSavedStateRegistryOwner`, `setViewTreeViewModelStoreOwner` **before** `windowManager.addView`.
- Wrap content in `UnrotTheme`.

### 9.5 Live Pill
- Separate overlay, `WRAP_CONTENT`, gravity TOP|END (or START), y offset = status bar height + 8 dp, flags `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE | FLAG_LAYOUT_IN_SCREEN`.
- Shows: small brain glyph colored by severity + today's count (+ "/ limit" if blocking enabled). Count increment animates (number roll, 200 ms) and a single light haptic? **No haptic** (it's another app's context; keep silent).
- Visible only while a reel surface session is active and the Block overlay is not shown.
- Hidden during screenshots? Not possible to control; accepted.

### 9.6 Overlay safety checklist
- All overlay operations on main thread.
- `addView` wrapped in try/catch (`WindowManager.BadTokenException`, `IllegalStateException`) → fallback: HOME + `BlockActivity`.
- `removeView` only if `view.isAttachedToWindow`.
- Remove all overlays on: service unbind/destroy, screen off, package leaves foreground, rules change, user disables blocking.
- Configuration changes (rotation, dark mode) → overlays remain; Compose recomposes.

---

## 10. Challenges

Common contract: `ChallengeActivity` hosts one challenge; result recorded as `ChallengeAttemptEntity`; on COMPLETED grant `reelsPerUnlock` if `unlocksUsedToday < maxUnlocksPerDay`; Back press = ABANDONED (counts as failure for battles); keep screen on during challenges (`FLAG_KEEP_SCREEN_ON`); pausing the activity (home, lock) = ABANDONED.

### 10.1 Haptic Breathing (30 s)
- Pattern: 3 cycles × (inhale 4 s, exhale 6 s).
- UI: large ring; "Hold your thumb here." Holding starts the cycle; ring expands on inhale, contracts on exhale; label "Breathe in" / "Breathe out".
- Haptics (Vibrator from `VibratorManager.defaultVibrator` on 31+, `getSystemService(Vibrator)` below):
  - If `hasAmplitudeControl()`: inhale = waveform of 8 steps × 500 ms with amplitudes rising 30→200; exhale = 12 steps × 500 ms falling 200→30. Use `VibrationEffect.createWaveform(timings, amplitudes, -1)`; restart per phase.
  - Else: inhale = 40 ms pulses every 500 ms; exhale = 40 ms pulses every 1000 ms.
  - If device has no vibrator or system haptics are disabled (vibration produces nothing we can detect) → still run; the visual guide is primary. Show "Vibration is off on this phone; follow the circle."
- Release before 30 s → progress resets to 0, haptics stop, "Start again" prompt. Three resets in one attempt → FAILED.
- Accessibility: a "Can't hold?" option switches to tap-at-each-phase-change mode (tap within ±800 ms of each phase switch; 2 misses → reset).

### 10.2 Face-Down (15 s)
- Sensors: `TYPE_GRAVITY` if present, else `TYPE_ACCELEROMETER` with low-pass (α = 0.8). `TYPE_PROXIMITY` optional.
- Face-down when `gz < -8.0 m/s²`; still when linear acceleration magnitude RMS over last 500 ms < 0.6 m/s²; near when `proximity < sensor.maximumRange` (works for binary sensors).
- Condition = faceDown && still && (near || no proximity sensor). Must hold 15 000 ms continuously; any break resets the timer (no penalty).
- Feedback: one short vibration when the condition starts, a long one (400 ms) on completion. UI shows instruction and timer (user won't see it while face-down; that's fine).
- Max wall time 3 minutes → FAILED.
- Unregister sensors in `onPause`.

### 10.3 Steady Timer (60 s)
- Display a 60-second countdown; progress speed depends on shake:
  - `shake` = RMS of linear acceleration magnitude over 500 ms window.
  - `shake < 0.6` → rate 1.0; `0.6–2.0` → rate 0.25; `> 2.0` → rate 0 (paused, timer text shakes).
- Max wall time 5 minutes → FAILED.
- Copy: "Hold still. The calmer you are, the faster this goes."

### 10.4 Selection
Random among the three, never the same type twice in a row; user can tap "Different challenge" once per attempt (strict mode: not allowed).

---

## 11. UI

### 11.1 Design direction (apply the design skills in `.claude/skills` within these constraints)
**Concept:** the interface is a nervous system. When healthy it's vivid and electric; as severity rises the **entire UI theme** (not only the brain) drains toward flat concrete grey. This is the one bold idea; everything else stays quiet and disciplined.

Tokens (dark theme; light theme derived by the designer with the same hues):
| Token | Healthy | Fried (s = 1) |
|---|---|---|
| `bg` | `#12142E` (deep synapse indigo) | `#2B2B2B` |
| `surface` | `#1C1F45` | `#363636` |
| `accentA` | `#3D7BFF` (electric blue) | `#8C8C8C` |
| `accentB` | `#FF4FD8` (vivid pink) | `#7A7A7A` |
| `text` | `#F3F1FF` | `#D6D6D6` |
| `warn` | `#FFB020` | `#B3A27A` |
Every themed color = `lerp(healthy, fried, easeInQuad(s))` in OKLab (implement a small OKLab lerp helper; sRGB lerp looks muddy). Contrast must stay ≥ 4.5:1 for body text at every s (verify at s = 0, 0.5, 1).

Type: **Bricolage Grotesque** (display: the big count, screen titles) and **Atkinson Hyperlegible** (body, labels). Both SIL OFL; download TTFs from the `google/fonts` GitHub repo, bundle in `res/font/`, include `OFL.txt`. Big count uses tabular figures if available; otherwise fixed-width box to avoid jitter.

Copy rules: sentence case; plain verbs; buttons say what happens ("Leave reels", "Earn 5 more reels", "Turn on tracking"); errors say what happened and how to fix it; never shame the user.

Motion: one signature moment: when the count increments while Home is open, the brain twitches (impulse added to wobble for 300 ms) and the number rolls. No decorative entrance animations elsewhere. Respect reduced motion (animator duration scale 0 → no motion).

### 11.2 Navigation
Bottom bar with 3 destinations: **Today**, **History**, **Battles**. Settings via top-bar icon on Today. Other routes: Onboarding (graph), Challenge (activity), Commitment, Inspector (Settings → Advanced), About.

### 11.3 Screens
**Today**
- Brain (≈ 55% of screen height).
- Big count; below it "of 60 today" and a thin allowance bar; unlocks left.
- Per-app row: small chips "Instagram 34 · YouTube 12" (use actual chip components, not middot strings).
- Status banner area (top): service off (red, "Tracking is off. Turn it on" → settings), detection may be broken (amber, §14.2), rules updated (transient).
- Empty state (0 today): healthy brain, "No reels yet today."

**History**
- 7/30-day toggle; bar chart per day (draw with Compose Canvas; no chart library needed), today highlighted; average; best day; current streak of days under limit.
- Tap a day → hourly histogram + per-app split + challenges done/failed.

**Battles** (§16) — leaderboard of today: rank, name, count, "protection off" marker; Yesterday tab; Add friend; Your code; Leave battles.

**Settings** — Limits (daily limit, blocking, strict mode, unlock size/budget), Tracked apps (toggles with install status and "detection status: OK / may be broken / untested version"), Live pill, Notifications, Rules (version, last updated, update now, offline mode), Data (export, import, delete all), Battles, Advanced (Inspector, diagnostic log), About (version, rules version, source link, license, privacy, "the brain is not a medical measure").

**Onboarding** — §13.

**Commitment** — §11.5.

### 11.4 Severity mapping
`r = todayCount / dailyLimit`. `s = clamp(r / 1.5, 0, 1)` then `s = smoothstep(0, 1, s)`. Stage names for labels: `r < 0.25` Sharp, `< 0.5` Buzzing, `< 0.75` Foggy, `< 1.0` Melting, `< 1.5` Fried, `≥ 1.5` Cooked.

### 11.5 Commitment flow (loosening settings)
1. User changes a loosening setting → change is not applied; navigate to Commitment screen.
2. Screen: what will change ("Daily limit 60 → 100"), a 60-second countdown (cannot be skipped, Back cancels), then a "Confirm change" button.
3. Strict mode on → after countdown, a challenge must be completed (any type); then confirm.
4. Leaving the screen (home/lock) cancels.
Tightening changes apply instantly.

---

## 12. Brain renderer (`ui/brain/`)

### 12.1 Outline
Normalized coordinates, origin at centroid, x right, y down, extent ≈ [-1, 1]. Lateral (side) view. Define `outlineRadius(θ)` as a smooth periodic function sampled at 48 spokes, built from the base ellipse `rx = 1.0, ry = 0.72` with additive bumps (Gaussian in θ): frontal lobe bulge (θ ≈ 200°, +0.08), parietal dome (θ ≈ 270°, +0.06), occipital point (θ ≈ 340°, +0.05), temporal lobe underside (θ ≈ 150°, −0.10 notch then +0.04), cerebellum bump (θ ≈ 40°, +0.07 with narrower width), flattened brainstem notch (θ ≈ 75°, −0.12). Tune visually in a `@Preview`; the shape must be star-shaped from the centroid (every spoke hits the outline once).

### 12.2 Mesh
Rings `k = 0..12`, spokes `j = 0..47`. Vertex `(k, j)` = `(k/12) × outlineRadius(θj) × (cos θj, sin θj)`; center vertex shared at k = 0. Triangles: for each ring band and spoke pair, two triangles; innermost ring fans to center. Index buffer `ShortArray` built once. Arrays: `basePositions`, `positions`, `colors` (IntArray) preallocated.

### 12.3 Folds (sulci)
8–12 fold curves defined in parameter space `(u = radius fraction 0.2..0.95, θ)` as Catmull-Rom splines with 6–10 control points each, seeded deterministically (fixed seed → same brain every launch). Each frame, transform fold points through the same deformation function and stroke them (width 2.5 dp × (1 − 0.5s), color = darker accent).

### 12.4 Deformation (per vertex, per frame; `t` seconds, `p = (x, y)` base, `u` = radius fraction)
1. Breathing (healthy idle): `scale = 1 + 0.015 × sin(2π × 0.25 t) × (1 − s)`.
2. Wobble: `x += sin(2π × 0.6 t + 4y) × 0.03 × s`, `y += cos(2π × 0.45 t + 3x) × 0.02 × s`.
3. Droop: `y += 0.20 × s^1.5 × ((y + 1) / 2)^2`; also `x *= 1 + 0.06 × s^1.5 × ((y + 1)/2)` (sags and spreads at the bottom).
4. Crack (s > 0.8): crack line through centroid at angle −70°; vertices on the far side translate by `0.08 × ((s − 0.8) / 0.2)` along the crack normal and rotate 4° about the crack midpoint; at s > 0.95 the separation jitters ±0.005.
5. Twitch impulse (on increment): add `0.04 × e^(−10τ) × sin(40τ)` to x-wobble, τ = seconds since increment.
Color per vertex: interpolate along `u` between `accentA` (center) and `accentB` (edge), then OKLab-lerp toward `friedGrey` by `s`; add per-vertex noise darkening `±0.06 × s` (blotchy decay).

### 12.5 Post-effect (API 33+)
AGSL as `RenderEffect.createRuntimeShaderEffect(shader, "content")` on the brain composable's `graphicsLayer`:
```glsl
uniform shader content;
uniform float severity;
half4 main(float2 coord) {
    float px = mix(1.0, 14.0, smoothstep(0.65, 1.0, severity));
    float2 c = (floor(coord / px) + 0.5) * px;
    half4 col = content.eval(c);
    half lum = dot(col.rgb, half3(0.2126, 0.7152, 0.0722));
    half sat = half(1.0 - smoothstep(0.15, 1.0, severity));
    return half4(mix(half3(lum), col.rgb, sat), col.a);
}
```
Recreate the `RenderEffect` only when severity changes by ≥ 0.01. API 29–32: `ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1 - s) })`, no pixelation.

### 12.6 Frame loop and performance
- `LaunchedEffect` with `withFrameNanos` loop; only while composable is in composition and `LocalLifecycleOwner` is ≥ RESUMED (`repeatOnLifecycle`).
- Budget: < 2 ms CPU per frame on a mid-range phone. No allocations in the loop (verify with Android Studio allocation tracking or by reasoning; avoid lambdas/boxing in the loop).
- Reduced motion: render one static frame at current s, update only when s changes.
- Static render API for the Block overlay and Live pill glyph (`renderBrainBitmap(sizePx, s)` cached per 0.05 s-step).

---

## 13. Onboarding and permissions

### 13.1 Steps
1. Welcome — what the app does in one sentence; brain preview animating 0 → 1 → 0.
2. How it works — counts reels in Instagram/YouTube/TikTok/Facebook/Snapchat; blocks reels at your limit; everything stays on your phone.
3. **Prominent disclosure** (§13.3) — "Agree and continue" / "Not now".
4. Enable service — button opens accessibility settings; return detection (§13.4); restricted-settings help (§13.5).
5. Notifications (33+) — optional.
6. Keep it running — OEM-specific battery guidance (§13.6); skippable.
7. Set your daily limit — slider with presets 30 / 60 / 100 and "about N minutes" estimate (25 s per reel).
8. Done → Today.
"Not now" at step 3 leads to Today with a persistent "Tracking is off" banner; onboarding can resume from Settings.

### 13.2 Detecting enabled state
`AccessibilityManager.getEnabledAccessibilityServiceList(FEEDBACK_ALL_MASK)` contains our component, **and** the service's heartbeat (`lastAliveAt`) is < 2 minutes old or `serviceConnectedAt` > `serviceDisconnectedAt`. Enabled-but-not-alive = "Enabled but not running" state (OEM killed it) → show restart guidance (toggle off/on).

### 13.3 Disclosure text (must appear verbatim in meaning; final copy can be polished)
> **Unrot uses Android's Accessibility service.**
> It's needed to see when you scroll reels and shorts in Instagram, YouTube, TikTok, Facebook and Snapchat, to show your live count, and to cover reels when you reach your limit.
> To tell reels apart, Unrot reads screen structure (like the size of the scrolling area) and, for some apps, briefly reads on-screen text in memory. It never saves, logs, or sends any screen content. Only your reel counts and times are stored, on your phone.
> If you join Friend Battles, only your daily count and failed challenges are shared with friends you add.
> You can turn this off anytime in Android settings.
Buttons: "Agree and continue" (primary), "Not now". Link: "Read the privacy policy".

### 13.4 Opening settings
`Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)`; best-effort highlight via extras `":settings:fragment_args_key"` = flattened component name and `":settings:show_fragment_args"` bundle with the same key. On return (`onResume`), re-check §13.2.

### 13.5 Restricted settings (Android 13+, sideloaded installs)
If the user returns without enabling the service at least once on API 33+, show: "If the switch is greyed out: open App info → ⋮ menu → Allow restricted settings, then try again." Button opens `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` for our package. Provide an illustrated 3-step card. (Play Store installs don't need this.)

### 13.6 OEM battery guidance
Map `Build.MANUFACTURER` (lowercased) → short instructions + link to the matching dontkillmyapp.com page (open in browser): xiaomi/redmi/poco (Autostart on, Battery saver: No restrictions), oppo/realme/oneplus (Allow background activity, lock app in recents), vivo/iqoo (Background power consumption: allow), samsung (Never sleeping apps), huawei/honor (App launch: manage manually), others: generic text. Button "Open battery settings" → `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` (no `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission).

---

## 14. Health, breakage, diagnostics

### 14.1 Service health
`ServiceHealthMonitor` (app side): states `RUNNING`, `ENABLED_NOT_RUNNING`, `DISABLED`. On transitions from RUNNING to DISABLED/ENABLED_NOT_RUNNING, open a `protection_gap` row; close it when RUNNING resumes (the service writes on connect). Today screen banner reflects state.

### 14.2 Breakage detection (per tracked app, per day)
Flag `MAY_BE_BROKEN` when: `packageForegroundMs ≥ 10 min` AND `reelCount == 0` AND the installed versionCode is not within the rule's `testedVersionCodes` range. Flag `UNTESTED_VERSION` (informational) when installed versionCode > max tested and counts look normal. Banner: "Instagram 4xx may not be counted correctly. Update rules or report it." Actions: "Update rules now", "Report" (§14.3).

### 14.3 Report flow
Opens a prefilled GitHub "new issue" URL in the browser: title `Detection: <app> <versionName>`, body with app versionName/Code, rules version, Android version, device manufacturer/model, anomaly codes from the last 24 h (structural only), and a reminder to attach an Inspector trace. No screen content. User reviews and submits themselves.

### 14.4 Diagnostic log
Ring buffer (500 entries) in app files dir: timestamp, level, tag, message. Release builds log only structural info. Settings → Advanced → Diagnostic log: view, copy, share (user-initiated).

---

## 15. Inspector (the rule-maintenance tool)

### 15.1 Access
Settings → Advanced → Inspector (all builds; it's a contributor feature). Shows a warning that recording captures screen structure (no text).

### 15.2 Recording
- Start → choose label (e.g., "Instagram reels 10 swipes") and expected count the user will perform.
- While recording, the service writes every event from tracked packages (scroll, window, content-changed sampled to max 5/s) + settle snapshots, as JSON Lines to app files dir. Live pill shows a red "REC" dot.
- Stop → file listed; actions: Share (system share sheet), Delete.
- Auto-stop after 5 minutes or 20 000 lines.

### 15.3 Trace line format
```json
{"t":1730460000123,"e":"SCROLLED","pkg":"com.instagram.android","cls":"androidx.recyclerview.widget.RecyclerView","vid":"com.instagram.android:id/…","b":[0,0,1080,2340],"win":[0,0,1080,2400],"from":4,"to":5,"cnt":30,"dy":812,"key":"…"}
{"t":…,"e":"SNAPSHOT","key":"…","missing":false,"children":[{"b":[0,-1500,1080,840],"cls":"…"},{"b":[0,840,1080,3180],"cls":"…"}],"sig":"9f2c…"}
{"t":…,"e":"FOREGROUND","pkg":"com.instagram.android","win":[0,0,1080,2400]}
{"t":…,"e":"SCREEN_OFF"}
```
`sig` is the hash only. Sidecar `name.expected.json`: `{"expectedReels": 10, "app": "instagram", "notes": "…", "appVersionCode": 123, "device": "…"}`.

### 15.4 Replay test
`TraceReplayTest` parametrized over all trace files: parse lines → Signals (SNAPSHOT lines answer the preceding `RequestSettleSnapshot`) → run Detector with bundled rules → assert counted reels == expectedReels (tolerance 0 for synthetic traces, ±1 allowed only when the sidecar sets `"tolerance": 1` with a reason).

### 15.5 Synthetic traces
Phase 2 creates synthetic traces for every edge case E1–E22 before any real trace exists.

---

## 16. Friend Battles

### 16.1 Setup (user does this; agent writes the guide in README)
1. Create a Supabase project. Enable Anonymous sign-ins (Auth settings).
2. Run `supabase/migrations/0001_init.sql` in the SQL editor.
3. Put `SUPABASE_URL` and `SUPABASE_ANON_KEY` in `local.properties` (local) and GitHub Actions secrets (CI). Gradle reads them into `BuildConfig`; absent → battles disabled in that build.

### 16.2 SQL (`0001_init.sql`)
```sql
create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null check (char_length(display_name) between 1 and 24),
  friend_code text not null unique,
  created_at timestamptz not null default now()
);
create table public.friendships (
  requester uuid not null references public.profiles(id) on delete cascade,
  addressee uuid not null references public.profiles(id) on delete cascade,
  status text not null check (status in ('pending','accepted')),
  created_at timestamptz not null default now(),
  primary key (requester, addressee),
  check (requester <> addressee)
);
create table public.daily_scores (
  user_id uuid not null references public.profiles(id) on delete cascade,
  day date not null,
  reel_count int not null check (reel_count between 0 and 100000),
  challenges_failed int not null default 0 check (challenges_failed between 0 and 1000),
  protection_off boolean not null default false,
  updated_at timestamptz not null default now(),
  primary key (user_id, day)
);

create or replace function public.are_friends(a uuid, b uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from friendships f where f.status = 'accepted'
    and ((f.requester = a and f.addressee = b) or (f.requester = b and f.addressee = a)));
$$;

alter table public.profiles enable row level security;
alter table public.friendships enable row level security;
alter table public.daily_scores enable row level security;

create policy profiles_read on public.profiles for select using (
  id = auth.uid() or exists (select 1 from friendships f
    where (f.requester = auth.uid() and f.addressee = profiles.id)
       or (f.addressee = auth.uid() and f.requester = profiles.id)));
create policy profiles_update_self on public.profiles for update using (id = auth.uid()) with check (id = auth.uid());

create policy friendships_read on public.friendships for select using (auth.uid() in (requester, addressee));
create policy friendships_delete on public.friendships for delete using (auth.uid() in (requester, addressee));

create policy scores_read on public.daily_scores for select using (user_id = auth.uid() or public.are_friends(auth.uid(), user_id));
create policy scores_insert_self on public.daily_scores for insert with check (
  user_id = auth.uid() and day between (current_date - 2) and (current_date + 1));
create policy scores_update_self on public.daily_scores for update using (user_id = auth.uid()) with check (
  user_id = auth.uid() and day between (current_date - 2) and (current_date + 1));

create or replace function public.create_profile(name text) returns text
language plpgsql security definer set search_path = public as $$
declare code text; alphabet text := 'ABCDEFGHJKMNPQRSTUVWXYZ23456789'; i int;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select friend_code into code from profiles where id = auth.uid();
  if code is not null then update profiles set display_name = name where id = auth.uid(); return code; end if;
  loop
    code := '';
    for i in 1..8 loop code := code || substr(alphabet, 1 + floor(random() * length(alphabet))::int, 1); end loop;
    begin
      insert into profiles(id, display_name, friend_code) values (auth.uid(), name, code);
      return code;
    exception when unique_violation then -- retry
    end;
  end loop;
end $$;

create or replace function public.send_friend_request(code text) returns text
language plpgsql security definer set search_path = public as $$
declare target uuid; pending_count int;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  select id into target from profiles where friend_code = upper(trim(code));
  if target is null then return 'NOT_FOUND'; end if;
  if target = auth.uid() then return 'SELF'; end if;
  if public.are_friends(auth.uid(), target) then return 'ALREADY_FRIENDS'; end if;
  if exists (select 1 from friendships where requester = target and addressee = auth.uid()) then
    update friendships set status = 'accepted' where requester = target and addressee = auth.uid();
    return 'ACCEPTED';
  end if;
  select count(*) into pending_count from friendships where requester = auth.uid() and status = 'pending';
  if pending_count >= 50 then return 'TOO_MANY_PENDING'; end if;
  insert into friendships(requester, addressee, status) values (auth.uid(), target, 'pending') on conflict do nothing;
  return 'SENT';
end $$;

create or replace function public.respond_friend_request(from_user uuid, accept boolean) returns void
language plpgsql security definer set search_path = public as $$
begin
  if accept then
    update friendships set status = 'accepted' where requester = from_user and addressee = auth.uid() and status = 'pending';
  else
    delete from friendships where requester = from_user and addressee = auth.uid();
  end if;
end $$;

revoke all on function public.create_profile(text), public.send_friend_request(text), public.respond_friend_request(uuid, boolean) from public, anon;
grant execute on function public.create_profile(text), public.send_friend_request(text), public.respond_friend_request(uuid, boolean) to authenticated;
```
Note: anonymous users have the `authenticated` role in Supabase. The agent must verify this against current Supabase docs and adjust grants if it changed.

### 16.3 Client
- Join: enter display name (1–24 chars, trimmed, no newline) → anonymous sign-in → `create_profile` → store code. Session persisted by supabase-kt.
- Sync (`BattlesSyncWorker`, periodic 60 min, network constraint, plus on app open and on Battles tab open): upsert rows for today and yesterday (`reel_count`, `challenges_failed` = FAILED+ABANDONED attempts, `protection_off` = any protection gap overlapping that local day).
- Leaderboard query: `daily_scores` where `day = <local today>` for self + friends, joined client-side with `profiles`. Friends in other time zones: each row is their own local day; label "Today (their time)". Missing row = "No data yet".
- Ranking: lower `reel_count` first; tie → fewer `challenges_failed`; `protection_off` rows sorted last with a marker.
- Leave battles: delete own profile (cascade) via `delete from profiles where id = auth.uid()` (add a delete policy for self) and sign out.
- Errors: offline → show cached last leaderboard with "Updated N min ago". Paused/unreachable backend → "Battles are unavailable right now."
- UI states that anonymous accounts are lost on uninstall.

---

## 17. Notifications
Channel `limits` (default importance): "80% of today's limit" and "Limit reached". Channel `service` (low): "Tracking stopped" when health monitor sees DISABLED while app process is alive (rare). Never notify more than once per type per day. Respect `POST_NOTIFICATIONS` denial silently.

---

## 18. Testing

### 18.1 Unit (CI, every push)
- `:detection`: every transition in §7.7, every edge case E1–E22, strategies, matcher, rules validation, trace replay.
- `app` JVM tests: AllowanceCalculator, severity mapping, OKLab lerp, date boundary logic (fixed clock + zone), commitment classification (loosening vs tightening), rules JSON validator with malformed inputs (bad regex, schemaVersion too high, negative ratios, empty file).

### 18.2 Manual device protocol (`docs/TESTING_ON_DEVICE.md`)
For each app: open surface, wait 2 s (expect 1), swipe 10 (expect 11), swipe back 3 and forward 3 (expect 11), open comments and scroll (expect 11), home feed scroll 20 items (expect 11), leave and return (expect +1), lock/unlock (expect +1). Plus: limit to 5 → block overlay appears on 6th, Back leaves, audio pauses; challenge grants +5; strict mode blocks whole app; reboot phone → service resumes; airplane mode → no crashes.

### 18.3 Rules regression
Before merging a rules change: all traces replay green.

---

## 19. Build, CI, release

### 19.1 Gradle
- Modules: `:app`, `:detection`. Version catalog. Configuration cache on. `kotlinOptions.allWarningsAsErrors = false` (avoid toolchain churn breaking builds).
- `copyRules` task: copies `rules/detection-rules.json` → `app/src/main/assets/` before `preBuild`; generated asset path in `.gitignore`.
- `BuildConfig` fields: `SUPABASE_URL`, `SUPABASE_ANON_KEY` (empty string default), `RULES_URL`.

### 19.2 CI (`.github/workflows/ci.yml`)
On push and PR: checkout → setup-java (Temurin 17) → Gradle setup action with caching → `./gradlew spotlessCheck lint :detection:test testDebugUnitTest assembleDebug` → upload `app-debug.apk` as artifact (retention 14 days). Secrets for Supabase are optional.

### 19.3 Cloud environment
Try to install Android command-line tools and SDK packages (`platform-tools`, `platforms;android-<compileSdk>`, `build-tools;<version>`) locally. If downloads from Google's SDK hosts are blocked, tell the user which domains need allowing in their environment's network settings, and meanwhile rely on CI for builds (ADR-018). `:detection` tests are pure JVM and should run locally regardless.

### 19.4 Release
- Release signing config reads keystore path/passwords from env vars; CI workflow `release.yml` on tag `v*` builds `assembleRelease`, signs, attaches APK to a GitHub Release.
- R8 enabled with rules for kotlinx.serialization and Room; keep the accessibility service class.
- Reproducibility: no build timestamps in `BuildConfig`, stable `versionCode` from tag, `vcsInfo` disabled if it breaks reproducibility.

### 19.5 Store and F-Droid metadata (Phase 9)
`fastlane/metadata/android/en-US/`: `title.txt`, `short_description.txt`, `full_description.txt` (includes the accessibility explanation), `images/` (screenshots taken by the user). Play Console: accessibility declaration text and a demo-video script are written by the agent into `docs/PLAY_SUBMISSION.md`.

### 19.6 Before the repo goes public
- Verify each skill folder in `.claude/skills/` has its upstream LICENSE; add attribution in `THIRD_PARTY_NOTICES.md`. If a skill's license forbids redistribution or is missing, tell the user before publishing.
- `.gitignore`: `local.properties`, `*.jks`, `*.keystore`, generated assets, build dirs.
- Grep the history for keys/passwords.

---

## 20. Phased implementation plan

Each phase: implement → tests → commit (conventional commits) → push → CI green → write the user's device test script → **wait at device gates**.

**Phase 0 — Scaffold**
Repo layout (§5), LICENSE (GPL-3.0 text), README skeleton, PRIVACY.md draft, Gradle modules, version catalog, theme stub, empty Today screen, CI workflow, `rules/detection-rules.json` with structural defaults (§7.6) for all five apps.
✅ CI green, debug APK artifact installs and opens.

**Phase 1 — Service + Inspector**
Manifest/service config (§6), service lifecycle (§7.1), event intake (§7.2), foreground tracker (§7.3), SignalMapper, Inspector recording + share (§15), minimal disclosure + enable flow (§13.3–13.5), diagnostic log.
🔶 DEVICE GATE: user enables the service (including restricted-settings unlock) and records traces: Instagram reels 10 swipes; Instagram home feed; Instagram reels with comments open; YouTube Shorts 10; YouTube home; TikTok 10 (if installed); Facebook reels 10 (if installed). User commits traces + sidecars (or sends them to the agent).

**Phase 2 — Detection core**
`:detection` module complete (§7.4–7.9), synthetic traces for E1–E22, real traces from Phase 1 added, settle snapshots on Android side (§7.8), Room schema + CountRepository (§8), Today screen showing live count per app, Live pill (§9.5).
✅ All replays green. 🔶 DEVICE GATE: manual protocol §18.2 counting section; agent refines rules from new traces until counts match.

**Phase 3 — Rules updates + health**
RulesRepository, validator, fetch worker, last-known-good (ADR-007); service health + protection gaps + breakage detection + report flow (§14).
✅ Tests for validator. 🔶 DEVICE GATE: disable/enable service → banners correct; push a rules change to `main` → app picks it up after "Update rules now".

**Phase 4 — Limits and blocking**
Settings for limits, AllowanceCalculator, EnforcementController, Block overlay with Compose lifecycle (§9.3–9.4, 9.6), audio focus, BlockActivity fallback, strict mode, notifications (§17).
🔶 DEVICE GATE: limit 5 scenario, Back behavior, audio pause, strict mode, rotation, lock screen.

**Phase 5 — Challenges + commitment**
Three challenges (§10), unlock grants, ChallengeActivity, Commitment flow (§11.5).
🔶 DEVICE GATE: each challenge completes and fails as specified; haptics feel right (ask user's subjective feedback; adjust amplitudes).

**Phase 6 — Brain + design system**
Read design/motion skills. Theme tokens with severity-driven OKLab lerp (§11.1), fonts, brain renderer (§12), Today screen final layout, Block overlay brain, pill glyph.
🔶 DEVICE GATE: verify `drawVertices` renders (ADR-012), check 60 fps feel, check s = 0/0.5/1 via a debug severity slider in Advanced (debug builds only).

**Phase 7 — Full onboarding + History + Settings complete**
Onboarding (§13), OEM guidance, History screens, all settings, export/import, About.
🔶 DEVICE GATE: fresh install walkthrough.

**Phase 8 — Friend Battles**
SQL migration, README setup guide, client (§16).
🔶 DEVICE GATE: user creates Supabase project; two accounts (second phone or an emulator) add each other and see scores.

**Phase 9 — Release prep**
R8, release signing via env, release workflow, reproducibility check (build twice, compare hashes), store metadata, `PLAY_SUBMISSION.md`, `CONTRIBUTING.md` (how to record and submit traces), THIRD_PARTY_NOTICES, final privacy policy.
✅ Signed release APK attached to a draft GitHub Release.

---

## 21. Failure modes master list (what can go wrong, what the app does)

| Failure | Detection | Response |
|---|---|---|
| Service disabled by user | Health monitor | Red banner, protection gap recorded |
| Service killed by OEM | Enabled but heartbeat stale | Amber banner + OEM guidance |
| Service crash loop | Android disables service | Same as disabled; diagnostic log shows exceptions; every entry point has try/catch so this should not happen |
| Target app update breaks detection | Breakage detector | Banner, update rules, report |
| Remote rules invalid | Validator | Keep last-known-good, log |
| Remote rules unreachable | Fetch error | Silent retry with WorkManager backoff |
| Overlay can't be added | Exception | HOME + BlockActivity |
| User force-stops our app | Service unbound until re-enabled on some OEMs | Banner on next open |
| Clock/time zone changes | Broadcasts | Re-derive "today"; past events unchanged |
| DB write fails | Exception | Retry once; in-memory count stays correct; log |
| Supabase paused/down | HTTP error | Cached leaderboard + message |
| Low-end device jank | — | Brain falls back to static frame if frame time > 32 ms for 60 consecutive frames |
| Device without vibrator/proximity/gravity | Sensor/vibrator checks | Fallbacks in §10 |
| TalkBack user | — | Our UI fully labeled; detection still works because the same events flow |
| Phone in another language | — | Structural rules don't depend on text |

---

## 22. Out of scope for v1 (do not build unless the user asks)
iOS; web-browser reels; user-added custom packages; home-screen widget; account linking for battles; cloud backup; tablet-specific layouts beyond "doesn't break"; Wear OS; per-app separate limits (keep the data model ready: settings keyed by appId later).

---

## 23. Open questions for the user (ask at the start of the relevant phase, don't block earlier phases)
1. Final app name and application ID (Phase 0 uses `app.unrot`).
2. GitHub owner/repo for `RULES_URL` (Phase 3).
3. Default daily limit: 60 OK? (Phase 4)
4. Should TikTok be P0 for your target audience? (Phase 2)
5. Display-name rules for battles (allow emoji?) (Phase 8).
