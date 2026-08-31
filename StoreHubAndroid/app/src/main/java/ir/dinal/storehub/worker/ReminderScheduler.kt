package ir.dinal.storehub.worker

import ir.dinal.storehub.util.MoneyFormat

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import ir.dinal.storehub.data.AppointmentEntity
import ir.dinal.storehub.data.IssuedCheckEntity
import ir.dinal.storehub.data.Jalali
import java.time.LocalTime
import java.time.ZoneId

/**
 * Primary local reminder scheduler. Uses AlarmManager so reminders are independent
 * of the app UI and do not require a backend. Periodic WorkManager is kept as a safety net.
 */
object ReminderScheduler {
    private val zone = ZoneId.of("Asia/Tehran")

    fun scheduleCheck(context: Context, item: IssuedCheckEntity) {
        val date = Jalali.parse(item.dueDatePersian) ?: return
        val dueDate = Jalali.toGregorian(date.year, date.month, date.day)
        val trigger = dueDate
            .minusDays(item.reminderDaysBefore.coerceAtLeast(0).toLong())
            .atTime(LocalTime.of(9, 0))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        schedule(
            context = context,
            requestCode = checkRequestCode(item.id),
            triggerAt = trigger,
            title = "سررسید چک: ${item.title}",
            text = "${item.dueDatePersian} • مبلغ ${MoneyFormat.toman(item.amount)}",
            seenKey = "check:${item.id}:${item.dueEpochDay}"
        )
    }

    fun scheduleAppointment(context: Context, item: AppointmentEntity) {
        val trigger = item.startsAtEpochMillis - item.reminderMinutesBefore.coerceAtLeast(0) * 60_000L
        schedule(
            context = context,
            requestCode = appointmentRequestCode(item.id),
            triggerAt = trigger,
            title = "قرار: ${item.title}",
            text = "${item.datePersian} ساعت ${item.time}${item.personName?.let { " • $it" }.orEmpty()}",
            seenKey = "appointment:${item.id}:${item.startsAtEpochMillis}"
        )
    }

    private fun schedule(context: Context, requestCode: Int, triggerAt: Long, title: String, text: String, seenKey: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pending = reminderPendingIntent(context, requestCode, title, text, seenKey)
        val safeTrigger = triggerAt.coerceAtLeast(System.currentTimeMillis() + 1_500L)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, safeTrigger, pending)
    }

    fun cancelCheck(context: Context, id: Long) = cancel(context, checkRequestCode(id))
    fun cancelAppointment(context: Context, id: Long) = cancel(context, appointmentRequestCode(id))

    private fun cancel(context: Context, requestCode: Int) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(pending)
        pending.cancel()
    }

    private fun reminderPendingIntent(context: Context, requestCode: Int, title: String, text: String, seenKey: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("notification_id", requestCode)
            putExtra("title", title)
            putExtra("text", text)
            putExtra("seen_key", seenKey)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun checkRequestCode(id: Long): Int = (100_000L + (id % 800_000L)).toInt()
    private fun appointmentRequestCode(id: Long): Int = (1_000_000L + (id % 800_000L)).toInt()
}
