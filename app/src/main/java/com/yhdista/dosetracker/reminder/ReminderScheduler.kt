package com.yhdista.dosetracker.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.yhdista.dosetracker.domain.model.Medication
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderScheduler(
    private val context: Context
) : DoseReminderScheduler {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun scheduleReminder(medication: Medication) {
        val reminderTimeStr = medication.reminderTime ?: return
        val time = try {
            LocalTime.parse(reminderTimeStr)
        } catch (_: Exception) {
            return
        }

        val now = ZonedDateTime.now(ZoneId.systemDefault())
        var scheduledTime = now.withHour(time.hour).withMinute(time.minute).withSecond(0).withNano(0)

        if (scheduledTime.isBefore(now)) {
            scheduledTime = scheduledTime.plusDays(1)
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("medicationId", medication.id)
            putExtra("medicationName", medication.name)
            putExtra("dosage", "${medication.dosage} ${medication.unit}")
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            medication.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    scheduledTime.toInstant().toEpochMilli(),
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    scheduledTime.toInstant().toEpochMilli(),
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                scheduledTime.toInstant().toEpochMilli(),
                pendingIntent
            )
        }
    }

    override fun cancelReminder(medicationId: Long) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            medicationId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }
}
