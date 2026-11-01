package app.unrot.detection

/** A rectangle in screen pixels (SPEC §7.4). */
data class Box(val l: Int, val t: Int, val r: Int, val b: Int) {
    val w: Int get() = r - l
    val h: Int get() = b - t
    val centerX: Int get() = l + w / 2
    val centerY: Int get() = t + h / 2

    fun contains(x: Int, y: Int): Boolean = x in l until r && y in t until b
}

/** One direct child of a scrolling container, read at settle time (SPEC §7.8). */
data class ChildInfo(val bounds: Box, val className: String?)

/**
 * Plain, immutable inputs to the detector (SPEC §7.4, ADR-005). The service turns each
 * accessibility event into one of these straight away and never keeps the event itself.
 *
 * `RulesChanged` (§7.4) arrives in Step 03 together with the rules model.
 */
sealed interface Signal {
    val nowMs: Long

    /** [pkg] is null when the app in front is not a tracked app. */
    data class Foreground(
        val pkg: String?,
        val appWindow: Box,
        val visiblePkgs: Set<String>,
        override val nowMs: Long,
    ) : Signal

    data class Scrolled(
        val pkg: String,
        val className: String?,
        val viewId: String?,
        val bounds: Box,
        val fromIndex: Int,
        val toIndex: Int,
        val itemCount: Int,
        val deltaY: Int?,
        val containerKey: String,
        override val nowMs: Long,
    ) : Signal

    data class SettleSnapshot(
        val containerKey: String,
        val children: List<ChildInfo>,
        val centerSignature: String?,
        override val nowMs: Long,
    ) : Signal

    data class Tick(override val nowMs: Long) : Signal

    data class ScreenOff(override val nowMs: Long) : Signal

    data class ScreenOn(override val nowMs: Long) : Signal
}
