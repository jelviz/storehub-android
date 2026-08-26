package ir.dinal.storehub.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ir.dinal.storehub.data.LocalStore
import java.time.LocalDate
import java.time.ZoneId

class ReminderWorker(private val context:Context,params:WorkerParameters):CoroutineWorker(context,params){
    override suspend fun doWork():Result=runCatching{
        val store=LocalStore.get(context);val today=LocalDate.now(ZoneId.of("Asia/Tehran")).toEpochDay();val now=System.currentTimeMillis();val checks=store.checks().filter{it.status==1&&today>=it.dueEpochDay-it.reminderDaysBefore&&today<=it.dueEpochDay};val apps=store.appointments().filter{it.status==1&&now>=it.startsAtEpochMillis-it.reminderMinutesBefore*60_000L&&now<=it.startsAtEpochMillis+60*60_000L}
        val nm=context.getSystemService(NotificationManager::class.java);nm.createNotificationChannel(NotificationChannel("storehub_local_reminders","یادآوری‌های StoreHub",NotificationManager.IMPORTANCE_HIGH));val seen=context.getSharedPreferences("storehub_local_seen",Context.MODE_PRIVATE)
        fun notifyOnce(key:String,id:Int,title:String,text:String){if(seen.getBoolean(key,false))return;val n=NotificationCompat.Builder(context,"storehub_local_reminders").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)).setAutoCancel(true).build();if(android.os.Build.VERSION.SDK_INT<33||ActivityCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED){NotificationManagerCompat.from(context).notify(id,n);seen.edit().putBoolean(key,true).apply()}}
        checks.forEach{notifyOnce("c:${it.id}:${it.dueEpochDay}",(it.id*31).toInt(),"سررسید چک: ${it.title}","سررسید ${it.dueDatePersian} — مبلغ ${it.amount.toLong()}")}
        apps.forEach{notifyOnce("a:${it.id}:${it.startsAtEpochMillis}",(it.id*37).toInt(),"قرار: ${it.title}","${it.datePersian} ساعت ${it.time}${it.personName?.let{x->" — $x"}.orEmpty()}")}
        Result.success()
    }.getOrElse{Result.retry()}
}
