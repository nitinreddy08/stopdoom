package app.unrot.inspector

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** File names for recordings: `20261101-143005-instagram-reels-10-swipes`. */
object TraceNames {
    private const val MAX_SLUG_LENGTH = 40
    private const val FALLBACK = "trace"
    private val stamp: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    private val notSlug = Regex("[^a-z0-9]+")

    fun build(label: String, timeMs: Long, zone: ZoneId): String {
        val time = Instant.ofEpochMilli(timeMs).atZone(zone).format(stamp)
        return "$time-${slug(label)}"
    }

    /** Lowercase ASCII letters and digits joined by dashes; "trace" if nothing is left. */
    fun slug(label: String): String = label.lowercase(Locale.ROOT)
        .replace(notSlug, "-")
        .trim('-')
        .take(MAX_SLUG_LENGTH)
        .trimEnd('-')
        .ifEmpty { FALLBACK }
}
