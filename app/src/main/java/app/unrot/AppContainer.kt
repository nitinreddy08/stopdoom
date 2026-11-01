package app.unrot

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import app.unrot.health.DiagnosticLog
import app.unrot.health.HealthStore
import app.unrot.health.LogEntry
import app.unrot.health.LogLevel
import app.unrot.inspector.AndroidTraceMetadata
import app.unrot.inspector.TraceRecorder
import app.unrot.rules.TrackedPackages
import app.unrot.service.ServiceStatus
import java.io.File
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency injection (ADR-011). Everything is created lazily on first use; `by lazy`
 * is thread-safe. The accessibility service and the UI share this instance (same process).
 */
class AppContainer(private val appContext: Context) {
    private val backgroundErrors = CoroutineExceptionHandler { _, t ->
        log.e(TAG, "background task failed: ${t.javaClass.name}")
    }

    /** Background work that should outlive a screen or the service. Failures are logged. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + backgroundErrors)

    val log: DiagnosticLog by lazy {
        DiagnosticLog(
            file = File(appContext.filesDir, LOG_FILE),
            scope = appScope,
            echo = if (BuildConfig.DEBUG) ::echoToLogcat else null,
        )
    }

    val health: HealthStore by lazy {
        HealthStore(
            PreferenceDataStoreFactory.create { appContext.preferencesDataStoreFile(HEALTH_STORE) },
        )
    }

    /** Package → app id from the bundled rules file; empty (and logged) if it can't be read. */
    val trackedApps: Map<String, String> by lazy {
        try {
            TrackedPackages.appsFromAssets(appContext)
        } catch (t: Throwable) {
            log.e(TAG, "could not read bundled rules: ${t.javaClass.name}")
            emptyMap()
        }
    }

    val trackedPackages: Set<String> by lazy { trackedApps.keys }

    /** Inspector recordings (SPEC §15), in `files/traces/`. */
    val traceRecorder: TraceRecorder by lazy {
        TraceRecorder(
            directory = File(appContext.filesDir, TRACES_DIR),
            metadata = AndroidTraceMetadata(appContext, trackedApps),
            scope = appScope,
            onError = { log.e("Inspector", it) },
        )
    }

    val status = ServiceStatus()

    private fun echoToLogcat(entry: LogEntry) {
        val priority = when (entry.level) {
            LogLevel.DEBUG -> Log.DEBUG
            LogLevel.INFO -> Log.INFO
            LogLevel.WARN -> Log.WARN
            LogLevel.ERROR -> Log.ERROR
        }
        Log.println(priority, "Unrot/${entry.tag}", entry.message)
    }

    private companion object {
        const val TAG = "App"
        const val LOG_FILE = "diagnostic-log.tsv"
        const val HEALTH_STORE = "health"
        const val TRACES_DIR = "traces"
    }
}
