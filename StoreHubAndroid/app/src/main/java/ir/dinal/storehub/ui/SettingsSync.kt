package ir.dinal.storehub.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import ir.dinal.storehub.data.*
import ir.dinal.storehub.worker.NotificationHelper
import ir.dinal.storehub.worker.WorkerScheduler
import kotlinx.coroutines.launch

@Composable
fun SyncScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val store = remember { LocalStore.get(ctx) }
    val prefs = remember { WooPrefs(ctx) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var page by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("سینک فقط کاتالوگ، قیمت، SKU، دسته‌بندی و تصویر را دریافت می‌کند؛ موجودی محلی دست نمی‌خورد.") }

    DinalScreen(nav, "سینک WooCommerce") { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                DinalHero("WooCommerce → DINAL", "اتصال مستقیم گوشی به فروشگاه اینترنتی") {
                    Icon(Icons.Rounded.Sync, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(42.dp))
                }
            }
            item {
                SectionCard("وضعیت اتصال") {
                    Text(prefs.baseUrl.ifBlank { "آدرس سایت هنوز تنظیم نشده" }, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssistChip(onClick = {}, label = { Text(if (prefs.hasKey()) "Key ثبت شده" else "Key ندارد") })
                        AssistChip(onClick = {}, label = { Text(if (prefs.hasSecret()) "Secret ثبت شده" else "Secret ندارد") })
                    }
                    if (page > 0 && busy) Text("در حال دریافت صفحه $page…")
                    Busy(busy)
                    Text(message, style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = {
                            scope.launch {
                                busy = true; page = 0
                                runCatching { store.syncWoo { p -> page = p } }
                                    .onSuccess { message = "افزوده: ${it.added} • بروزشده: ${it.updated} • خطا: ${it.failed}\n${it.message}" }
                                    .onFailure { message = it.message ?: "خطا در سینک" }
                                busy = false
                            }
                        },
                        enabled = !busy && prefs.hasKey() && prefs.hasSecret() && prefs.baseUrl.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) { Icon(Icons.Rounded.CloudDownload, null); Spacer(Modifier.width(6.dp)); Text(if (busy) "در حال سینک…" else "سینک محصولات") }
                    OutlinedButton(onClick = { nav.navigate("settings") }, modifier = Modifier.fillMaxWidth()) { Text("تنظیم اتصال WooCommerce") }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(activity: Activity, nav: NavHostController) {
    val ctx = LocalContext.current
    val prefs = remember { WooPrefs(ctx) }
    val store = remember { LocalStore.get(ctx) }
    val backup = remember { BackupManager(ctx) }
    val scope = rememberCoroutineScope()

    var base by remember { mutableStateOf(prefs.baseUrl) }
    var version by remember { mutableStateOf(prefs.apiVersion) }
    var key by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var auto by remember { mutableStateOf(prefs.autoSync) }
    var minutes by remember { mutableStateOf(prefs.autoSyncMinutes.toString()) }
    var queryAuth by remember { mutableStateOf(prefs.queryStringAuth) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var notifyAllowed by remember { mutableStateOf(NotificationHelper.canNotify(ctx)) }

    fun candidate() = WooSettings(
        baseUrl = base.trim().trimEnd('/'),
        apiVersion = version.trim().trim('/').ifBlank { "wc/v3" },
        consumerKey = key.ifBlank { prefs.consumerKey() },
        consumerSecret = secret.ifBlank { prefs.consumerSecret() },
        autoSync = auto,
        autoSyncMinutes = minutes.toIntOrNull() ?: 60,
        queryStringAuth = queryAuth
    )

    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notifyAllowed = granted || NotificationHelper.canNotify(ctx)
        if (notifyAllowed) NotificationHelper.test(ctx)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching { backup.exportTo(uri) }.onSuccess { message = "بکاپ ذخیره شد." }.onFailure { message = it.message }
            busy = false
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching { backup.importFrom(uri) }
                .onSuccess { base = prefs.baseUrl; version = prefs.apiVersion; auto = prefs.autoSync; minutes = prefs.autoSyncMinutes.toString(); queryAuth = prefs.queryStringAuth; message = "بازیابی انجام شد. کلید WooCommerce را دوباره وارد کن." }
                .onFailure { message = it.message }
            busy = false
        }
    }

    DinalScreen(nav, "تنظیمات") { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 34.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard("اتصال WooCommerce", subtitle = "کلیدها فقط روی همین گوشی ذخیره می‌شوند") {
                    OutlinedTextField(base, { base = it }, label = { Text("آدرس سایت") }, placeholder = { Text("https://example.com") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(version, { version = it }, label = { Text("نسخه API") }, placeholder = { Text("wc/v3") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(key, { key = it }, label = { Text(if (prefs.hasKey()) "Consumer Key — قبلاً ذخیره شده" else "Consumer Key") }, placeholder = { Text(if (prefs.hasKey()) "برای حفظ مقدار فعلی خالی بگذار" else "ck_…") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(secret, { secret = it }, label = { Text(if (prefs.hasSecret()) "Consumer Secret — قبلاً ذخیره شده" else "Consumer Secret") }, placeholder = { Text(if (prefs.hasSecret()) "برای حفظ مقدار فعلی خالی بگذار" else "cs_…") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) { Text("Query-string auth"); Text("فقط اگر هاست Authorization Header را حذف می‌کند", style = MaterialTheme.typography.bodySmall) }
                        Switch(queryAuth, { queryAuth = it })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) { Text("سینک خودکار"); Text("در پس‌زمینه و فقط با اینترنت", style = MaterialTheme.typography.bodySmall) }
                        Switch(auto, { auto = it })
                    }
                    if (auto) OutlinedTextField(minutes, { minutes = it }, label = { Text("فاصله سینک؛ حداقل ۱۵ دقیقه") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val s = candidate()
                                if (!s.baseUrl.startsWith("https://")) { message = "آدرس سایت باید با https:// شروع شود."; return@Button }
                                prefs.baseUrl = s.baseUrl; prefs.apiVersion = s.apiVersion; prefs.autoSync = s.autoSync; prefs.autoSyncMinutes = s.autoSyncMinutes; prefs.queryStringAuth = s.queryStringAuth
                                if (key.isNotBlank()) prefs.setConsumerKey(key)
                                if (secret.isNotBlank()) prefs.setConsumerSecret(secret)
                                key = ""; secret = ""; WorkerScheduler.scheduleWoo(ctx); message = "تنظیمات WooCommerce ذخیره شد."
                            }, modifier = Modifier.weight(1f)
                        ) { Icon(Icons.Rounded.Save, null); Spacer(Modifier.width(5.dp)); Text("ذخیره") }
                        OutlinedButton(
                            onClick = { scope.launch { busy = true; runCatching { store.testWoo(candidate()) }.onSuccess { message = it.message }.onFailure { message = it.message }; busy = false } },
                            enabled = !busy, modifier = Modifier.weight(1f)
                        ) { Icon(Icons.Rounded.WifiTethering, null); Spacer(Modifier.width(5.dp)); Text("تست") }
                    }
                }
            }

            item {
                SectionCard("انتشار هوشمند محصول") {
                    Text("تنظیم سه سایت WooCommerce و اتصال OpenAI برای ثبت هوشمند محصول و دستیار DINAL.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { nav.navigate("publishing_settings") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.AutoAwesome, null); Spacer(Modifier.width(6.dp)); Text("تنظیم ۳ سایت و هوش مصنوعی") }
                }
            }

            item {
                SectionCard("اعلان و آلارم", subtitle = "چک‌ها و قرارها حتی وقتی اپ بسته است یادآوری می‌شوند") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (notifyAllowed) "اعلان‌ها فعال‌اند" else "اعلان‌ها اجازه ندارند", fontWeight = FontWeight.SemiBold)
                        AssistChip(onClick = {}, label = { Text(if (notifyAllowed) "فعال" else "خاموش") }, leadingIcon = { Icon(if (notifyAllowed) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff, null, Modifier.size(16.dp)) })
                    }
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= 33 && !NotificationHelper.canNotify(ctx)) notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else { NotificationHelper.test(ctx); message = "اعلان آزمایشی ارسال شد." }
                    }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.NotificationsActive, null); Spacer(Modifier.width(6.dp)); Text("تست آلارم الان") }
                    OutlinedButton(onClick = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply { putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName) }
                        activity.startActivity(intent)
                    }, modifier = Modifier.fillMaxWidth()) { Text("تنظیمات اعلان گوشی") }
                }
            }

            item {
                SectionCard("چاپ لیبل") {
                    Text("لیبل استاندارد ۵۰×۳۰، چاپ سیستمی و چاپ مستقیم بلوتوث TSPL.")
                    Button(onClick = { nav.navigate("printer") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Print, null); Spacer(Modifier.width(6.dp)); Text("تنظیم چاپگر بلوتوث") }
                }
            }

            item {
                SectionCard("پشتیبان‌گیری محلی") {
                    Text("کالاها، موجودی، فروش، خرید، چک و قرارها داخل فایل JSON ذخیره می‌شوند. کلید WooCommerce عمداً در بکاپ نیست.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { exportLauncher.launch("dinal-storehub-${todayPersian().replace('/', '-')}.json") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Backup, null); Spacer(Modifier.width(6.dp)); Text("گرفتن بکاپ") }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Restore, null); Spacer(Modifier.width(6.dp)); Text("بازیابی بکاپ") }
                }
            }

            item { Busy(busy); message?.let { SuccessText(it) } }

            item {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f))) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("DINAL StoreHub", fontWeight = FontWeight.Bold)
                        Text("نسخه 16.3.0 • Android Only", style = MaterialTheme.typography.bodySmall)
                        Text("نویسنده: Mohammad Jelviz", style = MaterialTheme.typography.bodySmall)
                        Text("بدون بک‌اند، بدون VPS؛ اطلاعات اصلی روی همین گوشی ذخیره می‌شود.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
