package ir.dinal.storehub.worker

import android.content.Context
import androidx.work.*
import ir.dinal.storehub.data.WooPrefs
import java.util.concurrent.TimeUnit

object WorkerScheduler{
    fun scheduleAll(context:Context){scheduleReminders(context);scheduleWoo(context)}
    fun scheduleReminders(context:Context){val req=PeriodicWorkRequestBuilder<ReminderWorker>(15,TimeUnit.MINUTES).build();WorkManager.getInstance(context).enqueueUniquePeriodicWork("storehub_local_reminders",ExistingPeriodicWorkPolicy.UPDATE,req)}
    fun scheduleWoo(context:Context){val wm=WorkManager.getInstance(context);val p=WooPrefs(context);if(!p.autoSync){wm.cancelUniqueWork("storehub_woo_sync");return};val min=p.autoSyncMinutes.coerceAtLeast(15).toLong();val req=PeriodicWorkRequestBuilder<WooSyncWorker>(min,TimeUnit.MINUTES).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build();wm.enqueueUniquePeriodicWork("storehub_woo_sync",ExistingPeriodicWorkPolicy.UPDATE,req)}
}
