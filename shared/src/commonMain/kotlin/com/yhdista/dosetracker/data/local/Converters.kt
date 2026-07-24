package com.yhdista.dosetracker.data.local

import androidx.room.TypeConverter
import com.yhdista.dosetracker.domain.model.CycleCompleteAction
import com.yhdista.dosetracker.domain.model.CycleStatus
import com.yhdista.dosetracker.domain.model.CycleType
import com.yhdista.dosetracker.domain.model.DoseStatus
import kotlinx.datetime.Instant

internal class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Instant? {
        return value?.let { Instant.fromEpochMilliseconds(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Instant?): Long? {
        return date?.toEpochMilliseconds()
    }

    @TypeConverter
    fun fromDoseStatus(status: DoseStatus): String {
        return status.name
    }

    @TypeConverter
    fun toDoseStatus(value: String): DoseStatus {
        return DoseStatus.valueOf(value)
    }

    @TypeConverter
    fun fromCycleType(value: CycleType): String = value.name

    @TypeConverter
    fun toCycleType(value: String): CycleType = CycleType.valueOf(value)

    @TypeConverter
    fun fromCycleStatus(value: CycleStatus): String = value.name

    @TypeConverter
    fun toCycleStatus(value: String): CycleStatus = CycleStatus.valueOf(value)

    @TypeConverter
    fun fromCycleCompleteAction(value: CycleCompleteAction): String = value.name

    @TypeConverter
    fun toCycleCompleteAction(value: String): CycleCompleteAction = CycleCompleteAction.valueOf(value)
}
