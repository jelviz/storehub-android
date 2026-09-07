package ir.dinal.storehub.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import ir.dinal.storehub.data.LocalStore
import ir.dinal.storehub.data.ProductEntity
import ir.dinal.storehub.data.PurchaseLineDraft
import ir.dinal.storehub.data.StocktakeDetails
import ir.dinal.storehub.inventory.StocktakeStatus
import ir.dinal.storehub.publishing.LocalPhotoMatcher
import kotlinx.coroutines.launch

@Composable
fun PhotoStocktakeScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val store = remember { LocalStore.get(ctx) }
    val scope = rememberCoroutineScope()
    var warehouse by remember { mutableIntStateOf(LocalStore.WAREHOUSE_STORE) }
    var sessions by remember { mutableStateOf<List<StocktakeDetails>>(emptyList()) }
    var products by remember { mutableStateOf<List<ProductEntity>>(emptyList()) }
    var activeId by remember { mutableLongStateOf(0) }
    var hits by remember { mutableStateOf<List<ProductEntity>>(emptyList()) }
    var counted by remember { mutableStateOf("") }
    var picked by remember { mutableLongStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        products = store.products()
        sessions = store.stocktakes()
        if (activeId == 0L) activeId = sessions.firstOrNull { it.session.status == StocktakeStatus.OPEN }?.session?.id ?: 0
    }
    LaunchedEffect(Unit) { load() }
    val active = sessions.firstOrNull { it.session.id == activeId }

    val takePhoto = rememberCameraLaunch { uri ->
        scope.launch {
            busy = true; err = null
            runCatching { LocalPhotoMatcher.match(ctx, uri, products) }
                .onSuccess {
                    hits = it
                    picked = it.firstOrNull()?.id ?: 0
                    if (it.isEmpty()) err = "کالا در کاتالوگ محلی پیدا نشد. بارکد را رو به دوربین بگیر یا از لیست انتخاب کن."
                }
                .onFailure { err = it.message }
            busy = false
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true; err = null
            runCatching { LocalPhotoMatcher.match(ctx, uri, products) }
                .onSuccess {
                    hits = it
                    picked = it.firstOrNull()?.id ?: 0
                    if (it.isEmpty()) err = "کالا در کاتالوگ محلی پیدا نشد."
                }
                .onFailure { err = it.message }
            busy = false
        }
    }

    DinalScreen(nav, "انبارگردانی با عکس") { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { DinalHero("شمارش با عکس", "عکس یا بارکد کالا را می‌گیرد، با کاتالوگ محلی تطبیق می‌دهد و اختلاف را به‌صورت تراکنش انبارگردانی ثبت می‌کند") }
            item {
                SectionCard("شروع شمارش") {
                    WarehousePicker(warehouse) { warehouse = it }
                    Button(onClick = {
                        scope.launch {
                            busy = true
                            runCatching { store.startStocktake(warehouse, null) }
                                .onSuccess { activeId = it; load(); msg = "شمارش جدید باز شد." }
                                .onFailure { err = it.message }
                            busy = false
                        }
                    }, modifier = Modifier.fillMaxWidth(), enabled = !busy) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("شمارش جدید") }
                }
            }
            if (active != null && active.session.status == StocktakeStatus.OPEN) item {
                SectionCard("ثبت شمارش ${active.session.sessionNo}") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = takePhoto, enabled = !busy, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.PhotoCamera, null); Spacer(Modifier.width(4.dp)); Text("عکس") }
                        OutlinedButton(onClick = { gallery.launch("image/*") }, enabled = !busy, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.PhotoLibrary, null); Spacer(Modifier.width(4.dp)); Text("گالری") }
                    }
                    if (hits.isNotEmpty()) {
                        hits.forEach { p ->
                            FilterChip(selected = picked == p.id, onClick = { picked = p.id }, label = { Text(p.name) })
                        }
                    } else {
                        ProductPicker(products, picked) { picked = it }
                    }
                    OutlinedTextField(counted, { counted = it }, label = { Text("تعداد شمارش‌شده") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(
                        onClick = {
                            val qty = counted.toDoubleOrNull() ?: return@Button
                            scope.launch {
                                busy = true
                                runCatching { store.addStocktakeCount(active.session.id, picked, qty, null) }
                                    .onSuccess { counted = ""; hits = emptyList(); load(); msg = "ثبت شد." }
                                    .onFailure { err = it.message }
                                busy = false
                            }
                        },
                        enabled = !busy && picked > 0 && (counted.toDoubleOrNull() ?: -1.0) >= 0,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("افزودن به شمارش") }
                    active.items.forEach {
                        Text("${it.name}: سیستم ${it.systemQty.toQty()} → شمارش ${it.countedQty.toQty()} (اختلاف ${(it.countedQty - it.systemQty).toQty()})", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = {
                        scope.launch {
                            busy = true
                            runCatching { store.confirmStocktake(active.session.id) }
                                .onSuccess { msg = "انبارگردانی تأیید شد و موجودی اصلاح شد."; load() }
                                .onFailure { err = it.message }
                            busy = false
                        }
                    }, enabled = !busy && active.items.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.CheckCircle, null); Spacer(Modifier.width(6.dp)); Text("تأیید و اعمال اختلاف")
                    }
                }
            }
            item { Busy(busy); ErrorText(err); msg?.let { SuccessText(it) } }
            items(sessions, key = { it.session.id }) { s ->
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(s.session.sessionNo, fontWeight = FontWeight.Bold)
                        Text("${LocalStore.warehouseName(s.session.warehouseId)} • ${if (s.session.status == StocktakeStatus.CONFIRMED) "تأیید شده" else "باز"}")
                        Text("${s.items.size} قلم", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun PhotoPurchaseScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val store = remember { LocalStore.get(ctx) }
    val scope = rememberCoroutineScope()
    var products by remember { mutableStateOf<List<ProductEntity>>(emptyList()) }
    val draft = remember { mutableStateListOf<PurchaseLineDraft>() }
    var hits by remember { mutableStateOf<List<ProductEntity>>(emptyList()) }
    var picked by remember { mutableLongStateOf(0) }
    var qty by remember { mutableStateOf("1") }
    var cost by remember { mutableStateOf("") }
    var supplier by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var warehouse by remember { mutableIntStateOf(LocalStore.WAREHOUSE_DEPOT) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { products = store.products() }

    val takePhoto = rememberCameraLaunch { uri ->
        scope.launch {
            busy = true; err = null
            runCatching { LocalPhotoMatcher.match(ctx, uri, products) }
                .onSuccess {
                    hits = it
                    picked = it.firstOrNull()?.id ?: 0
                    if (it.isEmpty()) err = "این کالا در کاتالوگ StoreHub نیست. اول کالا را ثبت کن؛ این صفحه برای خرید است نه انتشار سایت."
                }
                .onFailure { err = it.message }
            busy = false
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            runCatching { LocalPhotoMatcher.match(ctx, uri, products) }
                .onSuccess { hits = it; picked = it.firstOrNull()?.id ?: 0 }
                .onFailure { err = it.message }
            busy = false
        }
    }

    DinalScreen(nav, "خرید با عکس") { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { DinalHero("خرید بازار با عکس", "جدا از ثبت هوشمند سایت. عکس کالا را با کاتالوگ محلی مچ می‌کند و فاکتور خرید می‌سازد") }
            item {
                SectionCard("شناسایی کالا") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = takePhoto, enabled = !busy, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.PhotoCamera, null); Spacer(Modifier.width(4.dp)); Text("عکس") }
                        OutlinedButton(onClick = { gallery.launch("image/*") }, enabled = !busy, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.PhotoLibrary, null); Spacer(Modifier.width(4.dp)); Text("گالری") }
                    }
                    if (hits.isNotEmpty()) hits.forEach { p -> FilterChip(selected = picked == p.id, onClick = { picked = p.id }, label = { Text(p.name) }) }
                    else ProductPicker(products, picked) { picked = it }
                    OutlinedTextField(qty, { qty = it }, label = { Text("تعداد") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    MoneyTextField(cost, { cost = it }, "قیمت خرید واحد", modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        val p = products.firstOrNull { it.id == picked } ?: return@Button
                    val q = qty.toDoubleOrNull() ?: return@Button
                    val c = parseToman(cost)
                    if (q <= 0) return@Button
                    draft.add(PurchaseLineDraft(p.id, p.name, q, c))
                        qty = "1"; cost = ""; hits = emptyList()
                    }, enabled = picked > 0, modifier = Modifier.fillMaxWidth()) { Text("افزودن به فاکتور") }
                }
            }
            item {
                SectionCard("فاکتور") {
                    OutlinedTextField(supplier, { supplier = it }, label = { Text("فروشنده") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(mobile, { mobile = it }, label = { Text("موبایل") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    WarehousePicker(warehouse) { warehouse = it }
                    if (draft.isEmpty()) Text("هنوز قلمی اضافه نشده.")
                    draft.forEach { Text("${it.name} × ${it.quantity} = ${toman(it.quantity * it.unitCost)}") }
                    ErrorText(err); msg?.let { SuccessText(it) }; Busy(busy)
                    Button(onClick = {
                        scope.launch {
                            busy = true; err = null
                            runCatching {
                                val id = store.createPurchase(supplier, mobile, todayPersian(), warehouse, 2, "خرید با عکس", draft.toList())
                                store.receivePurchase(id)
                            }.onSuccess {
                                draft.clear(); msg = "خرید ثبت و موجودی انبار مقصد به‌روز شد."
                            }.onFailure { err = it.message }
                            busy = false
                        }
                    }, enabled = draft.isNotEmpty() && !busy, modifier = Modifier.fillMaxWidth()) { Text("ثبت خرید و دریافت کالا") }
                    Text("اگر کالا را هنوز نمی‌خواهی وارد انبار کنی، از صفحه «خریدهای بازار» بدون دریافت استفاده کن.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
