package com.yhdista.dosetracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.yhdista.dosetracker.domain.model.CycleCompleteAction
import com.yhdista.dosetracker.domain.model.CycleStatus
import com.yhdista.dosetracker.domain.model.CycleType

@Entity(tableName = "cycles")
internal data class CycleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: CycleType,
    val totalWeeks: Int?,
    val startDate: String,
    val status: CycleStatus,
    val onCompleteAction: CycleCompleteAction,
    val nextCycleId: Long?
)
