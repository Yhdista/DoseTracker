package com.yhdista.dosetracker.di

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.datastore.preferences.preferencesDataStore
import com.yhdista.dosetracker.data.local.AppDatabase
import com.yhdista.dosetracker.data.local.getDatabaseBuilder
import com.yhdista.dosetracker.data.local.getRoomDatabase
import com.yhdista.dosetracker.data.repository.CycleRepositoryImpl
import com.yhdista.dosetracker.data.repository.DatabaseMaintenanceImpl
import com.yhdista.dosetracker.data.repository.DoseRepositoryImpl
import com.yhdista.dosetracker.data.repository.MedicationRepositoryImpl
import com.yhdista.dosetracker.data.repository.ScheduleRepositoryImpl
import com.yhdista.dosetracker.data.repository.SettingsRepositoryImpl
import com.yhdista.dosetracker.domain.repository.CycleRepository
import com.yhdista.dosetracker.domain.repository.DatabaseMaintenance
import com.yhdista.dosetracker.domain.repository.DoseRepository
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import com.yhdista.dosetracker.domain.repository.ScheduleRepository
import com.yhdista.dosetracker.domain.repository.SettingsRepository
import com.yhdista.dosetracker.domain.usecase.CreateCycleUseCase
import com.yhdista.dosetracker.domain.usecase.ManageScheduleUseCase
import com.yhdista.dosetracker.reminder.CycleLifecycleManager
import com.yhdista.dosetracker.reminder.DoseGenerator
import org.koin.dsl.module

private val Context.dataStore by preferencesDataStore(name = "app_settings")

val dataModule = module {
    single { get<Context>().dataStore }
    single<SettingsRepository> { SettingsRepositoryImpl(get()) }
    single {
        val context = get<Context>()
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        getRoomDatabase(getDatabaseBuilder(context), debugTooling = debuggable)
    }

    // DAOs stay private to the data layer: repositories are the only consumers.
    single<MedicationRepository> { MedicationRepositoryImpl(get<AppDatabase>().medicationDao()) }
    single<DoseRepository> { DoseRepositoryImpl(get<AppDatabase>().doseDao()) }
    single<ScheduleRepository> {
        ScheduleRepositoryImpl(get<AppDatabase>().reminderScheduleDao(), get<AppDatabase>().periodTimeDao())
    }
    single<CycleRepository> { CycleRepositoryImpl(get<AppDatabase>().cycleDao()) }
    single<DatabaseMaintenance> { DatabaseMaintenanceImpl(get<AppDatabase>()) }

    single { CycleLifecycleManager(get()) }
    single { CreateCycleUseCase(get(), get()) }
    single { ManageScheduleUseCase(get(), get(), get(), get()) }
    single { DoseGenerator(get(), get(), get(), get(), get(), get()) }
}
