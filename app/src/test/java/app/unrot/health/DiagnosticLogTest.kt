package app.unrot.health

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DiagnosticLogTest {
    @TempDir
    lateinit var dir: File

    private var now = 1_000L

    private fun file() = File(dir, "log.tsv")

    private fun newLog(capacity: Int = DiagnosticLog.CAPACITY) =
        DiagnosticLog(file = file(), capacity = capacity, clock = { now++ })

    private fun DiagnosticLog.messages() = entries.value.map { it.message }

    @Test
    fun `keeps only the newest entries, oldest first`() {
        val log = newLog(capacity = 3)
        repeat(5) { log.i("T", "m$it") }
        assertEquals(listOf("m2", "m3", "m4"), log.messages())
    }

    @Test
    fun `holds 500 entries by default`() {
        val log = newLog()
        repeat(501) { log.i("T", "m$it") }
        assertEquals(500, log.entries.value.size)
        assertEquals("m1", log.entries.value.first().message)
        assertEquals("m500", log.entries.value.last().message)
    }

    @Test
    fun `saved entries are loaded again`() {
        val log = newLog()
        log.i("Service", "connected")
        log.w("Foreground", "foreground com.instagram.android [0,0][1080,2400]")
        log.flush()
        assertEquals(log.entries.value, newLog().entries.value)
    }

    @Test
    fun `loading keeps only the newest entries`() {
        val log = newLog()
        repeat(10) { log.i("T", "m$it") }
        log.flush()
        assertEquals(listOf("m7", "m8", "m9"), newLog(capacity = 3).messages())
    }

    @Test
    fun `corrupt lines are skipped`() {
        file().writeText("garbage\n12\tINFO\tT\tok\nnot-a-time\tINFO\tT\tx\n13\tLOUD\tT\tx\n")
        assertEquals(listOf("ok"), newLog().messages())
    }

    @Test
    fun `tabs and newlines in a message are flattened`() {
        val log = newLog()
        log.e("T", "a\tb\nc")
        log.flush()
        assertEquals("a b c", newLog().entries.value.single().message)
    }

    @Test
    fun `an unreadable file never throws`() {
        assertTrue(file().mkdirs()) // A directory where the file should be.
        val log = newLog()
        log.i("T", "still works")
        log.flush()
        assertEquals(listOf("still works"), log.messages())
    }
}
