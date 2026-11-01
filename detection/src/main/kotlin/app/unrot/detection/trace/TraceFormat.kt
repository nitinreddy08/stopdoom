package app.unrot.detection.trace

import app.unrot.detection.Box
import app.unrot.detection.ChildInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

class TraceFormatException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

/**
 * Reads and writes trace lines (JSON Lines, SPEC §15.3) and `.expected.json` sidecars.
 * Field names and order follow §15.3; unknown fields are ignored when reading.
 */
object TraceFormat {
    const val TRACE_EXTENSION = ".jsonl"
    const val EXPECTATION_EXTENSION = ".expected.json"

    /** One line of JSON, without the line break. */
    fun encode(event: TraceEvent): String = when (event) {
        is TraceEvent.Scrolled -> buildJsonObject {
            put("t", event.t)
            put("e", SCROLLED)
            put("pkg", event.pkg)
            put("cls", event.cls)
            put("vid", event.vid)
            putBox("b", event.b)
            putBox("win", event.win)
            put("from", event.from)
            put("to", event.to)
            put("cnt", event.cnt)
            put("dy", event.dy)
            put("key", event.key)
        }

        is TraceEvent.Snapshot -> buildJsonObject {
            put("t", event.t)
            put("e", SNAPSHOT)
            put("key", event.key)
            put("missing", event.missing)
            put("children", JsonArray(event.children.map { it.toJson() }))
            put("sig", event.sig)
        }

        is TraceEvent.Foreground -> buildJsonObject {
            put("t", event.t)
            put("e", FOREGROUND)
            put("pkg", event.pkg)
            putBox("win", event.win)
        }

        is TraceEvent.Window -> buildJsonObject {
            put("t", event.t)
            put("e", WINDOW)
            put("pkg", event.pkg)
            put("cls", event.cls)
        }

        is TraceEvent.ContentChanged -> buildJsonObject {
            put("t", event.t)
            put("e", CONTENT_CHANGED)
            put("pkg", event.pkg)
            put("cls", event.cls)
            put("ct", event.types)
        }

        is TraceEvent.ScreenOff -> buildJsonObject {
            put("t", event.t)
            put("e", SCREEN_OFF)
        }

        is TraceEvent.ScreenOn -> buildJsonObject {
            put("t", event.t)
            put("e", SCREEN_ON)
        }
    }.toString()

    /** Throws [TraceFormatException] for a malformed line or an unknown event type. */
    fun decode(line: String): TraceEvent = parse(line) { obj ->
        val t = obj.long("t")
        when (val type = obj.string("e")) {
            SCROLLED -> TraceEvent.Scrolled(
                t = t,
                pkg = obj.string("pkg"),
                cls = obj.stringOrNull("cls"),
                vid = obj.stringOrNull("vid"),
                b = obj.box("b"),
                win = obj.boxOrNull("win"),
                from = obj.int("from"),
                to = obj.int("to"),
                cnt = obj.int("cnt"),
                dy = obj.intOrNull("dy"),
                key = obj.string("key"),
            )

            SNAPSHOT -> TraceEvent.Snapshot(
                t = t,
                key = obj.string("key"),
                missing = obj.boolean("missing"),
                children = obj.array("children").map { it.toChild() },
                sig = obj.stringOrNull("sig"),
            )

            FOREGROUND -> TraceEvent.Foreground(t, obj.stringOrNull("pkg"), obj.box("win"))

            WINDOW -> TraceEvent.Window(t, obj.string("pkg"), obj.stringOrNull("cls"))

            CONTENT_CHANGED ->
                TraceEvent.ContentChanged(
                    t,
                    obj.string("pkg"),
                    obj.stringOrNull("cls"),
                    obj.int("ct"),
                )

            SCREEN_OFF -> TraceEvent.ScreenOff(t)

            SCREEN_ON -> TraceEvent.ScreenOn(t)

            else -> throw TraceFormatException("unknown event type: $type")
        }
    }

    fun encodeExpectation(expectation: TraceExpectation): String = buildJsonObject {
        put("expectedReels", expectation.expectedReels)
        put("app", expectation.app)
        put("notes", expectation.notes)
        put("appVersionCode", expectation.appVersionCode)
        put("device", expectation.device)
        expectation.tolerance?.let { put("tolerance", it) }
    }.toString()

    /** Throws [TraceFormatException] if [json] is not a valid sidecar. */
    fun decodeExpectation(json: String): TraceExpectation = parse(json) { obj ->
        TraceExpectation(
            expectedReels = obj.int("expectedReels"),
            app = obj.string("app"),
            notes = obj.stringOrNull("notes").orEmpty(),
            appVersionCode = obj.longOrNull("appVersionCode"),
            device = obj.stringOrNull("device").orEmpty(),
            tolerance = obj.intOrNull("tolerance"),
        )
    }

    private const val SCROLLED = "SCROLLED"
    private const val SNAPSHOT = "SNAPSHOT"
    private const val FOREGROUND = "FOREGROUND"
    private const val WINDOW = "WINDOW"
    private const val CONTENT_CHANGED = "CONTENT_CHANGED"
    private const val SCREEN_OFF = "SCREEN_OFF"
    private const val SCREEN_ON = "SCREEN_ON"

    private inline fun <T> parse(text: String, read: (JsonObject) -> T): T {
        try {
            return read(Json.parseToJsonElement(text).jsonObject)
        } catch (e: TraceFormatException) {
            throw e
        } catch (e: IllegalArgumentException) {
            // Covers kotlinx.serialization's parse errors and wrong JSON element types.
            throw TraceFormatException("malformed trace JSON: ${e.message}", e)
        }
    }

    private fun Box.toJson(): JsonArray = JsonArray(listOf(l, t, r, b).map { JsonPrimitive(it) })

    private fun JsonObjectBuilder.putBox(key: String, box: Box?) {
        put(key, box?.toJson() ?: JsonNull)
    }

    private fun ChildInfo.toJson(): JsonObject = buildJsonObject {
        put("b", bounds.toJson())
        put("cls", className)
    }

    private fun JsonElement.toChild(): ChildInfo {
        val obj = this as? JsonObject ?: throw TraceFormatException("child is not an object")
        return ChildInfo(obj.box("b"), obj.stringOrNull("cls"))
    }

    private fun JsonElement.toBox(key: String): Box {
        val values = (this as? JsonArray)?.map { (it as? JsonPrimitive)?.intOrNull }
        if (values == null || values.size != 4 || values.any { it == null }) {
            throw TraceFormatException("$key must be [left, top, right, bottom]")
        }
        return Box(values[0]!!, values[1]!!, values[2]!!, values[3]!!)
    }

    private fun JsonObject.primitive(key: String): JsonPrimitive? = get(key) as? JsonPrimitive

    private fun JsonObject.long(key: String): Long =
        primitive(key)?.longOrNull ?: throw TraceFormatException("$key must be a number")

    private fun JsonObject.longOrNull(key: String): Long? = primitive(key)?.longOrNull

    private fun JsonObject.int(key: String): Int =
        primitive(key)?.intOrNull ?: throw TraceFormatException("$key must be a whole number")

    private fun JsonObject.intOrNull(key: String): Int? = primitive(key)?.intOrNull

    private fun JsonObject.boolean(key: String): Boolean =
        primitive(key)?.booleanOrNull ?: throw TraceFormatException("$key must be true or false")

    private fun JsonObject.string(key: String): String =
        stringOrNull(key) ?: throw TraceFormatException("$key must be a string")

    private fun JsonObject.stringOrNull(key: String): String? =
        primitive(key)?.takeIf { it.isString }?.content

    private fun JsonObject.box(key: String): Box =
        get(key)?.toBox(key) ?: throw TraceFormatException("$key is missing")

    private fun JsonObject.boxOrNull(key: String): Box? =
        get(key)?.takeIf { it != JsonNull }?.toBox(key)

    private fun JsonObject.array(key: String): JsonArray =
        get(key) as? JsonArray ?: throw TraceFormatException("$key must be a list")
}
