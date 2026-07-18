package com.yhdista.dosetracker

import android.app.Application
import androidx.work.Configuration
import com.yhdista.dosetracker.di.appModule
import com.yhdista.dosetracker.di.dataModule
import com.yhdista.dosetracker.di.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.factory.KoinWorkerFactory
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin

class DoseTrackerApp : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@DoseTrackerApp)
            workManagerFactory()
            modules(appModule, dataModule, viewModelModule)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(KoinWorkerFactory())
            .build()
}
