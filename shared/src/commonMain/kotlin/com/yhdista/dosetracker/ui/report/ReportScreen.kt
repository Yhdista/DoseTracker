package com.yhdista.dosetracker.ui.report

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yhdista.dosetracker.ui.common.DataContent
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.plus
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(viewModel: ReportViewModel, onMedicationClick: (Long) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Weekly Report", fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.onEvent(ReportEvent.PreviousWeek) }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Previous week")
                }
                Text(
                    text = "${state.weekStart.format(weekLabelFormat)} - " +
                        "${state.weekStart.plus(6, DateTimeUnit.DAY).format(weekLabelFormat)}",
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = { viewModel.onEvent(ReportEvent.NextWeek) }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Next week")
                }
            }

            DataContent(state.summaries) { summaries ->
                if (summaries.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No doses this week")
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(summaries) { summary ->
                            MedicationSummaryCard(summary, onClick = { onMedicationClick(summary.medicationId) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MedicationSummaryCard(summary: MedicationWeekSummary, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(summary.medicationName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Taken: ${summary.taken}  Missed: ${summary.missed}  Skipped: ${summary.skipped}")
            if (summary.upcoming > 0) {
                Text(
                    "Upcoming: ${summary.upcoming}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (summary.totalAmountScheduled > 0) {
                val percentage = (summary.totalAmountTaken / summary.totalAmountScheduled * 100).roundToInt()
                val progress = (summary.totalAmountTaken / summary.totalAmountScheduled).toFloat().coerceIn(0f, 1f)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${summary.totalAmountTaken} / ${summary.totalAmountScheduled} ${summary.unit} ($percentage%)",
                    style = MaterialTheme.typography.bodySmall
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

private val weekLabelFormat = LocalDate.Format {
    monthNumber(); char('/'); day()
}
