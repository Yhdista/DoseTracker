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
import com.yhdista.dosetracker.data.local.dao.ReminderScheduleDao
import com.yhdista.dosetracker.data.local.dao.PeriodTimeDao
import com.yhdista.dosetracker.data.local.entity.DoseEntity
import com.yhdista.dosetracker.data.local.entity.MedicationEntity
import com.yhdista.dosetracker.data.local.entity.ReminderScheduleEntity
import com.yhdista.dosetracker.data.local.entity.PeriodTimeEntity

@Database(
    entities = [MedicationEntity::class, DoseEntity::class, ReminderScheduleEntity::class, PeriodTimeEntity::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun doseDao(): DoseDao
    abstract fun reminderScheduleDao(): ReminderScheduleDao
    abstract fun periodTimeDao(): PeriodTimeDao

    companion object {
        const val DATABASE_NAME = "dosetracker_db"

        val seedCallback = object : RoomDatabase.Callback() {
            override fun onCreate(connection: SQLiteConnection) {
                super.onCreate(connection)
                connection.execSQL("INSERT INTO medications (name, dosage, unit) VALUES ('Paracetamol', 500.0, 'mg')")
                connection.execSQL("INSERT INTO medications (name, dosage, unit) VALUES ('Ibuprofen', 400.0, 'mg')")
                connection.execSQL("INSERT INTO medications (name, dosage, unit) VALUES ('Amoxicillin', 500.0, 'mg')")
                connection.execSQL("INSERT INTO medications (name, dosage, unit) VALUES ('Vitamin C', 1000.0, 'mg')")
                connection.execSQL("INSERT INTO medications (name, dosage, unit) VALUES ('Lisinopril', 10.0, 'mg')")
                connection.execSQL("INSERT INTO reminder_schedules (medicationId, minutesOfDay, daysOfWeek, enabled, scheduleType, intervalDays, startDate, timeType, dayPeriod) VALUES (1, 480, 127, 1, 'WEEKDAYS', 1, NULL, 'EXACT', NULL)")
                connection.execSQL("INSERT INTO reminder_schedules (medicationId, minutesOfDay, daysOfWeek, enabled, scheduleType, intervalDays, startDate, timeType, dayPeriod) VALUES (2, 540, 127, 1, 'WEEKDAYS', 1, NULL, 'EXACT', NULL)")
                connection.execSQL("INSERT INTO reminder_schedules (medicationId, minutesOfDay, daysOfWeek, enabled, scheduleType, intervalDays, startDate, timeType, dayPeriod) VALUES (3, 420, 127, 1, 'WEEKDAYS', 1, NULL, 'EXACT', NULL)")
                connection.execSQL("INSERT INTO reminder_schedules (medicationId, minutesOfDay, daysOfWeek, enabled, scheduleType, intervalDays, startDate, timeType, dayPeriod) VALUES (4, 600, 127, 1, 'WEEKDAYS', 1, NULL, 'EXACT', NULL)")
                connection.execSQL("INSERT INTO reminder_schedules (medicationId, minutesOfDay, daysOfWeek, enabled, scheduleType, intervalDays, startDate, timeType, dayPeriod) VALUES (5, 510, 127, 1, 'WEEKDAYS', 1, NULL, 'EXACT', NULL)")
                connection.execSQL("INSERT INTO period_times (period, minutesOfDay) VALUES ('MORNING', 480)")
                connection.execSQL("INSERT INTO period_times (period, minutesOfDay) VALUES ('NOON', 720)")
                connection.execSQL("INSERT INTO period_times (period, minutesOfDay) VALUES ('EVENING', 1080)")
                connection.execSQL("INSERT INTO period_times (period, minutesOfDay) VALUES ('NIGHT', 1320)")
            }
        }
    }
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
