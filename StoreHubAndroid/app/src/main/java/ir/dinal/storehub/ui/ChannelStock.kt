package ir.dinal.storehub.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import ir.dinal.storehub.data.ChannelStockRow
import ir.dinal.storehub.data.LocalStore
import ir.dinal.storehub.data.MarketplaceEndpoint
import ir.dinal.storehub.inventory.ChannelIds
import ir.dinal.storehub.inventory.IntegrationMode
import ir.dinal.storehub.inventory.SyncQueueStatus
import kotlinx.coroutines.launch

@Composable
fun ChannelStockScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val store = remember { LocalStore.get(ctx) }
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<ChannelStockRow>>(emptyList()) }
    var endpoints by remember { mutableStateOf(store.marketplaceEndpoints()) }
    var snappUrl by remember { mutableStateOf(endpoints.firstOrNull { it.channelId == ChannelIds.SNAPP }?.baseUrl.orEmpty()) }
    var snappToken by remember { mutableStateOf("") }
    var tapsiUrl by remember { mutableStateOf(endpoints.firstOrNull { it.channelId == ChannelIds.TAPSI }?.baseUrl.orEmpty()) }
    var tapsiToken by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }

    suspend fun load() { rows = store.channelStockRows(); endpoints = store.marketplaceEndpoints() }
    LaunchedEffect(Unit) { load() }

    DinalScreen(nav, "ارسال موجودی کانال‌ها") { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 36.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                DinalHero("StoreHub مرجع است", "موجودی محاسبه‌شده به ووکامرس با REST استاندارد می‌رود. اسنپ و تپسی اگر API فروشنده داشته باشی با HTTPS ارسال می‌شوند؛ وگرنه عدد را دستی کپی می‌کنی.") {
                    Icon(Icons.Rounded.CloudUpload, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(42.dp))
                }
            }
            item {
                SectionCard("ووکامرس") {
                    Text("برای هر سایت فعال در «اتصال ۳ سایت»، موجودی با PUT استاندارد wc/v3 روی همان product id فرستاده می‌شود. manage_stock روشن می‌شود. موجودی از ووکامرس خوانده نمی‌شود.")
                    Text("سیاست: ووکامرس = جمع قابل‌فروش دپو+مغازه منهای ذخیره اطمینان. اسنپ/تپسی = فقط مغازه.", style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                SectionCard("اسنپ‌شاپ") {
                    OutlinedTextField(snappUrl, { snappUrl = it }, label = { Text("آدرس HTTPS API موجودی") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(snappToken, { snappToken = it }, label = { Text("توکن Bearer (اختیاری)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(onClick = {
                        scope.launch {
                            runCatching {
                                store.saveMarketplace(MarketplaceEndpoint(ChannelIds.SNAPP, "اسنپ‌شاپ", snappUrl, snappToken))
                            }.onSuccess { msg = "تنظیم اسنپ ذخیره شد." }.onFailure { err = it.message }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("ذخیره اسنپ") }
                }
            }
            item {
                SectionCard("تپسی‌شاپ") {
                    OutlinedTextField(tapsiUrl, { tapsiUrl = it }, label = { Text("آدرس HTTPS API موجودی") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(tapsiToken, { tapsiToken = it }, label = { Text("توکن Bearer (اختیاری)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(onClick = {
                        scope.launch {
                            runCatching {
                                store.saveMarketplace(MarketplaceEndpoint(ChannelIds.TAPSI, "تپسی‌شاپ", tapsiUrl, tapsiToken))
                            }.onSuccess { msg = "تنظیم تپسی ذخیره شد." }.onFailure { err = it.message }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("ذخیره تپسی") }
                }
            }
            item {
                Button(onClick = {
                    scope.launch {
                        busy = true; err = null
                        runCatching { store.pushChannelStock() }
                            .onSuccess { msg = it.message; load() }
                            .onFailure { err = it.message }
                        busy = false
                    }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("ارسال موجودی الان") }
                Text("اگر API اسنپ/تپسی نداری، عدد را کپی کن و در پنل فروشنده بگذار، بعد «ارسال دستی» را بزن.", style = MaterialTheme.typography.bodySmall)
            }
            item { Busy(busy); ErrorText(err); msg?.let { SuccessText(it) } }
            items(rows, key = { it.item.id }) { row ->
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(row.productName, fontWeight = FontWeight.Bold)
                        Text("${row.channelName} • ${row.item.calculatedQuantity.toQty()} عدد • ${row.item.status}")
                        row.item.lastError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                        if (row.mode != IntegrationMode.API && row.item.status != SyncQueueStatus.SUCCESS) {
                            OutlinedButton(onClick = {
                                scope.launch { store.markChannelStockManual(row.item.id); load() }
                            }) { Text("ارسال دستی انجام شد") }
                        }
                    }
                }
            }
        }
    }
}
