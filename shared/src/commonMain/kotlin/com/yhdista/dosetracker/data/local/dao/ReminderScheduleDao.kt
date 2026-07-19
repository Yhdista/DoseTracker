package com.yhdista.dosetracker.data.local.dao

import androidx.room.*
import com.yhdista.dosetracker.data.local.entity.ReminderScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderScheduleDao {
    @Query("SELECT * FROM reminder_schedules WHERE medicationId = :medicationId")
    fun getSchedulesForMedication(medicationId: Long): Flow<List<ReminderScheduleEntity>>

    @Query("SELECT * FROM reminder_schedules WHERE enabled = 1")
    suspend fun getAllEnabledSchedules(): List<ReminderScheduleEntity>

    @Query("SELECT * FROM reminder_schedules")
    fun getAllSchedulesFlow(): Flow<List<ReminderScheduleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: ReminderScheduleEntity): Long

    @Delete
    suspend fun deleteSchedule(schedule: ReminderScheduleEntity)

    @Update
    suspend fun updateSchedule(schedule: ReminderScheduleEntity)
}
