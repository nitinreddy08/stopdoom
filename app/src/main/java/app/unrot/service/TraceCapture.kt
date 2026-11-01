package app.unrot.service

import android.view.accessibility.AccessibilityNodeInfo
import app.unrot.detection.Signal
import app.unrot.detection.trace.TraceEvent
import app.unrot.health.DiagnosticLog
import app.unrot.inspector.TraceRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/** Work handed from the service's main thread to the detector thread while recording. */
sealed interface CaptureInput {
    /** [node] is the scrolled container (`event.source`), owned by the capture from now on. */
    class Scrolled(val signal: Signal.Scrolled, val node: AccessibilityNodeInfo?) : CaptureInput

    class Line(val event: TraceEvent) : CaptureInput
}

/**
 * Feeds the Inspector recorder (SPEC §15.2) on the detector thread (ADR-021): writes each event,
 * and once a container has been still for [SETTLE_MS] after a scroll, writes its settle
 * snapshot (SPEC §7.8). Holds a container node only until its snapshot is taken.
 */
class TraceCapture(
    private val recorder: TraceRecorder,
    private val inspector: NodeInspector,
    private val trackedPackages: Set<String>,
    private val status: ServiceStatus,
    private val scope: CoroutineScope,
    private val log: DiagnosticLog,
    private val clock: () -> Long,
) {
    private class Pending(val node: AccessibilityNodeInfo?, val job: Job) {
        fun cancel() {
            job.cancel()
            node?.recycleBelowApi33()
        }
    }

    /** Containers waiting for their settle snapshot, oldest first. */
    private val pending = LinkedHashMap<String, Pending>()

    fun handle(input: CaptureInput) {
        when (input) {
            is CaptureInput.Scrolled -> onScrolled(input.signal, input.node)
            is CaptureInput.Line -> if (recorder.isRecording) recorder.record(input.event)
        }
    }

    fun onForeground(state: ForegroundState) {
        if (recorder.isRecording) recorder.record(state.toTraceEvent(trackedPackages))
    }

    /** Drops every waiting snapshot and its node. */
    fun clear() {
        pending.values.forEach { it.cancel() }
        pending.clear()
    }

    private fun onScrolled(signal: Signal.Scrolled, node: AccessibilityNodeInfo?) {
        if (!recorder.isRecording) {
            node?.recycleBelowApi33()
            clear()
            return
        }
        val window = status.foreground.value?.appWindowBounds?.toBox()
        recorder.record(TraceEvent.Scrolled.of(signal, window))

        val key = signal.containerKey
        pending.remove(key)?.cancel()
        while (pending.size >= MAX_PENDING) {
            pending.remove(pending.keys.first())?.cancel()
        }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            delay(SETTLE_MS)
            settle(key, coroutineContext.job)
        }
        pending[key] = Pending(node, job)
        job.start()
    }

    private fun settle(key: String, job: Job) {
        val entry = pending[key]?.takeIf { it.job === job } ?: return
        pending.remove(key)
        try {
            val result = entry.node?.let { inspector.snapshot(it, withSignature = true) }
                ?: NodeInspector.MISSING
            if (result.slow) log.w(TAG, "SNAPSHOT_SLOW ${result.children.size} children")
            val snapshot = TraceEvent.Snapshot(
                t = clock(),
                key = key,
                missing = result.missing,
                children = result.children,
                sig = result.signature,
            )
            recorder.record(snapshot)
        } finally {
            entry.node?.recycleBelowApi33()
        }
    }

    companion object {
        /**
         * Quiet time after the last scroll before the snapshot: the rules' default `settleMs`.
         * From Step 03 the detector asks for snapshots with each surface's own value.
         */
        const val SETTLE_MS = 300L
        private const val MAX_PENDING = 4
        private const val TAG = "Inspector"
    }
}
