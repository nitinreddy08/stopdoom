package app.unrot.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import app.unrot.detection.Box
import app.unrot.detection.Signal

/**
 * Turns accessibility events into plain [Signal]s right away (SPEC §7.2, ADR-005): the event is
 * recycled by Android after the callback and must never be kept. Reads structure only, no text.
 */
object SignalMapper {
    /** Android reports an unknown scroll distance as -1. */
    const val UNDEFINED_DELTA = -1

    /**
     * [source] is `event.source`, read once by the caller. The caller keeps it alongside the
     * signal (never inside it) for the settle snapshot, and recycles it afterwards.
     */
    fun scrolled(
        event: AccessibilityEvent,
        source: AccessibilityNodeInfo?,
        nowMs: Long,
    ): Signal.Scrolled {
        val rect = Rect()
        source?.getBoundsInScreen(rect)
        val bounds = Box(rect.left, rect.top, rect.right, rect.bottom)
        val className = event.className?.toString()
        val viewId = source?.viewIdResourceName
        return Signal.Scrolled(
            pkg = event.packageName?.toString().orEmpty(),
            className = className,
            viewId = viewId,
            bounds = bounds,
            fromIndex = event.fromIndex,
            toIndex = event.toIndex,
            itemCount = event.itemCount,
            deltaY = deltaY(event.scrollDeltaY),
            containerKey = containerKey(event.windowId, viewId, className, bounds),
            nowMs = nowMs,
        )
    }

    /**
     * Identifies one scrolling container without object identity (SPEC §7.2):
     * `windowId:viewId:className:bounds`, bounds as `Rect.flattenToString()` writes them.
     */
    fun containerKey(windowId: Int, viewId: String?, className: String?, bounds: Box): String =
        "$windowId:$viewId:$className:${bounds.l} ${bounds.t} ${bounds.r} ${bounds.b}"

    fun deltaY(scrollDeltaY: Int): Int? = scrollDeltaY.takeIf { it != UNDEFINED_DELTA }
}
