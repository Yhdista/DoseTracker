package com.yhdista.dosetracker.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Dose
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.repository.DatabaseMaintenance
import com.yhdista.dosetracker.domain.repository.DoseRepository
import com.yhdista.dosetracker.reminder.DoseGenerator
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn

class DebugViewModel(
    private val doseRepository: DoseRepository,
    private val doseGenerator: DoseGenerator,
    private val maintenance: DatabaseMaintenance
) : ViewModel() {

    fun generateDosesForToday() {
        viewModelScope.launch {
            doseGenerator.runForToday()
        }
    }

    fun generateMockHistory() {
        viewModelScope.launch {
            val zone = TimeZone.currentSystemDefault()
            val today = Clock.System.todayIn(zone)
            val now = Clock.System.now()

            // 1. Generate doses for the last 7 days (including today)
            for (i in 0..7) {
                val date = today.minus(i, DateTimeUnit.DAY)
                doseGenerator.runForDate(date)
            }

            // 2. Fetch all doses and randomize status for past ones
            // (skip the Loading emission — first() alone would always return it)
            val result = doseRepository.getAllDoses()
                .filterIsInstance<Data.Success<List<Dose>>>()
                .first()
            result.data.forEach { dose ->
                if (dose.timestamp < now && dose.status == DoseStatus.PENDING) {
                    val randomStatus = when ((0..2).random()) {
                        0 -> DoseStatus.TAKEN
                        1 -> DoseStatus.MISSED
                        else -> DoseStatus.SKIPPED
                    }
                    doseRepository.updateDose(dose.copy(status = randomStatus))
                }
            }
        }
    }

    fun resetDatabase() {
        viewModelScope.launch {
            maintenance.clearAllData()
        }
    }
}
