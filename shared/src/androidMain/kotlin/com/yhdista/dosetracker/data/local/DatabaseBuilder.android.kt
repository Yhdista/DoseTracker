package com.yhdista.dosetracker.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

internal fun getDatabaseBuilder(context: Context): RoomDatabase.Builder<AppDatabase> {
    val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
    // No destructive fallback: this database holds the user's medication history.
    // Every schema bump from version 5 on must ship an explicit Migration.
    return Room.databaseBuilder<AppDatabase>(
        context = context.applicationContext,
        name = dbFile.absolutePath
    )
}
