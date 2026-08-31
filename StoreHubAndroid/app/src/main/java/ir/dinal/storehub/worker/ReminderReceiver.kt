package ir.dinal.storehub.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("notification_id", 70001)
        val title = intent.getStringExtra("title") ?: "یادآوری دینال"
        val text = intent.getStringExtra("text") ?: "یک یادآوری در StoreHub داری."
        val seenKey = intent.getStringExtra("seen_key")
        val prefs = context.getSharedPreferences("dinal_seen_reminders", Context.MODE_PRIVATE)
        if (!seenKey.isNullOrBlank() && prefs.getBoolean(seenKey, false)) return
        if (NotificationHelper.show(context, id, title, text) && !seenKey.isNullOrBlank()) {
            prefs.edit().putBoolean(seenKey, true).apply()
        }
    }
}
