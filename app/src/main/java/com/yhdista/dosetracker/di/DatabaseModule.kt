package com.yhdista.dosetracker.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yhdista.dosetracker.data.local.AppDatabase
import com.yhdista.dosetracker.data.local.dao.DoseDao
import com.yhdista.dosetracker.data.local.dao.MedicationDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        ).addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                db.execSQL("INSERT INTO medications (name, dosage, unit, frequency, reminderTime) VALUES ('Paracetamol', 500.0, 'mg', 'Every 6 hours', '08:00')")
                db.execSQL("INSERT INTO medications (name, dosage, unit, frequency, reminderTime) VALUES ('Ibuprofen', 400.0, 'mg', 'Twice a day', '09:00')")
                db.execSQL("INSERT INTO medications (name, dosage, unit, frequency, reminderTime) VALUES ('Amoxicillin', 500.0, 'mg', 'Three times a day', '07:00')")
                db.execSQL("INSERT INTO medications (name, dosage, unit, frequency, reminderTime) VALUES ('Vitamin C', 1000.0, 'mg', 'Daily', '10:00')")
                db.execSQL("INSERT INTO medications (name, dosage, unit, frequency, reminderTime) VALUES ('Lisinopril', 10.0, 'mg', 'Daily', '08:30')")
            }
        }).build()
    }

    @Provides
    fun provideMedicationDao(database: AppDatabase): MedicationDao {
        return database.medicationDao()
    }

    @Provides
    fun provideDoseDao(database: AppDatabase): DoseDao {
        return database.doseDao()
    }
}
