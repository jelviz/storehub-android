package ir.dinal.storehub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ImageNotSupported
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import ir.dinal.storehub.data.*
import ir.dinal.storehub.ui.theme.DinalGold
import ir.dinal.storehub.ui.theme.DinalPlum
import ir.dinal.storehub.ui.theme.DinalPurple
import ir.dinal.storehub.ui.theme.DinalRose
import ir.dinal.storehub.util.MoneyFormat

/** Canonical three-digit money formatting used by every Compose screen. */
fun money(v: Double): String = MoneyFormat.number(v)
fun toman(v: Double): String = MoneyFormat.toman(v)

private fun englishDigits(value: String): String = buildString(value.length) {
    value.forEach { ch ->
        append(
            when (ch) {
                '۰' -> '0'; '۱' -> '1'; '۲' -> '2'; '۳' -> '3'; '۴' -> '4'
                '۵' -> '5'; '۶' -> '6'; '۷' -> '7'; '۸' -> '8'; '۹' -> '9'
                '٠' -> '0'; '١' -> '1'; '٢' -> '2'; '٣' -> '3'; '٤' -> '4'
                '٥' -> '5'; '٦' -> '6'; '٧' -> '7'; '٨' -> '8'; '٩' -> '9'
                else -> ch
            }
        )
    }
}

/** Returns only normalized Latin digits. Money field state stays separator-free. */
private fun normalizeMoneyDigits(value: String): String {
    val digits = englishDigits(value).filter { it.isDigit() }
    if (digits.isBlank()) return ""
    return digits.trimStart('0').ifBlank { "0" }
}

/** Convenience formatter for non-editable strings. */
fun formatMoneyInput(value: String): String = MoneyFormat.groupDigits(normalizeMoneyDigits(value))

fun parseToman(value: String): Double = normalizeMoneyDigits(value).toDoubleOrNull() ?: 0.0
fun moneyInputFrom(value: Double): String = if (value <= 0.0) "" else value.toLong().toString()

/**
 * Displays thousands separators without inserting them into the backing text.
 * This is important: editing in the middle of an amount keeps the cursor exactly
 * next to the digit the user touched instead of jumping to the end after regrouping.
 */
private object ThousandsSeparatorTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val formatted = MoneyFormat.groupDigits(raw)
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                MoneyFormat.groupedOffset(raw.length, offset)

            override fun transformedToOriginal(offset: Int): Int {
                val safe = offset.coerceIn(0, formatted.length)
                var orig = 0
                var trans = 0
                while (trans < safe && orig < raw.length) {
                    if (orig > 0 && (raw.length - orig) % 3 == 0) {
                        trans++
                        if (trans >= safe) return orig
                    }
                    orig++
                    trans++
                }
                return orig.coerceIn(0, raw.length)
            }
        }
        return TransformedText(AnnotatedString(formatted), mapping)
    }
}

@Composable
fun MoneyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supportingText: String? = null
) {
    val rawValue = normalizeMoneyDigits(value)
    OutlinedTextField(
        value = rawValue,
        onValueChange = { onValueChange(normalizeMoneyDigits(it)) },
        label = { Text(label) },
        suffix = { Text("تومان", fontWeight = FontWeight.SemiBold) },
        supportingText = { if (!supportingText.isNullOrBlank()) Text(supportingText) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        visualTransformation = ThousandsSeparatorTransformation,
        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr, textAlign = TextAlign.Start),
        modifier = modifier,
        singleLine = true,
        enabled = enabled
    )
}

fun todayPersian() = Jalali.format(Jalali.today())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DinalScreen(
    nav: NavHostController,
    title: String,
    showBack: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = { nav.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "بازگشت")
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .96f)
                )
            )
        },
        floatingActionButton = floatingActionButton,
        content = content
    )
}

@Composable
fun DinalHero(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = DinalPlum)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(DinalPlum, DinalPurple, DinalRose.copy(alpha = .85f)))
                )
                .padding(20.dp)
        ) {
            Column(Modifier.align(Alignment.CenterStart)) {
                Text(title, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text(subtitle, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .86f), style = MaterialTheme.typography.bodyMedium)
            }
            trailing?.let { Box(Modifier.align(Alignment.CenterEnd)) { it() } }
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .10f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            content()
        }
    }
}

@Composable
fun MetricCard(label: String, value: String, accent: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = .18f))
    ) {
        Column(Modifier.padding(15.dp)) {
            Box(Modifier.width(32.dp).height(4.dp).clip(RoundedCornerShape(99.dp)).background(accent))
            Spacer(Modifier.height(10.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ProductThumb(product: ProductEntity, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 64.dp) {
    val model = product.imageUrl?.takeIf { it.isNotBlank() }
    Box(
        modifier = modifier.size(size).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (model != null) {
            SubcomposeAsyncImage(
                model = model,
                contentDescription = product.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                },
                error = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.ImageNotSupported, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                    }
                },
                success = { SubcomposeAsyncImageContent() }
            )
        } else {
            Icon(Icons.Rounded.ImageNotSupported, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable fun ErrorText(text: String?) {
    if (!text.isNullOrBlank()) {
        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
            Text(text, Modifier.padding(10.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable fun SuccessText(text: String?) {
    if (!text.isNullOrBlank()) {
        Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(12.dp)) {
            Text(text, Modifier.padding(10.dp), color = MaterialTheme.colorScheme.onTertiaryContainer, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable fun Busy(b: Boolean) { if (b) LinearProgressIndicator(Modifier.fillMaxWidth()) }

@Composable
fun ProductPicker(products: List<ProductEntity>, selected: Long, onSelect: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val product = products.firstOrNull { it.id == selected }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (product != null) ProductThumb(product, size = 42.dp)
                Spacer(Modifier.width(8.dp))
                Text(product?.name ?: "انتخاب کالا", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            products.take(300).forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = { onSelect(p.id); open = false }
                )
            }
        }
    }
}

@Composable
fun WarehousePicker(selected: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ open = true }, Modifier.fillMaxWidth()) { Text(LocalStore.warehouseName(selected)) }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem(text = { Text("مغازه") }, onClick = { onSelect(LocalStore.WAREHOUSE_STORE); open = false })
            DropdownMenuItem(text = { Text("دپو") }, onClick = { onSelect(LocalStore.WAREHOUSE_DEPOT); open = false })
        }
    }
}

@Composable
fun PaymentPicker(selected: Int, onSelect: (Int) -> Unit) {
    val labels = linkedMapOf(1 to "نقدی", 2 to "کارتخوان", 3 to "اقساطی", 4 to "ترکیبی", 5 to "سایر")
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ open = true }, Modifier.fillMaxWidth()) { Text(labels[selected] ?: "کارتخوان") }
        DropdownMenu(open, { open = false }) {
            labels.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(id); open = false }) }
        }
    }
}

@Composable
fun BrandDivider() {
    HorizontalDivider(color = DinalGold.copy(alpha = .45f))
}
