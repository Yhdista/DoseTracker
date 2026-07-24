package com.yhdista.dosetracker.ui.medicationdetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.DayPeriod
import com.yhdista.dosetracker.domain.model.ReminderSchedule
import com.yhdista.dosetracker.domain.model.ScheduleType
import com.yhdista.dosetracker.domain.model.TimeType
import com.yhdista.dosetracker.ui.common.label
import com.yhdista.dosetracker.reminder.WeekDays
import com.yhdista.dosetracker.ui.common.DataContent
import com.yhdista.dosetracker.ui.schedule.ScheduleDialog
import com.yhdista.dosetracker.ui.schedule.formatMinutes
import kotlin.time.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MedicationDetailScreen(
    medicationId: Long,
    viewModel: MedicationDetailViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingSchedule by remember { mutableStateOf<ReminderSchedule?>(null) }
    var showPeriodSettings by remember { mutableStateOf(false) }

    val periodTimes = (state.periodTimes as? Data.Success)?.data ?: emptyMap()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text((state.medication as? Data.Success)?.data?.name ?: "Medication", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showPeriodSettings = true }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Period settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "Add reminder")
            }
        }
    ) { padding ->
        if (showPeriodSettings) {
            PeriodSettingsDialog(
                currentTimes = periodTimes,
                onDismiss = { showPeriodSettings = false },
                onSavePeriod = { period, minutes ->
                    viewModel.onEvent(MedicationDetailEvent.UpdatePeriodTime(period, minutes))
                }
            )
        }

        if (showAddDialog) {
            ScheduleDialog(
                defaultTimeType = state.defaultTimeType,
                periodTimes = periodTimes,
                onDismiss = { showAddDialog = false },
                onConfirm = { minutes, days, schedType, interval, start, tType, period ->
                    viewModel.onEvent(
                        MedicationDetailEvent.AddSchedule(
                            minutesOfDay = minutes,
                            daysOfWeek = days,
                            scheduleType = schedType,
                            intervalDays = interval,
                            startDate = start,
                            timeType = tType,
                            dayPeriod = period
                        )
                    )
                    showAddDialog = false
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
                        MedicationDetailEvent.UpdateSchedule(
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

        DataContent(state.schedules, Modifier.padding(padding)) { schedules ->
            if (schedules.isEmpty()) {
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No reminders yet")
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(schedules, key = { it.id }) { schedule ->
                        ScheduleRow(
                            schedule = schedule,
                            periodTimes = periodTimes,
                            onClick = { editingSchedule = schedule },
                            onDelete = { viewModel.onEvent(MedicationDetailEvent.DeleteSchedule(schedule)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleRow(
    schedule: ReminderSchedule,
    periodTimes: Map<DayPeriod, Int>,
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
        "Every ${schedule.intervalDays} days (from ${schedule.startDate})"
    } else {
        val days = WeekDays.fromBitmask(schedule.daysOfWeek)
        if (days.size == 7) "Every day" else days.joinToString { it.name.take(3) }
    }

    ListItem(
        headlineContent = { Text(timeLabel) },
        supportingContent = { Text(freqLabel) },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete reminder")
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}


@Composable
fun PeriodSettingsDialog(
    currentTimes: Map<DayPeriod, Int>,
    onDismiss: () -> Unit,
    onSavePeriod: (DayPeriod, Int) -> Unit
) {
    var editingPeriod by remember { mutableStateOf<DayPeriod?>(null) }
    
    if (editingPeriod != null) {
        val period = editingPeriod!!
        val minutes = currentTimes[period] ?: 480
        PeriodTimePickerDialog(
            periodName = period.label,
            initialMinutes = minutes,
            onDismiss = { editingPeriod = null },
            onConfirm = { newMinutes ->
                onSavePeriod(period, newMinutes)
                editingPeriod = null
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Period Notification Times") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DayPeriod.entries.forEach { key ->
                    val minutes = currentTimes[key] ?: 0
                    ListItem(
                        headlineContent = { Text(key.label) },
                        supportingContent = { Text(formatMinutes(minutes)) },
                        trailingContent = {
                            TextButton(onClick = { editingPeriod = key }) {
                                Text("Change")
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Done") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodTimePickerDialog(
    periodName: String,
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Time for ${periodName.lowercase().replaceFirstChar { it.uppercase() }}") },
        text = {
            TimePicker(state = timePickerState)
        },
        confirmButton = {
            Button(onClick = { onConfirm(timePickerState.hour * 60 + timePickerState.minute) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

