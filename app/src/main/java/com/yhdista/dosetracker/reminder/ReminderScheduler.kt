package com.yhdista.dosetracker.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.datetime.Instant

class ReminderScheduler(
    private val context: Context
) : DoseReminderScheduler {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun scheduleReminder(doseId: Long, at: Instant) {
        com.yhdista.dosetracker.core.AppLogger.d("ReminderScheduler", "scheduleReminder(doseId=$doseId, at=$at)")
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("doseId", doseId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            doseId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAtMillis = at.toEpochMilliseconds()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    override fun cancelReminder(doseId: Long) {
        com.yhdista.dosetracker.core.AppLogger.d("ReminderScheduler", "cancelReminder(doseId=$doseId)")
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            doseId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }

    override fun scheduleMissedTimeout(doseId: Long, at: Instant) {
        com.yhdista.dosetracker.core.AppLogger.d("ReminderScheduler", "scheduleMissedTimeout(doseId=$doseId, at=$at)")
        val intent = Intent(context, MissedDoseReceiver::class.java).apply {
            putExtra("doseId", doseId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            doseId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAtMillis = at.toEpochMilliseconds()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    override fun cancelMissedTimeout(doseId: Long) {
        com.yhdista.dosetracker.core.AppLogger.d("ReminderScheduler", "cancelMissedTimeout(doseId=$doseId)")
        val intent = Intent(context, MissedDoseReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            doseId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }
}
