package com.yhdista.dosetracker.data.local.dao

import androidx.room.*
import com.yhdista.dosetracker.data.local.entity.CycleEntity
import com.yhdista.dosetracker.data.local.entity.CycleWeekEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface CycleDao {
    @Query("SELECT * FROM cycles WHERE status = 'ACTIVE' LIMIT 1")
    fun getActiveCycleFlow(): Flow<CycleEntity?>

    @Query("SELECT * FROM cycles WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveCycleOnce(): CycleEntity?

    @Query("SELECT * FROM cycles WHERE type = 'STANDARD' LIMIT 1")
    suspend fun getStandardCycle(): CycleEntity?

    @Query("SELECT * FROM cycles WHERE status = 'COMPLETED' ORDER BY startDate DESC")
    fun getCompletedCycles(): Flow<List<CycleEntity>>

    @Query("SELECT * FROM cycles WHERE id = :id")
    suspend fun getCycleById(id: Long): CycleEntity?

    @Insert
    suspend fun insertCycle(cycle: CycleEntity): Long

    @Update
    suspend fun updateCycle(cycle: CycleEntity)

    @Transaction
    suspend fun insertCycleWithWeeks(cycle: CycleEntity, weekCount: Int): Long {
        val cycleId = insertCycle(cycle)
        for (weekIndex in 0 until weekCount) {
            insertCycleWeek(CycleWeekEntity(cycleId = cycleId, weekIndex = weekIndex))
        }
        return cycleId
    }

    @Transaction
    suspend fun completeAndActivate(completed: CycleEntity, activated: CycleEntity?) {
        updateCycle(completed)
        if (activated != null) updateCycle(activated)
    }

    @Query("SELECT * FROM cycle_weeks WHERE cycleId = :cycleId ORDER BY weekIndex ASC")
    fun getWeeksForCycle(cycleId: Long): Flow<List<CycleWeekEntity>>

    @Query("SELECT * FROM cycle_weeks WHERE cycleId = :cycleId AND weekIndex = :weekIndex LIMIT 1")
    suspend fun getCycleWeek(cycleId: Long, weekIndex: Int): CycleWeekEntity?

    @Insert
    suspend fun insertCycleWeek(week: CycleWeekEntity): Long
}
