package com.yhdista.dosetracker.ui.cycle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yhdista.dosetracker.ui.common.ObserveAsEvents
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yhdista.dosetracker.core.isoWeekOf
import com.yhdista.dosetracker.core.isoWeekStart
import com.yhdista.dosetracker.core.isoWeeksInYear
import com.yhdista.dosetracker.domain.model.CycleCompleteAction
import com.yhdista.dosetracker.domain.model.CycleType
import com.yhdista.dosetracker.shared.resources.Res
import com.yhdista.dosetracker.shared.resources.back
import com.yhdista.dosetracker.shared.resources.catalog_name
import com.yhdista.dosetracker.shared.resources.createcycle_on_complete
import com.yhdista.dosetracker.shared.resources.createcycle_start
import com.yhdista.dosetracker.shared.resources.createcycle_start_already_over
import com.yhdista.dosetracker.shared.resources.createcycle_start_today
import com.yhdista.dosetracker.shared.resources.createcycle_start_week
import com.yhdista.dosetracker.shared.resources.createcycle_starts_on
import com.yhdista.dosetracker.shared.resources.createcycle_title
import com.yhdista.dosetracker.shared.resources.createcycle_total_weeks
import com.yhdista.dosetracker.shared.resources.createcycle_type
import com.yhdista.dosetracker.shared.resources.createcycle_week_option
import com.yhdista.dosetracker.shared.resources.timeline_date_short
import com.yhdista.dosetracker.shared.resources.cycle_type_normal
import com.yhdista.dosetracker.shared.resources.cycle_type_post
import com.yhdista.dosetracker.shared.resources.cycle_type_standard
import com.yhdista.dosetracker.shared.resources.save
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCycleScreen(
    viewModel: CreateCycleViewModel,
    onBack: () -> Unit,
    onCreated: (cycleId: Long, weekCount: Int) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.uiEvents) { event ->
        when (event) {
            is CreateCycleUiEvent.Created -> onCreated(event.cycleId, event.weekCount)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.createcycle_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                }
            )
        }
    ) { padding ->
        if (state.hasActiveCycle == null) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Name field
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { viewModel.onEvent(CreateCycleEvent.NameChanged(it)) },
                    label = { Text(stringResource(Res.string.catalog_name)) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Type selector
                Text(stringResource(Res.string.createcycle_type), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.hasActiveCycle == false) {
                        FilterChip(
                            selected = state.type == CycleType.NORMAL,
                            onClick = { viewModel.onEvent(CreateCycleEvent.TypeChanged(CycleType.NORMAL)) },
                            label = { Text(stringResource(Res.string.cycle_type_normal)) }
                        )
                    }
                    FilterChip(
                        selected = state.type == CycleType.STANDARD,
                        onClick = { viewModel.onEvent(CreateCycleEvent.TypeChanged(CycleType.STANDARD)) },
                        label = { Text(stringResource(Res.string.cycle_type_standard)) }
                    )
                    if (state.hasActiveCycle == true) {
                        FilterChip(
                            selected = state.type == CycleType.POST,
                            onClick = { viewModel.onEvent(CreateCycleEvent.TypeChanged(CycleType.POST)) },
                            label = { Text(stringResource(Res.string.cycle_type_post)) }
                        )
                    }
                }

                // Total weeks field (hidden for STANDARD type)
                if (state.type != CycleType.STANDARD) {
                    OutlinedTextField(
                        value = state.totalWeeks.toString(),
                        onValueChange = { newValue ->
                            newValue.toIntOrNull()?.let {
                                viewModel.onEvent(CreateCycleEvent.TotalWeeksChanged(it))
                            }
                        },
                        label = { Text(stringResource(Res.string.createcycle_total_weeks)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Start selector: cycles do not have to begin today — a week of the year can be
                // picked instead, in the past (cycle already running) or in the future.
                if (state.canPickStartWeek) {
                    Text(stringResource(Res.string.createcycle_start), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.startMode == CycleStartMode.TODAY,
                            onClick = { viewModel.onEvent(CreateCycleEvent.StartModeChanged(CycleStartMode.TODAY)) },
                            label = { Text(stringResource(Res.string.createcycle_start_today)) }
                        )
                        FilterChip(
                            selected = state.startMode == CycleStartMode.WEEK,
                            onClick = { viewModel.onEvent(CreateCycleEvent.StartModeChanged(CycleStartMode.WEEK)) },
                            label = { Text(stringResource(Res.string.createcycle_start_week)) }
                        )
                    }
                    if (state.startMode == CycleStartMode.WEEK) {
                        StartWeekPicker(
                            state = state,
                            onYearChange = { viewModel.onEvent(CreateCycleEvent.StartYearChanged(it)) },
                            onWeekChange = { viewModel.onEvent(CreateCycleEvent.StartWeekChanged(it)) }
                        )
                    }
                }

                // On complete action selector
                if (state.type != CycleType.STANDARD && state.hasActiveCycle == false) {
                    Text(stringResource(Res.string.createcycle_on_complete), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.onCompleteAction == CycleCompleteAction.TO_STANDARD,
                            onClick = { viewModel.onEvent(CreateCycleEvent.OnCompleteActionChanged(CycleCompleteAction.TO_STANDARD)) },
                            label = { Text(stringResource(Res.string.cycle_type_standard)) }
                        )
                        FilterChip(
                            selected = state.onCompleteAction == CycleCompleteAction.TO_POST,
                            onClick = { viewModel.onEvent(CreateCycleEvent.OnCompleteActionChanged(CycleCompleteAction.TO_POST)) },
                            label = { Text(stringResource(Res.string.cycle_type_post)) }
                        )
                    }
                }

                Button(
                    onClick = { viewModel.onEvent(CreateCycleEvent.Save) },
                    enabled = state.isValid,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(Res.string.save))
                }
            }
        }
    }
}

/**
 * Year chips (last, current and next week-year) plus a week dropdown, with the resolved start
 * date spelled out underneath — a bare week number is not something anyone can date by eye.
 */
@Composable
private fun StartWeekPicker(
    state: CreateCycleState,
    onYearChange: (Int) -> Unit,
    onWeekChange: (Int) -> Unit,
) {
    val currentWeek = remember(state.today) { isoWeekOf(state.today) }
    val years = remember(currentWeek) { listOf(currentWeek.year - 1, currentWeek.year, currentWeek.year + 1) }
    var weekMenuOpen by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            years.forEach { year ->
                FilterChip(
                    selected = state.startYear == year,
                    onClick = { onYearChange(year) },
                    label = { Text(year.toString()) }
                )
            }
        }

        Box {
            OutlinedButton(onClick = { weekMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Text(weekOptionLabel(state.startYear, state.startWeek))
            }
            DropdownMenu(expanded = weekMenuOpen, onDismissRequest = { weekMenuOpen = false }) {
                for (week in 1..isoWeeksInYear(state.startYear)) {
                    DropdownMenuItem(
                        text = { Text(weekOptionLabel(state.startYear, week)) },
                        onClick = {
                            onWeekChange(week)
                            weekMenuOpen = false
                        }
                    )
                }
            }
        }

        if (state.startsInThePast) {
            Text(
                stringResource(Res.string.createcycle_start_already_over),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            Text(
                stringResource(Res.string.createcycle_starts_on, formatWeekDate(state.startDate), state.startDate.year.toString()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** "Week 30 · 20. 7. – 26. 7." — the week number alone is not enough to recognize the dates. */
@Composable
private fun weekOptionLabel(year: Int, week: Int): String {
    val start = isoWeekStart(year, week)
    val end = start.plus(6, DateTimeUnit.DAY)
    return stringResource(
        Res.string.createcycle_week_option,
        week.toString(),
        formatWeekDate(start),
        formatWeekDate(end)
    )
}

@Composable
private fun formatWeekDate(date: LocalDate): String =
    stringResource(Res.string.timeline_date_short, date.day.toString(), (date.month.ordinal + 1).toString())
