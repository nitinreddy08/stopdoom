package app.unrot.inspector

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RateLimiterTest {
    @Test
    fun `lets at most 5 events through per second`() {
        val limiter = RateLimiter(maxEvents = 5, windowMs = 1_000)
        val accepted = (0 until 20).count { limiter.tryAcquire(1_000L + it * 10) }
        assertEquals(5, accepted)
    }

    @Test
    fun `the window slides`() {
        val limiter = RateLimiter(maxEvents = 5, windowMs = 1_000)
        val times = listOf(0L, 100, 200, 300, 400, 500, 999, 1_000, 1_099, 1_100)
        val results = times.map { limiter.tryAcquire(it) }
        // 500 and 999 are refused (5 already in the last second); 1000 frees the slot of 0, etc.
        assertEquals(
            listOf(true, true, true, true, true, false, false, true, false, true),
            results,
        )
    }

    @Test
    fun `steady events at 10 per second are halved`() {
        val limiter = RateLimiter(maxEvents = 5, windowMs = 1_000)
        val accepted = (0 until 100).count { limiter.tryAcquire(it * 100L) }
        assertEquals(50, accepted)
    }
}
