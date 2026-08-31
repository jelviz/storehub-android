package ir.dinal.storehub.ui

import android.app.Activity
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
import ir.dinal.storehub.data.*
import kotlinx.coroutines.launch

@Composable
fun PosScreen(activity: Activity, nav: NavHostController) {
    val ctx = LocalContext.current
    val store = remember { LocalStore.get(ctx) }
    val scope = rememberCoroutineScope()
    val cart = remember { mutableStateListOf<CartLine>() }
    var code by remember { mutableStateOf("") }
    var customer by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var payment by remember { mutableIntStateOf(2) }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val posEntry = remember(nav) { nav.getBackStackEntry("pos") }
    val scanResult by posEntry.savedStateHandle.getStateFlow("scan_result", "").collectAsState()

    fun addProduct(p: ProductEntity) {
        val i = cart.indexOfFirst { it.product.id == p.id }
        if (i >= 0) cart[i] = cart[i].copy(quantity = cart[i].quantity + 1)
        else cart.add(CartLine(p, 1.0))
    }

    fun lookup(c: String) {
        if (c.isBlank()) return
        scope.launch {
            val found = store.findByCode(c.trim())
            if (found == null) msg = "کالایی با کد «${c.trim()}» پیدا نشد."
            else {
                addProduct(found)
                code = ""
                msg = null
            }
        }
    }

    LaunchedEffect(scanResult) {
        if (scanResult.isNotBlank()) {
            lookup(scanResult)
            posEntry.savedStateHandle["scan_result"] = ""
        }
    }

    DinalScreen(nav, "صندوق فروش", showBack = false) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DinalHero("فروش سریع", "اسکن کن، تعداد را تنظیم کن و فروش را ثبت کن") {
                Icon(Icons.Rounded.PointOfSale, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(42.dp))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("بارکد / SKU") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    trailingIcon = { Icon(Icons.Rounded.Search, null) }
                )
                FilledTonalIconButton(onClick = { lookup(code) }) { Icon(Icons.Rounded.AddShoppingCart, "افزودن") }
                Button(onClick = { nav.navigate("scanner") }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)) {
                    Icon(Icons.Rounded.QrCodeScanner, null); Spacer(Modifier.width(5.dp)); Text("اسکن")
                }
            }

            ErrorText(msg)
            Busy(busy)

            if (cart.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)
                ) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Rounded.ShoppingBasket, null, modifier = Modifier.size(60.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(10.dp))
                        Text("سبد فروش خالی است", fontWeight = FontWeight.SemiBold)
                        Text("بارکد را اسکن کن یا کد کالا را وارد کن", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(cart, key = { it.product.id }) { line ->
                        Card(shape = RoundedCornerShape(18.dp)) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                ProductThumb(line.product, size = 58.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(line.product.name, fontWeight = FontWeight.Bold, maxLines = 2)
                                    Text("${toman(line.product.price)} × ${line.quantity}", style = MaterialTheme.typography.bodySmall)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = {
                                        val i = cart.indexOfFirst { it.product.id == line.product.id }
                                        if (i >= 0 && cart[i].quantity > 1) cart[i] = cart[i].copy(quantity = cart[i].quantity - 1)
                                        else if (i >= 0) cart.removeAt(i)
                                    }) { Icon(Icons.Rounded.RemoveCircleOutline, "کم کردن") }
                                    Text(line.quantity.toInt().toString(), fontWeight = FontWeight.Bold)
                                    IconButton(onClick = {
                                        val i = cart.indexOfFirst { it.product.id == line.product.id }
                                        if (i >= 0) cart[i] = cart[i].copy(quantity = cart[i].quantity + 1)
                                    }) { Icon(Icons.Rounded.AddCircleOutline, "زیاد کردن") }
                                }
                            }
                        }
                    }
                }
            }

            Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 2.dp) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("جمع کل", fontWeight = FontWeight.SemiBold)
                        Text(toman(cart.sumOf { it.product.price * it.quantity }), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    PaymentPicker(payment) { payment = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(customer, { customer = it }, label = { Text("نام مشتری") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(mobile, { mobile = it }, label = { Text("موبایل") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                busy = true; msg = null
                                runCatching { store.checkout(cart.toList(), payment, customer, mobile) }
                                    .onSuccess { id -> cart.clear(); customer = ""; mobile = ""; msg = "فروش با شماره داخلی $id ثبت شد." }
                                    .onFailure { msg = it.message }
                                busy = false
                            }
                        },
                        enabled = cart.isNotEmpty() && !busy,
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Icon(Icons.Rounded.Done, null); Spacer(Modifier.width(6.dp)); Text("ثبت فروش و کسر موجودی")
                    }
                }
            }
        }
    }
}

@Composable
fun SalesScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val store = remember { LocalStore.get(ctx) }
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<SaleEntity>>(emptyList()) }
    var details by remember { mutableStateOf<SaleDetails?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val returnQty = remember { mutableStateMapOf<Long, String>() }

    suspend fun load() { list = store.sales() }
    LaunchedEffect(Unit) { load() }

    DinalScreen(nav, "فروش‌ها و مرجوعی") { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 30.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            item { ErrorText(error) }
            if (list.isEmpty()) item { SectionCard("هنوز فروشی ثبت نشده") { Text("فروش‌های ثبت‌شده از صندوق اینجا نمایش داده می‌شوند.") } }
            items(list, key = { it.id }) { s ->
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(s.invoiceNo, fontWeight = FontWeight.Bold)
                            Text(toman(s.total - s.returnedTotal), fontWeight = FontWeight.Bold)
                        }
                        Text("مرجوعی: ${toman(s.returnedTotal)}", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { scope.launch { details = store.saleDetails(s.id); returnQty.clear() } }) {
                            Icon(Icons.Rounded.Undo, null); Spacer(Modifier.width(5.dp)); Text("جزئیات و مرجوعی")
                        }
                    }
                }
            }
        }
    }

    details?.let { d ->
        AlertDialog(
            onDismissRequest = { details = null },
            title = { Text("فاکتور ${d.sale.invoiceNo}") },
            text = {
                LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(d.items, key = { it.id }) { i ->
                        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)) {
                            Column(Modifier.padding(10.dp)) {
                                Text(i.name, fontWeight = FontWeight.Bold)
                                Text("خرید: ${i.quantity} • مرجوع‌شده: ${i.returnedQuantity} • مانده: ${i.quantity - i.returnedQuantity}", style = MaterialTheme.typography.bodySmall)
                                OutlinedTextField(returnQty[i.id] ?: "", { returnQty[i.id] = it }, label = { Text("تعداد مرجوعی") }, singleLine = true)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val q = returnQty.mapValues { it.value.toDoubleOrNull() ?: 0.0 }
                        runCatching { store.returnSale(d.sale.id, q, "مرجوعی از اپ") }
                            .onSuccess { details = null; load() }
                            .onFailure { error = it.message }
                    }
                }) { Text("ثبت مرجوعی") }
            },
            dismissButton = { TextButton(onClick = { details = null }) { Text("بستن") } }
        )
    }
}
