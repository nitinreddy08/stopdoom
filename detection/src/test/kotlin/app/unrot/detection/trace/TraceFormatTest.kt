package app.unrot.detection.trace

import app.unrot.detection.Box
import app.unrot.detection.ChildInfo
import app.unrot.detection.Signal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TraceFormatTest {
    private val list = Box(0, 0, 1080, 2340)
    private val window = Box(0, 0, 1080, 2400)

    private val scrolled = TraceEvent.Scrolled(
        t = 1730460000123,
        pkg = INSTAGRAM,
        cls = "androidx.recyclerview.widget.RecyclerView",
        vid = "com.instagram.android:id/clips_viewer_view_pager",
        b = list,
        win = window,
        from = 4,
        to = 5,
        cnt = 30,
        dy = 812,
        key = "17:com.instagram.android:id/x:Recycler:0 0 1080 2340",
    )

    private val snapshot = TraceEvent.Snapshot(
        t = 1730460000500,
        key = "k",
        missing = false,
        children = listOf(
            ChildInfo(Box(0, -1500, 1080, 840), "android.widget.FrameLayout"),
            ChildInfo(Box(0, 840, 1080, 3180), null),
        ),
        sig = "9f2c0a1b2c3d4e5f",
    )

    @Test
    fun `scrolled line has the SPEC 15_3 fields in order`() {
        val expected = """{"t":1730460000123,"e":"SCROLLED","pkg":"com.instagram.android",""" +
            """"cls":"androidx.recyclerview.widget.RecyclerView",""" +
            """"vid":"com.instagram.android:id/clips_viewer_view_pager",""" +
            """"b":[0,0,1080,2340],"win":[0,0,1080,2400],"from":4,"to":5,"cnt":30,"dy":812,""" +
            """"key":"17:com.instagram.android:id/x:Recycler:0 0 1080 2340"}"""
        assertEquals(expected, TraceFormat.encode(scrolled))
    }

    @Test
    fun `missing values are written as null`() {
        val event = scrolled.copy(cls = null, vid = null, win = null, dy = null)
        val line = TraceFormat.encode(event)
        assertEquals(true, line.contains(""""cls":null,"vid":null,"""))
        assertEquals(true, line.contains(""""win":null,"""))
        assertEquals(true, line.contains(""""dy":null,"""))
        assertEquals(event, TraceFormat.decode(line))
    }

    @Test
    fun `snapshot line has the SPEC 15_3 fields in order`() {
        val expected = """{"t":1730460000500,"e":"SNAPSHOT","key":"k","missing":false,""" +
            """"children":[{"b":[0,-1500,1080,840],"cls":"android.widget.FrameLayout"},""" +
            """{"b":[0,840,1080,3180],"cls":null}],"sig":"9f2c0a1b2c3d4e5f"}"""
        assertEquals(expected, TraceFormat.encode(snapshot))
    }

    @Test
    fun `foreground and screen lines match SPEC 15_3`() {
        assertEquals(
            """{"t":5,"e":"FOREGROUND","pkg":"com.instagram.android","win":[0,0,1080,2400]}""",
            TraceFormat.encode(TraceEvent.Foreground(5, INSTAGRAM, window)),
        )
        assertEquals(
            """{"t":6,"e":"FOREGROUND","pkg":null,"win":[0,0,1080,2400]}""",
            TraceFormat.encode(TraceEvent.Foreground(6, null, window)),
        )
        assertEquals("""{"t":7,"e":"SCREEN_OFF"}""", TraceFormat.encode(TraceEvent.ScreenOff(7)))
        assertEquals("""{"t":8,"e":"SCREEN_ON"}""", TraceFormat.encode(TraceEvent.ScreenOn(8)))
    }

    @Test
    fun `window and content-changed lines`() {
        assertEquals(
            """{"t":9,"e":"WINDOW","pkg":"com.instagram.android","cls":"android.app.Dialog"}""",
            TraceFormat.encode(TraceEvent.Window(9, INSTAGRAM, "android.app.Dialog")),
        )
        assertEquals(
            """{"t":10,"e":"CONTENT_CHANGED","pkg":"com.instagram.android","cls":null,"ct":1}""",
            TraceFormat.encode(TraceEvent.ContentChanged(10, INSTAGRAM, null, 1)),
        )
    }

    @Test
    fun `every event type reads back unchanged`() {
        val events = listOf(
            scrolled,
            snapshot,
            snapshot.copy(missing = true, children = emptyList(), sig = null),
            TraceEvent.Foreground(5, INSTAGRAM, window),
            TraceEvent.Foreground(6, null, window),
            TraceEvent.Window(9, INSTAGRAM, null),
            TraceEvent.ContentChanged(10, INSTAGRAM, "android.widget.TextView", 4),
            TraceEvent.ScreenOff(7),
            TraceEvent.ScreenOn(8),
        )
        events.forEach { assertEquals(it, TraceFormat.decode(TraceFormat.encode(it))) }
    }

    @Test
    fun `special characters in names survive`() {
        val event = TraceEvent.Window(1, INSTAGRAM, "a\"b\\c\td")
        assertEquals(event, TraceFormat.decode(TraceFormat.encode(event)))
    }

    @Test
    fun `unknown fields are ignored`() {
        val line = """{"t":7,"e":"SCREEN_OFF","extra":[1,2]}"""
        assertEquals(TraceEvent.ScreenOff(7), TraceFormat.decode(line))
    }

    @Test
    fun `malformed lines are rejected`() {
        val bad = listOf(
            "",
            "not json",
            "[1,2]",
            """{"e":"SCREEN_OFF"}""",
            """{"t":1,"e":"NOPE"}""",
            """{"t":1,"e":"FOREGROUND","pkg":"p","win":[0,0,1]}""",
            """{"t":1,"e":"FOREGROUND","pkg":"p","win":"x"}""",
            """{"t":1,"e":"SNAPSHOT","key":"k","missing":"no","children":[],"sig":null}""",
            """{"t":1,"e":"SCROLLED","pkg":"p","b":[0,0,1,1],"from":1,"to":2,"cnt":3,"key":4}""",
        )
        bad.forEach { line ->
            assertThrows(TraceFormatException::class.java, { TraceFormat.decode(line) }, line)
        }
    }

    @Test
    fun `scrolled events convert to and from signals`() {
        val signal = scrolled.toSignal()
        assertEquals(
            Signal.Scrolled(
                pkg = INSTAGRAM,
                className = scrolled.cls,
                viewId = scrolled.vid,
                bounds = list,
                fromIndex = 4,
                toIndex = 5,
                itemCount = 30,
                deltaY = 812,
                containerKey = scrolled.key,
                nowMs = scrolled.t,
            ),
            signal,
        )
        assertEquals(scrolled, TraceEvent.Scrolled.of(signal, window))
        assertEquals(
            Signal.SettleSnapshot("k", snapshot.children, snapshot.sig, snapshot.t),
            snapshot.toSignal(),
        )
        assertEquals(
            Signal.Foreground(null, window, emptySet(), 6),
            TraceEvent.Foreground(6, null, window).toSignal(),
        )
    }

    @Test
    fun `expectation sidecar matches SPEC 15_3`() {
        val expectation = TraceExpectation(
            expectedReels = 10,
            app = "instagram",
            notes = "Instagram reels 10 swipes",
            appVersionCode = 123,
            device = "OnePlus CPH2487, Android 16 (API 36)",
        )
        val json = TraceFormat.encodeExpectation(expectation)
        assertEquals(
            """{"expectedReels":10,"app":"instagram","notes":"Instagram reels 10 swipes",""" +
                """"appVersionCode":123,"device":"OnePlus CPH2487, Android 16 (API 36)"}""",
            json,
        )
        assertEquals(expectation, TraceFormat.decodeExpectation(json))
    }

    @Test
    fun `expectation tolerance and missing optional fields`() {
        val withTolerance = TraceExpectation(1, "youtube", "flaky ad", null, "d", tolerance = 1)
        val json = TraceFormat.encodeExpectation(withTolerance)
        assertEquals(true, json.endsWith(""""appVersionCode":null,"device":"d","tolerance":1}"""))
        assertEquals(withTolerance, TraceFormat.decodeExpectation(json))
        assertEquals(
            TraceExpectation(0, "instagram", "", null, ""),
            TraceFormat.decodeExpectation("""{"expectedReels":0,"app":"instagram"}"""),
        )
        assertThrows(TraceFormatException::class.java) {
            TraceFormat.decodeExpectation("""{"app":"instagram"}""")
        }
    }

    private companion object {
        const val INSTAGRAM = "com.instagram.android"
    }
}
