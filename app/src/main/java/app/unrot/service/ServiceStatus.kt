package app.unrot.service

import kotlinx.coroutines.flow.MutableStateFlow

/** Live service state shared with the UI (same process). Written by the service only. */
class ServiceStatus {
    val connected = MutableStateFlow(false)
    val foreground = MutableStateFlow<ForegroundState?>(null)
    val contentEventsEnabled = MutableStateFlow(false)
}
