package app.unrot.rules

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TrackedPackagesTest {
    @Test
    fun `bundled rules list exactly the six tracked packages`() {
        // Set by app/build.gradle.kts.
        val path = requireNotNull(System.getProperty("unrot.rulesFile"))
        val rulesFile = File(path)
        val expected = setOf(
            "com.instagram.android",
            "com.google.android.youtube",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.facebook.katana",
            "com.snapchat.android",
        )
        assertEquals(expected, TrackedPackages.parse(rulesFile.readText()))
    }

    @Test
    fun `each package maps to its app id`() {
        val rulesFile = File(requireNotNull(System.getProperty("unrot.rulesFile")))
        val apps = TrackedPackages.parseApps(rulesFile.readText())
        assertEquals("instagram", apps["com.instagram.android"])
        assertEquals("youtube", apps["com.google.android.youtube"])
        assertEquals("tiktok", apps["com.zhiliaoapp.musically"])
        assertEquals("tiktok", apps["com.ss.android.ugc.trill"])
        assertEquals("facebook", apps["com.facebook.katana"])
        assertEquals("snapchat", apps["com.snapchat.android"])
    }

    @Test
    fun `an invalid rules file throws`() {
        assertThrows(Exception::class.java) { TrackedPackages.parse("{}") }
        assertThrows(Exception::class.java) { TrackedPackages.parse("not json") }
    }
}
