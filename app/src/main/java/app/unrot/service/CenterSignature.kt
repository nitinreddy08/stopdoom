package app.unrot.service

import app.unrot.detection.Box
import java.security.MessageDigest

/**
 * The `centerSignature` of a settle snapshot (SPEC §7.4): SHA-256 of
 * `className|childCount|first 64 chars of the center child's text`, as 16 hex characters.
 *
 * ADR-019: the text is read in memory only, lives in a [TextCollector] for the length of one
 * snapshot, and only the hash leaves this file. Never log or store the collector's contents.
 */
object CenterSignature {
    const val MAX_TEXT = 64
    const val MAX_DEPTH = 3
    const val MAX_NODES = 20
    private const val HEX_LENGTH = 16

    /** Joins non-blank text and content descriptions, up to [MAX_TEXT] characters. */
    class TextCollector {
        private val builder = StringBuilder(MAX_TEXT)

        val isFull: Boolean get() = builder.length >= MAX_TEXT

        fun add(value: CharSequence?) {
            if (value.isNullOrBlank() || isFull) return
            builder.append(value, 0, minOf(value.length, MAX_TEXT - builder.length))
        }

        internal fun text(): String = builder.toString()
    }

    fun hash(className: String?, childCount: Int, text: TextCollector): String {
        val input = "$className|$childCount|${text.text()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }.take(HEX_LENGTH)
    }

    /** Index of the first child containing the container's center point (SPEC §7.8), or null. */
    fun centerChild(container: Box, children: List<Box>): Int? = children
        .indexOfFirst { it.contains(container.centerX, container.centerY) }
        .takeIf { it >= 0 }
}
