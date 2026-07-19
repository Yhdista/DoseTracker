package com.yhdista.dosetracker.ui.confirm

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yhdista.dosetracker.core.Data
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
    val state by viewModel.state.collectAsState()

    LaunchedEffect(doseId) {
        viewModel.setDoseId(doseId)
    }

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) onBack()
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
        when (val result = state.dose) {
            is Data.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is Data.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error: ${result.message}")
                }
            }
            is Data.Success -> {
                Column(
                    modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Medication: ${result.data.medicationName}",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    OutlinedTextField(
                        value = state.amount,
                        onValueChange = { viewModel.onEvent(ConfirmDoseEvent.UpdateAmount(it)) },
                        label = { Text("Amount (${result.data.unit ?: ""})") },
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
                        onClick = { onNavigateToMedicationDetail(result.data.medicationId) },
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
}

private val confirmTimeFormat = kotlinx.datetime.LocalDateTime.Format {
    year(); char('-'); monthNumber(); char('-'); day()
    char(' ')
    hour(); char(':'); minute()
}
