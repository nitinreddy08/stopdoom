package app.unrot.ui.setup

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.unrot.R

/**
 * Shown on the home screen until the service runs: a "Turn on" card and, after a return from
 * settings without success on Android 13+, the restricted-settings help (SPEC §13.5).
 */
@Composable
fun ServiceSetupCard(
    setup: ServiceSetup,
    showRestrictedHelp: Boolean,
    onTurnOn: () -> Unit,
    onOpenAppInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (setup == ServiceSetup.RUNNING) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val notRunning = setup == ServiceSetup.NOT_RUNNING
                val title =
                    if (notRunning) R.string.setup_not_running_title else R.string.setup_off_title
                val body =
                    if (notRunning) R.string.setup_not_running_body else R.string.setup_off_body
                Text(
                    text = stringResource(title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(text = stringResource(body), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onTurnOn) { Text(stringResource(R.string.setup_turn_on)) }
            }
        }
        if (showRestrictedHelp && setup == ServiceSetup.NOT_ENABLED) {
            RestrictedSettingsCard(onOpenAppInfo)
        }
    }
}

/** SPEC §13.5: the 3-step "Allow restricted settings" guide for sideloaded installs. */
@Composable
private fun RestrictedSettingsCard(onOpenAppInfo: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.restricted_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.restricted_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Step(1, R.string.restricted_step_1)
            Step(2, R.string.restricted_step_2)
            Step(3, R.string.restricted_step_3)
            OutlinedButton(onClick = onOpenAppInfo) {
                Text(stringResource(R.string.restricted_open_app_info))
            }
        }
    }
}

@Composable
private fun Step(number: Int, @StringRes text: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(28.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = number.toString(), style = MaterialTheme.typography.labelLarge)
            }
        }
        Text(text = stringResource(text), style = MaterialTheme.typography.bodyMedium)
    }
}
