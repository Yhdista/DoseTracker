package com.yhdista.dosetracker.ui.today

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.CycleType
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.shared.resources.Res
import com.yhdista.dosetracker.shared.resources.cycle_type_normal
import com.yhdista.dosetracker.shared.resources.cycle_type_post
import com.yhdista.dosetracker.shared.resources.cycle_type_standard
import com.yhdista.dosetracker.shared.resources.today_badge
import com.yhdista.dosetracker.shared.resources.today_cycle_settings
import com.yhdista.dosetracker.shared.resources.today_dose_count_few
import com.yhdista.dosetracker.shared.resources.today_dose_count_many
import com.yhdista.dosetracker.shared.resources.today_dose_count_one
import com.yhdista.dosetracker.shared.resources.today_legend_post
import com.yhdista.dosetracker.shared.resources.today_legend_standard
import com.yhdista.dosetracker.shared.resources.today_legend_unplanned
import com.yhdista.dosetracker.shared.resources.today_more_times
import com.yhdista.dosetracker.shared.resources.today_new_cycle
import com.yhdista.dosetracker.shared.resources.today_next_week
import com.yhdista.dosetracker.shared.resources.today_no_active_cycle
import com.yhdista.dosetracker.shared.resources.today_no_doses
import com.yhdista.dosetracker.shared.resources.today_no_doses_today
import com.yhdista.dosetracker.shared.resources.today_other_doses
import com.yhdista.dosetracker.shared.resources.today_remaining_days
import com.yhdista.dosetracker.shared.resources.today_running_days
import com.yhdista.dosetracker.shared.resources.today_running_days_week
import com.yhdista.dosetracker.shared.resources.today_running_indefinitely
import com.yhdista.dosetracker.shared.resources.today_scheduled_at
import com.yhdista.dosetracker.shared.resources.today_this_week
import com.yhdista.dosetracker.shared.resources.today_timeline_header
import com.yhdista.dosetracker.shared.resources.today_title
import com.yhdista.dosetracker.shared.resources.today_toggle_status
import com.yhdista.dosetracker.shared.resources.today_within_cycle
import com.yhdista.dosetracker.ui.theme.DoseTrackerTheme
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
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
    onNavigateToCycleSettings: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    TodayContent(
        state = state,
        onEvent = viewModel::onEvent,
        onNavigateToConfirm = onNavigateToConfirm,
        onCreateCycle = onNavigateToCreateCycle,
        onNavigateToCycleSettings = onNavigateToCycleSettings
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TodayContent(
    state: TodayState,
    onEvent: (TodayEvent) -> Unit,
    onNavigateToConfirm: (Long) -> Unit,
    onCreateCycle: () -> Unit = {},
    onNavigateToCycleSettings: () -> Unit = {}
) {
    val zone = TimeZone.currentSystemDefault()
    // The ViewModel's date flow re-emits after midnight; fall back to the wall clock
    // only for the first frame before the state arrives.
    val today = state.today ?: Clock.System.todayIn(zone)

    val activeCycle = (state.activeCycle as? Data.Success)?.data
    val completedCycles = (state.completedCycles as? Data.Success)?.data ?: emptyList()

    val bands = buildTimelineBands(
        today = today,
        activeCycle = activeCycle,
        otherCycles = completedCycles + state.futureCycles
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.today_title), fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // --- Timeline strip ---
            item {
                TimelineSection(
                    today = today,
                    bands = bands,
                    onBandClick = { band ->
                        if (band.kind == BandKind.ACTIVE) onNavigateToCycleSettings()
                    }
                )
            }

            // --- Active cycle status card ---
            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    if (activeCycle != null) {
                        CycleDashboardHeader(
                            cycle = activeCycle,
                            onOpenSettings = onNavigateToCycleSettings
                        )
                    } else {
                        NoCycleHeader(onCreateCycle = onCreateCycle)
                    }
                }
            }

            // --- 14-day agenda ---
            when (val doses = state.dosesInWindow) {
                is Data.Loading -> item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is Data.Error -> item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(doses.message)
                    }
                }
                is Data.Success -> {
                    val agenda = buildAgenda(today, doses.data, zone)
                    val activeCycleId = activeCycle?.id

                    // Week 1 (Tento týden): today card + the next 6 days.
                    stickyHeader { WeekHeader(stringResource(Res.string.today_this_week)) }
                    item {
                        TodayCard(
                            day = agenda.first(),
                            cycleName = activeCycle?.name,
                            activeCycleId = activeCycleId,
                            today = today,
                            onNavigateToConfirm = onNavigateToConfirm,
                            onToggleStatus = { onEvent(TodayEvent.ToggleDoseStatus(it)) }
                        )
                    }
                    items(agenda.subList(1, 7), key = { it.date.toString() }) { day ->
                        DayRow(day = day, activeCycleId = activeCycleId)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }

                    // Week 2 (Příští týden): the remaining 7 days.
                    stickyHeader { WeekHeader(stringResource(Res.string.today_next_week)) }
                    items(agenda.subList(7, 14), key = { it.date.toString() }) { day ->
                        DayRow(day = day, activeCycleId = activeCycleId)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineSection(
    today: LocalDate,
    bands: List<CycleBand>,
    onBandClick: (CycleBand) -> Unit
) {
    Column(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
        Text(
            stringResource(Res.string.today_timeline_header),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp)
        )
        CycleTimeline(
            today = today,
            bands = bands,
            modifier = Modifier.fillMaxWidth(),
            outlineColor = MaterialTheme.colorScheme.outline,
            hatchColor = MaterialTheme.colorScheme.onSurfaceVariant,
            primaryColor = MaterialTheme.colorScheme.primary,
            onBandClick = onBandClick
        )
        TimelineLegend()
    }
}

@Composable
private fun TimelineLegend() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        LegendDot(com.yhdista.dosetracker.ui.theme.CycleColors.Normal, stringResource(Res.string.cycle_type_normal))
        LegendDot(com.yhdista.dosetracker.ui.theme.CycleColors.Standard, stringResource(Res.string.today_legend_standard))
        LegendDot(com.yhdista.dosetracker.ui.theme.CycleColors.Post, stringResource(Res.string.today_legend_post))
        LegendDot(MaterialTheme.colorScheme.outline, stringResource(Res.string.today_legend_unplanned))
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WeekHeader(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun TodayCard(
    day: AgendaDay,
    cycleName: String?,
    activeCycleId: Long?,
    today: LocalDate,
    onNavigateToConfirm: (Long) -> Unit,
    onToggleStatus: (Dose) -> Unit
) {
    val cycleDoses = if (activeCycleId != null) day.doses.filter { it.cycleId == activeCycleId } else emptyList()
    val otherDoses = if (activeCycleId != null) day.doses.filter { it.cycleId != activeCycleId } else day.doses

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column {
            // Header strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        stringResource(Res.string.today_badge),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
                Text(
                    formatFullDate(today),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (day.doses.isEmpty()) {
                    Text(
                        stringResource(Res.string.today_no_doses_today),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (cycleDoses.isNotEmpty()) {
                    Text(
                        stringResource(Res.string.today_within_cycle, cycleName ?: "").trimEnd(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    cycleDoses.forEach { dose ->
                        CompactDoseRow(dose, onClick = { onNavigateToConfirm(dose.id) }, onToggle = { onToggleStatus(dose) })
                    }
                }
                if (otherDoses.isNotEmpty()) {
                    if (cycleDoses.isNotEmpty()) {
                        Text(
                            stringResource(Res.string.today_other_doses),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    otherDoses.forEach { dose ->
                        CompactDoseRow(dose, onClick = { onNavigateToConfirm(dose.id) }, onToggle = { onToggleStatus(dose) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactDoseRow(dose: Dose, onClick: () -> Unit, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                doseTitle(dose),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(Res.string.today_scheduled_at, formatTimeOnly(dose)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = if (dose.status == DoseStatus.TAKEN) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = stringResource(Res.string.today_toggle_status),
                tint = if (dose.status == DoseStatus.TAKEN) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun DayRow(day: AgendaDay, activeCycleId: Long?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Date rail
        Column(
            modifier = Modifier.width(38.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                czechDayAbbrev(day.date.dayOfWeek),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                day.date.day.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Summary
        if (day.doses.isEmpty()) {
            Text(
                stringResource(Res.string.today_no_doses),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val hasCycleDose = activeCycleId != null && day.doses.any { it.cycleId == activeCycleId }
            FlowRowSummary(day = day, cycleTinted = hasCycleDose)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowSummary(day: AgendaDay, cycleTinted: Boolean) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            doseCountLabel(day.doses.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterVertically)
        )
        val times = day.doses.map { formatTimeOnly(it) }
        val shown = times.take(3)
        shown.forEach { t -> TimeChip(t, cycleTinted) }
        if (times.size > 3) {
            TimeChip(stringResource(Res.string.today_more_times, (times.size - 3).toString()), cycleTinted = false)
        }
    }
}

@Composable
private fun TimeChip(label: String, cycleTinted: Boolean) {
    Surface(
        color = if (cycleTinted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (cycleTinted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun CycleDashboardHeader(
    cycle: Cycle,
    onOpenSettings: () -> Unit
) {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val elapsedDays = cycle.startDate.daysUntil(today)
    val typeLabel = when (cycle.type) {
        CycleType.NORMAL -> stringResource(Res.string.cycle_type_normal)
        CycleType.STANDARD -> stringResource(Res.string.cycle_type_standard)
        CycleType.POST -> stringResource(Res.string.cycle_type_post)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(cycle.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = {
                    com.yhdista.dosetracker.core.AppLogger.d("TodayScreen", "Click: Nastavení cyklu (cycleId=${cycle.id}, name='${cycle.name}')")
                    onOpenSettings()
                }) {
                    Icon(Icons.Rounded.Settings, contentDescription = stringResource(Res.string.today_cycle_settings))
                }
            }
            Text(typeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val totalWeeks = cycle.totalWeeks
            if (totalWeeks != null) {
                val totalDays = totalWeeks * 7
                val elapsedWeeks = (elapsedDays / 7) + 1
                val remainingDays = (totalDays - elapsedDays).coerceAtLeast(0)
                val endDate = cycle.startDate.plus(totalDays, DateTimeUnit.DAY)
                val progress = (elapsedDays.toFloat() / totalDays.toFloat()).coerceIn(0f, 1f)
                Spacer(Modifier.height(2.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(
                        Res.string.today_running_days_week,
                        elapsedDays.toString(),
                        elapsedWeeks.coerceAtMost(totalWeeks).toString(),
                        totalWeeks.toString()
                    )
                )
                Text(stringResource(Res.string.today_remaining_days, remainingDays.toString(), endDate.toString()))
            } else {
                Text(stringResource(Res.string.today_running_days, elapsedDays.toString()))
                Text(stringResource(Res.string.today_running_indefinitely))
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
            Text(stringResource(Res.string.today_no_active_cycle), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = {
                com.yhdista.dosetracker.core.AppLogger.d("TodayScreen", "Click: + Nový cyklus (no active cycle)")
                onCreateCycle()
            }) {
                Text(stringResource(Res.string.today_new_cycle))
            }
        }
    }
}

// --- formatting helpers ---

private val timeOnlyFormat = kotlinx.datetime.LocalDateTime.Format {
    hour(); char(':'); minute()
}

private fun doseTitle(dose: Dose): String {
    val amount = dose.amount
    val unit = dose.unit
    return if (amount != null) {
        "${dose.medicationName} ${amount}${unit ?: ""}".trimEnd()
    } else {
        dose.medicationName
    }
}

private fun formatTimeOnly(dose: Dose): String =
    dose.timestamp.toLocalDateTime(TimeZone.currentSystemDefault()).format(timeOnlyFormat)

@Composable
private fun doseCountLabel(count: Int): String = when (count) {
    1 -> stringResource(Res.string.today_dose_count_one)
    in 2..4 -> stringResource(Res.string.today_dose_count_few, count.toString())
    else -> stringResource(Res.string.today_dose_count_many, count.toString())
}

private fun czechDayAbbrev(dow: DayOfWeek): String = when (dow) {
    DayOfWeek.MONDAY -> "Po"
    DayOfWeek.TUESDAY -> "Út"
    DayOfWeek.WEDNESDAY -> "St"
    DayOfWeek.THURSDAY -> "Čt"
    DayOfWeek.FRIDAY -> "Pá"
    DayOfWeek.SATURDAY -> "So"
    DayOfWeek.SUNDAY -> "Ne"
    else -> ""
}

private fun czechDayFull(dow: DayOfWeek): String = when (dow) {
    DayOfWeek.MONDAY -> "Pondělí"
    DayOfWeek.TUESDAY -> "Úterý"
    DayOfWeek.WEDNESDAY -> "Středa"
    DayOfWeek.THURSDAY -> "Čtvrtek"
    DayOfWeek.FRIDAY -> "Pátek"
    DayOfWeek.SATURDAY -> "Sobota"
    DayOfWeek.SUNDAY -> "Neděle"
    else -> ""
}

private fun czechMonthGenitive(month: Month): String = when (month) {
    Month.JANUARY -> "ledna"; Month.FEBRUARY -> "února"; Month.MARCH -> "března"
    Month.APRIL -> "dubna"; Month.MAY -> "května"; Month.JUNE -> "června"
    Month.JULY -> "července"; Month.AUGUST -> "srpna"; Month.SEPTEMBER -> "září"
    Month.OCTOBER -> "října"; Month.NOVEMBER -> "listopadu"; Month.DECEMBER -> "prosince"
    else -> ""
}

private fun formatFullDate(date: LocalDate): String =
    "${czechDayFull(date.dayOfWeek)}, ${date.day}. ${czechMonthGenitive(date.month)}"

/**
 * Compact dose card kept for the in-app "Grafický manuál" style guide, which renders it as a
 * live example. Not used by the redesigned Today screen itself (which uses [CompactDoseRow]).
 */
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
                    text = stringResource(Res.string.today_scheduled_at, formatTimeOnly(dose)),
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
                    contentDescription = stringResource(Res.string.today_toggle_status),
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
                dosesInWindow = Data.Success(listOf(
                    Dose(id = 1, medicationId = 1, medicationName = "Testosteron E", timestamp = Clock.System.now(), amount = 250.0, unit = "mg", status = DoseStatus.PENDING),
                    Dose(id = 2, medicationId = 2, medicationName = "Anastrozol", timestamp = Clock.System.now(), amount = 0.5, unit = "mg", status = DoseStatus.TAKEN)
                )),
                activeCycle = Data.Success(null),
                completedCycles = Data.Success(emptyList())
            ),
            onEvent = {},
            onNavigateToConfirm = {}
        )
    }
}
