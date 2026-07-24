package com.yhdista.dosetracker

import android.app.Application
import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.yhdista.dosetracker.di.appModule
import com.yhdista.dosetracker.di.dataModule
import com.yhdista.dosetracker.di.viewModelModule
import com.yhdista.dosetracker.reminder.RescheduleWorker
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import java.util.concurrent.TimeUnit
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.factory.KoinWorkerFactory
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import com.yhdista.dosetracker.core.AppLogger
import com.yhdista.dosetracker.core.AndroidLogcatEngine
import com.yhdista.dosetracker.core.FileLogEngine

class DoseTrackerApp : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        
        // Plant Timber DebugTree for console logging
        timber.log.Timber.plant(timber.log.Timber.DebugTree())
        
        // Initialize Genius Log-Driven Development framework
        AppLogger.init(
            AndroidLogcatEngine(),
            FileLogEngine(this)
        )
        AppLogger.i("DoseTrackerApp", "Application onCreate: Log System Initialized")

        startKoin {
            androidContext(this@DoseTrackerApp)
            workManagerFactory()
            modules(appModule, dataModule, viewModelModule)
        }
        scheduleDailyDoseGeneration(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(KoinWorkerFactory())
            .build()
}

private fun scheduleDailyDoseGeneration(context: Context) {
    val request = PeriodicWorkRequestBuilder<RescheduleWorker>(1, TimeUnit.DAYS)
        .setInitialDelay(millisUntilNextMidnight(), TimeUnit.MILLISECONDS)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "daily-dose-generation",
        ExistingPeriodicWorkPolicy.KEEP,
        request
    )
}

private fun millisUntilNextMidnight(): Long {
    val zone = TimeZone.currentSystemDefault()
    val now = Clock.System.now()
    val today = now.toLocalDateTime(zone).date
    val nextMidnight = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
    return (nextMidnight - now).inWholeMilliseconds
}
