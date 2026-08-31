package ir.dinal.storehub.ui

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
import androidx.navigation.NavHostController
import ir.dinal.storehub.data.*
import kotlinx.coroutines.launch

@Composable
fun TransfersScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val store = remember { LocalStore.get(ctx) }
    val scope = rememberCoroutineScope()
    var products by remember { mutableStateOf<List<ProductEntity>>(emptyList()) }
    var list by remember { mutableStateOf<List<TransferDetails>>(emptyList()) }
    var productId by remember { mutableLongStateOf(0) }
    var qty by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var showForm by remember { mutableStateOf(false) }

    suspend fun load() {
        products = store.products()
        list = store.transfers()
        if (productId == 0L) productId = products.firstOrNull()?.id ?: 0
    }
    LaunchedEffect(Unit) { load() }

    DinalScreen(nav, "انتقال دپو → مغازه", floatingActionButton = {
        FloatingActionButton(onClick = { showForm = !showForm }) { Icon(if (showForm) Icons.Rounded.Close else Icons.Rounded.Add, null) }
    }) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { DinalHero("انتقال دو مرحله‌ای", "ابتدا خروج از دپو، سپس تأیید تحویل در مغازه") { Icon(Icons.Rounded.SwapHoriz, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(42.dp)) } }
            if (showForm) item {
                SectionCard("انتقال جدید") {
                    ProductPicker(products, productId) { productId = it }
                    OutlinedTextField(qty, { qty = it }, label = { Text("تعداد") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(note, { note = it }, label = { Text("توضیح") }, modifier = Modifier.fillMaxWidth())
                    ErrorText(err)
                    Button(
                        onClick = { scope.launch { runCatching { store.createTransfer(productId, qty.toDoubleOrNull() ?: 0.0, note) }.onSuccess { qty = ""; note = ""; showForm = false; load() }.onFailure { err = it.message } } },
                        modifier = Modifier.fillMaxWidth(), enabled = productId > 0 && (qty.toDoubleOrNull() ?: 0.0) > 0
                    ) { Text("ثبت درخواست انتقال") }
                }
            }
            items(list, key = { it.transfer.id }) { d ->
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(d.transfer.transferNo, fontWeight = FontWeight.Bold)
                            AssistChip(onClick = {}, label = { Text(when (d.transfer.status) { 1 -> "ثبت‌شده"; 2 -> "در مسیر"; else -> "تحویل‌شده" }) })
                        }
                        d.items.forEach { Text("${it.name}: ${it.quantity}") }
                        when (d.transfer.status) {
                            1 -> Button({ scope.launch { runCatching { store.dispatchTransfer(d.transfer.id) }.onSuccess { load() }.onFailure { err = it.message } } }, Modifier.fillMaxWidth()) { Text("ثبت خروج از دپو") }
                            2 -> Button({ scope.launch { runCatching { store.receiveTransfer(d.transfer.id) }.onSuccess { load() }.onFailure { err = it.message } } }, Modifier.fillMaxWidth()) { Text("تأیید تحویل مغازه") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PurchasesScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val store = remember { LocalStore.get(ctx) }
    val scope = rememberCoroutineScope()
    var products by remember { mutableStateOf<List<ProductEntity>>(emptyList()) }
    var purchases by remember { mutableStateOf<List<PurchaseDetails>>(emptyList()) }
    var selected by remember { mutableLongStateOf(0) }
    var qty by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    val draft = remember { mutableStateListOf<PurchaseLineDraft>() }
    var supplier by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(todayPersian()) }
    var warehouse by remember { mutableIntStateOf(LocalStore.WAREHOUSE_DEPOT) }
    var payment by remember { mutableIntStateOf(2) }
    var note by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var showForm by remember { mutableStateOf(false) }

    suspend fun load() {
        products = store.products()
        purchases = store.purchases()
        if (selected == 0L) selected = products.firstOrNull()?.id ?: 0
    }
    LaunchedEffect(Unit) { load() }

    DinalScreen(nav, "خریدهای بازار", floatingActionButton = {
        FloatingActionButton(onClick = { showForm = !showForm }) { Icon(if (showForm) Icons.Rounded.Close else Icons.Rounded.Add, null) }
    }) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (showForm) item {
                SectionCard("ثبت خرید") {
                    OutlinedTextField(supplier, { supplier = it }, label = { Text("فروشنده") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(mobile, { mobile = it }, label = { Text("موبایل فروشنده") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    PersianDateField("تاریخ خرید", date) { date = it }
                    WarehousePicker(warehouse) { warehouse = it }
                    PaymentPicker(payment) { payment = it }
                    BrandDivider()
                    ProductPicker(products, selected) { selected = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(qty, { qty = it }, label = { Text("تعداد") }, modifier = Modifier.weight(1f), singleLine = true)
                        MoneyTextField(cost, { cost = it }, "قیمت خرید واحد", modifier = Modifier.weight(1f))
                    }
                    OutlinedButton(
                        onClick = {
                            val p = products.firstOrNull { it.id == selected }
                            val q = qty.toDoubleOrNull() ?: 0.0
                            val c = parseToman(cost)
                            if (p != null && q > 0) { draft.add(PurchaseLineDraft(p.id, p.name, q, c)); qty = ""; cost = "" }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Icon(Icons.Rounded.PlaylistAdd, null); Spacer(Modifier.width(6.dp)); Text("افزودن قلم") }
                    draft.forEachIndexed { i, l ->
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)) {
                            Row(Modifier.fillMaxWidth().padding(9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${l.name} × ${l.quantity} = ${toman(l.quantity * l.unitCost)}", Modifier.weight(1f))
                                IconButton({ draft.removeAt(i) }) { Icon(Icons.Rounded.DeleteOutline, "حذف") }
                            }
                        }
                    }
                    OutlinedTextField(note, { note = it }, label = { Text("توضیح") }, modifier = Modifier.fillMaxWidth())
                    Text("جمع خرید: ${toman(draft.sumOf { it.quantity * it.unitCost })}", fontWeight = FontWeight.Bold)
                    ErrorText(err)
                    Button(
                        onClick = { scope.launch { runCatching { store.createPurchase(supplier, mobile, date, warehouse, payment, note, draft.toList()) }.onSuccess { draft.clear(); supplier = ""; mobile = ""; note = ""; showForm = false; load() }.onFailure { err = it.message } } },
                        enabled = draft.isNotEmpty(), modifier = Modifier.fillMaxWidth()
                    ) { Text("ثبت خرید") }
                }
            }
            items(purchases, key = { it.purchase.id }) { d ->
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(d.purchase.purchaseNo, fontWeight = FontWeight.Bold)
                            Text(toman(d.purchase.total), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Text("${d.purchase.purchaseDatePersian} • ${LocalStore.warehouseName(d.purchase.warehouseId)}")
                        Text(d.purchase.supplierName ?: "بدون نام فروشنده", style = MaterialTheme.typography.bodySmall)
                        d.items.take(4).forEach { Text("${it.name}: ${it.quantity} × ${toman(it.unitCost)}", style = MaterialTheme.typography.bodySmall) }
                        if (d.purchase.status == 1) {
                            Button({ scope.launch { runCatching { store.receivePurchase(d.purchase.id) }.onSuccess { load() }.onFailure { err = it.message } } }, Modifier.fillMaxWidth()) { Text("دریافت کالا و افزایش موجودی") }
                        } else AssistChip(onClick = {}, label = { Text("دریافت‌شده") }, leadingIcon = { Icon(Icons.Rounded.CheckCircle, null, Modifier.size(16.dp)) })
                    }
                }
            }
        }
    }
}
