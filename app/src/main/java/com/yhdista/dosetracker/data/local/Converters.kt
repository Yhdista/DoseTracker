package com.yhdista.dosetracker.data.local

import androidx.room.TypeConverter
import com.yhdista.dosetracker.domain.model.DoseStatus
import java.time.Instant

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Instant? {
        return value?.let { Instant.ofEpochMilli(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Instant?): Long? {
        return date?.toEpochMilli()
    }

    @TypeConverter
    fun fromDoseStatus(status: DoseStatus): String {
        return status.name
    }

    @TypeConverter
    fun toDoseStatus(value: String): DoseStatus {
        return DoseStatus.valueOf(value)
    }
}
