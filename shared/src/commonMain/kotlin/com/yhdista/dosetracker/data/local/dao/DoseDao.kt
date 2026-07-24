package com.yhdista.dosetracker.data.local.dao

import androidx.room.*
import com.yhdista.dosetracker.data.local.entity.DoseEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

internal data class DoseWithMedication(
    @Embedded val dose: DoseEntity,
    @ColumnInfo(name = "medicationName") val medicationName: String
)

@Dao
internal interface DoseDao {
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
        WHERE doses.medicationId = :medicationId
          AND timestamp >= :startTime AND timestamp <= :endTime
        ORDER BY timestamp ASC
    """)
    fun getDosesForMedicationInTimeRange(medicationId: Long, startTime: Long, endTime: Long): Flow<List<DoseWithMedication>>

    @Transaction
    @Query("""
        SELECT doses.*, medications.name as medicationName
        FROM doses
        INNER JOIN medications ON doses.medicationId = medications.id
        ORDER BY timestamp DESC
    """)
    fun getAllDoses(): Flow<List<DoseWithMedication>>

    // ABORT (default): a conflict on the unique (scheduleId, timestamp) index must fail
    // loudly instead of silently replacing the row under a new id and orphaning alarms.
    @Insert
    suspend fun insertDose(dose: DoseEntity): Long

    @Update
    suspend fun updateDose(dose: DoseEntity)

    @Delete
    suspend fun deleteDose(dose: DoseEntity)

    @Transaction
    @Query("""
        SELECT doses.*, medications.name as medicationName
        FROM doses
        INNER JOIN medications ON doses.medicationId = medications.id
        WHERE doses.id = :id
    """)
    suspend fun getDoseWithMedicationById(id: Long): DoseWithMedication?

    @Query("SELECT * FROM doses WHERE scheduleId = :scheduleId AND timestamp = :timestamp LIMIT 1")
    suspend fun getDoseForSchedule(scheduleId: Long, timestamp: Instant): DoseEntity?

    @Query("UPDATE doses SET status = 'MISSED' WHERE status = 'PENDING' AND timestamp <= :cutoff")
    suspend fun markPendingDosesMissedBefore(cutoff: Instant): Int

    @Query("SELECT * FROM doses WHERE scheduleId = :scheduleId AND timestamp >= :startTime AND timestamp <= :endTime LIMIT 1")
    suspend fun getDoseForScheduleOnDate(scheduleId: Long, startTime: Instant, endTime: Instant): DoseEntity?

    @Transaction
    @Query("""
        SELECT doses.*, medications.name as medicationName
        FROM doses
        INNER JOIN medications ON doses.medicationId = medications.id
        WHERE doses.id = :id
    """)
    fun getDoseWithMedicationByIdFlow(id: Long): Flow<DoseWithMedication?>
}
