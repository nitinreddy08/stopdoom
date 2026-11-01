package app.unrot

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.unrot.ui.debug.DebugScreen
import app.unrot.ui.home.TodayScreen
import app.unrot.ui.inspector.InspectorScreen
import app.unrot.ui.setup.AccessibilitySetup
import app.unrot.ui.setup.DisclosureScreen
import app.unrot.ui.setup.ServiceSetupCard
import app.unrot.ui.theme.UnrotTheme

private enum class Screen { HOME, DISCLOSURE, INSPECTOR }

class MainActivity : ComponentActivity() {
    /** Re-checked on every resume (SPEC §13.4). */
    private var serviceEnabled by mutableStateOf(false)

    /** True while the user is in Accessibility settings after the disclosure. */
    private var openedSettings = false

    /** SPEC §13.5: shown once the user came back from settings without turning Unrot on. */
    private var showRestrictedHelp by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openedSettings = savedInstanceState?.getBoolean(KEY_OPENED_SETTINGS) ?: false
        showRestrictedHelp = savedInstanceState?.getBoolean(KEY_RESTRICTED_HELP) ?: false
        enableEdgeToEdge()
        val container = (application as UnrotApp).container
        setContent {
            UnrotTheme {
                App(container)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        serviceEnabled = AccessibilitySetup.isServiceEnabled(this)
        if (openedSettings) {
            openedSettings = false
            // Restricted settings exist on Android 13+ only.
            val restricted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            if (!serviceEnabled && restricted) showRestrictedHelp = true
        }
        if (serviceEnabled) showRestrictedHelp = false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_OPENED_SETTINGS, openedSettings)
        outState.putBoolean(KEY_RESTRICTED_HELP, showRestrictedHelp)
    }

    @Composable
    private fun App(container: AppContainer) {
        var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
        BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }
        val connected by container.status.connected.collectAsStateWithLifecycle()

        when (screen) {
            Screen.HOME -> {
                val header: @Composable ColumnScope.() -> Unit = {
                    ServiceSetupCard(
                        setup = AccessibilitySetup.state(serviceEnabled, connected),
                        showRestrictedHelp = showRestrictedHelp,
                        onTurnOn = { screen = Screen.DISCLOSURE },
                        onOpenAppInfo = { AccessibilitySetup.openAppInfo(this@MainActivity) },
                    )
                    OutlinedButton(
                        onClick = { screen = Screen.INSPECTOR },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.inspector_open)) }
                }
                // The debug panel can never appear in a release build.
                if (BuildConfig.DEBUG) {
                    DebugScreen(container, header = header)
                } else {
                    TodayScreen(header = header)
                }
            }

            Screen.DISCLOSURE -> DisclosureScreen(
                onAgree = {
                    screen = Screen.HOME
                    openedSettings = AccessibilitySetup.openAccessibilitySettings(this)
                },
                onNotNow = { screen = Screen.HOME },
                onPrivacyPolicy = { AccessibilitySetup.openPrivacyPolicy(this) },
            )

            Screen.INSPECTOR -> InspectorScreen(
                recorder = container.traceRecorder,
                serviceRunning = connected,
                currentForeground = { container.status.foreground.value },
                trackedPackages = container.trackedPackages,
                onBack = { screen = Screen.HOME },
            )
        }
    }

    private companion object {
        const val KEY_OPENED_SETTINGS = "openedSettings"
        const val KEY_RESTRICTED_HELP = "restrictedHelp"
    }
}
