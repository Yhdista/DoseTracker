package com.yhdista.dosetracker.ui.report

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.core.describe
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

enum class ReportRangeMode { MONTH, YEAR }

data class WeekQuantity(val weekStart: LocalDate, val totalTaken: Double)

data class MedicationReportState(
    val medicationName: String = "",
    val unit: String = "",
    val mode: ReportRangeMode = ReportRangeMode.MONTH,
    val periodStart: LocalDate = monthStartOf(Clock.System.todayIn(TimeZone.currentSystemDefault())),
    val weeks: Data<List<WeekQuantity>> = Data.Loading
)

sealed interface MedicationReportEvent {
    data object ToggleMode : MedicationReportEvent
    data object PreviousPeriod : MedicationReportEvent
    data object NextPeriod : MedicationReportEvent
}

private data class PeriodSelection(val mode: ReportRangeMode, val periodStart: LocalDate)

private fun monthStartOf(date: LocalDate): LocalDate = LocalDate(date.year, date.month, 1)

private fun yearStartOf(date: LocalDate): LocalDate = LocalDate(date.year, 1, 1)

private fun periodStartFor(mode: ReportRangeMode, anchor: LocalDate): LocalDate = when (mode) {
    ReportRangeMode.MONTH -> monthStartOf(anchor)
    ReportRangeMode.YEAR -> yearStartOf(anchor)
}

private fun periodEndExclusive(mode: ReportRangeMode, periodStart: LocalDate): LocalDate = when (mode) {
    ReportRangeMode.MONTH -> periodStart.plus(1, DateTimeUnit.MONTH)
    ReportRangeMode.YEAR -> periodStart.plus(1, DateTimeUnit.YEAR)
}

private fun weeksInPeriod(periodStart: LocalDate, periodEndExclusive: LocalDate): List<LocalDate> {
    val firstWeek = weekStartOf(periodStart)
    return generateSequence(firstWeek) { it.plus(7, DateTimeUnit.DAY) }
        .takeWhile { it < periodEndExclusive }
        .toList()
}

private fun bucketDosesByWeek(
    doses: List<Dose>,
    periodStart: LocalDate,
    periodEndExclusive: LocalDate
): List<WeekQuantity> {
    val zone = TimeZone.currentSystemDefault()
    val takenByWeek = doses
        .filter { it.status == DoseStatus.TAKEN }
        .groupBy { weekStartOf(it.timestamp.toLocalDateTime(zone).date) }
        .mapValues { (_, weekDoses) -> weekDoses.sumOf { it.amount ?: 0.0 } }

    return weeksInPeriod(periodStart, periodEndExclusive).map { weekStart ->
        WeekQuantity(weekStart, takenByWeek[weekStart] ?: 0.0)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MedicationReportViewModel(
    private val repository: MedicationRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val medicationIdFlow = savedStateHandle.getStateFlow<Long?>("medicationId", null)
    private val _selection = MutableStateFlow(
        PeriodSelection(ReportRangeMode.MONTH, monthStartOf(Clock.System.todayIn(TimeZone.currentSystemDefault())))
    )

    val uiState: StateFlow<MedicationReportState> = combine(
        medicationIdFlow.filterNotNull(),
        _selection
    ) { medicationId, selection -> medicationId to selection }
        .flatMapLatest { (medicationId, selection) ->
            val endExclusive = periodEndExclusive(selection.mode, selection.periodStart)
            combine(
                repository.getMedicationById(medicationId),
                repository.getDosesForMedicationInRange(medicationId, selection.periodStart, endExclusive)
            ) { medicationResult, dosesResult ->
                val medication = (medicationResult as? Data.Success)?.data
                val weeks = when (dosesResult) {
                    is Data.Success -> Data.Success(bucketDosesByWeek(dosesResult.data, selection.periodStart, endExclusive))
                    is Data.Error -> Data.Error(dosesResult.message)
                    is Data.Loading -> Data.Loading
                }
                MedicationReportState(
                    medicationName = medication?.name ?: "",
                    unit = medication?.unit?.symbol ?: "",
                    mode = selection.mode,
                    periodStart = selection.periodStart,
                    weeks = weeks
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MedicationReportState()
        )

    init {
        viewModelScope.launch {
            uiState.collect { state ->
                val weeksDesc = state.weeks.describe { weeks ->
                    weeks.joinToString(prefix = "[", postfix = "]") { "${it.weekStart}=${it.totalTaken}" }
                }
                com.yhdista.dosetracker.core.AppLogger.d(
                    "MedicationReportViewModel",
                    "State updated: medicationName='${state.medicationName}', unit='${state.unit}', mode=${state.mode}, periodStart=${state.periodStart}, weeks=$weeksDesc"
                )
            }
        }
    }

    fun setMedicationId(id: Long) {
        if (savedStateHandle.get<Long>("medicationId") == null) {
            savedStateHandle["medicationId"] = id
        }
    }

    fun onEvent(event: MedicationReportEvent) {
        com.yhdista.dosetracker.core.AppLogger.d("MedicationReportViewModel", "onEvent: $event")
        when (event) {
            is MedicationReportEvent.ToggleMode -> _selection.update { current ->
                val newMode = if (current.mode == ReportRangeMode.MONTH) ReportRangeMode.YEAR else ReportRangeMode.MONTH
                PeriodSelection(newMode, periodStartFor(newMode, current.periodStart))
            }
            is MedicationReportEvent.PreviousPeriod -> _selection.update { current ->
                current.copy(
                    periodStart = when (current.mode) {
                        ReportRangeMode.MONTH -> current.periodStart.minus(1, DateTimeUnit.MONTH)
                        ReportRangeMode.YEAR -> current.periodStart.minus(1, DateTimeUnit.YEAR)
                    }
                )
            }
            is MedicationReportEvent.NextPeriod -> _selection.update { current ->
                current.copy(
                    periodStart = when (current.mode) {
                        ReportRangeMode.MONTH -> current.periodStart.plus(1, DateTimeUnit.MONTH)
                        ReportRangeMode.YEAR -> current.periodStart.plus(1, DateTimeUnit.YEAR)
                    }
                )
            }
        }
    }
}
