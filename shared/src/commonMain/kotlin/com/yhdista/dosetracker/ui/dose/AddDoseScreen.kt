package com.yhdista.dosetracker.ui.dose

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import com.yhdista.dosetracker.shared.resources.Res
import com.yhdista.dosetracker.shared.resources.adddose_amount_label
import com.yhdista.dosetracker.shared.resources.adddose_medication
import com.yhdista.dosetracker.shared.resources.adddose_time
import com.yhdista.dosetracker.shared.resources.adddose_title
import com.yhdista.dosetracker.shared.resources.back
import com.yhdista.dosetracker.ui.common.DataContent
import com.yhdista.dosetracker.ui.common.ObserveAsEvents
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDoseScreen(
    medicationId: Long,
    viewModel: AddDoseViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.uiEvents) { event ->
        when (event) {
            AddDoseUiEvent.Saved -> onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.adddose_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                }
            )
        }
    ) { padding ->
        DataContent(state.medication, Modifier.padding(padding)) { medication ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(Res.string.adddose_medication, medication.name),
                    style = MaterialTheme.typography.headlineSmall
                )

                OutlinedTextField(
                    value = state.amount,
                    onValueChange = { viewModel.onEvent(AddDoseEvent.UpdateAmount(it)) },
                    label = { Text(stringResource(Res.string.adddose_amount_label, medication.unit)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(Res.string.adddose_time, state.time.format(dateTimeDisplayFormat)),
                    style = MaterialTheme.typography.bodyLarge
                )

                Button(
                    onClick = { viewModel.onEvent(AddDoseEvent.SaveDose) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(Res.string.adddose_title))
                }

                if (state.error != null) {
                    Text(
                        text = state.error!!,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

private val dateTimeDisplayFormat = kotlinx.datetime.LocalDateTime.Format {
    year(); char('-'); monthNumber(); char('-'); day()
    char(' ')
    hour(); char(':'); minute()
}
