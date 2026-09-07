package ir.dinal.storehub.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import ir.dinal.storehub.data.LocalStore
import ir.dinal.storehub.data.ShopOrderDetails
import ir.dinal.storehub.inventory.OrderStatus
import ir.dinal.storehub.publishing.ProductImageProcessor
import kotlinx.coroutines.launch

@Composable
fun OnlineOrdersScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val store = remember { LocalStore.get(ctx) }
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<ShopOrderDetails>>(emptyList()) }
    var selected by remember { mutableStateOf<ShopOrderDetails?>(null) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        list = store.shopOrders()
        selected = selected?.order?.id?.let { id -> list.firstOrNull { it.order.id == id } }
    }
    LaunchedEffect(Unit) { runCatching { load() }.onFailure { err = it.message } }

    DinalScreen(
        nav,
        "سفارش‌های آنلاین",
        actions = {
            IconButton(onClick = {
                scope.launch {
                    busy = true; err = null
                    runCatching { store.importOnlineOrders() }
                        .onSuccess { msg = it.message; load() }
                        .onFailure { err = it.message }
                    busy = false
                }
            }) { Icon(Icons.Rounded.CloudDownload, "گرفتن سفارش از ووکامرس") }
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 30.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                DinalHero("سفارش ووکامرس", "گرفتن سفارش، رزرو موجودی مغازه، چیدن، بسته‌بندی و ارسال") {
                    Icon(Icons.Rounded.LocalShipping, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(42.dp))
                }
            }
            item { Busy(busy); ErrorText(err); msg?.let { SuccessText(it) } }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        scope.launch { busy = true; runCatching { store.retryOrderStock(); load() }.onFailure { err = it.message }; busy = false }
                    }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("تلاش دوباره رزرو") }
                    OutlinedButton(onClick = { nav.navigate("channel_stock") }, modifier = Modifier.weight(1f)) { Text("ارسال موجودی") }
                }
            }
            if (list.isEmpty()) item { SectionCard("سفارشی نیست") { Text("با دکمه ابر، سفارش‌های pending / on-hold / processing سایت را می‌گیرد و موجودی مغازه را رزرو می‌کند.") } }
            items(list, key = { it.order.id }) { row ->
                Card(onClick = { selected = row }, shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(row.order.orderNo, fontWeight = FontWeight.Bold)
                            AssistChip(onClick = {}, label = { Text(OrderStatus.label(row.order.status)) })
                        }
                        Text("${row.channelName} • ${row.order.externalOrderId}", style = MaterialTheme.typography.bodySmall)
                        row.order.customerName?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        Text(toman(row.order.total), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    selected?.let { details ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(details.order.orderNo) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("${details.channelName} • وضعیت کانال: ${details.order.externalStatus}")
                    Text(OrderStatus.label(details.order.status), fontWeight = FontWeight.Bold)
                    details.order.customerName?.let { Text("مشتری: $it") }
                    details.order.shippingAddress?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    details.items.forEach { item ->
                        Text(
                            "${item.name} × ${item.quantity.toQty()}  • رزرو مغازه ${item.reservedStoreQty.toQty()} • دپو ${item.waitingDepotQty.toQty()} • کمبود ${item.shortageQty.toQty()}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    ErrorText(err)
                }
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    when (details.order.status) {
                        OrderStatus.WAITING_DEPOT, OrderStatus.SHORTAGE, OrderStatus.NEW -> {
                            Button(onClick = {
                                scope.launch { runCatching { store.retryOrderStock(); load() }.onFailure { err = it.message } }
                            }, modifier = Modifier.fillMaxWidth()) { Text("تلاش دوباره رزرو") }
                        }
                        OrderStatus.RESERVED -> Button(onClick = {
                            scope.launch { runCatching { store.startPicking(details.order.id); store.confirmPicked(details.order.id); load() }.onFailure { err = it.message } }
                        }, modifier = Modifier.fillMaxWidth()) { Text("چیدن کالا") }
                        OrderStatus.PICKING -> Button(onClick = {
                            scope.launch { runCatching { store.confirmPicked(details.order.id); load() }.onFailure { err = it.message } }
                        }, modifier = Modifier.fillMaxWidth()) { Text("تأیید چیدن") }
                        OrderStatus.PICKED -> Button(onClick = {
                            scope.launch { runCatching { store.startPacking(details.order.id); store.confirmPacked(details.order.id); load() }.onFailure { err = it.message } }
                        }, modifier = Modifier.fillMaxWidth()) { Text("بسته‌بندی") }
                        OrderStatus.PACKING -> Button(onClick = {
                            scope.launch { runCatching { store.confirmPacked(details.order.id); load() }.onFailure { err = it.message } }
                        }, modifier = Modifier.fillMaxWidth()) { Text("تأیید بسته‌بندی") }
                        OrderStatus.PACKED -> Button(onClick = {
                            scope.launch { runCatching { store.shipOrder(details.order.id); load(); selected = null }.onFailure { err = it.message } }
                        }, modifier = Modifier.fillMaxWidth()) { Text("ارسال و خروج از موجودی") }
                    }
                    if (OrderStatus.isOpen(details.order.status)) {
                        TextButton(onClick = {
                            scope.launch { runCatching { store.cancelOrder(details.order.id, "لغو از StoreHub"); load(); selected = null }.onFailure { err = it.message } }
                        }) { Text("لغو سفارش و آزادسازی رزرو") }
                    }
                    TextButton(onClick = { selected = null }) { Text("بستن") }
                }
            },
            dismissButton = {}
        )
    }
}

@Composable
fun FulfillmentQueueScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val store = remember { LocalStore.get(ctx) }
    var list by remember { mutableStateOf<List<ShopOrderDetails>>(emptyList()) }
    LaunchedEffect(Unit) { list = store.shopOrders().filter { it.order.status in setOf(OrderStatus.RESERVED, OrderStatus.PICKING, OrderStatus.PICKED, OrderStatus.PACKING, OrderStatus.PACKED) } }
    DinalScreen(nav, "چیدن و بسته‌بندی") { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 30.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { DinalHero("صف ارسال", "سفارش‌هایی که رزرو شده‌اند و باید چیده، بسته و ارسال شوند") }
            if (list.isEmpty()) item { SectionCard("صف خالی است") { Text("سفارش رزرو‌شده‌ای برای چیدن نیست. از سفارش‌های آنلاین بگیر.") } }
            items(list, key = { it.order.id }) { row ->
                Card(onClick = { nav.navigate("orders") }, shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(row.order.orderNo, fontWeight = FontWeight.Bold)
                        Text(OrderStatus.label(row.order.status), color = MaterialTheme.colorScheme.primary)
                        row.items.forEach { Text("${it.name}: ${it.quantity.toQty()}", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

@Composable
fun rememberCameraLaunch(onCapture: (Uri) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let(onCapture)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val f = ProductImageProcessor.newCameraFile(ctx)
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
            cameraUri = uri
            camera.launch(uri)
        }
    }
    return {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val f = ProductImageProcessor.newCameraFile(ctx)
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
            cameraUri = uri
            camera.launch(uri)
        } else permission.launch(Manifest.permission.CAMERA)
    }
}
