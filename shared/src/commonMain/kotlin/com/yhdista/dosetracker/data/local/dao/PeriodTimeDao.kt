package com.yhdista.dosetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yhdista.dosetracker.data.local.entity.PeriodTimeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PeriodTimeDao {
    @Query("SELECT * FROM period_times")
    fun getAllPeriodTimesFlow(): Flow<List<PeriodTimeEntity>>

    @Query("SELECT * FROM period_times")
    suspend fun getAllPeriodTimes(): List<PeriodTimeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPeriodTime(periodTime: PeriodTimeEntity)
}
