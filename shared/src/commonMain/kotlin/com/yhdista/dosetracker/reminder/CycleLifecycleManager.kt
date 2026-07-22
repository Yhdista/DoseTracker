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

        com.yhdista.dosetracker.core.AppLogger.i("CycleLifecycleManager", "Active cycle expired: id=${cycle.id}, name='${cycle.name}', totalWeeks=$totalWeeks, completed on=$today")
        repository.updateCycle(cycle.copy(status = CycleStatus.COMPLETED))

        com.yhdista.dosetracker.core.AppLogger.i("CycleLifecycleManager", "Executing on-complete action: ${cycle.onCompleteAction}")
        when (cycle.onCompleteAction) {
            CycleCompleteAction.TO_STANDARD -> {
                val standard = repository.getStandardCycle() ?: return
                com.yhdista.dosetracker.core.AppLogger.i("CycleLifecycleManager", "Activating standard cycle: id=${standard.id}")
                repository.updateCycle(standard.copy(status = CycleStatus.ACTIVE, startDate = today))
            }
            CycleCompleteAction.TO_POST -> {
                val nextId = cycle.nextCycleId ?: return
                val next = repository.getCycleById(nextId) ?: return
                com.yhdista.dosetracker.core.AppLogger.i("CycleLifecycleManager", "Activating post cycle: id=${next.id}, name='${next.name}'")
                repository.updateCycle(next.copy(status = CycleStatus.ACTIVE, startDate = today))
            }
            CycleCompleteAction.TO_NONE -> {
                com.yhdista.dosetracker.core.AppLogger.i("CycleLifecycleManager", "No cycle transition configured.")
            }
        }
    }
}
