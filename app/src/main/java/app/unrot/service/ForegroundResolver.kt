package app.unrot.service

import app.unrot.detection.Box
import app.unrot.detection.trace.TraceEvent

/** Window types the tracker cares about (mapped from `AccessibilityWindowInfo.type`). */
enum class WindowKind {
    APPLICATION,
    INPUT_METHOD,
    SYSTEM,
    ACCESSIBILITY_OVERLAY,
    SPLIT_SCREEN_DIVIDER,
    OTHER,
}

data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun toBox(): Box = Box(left, top, right, bottom)

    override fun toString(): String = "[$left,$top][$right,$bottom]"
}

/** Plain copy of one `AccessibilityWindowInfo`, so the selection logic runs in JVM unit tests. */
data class WindowSnapshot(
    val kind: WindowKind,
    val packageName: String?,
    val bounds: Bounds,
    val isFocused: Boolean,
    val isActive: Boolean,
    val isPictureInPicture: Boolean = false,
)

/** The app in front (SPEC §7.3). [visiblePackages] lists every visible app, foreground first. */
data class ForegroundState(
    val packageName: String,
    val appWindowBounds: Bounds,
    val visiblePackages: List<String>,
    val atMs: Long,
) {
    /**
     * As an Inspector trace line. Apps that are not tracked are written as null, so a shared
     * trace never reveals which other apps were used.
     */
    fun toTraceEvent(trackedPackages: Set<String>): TraceEvent.Foreground {
        val pkg = packageName.takeIf { it in trackedPackages }
        return TraceEvent.Foreground(atMs, pkg, appWindowBounds.toBox())
    }
}

object ForegroundResolver {
    /**
     * Packages whose windows never count as the app in front, even when Android reports them as
     * application windows: system and phone-maker panels that open over apps. While one is open,
     * the previous app stays in front (SPEC §7.3 step 3).
     * - `com.android.systemui`: notification shade, quick settings, volume and similar panels.
     * - `com.coloros.assistantscreen`: the OnePlus/OPPO/realme Shelf, a panel opened by swiping
     *   down on the home screen (seen on a OnePlus 11R, OxygenOS 16, 2026-11-01).
     * - `com.oplus.pscanvas`: the OxygenOS/ColorOS split-screen container, reported as an
     *   application window next to the two apps (seen on the same phone).
     */
    val NEVER_FOREGROUND: Set<String> = setOf(
        "com.android.systemui",
        "com.coloros.assistantscreen",
        "com.oplus.pscanvas",
    )

    /**
     * Picks the foreground app from the current windows (SPEC §7.3 steps 2–3): the focused
     * application window, else the active one. Keyboard, system UI (notification shade, volume),
     * accessibility overlays, the split-screen divider and [NEVER_FOREGROUND] panels are ignored.
     * A picture-in-picture window never counts. Returns null when no application window qualifies.
     */
    fun resolve(windows: List<WindowSnapshot>, nowMs: Long): ForegroundState? {
        val apps = windows.filter { it.isAppCandidate() }
        val chosen = apps.firstOrNull { it.isFocused }
            ?: apps.firstOrNull { it.isActive }
            ?: return null
        val chosenPackage = chosen.packageName ?: return null
        val visible = (listOf(chosenPackage) + apps.mapNotNull { it.packageName }).distinct()
        return ForegroundState(chosenPackage, chosen.bounds, visible, nowMs)
    }

    /** SPEC §7.3 step 4: report only when the foreground package or its window bounds change. */
    fun isChange(previous: ForegroundState?, next: ForegroundState): Boolean {
        if (previous == null) return true
        val samePackage = previous.packageName == next.packageName
        return !samePackage || previous.appWindowBounds != next.appWindowBounds
    }

    private fun WindowSnapshot.isAppCandidate(): Boolean {
        val pkg = packageName ?: return false
        return kind == WindowKind.APPLICATION && pkg !in NEVER_FOREGROUND && !isPictureInPicture
    }
}
