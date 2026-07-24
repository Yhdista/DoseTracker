package com.yhdista.dosetracker.domain.repository

/** Destructive maintenance operations, kept behind an interface so UI code never touches the database directly. */
interface DatabaseMaintenance {
    suspend fun clearAllData()
}
