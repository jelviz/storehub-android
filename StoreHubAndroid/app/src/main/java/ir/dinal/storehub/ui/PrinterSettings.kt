package ir.dinal.storehub.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import ir.dinal.storehub.data.ProductEntity
import ir.dinal.storehub.util.LabelPrinter
import ir.dinal.storehub.util.PrinterPrefs

@Composable
fun PrinterSettingsScreen(activity: Activity, nav: NavHostController) {
    val context = LocalContext.current
    val prefs = remember { PrinterPrefs(context) }
    var selectedMac by remember { mutableStateOf(prefs.macAddress) }
    var selectedName by remember { mutableStateOf(prefs.printerName) }
    var devices by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var hasPermission by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) devices = runCatching { LabelPrinter.pairedDevices(context) }.getOrDefault(emptyList())
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) devices = runCatching { LabelPrinter.pairedDevices(context) }.getOrDefault(emptyList())
    }

    DinalScreen(nav, "چاپگر لیبل") { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 30.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                DinalHero("لیبل ۵۰×۳۰ میلی‌متر", "چاپ مستقیم بلوتوث برای چاپگرهای سازگار با TSPL") {
                    Icon(Icons.Rounded.Print, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(42.dp))
                }
            }
            item {
                SectionCard("چاپگر انتخاب‌شده") {
                    if (selectedMac.isBlank()) Text("چاپگری انتخاب نشده است.")
                    else {
                        Text(selectedName.ifBlank { "Bluetooth Printer" }, fontWeight = FontWeight.Bold)
                        Text(selectedMac, style = MaterialTheme.typography.bodySmall)
                        AssistChip(onClick = {}, label = { Text("TSPL") }, leadingIcon = { Icon(Icons.Rounded.CheckCircle, null, Modifier.size(16.dp)) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { LabelPrinter.printSystem(activity, demoProduct(), LabelPrinter.LabelMode.FULL, "") }, modifier = Modifier.weight(1f)) { Text("تست چاپ سیستمی") }
                        Button(
                            onClick = {
                                if (selectedMac.isBlank()) message = "اول یک چاپگر بلوتوث انتخاب کن."
                                else LabelPrinter.printBluetooth(context, demoProduct(), LabelPrinter.LabelMode.FULL, "", selectedMac) { ok, msg -> activity.runOnUiThread { message = if (ok) "لیبل آزمایشی برای چاپگر ارسال شد." else msg } }
                            },
                            modifier = Modifier.weight(1f), enabled = selectedMac.isNotBlank()
                        ) { Text("تست بلوتوث") }
                    }
                    message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            if (!hasPermission) {
                item {
                    SectionCard("دسترسی بلوتوث") {
                        Text("برای دیدن چاپگرهای Pair شده و چاپ مستقیم، اجازه اتصال بلوتوث لازم است.")
                        Button(onClick = {
                            if (Build.VERSION.SDK_INT >= 31) permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Bluetooth, null); Spacer(Modifier.width(6.dp)); Text("دادن دسترسی بلوتوث") }
                    }
                }
            } else {
                item { Text("دستگاه‌های Pair شده", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                if (devices.isEmpty()) item {
                    SectionCard("چاپگر پیدا نشد") {
                        Text("اول از تنظیمات بلوتوث گوشی، چاپگر را Pair کن و بعد به این صفحه برگرد.")
                    }
                }
                items(devices, key = { it.second }) { (name, mac) ->
                    Card(
                        onClick = {
                            selectedMac = mac; selectedName = name
                            prefs.macAddress = mac; prefs.printerName = name; prefs.protocol = "TSPL"
                            message = "$name به‌عنوان چاپگر لیبل ذخیره شد."
                        },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = if (selectedMac == mac) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column { Text(name, fontWeight = FontWeight.Bold); Text(mac, style = MaterialTheme.typography.bodySmall) }
                            if (selectedMac == mac) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

private fun demoProduct() = ProductEntity(
    id = 1,
    name = "لیبل آزمایشی دینال",
    sku = "DINAL-TEST",
    barcode = "DINAL-TEST-001",
    internalCode = "D-001",
    price = 125000.0,
    isEnabledForStore = true
)
