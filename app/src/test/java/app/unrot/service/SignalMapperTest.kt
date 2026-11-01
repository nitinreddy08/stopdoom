package app.unrot.service

import app.unrot.detection.Box
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SignalMapperTest {
    @Test
    fun `container key is windowId, view id, class and flattened bounds`() {
        val key = SignalMapper.containerKey(
            windowId = 42,
            viewId = "com.instagram.android:id/clips_viewer_view_pager",
            className = "androidx.viewpager.widget.ViewPager",
            bounds = Box(0, 66, 1080, 2340),
        )
        assertEquals(
            "42:com.instagram.android:id/clips_viewer_view_pager:" +
                "androidx.viewpager.widget.ViewPager:0 66 1080 2340",
            key,
        )
    }

    @Test
    fun `missing view id and class still give a stable key`() {
        val key = SignalMapper.containerKey(7, null, null, Box(-5, 0, 10, 20))
        assertEquals("7:null:null:-5 0 10 20", key)
    }

    @Test
    fun `undefined scroll delta becomes null`() {
        assertNull(SignalMapper.deltaY(SignalMapper.UNDEFINED_DELTA))
        assertEquals(812, SignalMapper.deltaY(812))
        assertEquals(-2400, SignalMapper.deltaY(-2400))
        assertEquals(0, SignalMapper.deltaY(0))
    }
}
