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
import com.yhdista.dosetracker.domain.model.ReminderSchedule
import com.yhdista.dosetracker.reminder.WeekDays
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

    LaunchedEffect(medicationId) {
        viewModel.setMedicationId(medicationId)
    }

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

        when (val result = state.schedules) {
            is Data.Loading -> {
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is Data.Error -> {
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error: ${result.message}")
                }
            }
            is Data.Success -> {
                if (result.data.isEmpty()) {
                    Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No reminders yet")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.padding(padding).fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(result.data, key = { it.id }) { schedule ->
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
}

@Composable
private fun ScheduleRow(
    schedule: ReminderSchedule,
    periodTimes: Map<String, Int>,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val timeLabel = if (schedule.timeType == "PERIOD") {
        val periodName = schedule.dayPeriod?.lowercase()?.replaceFirstChar { it.uppercase() } ?: ""
        val minutes = periodTimes[schedule.dayPeriod] ?: schedule.minutesOfDay
        "$periodName (${formatMinutes(minutes)})"
    } else {
        formatMinutes(schedule.minutesOfDay)
    }

    val freqLabel = if (schedule.scheduleType == "INTERVAL") {
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

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ScheduleDialog(
    schedule: ReminderSchedule? = null,
    periodTimes: Map<String, Int>,
    onDismiss: () -> Unit,
    onConfirm: (
        minutesOfDay: Int,
        days: Set<DayOfWeek>,
        scheduleType: String,
        intervalDays: Int,
        startDate: LocalDate?,
        timeType: String,
        dayPeriod: String?
    ) -> Unit
) {
    val isEdit = schedule != null

    var timeType by remember { mutableStateOf(schedule?.timeType ?: "EXACT") }
    val initialMinutes = schedule?.minutesOfDay ?: 480
    val timePickerState = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = true
    )
    var dayPeriod by remember { mutableStateOf(schedule?.dayPeriod ?: "MORNING") }

    var scheduleType by remember { mutableStateOf(schedule?.scheduleType ?: "WEEKDAYS") }
    var selectedDays by remember {
        mutableStateOf(
            if (schedule != null && schedule.scheduleType == "WEEKDAYS") {
                WeekDays.fromBitmask(schedule.daysOfWeek)
            } else {
                DayOfWeek.entries.toSet()
            }
        )
    }
    var intervalDaysStr by remember { mutableStateOf(schedule?.intervalDays?.toString() ?: "2") }
    var startDate by remember { mutableStateOf(schedule?.startDate ?: Clock.System.todayIn(TimeZone.currentSystemDefault())) }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        startDate = Instant.fromEpochMilliseconds(millis)
                            .toLocalDateTime(TimeZone.currentSystemDefault()).date
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit Reminder" else "Add Reminder") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Time Setting", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = timeType == "EXACT",
                        onClick = { timeType = "EXACT" },
                        label = { Text("Exact Time") }
                    )
                    FilterChip(
                        selected = timeType == "PERIOD",
                        onClick = { timeType = "PERIOD" },
                        label = { Text("Day Period") }
                    )
                }

                if (timeType == "EXACT") {
                    TimePicker(state = timePickerState)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "MORNING" to "Morning (Ráno)",
                            "NOON" to "Noon (Poledne)",
                            "EVENING" to "Evening (Večer)",
                            "NIGHT" to "Night (Noc)"
                        ).forEach { (key, label) ->
                            val timeStr = periodTimes[key]?.let { formatMinutes(it) } ?: ""
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { dayPeriod = key }
                            ) {
                                RadioButton(
                                    selected = dayPeriod == key,
                                    onClick = { dayPeriod = key }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("$label - $timeStr")
                            }
                        }
                    }
                }

                HorizontalDivider()

                Text("Frequency Setting", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = scheduleType == "WEEKDAYS",
                        onClick = { scheduleType = "WEEKDAYS" },
                        label = { Text("Specific Days") }
                    )
                    FilterChip(
                        selected = scheduleType == "INTERVAL",
                        onClick = { scheduleType = "INTERVAL" },
                        label = { Text("Interval") }
                    )
                }

                if (scheduleType == "WEEKDAYS") {
                    Text("Days of week", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DayOfWeek.entries.forEach { day ->
                            FilterChip(
                                selected = day in selectedDays,
                                onClick = {
                                    selectedDays = if (day in selectedDays) selectedDays - day else selectedDays + day
                                },
                                label = { Text(day.name.take(3)) }
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = intervalDaysStr,
                            onValueChange = { newValue ->
                                if (newValue.all { it.isDigit() }) {
                                    intervalDaysStr = newValue
                                }
                            },
                            label = { Text("Repeat every X days") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Start Date: $startDate", style = MaterialTheme.typography.bodyLarge)
                            Button(onClick = { showDatePicker = true }) {
                                Text("Choose")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val interval = intervalDaysStr.toIntOrNull() ?: 1
            val isValid = if (scheduleType == "WEEKDAYS") selectedDays.isNotEmpty() else interval > 0
            Button(
                onClick = {
                    val minutes = timePickerState.hour * 60 + timePickerState.minute
                    onConfirm(
                        minutes,
                        selectedDays,
                        scheduleType,
                        interval,
                        if (scheduleType == "INTERVAL") startDate else null,
                        timeType,
                        if (timeType == "PERIOD") dayPeriod else null
                    )
                },
                enabled = isValid
            ) {
                Text(if (isEdit) "Save" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun PeriodSettingsDialog(
    currentTimes: Map<String, Int>,
    onDismiss: () -> Unit,
    onSavePeriod: (String, Int) -> Unit
) {
    var editingPeriod by remember { mutableStateOf<String?>(null) }
    
    if (editingPeriod != null) {
        val period = editingPeriod!!
        val minutes = currentTimes[period] ?: 480
        PeriodTimePickerDialog(
            periodName = period,
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
                listOf(
                    "MORNING" to "Morning (Ráno)",
                    "NOON" to "Noon (Poledne)",
                    "EVENING" to "Evening (Večer)",
                    "NIGHT" to "Night (Noc)"
                ).forEach { (key, label) ->
                    val minutes = currentTimes[key] ?: 0
                    ListItem(
                        headlineContent = { Text(label) },
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

private fun formatMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    val hStr = if (h < 10) "0$h" else "$h"
    val mStr = if (m < 10) "0$m" else "$m"
    return "$hStr:$mStr"
}
