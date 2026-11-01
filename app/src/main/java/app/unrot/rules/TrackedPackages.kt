package app.unrot.rules

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads only the tracked apps from the bundled rules file (`rules/detection-rules.json`, copied
 * into assets by `copyRules`). The full rules repository and validator arrive in Step 05.
 */
object TrackedPackages {
    const val ASSET_NAME = "detection-rules.json"

    /** Package name → the rules' app id (e.g. "instagram"). Throws if [json] is not valid. */
    fun parseApps(json: String): Map<String, String> {
        val apps = Json.parseToJsonElement(json).jsonObject.getValue("apps").jsonArray
        return buildMap {
            apps.forEach { app ->
                val fields = app.jsonObject
                val id = fields.getValue("id").jsonPrimitive.content
                fields.getValue("packages").jsonArray.forEach { put(it.jsonPrimitive.content, id) }
            }
        }
    }

    /** Throws if [json] is not a valid rules file. */
    fun parse(json: String): Set<String> = parseApps(json).keys

    fun appsFromAssets(context: Context): Map<String, String> =
        context.assets.open(ASSET_NAME).bufferedReader().use { parseApps(it.readText()) }
}
