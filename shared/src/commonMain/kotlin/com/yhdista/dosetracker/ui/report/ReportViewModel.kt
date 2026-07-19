package com.yhdista.dosetracker.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

data class MedicationWeekSummary(
    val medicationId: Long,
    val medicationName: String,
    val taken: Int,
    val missed: Int,
    val skipped: Int,
    val upcoming: Int,
    val totalAmountTaken: Double,
    val totalAmountScheduled: Double,
    val unit: String
)

data class ReportState(
    val weekStart: LocalDate = currentWeekStart(),
    val summaries: Data<List<MedicationWeekSummary>> = Data.Loading
)

sealed interface ReportEvent {
    data object PreviousWeek : ReportEvent
    data object NextWeek : ReportEvent
}

fun weekStartOf(date: LocalDate): LocalDate {
    val daysSinceMonday = date.dayOfWeek.isoDayNumber - DayOfWeek.MONDAY.isoDayNumber
    return date.minus(daysSinceMonday, DateTimeUnit.DAY)
}

private fun currentWeekStart(): LocalDate =
    weekStartOf(Clock.System.todayIn(TimeZone.currentSystemDefault()))

@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModel(
    private val repository: MedicationRepository
) : ViewModel() {

    private val _weekStart = MutableStateFlow(currentWeekStart())

    val uiState: StateFlow<ReportState> = _weekStart
        .flatMapLatest { weekStart ->
            repository.getDosesInWeek(weekStart).map { result -> weekStart to summarize(result) }
        }
        .map { (weekStart, summaries) -> ReportState(weekStart = weekStart, summaries = summaries) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ReportState()
        )

    fun onEvent(event: ReportEvent) {
        when (event) {
            is ReportEvent.PreviousWeek -> _weekStart.update { it.minus(7, DateTimeUnit.DAY) }
            is ReportEvent.NextWeek -> _weekStart.update { it.plus(7, DateTimeUnit.DAY) }
        }
    }

    private fun summarize(result: Data<List<Dose>>): Data<List<MedicationWeekSummary>> {
        return when (result) {
            is Data.Success -> Data.Success(
                result.data
                    .groupBy { it.medicationId }
                    .map { (medicationId, doses) ->
                        MedicationWeekSummary(
                            medicationId = medicationId,
                            medicationName = doses.first().medicationName,
                            taken = doses.count { it.status == DoseStatus.TAKEN },
                            missed = doses.count { it.status == DoseStatus.MISSED },
                            skipped = doses.count { it.status == DoseStatus.SKIPPED },
                            upcoming = doses.count { it.status == DoseStatus.PENDING },
                            totalAmountTaken = doses.filter { it.status == DoseStatus.TAKEN }
                                .sumOf { it.amount ?: 0.0 },
                            totalAmountScheduled = doses.sumOf { it.amount ?: 0.0 },
                            unit = doses.firstOrNull { it.unit != null }?.unit ?: ""
                        )
                    }
            )
            is Data.Error -> Data.Error(result.message)
            is Data.Loading -> Data.Loading
        }
    }
}
