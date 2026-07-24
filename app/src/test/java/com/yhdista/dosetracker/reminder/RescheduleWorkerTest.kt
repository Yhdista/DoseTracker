package com.yhdista.dosetracker.reminder

import android.content.Context
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RescheduleWorkerTest {

    private val doseGenerator = mock<DoseGenerator>()
    private val worker = RescheduleWorker(mock<Context>(), mock<WorkerParameters>(), doseGenerator)

    @Test
    fun `returns success when dose generation completes`() = runTest {
        whenever(doseGenerator.runForToday()).doSuspendableAnswer { }

        assertEquals(Result.success(), worker.doWork())
    }

    @Test
    fun `returns retry when dose generation fails so a boot-time hiccup does not silence reminders`() = runTest {
        whenever(doseGenerator.runForToday()).doSuspendableAnswer { throw IllegalStateException("db not ready") }

        assertEquals(Result.retry(), worker.doWork())
    }
}
