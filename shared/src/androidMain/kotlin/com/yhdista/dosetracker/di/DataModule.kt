package com.yhdista.dosetracker.di

import android.content.Context
import com.yhdista.dosetracker.data.local.getDatabaseBuilder
import com.yhdista.dosetracker.data.local.getRoomDatabase
import com.yhdista.dosetracker.data.repository.MedicationRepositoryImpl
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import org.koin.dsl.module

val dataModule = module {
    single {
        getRoomDatabase(getDatabaseBuilder(get<Context>()))
    }
    single { get<com.yhdista.dosetracker.data.local.AppDatabase>().medicationDao() }
    single { get<com.yhdista.dosetracker.data.local.AppDatabase>().doseDao() }
    single { get<com.yhdista.dosetracker.data.local.AppDatabase>().reminderScheduleDao() }
    single { get<com.yhdista.dosetracker.data.local.AppDatabase>().periodTimeDao() }
    single { get<com.yhdista.dosetracker.data.local.AppDatabase>().cycleDao() }
    single<MedicationRepository> {
        MedicationRepositoryImpl(
            medicationDao = get(),
            doseDao = get(),
            scheduleDao = get(),
            periodTimeDao = get(),
            cycleDao = get()
        )
    }
    single { com.yhdista.dosetracker.reminder.CycleLifecycleManager(get()) }
    single { com.yhdista.dosetracker.reminder.DoseGenerator(get(), get()) }
}
