package com.yhdista.dosetracker.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.model.DoseStatus
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlin.time.Clock
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

class RescheduleWorker(
    context: Context,
    params: WorkerParameters,
    private val repository: MedicationRepository,
    private val scheduler: DoseReminderScheduler
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val dosesResult = repository.getDosesForDate(today)
            .filter { it !is Data.Loading }
            .first()

        if (dosesResult is Data.Success) {
            val now = Clock.System.now()
            dosesResult.data
                .filter { it.status == DoseStatus.PENDING && it.timestamp > now }
                .forEach { dose -> scheduler.scheduleReminder(dose.id, dose.timestamp) }
        }

        return Result.success()
    }
}
