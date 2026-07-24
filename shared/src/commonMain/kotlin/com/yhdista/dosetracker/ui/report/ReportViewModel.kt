package com.yhdista.dosetracker.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.core.logEach
import com.yhdista.dosetracker.core.describe
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.repository.DoseRepository
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
    private val doseRepository: DoseRepository
) : ViewModel() {

    private val _weekStart = MutableStateFlow(currentWeekStart())

    val uiState: StateFlow<ReportState> = _weekStart
        .flatMapLatest { weekStart ->
            doseRepository.getDosesInWeek(weekStart).map { result -> weekStart to summarize(result) }
        }
        .map { (weekStart, summaries) -> ReportState(weekStart = weekStart, summaries = summaries) }
        .logEach("ReportViewModel") { state ->
        val summariesDesc = state.summaries.describe { summaries ->
            summaries.joinToString(prefix = "[", postfix = "]") { sum ->
                "${sum.medicationName}: TAKEN=${sum.taken}, MISSED=${sum.missed}, SKIPPED=${sum.skipped}, UPCOMING=${sum.upcoming}"
            }
        }
        "State updated: weekStart=${state.weekStart}, summaries=$summariesDesc"
    }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ReportState()
        )


    fun onEvent(event: ReportEvent) {
        com.yhdista.dosetracker.core.AppLogger.d("ReportViewModel", "onEvent: $event")
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
