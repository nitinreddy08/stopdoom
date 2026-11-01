package app.unrot.ui.inspector

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.unrot.R
import app.unrot.inspector.RecorderState
import app.unrot.inspector.SavedTrace
import app.unrot.inspector.StopReason
import app.unrot.inspector.TracePresets
import app.unrot.inspector.TraceRecorder
import app.unrot.inspector.TraceSharing
import app.unrot.service.ForegroundState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_EXPECTED = 1_000
private val savedAtFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM HH:mm")

/**
 * The Inspector (SPEC §15): record what tracked apps look like to the accessibility service,
 * then share the recordings. Reached from the home screen until Settings exists (Step 11).
 */
@Composable
fun InspectorScreen(
    recorder: TraceRecorder,
    serviceRunning: Boolean,
    currentForeground: () -> ForegroundState?,
    trackedPackages: Set<String>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by recorder.state.collectAsStateWithLifecycle()
    val saved by recorder.saved.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var label by rememberSaveable { mutableStateOf("") }
    var expected by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<SavedTrace?>(null) }

    LaunchedEffect(recorder) { withContext(Dispatchers.IO) { recorder.reload() } }

    val savedTo = stringResource(R.string.inspector_saved_to)
    val saveFailed = stringResource(R.string.inspector_save_failed)
    val shareSubject = stringResource(R.string.inspector_share_subject)
    val shareAllSubject = stringResource(R.string.inspector_share_all_subject)
    val chooserTitle = stringResource(R.string.inspector_share_chooser)

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.inspector_back)) }
                Text(
                    text = stringResource(R.string.inspector_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
            }
            WarningCard()

            when (val current = state) {
                is RecorderState.Recording -> RecordingCard(
                    recording = current,
                    onStop = { scope.launch(Dispatchers.IO) { recorder.stop() } },
                )

                is RecorderState.Idle -> {
                    current.lastStop?.let { StopMessage(it) }
                    if (!serviceRunning) {
                        Text(
                            text = stringResource(R.string.inspector_service_needed),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    StartForm(
                        label = label,
                        expected = expected,
                        canStart = serviceRunning,
                        onLabel = { label = it },
                        onExpected = { expected = it.filter(Char::isDigit).take(4) },
                        onStart = { count ->
                            val first = listOfNotNull(
                                currentForeground()?.toTraceEvent(trackedPackages),
                            )
                            scope.launch(Dispatchers.IO) { recorder.start(label, count, first) }
                        },
                    )
                }
            }

            Text(
                text = stringResource(R.string.inspector_saved_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            message?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
            if (saved.isEmpty()) {
                Text(stringResource(R.string.inspector_none))
            } else {
                Button(
                    onClick = {
                        scope.launch {
                            val zip = zipAll(recorder, context)
                            if (zip != null) {
                                TraceSharing.shareZip(context, zip, shareAllSubject, chooserTitle)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.inspector_share_all)) }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            message = withContext(Dispatchers.IO) {
                                val zip = zipAll(recorder, context)
                                if (zip != null && TraceSharing.saveToDownloads(context, zip)) {
                                    savedTo.format(zip.name)
                                } else {
                                    saveFailed
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.inspector_save_all)) }
                saved.forEach { trace ->
                    SavedTraceCard(
                        trace = trace,
                        onShare = {
                            TraceSharing.share(
                                context,
                                trace,
                                shareSubject.format(trace.label),
                                chooserTitle,
                            )
                        },
                        onDelete = { toDelete = trace },
                    )
                }
            }
        }
    }

    toDelete?.let { trace ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.inspector_delete_title)) },
            text = { Text(trace.label) },
            confirmButton = {
                TextButton(
                    onClick = {
                        toDelete = null
                        scope.launch(Dispatchers.IO) { recorder.delete(trace.name) }
                    },
                ) { Text(stringResource(R.string.inspector_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) {
                    Text(stringResource(R.string.inspector_cancel))
                }
            },
        )
    }
}

private suspend fun zipAll(recorder: TraceRecorder, context: Context) =
    withContext(Dispatchers.IO) {
        val zip = TraceSharing.newZipFile(context)
        zip.takeIf { recorder.zipAll(it) }
    }

@Composable
private fun WarningCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.inspector_warning),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun StopMessage(reason: StopReason) {
    val text = when (reason) {
        StopReason.USER -> return
        StopReason.TIME_LIMIT -> R.string.inspector_stopped_time
        StopReason.LINE_LIMIT -> R.string.inspector_stopped_lines
        StopReason.ERROR -> R.string.inspector_stopped_error
    }
    Text(text = stringResource(text), color = MaterialTheme.colorScheme.error)
}

@Composable
private fun StartForm(
    label: String,
    expected: String,
    canStart: Boolean,
    onLabel: (String) -> Unit,
    onExpected: (String) -> Unit,
    onStart: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.inspector_presets),
            style = MaterialTheme.typography.titleSmall,
        )
        TracePresets.ALL.forEach { preset ->
            TextButton(
                onClick = {
                    onLabel(preset.label)
                    onExpected(preset.expectedReels.toString())
                },
            ) { Text("${preset.label} (${preset.expectedReels})") }
        }
        OutlinedTextField(
            value = label,
            onValueChange = onLabel,
            label = { Text(stringResource(R.string.inspector_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = expected,
            onValueChange = onExpected,
            label = { Text(stringResource(R.string.inspector_expected)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        val count = expected.toIntOrNull()?.takeIf { it in 0..MAX_EXPECTED }
        Button(
            onClick = { count?.let(onStart) },
            enabled = canStart && label.isNotBlank() && count != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.inspector_start)) }
    }
}

@Composable
private fun RecordingCard(recording: RecorderState.Recording, onStop: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(recording.name) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val seconds = ((now - recording.startedAtMs) / 1_000).coerceAtLeast(0)
    val elapsed = String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.inspector_recording, recording.label),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(
                    R.string.inspector_recording_details,
                    recording.expectedReels,
                    recording.lines,
                    elapsed,
                ),
            )
            Text(stringResource(R.string.inspector_recording_hint))
            Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.inspector_stop))
            }
        }
    }
}

@Composable
private fun SavedTraceCard(trace: SavedTrace, onShare: () -> Unit, onDelete: () -> Unit) {
    val savedAt = Instant.ofEpochMilli(trace.savedAtMs)
        .atZone(ZoneId.systemDefault())
        .format(savedAtFormatter)
    val size = String.format(Locale.ROOT, "%.1f KB", trace.sizeBytes / 1024.0)
    val expected = trace.expectedReels?.toString() ?: stringResource(R.string.inspector_unknown)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = trace.label, style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(
                    R.string.inspector_saved_details,
                    trace.app,
                    expected,
                    size,
                    savedAt,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onShare) { Text(stringResource(R.string.inspector_share)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.inspector_delete)) }
            }
        }
    }
}
