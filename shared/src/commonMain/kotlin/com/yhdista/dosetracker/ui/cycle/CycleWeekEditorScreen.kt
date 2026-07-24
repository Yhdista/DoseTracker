package com.yhdista.dosetracker.ui.cycle

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Medication
import com.yhdista.dosetracker.domain.model.ScheduleType
import com.yhdista.dosetracker.domain.model.TimeType
import com.yhdista.dosetracker.ui.common.label
import com.yhdista.dosetracker.domain.model.ReminderSchedule
import com.yhdista.dosetracker.reminder.WeekDays
import com.yhdista.dosetracker.shared.resources.Res
import com.yhdista.dosetracker.shared.resources.back
import com.yhdista.dosetracker.shared.resources.cancel
import com.yhdista.dosetracker.shared.resources.catalog_add_medication
import com.yhdista.dosetracker.shared.resources.cycle_week_number
import com.yhdista.dosetracker.shared.resources.cycleweek_empty
import com.yhdista.dosetracker.shared.resources.cycleweek_pick_medication
import com.yhdista.dosetracker.shared.resources.delete
import com.yhdista.dosetracker.shared.resources.error_prefix
import com.yhdista.dosetracker.shared.resources.schedule_every_day
import com.yhdista.dosetracker.shared.resources.schedule_every_n_days_from
import com.yhdista.dosetracker.ui.schedule.ScheduleDialog
import com.yhdista.dosetracker.ui.schedule.formatMinutes
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleWeekEditorScreen(
    cycleId: Long,
    weekIndex: Int,
    viewModel: CycleWeekEditorViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingMedicationId by remember { mutableStateOf<Long?>(null) }
    var editingSchedule by remember { mutableStateOf<ReminderSchedule?>(null) }
    var pickingMedication by remember { mutableStateOf(false) }

    val periodTimes = (state.periodTimes as? Data.Success)?.data ?: emptyMap()
    val medications = (state.medications as? Data.Success)?.data ?: emptyList()
    val medicationNames = medications.associate { it.id to it.name }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.cycle_week_number, weekIndex + 1), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { pickingMedication = true }) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(Res.string.catalog_add_medication))
            }
        }
    ) { padding ->
        if (pickingMedication) {
            MedicationPickerDialog(
                medications = medications,
                onDismiss = { pickingMedication = false },
                onPick = { medicationId ->
                    pickingMedication = false
                    pendingMedicationId = medicationId
                }
            )
        }

        pendingMedicationId?.let { medicationId ->
            ScheduleDialog(
                defaultTimeType = state.defaultTimeType,
                periodTimes = periodTimes,
                onDismiss = { pendingMedicationId = null },
                onConfirm = { minutes, days, schedType, interval, start, tType, period ->
                    viewModel.onEvent(
                        CycleWeekEditorEvent.AddSchedule(
                            medicationId = medicationId,
                            minutesOfDay = minutes,
                            daysOfWeek = days,
                            scheduleType = schedType,
                            intervalDays = interval,
                            startDate = start,
                            timeType = tType,
                            dayPeriod = period
                        )
                    )
                    pendingMedicationId = null
                }
            )
        }

        editingSchedule?.let { schedule ->
            ScheduleDialog(
                schedule = schedule,
                defaultTimeType = state.defaultTimeType,
                periodTimes = periodTimes,
                onDismiss = { editingSchedule = null },
                onConfirm = { minutes, days, schedType, interval, start, tType, period ->
                    viewModel.onEvent(
                        CycleWeekEditorEvent.UpdateSchedule(
                            schedule.copy(
                                minutesOfDay = minutes,
                                daysOfWeek = WeekDays.toBitmask(days),
                                scheduleType = schedType,
                                intervalDays = interval,
                                startDate = start,
                                timeType = tType,
                                dayPeriod = period
                            )
                        )
                    )
                    editingSchedule = null
                }
            )
        }

        when (val result = state.schedules) {
            is Data.Loading -> {
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is Data.Error -> {
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(Res.string.error_prefix, result.message))
                }
            }
            is Data.Success -> {
                if (result.data.isEmpty()) {
                    Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(Res.string.cycleweek_empty))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.padding(padding).fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(result.data, key = { it.id }) { schedule ->
                            CycleScheduleRow(
                                schedule = schedule,
                                medicationName = medicationNames[schedule.medicationId] ?: "?",
                                periodTimes = periodTimes,
                                onClick = { editingSchedule = schedule },
                                onDelete = { viewModel.onEvent(CycleWeekEditorEvent.DeleteSchedule(schedule)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CycleScheduleRow(
    schedule: ReminderSchedule,
    medicationName: String,
    periodTimes: Map<com.yhdista.dosetracker.domain.model.DayPeriod, Int>,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val timeLabel = if (schedule.timeType == TimeType.PERIOD) {
        val periodName = schedule.dayPeriod?.label ?: ""
        val minutes = schedule.dayPeriod?.let { periodTimes[it] } ?: schedule.minutesOfDay
        "$periodName (${formatMinutes(minutes)})"
    } else {
        formatMinutes(schedule.minutesOfDay)
    }
    val freqLabel = if (schedule.scheduleType == ScheduleType.INTERVAL) {
        stringResource(Res.string.schedule_every_n_days_from, schedule.intervalDays, schedule.startDate.toString())
    } else {
        val days = WeekDays.fromBitmask(schedule.daysOfWeek)
        if (days.size == 7) stringResource(Res.string.schedule_every_day) else days.joinToString { it.name.take(3) }
    }

    ListItem(
        headlineContent = { Text(medicationName, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text("$timeLabel - $freqLabel") },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = stringResource(Res.string.delete))
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun MedicationPickerDialog(
    medications: List<Medication>,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.cycleweek_pick_medication)) },
        text = {
            LazyColumn {
                items(medications, key = { it.id }) { medication ->
                    ListItem(
                        headlineContent = { Text(medication.name) },
                        modifier = Modifier.clickable { onPick(medication.id) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        }
    )
}
