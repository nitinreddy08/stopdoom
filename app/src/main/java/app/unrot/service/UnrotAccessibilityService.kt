package app.unrot.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SCROLLED
import android.view.accessibility.AccessibilityEvent.TYPE_WINDOWS_CHANGED
import android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
import app.unrot.AppContainer
import app.unrot.BuildConfig
import app.unrot.UnrotApp
import app.unrot.detection.trace.TraceEvent
import app.unrot.inspector.RateLimiter
import app.unrot.inspector.TraceRecorder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The single component that observes other apps (ADR-003). So far: lifecycle, heartbeat,
 * foreground tracking and, while the Inspector is recording, trace capture (Step 02). No counting
 * and no overlays yet.
 *
 * Every entry point is wrapped so a bug is logged instead of crashing (SPEC §3 rule 3): a crash
 * loop makes Android turn the service off.
 */
class UnrotAccessibilityService : AccessibilityService() {
    private val container: AppContainer get() = (applicationContext as UnrotApp).container
    private val clock: () -> Long = System::currentTimeMillis

    private var connected = false
    private var scope: CoroutineScope? = null
    private var refreshRequests: Channel<Unit>? = null
    private var captureInputs: Channel<CaptureInput>? = null
    private var screenReceiver: ScreenStateReceiver? = null
    private var recorder: TraceRecorder? = null
    private var trackedPackages: Set<String> = emptySet()

    /** SPEC §15.2: content-changed lines are sampled to at most 5 per second. Main thread only. */
    private val contentSampler = RateLimiter(maxEvents = 5, windowMs = 1_000)

    override fun onServiceConnected() {
        guard("onServiceConnected") {
            val c = container
            val handler = CoroutineExceptionHandler { _, t -> report("coroutine", t) }
            // ADR-021: one detector thread owns the tracking state.
            val detectorThread = Dispatchers.Default.limitedParallelism(1)
            val serviceScope = CoroutineScope(SupervisorJob() + detectorThread + handler)
            val traceCapture = TraceCapture(
                recorder = c.traceRecorder,
                inspector = NodeInspector(),
                trackedPackages = c.trackedPackages,
                status = c.status,
                scope = serviceScope,
                log = c.log,
                clock = clock,
            )
            val tracker = ForegroundTracker(
                service = this,
                trackedPackages = c.trackedPackages,
                status = c.status,
                log = c.log,
                clock = clock,
                onChange = traceCapture::onForeground,
            )
            val requests = Channel<Unit>(Channel.CONFLATED)
            // Unlimited but short-lived: only filled while recording, which stops by itself.
            val inputs = Channel<CaptureInput>(Channel.UNLIMITED)

            scope = serviceScope
            refreshRequests = requests
            captureInputs = inputs
            recorder = c.traceRecorder
            trackedPackages = c.trackedPackages
            screenReceiver = ScreenStateReceiver(c.log, ::onScreen).also { it.register(this) }
            connected = true
            c.status.connected.value = true
            c.log.i(TAG, "connected; tracking ${c.trackedPackages.size} packages")

            serviceScope.launch { c.health.recordConnected(clock()) }
            serviceScope.launch { heartbeat(c) }
            serviceScope.launch {
                requests.consumeEach { guard("refresh") { tracker.refresh() } }
            }
            serviceScope.launch {
                inputs.consumeEach { guard("capture") { traceCapture.handle(it) } }
            }
            requests.trySend(Unit) // Learn the current foreground app right away.
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        guard("onAccessibilityEvent") {
            if (event == null || !connected) return@guard
            val pkg = event.packageName?.toString()
            // SPEC §7.2: ignore our own app.
            if (pkg == packageName) return@guard
            when (event.eventType) {
                // Window events reach the tracker even without a package name (PROGRESS.md,
                // Step 01 decision): it re-reads the window list and reads no screen content.
                TYPE_WINDOW_STATE_CHANGED -> {
                    refreshRequests?.trySend(Unit)
                    recordingPackage(pkg)?.let {
                        capture(TraceEvent.Window(clock(), it, event.className?.toString()))
                    }
                }

                TYPE_WINDOWS_CHANGED -> refreshRequests?.trySend(Unit)

                // Until the detector arrives (Step 03), scrolls are only read while recording.
                TYPE_VIEW_SCROLLED -> recordingPackage(pkg)?.let { scrolled(event) }

                EventTypes.CONTENT_CHANGED -> recordingPackage(pkg)?.let {
                    val now = clock()
                    if (contentSampler.tryAcquire(now)) {
                        val cls = event.className?.toString()
                        capture(TraceEvent.ContentChanged(now, it, cls, event.contentChangeTypes))
                    }
                }
            }
        }
    }

    /** [pkg] if the Inspector is recording and it is a tracked app, else null. */
    private fun recordingPackage(pkg: String?): String? =
        pkg?.takeIf { recorder?.isRecording == true && it in trackedPackages }

    private fun scrolled(event: AccessibilityEvent) {
        val source = event.source
        val signal = SignalMapper.scrolled(event, source, clock())
        val sent = captureInputs?.trySend(CaptureInput.Scrolled(signal, source))?.isSuccess
        if (sent != true) source?.recycleBelowApi33()
    }

    private fun capture(event: TraceEvent) {
        captureInputs?.trySend(CaptureInput.Line(event))
    }

    private fun onScreen(on: Boolean) {
        if (recorder?.isRecording != true) return
        capture(if (on) TraceEvent.ScreenOn(clock()) else TraceEvent.ScreenOff(clock()))
    }

    override fun onInterrupt() {
        guard("onInterrupt") { container.log.i(TAG, "interrupted") }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        guard("onUnbind") { shutdown("unbind") }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        guard("onDestroy") { shutdown("destroy") }
        super.onDestroy()
    }

    private fun shutdown(reason: String) {
        if (!connected) return
        connected = false
        val c = container
        screenReceiver?.unregister(this)
        screenReceiver = null
        refreshRequests?.close()
        refreshRequests = null
        // Waiting snapshots die with the scope; their nodes are left to the garbage collector.
        captureInputs?.close()
        captureInputs = null
        recorder = null
        scope?.cancel()
        scope = null
        c.status.connected.value = false
        c.status.contentEventsEnabled.value = false
        c.log.i(TAG, "disconnected ($reason)")
        val now = clock()
        c.appScope.launch {
            c.health.recordDisconnected(now)
            c.log.flush()
        }
    }

    /** Writes `lastAliveAt` every 60 s while connected (SPEC §7.1 step 3). */
    private suspend fun heartbeat(c: AppContainer) {
        while (true) {
            try {
                c.health.recordAlive(clock())
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                report("heartbeat", t)
            }
            delay(HEARTBEAT_MS)
        }
    }

    private inline fun guard(where: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            report(where, t)
        }
    }

    private fun report(where: String, t: Throwable) {
        try {
            container.log.e(TAG, "$where failed: ${describe(t)}")
        } catch (ignored: Throwable) {
            // Nothing else to do; never crash.
        }
    }

    private companion object {
        const val TAG = "Service"
        const val HEARTBEAT_MS = 60_000L

        /** Exception class and top frame; the message only in debug builds (ADR-019). */
        fun describe(t: Throwable): String {
            val frame = t.stackTrace.firstOrNull()?.let { " at $it" }.orEmpty()
            val detail = if (BuildConfig.DEBUG) t.message?.let { ": $it" }.orEmpty() else ""
            return t.javaClass.name + frame + detail
        }
    }
}
