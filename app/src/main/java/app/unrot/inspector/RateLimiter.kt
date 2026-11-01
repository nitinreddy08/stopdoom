package app.unrot.inspector

/**
 * Lets at most [maxEvents] through in any [windowMs] (sliding window). Used to sample
 * content-changed events to at most 5 per second while recording (SPEC §15.2).
 * Not thread-safe: the service uses it on the main thread only.
 */
class RateLimiter(private val maxEvents: Int, private val windowMs: Long) {
    private val accepted = ArrayDeque<Long>(maxEvents)

    fun tryAcquire(nowMs: Long): Boolean {
        while (accepted.isNotEmpty() && nowMs - accepted.first() >= windowMs) {
            accepted.removeFirst()
        }
        if (accepted.size >= maxEvents) return false
        accepted.addLast(nowMs)
        return true
    }
}
