package com.yhdista.dosetracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val dosage: Double,
    val unit: String,
    val frequency: String,
    val reminderTime: String? = null
)
