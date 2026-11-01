package app.unrot.ui.debug

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.unrot.AppContainer
import app.unrot.R
import app.unrot.health.LogEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val LOG_LINES = 20
private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

/**
 * Service status for testing on a phone. Shown only in debug builds (MainActivity checks
 * `BuildConfig.DEBUG`); it moves to Settings > Advanced in Step 11.
 */
@Composable
fun DebugScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val connected by container.status.connected.collectAsStateWithLifecycle()
    val foreground by container.status.foreground.collectAsStateWithLifecycle()
    val contentEvents by container.status.contentEventsEnabled.collectAsStateWithLifecycle()
    val connectedAt by container.health.connectedAt.collectAsStateWithLifecycle(initialValue = null)
    val lastAliveAt by container.health.lastAliveAt.collectAsStateWithLifecycle(initialValue = null)
    val entries by container.log.entries.collectAsStateWithLifecycle()
    val none = stringResource(R.string.debug_none)

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.debug_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            val service = if (connected) R.string.debug_connected else R.string.debug_not_connected
            StatusRow(R.string.debug_service, stringResource(service))
            StatusRow(R.string.debug_connected_since, formatTime(connectedAt) ?: none)
            StatusRow(R.string.debug_last_heartbeat, formatTime(lastAliveAt) ?: none)
            StatusRow(R.string.debug_foreground, foreground?.packageName ?: none)
            val visible = foreground?.visiblePackages?.joinToString()
            StatusRow(R.string.debug_visible_apps, visible ?: none)
            StatusRow(R.string.debug_window_bounds, foreground?.appWindowBounds?.toString() ?: none)
            val events = if (contentEvents) R.string.debug_on else R.string.debug_off
            StatusRow(R.string.debug_content_events, stringResource(events))

            Text(
                text = stringResource(R.string.debug_log_title, LOG_LINES),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            entries.takeLast(LOG_LINES).asReversed().forEach { entry ->
                Text(
                    text = formatEntry(entry),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun StatusRow(@StringRes label: Int, value: String) {
    Column {
        Text(text = stringResource(label), style = MaterialTheme.typography.labelMedium)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun formatTime(timeMs: Long?): String? = timeMs?.let {
    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(timeFormatter)
}

private fun formatEntry(entry: LogEntry): String =
    "${formatTime(entry.timeMs)} ${entry.level.name.first()} ${entry.tag}: ${entry.message}"
