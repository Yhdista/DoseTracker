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
import com.yhdista.dosetracker.shared.resources.Res
import com.yhdista.dosetracker.shared.resources.report_empty
import com.yhdista.dosetracker.shared.resources.report_next_week
import com.yhdista.dosetracker.shared.resources.report_previous_week
import com.yhdista.dosetracker.shared.resources.report_taken_missed_skipped
import com.yhdista.dosetracker.shared.resources.report_title
import com.yhdista.dosetracker.shared.resources.report_upcoming
import com.yhdista.dosetracker.ui.common.DataContent
import kotlinx.datetime.DateTimeUnit
import org.jetbrains.compose.resources.stringResource
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
            TopAppBar(title = { Text(stringResource(Res.string.report_title), fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.onEvent(ReportEvent.PreviousWeek) }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(Res.string.report_previous_week))
                }
                Text(
                    text = "${state.weekStart.format(weekLabelFormat)} - " +
                        "${state.weekStart.plus(6, DateTimeUnit.DAY).format(weekLabelFormat)}",
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = { viewModel.onEvent(ReportEvent.NextWeek) }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = stringResource(Res.string.report_next_week))
                }
            }

            DataContent(state.summaries) { summaries ->
                if (summaries.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(Res.string.report_empty))
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
            Text(
                stringResource(
                    Res.string.report_taken_missed_skipped,
                    summary.taken.toString(),
                    summary.missed.toString(),
                    summary.skipped.toString()
                )
            )
            if (summary.upcoming > 0) {
                Text(
                    stringResource(Res.string.report_upcoming, summary.upcoming.toString()),
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
