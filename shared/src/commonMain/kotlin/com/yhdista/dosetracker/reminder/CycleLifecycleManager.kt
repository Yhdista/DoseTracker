package com.yhdista.dosetracker.reminder

import com.yhdista.dosetracker.domain.model.CycleCompleteAction
import com.yhdista.dosetracker.domain.model.CycleStatus
import com.yhdista.dosetracker.domain.model.CycleType
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

class CycleLifecycleManager(
    private val repository: MedicationRepository
) {
    suspend fun advance(today: LocalDate) {
        val cycle = repository.getActiveCycleOnce() ?: return
        if (cycle.type == CycleType.STANDARD) return
        val totalWeeks = cycle.totalWeeks ?: return
        val weekIndex = cycle.startDate.daysUntil(today) / 7
        if (weekIndex < totalWeeks) return

        repository.updateCycle(cycle.copy(status = CycleStatus.COMPLETED))

        when (cycle.onCompleteAction) {
            CycleCompleteAction.TO_STANDARD -> {
                val standard = repository.getStandardCycle() ?: return
                repository.updateCycle(standard.copy(status = CycleStatus.ACTIVE, startDate = today))
            }
            CycleCompleteAction.TO_POST -> {
                val nextId = cycle.nextCycleId ?: return
                val next = repository.getCycleById(nextId) ?: return
                repository.updateCycle(next.copy(status = CycleStatus.ACTIVE, startDate = today))
            }
            CycleCompleteAction.TO_NONE -> Unit
        }
    }
}
