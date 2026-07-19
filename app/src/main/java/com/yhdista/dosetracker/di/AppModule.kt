package com.yhdista.dosetracker.di

import com.yhdista.dosetracker.reminder.DoseReminderScheduler
import com.yhdista.dosetracker.reminder.NotificationHelper
import com.yhdista.dosetracker.reminder.ReminderScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.worker
import org.koin.dsl.module

val appModule = module {
    single<DoseReminderScheduler> { ReminderScheduler(androidContext()) }
    single { NotificationHelper(androidContext()) }

    worker { params ->
        com.yhdista.dosetracker.reminder.RescheduleWorker(
            params.get(),
            params.get(),
            get()
        )
    }
}
