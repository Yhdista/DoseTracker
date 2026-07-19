package com.yhdista.dosetracker.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yhdista.dosetracker.core.Data
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.format.char

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Dose History", fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        when (val result = state.dosesWithMeds) {
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
                val items = result.data
                if (items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No history found")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize()
                    ) {
                        items(items) { item ->
                            HistoryItem(item)
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

private val historyTimeFormat = LocalDateTime.Format {
    year(); char('-'); monthNumber(); char('-'); day()
    char(' ')
    hour(); char(':'); minute()
}

@Composable
fun HistoryItem(item: DoseWithMedication) {
    val timeStr = item.dose.timestamp.toLocalDateTime(TimeZone.currentSystemDefault()).format(historyTimeFormat)

    ListItem(
        headlineContent = { Text(item.medication?.name ?: "Unknown Medication") },
        supportingContent = {
            val amountStr = item.dose.amount?.toString() ?: item.medication?.dosage?.toString() ?: ""
            val unitStr = item.dose.unit ?: item.medication?.unit ?: ""
            Text("$amountStr $unitStr at $timeStr")
        },
        trailingContent = {
            Text(
                text = item.dose.status.name,
                style = MaterialTheme.typography.labelSmall,
                color = if (item.dose.status.name == "TAKEN") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
    )
}
