package ir.dinal.storehub.util

import android.content.Context

class PrinterPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("storehub_printer", Context.MODE_PRIVATE)
    var macAddress: String
        get() = prefs.getString("mac", "") ?: ""
        set(value) = prefs.edit().putString("mac", value).apply()
    var printerName: String
        get() = prefs.getString("name", "") ?: ""
        set(value) = prefs.edit().putString("name", value).apply()
    var protocol: String
        get() = prefs.getString("protocol", "TSPL") ?: "TSPL"
        set(value) = prefs.edit().putString("protocol", value).apply()
    fun clear() = prefs.edit().clear().apply()
}
