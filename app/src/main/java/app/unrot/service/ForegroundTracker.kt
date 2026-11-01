package app.unrot.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityWindowInfo
import app.unrot.health.DiagnosticLog

/**
 * Works out which app is in front from the window list (SPEC §7.3) and subscribes to
 * content-changed events only while a tracked app is in front (ADR-004). [onChange] hears every
 * foreground change (the Inspector records them). Runs on the service's single detector thread
 * (ADR-021).
 */
class ForegroundTracker(
    private val service: AccessibilityService,
    private val trackedPackages: Set<String>,
    private val status: ServiceStatus,
    private val log: DiagnosticLog,
    private val clock: () -> Long,
    private val onChange: (ForegroundState) -> Unit = {},
) {
    private var current: ForegroundState? = null
    private var contentEventsEnabled = false
    private var lastFocus: String? = null

    /** Re-reads the window list. Called after window events (conflated). */
    fun refresh() {
        val windows = service.windows
        val snapshots = try {
            windows.map { it.toSnapshot() }
        } finally {
            windows.forEach { it.recycleBelowApi33() }
        }
        val focused = snapshots.firstOrNull { it.isFocused }
        val focus = focused?.let { "${it.kind} ${it.packageName ?: "?"}" } ?: "none"
        // A focused window that is not the current app (shade, Shelf, keyboard...). "No window
        // focused" is skipped: it happens between every two windows and only adds noise.
        val ignoredFocus = focused != null && focused.packageName != current?.packageName
        val next = ForegroundResolver.resolve(snapshots, clock())
        if (next != null && ForegroundResolver.isChange(current, next)) {
            current = next
            status.foreground.value = next
            val message = "foreground ${next.packageName} ${next.appWindowBounds}"
            log.i(TAG, "$message visible=${next.visiblePackages}")
            setContentEvents(next.packageName in trackedPackages)
            onChange(next)
        } else if (ignoredFocus && focus != lastFocus) {
            log.d(TAG, "kept ${current?.packageName ?: "none"}; focus on $focus")
        }
        lastFocus = focus
    }

    private fun setContentEvents(enabled: Boolean) {
        if (enabled == contentEventsEnabled) return
        val info = service.serviceInfo ?: return
        info.eventTypes = EventTypes.withContentChanged(info.eventTypes, enabled)
        service.serviceInfo = info
        contentEventsEnabled = enabled
        status.contentEventsEnabled.value = enabled
        log.i(TAG, if (enabled) "content-changed events on" else "content-changed events off")
    }

    private fun AccessibilityWindowInfo.toSnapshot(): WindowSnapshot {
        val rect = Rect()
        getBoundsInScreen(rect)
        val kind = kindOf(type)
        // App windows always; other windows only when focused, so the log can name them.
        val readPackage = kind == WindowKind.APPLICATION || isFocused
        return WindowSnapshot(
            kind = kind,
            packageName = if (readPackage) rootPackageName() else null,
            bounds = Bounds(rect.left, rect.top, rect.right, rect.bottom),
            isFocused = isFocused,
            isActive = isActive,
            isPictureInPicture = isInPictureInPictureMode,
        )
    }

    /** Package of the app that owns this window. Reads only the root node's package name. */
    private fun AccessibilityWindowInfo.rootPackageName(): String? {
        val node = root ?: return null
        return try {
            node.packageName?.toString()
        } finally {
            node.recycleBelowApi33()
        }
    }

    private companion object {
        const val TAG = "Foreground"

        fun kindOf(type: Int): WindowKind = when (type) {
            AccessibilityWindowInfo.TYPE_APPLICATION -> WindowKind.APPLICATION
            AccessibilityWindowInfo.TYPE_INPUT_METHOD -> WindowKind.INPUT_METHOD
            AccessibilityWindowInfo.TYPE_SYSTEM -> WindowKind.SYSTEM
            AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> WindowKind.ACCESSIBILITY_OVERLAY
            AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER -> WindowKind.SPLIT_SCREEN_DIVIDER
            else -> WindowKind.OTHER
        }
    }
}
