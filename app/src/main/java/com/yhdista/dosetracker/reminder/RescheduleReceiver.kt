package com.yhdista.dosetracker.reminder

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        com.yhdista.dosetracker.core.AppLogger.i("RescheduleReceiver", "onReceive action=${intent.action}")
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        ) {
            val workRequest = OneTimeWorkRequestBuilder<RescheduleWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            // Unique + KEEP: boot and quickboot broadcasts can arrive back to back;
            // one reschedule pass is enough.
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "reschedule-alarms",
                ExistingWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
