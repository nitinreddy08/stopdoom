package app.unrot.ui.setup

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.net.toUri
import app.unrot.service.UnrotAccessibilityService

/** Where the user is in turning the service on (SPEC §13.2). */
enum class ServiceSetup { NOT_ENABLED, NOT_RUNNING, RUNNING }

/** Turning the accessibility service on (SPEC §13.2–13.5). */
object AccessibilitySetup {
    const val PRIVACY_URL = "https://github.com/nitinreddy08/stopdoom/blob/main/PRIVACY.md"
    private const val FRAGMENT_ARGS_KEY = ":settings:fragment_args_key"
    private const val SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"

    /**
     * SPEC §13.2: running when the service is connected (same process); enabled-but-not-running
     * means Android or the phone maker stopped it.
     */
    fun state(enabled: Boolean, connected: Boolean): ServiceSetup = when {
        connected -> ServiceSetup.RUNNING
        enabled -> ServiceSetup.NOT_RUNNING
        else -> ServiceSetup.NOT_ENABLED
    }

    fun isServiceEnabled(context: Context): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        val component = ComponentName(context, UnrotAccessibilityService::class.java)
        val enabled = manager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
        )
        return enabled.any {
            val info = it.resolveInfo.serviceInfo
            info.packageName == component.packageName && info.name == component.className
        }
    }

    /**
     * SPEC §13.4: opens Accessibility settings, asking Settings to highlight Unrot (not every
     * phone does). Only call after the disclosure screen (ADR-019). Returns false if it failed.
     */
    fun openAccessibilitySettings(context: Context): Boolean {
        val component =
            ComponentName(context, UnrotAccessibilityService::class.java).flattenToString()
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            putExtra(FRAGMENT_ARGS_KEY, component)
            putExtra(SHOW_FRAGMENT_ARGS, Bundle().apply { putString(FRAGMENT_ARGS_KEY, component) })
        }
        return start(context, intent)
    }

    /** SPEC §13.5: our App info page, where "Allow restricted settings" lives. */
    fun openAppInfo(context: Context): Boolean {
        val uri = Uri.fromParts("package", context.packageName, null)
        return start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
    }

    fun openPrivacyPolicy(context: Context): Boolean =
        start(context, Intent(Intent.ACTION_VIEW, PRIVACY_URL.toUri()))

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
