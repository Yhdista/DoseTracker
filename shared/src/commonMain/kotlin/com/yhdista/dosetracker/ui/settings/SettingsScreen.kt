package com.yhdista.dosetracker.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.DayPeriod
import com.yhdista.dosetracker.shared.resources.Res
import com.yhdista.dosetracker.shared.resources.cancel
import com.yhdista.dosetracker.shared.resources.ok
import com.yhdista.dosetracker.shared.resources.settings_about
import com.yhdista.dosetracker.shared.resources.settings_about_desc
import com.yhdista.dosetracker.shared.resources.settings_app_version
import com.yhdista.dosetracker.shared.resources.settings_change
import com.yhdista.dosetracker.shared.resources.settings_day_period
import com.yhdista.dosetracker.shared.resources.settings_day_period_times
import com.yhdista.dosetracker.shared.resources.settings_default_time_type
import com.yhdista.dosetracker.shared.resources.settings_default_time_type_desc
import com.yhdista.dosetracker.shared.resources.settings_exact_time
import com.yhdista.dosetracker.shared.resources.settings_reminders
import com.yhdista.dosetracker.shared.resources.settings_set_time_for
import com.yhdista.dosetracker.shared.resources.settings_title
import com.yhdista.dosetracker.ui.common.label
import com.yhdista.dosetracker.ui.schedule.formatMinutes
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val periodTimes = (state.periodTimes as? Data.Success)?.data ?: emptyMap()
    var editingPeriod by remember { mutableStateOf<DayPeriod?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings_title), fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        if (editingPeriod != null) {
            val period = editingPeriod!!
            val minutes = periodTimes[period] ?: 480
            PeriodTimePickerDialog(
                periodName = period.label,
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
                text = stringResource(Res.string.settings_day_period_times),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    DayPeriod.entries.forEach { key ->
                        val minutes = periodTimes[key] ?: 0
                        ListItem(
                            headlineContent = { Text(key.label) },
                            supportingContent = { Text(formatMinutes(minutes)) },
                            trailingContent = {
                                TextButton(onClick = { editingPeriod = key }) {
                                    Text(stringResource(Res.string.settings_change))
                                }
                            },
                            modifier = Modifier.clickable { editingPeriod = key }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(Res.string.settings_reminders),
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
                        text = stringResource(Res.string.settings_default_time_type),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(Res.string.settings_default_time_type_desc),
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
                            Text(stringResource(Res.string.settings_exact_time))
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
                            Text(stringResource(Res.string.settings_day_period))
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(Res.string.settings_about),
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
                        text = stringResource(Res.string.settings_app_version),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(Res.string.settings_about_desc),
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
        title = { Text(stringResource(Res.string.settings_set_time_for, periodName)) },
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
                Text(stringResource(Res.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        }
    )
}
