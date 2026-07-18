package com.yhdista.dosetracker.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.yhdista.dosetracker.data.local.dao.DoseDao
import com.yhdista.dosetracker.data.local.dao.MedicationDao
import com.yhdista.dosetracker.data.local.entity.DoseEntity
import com.yhdista.dosetracker.data.local.entity.MedicationEntity

@Database(
    entities = [MedicationEntity::class, DoseEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun doseDao(): DoseDao

    companion object {
        const val DATABASE_NAME = "dosetracker_db"

        val seedCallback = object : RoomDatabase.Callback() {
            override fun onCreate(connection: SQLiteConnection) {
                super.onCreate(connection)
                connection.execSQL("INSERT INTO medications (name, dosage, unit, frequency, reminderTime) VALUES ('Paracetamol', 500.0, 'mg', 'Every 6 hours', '08:00')")
                connection.execSQL("INSERT INTO medications (name, dosage, unit, frequency, reminderTime) VALUES ('Ibuprofen', 400.0, 'mg', 'Twice a day', '09:00')")
                connection.execSQL("INSERT INTO medications (name, dosage, unit, frequency, reminderTime) VALUES ('Amoxicillin', 500.0, 'mg', 'Three times a day', '07:00')")
                connection.execSQL("INSERT INTO medications (name, dosage, unit, frequency, reminderTime) VALUES ('Vitamin C', 1000.0, 'mg', 'Daily', '10:00')")
                connection.execSQL("INSERT INTO medications (name, dosage, unit, frequency, reminderTime) VALUES ('Lisinopril', 10.0, 'mg', 'Daily', '08:30')")
            }
        }
    }
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
