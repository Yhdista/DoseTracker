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

    private val acceptedActions = setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        "android.intent.action.QUICKBOOT_POWERON",
        AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        // Alarms are RTC epoch-millis: after a timezone/clock change the wall-clock
        // times are wrong until doses are re-generated and re-armed.
        Intent.ACTION_TIMEZONE_CHANGED,
        Intent.ACTION_TIME_CHANGED,
        ACTION_MIDNIGHT_GENERATION,
    )

    override fun onReceive(context: Context, intent: Intent) {
        com.yhdista.dosetracker.core.AppLogger.i("RescheduleReceiver", "onReceive action=${intent.action}")
        if (intent.action !in acceptedActions) return

        // Re-arm the next midnight tick first: alarms are wiped by reboot and
        // permission revocation, and midnight moves with the timezone.
        scheduleNextMidnightAlarm(context)

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
