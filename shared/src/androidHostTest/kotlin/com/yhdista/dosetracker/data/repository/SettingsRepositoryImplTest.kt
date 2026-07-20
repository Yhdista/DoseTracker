package com.yhdista.dosetracker.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.yhdista.dosetracker.domain.model.TimeType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryImplTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @Test
    fun `getDefaultTimeType returns PERIOD by default`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { tmpFolder.newFile("settings.preferences_pb") }
        )
        val repository = SettingsRepositoryImpl(dataStore)

        val defaultType = repository.getDefaultTimeType().first()
        assertEquals(TimeType.PERIOD, defaultType)
    }

    @Test
    fun `setDefaultTimeType updates the setting correctly`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { tmpFolder.newFile("settings.preferences_pb") }
        )
        val repository = SettingsRepositoryImpl(dataStore)

        repository.setDefaultTimeType(TimeType.EXACT)
        val defaultType = repository.getDefaultTimeType().first()
        assertEquals(TimeType.EXACT, defaultType)
    }
}
