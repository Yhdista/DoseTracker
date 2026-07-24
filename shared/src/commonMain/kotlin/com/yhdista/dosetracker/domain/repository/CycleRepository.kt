package com.yhdista.dosetracker.domain.repository

import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.Cycle
import com.yhdista.dosetracker.domain.model.CycleWeek
import kotlinx.coroutines.flow.Flow

interface CycleRepository {
    fun getActiveCycle(): Flow<Data<Cycle?>>
    suspend fun getActiveCycleOnce(): Cycle?
    suspend fun getStandardCycle(): Cycle?
    suspend fun getCycleById(id: Long): Cycle?
    fun getCompletedCycles(): Flow<Data<List<Cycle>>>
    suspend fun createCycle(cycle: Cycle): Data<Long>
    suspend fun updateCycle(cycle: Cycle): Data<Unit>

    /** Atomically marks [completed] as finished and, when non-null, activates [activated]. */
    suspend fun completeAndActivateCycle(completed: Cycle, activated: Cycle?): Data<Unit>

    fun getWeeksForCycle(cycleId: Long): Flow<Data<List<CycleWeek>>>
    suspend fun getCycleWeek(cycleId: Long, weekIndex: Int): CycleWeek?
}
