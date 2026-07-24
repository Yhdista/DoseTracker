package com.yhdista.dosetracker.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

const val ACTION_MIDNIGHT_GENERATION = "com.yhdista.dosetracker.ACTION_MIDNIGHT_GENERATION"

/**
 * Self-perpetuating exact alarm at local midnight driving daily dose generation.
 * The periodic WorkManager job stays as a fallback, but WorkManager only guarantees
 * "at least a day apart" and Doze defers it — doses whose time passed before a late
 * run would get no reminder. An exact alarm doesn't drift and re-anchors on TZ change.
 */
internal fun scheduleNextMidnightAlarm(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, RescheduleReceiver::class.java).apply {
        action = ACTION_MIDNIGHT_GENERATION
    }
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val triggerAtMillis = nextMidnightEpochMillis()
    com.yhdista.dosetracker.core.AppLogger.d("MidnightAlarm", "Scheduling midnight generation alarm at epochMillis=$triggerAtMillis")

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    } else {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }
}

internal fun nextMidnightEpochMillis(): Long {
    val zone = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(zone).date
    return today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds()
}
