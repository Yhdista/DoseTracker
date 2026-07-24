package com.yhdista.dosetracker.data.repository

import com.yhdista.dosetracker.core.AppLogger
import com.yhdista.dosetracker.data.local.AppDatabase
import com.yhdista.dosetracker.domain.repository.DatabaseMaintenance

internal class DatabaseMaintenanceImpl(
    private val database: AppDatabase
) : DatabaseMaintenance {

    override suspend fun clearAllData() {
        AppLogger.i("Database", "Clearing all tables")
        database.clearAllTables()
    }
}
