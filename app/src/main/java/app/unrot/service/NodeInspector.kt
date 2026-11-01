package app.unrot.service

import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import app.unrot.detection.Box
import app.unrot.detection.ChildInfo

/**
 * The settle-time snapshot of a scrolling container (SPEC §7.8). Runs on the detector thread.
 * Reads bounds and class names of up to [MAX_CHILDREN] direct children and, when asked, the
 * [CenterSignature] of the child at the container's center. Keeps no node references: every node
 * it obtains is recycled before it returns (the caller owns [snapshot]'s `container`).
 */
class NodeInspector(private val uptimeMs: () -> Long = SystemClock::uptimeMillis) {
    data class Result(
        val missing: Boolean,
        val children: List<ChildInfo>,
        val signature: String?,
        /** True if the 30 ms budget ran out (SPEC §7.8 step 4: `SNAPSHOT_SLOW`). */
        val slow: Boolean,
    )

    fun snapshot(container: AccessibilityNodeInfo, withSignature: Boolean): Result {
        val start = uptimeMs()
        if (!container.refresh()) return MISSING
        val bounds = container.boxInScreen()
        val nodes = ArrayList<AccessibilityNodeInfo>(MAX_CHILDREN)
        try {
            var slow = false
            for (i in 0 until minOf(container.childCount, MAX_CHILDREN)) {
                if (overBudget(start)) {
                    slow = true
                    break
                }
                container.getChild(i)?.let { nodes += it }
            }
            val boxes = nodes.map { it.boxInScreen() }
            val children = nodes.mapIndexed { i, node ->
                ChildInfo(boxes[i], node.className?.toString())
            }
            var signature: String? = null
            if (withSignature && !slow) {
                val center = CenterSignature.centerChild(bounds, boxes)?.let { nodes[it] }
                if (center != null) {
                    signature = signature(center, start)
                    slow = signature == null
                }
            }
            return Result(missing = false, children = children, signature = signature, slow = slow)
        } finally {
            nodes.forEach { it.recycleBelowApi33() }
        }
    }

    /**
     * Walks the center child's subtree breadth-first (depth ≤ 3, at most 20 nodes) collecting
     * text in memory only (ADR-019). Returns null if the time budget runs out.
     */
    private fun signature(center: AccessibilityNodeInfo, start: Long): String? {
        val text = CenterSignature.TextCollector()
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        val obtained = ArrayList<AccessibilityNodeInfo>()
        queue.addLast(center to 0)
        var visited = 0
        try {
            while (queue.isNotEmpty() && visited < CenterSignature.MAX_NODES && !text.isFull) {
                if (overBudget(start)) return null
                val (node, depth) = queue.removeFirst()
                visited++
                text.add(node.text)
                text.add(node.contentDescription)
                if (depth >= CenterSignature.MAX_DEPTH) continue
                for (i in 0 until node.childCount) {
                    if (visited + queue.size >= CenterSignature.MAX_NODES) break
                    val child = node.getChild(i) ?: continue
                    obtained += child
                    queue.addLast(child to depth + 1)
                }
            }
            return CenterSignature.hash(center.className?.toString(), center.childCount, text)
        } finally {
            obtained.forEach { it.recycleBelowApi33() }
        }
    }

    private fun overBudget(start: Long) = uptimeMs() - start > BUDGET_MS

    private fun AccessibilityNodeInfo.boxInScreen(): Box {
        val rect = Rect()
        getBoundsInScreen(rect)
        return Box(rect.left, rect.top, rect.right, rect.bottom)
    }

    companion object {
        const val MAX_CHILDREN = 6
        const val BUDGET_MS = 30L
        val MISSING = Result(missing = true, children = emptyList(), signature = null, slow = false)
    }
}
