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

        com.yhdista.dosetracker.core.AppLogger.i("CycleLifecycleManager", "Executing on-complete action: ${cycle.onCompleteAction}")
        val next = when (cycle.onCompleteAction) {
            CycleCompleteAction.TO_STANDARD -> repository.getStandardCycle()
            CycleCompleteAction.TO_POST -> cycle.nextCycleId?.let { repository.getCycleById(it) }
            CycleCompleteAction.TO_NONE -> null
        }
        if (next != null) {
            com.yhdista.dosetracker.core.AppLogger.i("CycleLifecycleManager", "Activating next cycle: id=${next.id}, name='${next.name}'")
        } else {
            com.yhdista.dosetracker.core.AppLogger.i("CycleLifecycleManager", "No follow-up cycle to activate.")
        }
        // Single atomic write: a crash between "complete" and "activate" must not
        // leave the app without an active cycle.
        repository.completeAndActivateCycle(
            cycle.copy(status = CycleStatus.COMPLETED),
            next?.copy(status = CycleStatus.ACTIVE, startDate = today)
        )
    }
}
