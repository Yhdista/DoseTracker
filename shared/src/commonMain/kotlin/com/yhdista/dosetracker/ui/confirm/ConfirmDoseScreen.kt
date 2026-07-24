package com.yhdista.dosetracker.ui.confirm

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
fun ConfirmDoseScreen(
    doseId: Long,
    viewModel: ConfirmDoseViewModel,
    onBack: () -> Unit,
    onNavigateToMedicationDetail: (Long) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.uiEvents) { event ->
        when (event) {
            ConfirmDoseUiEvent.Saved -> onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Confirm Dose", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        DataContent(state.dose, Modifier.padding(padding)) { dose ->
            Column(
                modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Medication: ${dose.medicationName}",
                    style = MaterialTheme.typography.headlineSmall
                )
                OutlinedTextField(
                    value = state.amount,
                    onValueChange = { viewModel.onEvent(ConfirmDoseEvent.UpdateAmount(it)) },
                    label = { Text("Amount (${dose.unit ?: ""})") },
                    modifier = Modifier.fillMaxWidth()
                )
                state.time?.let { time ->
                    Text(
                        text = "Time: ${time.format(confirmTimeFormat)}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Button(
                    onClick = { viewModel.onEvent(ConfirmDoseEvent.Save) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save")
                }
                OutlinedButton(
                    onClick = { onNavigateToMedicationDetail(dose.medicationId) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Edit Medication Schedule")
                }
                if (state.error != null) {
                    Text(text = state.error!!, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private val confirmTimeFormat = kotlinx.datetime.LocalDateTime.Format {
    year(); char('-'); monthNumber(); char('-'); day()
    char(' ')
    hour(); char(':'); minute()
}
