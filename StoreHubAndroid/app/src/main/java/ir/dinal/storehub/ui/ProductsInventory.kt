package ir.dinal.storehub.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import ir.dinal.storehub.data.*
import ir.dinal.storehub.util.LabelPrinter
import ir.dinal.storehub.util.PrinterPrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(activity: Activity, nav: NavHostController) {
    val ctx = LocalContext.current
    val store = remember { LocalStore.get(ctx) }
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<ProductEntity>>(emptyList()) }
    var q by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<ProductEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var labelProduct by remember { mutableStateOf<ProductEntity?>(null) }

    suspend fun load() { list = store.products(q) }
    LaunchedEffect(q) {
        delay(220)
        runCatching { load() }.onFailure { err = it.message }
    }

    DinalScreen(
        nav = nav,
        title = "کالاها",
        showBack = false,
        actions = {
            IconButton(onClick = { nav.navigate("smart_product") }) { Icon(Icons.Rounded.AutoAwesome, "ثبت هوشمند با عکس") }
            IconButton(onClick = { nav.navigate("sync") }) { Icon(Icons.Rounded.Sync, "سینک ووکامرس") }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; showEditor = true }) {
                Icon(Icons.Rounded.Add, "کالای جدید")
            }
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            item {
                Card(onClick = { nav.navigate("smart_product") }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f))) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("ثبت هوشمند کالا از عکس", fontWeight = FontWeight.Bold)
                            Text("پس‌زمینه سفید، WebP، توضیحات AI و انتشار روی ۳ WooCommerce", style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Rounded.ChevronLeft, null)
                    }
                }
            }
            item {
                OutlinedTextField(
                    q,
                    { q = it },
                    label = { Text("جستجو در نام، SKU یا بارکد") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = { if (q.isNotBlank()) IconButton({ q = "" }) { Icon(Icons.Rounded.Close, "پاک کردن") } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp)
                )
            }
            item { ErrorText(err) }
            if (list.isEmpty()) {
                item {
                    SectionCard("کالایی پیدا نشد") {
                        Text("می‌توانی کالای جدید ثبت کنی یا محصولات را از WooCommerce سینک کنی.")
                    }
                }
            }
            items(list, key = { it.id }) { p ->
                Card(shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ProductThumb(p, size = 74.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(p.name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(toman(p.price), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            Text(
                                p.sku ?: p.barcode ?: p.internalCode,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AssistChip(onClick = {}, label = { Text(if (p.source == ProductEntity.SOURCE_WOO) "WooCommerce" else "دستی") })
                                if (p.isEnabledForStore) AssistChip(onClick = {}, label = { Text("فعال") }, leadingIcon = { Icon(Icons.Rounded.CheckCircle, null, Modifier.size(16.dp)) })
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = { editing = p; showEditor = true }) { Icon(Icons.Rounded.Edit, "ویرایش") }
                            IconButton(onClick = { labelProduct = p }) { Icon(Icons.Rounded.Print, "چاپ لیبل") }
                            if (!p.isEnabledForStore) {
                                IconButton(onClick = { scope.launch { store.enableStore(p.id, 0.0); load() } }) {
                                    Icon(Icons.Rounded.AddBusiness, "فعال‌سازی در مغازه")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        ProductEditorSheet(
            initial = editing,
            onDismiss = { showEditor = false; editing = null },
            onSave = { p, opening ->
                scope.launch {
                    runCatching { store.saveProduct(p, opening) }
                        .onSuccess { showEditor = false; editing = null; load() }
                        .onFailure { err = it.message }
                }
            }
        )
    }

    labelProduct?.let { p ->
        val prefs = remember { PrinterPrefs(ctx) }
        val wooBaseUrl = remember { WooPrefs(ctx).baseUrl }
        var labelMode by remember(p.id) { mutableStateOf(LabelPrinter.LabelMode.QR_ONLY) }
        val qrPayload = remember(p.id, p.productUrl, wooBaseUrl) { LabelPrinter.qrPayload(p, wooBaseUrl) }
        val isWebsiteQr = qrPayload.startsWith("https://") || qrPayload.startsWith("http://")
        val preview = remember(p.id, p.updatedAt, labelMode, wooBaseUrl) {
            LabelPrinter.render(p, labelMode, wooBaseUrl)
        }

        AlertDialog(
            onDismissRequest = { labelProduct = null },
            icon = { Icon(Icons.Rounded.QrCode2, null) },
            title = { Text("انتخاب نوع لیبل ۵۰×۳۰") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(p.name, fontWeight = FontWeight.Bold)
                    if (isWebsiteQr) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text("QR مشتری → صفحه همین کالا در سایت", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("قیمت داخل QR ذخیره نمی‌شود؛ مشتری قیمت روز WooCommerce را روی سایت می‌بیند.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                "برای این کالا لینک صفحه سایت ثبت نشده. اگر کالا از WooCommerce است یک بار سینک بزن؛ برای کالای دستی می‌توانی لینک صفحه کالا را در ویرایش وارد کنی.",
                                Modifier.padding(10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }

                    Text("نوع لیبل", fontWeight = FontWeight.Bold)
                    LabelModeChoice(
                        selected = labelMode == LabelPrinter.LabelMode.QR_ONLY,
                        title = "فقط QR",
                        subtitle = "لیبل خلوت؛ فقط QR صفحه محصول",
                        onClick = { labelMode = LabelPrinter.LabelMode.QR_ONLY }
                    )
                    LabelModeChoice(
                        selected = labelMode == LabelPrinter.LabelMode.QR_PRICE,
                        title = "QR + قیمت",
                        subtitle = "QR در کنار قیمت چاپی فعلی",
                        onClick = { labelMode = LabelPrinter.LabelMode.QR_PRICE }
                    )
                    LabelModeChoice(
                        selected = labelMode == LabelPrinter.LabelMode.QR_NAME_CODE,
                        title = "QR + نام + کد",
                        subtitle = "بدون قیمت؛ مناسب وقتی قیمت زیاد تغییر می‌کند",
                        onClick = { labelMode = LabelPrinter.LabelMode.QR_NAME_CODE }
                    )
                    LabelModeChoice(
                        selected = labelMode == LabelPrinter.LabelMode.FULL,
                        title = "لیبل کامل",
                        subtitle = "QR + نام + کد + قیمت",
                        onClick = { labelMode = LabelPrinter.LabelMode.FULL }
                    )

                    Text("پیش‌نمایش", fontWeight = FontWeight.Bold)
                    Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = "پیش‌نمایش لیبل",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(5f / 3f)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                    )

                    Text(
                        if (isWebsiteQr) "مقصد QR: $qrPayload" else "محتوای QR: $qrPayload",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (prefs.macAddress.isBlank()) {
                        Text("چاپگر بلوتوث انتخاب نشده؛ چاپ سیستمی قابل استفاده است.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("چاپگر بلوتوث: ${prefs.printerName}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (prefs.macAddress.isNotBlank()) {
                        LabelPrinter.printBluetooth(ctx, p, labelMode, wooBaseUrl, prefs.macAddress) { ok, message ->
                            activity.runOnUiThread { err = if (ok) null else message }
                        }
                    } else {
                        LabelPrinter.printSystem(activity, p, labelMode, wooBaseUrl)
                    }
                    labelProduct = null
                }) { Text(if (prefs.macAddress.isNotBlank()) "چاپ بلوتوث" else "چاپ") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        LabelPrinter.printSystem(activity, p, labelMode, wooBaseUrl)
                        labelProduct = null
                    }) { Text("چاپ سیستمی") }
                    TextButton(onClick = { labelProduct = null; nav.navigate("printer") }) { Text("تنظیم چاپگر") }
                }
            }
        )
    }
}


@Composable
private fun LabelModeChoice(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
        border = if (selected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductEditorSheet(
    initial: ProductEntity?,
    onDismiss: () -> Unit,
    onSave: (ProductEntity, Double) -> Unit
) {
    val ctx = LocalContext.current
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var sku by remember(initial?.id) { mutableStateOf(initial?.sku ?: "") }
    var barcode by remember(initial?.id) { mutableStateOf(initial?.barcode ?: "") }
    var category by remember(initial?.id) { mutableStateOf(initial?.category ?: "") }
    var price by remember(initial?.id) { mutableStateOf(initial?.price?.let { moneyInputFrom(it) } ?: "") }
    var threshold by remember(initial?.id) { mutableStateOf(initial?.lowStockThreshold?.toString() ?: "1") }
    var enabled by remember(initial?.id) { mutableStateOf(initial?.isEnabledForStore ?: true) }
    var opening by remember(initial?.id) { mutableStateOf("0") }
    var image by remember(initial?.id) { mutableStateOf(initial?.imageUrl ?: "") }
    var productUrl by remember(initial?.id) { mutableStateOf(initial?.productUrl ?: "") }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            image = uri.toString()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(
            Modifier.fillMaxWidth().imePadding(),
            contentPadding = PaddingValues(18.dp, 4.dp, 18.dp, 36.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Text(if (initial == null) "کالای جدید" else "ویرایش کالا", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProductThumb((initial ?: ProductEntity(name = name)).copy(name = name.ifBlank { "تصویر کالا" }, imageUrl = image.ifBlank { null }), size = 92.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Button(onClick = { imagePicker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.AddPhotoAlternate, null); Spacer(Modifier.width(6.dp)); Text("انتخاب تصویر")
                        }
                        if (image.isNotBlank()) TextButton(onClick = { image = "" }) { Text("حذف تصویر") }
                    }
                }
            }
            item { OutlinedTextField(name, { name = it }, label = { Text("نام کالا") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            item { OutlinedTextField(sku, { sku = it }, label = { Text("SKU") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            item { OutlinedTextField(barcode, { barcode = it }, label = { Text("بارکد / QR") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            item { OutlinedTextField(category, { category = it }, label = { Text("دسته‌بندی") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            item {
                OutlinedTextField(
                    productUrl,
                    { productUrl = it },
                    label = { Text("لینک صفحه کالا در سایت (برای QR مشتری)") },
                    supportingText = { Text(if (initial?.source == ProductEntity.SOURCE_WOO) "در سینک WooCommerce به‌صورت خودکار به‌روزرسانی می‌شود" else "اختیاری؛ اگر این کالا روی سایت صفحه دارد، لینک آن را وارد کن") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            item { MoneyTextField(price, { price = it }, "قیمت فروش", modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(threshold, { threshold = it }, label = { Text("حد هشدار موجودی") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                    Spacer(Modifier.width(10.dp))
                    Column { Text("فعال در مغازه", fontWeight = FontWeight.SemiBold); Text("در صندوق و موجودی مغازه نمایش داده شود", style = MaterialTheme.typography.bodySmall) }
                }
            }
            if (initial == null && enabled) item { OutlinedTextField(opening, { opening = it }, label = { Text("موجودی اولیه مغازه") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            item {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            onSave(
                                (initial ?: ProductEntity(name = name)).copy(
                                    name = name,
                                    sku = sku.ifBlank { null },
                                    barcode = barcode.ifBlank { null },
                                    category = category.ifBlank { null },
                                    price = parseToman(price),
                                    imageUrl = image.ifBlank { null },
                                    productUrl = productUrl.trim().ifBlank { null },
                                    lowStockThreshold = threshold.toIntOrNull() ?: 1,
                                    isEnabledForStore = enabled,
                                    updatedAt = System.currentTimeMillis()
                                ),
                                opening.toDoubleOrNull() ?: 0.0
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = name.isNotBlank()
                ) { Icon(Icons.Rounded.Save, null); Spacer(Modifier.width(6.dp)); Text("ذخیره کالا") }
            }
        }
    }
}

@Composable
fun InventoryScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val store = remember { LocalStore.get(ctx) }
    val scope = rememberCoroutineScope()
    var warehouse by remember { mutableIntStateOf(LocalStore.WAREHOUSE_STORE) }
    var list by remember { mutableStateOf<List<InventoryRow>>(emptyList()) }
    var selected by remember { mutableStateOf<InventoryRow?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    suspend fun load() { list = store.inventory(warehouse) }
    LaunchedEffect(warehouse) { load() }

    DinalScreen(nav, "انبار و موجودی") { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 30.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { WarehousePicker(warehouse) { warehouse = it } }
            item { ErrorText(err) }
            items(list, key = { it.product.id }) { row ->
                val low = row.quantity <= row.product.lowStockThreshold
                Card(shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ProductThumb(row.product, size = 62.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(row.product.name, fontWeight = FontWeight.Bold)
                            Text("موجودی: ${row.quantity}", color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            Text("هشدار از ${row.product.lowStockThreshold} عدد", style = MaterialTheme.typography.bodySmall)
                        }
                        FilledTonalButton(onClick = { selected = row }) { Text("تعدیل") }
                    }
                }
            }
        }
    }

    selected?.let { row ->
        InventoryAdjustDialog(row, onDismiss = { selected = null }) { delta, note ->
            scope.launch {
                runCatching { store.adjust(row.product.id, warehouse, delta, note) }
                    .onSuccess { selected = null; load() }
                    .onFailure { err = it.message }
            }
        }
    }
}

@Composable
private fun InventoryAdjustDialog(row: InventoryRow, onDismiss: () -> Unit, onSave: (Double, String?) -> Unit) {
    var delta by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعدیل ${row.product.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("موجودی فعلی: ${row.quantity}")
                OutlinedTextField(delta, { delta = it }, label = { Text("تغییر؛ مثال ۵ یا -۲") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("علت / توضیح") })
            }
        },
        confirmButton = { Button({ delta.toDoubleOrNull()?.let { onSave(it, note.ifBlank { null }) } }) { Text("ثبت") } },
        dismissButton = { TextButton(onDismiss) { Text("انصراف") } }
    )
}

@Composable
fun HistoryScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    var rows by remember { mutableStateOf<List<MovementRow>>(emptyList()) }
    LaunchedEffect(Unit) { rows = LocalStore.get(ctx).movements() }
    DinalScreen(nav, "تاریخچه موجودی") { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 30.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(rows, key = { it.movement.id }) { r ->
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(r.productName, fontWeight = FontWeight.Bold)
                        Text("${LocalStore.warehouseName(r.movement.warehouseId)} • تغییر ${r.movement.quantityDelta} • مانده ${r.movement.balanceAfter}")
                        r.movement.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}
