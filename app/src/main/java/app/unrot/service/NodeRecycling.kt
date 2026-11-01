package app.unrot.service

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

// recycle() is required before API 33 and a deprecated no-op from API 33.

@Suppress("DEPRECATION")
internal fun AccessibilityWindowInfo.recycleBelowApi33() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) recycle()
}

@Suppress("DEPRECATION")
internal fun AccessibilityNodeInfo.recycleBelowApi33() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) recycle()
}
