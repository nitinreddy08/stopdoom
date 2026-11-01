package app.unrot.inspector

import app.unrot.detection.Box
import app.unrot.detection.trace.TraceEvent
import app.unrot.detection.trace.TraceExpectation
import app.unrot.detection.trace.TraceFormat
import java.io.File
import java.time.ZoneOffset
import java.util.zip.ZipFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class TraceRecorderTest {
    @TempDir
    lateinit var dir: File

    private var now = START

    private val metadata = object : TraceMetadata {
        override val device = "Test Phone, Android 16 (API 36)"

        override fun appId(pkg: String) =
            mapOf(INSTAGRAM to "instagram", SNAPCHAT to "snapchat")[pkg]

        override fun versionCode(pkg: String) = if (pkg == INSTAGRAM) 400L else null
    }

    private fun recorder(maxLines: Int = TraceRecorder.MAX_LINES) = TraceRecorder(
        directory = dir,
        metadata = metadata,
        clock = { now },
        maxLines = maxLines,
        zone = ZoneOffset.UTC,
    )

    private fun scrolled(pkg: String = INSTAGRAM, t: Long = now) = TraceEvent.Scrolled(
        t = t,
        pkg = pkg,
        cls = "androidx.recyclerview.widget.RecyclerView",
        vid = null,
        b = SCREEN,
        win = SCREEN,
        from = 1,
        to = 2,
        cnt = 10,
        dy = 2400,
        key = "1:null:R:0 0 1080 2400",
    )

    private fun File.lines() = readLines().filter { it.isNotBlank() }

    private fun traceFile(name: String) = File(dir, "$name.jsonl")

    private fun sidecar(name: String) =
        TraceFormat.decodeExpectation(File(dir, "$name.expected.json").readText())

    private fun TraceRecorder.recordingName() = (state.value as RecorderState.Recording).name

    @Test
    fun `start writes the first lines and a sidecar`() {
        val recorder = recorder()
        val first = TraceEvent.Foreground(START, null, SCREEN)
        assertTrue(recorder.start("  Instagram Reels, 10 swipes ", 11, listOf(first)))
        val name = recorder.recordingName()

        assertEquals("20241101-120000-instagram-reels-10-swipes", name)
        assertTrue(recorder.isRecording)
        assertEquals(1, (recorder.state.value as RecorderState.Recording).lines)
        recorder.stop()
        assertEquals(listOf(TraceFormat.encode(first)), traceFile(name).lines())
        assertEquals("Instagram Reels, 10 swipes", sidecar(name).notes)
    }

    @Test
    fun `lines are written exactly in the trace format, one per line`() {
        val recorder = recorder()
        recorder.start("reels", 2)
        val name = recorder.recordingName()
        val events = listOf(
            scrolled(),
            TraceEvent.Snapshot(
                START + 300,
                "k",
                missing = false,
                children = emptyList(),
                sig = "ab",
            ),
            TraceEvent.ScreenOff(START + 400),
        )
        events.forEach { recorder.record(it) }
        recorder.stop()

        assertEquals(events.map { TraceFormat.encode(it) }, traceFile(name).lines())
        assertEquals(events, traceFile(name).lines().map { TraceFormat.decode(it) })
    }

    @Test
    fun `stop fills in the app and its version from the most scrolled package`() {
        val recorder = recorder()
        recorder.start("reels", 11)
        val name = recorder.recordingName()
        recorder.record(TraceEvent.Foreground(now, SNAPCHAT, SCREEN))
        recorder.record(TraceEvent.Foreground(now, SNAPCHAT, SCREEN))
        repeat(3) { recorder.record(scrolled(INSTAGRAM)) }
        recorder.record(scrolled(SNAPCHAT))
        val saved = recorder.stop()

        val expected = TraceExpectation(11, "instagram", "reels", 400L, metadata.device)
        assertEquals(expected, sidecar(name))
        assertEquals(name, saved?.name)
        assertEquals("instagram", saved?.app)
        assertEquals(RecorderState.Idle(StopReason.USER), recorder.state.value)
        assertFalse(recorder.isRecording)
    }

    @Test
    fun `without scrolls the app comes from other lines, else it is unknown`() {
        val recorder = recorder()
        recorder.start("stories", 0)
        val stories = recorder.recordingName()
        recorder.record(TraceEvent.Window(now, SNAPCHAT, null))
        recorder.stop()
        assertEquals("snapchat", sidecar(stories).app)
        assertNull(sidecar(stories).appVersionCode)

        now += 1_000
        recorder.start("nothing", 0)
        val nothing = recorder.recordingName()
        recorder.record(TraceEvent.Foreground(now, null, SCREEN))
        recorder.stop()
        assertEquals(TraceRecorder.UNKNOWN_APP, sidecar(nothing).app)
    }

    @Test
    fun `events while idle are ignored and a second start is refused`() {
        val recorder = recorder()
        recorder.record(scrolled())
        assertTrue(dir.listFiles().orEmpty().isEmpty())

        assertTrue(recorder.start("a", 1))
        assertFalse(recorder.start("b", 1))
        assertEquals("a", recorder.stop()?.label)
        assertNull(recorder.stop())
    }

    @Test
    fun `stops by itself at the line limit`() {
        val recorder = recorder(maxLines = 3)
        recorder.start("limit", 1)
        val name = recorder.recordingName()
        repeat(5) { recorder.record(scrolled()) }

        assertEquals(3, traceFile(name).lines().size)
        assertEquals(RecorderState.Idle(StopReason.LINE_LIMIT), recorder.state.value)
        assertFalse(recorder.isRecording)
    }

    @Test
    fun `stops by itself after 5 minutes`() {
        val recorder = recorder()
        recorder.start("time", 1)
        val name = recorder.recordingName()
        now += TraceRecorder.MAX_DURATION_MS - 1
        recorder.record(scrolled())
        now += 1
        recorder.record(scrolled())

        assertEquals(1, traceFile(name).lines().size)
        assertEquals(RecorderState.Idle(StopReason.TIME_LIMIT), recorder.state.value)
    }

    @Test
    fun `lists finished recordings newest first and never the running one`() {
        val recorder = recorder()
        recorder.start("first", 1)
        recorder.stop()
        now += 1_000
        recorder.start("second", 2)
        recorder.stop()
        now += 1_000
        recorder.start("running", 3)

        recorder.reload()
        assertEquals(listOf("second", "first"), recorder.saved.value.map { it.label })
        assertEquals(listOf(2, 1), recorder.saved.value.map { it.expectedReels })

        val fresh = recorder()
        fresh.reload()
        assertEquals(3, fresh.saved.value.size)
    }

    @Test
    fun `two recordings in the same second get different names`() {
        val recorder = recorder()
        recorder.start("same", 1)
        val first = recorder.recordingName()
        recorder.stop()
        recorder.start("same", 1)
        val second = recorder.recordingName()
        recorder.stop()
        assertEquals("$first-2", second)
    }

    @Test
    fun `delete removes both files`() {
        val recorder = recorder()
        recorder.start("gone", 1)
        val name = recorder.recordingName()
        recorder.stop()
        recorder.delete(name)
        assertTrue(dir.listFiles().orEmpty().isEmpty())
        assertTrue(recorder.saved.value.isEmpty())
    }

    @Test
    fun `zipAll packs every trace with its sidecar`() {
        val recorder = recorder()
        val target = File(dir, "out/all.zip")
        recorder.reload()
        assertFalse(recorder.zipAll(target))

        recorder.start("one", 1)
        recorder.record(scrolled())
        val name = recorder.recordingName()
        recorder.stop()
        assertTrue(recorder.zipAll(target))
        val entries = ZipFile(target).use { zip -> zip.entries().toList().map { it.name } }
        assertEquals(setOf("$name.jsonl", "$name.expected.json"), entries.toSet())
    }

    @Test
    fun `a broken folder stops the recording instead of crashing`() {
        val errors = mutableListOf<String>()
        val notADirectory = File(dir, "file").apply { writeText("x") }
        val recorder = TraceRecorder(notADirectory, metadata, { now }, onError = { errors += it })
        assertFalse(recorder.start("x", 1))
        assertFalse(recorder.isRecording)
        assertEquals(1, errors.size)
    }

    private companion object {
        const val START = 1730462400000L // 2024-11-01T12:00:00Z
        const val INSTAGRAM = "com.instagram.android"
        const val SNAPCHAT = "com.snapchat.android"
        val SCREEN = Box(0, 0, 1080, 2400)
    }
}
