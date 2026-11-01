package app.unrot.service

import android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SCROLLED
import android.view.accessibility.AccessibilityEvent.TYPE_WINDOWS_CHANGED
import android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
import android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EventTypesTest {
    // The static config from res/xml/accessibility_service_config.xml (SPEC §6.4).
    private val base = TYPE_WINDOW_STATE_CHANGED or TYPE_WINDOWS_CHANGED or TYPE_VIEW_SCROLLED

    @Test
    fun `enabling adds content-changed and keeps the other types`() {
        val enabled = EventTypes.withContentChanged(base, enabled = true)
        assertEquals(base or TYPE_WINDOW_CONTENT_CHANGED, enabled)
        assertEquals(enabled, EventTypes.withContentChanged(enabled, enabled = true))
    }

    @Test
    fun `disabling removes only content-changed`() {
        val enabled = base or TYPE_WINDOW_CONTENT_CHANGED
        assertEquals(base, EventTypes.withContentChanged(enabled, enabled = false))
        assertEquals(base, EventTypes.withContentChanged(base, enabled = false))
    }
}
