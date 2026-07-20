package com.yhdista.dosetracker.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.CycleType
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.ui.theme.DoseTrackerTheme
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.format
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlinx.datetime.format.char
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    onNavigateToConfirm: (Long) -> Unit,
    onNavigateToCreateCycle: () -> Unit,
    onNavigateToCycleHistory: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    TodayContent(
        state = state,
        onEvent = viewModel::onEvent,
        onNavigateToConfirm = onNavigateToConfirm,
        onCreateCycle = onNavigateToCreateCycle,
        onOpenCycleHistory = onNavigateToCycleHistory
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayContent(
    state: TodayState,
    onEvent: (TodayEvent) -> Unit,
    onNavigateToConfirm: (Long) -> Unit,
    onCreateCycle: () -> Unit = {},
    onOpenCycleHistory: () -> Unit = {}
) {
    val activeCycle = (state.activeCycle as? Data.Success)?.data

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Today's Doses", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        when (val doses = state.doses) {
            is Data.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is Data.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(doses.message)
                }
            }
            is Data.Success -> {
                val cycleDoses = if (activeCycle != null) doses.data.filter { it.cycleId == activeCycle.id } else emptyList()
                val otherDoses = if (activeCycle != null) doses.data.filter { it.cycleId != activeCycle.id } else doses.data

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        if (activeCycle != null) {
                            CycleDashboardHeader(cycle = activeCycle, onOpenHistory = onOpenCycleHistory, onManageCycle = onCreateCycle)
                        } else {
                            NoCycleHeader(onCreateCycle = onCreateCycle)
                        }
                    }

                    if (doses.data.isEmpty()) {
                        item {
                            Text(
                                "No doses scheduled for today",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (activeCycle != null && cycleDoses.isNotEmpty()) {
                        item {
                            Text(
                                "V rámci cyklu ${activeCycle.name}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        items(cycleDoses, key = { it.id }) { dose ->
                            DoseItem(
                                dose = dose,
                                onClick = { onNavigateToConfirm(dose.id) },
                                onToggleStatus = { onEvent(TodayEvent.ToggleDoseStatus(dose)) }
                            )
                        }
                    }

                    if (otherDoses.isNotEmpty()) {
                        if (activeCycle != null) {
                            item {
                                Text(
                                    "Ostatní",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        items(otherDoses, key = { it.id }) { dose ->
                            DoseItem(
                                dose = dose,
                                onClick = { onNavigateToConfirm(dose.id) },
                                onToggleStatus = { onEvent(TodayEvent.ToggleDoseStatus(dose)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CycleDashboardHeader(cycle: Cycle, onOpenHistory: () -> Unit, onManageCycle: () -> Unit) {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val elapsedDays = cycle.startDate.daysUntil(today)
    val typeLabel = when (cycle.type) {
        CycleType.NORMAL -> "Cyklus"
        CycleType.STANDARD -> "Standardní cyklus"
        CycleType.POST -> "Post-cyklus"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(cycle.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(typeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Začátek: ${cycle.startDate}")
            Text("Běží $elapsedDays dní")
            val totalWeeks = cycle.totalWeeks
            if (totalWeeks != null) {
                val totalDays = totalWeeks * 7
                val remainingDays = (totalDays - elapsedDays).coerceAtLeast(0)
                val endDate = cycle.startDate.plus(totalDays, DateTimeUnit.DAY)
                Text("Zbývá $remainingDays dní (končí $endDate)")
            } else {
                Text("Běží neomezeně")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpenHistory) {
                    Text("Historie cyklu")
                }
                TextButton(onClick = onManageCycle) {
                    Text("Spravovat")
                }
            }
        }
    }
}

@Composable
private fun NoCycleHeader(onCreateCycle: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Žádný aktivní cyklus", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onCreateCycle) {
                Text("+ Nový cyklus")
            }
        }
    }
}

private val timeOnlyFormat = kotlinx.datetime.LocalDateTime.Format {
    hour(); char(':'); minute()
}

@Composable
fun DoseItem(
    dose: Dose,
    onClick: () -> Unit,
    onToggleStatus: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (dose.status == DoseStatus.TAKEN)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dose.medicationName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Scheduled at ${dose.timestamp.toLocalDateTime(TimeZone.currentSystemDefault()).format(timeOnlyFormat)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onToggleStatus) {
                Icon(
                    imageVector = if (dose.status == DoseStatus.TAKEN)
                        Icons.Rounded.CheckCircle
                    else
                        Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = "Toggle Status",
                    tint = if (dose.status == DoseStatus.TAKEN)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Preview
@Composable
fun TodayContentPreview() {
    DoseTrackerTheme {
        TodayContent(
            state = TodayState(
                doses = Data.Success(listOf(
                    Dose(id = 1, medicationId = 1, timestamp = Clock.System.now(), status = DoseStatus.PENDING),
                    Dose(id = 2, medicationId = 2, timestamp = Clock.System.now(), status = DoseStatus.TAKEN)
                )),
                activeCycle = Data.Success(null)
            ),
            onEvent = {},
            onNavigateToConfirm = {}
        )
    }
}
