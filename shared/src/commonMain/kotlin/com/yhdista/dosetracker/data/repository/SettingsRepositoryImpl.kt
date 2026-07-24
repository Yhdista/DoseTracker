package com.yhdista.dosetracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.yhdista.dosetracker.domain.model.TimeType
import com.yhdista.dosetracker.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

internal class SettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    private object PreferencesKeys {
        val DEFAULT_TIME_TYPE = stringPreferencesKey("default_time_type")
    }

    override fun getDefaultTimeType(): Flow<TimeType> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                val timeTypeString = preferences[PreferencesKeys.DEFAULT_TIME_TYPE]
                if (timeTypeString != null) {
                    TimeType.fromValue(timeTypeString)
                } else {
                    TimeType.PERIOD // User request: default is "day period"
                }
            }
    }

    override suspend fun setDefaultTimeType(timeType: TimeType) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_TIME_TYPE] = timeType.value
        }
    }
}
