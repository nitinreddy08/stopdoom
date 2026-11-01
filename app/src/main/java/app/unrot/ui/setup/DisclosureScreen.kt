package app.unrot.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.unrot.R

/**
 * The prominent disclosure shown before any redirect to accessibility settings (SPEC §13.3,
 * ADR-019). Nothing is pre-agreed: only "Agree and continue" opens settings. Minimal version;
 * the final copy and look come in Step 11.
 */
@Composable
fun DisclosureScreen(
    onAgree: () -> Unit,
    onNotNow: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.disclosure_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            listOf(
                R.string.disclosure_purpose,
                R.string.disclosure_reading,
                R.string.disclosure_battles,
                R.string.disclosure_turn_off,
            ).forEach { paragraph ->
                Text(text = stringResource(paragraph), style = MaterialTheme.typography.bodyLarge)
            }
            TextButton(onClick = onPrivacyPolicy) {
                Text(stringResource(R.string.disclosure_privacy))
            }
            Text(
                text = stringResource(R.string.disclosure_next_steps),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onAgree, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.disclosure_agree))
            }
            OutlinedButton(onClick = onNotNow, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.disclosure_not_now))
            }
        }
    }
}
