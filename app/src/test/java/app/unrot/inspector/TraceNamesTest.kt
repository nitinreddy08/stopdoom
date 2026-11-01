package app.unrot.inspector

import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TraceNamesTest {
    @Test
    fun `names start with the local time and end with the label`() {
        val name = TraceNames.build("Instagram Reels, 10 swipes!", 1730462405000L, ZoneOffset.UTC)
        assertEquals("20241101-120005-instagram-reels-10-swipes", name)
    }

    @Test
    fun `slugs keep only ascii letters and digits`() {
        assertEquals("reels-5-down-2-up", TraceNames.slug("  Reels: 5 down / 2 up  "))
        assertEquals("caf", TraceNames.slug("Café"))
        assertEquals("trace", TraceNames.slug("!!!"))
        assertEquals("trace", TraceNames.slug(""))
    }

    @Test
    fun `slugs are at most 40 characters and never end with a dash`() {
        val slug = TraceNames.slug("a".repeat(39) + " b c d")
        assertEquals("a".repeat(39), slug)
        assertEquals(40, TraceNames.slug("x".repeat(100)).length)
    }
}
