package ir.dinal.storehub.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ir.dinal.storehub.data.LocalStore

class ReminderRescheduleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        val store = LocalStore.get(applicationContext)
        store.checks().filter { it.status == 1 }.forEach { ReminderScheduler.scheduleCheck(applicationContext, it) }
        store.appointments().filter { it.status == 1 }.forEach { ReminderScheduler.scheduleAppointment(applicationContext, it) }
        Result.success()
    }.getOrElse { Result.retry() }
}
