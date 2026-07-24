package com.yhdista.dosetracker.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yhdista.dosetracker.core.Data
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char

private const val MAX_BAR_HEIGHT_DP = 160
private const val MIN_BAR_HEIGHT_DP = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationReportScreen(
    medicationId: Long,
    viewModel: MedicationReportViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedWeek by remember { mutableStateOf<LocalDate?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.medicationName.ifEmpty { "Medication" }, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                SegmentedButton(
                    selected = state.mode == ReportRangeMode.MONTH,
                    onClick = {
                        if (state.mode != ReportRangeMode.MONTH) viewModel.onEvent(MedicationReportEvent.ToggleMode)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("Month") }
                SegmentedButton(
                    selected = state.mode == ReportRangeMode.YEAR,
                    onClick = {
                        if (state.mode != ReportRangeMode.YEAR) viewModel.onEvent(MedicationReportEvent.ToggleMode)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("Year") }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.onEvent(MedicationReportEvent.PreviousPeriod) }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Previous period")
                }
                Text(
                    text = periodLabel(state.mode, state.periodStart),
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = { viewModel.onEvent(MedicationReportEvent.NextPeriod) }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Next period")
                }
            }

            when (val result = state.weeks) {
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
                    val weeks = result.data
                    val maxTaken = weeks.maxOfOrNull { it.totalTaken } ?: 0.0
                    Text(
                        text = selectedWeek?.let { ws -> weeks.find { it.weekStart == ws } }
                            ?.let { "${it.weekStart.format(weekTickFormat)}: ${it.totalTaken} ${state.unit}" }
                            ?: " ",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        weeks.forEach { week ->
                            WeekBar(
                                week = week,
                                maxTaken = maxTaken,
                                isSelected = selectedWeek == week.weekStart,
                                onClick = {
                                    selectedWeek = if (selectedWeek == week.weekStart) null else week.weekStart
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekBar(
    week: WeekQuantity,
    maxTaken: Double,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val heightDp = if (maxTaken > 0) {
        (MIN_BAR_HEIGHT_DP + (week.totalTaken / maxTaken) * (MAX_BAR_HEIGHT_DP - MIN_BAR_HEIGHT_DP)).dp
    } else {
        MIN_BAR_HEIGHT_DP.dp
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(32.dp)
    ) {
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(MAX_BAR_HEIGHT_DP.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(heightDp)
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = if (isSelected) 1f else 0.7f)
                    )
                    .clickable(onClick = onClick)
            )
        }
        Text(
            text = week.weekStart.format(weekTickFormat),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun periodLabel(mode: ReportRangeMode, periodStart: LocalDate): String {
    return when (mode) {
        ReportRangeMode.MONTH -> periodStart.format(monthLabelFormat)
        ReportRangeMode.YEAR -> periodStart.year.toString()
    }
}

private val monthLabelFormat = LocalDate.Format {
    monthName(MonthNames.ENGLISH_FULL); char(' '); year()
}

private val weekTickFormat = LocalDate.Format {
    monthNumber(); char('/'); day()
}
