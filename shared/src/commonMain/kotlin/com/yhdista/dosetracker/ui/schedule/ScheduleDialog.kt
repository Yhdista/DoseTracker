package com.yhdista.dosetracker.ui.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yhdista.dosetracker.domain.model.DayPeriod
import com.yhdista.dosetracker.domain.model.ReminderSchedule
import com.yhdista.dosetracker.domain.model.ScheduleType
import com.yhdista.dosetracker.domain.model.TimeType
import com.yhdista.dosetracker.reminder.WeekDays
import com.yhdista.dosetracker.shared.resources.Res
import com.yhdista.dosetracker.shared.resources.add
import com.yhdista.dosetracker.shared.resources.cancel
import com.yhdista.dosetracker.shared.resources.ok
import com.yhdista.dosetracker.shared.resources.save
import com.yhdista.dosetracker.shared.resources.schedule_add_reminder
import com.yhdista.dosetracker.shared.resources.schedule_choose
import com.yhdista.dosetracker.shared.resources.schedule_days_of_week
import com.yhdista.dosetracker.shared.resources.schedule_edit_reminder
import com.yhdista.dosetracker.shared.resources.schedule_frequency_setting
import com.yhdista.dosetracker.shared.resources.schedule_interval
import com.yhdista.dosetracker.shared.resources.schedule_repeat_every_x_days
import com.yhdista.dosetracker.shared.resources.schedule_specific_days
import com.yhdista.dosetracker.shared.resources.schedule_start_date
import com.yhdista.dosetracker.shared.resources.schedule_time_setting
import com.yhdista.dosetracker.shared.resources.settings_day_period
import com.yhdista.dosetracker.shared.resources.settings_exact_time
import com.yhdista.dosetracker.ui.common.label
import org.jetbrains.compose.resources.stringResource
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
fun ScheduleDialog(
    schedule: ReminderSchedule? = null,
    defaultTimeType: TimeType = TimeType.EXACT,
    periodTimes: Map<DayPeriod, Int>,
    onDismiss: () -> Unit,
    onConfirm: (
        minutesOfDay: Int,
        days: Set<DayOfWeek>,
        scheduleType: ScheduleType,
        intervalDays: Int,
        startDate: LocalDate?,
        timeType: TimeType,
        dayPeriod: DayPeriod?
    ) -> Unit
) {
    val isEdit = schedule != null

    var timeType by remember { mutableStateOf(schedule?.timeType ?: defaultTimeType) }
    val initialMinutes = schedule?.minutesOfDay ?: 480
    val timePickerState = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = true
    )
    var dayPeriod by remember { mutableStateOf(schedule?.dayPeriod ?: DayPeriod.MORNING) }

    var scheduleType by remember { mutableStateOf(schedule?.scheduleType ?: ScheduleType.WEEKDAYS) }
    var selectedDays by remember {
        mutableStateOf(
            if (schedule != null && schedule.scheduleType == ScheduleType.WEEKDAYS) {
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
                }) { Text(stringResource(Res.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(Res.string.cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) stringResource(Res.string.schedule_edit_reminder) else stringResource(Res.string.schedule_add_reminder)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(stringResource(Res.string.schedule_time_setting), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = timeType == TimeType.EXACT,
                        onClick = { timeType = TimeType.EXACT },
                        label = { Text(stringResource(Res.string.settings_exact_time)) }
                    )
                    FilterChip(
                        selected = timeType == TimeType.PERIOD,
                        onClick = { timeType = TimeType.PERIOD },
                        label = { Text(stringResource(Res.string.settings_day_period)) }
                    )
                }

                if (timeType == TimeType.EXACT) {
                    TimePicker(state = timePickerState)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DayPeriod.entries.forEach { period ->
                            val timeStr = periodTimes[period]?.let { formatMinutes(it) } ?: ""
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { dayPeriod = period }
                            ) {
                                RadioButton(
                                    selected = dayPeriod == period,
                                    onClick = { dayPeriod = period }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("${period.label} - $timeStr")
                            }
                        }
                    }
                }

                HorizontalDivider()

                Text(stringResource(Res.string.schedule_frequency_setting), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = scheduleType == ScheduleType.WEEKDAYS,
                        onClick = { scheduleType = ScheduleType.WEEKDAYS },
                        label = { Text(stringResource(Res.string.schedule_specific_days)) }
                    )
                    FilterChip(
                        selected = scheduleType == ScheduleType.INTERVAL,
                        onClick = { scheduleType = ScheduleType.INTERVAL },
                        label = { Text(stringResource(Res.string.schedule_interval)) }
                    )
                }

                if (scheduleType == ScheduleType.WEEKDAYS) {
                    Text(stringResource(Res.string.schedule_days_of_week), style = MaterialTheme.typography.labelLarge)
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
                            label = { Text(stringResource(Res.string.schedule_repeat_every_x_days)) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(Res.string.schedule_start_date, startDate.toString()), style = MaterialTheme.typography.bodyLarge)
                            Button(onClick = { showDatePicker = true }) {
                                Text(stringResource(Res.string.schedule_choose))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val interval = intervalDaysStr.toIntOrNull() ?: 1
            val isValid = if (scheduleType == ScheduleType.WEEKDAYS) selectedDays.isNotEmpty() else interval > 0
            Button(
                onClick = {
                    val minutes = timePickerState.hour * 60 + timePickerState.minute
                    onConfirm(
                        minutes,
                        selectedDays,
                        scheduleType,
                        interval,
                        if (scheduleType == ScheduleType.INTERVAL) startDate else null,
                        timeType,
                        if (timeType == TimeType.PERIOD) dayPeriod else null
                    )
                },
                enabled = isValid
            ) {
                Text(if (isEdit) stringResource(Res.string.save) else stringResource(Res.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        }
    )
}

fun formatMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    val hStr = if (h < 10) "0$h" else "$h"
    val mStr = if (m < 10) "0$m" else "$m"
    return "$hStr:$mStr"
}
