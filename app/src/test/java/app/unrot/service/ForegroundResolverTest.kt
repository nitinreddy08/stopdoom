package app.unrot.service

import app.unrot.detection.Box
import app.unrot.detection.trace.TraceEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ForegroundResolverTest {
    private val full = Bounds(0, 0, 1080, 2400)
    private val top = Bounds(0, 0, 1080, 1180)
    private val bottom = Bounds(0, 1220, 1080, 2400)

    private fun app(
        pkg: String?,
        bounds: Bounds = full,
        focused: Boolean = false,
        active: Boolean = false,
        pip: Boolean = false,
    ) = WindowSnapshot(WindowKind.APPLICATION, pkg, bounds, focused, active, pip)

    private fun other(kind: WindowKind, focused: Boolean = false) =
        WindowSnapshot(kind, null, full, focused, focused)

    private fun resolve(vararg windows: WindowSnapshot) =
        ForegroundResolver.resolve(windows.toList(), 42L)

    @Test
    fun `focused application window is the foreground`() {
        val state = resolve(app(INSTAGRAM, focused = true, active = true))
        assertEquals(INSTAGRAM, state?.packageName)
        assertEquals(full, state?.appWindowBounds)
        assertEquals(listOf(INSTAGRAM), state?.visiblePackages)
        assertEquals(42L, state?.atMs)
    }

    @Test
    fun `notification shade on top does not change the foreground`() {
        val shade = other(WindowKind.SYSTEM, focused = true)
        assertEquals(INSTAGRAM, resolve(shade, app(INSTAGRAM, active = true))?.packageName)
        // No focused or active app window: no result, so the tracker keeps the previous app.
        assertNull(resolve(shade, app(INSTAGRAM)))
    }

    @Test
    fun `keyboard does not change the foreground`() {
        val keyboard = other(WindowKind.INPUT_METHOD, focused = true)
        val state = resolve(keyboard, app(YOUTUBE, focused = true))
        assertEquals(YOUTUBE, state?.packageName)
        assertEquals(listOf(YOUTUBE), state?.visiblePackages)
    }

    @Test
    fun `split screen picks the focused half and lists both apps`() {
        val divider = other(WindowKind.SPLIT_SCREEN_DIVIDER)
        val first = resolve(app(INSTAGRAM, top, focused = true), divider, app(YOUTUBE, bottom))
        assertEquals(INSTAGRAM, first?.packageName)
        assertEquals(top, first?.appWindowBounds)
        assertEquals(listOf(INSTAGRAM, YOUTUBE), first?.visiblePackages)

        val second = resolve(app(INSTAGRAM, top), divider, app(YOUTUBE, bottom, focused = true))
        assertEquals(YOUTUBE, second?.packageName)
        assertEquals(bottom, second?.appWindowBounds)
        assertEquals(listOf(YOUTUBE, INSTAGRAM), second?.visiblePackages)
    }

    @Test
    fun `active window is used when no application window is focused`() {
        val state = resolve(app(INSTAGRAM), app(LAUNCHER, active = true))
        assertEquals(LAUNCHER, state?.packageName)
    }

    @Test
    fun `picture in picture never counts as foreground`() {
        val pip = app(YOUTUBE, Bounds(600, 1800, 1000, 2200), active = true, pip = true)
        val state = resolve(pip, app(LAUNCHER, focused = true))
        assertEquals(LAUNCHER, state?.packageName)
        assertEquals(listOf(LAUNCHER), state?.visiblePackages)
        assertNull(resolve(pip))
    }

    @Test
    fun `our overlay and windows without a package are ignored`() {
        val overlay = other(WindowKind.ACCESSIBILITY_OVERLAY, focused = true)
        val unknown = app(null, focused = true)
        assertEquals(TIKTOK, resolve(overlay, unknown, app(TIKTOK, active = true))?.packageName)
        assertNull(resolve(overlay, unknown))
    }

    @Test
    fun `only package or bounds changes count as a change`() {
        val start = ForegroundState(INSTAGRAM, full, listOf(INSTAGRAM), 1L)
        assertTrue(ForegroundResolver.isChange(null, start))
        val sameAgain = start.copy(visiblePackages = listOf(INSTAGRAM, YOUTUBE), atMs = 2L)
        assertFalse(ForegroundResolver.isChange(start, sameAgain))
        assertTrue(ForegroundResolver.isChange(start, start.copy(appWindowBounds = top)))
        assertTrue(ForegroundResolver.isChange(start, start.copy(packageName = YOUTUBE)))
    }

    @Test
    fun `Shelf over the home screen never becomes the foreground`() {
        // OxygenOS 16 reports the Shelf (swipe down on the home screen) as an application window.
        val shelf = app(SHELF, focused = true, active = true)
        val state = resolve(shelf, app(LAUNCHER, active = true))
        assertEquals(LAUNCHER, state?.packageName)
        assertEquals(listOf(LAUNCHER), state?.visiblePackages)
        // Launcher neither focused nor active: no result, so the tracker keeps the previous app.
        assertNull(resolve(shelf, app(LAUNCHER)))
    }

    @Test
    fun `system UI shown as an application window never becomes the foreground`() {
        assertNull(resolve(app(SYSTEM_UI, focused = true, active = true)))
        val state = resolve(app(SYSTEM_UI, focused = true), app(YOUTUBE, active = true))
        assertEquals(YOUTUBE, state?.packageName)
    }

    @Test
    fun `split-screen container window is ignored`() {
        // OxygenOS 16 adds a com.oplus.pscanvas application window next to the two halves.
        val canvas = app(SPLIT_CANVAS)
        val state = resolve(app(YOUTUBE, top), canvas, app(INSTAGRAM, bottom, focused = true))
        assertEquals(INSTAGRAM, state?.packageName)
        assertEquals(listOf(INSTAGRAM, YOUTUBE), state?.visiblePackages)
    }

    @Test
    fun `trace lines name tracked apps only`() {
        val tracked = setOf(INSTAGRAM)
        val instagram = ForegroundState(INSTAGRAM, top, listOf(INSTAGRAM), 5L)
        assertEquals(
            TraceEvent.Foreground(5L, INSTAGRAM, Box(0, 0, 1080, 1180)),
            instagram.toTraceEvent(tracked),
        )
        val launcher = ForegroundState(LAUNCHER, full, listOf(LAUNCHER), 6L)
        assertEquals(TraceEvent.Foreground(6L, null, full.toBox()), launcher.toTraceEvent(tracked))
    }

    private companion object {
        const val SPLIT_CANVAS = "com.oplus.pscanvas"
        const val SHELF = "com.coloros.assistantscreen"
        const val SYSTEM_UI = "com.android.systemui"
        const val INSTAGRAM = "com.instagram.android"
        const val YOUTUBE = "com.google.android.youtube"
        const val TIKTOK = "com.zhiliaoapp.musically"
        const val LAUNCHER = "net.oneplus.launcher"
    }
}
