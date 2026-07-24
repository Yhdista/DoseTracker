package com.yhdista.dosetracker.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

internal fun getRoomDatabase(
    builder: RoomDatabase.Builder<AppDatabase>,
    debugTooling: Boolean,
): AppDatabase {
    return builder
        .addCallback(AppDatabase.defaultsCallback)
        .apply {
            if (debugTooling) {
                // SQL statement logging exposes full medication data — debug builds only.
                addCallback(AppDatabase.demoSeedCallback)
                setDriver(LoggingSQLiteDriver(BundledSQLiteDriver()))
            } else {
                setDriver(BundledSQLiteDriver())
            }
        }
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}
