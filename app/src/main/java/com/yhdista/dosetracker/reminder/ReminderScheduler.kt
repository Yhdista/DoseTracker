package com.yhdista.dosetracker.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.datetime.Instant

internal class ReminderScheduler(
    private val context: Context
) : DoseReminderScheduler {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun scheduleReminder(doseId: Long, at: Instant) {
        com.yhdista.dosetracker.core.AppLogger.d("ReminderScheduler", "scheduleReminder(doseId=$doseId, at=$at)")
        schedule(ReminderReceiver::class.java, doseId, at)
    }

    override fun cancelReminder(doseId: Long) {
        com.yhdista.dosetracker.core.AppLogger.d("ReminderScheduler", "cancelReminder(doseId=$doseId)")
        cancel(ReminderReceiver::class.java, doseId)
    }

    override fun scheduleMissedTimeout(doseId: Long, at: Instant) {
        com.yhdista.dosetracker.core.AppLogger.d("ReminderScheduler", "scheduleMissedTimeout(doseId=$doseId, at=$at)")
        schedule(MissedDoseReceiver::class.java, doseId, at)
    }

    override fun cancelMissedTimeout(doseId: Long) {
        com.yhdista.dosetracker.core.AppLogger.d("ReminderScheduler", "cancelMissedTimeout(doseId=$doseId)")
        cancel(MissedDoseReceiver::class.java, doseId)
    }

    // Reminder vs missed-timeout PendingIntents are distinguished solely by the target
    // receiver class — request codes intentionally collide (doseId truncated to Int).
    private fun schedule(receiver: Class<*>, doseId: Long, at: Instant) {
        // FLAG_UPDATE_CURRENT always creates, so the intent is never null here.
        val pendingIntent = pendingIntent(receiver, doseId, PendingIntent.FLAG_UPDATE_CURRENT)!!
        val triggerAtMillis = at.toEpochMilliseconds()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun cancel(receiver: Class<*>, doseId: Long) {
        pendingIntent(receiver, doseId, PendingIntent.FLAG_NO_CREATE)?.let(alarmManager::cancel)
    }

    private fun pendingIntent(receiver: Class<*>, doseId: Long, flags: Int): PendingIntent? {
        val intent = Intent(context, receiver).apply { putExtra("doseId", doseId) }
        return PendingIntent.getBroadcast(
            context,
            doseId.toInt(),
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
