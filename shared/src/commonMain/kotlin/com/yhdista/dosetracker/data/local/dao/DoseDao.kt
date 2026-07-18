package com.yhdista.dosetracker.data.local.dao

import androidx.room.*
import com.yhdista.dosetracker.data.local.entity.DoseEntity
import kotlinx.coroutines.flow.Flow

data class DoseWithMedication(
    @Embedded val dose: DoseEntity,
    @ColumnInfo(name = "medicationName") val medicationName: String
)

@Dao
interface DoseDao {
    @Transaction
    @Query("""
        SELECT doses.*, medications.name as medicationName
        FROM doses
        INNER JOIN medications ON doses.medicationId = medications.id
        WHERE medicationId = :medicationId
        ORDER BY timestamp DESC
    """)
    fun getDosesForMedication(medicationId: Long): Flow<List<DoseWithMedication>>

    @Transaction
    @Query("""
        SELECT doses.*, medications.name as medicationName
        FROM doses
        INNER JOIN medications ON doses.medicationId = medications.id
        WHERE timestamp >= :startTime AND timestamp <= :endTime
        ORDER BY timestamp ASC
    """)
    fun getDosesInTimeRange(startTime: Long, endTime: Long): Flow<List<DoseWithMedication>>

    @Transaction
    @Query("""
        SELECT doses.*, medications.name as medicationName
        FROM doses
        INNER JOIN medications ON doses.medicationId = medications.id
        ORDER BY timestamp DESC
    """)
    fun getAllDoses(): Flow<List<DoseWithMedication>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDose(dose: DoseEntity): Long

    @Update
    suspend fun updateDose(dose: DoseEntity)

    @Delete
    suspend fun deleteDose(dose: DoseEntity)
}
