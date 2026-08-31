package ir.dinal.storehub.worker

import ir.dinal.storehub.util.MoneyFormat

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ir.dinal.storehub.data.LocalStore
import java.time.ZonedDateTime
import java.time.ZoneId

/**
 * Backup periodic reminder check. Precise one-time reminders are scheduled by ReminderScheduler;
 * this worker catches reminders after reboot/doze or when a previous work item was delayed.
 */
class ReminderWorker(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        val store = LocalStore.get(context)
        val zone = ZoneId.of("Asia/Tehran")
        val localNow = ZonedDateTime.now(zone)
        val today = localNow.toLocalDate().toEpochDay()
        val now = System.currentTimeMillis()
        val seen = context.getSharedPreferences("dinal_seen_reminders", Context.MODE_PRIVATE)

        store.checks()
            .filter { it.status == 1 && localNow.hour >= 9 && today >= it.dueEpochDay - it.reminderDaysBefore && today <= it.dueEpochDay }
            .forEach {
                val key = "check:${it.id}:${it.dueEpochDay}"
                if (!seen.getBoolean(key, false)) {
                    NotificationHelper.show(context, (100000 + it.id).toInt(), "سررسید چک: ${it.title}", "${it.dueDatePersian} • مبلغ ${MoneyFormat.toman(it.amount)}")
                    seen.edit().putBoolean(key, true).apply()
                }
            }

        store.appointments()
            .filter { it.status == 1 && now >= it.startsAtEpochMillis - it.reminderMinutesBefore * 60_000L && now <= it.startsAtEpochMillis + 2 * 60 * 60_000L }
            .forEach {
                val key = "appointment:${it.id}:${it.startsAtEpochMillis}"
                if (!seen.getBoolean(key, false)) {
                    NotificationHelper.show(context, (200000 + it.id).toInt(), "قرار: ${it.title}", "${it.datePersian} ساعت ${it.time}${it.personName?.let { p -> " • $p" }.orEmpty()}")
                    seen.edit().putBoolean(key, true).apply()
                }
            }
        Result.success()
    }.getOrElse { Result.retry() }
}
