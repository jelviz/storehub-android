package ir.dinal.storehub.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import ir.dinal.storehub.data.AlertCenter
import ir.dinal.storehub.data.LocalStore
import ir.dinal.storehub.inventory.AlertLevel
import ir.dinal.storehub.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun AlertsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val store = remember { LocalStore.get(ctx) }
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<AlertCenter?>(null) }
    var names by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun load(refresh: Boolean = false) {
        data = store.alerts(refresh)
        names = store.products().associate { it.id to it.name }
    }
    LaunchedEffect(Unit) { runCatching { load(true) }.onFailure { err = it.message } }

    DinalScreen(
        nav,
        "هشدار موجودی",
        actions = {
            IconButton(onClick = { scope.launch { busy = true; runCatching { load(true) }.onFailure { err = it.message }; busy = false } }) {
                Icon(Icons.Rounded.Refresh, "بررسی دوباره")
            }
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 30.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { DinalHero("مرکز هشدار", "کمبود فروشگاه، کمبود دپو، پیشنهاد انتقال و خرید") { Icon(Icons.Rounded.NotificationsActive, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(42.dp)) } }
            item { Busy(busy); ErrorText(err) }
            val center = data
            if (center == null) {
                item { Text("در حال بارگذاری…") }
            } else {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricCard("انتقال", center.transfers.size.toString(), DinalGold, Modifier.weight(1f))
                        MetricCard("خرید", center.purchases.size.toString(), DinalRose, Modifier.weight(1f))
                    }
                }
                if (center.transfers.isNotEmpty()) {
                    item { Text("پیشنهاد انتقال دپو → فروشگاه", fontWeight = FontWeight.Bold) }
                    items(center.transfers, key = { "t-${it.id}" }) { s ->
                        Card(shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(names[s.productId] ?: "کالا ${s.productId}", fontWeight = FontWeight.Bold)
                                Text("${s.quantity.toQty()} عدد از دپو", style = MaterialTheme.typography.bodySmall)
                                Text(s.reason, style = MaterialTheme.typography.bodySmall)
                                Button(
                                    onClick = { scope.launch { runCatching { store.createTransfer(s.productId, s.quantity, s.reason) }.onSuccess { nav.navigate("transfers") }.onFailure { err = it.message } } },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("ساخت انتقال") }
                            }
                        }
                    }
                }
                if (center.purchases.isNotEmpty()) {
                    item { Text("پیشنهاد خرید", fontWeight = FontWeight.Bold) }
                    items(center.purchases, key = { "p-${it.id}" }) { s ->
                        Card(shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(names[s.productId] ?: "کالا", fontWeight = FontWeight.Bold)
                                Text("خرید ${s.quantity.toQty()} عدد", fontWeight = FontWeight.SemiBold)
                                Text(s.reason, style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { nav.navigate("purchases") }) { Text("رفتن به خرید") }
                            }
                        }
                    }
                }
                item { Text("اعلان‌ها", fontWeight = FontWeight.Bold) }
                if (center.notifications.isEmpty()) {
                    item { SectionCard("اعلانی نیست") { Text("بعد از فروش، خرید یا تعدیل، سطح موجودی دوباره محاسبه می‌شود.") } }
                }
                items(center.notifications, key = { it.id }) { n ->
                    val unread = n.readAt == null
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = if (unread) MaterialTheme.colorScheme.errorContainer.copy(alpha = .35f) else MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(AlertLevel.label(n.severity), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text(n.message, style = MaterialTheme.typography.bodySmall)
                            }
                            if (unread) {
                                TextButton(onClick = { scope.launch { store.markAlertRead(n.id); load() } }) { Text("خواندم") }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun Double.toQty(): String = if (kotlin.math.abs(this - toLong()) < 0.000001) toLong().toString() else toString()
