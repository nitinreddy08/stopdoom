package app.unrot.rules

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads only the tracked package names from the bundled rules file (`rules/detection-rules.json`,
 * copied into assets by `copyRules`). The full rules repository and validator arrive in Step 05.
 */
object TrackedPackages {
    const val ASSET_NAME = "detection-rules.json"

    /** Throws if [json] is not a valid rules file. */
    fun parse(json: String): Set<String> {
        val apps = Json.parseToJsonElement(json).jsonObject.getValue("apps").jsonArray
        return apps.flatMap { app ->
            app.jsonObject.getValue("packages").jsonArray.map { it.jsonPrimitive.content }
        }.toSet()
    }

    fun fromAssets(context: Context): Set<String> =
        context.assets.open(ASSET_NAME).bufferedReader().use { parse(it.readText()) }
}
