package ir.dinal.storehub.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ir.dinal.storehub.data.LocalStore
import ir.dinal.storehub.data.WooPrefs

class WooSyncWorker(private val context:Context,params:WorkerParameters):CoroutineWorker(context,params){
    override suspend fun doWork():Result{val p=WooPrefs(context);if(!p.autoSync||!p.hasKey()||!p.hasSecret()||p.baseUrl.isBlank())return Result.success();return runCatching{LocalStore.get(context).syncWoo();Result.success()}.getOrElse{Result.retry()}}
}
