package com.yhdista.dosetracker.ui.settings

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
import com.yhdista.dosetracker.core.Data

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val periodTimes = (state.periodTimes as? Data.Success)?.data ?: emptyMap()
    var editingPeriod by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        if (editingPeriod != null) {
            val period = editingPeriod!!
            val minutes = periodTimes[period] ?: 480
            PeriodTimePickerDialog(
                periodName = period,
                initialMinutes = minutes,
                onDismiss = { editingPeriod = null },
                onConfirm = { newMinutes ->
                    viewModel.onEvent(SettingsEvent.UpdatePeriodTime(period, newMinutes))
                    editingPeriod = null
                }
            )
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Day Period Times",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    listOf(
                        "MORNING" to "Morning (Ráno)",
                        "NOON" to "Noon (Poledne)",
                        "EVENING" to "Evening (Večer)",
                        "NIGHT" to "Night (Noc)"
                    ).forEach { (key, label) ->
                        val minutes = periodTimes[key] ?: 0
                        ListItem(
                            headlineContent = { Text(label) },
                            supportingContent = { Text(formatMinutes(minutes)) },
                            trailingContent = {
                                TextButton(onClick = { editingPeriod = key }) {
                                    Text("Change")
                                }
                            },
                            modifier = Modifier.clickable { editingPeriod = key }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Reminder Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Default Time Type",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Select whether new reminders default to exact times or day periods.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                viewModel.onEvent(SettingsEvent.UpdateDefaultTimeType(com.yhdista.dosetracker.domain.model.TimeType.EXACT))
                            }
                        ) {
                            RadioButton(
                                selected = state.defaultTimeType == com.yhdista.dosetracker.domain.model.TimeType.EXACT,
                                onClick = {
                                    viewModel.onEvent(SettingsEvent.UpdateDefaultTimeType(com.yhdista.dosetracker.domain.model.TimeType.EXACT))
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Exact Time")
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                viewModel.onEvent(SettingsEvent.UpdateDefaultTimeType(com.yhdista.dosetracker.domain.model.TimeType.PERIOD))
                            }
                        ) {
                            RadioButton(
                                selected = state.defaultTimeType == com.yhdista.dosetracker.domain.model.TimeType.PERIOD,
                                onClick = {
                                    viewModel.onEvent(SettingsEvent.UpdateDefaultTimeType(com.yhdista.dosetracker.domain.model.TimeType.PERIOD))
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Day Period")
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "About",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "DoseTracker v1.0.0",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "A simple medication reminder app to help you track your doses and stay healthy.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodTimePickerDialog(
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
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TimePicker(state = timePickerState)
            }
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
