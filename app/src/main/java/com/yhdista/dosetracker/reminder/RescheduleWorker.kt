package com.yhdista.dosetracker.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.yhdista.dosetracker.core.Data
import com.yhdista.dosetracker.domain.repository.MedicationRepository
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

class RescheduleWorker(
    context: Context,
    params: WorkerParameters,
    private val repository: MedicationRepository,
    private val scheduler: DoseReminderScheduler
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val medicationsResult = repository.getMedications()
            .filter { it is Data.Success }
            .first()

        if (medicationsResult is Data.Success) {
            medicationsResult.data.forEach { medication ->
                if (medication.reminderTime != null) {
                    scheduler.scheduleReminder(medication)
                }
            }
        }

        return Result.success()
    }
}
