package app.unrot.service

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
)

object ForegroundResolver {
    /**
     * Picks the foreground app from the current windows (SPEC §7.3 steps 2–3): the focused
     * application window, else the active one. Keyboard, system UI (notification shade, volume),
     * accessibility overlays and the split-screen divider are ignored. A picture-in-picture window
     * never counts. Returns null when no application window qualifies.
     */
    fun resolve(windows: List<WindowSnapshot>, nowMs: Long): ForegroundState? {
        val apps = windows.filter {
            it.kind == WindowKind.APPLICATION && it.packageName != null && !it.isPictureInPicture
        }
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
}
