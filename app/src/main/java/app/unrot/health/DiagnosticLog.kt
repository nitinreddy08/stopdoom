package app.unrot.health

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

data class LogEntry(val timeMs: Long, val level: LogLevel, val tag: String, val message: String)

/**
 * Diagnostic log (SPEC §14.4): a ring buffer of the newest [capacity] entries, kept in memory and
 * saved to [file] shortly after each write. Callers must log structural info only (package names,
 * window types, bounds, counts), never text from other apps (ADR-019).
 *
 * Never throws: logging must not be able to crash the accessibility service.
 */
class DiagnosticLog(
    private val file: File?,
    private val scope: CoroutineScope? = null,
    private val capacity: Int = CAPACITY,
    private val clock: () -> Long = System::currentTimeMillis,
    private val echo: ((LogEntry) -> Unit)? = null,
) {
    private val lock = Any()
    private val fileLock = Any()
    private val buffer = ArrayDeque<LogEntry>(capacity)
    private var flushScheduled = false

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())

    /** Oldest first. */
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    init {
        load()
    }

    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)

    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message)

    fun w(tag: String, message: String) = log(LogLevel.WARN, tag, message)

    fun e(tag: String, message: String) = log(LogLevel.ERROR, tag, message)

    fun log(level: LogLevel, tag: String, message: String) {
        try {
            val entry = LogEntry(clock(), level, clean(tag), clean(message))
            synchronized(lock) {
                add(entry)
                _entries.value = buffer.toList()
            }
            echo?.invoke(entry)
            scheduleFlush()
        } catch (t: Throwable) {
            // Logging must never crash the caller.
        }
    }

    /** Writes the buffer to [file] (atomically, via a temp file). Safe to call from any thread. */
    fun flush() {
        val target = file ?: return
        val snapshot = synchronized(lock) {
            flushScheduled = false
            buffer.toList()
        }
        try {
            synchronized(fileLock) {
                target.parentFile?.mkdirs()
                val tmp = File(target.path + ".tmp")
                tmp.writeText(snapshot.joinToString(separator = "") { format(it) + "\n" })
                if (!tmp.renameTo(target)) {
                    target.delete()
                    tmp.renameTo(target)
                }
            }
        } catch (t: Throwable) {
            // Keep the in-memory buffer; the next flush retries.
        }
    }

    private fun scheduleFlush() {
        val flushScope = scope ?: return
        synchronized(lock) {
            if (flushScheduled) return
            flushScheduled = true
        }
        flushScope.launch {
            delay(FLUSH_DELAY_MS)
            flush()
        }
    }

    private fun load() {
        val source = file ?: return
        try {
            if (!source.exists()) return
            val loaded = source.readLines().mapNotNull { parse(it) }
            synchronized(lock) {
                loaded.forEach { add(it) }
                _entries.value = buffer.toList()
            }
        } catch (t: Throwable) {
            // A corrupt or unreadable file starts an empty log.
        }
    }

    private fun add(entry: LogEntry) {
        while (buffer.size >= capacity) buffer.removeFirst()
        buffer.addLast(entry)
    }

    companion object {
        const val CAPACITY = 500
        private const val FLUSH_DELAY_MS = 1_000L

        private fun clean(text: String): String =
            text.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

        private fun format(entry: LogEntry): String =
            "${entry.timeMs}\t${entry.level.name}\t${entry.tag}\t${entry.message}"

        private fun parse(line: String): LogEntry? {
            val parts = line.split('\t', limit = 4)
            if (parts.size != 4) return null
            val time = parts[0].toLongOrNull() ?: return null
            val level = LogLevel.entries.firstOrNull { it.name == parts[1] } ?: return null
            return LogEntry(time, level, parts[2], parts[3])
        }
    }
}
