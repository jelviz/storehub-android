package ir.dinal.storehub.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ir.dinal.storehub.data.LocalStore

class StockPushWorker(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        LocalStore.get(context).pushChannelStock()
        Result.success()
    }.getOrElse { Result.retry() }
}
