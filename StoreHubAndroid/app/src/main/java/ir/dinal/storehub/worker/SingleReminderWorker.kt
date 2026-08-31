package ir.dinal.storehub.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SingleReminderWorker(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val title = inputData.getString("title") ?: "یادآوری دینال"
        val text = inputData.getString("text") ?: "یک یادآوری ثبت‌شده داری."
        val id = inputData.getInt("notification_id", (System.currentTimeMillis() % Int.MAX_VALUE).toInt())
        NotificationHelper.show(context, id, title, text)
        return Result.success()
    }
}
