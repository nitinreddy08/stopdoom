package app.unrot.service

import app.unrot.detection.Box
import java.security.MessageDigest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CenterSignatureTest {
    private fun collect(vararg values: String?) =
        CenterSignature.TextCollector().apply { values.forEach { add(it) } }

    private fun hash(vararg values: String?, cls: String? = "FrameLayout", children: Int = 3) =
        CenterSignature.hash(cls, children, collect(*values))

    @Test
    fun `hash is 16 lowercase hex characters and stable`() {
        val signature = hash("caption", "@someone")
        assertTrue(signature.matches(Regex("[0-9a-f]{16}")), signature)
        assertEquals(signature, hash("caption", "@someone"))
    }

    @Test
    fun `hash is SHA-256 of className, childCount and text`() {
        val expected = MessageDigest.getInstance("SHA-256")
            .digest("FrameLayout|3|ab".toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)
        assertEquals(expected, hash("a", "b"))
    }

    @Test
    fun `different reels give different hashes`() {
        assertNotEquals(hash("first reel"), hash("second reel"))
        assertNotEquals(hash("same", cls = "A"), hash("same", cls = "B"))
        assertNotEquals(hash("same", children = 3), hash("same", children = 4))
    }

    @Test
    fun `blank and missing text is skipped`() {
        assertEquals(hash("ab"), hash(null, "  ", "a", "", "b", "\n"))
    }

    @Test
    fun `only the first 64 characters count`() {
        val long = "x".repeat(64)
        assertEquals(hash(long), hash(long + "more"))
        assertEquals(hash(long), hash("x".repeat(30), "x".repeat(34), "ignored"))
        assertNotEquals(hash("x".repeat(63)), hash(long))
        assertTrue(collect(long).isFull)
        assertFalse(collect("x".repeat(63)).isFull)
    }

    @Test
    fun `center child is the one containing the container center`() {
        val container = Box(0, 0, 1080, 2400)
        val above = Box(0, -1200, 1080, 1100)
        val center = Box(0, 1100, 1080, 3500)
        assertEquals(1, CenterSignature.centerChild(container, listOf(above, center)))
        assertEquals(0, CenterSignature.centerChild(container, listOf(container, center)))
        assertNull(CenterSignature.centerChild(container, listOf(above)))
        assertNull(CenterSignature.centerChild(container, emptyList()))
    }
}
