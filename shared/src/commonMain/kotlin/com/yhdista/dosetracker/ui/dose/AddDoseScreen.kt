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
import com.yhdista.dosetracker.ui.common.DataContent
import com.yhdista.dosetracker.ui.common.ObserveAsEvents
import kotlinx.datetime.format
import kotlinx.datetime.format.char

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
                title = { Text("Log Dose", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
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
                    text = "Medication: ${medication.name}",
                    style = MaterialTheme.typography.headlineSmall
                )

                OutlinedTextField(
                    value = state.amount,
                    onValueChange = { viewModel.onEvent(AddDoseEvent.UpdateAmount(it)) },
                    label = { Text("Amount (${medication.unit})") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Time: ${state.time.format(dateTimeDisplayFormat)}",
                    style = MaterialTheme.typography.bodyLarge
                )

                Button(
                    onClick = { viewModel.onEvent(AddDoseEvent.SaveDose) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Log Dose")
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
