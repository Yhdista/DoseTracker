package com.yhdista.dosetracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cycle_weeks",
    foreignKeys = [
        ForeignKey(
            entity = CycleEntity::class,
            parentColumns = ["id"],
            childColumns = ["cycleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["cycleId"]), Index(value = ["cycleId", "weekIndex"], unique = true)]
)
data class CycleWeekEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val cycleId: Long,
    val weekIndex: Int
)
