package app.unrot.detection.trace

import app.unrot.detection.Box
import app.unrot.detection.ChildInfo
import app.unrot.detection.Signal

/**
 * One line of an Inspector trace (SPEC §15.3). Traces hold screen structure only: package and
 * class names, view ids, bounds, list indexes and a hash ([Snapshot.sig]). Never text.
 *
 * Line types beyond the §15.3 examples (PROGRESS.md, Step 02): [Window], [ContentChanged] and
 * [ScreenOn].
 */
sealed interface TraceEvent {
    /** Wall-clock time in milliseconds. */
    val t: Long

    /** A scroll event from a tracked app. [win] is the app window at that moment, if known. */
    data class Scrolled(
        override val t: Long,
        val pkg: String,
        val cls: String?,
        val vid: String?,
        val b: Box,
        val win: Box?,
        val from: Int,
        val to: Int,
        val cnt: Int,
        val dy: Int?,
        val key: String,
    ) : TraceEvent {
        fun toSignal(): Signal.Scrolled =
            Signal.Scrolled(pkg, cls, vid, b, from, to, cnt, dy, key, t)

        companion object {
            fun of(signal: Signal.Scrolled, window: Box?): Scrolled = Scrolled(
                t = signal.nowMs,
                pkg = signal.pkg,
                cls = signal.className,
                vid = signal.viewId,
                b = signal.bounds,
                win = window,
                from = signal.fromIndex,
                to = signal.toIndex,
                cnt = signal.itemCount,
                dy = signal.deltaY,
                key = signal.containerKey,
            )
        }
    }

    /** The container's children once scrolling has settled (SPEC §7.8). */
    data class Snapshot(
        override val t: Long,
        val key: String,
        val missing: Boolean,
        val children: List<ChildInfo>,
        val sig: String?,
    ) : TraceEvent {
        fun toSignal(): Signal.SettleSnapshot = Signal.SettleSnapshot(key, children, sig, t)
    }

    /** The app in front changed. [pkg] is null for any app that is not tracked. */
    data class Foreground(override val t: Long, val pkg: String?, val win: Box) : TraceEvent {
        fun toSignal(): Signal.Foreground = Signal.Foreground(pkg, win, setOfNotNull(pkg), t)
    }

    /** A window-state event from a tracked app (a screen, dialog or sheet opened). */
    data class Window(override val t: Long, val pkg: String, val cls: String?) : TraceEvent

    /** A content-changed event from a tracked app (sampled, at most 5 per second). */
    data class ContentChanged(
        override val t: Long,
        val pkg: String,
        val cls: String?,
        val types: Int,
    ) : TraceEvent

    data class ScreenOff(override val t: Long) : TraceEvent

    data class ScreenOn(override val t: Long) : TraceEvent
}

/**
 * The `name.expected.json` sidecar of a trace (SPEC §15.3). [tolerance] is only set by hand,
 * with the reason in [notes] (SPEC §15.4).
 */
data class TraceExpectation(
    val expectedReels: Int,
    val app: String,
    val notes: String,
    val appVersionCode: Long?,
    val device: String,
    val tolerance: Int? = null,
)
