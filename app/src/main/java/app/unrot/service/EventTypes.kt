package app.unrot.service

import android.view.accessibility.AccessibilityEvent

/** Runtime subscription to `TYPE_WINDOW_CONTENT_CHANGED` (ADR-004, SPEC §7.3 step 5). */
object EventTypes {
    const val CONTENT_CHANGED = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED

    /** Returns [eventTypes] with content-changed events added or removed; other bits untouched. */
    fun withContentChanged(eventTypes: Int, enabled: Boolean): Int =
        if (enabled) eventTypes or CONTENT_CHANGED else eventTypes and CONTENT_CHANGED.inv()
}
