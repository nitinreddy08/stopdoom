package app.unrot.inspector

import app.unrot.detection.trace.TraceEvent
import app.unrot.detection.trace.TraceExpectation
import app.unrot.detection.trace.TraceFormat
import app.unrot.detection.trace.TraceFormatException
import java.io.File
import java.io.IOException
import java.io.Writer
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class StopReason { USER, TIME_LIMIT, LINE_LIMIT, ERROR }

sealed interface RecorderState {
    /** [lastStop] says why the previous recording ended, if there was one. */
    data class Idle(val lastStop: StopReason? = null) : RecorderState

    data class Recording(
        val name: String,
        val label: String,
        val expectedReels: Int,
        val startedAtMs: Long,
        val lines: Int,
    ) : RecorderState
}

/** A finished recording: `name.jsonl` plus `name.expected.json`. */
data class SavedTrace(
    val name: String,
    val trace: File,
    val expectation: File,
    val label: String,
    val expectedReels: Int?,
    val app: String,
    val sizeBytes: Long,
    val savedAtMs: Long,
)

/** Facts about the phone and the recorded app, for the sidecar file. */
interface TraceMetadata {
    /** Phone and Android version, e.g. "OnePlus CPH2487, Android 16 (API 36)". */
    val device: String

    /** The rules' app id ("instagram") for a tracked package, or null. */
    fun appId(pkg: String): String?

    /** The installed version code of [pkg], or null if unknown. */
    fun versionCode(pkg: String): Long?
}

/**
 * Inspector recordings (SPEC §15.2): writes trace lines (§15.3) to `directory/name.jsonl` and the
 * `name.expected.json` sidecar. Stops by itself after [maxDurationMs] or [maxLines].
 *
 * Thread-safe: the service records from its detector thread while the Inspector screen starts,
 * stops and lists recordings from other threads. Never throws on file errors; it stops instead.
 */
class TraceRecorder(
    private val directory: File,
    private val metadata: TraceMetadata,
    private val clock: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope? = null,
    private val maxLines: Int = MAX_LINES,
    private val maxDurationMs: Long = MAX_DURATION_MS,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val onError: (String) -> Unit = {},
) {
    private class Active(
        val name: String,
        val label: String,
        val expectedReels: Int,
        val startedAtMs: Long,
        val writer: Writer,
    ) {
        var lines = 0
        val scrolledPackages = HashMap<String, Int>()
        val otherPackages = HashMap<String, Int>()
    }

    private val lock = Any()
    private var active: Active? = null
    private var timer: Job? = null

    private val _state = MutableStateFlow<RecorderState>(RecorderState.Idle())
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    private val _saved = MutableStateFlow<List<SavedTrace>>(emptyList())

    /** Finished recordings, newest first. Call [reload] once to read them from disk. */
    val saved: StateFlow<List<SavedTrace>> = _saved.asStateFlow()

    /** Cheap check for the service's event intake (main thread). */
    @Volatile
    var isRecording: Boolean = false
        private set

    /**
     * Starts a recording and writes [first] (e.g. the app in front) as its first lines. Returns
     * false if a recording is already running or the files can't be created.
     */
    fun start(label: String, expectedReels: Int, first: List<TraceEvent> = emptyList()): Boolean {
        synchronized(lock) {
            if (active != null) return false
            val now = clock()
            val cleanLabel = label.trim()
            val recording = try {
                directory.mkdirs()
                val name = uniqueName(TraceNames.build(cleanLabel, now, zone))
                val expectation = TraceExpectation(
                    expectedReels = expectedReels,
                    app = UNKNOWN_APP,
                    notes = cleanLabel,
                    appVersionCode = null,
                    device = metadata.device,
                )
                writeExpectation(name, expectation)
                Active(name, cleanLabel, expectedReels, now, traceFile(name).bufferedWriter())
            } catch (e: IOException) {
                onError("could not start a recording: ${e.javaClass.name}")
                return false
            }
            active = recording
            isRecording = true
            publish(recording)
            timer = scope?.launch {
                delay(maxDurationMs)
                stopIfCurrent(recording.name, StopReason.TIME_LIMIT)
            }
            first.forEach { write(recording, it) }
            return true
        }
    }

    /** Writes one line if a recording is running; ignored otherwise. */
    fun record(event: TraceEvent) {
        synchronized(lock) {
            val recording = active ?: return
            if (clock() - recording.startedAtMs >= maxDurationMs) {
                finish(StopReason.TIME_LIMIT)
                return
            }
            write(recording, event)
        }
    }

    /** Stops the running recording, if any, and returns it. */
    fun stop(): SavedTrace? = synchronized(lock) { finish(StopReason.USER) }

    /** Re-reads the list of finished recordings from disk. */
    fun reload() {
        synchronized(lock) {
            val files = directory.listFiles { file ->
                file.isFile && file.name.endsWith(TraceFormat.TRACE_EXTENSION)
            }.orEmpty()
            _saved.value = files
                .filter { it.name.removeSuffix(TraceFormat.TRACE_EXTENSION) != active?.name }
                .map { load(it) }
                .sortedByDescending { it.name }
        }
    }

    /** Deletes a finished recording (both files). The running recording can't be deleted. */
    fun delete(name: String) {
        synchronized(lock) {
            if (name == active?.name) return
            traceFile(name).delete()
            expectationFile(name).delete()
            reload()
        }
    }

    /**
     * Writes every finished recording (both files of each) into the zip file [target].
     * Returns false if there is nothing to zip or the zip can't be written.
     */
    fun zipAll(target: File): Boolean {
        val traces = synchronized(lock) { _saved.value }
        if (traces.isEmpty()) return false
        return try {
            target.parentFile?.mkdirs()
            ZipOutputStream(target.outputStream().buffered()).use { zip ->
                traces.flatMap { listOf(it.trace, it.expectation) }
                    .filter { it.isFile }
                    .forEach { file ->
                        zip.putNextEntry(ZipEntry(file.name))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
            }
            true
        } catch (e: IOException) {
            onError("could not zip recordings: ${e.javaClass.name}")
            target.delete()
            false
        }
    }

    private fun stopIfCurrent(name: String, reason: StopReason) {
        synchronized(lock) {
            if (active?.name == name) finish(reason)
        }
    }

    private fun write(recording: Active, event: TraceEvent) {
        try {
            recording.writer.write(TraceFormat.encode(event))
            recording.writer.write("\n")
            recording.lines++
            if (recording.lines % FLUSH_EVERY_LINES == 0) recording.writer.flush()
        } catch (e: IOException) {
            onError("could not write a trace line: ${e.javaClass.name}")
            finish(StopReason.ERROR)
            return
        }
        countPackage(recording, event)
        if (recording.lines >= maxLines) finish(StopReason.LINE_LIMIT) else publish(recording)
    }

    private fun finish(reason: StopReason): SavedTrace? {
        val recording = active ?: return null
        active = null
        isRecording = false
        timer?.cancel()
        timer = null
        try {
            recording.writer.close()
        } catch (e: IOException) {
            onError("could not close a trace: ${e.javaClass.name}")
        }
        val pkg = mainPackage(recording)
        val expectation = TraceExpectation(
            expectedReels = recording.expectedReels,
            app = pkg?.let { metadata.appId(it) } ?: UNKNOWN_APP,
            notes = recording.label,
            appVersionCode = pkg?.let { metadata.versionCode(it) },
            device = metadata.device,
        )
        try {
            writeExpectation(recording.name, expectation)
        } catch (e: IOException) {
            onError("could not save a trace's expectation: ${e.javaClass.name}")
        }
        _state.value = RecorderState.Idle(reason)
        reload()
        return _saved.value.firstOrNull { it.name == recording.name }
    }

    private fun publish(recording: Active) {
        _state.value = RecorderState.Recording(
            name = recording.name,
            label = recording.label,
            expectedReels = recording.expectedReels,
            startedAtMs = recording.startedAtMs,
            lines = recording.lines,
        )
    }

    private fun countPackage(recording: Active, event: TraceEvent) {
        val (counts, pkg) = when (event) {
            is TraceEvent.Scrolled -> recording.scrolledPackages to event.pkg
            is TraceEvent.Foreground -> recording.otherPackages to event.pkg
            is TraceEvent.Window -> recording.otherPackages to event.pkg
            is TraceEvent.ContentChanged -> recording.otherPackages to event.pkg
            else -> return
        }
        if (pkg != null) counts[pkg] = (counts[pkg] ?: 0) + 1
    }

    /** The tracked app the recording is about: the one scrolled most, else seen most. */
    private fun mainPackage(recording: Active): String? =
        (recording.scrolledPackages.ifEmpty { recording.otherPackages })
            .maxByOrNull { it.value }?.key

    private fun load(trace: File): SavedTrace {
        val name = trace.name.removeSuffix(TraceFormat.TRACE_EXTENSION)
        val sidecar = expectationFile(name)
        val expectation = try {
            TraceFormat.decodeExpectation(sidecar.readText())
        } catch (e: IOException) {
            null
        } catch (e: TraceFormatException) {
            null
        }
        return SavedTrace(
            name = name,
            trace = trace,
            expectation = sidecar,
            label = expectation?.notes?.ifBlank { null } ?: name,
            expectedReels = expectation?.expectedReels,
            app = expectation?.app ?: UNKNOWN_APP,
            sizeBytes = trace.length(),
            savedAtMs = trace.lastModified(),
        )
    }

    private fun uniqueName(base: String): String {
        var name = base
        var suffix = 2
        while (traceFile(name).exists() || expectationFile(name).exists()) {
            name = "$base-${suffix++}"
        }
        return name
    }

    private fun writeExpectation(name: String, expectation: TraceExpectation) {
        expectationFile(name).writeText(TraceFormat.encodeExpectation(expectation) + "\n")
    }

    private fun traceFile(name: String) = File(directory, name + TraceFormat.TRACE_EXTENSION)

    private fun expectationFile(name: String) =
        File(directory, name + TraceFormat.EXPECTATION_EXTENSION)

    companion object {
        /** SPEC §15.2: auto-stop after 5 minutes or 20 000 lines. */
        const val MAX_DURATION_MS = 5 * 60_000L
        const val MAX_LINES = 20_000
        const val UNKNOWN_APP = "unknown"
        private const val FLUSH_EVERY_LINES = 10
    }
}
