package app.unrot.detection.trace

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Checks every committed trace in `src/test/resources/traces/` (ADR-005): each line parses and
 * each trace has a valid `.expected.json`. Counting them is Step 03 (`TraceReplayTest`).
 */
class RecordedTracesTest {
    private val directory: File = File(
        requireNotNull(javaClass.getResource("/traces/README.md")) {
            "traces folder is missing from the test resources"
        }.toURI(),
    ).parentFile

    @TestFactory
    fun `committed traces are well formed`(): List<DynamicTest> {
        val traces = directory.walkTopDown()
            .filter { it.isFile && it.name.endsWith(TraceFormat.TRACE_EXTENSION) }
            .sortedBy { it.path }
            .toList()
        return traces.map { trace -> DynamicTest.dynamicTest(trace.name) { check(trace) } }
    }

    private fun check(trace: File) {
        val base = trace.name.removeSuffix(TraceFormat.TRACE_EXTENSION)
        val sidecar = File(trace.parentFile, base + TraceFormat.EXPECTATION_EXTENSION)
        assertTrue(sidecar.isFile, "missing ${sidecar.name}")
        TraceFormat.decodeExpectation(sidecar.readText())
        var lines = 0
        trace.useLines { all ->
            all.filter { it.isNotBlank() }.forEachIndexed { index, line ->
                try {
                    TraceFormat.decode(line)
                } catch (e: TraceFormatException) {
                    throw AssertionError("${trace.name} line ${index + 1}: ${e.message}", e)
                }
                lines++
            }
        }
        assertTrue(lines > 0, "${trace.name} is empty")
    }
}
