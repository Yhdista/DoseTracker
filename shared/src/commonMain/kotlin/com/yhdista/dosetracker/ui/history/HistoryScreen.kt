package com.yhdista.dosetracker.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yhdista.dosetracker.shared.resources.Res
import com.yhdista.dosetracker.shared.resources.history_amount_at_time
import com.yhdista.dosetracker.shared.resources.history_empty
import com.yhdista.dosetracker.shared.resources.history_title
import com.yhdista.dosetracker.shared.resources.history_unknown_medication
import com.yhdista.dosetracker.ui.common.DataContent
import kotlinx.datetime.LocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.format.char

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(Res.string.history_title), fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        DataContent(state.dosesWithMeds, Modifier.padding(padding)) { items ->
            if (items.isEmpty()) {
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(Res.string.history_empty))
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

private val historyTimeFormat = LocalDateTime.Format {
    year(); char('-'); monthNumber(); char('-'); day()
    char(' ')
    hour(); char(':'); minute()
}

@Composable
fun HistoryItem(item: DoseWithMedication) {
    val timeStr = item.dose.timestamp.toLocalDateTime(TimeZone.currentSystemDefault()).format(historyTimeFormat)

    ListItem(
        headlineContent = { Text(item.medication?.name ?: stringResource(Res.string.history_unknown_medication)) },
        supportingContent = {
            val amountStr = item.dose.amount?.toString() ?: item.medication?.dosage?.toString() ?: ""
            val unitStr = item.dose.unit ?: item.medication?.unit ?: ""
            Text(stringResource(Res.string.history_amount_at_time, amountStr, unitStr, timeStr))
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
