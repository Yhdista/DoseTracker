package com.yhdista.dosetracker.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.data.local.AppDatabase
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import com.yhdista.dosetracker.reminder.DoseGenerator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn

class DebugViewModel(
    private val repository: MedicationRepository,
    private val doseGenerator: DoseGenerator,
    private val database: AppDatabase
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
            val result = repository.getAllDoses().first()
            if (result is Data.Success) {
                result.data.forEach { dose ->
                    if (dose.timestamp < now && dose.status == DoseStatus.PENDING) {
                        val randomStatus = when ((0..2).random()) {
                            0 -> DoseStatus.TAKEN
                            1 -> DoseStatus.MISSED
                            else -> DoseStatus.SKIPPED
                        }
                        repository.updateDose(dose.copy(status = randomStatus))
                    }
                }
            }
        }
    }

    fun resetDatabase() {
        viewModelScope.launch {
            database.clearAllTables()
        }
    }
}
