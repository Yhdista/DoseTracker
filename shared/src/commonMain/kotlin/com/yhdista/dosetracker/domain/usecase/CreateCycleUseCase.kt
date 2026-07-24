package com.yhdista.dosetracker.domain.usecase

import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.CycleCompleteAction
import com.yhdista.dosetracker.domain.model.CycleStatus
import com.yhdista.dosetracker.domain.model.CycleType
import com.yhdista.dosetracker.domain.repository.CycleRepository
import com.yhdista.dosetracker.reminder.DoseGenerator
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * Owns the cycle-creation rules that used to live in the ViewModel:
 * - no active cycle → the new cycle starts ACTIVE immediately and doses regenerate;
 * - STANDARD → the single template row is updated in place (or created as DRAFT);
 * - POST → created as DRAFT and linked to the active cycle via nextCycleId/TO_POST.
 */
class CreateCycleUseCase(
    private val cycleRepository: CycleRepository,
    private val doseGenerator: DoseGenerator,
) {

    /**
     * @param startDate first day of the new cycle, or null for "today". Only honoured on the path
     *   where the cycle really starts (no active cycle): a STANDARD template and a chained POST
     *   cycle both get their start date from activation, not from creation.
     * @return the created/updated cycle id, or null for a combination the UI never offers.
     */
    suspend operator fun invoke(
        name: String,
        type: CycleType,
        totalWeeks: Int,
        onCompleteAction: CycleCompleteAction,
        startDate: LocalDate? = null,
    ): Data<Long>? {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val active = cycleRepository.getActiveCycleOnce()

        return when {
            active == null -> {
                val cycle = Cycle(
                    name = name,
                    type = type,
                    totalWeeks = if (type == CycleType.STANDARD) null else totalWeeks,
                    startDate = if (type == CycleType.STANDARD) today else (startDate ?: today),
                    status = CycleStatus.ACTIVE,
                    onCompleteAction = onCompleteAction
                )
                cycleRepository.createCycle(cycle).also {
                    if (it is Data.Success) doseGenerator.runForToday()
                }
            }
            type == CycleType.STANDARD -> {
                val existing = cycleRepository.getStandardCycle()
                if (existing != null) {
                    cycleRepository.updateCycle(existing.copy(name = name))
                    Data.Success(existing.id)
                } else {
                    val cycle = Cycle(
                        name = name,
                        type = CycleType.STANDARD,
                        totalWeeks = null,
                        startDate = today,
                        status = CycleStatus.DRAFT,
                        onCompleteAction = CycleCompleteAction.TO_NONE
                    )
                    cycleRepository.createCycle(cycle)
                }
            }
            type == CycleType.POST -> {
                val cycle = Cycle(
                    name = name,
                    type = CycleType.POST,
                    totalWeeks = totalWeeks,
                    startDate = today,
                    status = CycleStatus.DRAFT,
                    onCompleteAction = CycleCompleteAction.TO_NONE
                )
                val created = cycleRepository.createCycle(cycle)
                if (created is Data.Success) {
                    cycleRepository.updateCycle(
                        active.copy(nextCycleId = created.data, onCompleteAction = CycleCompleteAction.TO_POST)
                    )
                }
                created
            }
            else -> null // NORMAL type while a cycle is already active is not offered by the UI.
        }
    }
}
