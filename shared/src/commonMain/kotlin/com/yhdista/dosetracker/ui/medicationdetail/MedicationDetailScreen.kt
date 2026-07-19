package com.yhdista.dosetracker.ui.medicationdetail

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
import androidx.compose.ui.unit.dp
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.ReminderSchedule
import com.yhdista.dosetracker.reminder.WeekDays
import kotlinx.datetime.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MedicationDetailScreen(
    medicationId: Long,
    viewModel: MedicationDetailViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(medicationId) {
        viewModel.setMedicationId(medicationId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text((state.medication as? Data.Success)?.data?.name ?: "Medication") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
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
        if (showAddDialog) {
            AddScheduleDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { minutesOfDay, days ->
                    viewModel.onEvent(MedicationDetailEvent.AddSchedule(minutesOfDay, days))
                    showAddDialog = false
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
private fun ScheduleRow(schedule: ReminderSchedule, onDelete: () -> Unit) {
    val hour = schedule.minutesOfDay / 60
    val minute = schedule.minutesOfDay % 60
    val days = WeekDays.fromBitmask(schedule.daysOfWeek)
    val daysLabel = if (days.size == 7) "Every day" else days.joinToString { it.name.take(3) }

    ListItem(
        headlineContent = { Text("%02d:%02d".format(hour, minute)) },
        supportingContent = { Text(daysLabel) },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete reminder")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AddScheduleDialog(
    onDismiss: () -> Unit,
    onConfirm: (minutesOfDay: Int, days: Set<DayOfWeek>) -> Unit
) {
    val timePickerState = rememberTimePickerState(initialHour = 8, initialMinute = 0, is24Hour = true)
    var selectedDays by remember { mutableStateOf(DayOfWeek.entries.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TimePicker(state = timePickerState)
                Text("Days", style = MaterialTheme.typography.labelLarge)
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
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(timePickerState.hour * 60 + timePickerState.minute, selectedDays) },
                enabled = selectedDays.isNotEmpty()
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
