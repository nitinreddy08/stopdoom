package app.unrot.inspector

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

/** Phone model, Android version and app version codes for trace sidecars (SPEC §15.3). */
class AndroidTraceMetadata(private val context: Context, private val appIds: Map<String, String>) :
    TraceMetadata {
    override val device: String =
        "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} " +
            "(API ${Build.VERSION.SDK_INT})"

    override fun appId(pkg: String): String? = appIds[pkg]

    override fun versionCode(pkg: String): Long? = try {
        packageInfo(pkg).longVersionCode
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    // Visible through the manifest's <queries> for the tracked packages.
    private fun packageInfo(pkg: String): PackageInfo {
        val manager = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            manager.getPackageInfo(pkg, 0)
        }
    }
}
