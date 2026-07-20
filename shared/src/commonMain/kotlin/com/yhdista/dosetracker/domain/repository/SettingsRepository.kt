package com.yhdista.dosetracker.domain.repository

import com.yhdista.dosetracker.domain.model.TimeType
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getDefaultTimeType(): Flow<TimeType>
    suspend fun setDefaultTimeType(timeType: TimeType)
}
