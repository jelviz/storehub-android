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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import ir.dinal.storehub.data.*
import ir.dinal.storehub.publishing.OpenAiProductClient
import ir.dinal.storehub.publishing.ProductImageProcessor
import ir.dinal.storehub.publishing.WooPublisher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PublishingSettingsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val prefs = remember { PublishingPrefs(ctx) }
    val scope = rememberCoroutineScope()
    var sites by remember { mutableStateOf((1..3).map { prefs.site(it) }) }
    var aiKey by remember { mutableStateOf(prefs.openAiKey()) }
    var aiModel by remember { mutableStateOf(prefs.openAiModel) }
    var provider by remember { mutableStateOf(if (prefs.openAiBaseUrl.contains("openrouter.ai")) "openrouter" else prefs.aiProvider) }
    var apiBase by remember { mutableStateOf(prefs.openAiBaseUrl) }
    var message by remember { mutableStateOf<String?>(null) }
    var busySite by remember { mutableStateOf<Int?>(null) }

    fun updateSite(index: Int, transform: (WooPublishSite) -> WooPublishSite) {
        sites = sites.map { if (it.index == index) transform(it) else it }
    }

    DinalScreen(nav, "انتشار هوشمند و ۳ سایت") { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                DinalHero("اتصال انتشار محصول", "سه WooCommerce + تولید متن هوشمند") {
                    Icon(Icons.Rounded.CloudUpload, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(42.dp))
                }
            }
            item {
                SectionCard("هوش مصنوعی DINAL", subtitle = "برای پیش‌نویس محصول و دستیار DINAL استفاده می‌شود") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = provider == "openai", onClick = { provider = "openai"; apiBase = "https://api.openai.com/v1" }, label = { Text("OpenAI") })
                        FilterChip(selected = provider == "gemini", onClick = { provider = "gemini" }, label = { Text("Gemini") })
                        FilterChip(selected = provider == "openrouter", onClick = { provider = "openrouter"; apiBase = "https://openrouter.ai/api/v1" }, label = { Text("OpenRouter") })
                    }
                    OutlinedTextField(
                        value = aiKey,
                        onValueChange = { aiKey = it },
                        label = { Text("API Key") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    OutlinedTextField(aiModel, { aiModel = it }, label = { Text("مدل") }, placeholder = { Text(if (provider == "gemini") "gemini-2.0-flash" else "gpt-4o-mini") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    if (provider != "gemini") {
                        OutlinedTextField(apiBase, { apiBase = it }, label = { Text("آدرس API") }, placeholder = { Text("https://api.openai.com/v1") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    }
                    Text("کلید را از Google AI Studio یا OpenAI/OpenRouter بگیر. gpt-5.6-sol مدل Cursor است و به OpenAI نمی‌خورد. کلید در Keystore همین گوشی ذخیره می‌شود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = {
                        prefs.aiProvider = if (provider == "openrouter") "openai" else provider
                        prefs.openAiBaseUrl = apiBase
                        if (aiKey.isNotBlank()) prefs.setOpenAiKey(aiKey)
                        prefs.openAiModel = aiModel.ifBlank { if (provider == "gemini") "gemini-2.0-flash" else "gpt-4o-mini" }
                        message = "تنظیمات AI ذخیره شد."
                    }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Save, null); Spacer(Modifier.width(6.dp)); Text("ذخیره AI") }
                }
            }

            items(sites, key = { it.index }) { site ->
                SectionCard(site.name.ifBlank { "سایت ${site.index}" }, subtitle = "WooCommerce برای محصول + WordPress Application Password برای آپلود WebP") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Switch(site.enabled, { v -> updateSite(site.index) { it.copy(enabled = v) } })
                        Spacer(Modifier.width(8.dp))
                        Text(if (site.enabled) "فعال برای انتشار" else "غیرفعال", fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedTextField(site.name, { v -> updateSite(site.index) { it.copy(name = v) } }, label = { Text("نام نمایشی سایت") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(site.baseUrl, { v -> updateSite(site.index) { it.copy(baseUrl = v) } }, label = { Text("آدرس سایت") }, placeholder = { Text("https://example.com") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(site.apiVersion, { v -> updateSite(site.index) { it.copy(apiVersion = v) } }, label = { Text("Woo API") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(site.consumerKey, { v -> updateSite(site.index) { it.copy(consumerKey = v) } }, label = { Text("Consumer Key — Read/Write") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(site.consumerSecret, { v -> updateSite(site.index) { it.copy(consumerSecret = v) } }, label = { Text("Consumer Secret") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Query-string auth")
                            Text("فقط اگر هاست Authorization Header را حذف می‌کند", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(site.queryStringAuth, { v -> updateSite(site.index) { it.copy(queryStringAuth = v) } })
                    }
                    BrandDivider()
                    Text("آپلود عکس در WordPress Media", fontWeight = FontWeight.Bold)
                    OutlinedTextField(site.wpUsername, { v -> updateSite(site.index) { it.copy(wpUsername = v) } }, label = { Text("نام کاربری مدیر/ویرایشگر وردپرس") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(site.wpAppPassword, { v -> updateSite(site.index) { it.copy(wpAppPassword = v) } }, label = { Text("Application Password وردپرس") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Text("Application Password با رمز ورود اصلی وردپرس فرق دارد. برای اینکه عکس WebP شفاف واقعاً در Media سایت آپلود شود لازم است.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val current = sites.first { it.index == site.index }
                            prefs.saveSite(current)
                            message = "${current.name} ذخیره شد."
                        }, modifier = Modifier.weight(1f)) { Text("ذخیره") }
                        OutlinedButton(onClick = {
                            val current = sites.first { it.index == site.index }
                            scope.launch {
                                busySite = site.index; message = null
                                val result = runCatching { withContext(Dispatchers.IO) { WooPublisher().test(current) } }.fold({ it }, { it.message ?: "خطا" })
                                message = "${current.name}: $result"; busySite = null
                            }
                        }, enabled = busySite == null, modifier = Modifier.weight(1f)) {
                            if (busySite == site.index) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.WifiTethering, null)
                            Spacer(Modifier.width(5.dp)); Text("تست")
                        }
                    }
                }
            }
            item { message?.let { SuccessText(it) } }
            item {
                SectionCard("نکته دسترسی") {
                    Text("برای ساخت محصول، Consumer Key سایت باید دسترسی Read/Write داشته باشد. برای آپلود تصویر هم WordPress Application Password لازم است (کاربران → شناسه شما → Application Passwords). اگر عکس آپلود نشود، کالا بدون عکس باز هم ثبت می‌شود.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun SmartProductScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val prefs = remember { PublishingPrefs(ctx) }
    val store = remember { LocalStore.get(ctx) }
    val scope = rememberCoroutineScope()

    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var processedFile by remember { mutableStateOf<File?>(null) }
    var aiJpegFile by remember { mutableStateOf<File?>(null) }
    var backgroundRemoved by rememberSaveable { mutableStateOf(false) }
    var threshold by remember { mutableFloatStateOf(48f) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var sku by rememberSaveable { mutableStateOf("") }
    var shortDescription by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var seoTitle by rememberSaveable { mutableStateOf("") }
    var seoDescription by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var tags by rememberSaveable { mutableStateOf("") }
    var extraHint by rememberSaveable { mutableStateOf("") }
    var opening by rememberSaveable { mutableStateOf("0") }
    var globalRegular by rememberSaveable { mutableStateOf("") }
    var globalSale by rememberSaveable { mutableStateOf("") }
    var samePrice by rememberSaveable { mutableStateOf(true) }
    var status by rememberSaveable { mutableStateOf("draft") }
    var busy by remember { mutableStateOf(false) }
    var stage by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var publishResults by remember { mutableStateOf<List<WooPublishResult>>(emptyList()) }
    var localSaved by rememberSaveable { mutableStateOf(false) }

    var sites by remember { mutableStateOf(prefs.sites()) }
    val selectedSites = remember { mutableStateMapOf<Int, Boolean>().apply { sites.forEach { put(it.index, it.enabled && it.baseUrl.isNotBlank()) } } }
    val regularBySite = remember { mutableStateMapOf<Int, String>().apply { sites.forEach { put(it.index, "") } } }
    val saleBySite = remember { mutableStateMapOf<Int, String>().apply { sites.forEach { put(it.index, "") } } }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val latest = prefs.sites()
                sites = latest
                latest.forEach { site ->
                    if (!selectedSites.containsKey(site.index)) selectedSites[site.index] = site.enabled && site.baseUrl.isNotBlank()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    suspend fun runAi(file: File) {
        if (!prefs.hasOpenAiKey()) {
            message = "عکس آماده شد. برای تولید خودکار توضیحات، OpenAI API Key را در تنظیمات انتشار وارد کن؛ یا متن‌ها را دستی تکمیل کن."
            return
        }
        stage = "در حال ساخت عنوان و توضیحات کامل…"
        val draft = withContext(Dispatchers.IO) {
            OpenAiProductClient(prefs.openAiKey(), prefs.openAiModel, prefs.aiProvider, prefs.openAiBaseUrl).generateDraft(file, extraHint)
        }
        name = draft.name
        shortDescription = draft.shortDescription
        description = draft.description
        seoTitle = draft.seoTitle
        seoDescription = draft.seoDescription
        category = draft.category
        tags = draft.tags.joinToString("، ")
        message = "پیش‌نویس هوشمند آماده شد؛ قبل از انتشار همه متن‌ها قابل ویرایش‌اند."
    }

    fun processImage(uri: Uri) {
        sourceUri = uri
        publishResults = emptyList(); localSaved = false; backgroundRemoved = false
        scope.launch {
            busy = true; error = null; message = null; stage = "آماده‌سازی عکس و ساخت WebP…"
            runCatching {
                val processed = ProductImageProcessor.prepareProductImage(ctx, uri, threshold.toInt())
                processedFile = processed.file
                aiJpegFile = processed.aiJpeg
                backgroundRemoved = processed.backgroundRemoved
                runAi(processed.aiJpeg)
                processed.warning?.let { warning ->
                    message = listOfNotNull(message, warning).joinToString("\n")
                }
            }.onFailure { error = it.message }
            busy = false; stage = ""
        }
    }

    fun retryBackgroundRemoval() {
        val uri = sourceUri ?: return
        scope.launch {
            busy = true; error = null; stage = "تلاش مجدد برای حذف بک‌گراند…"
            runCatching { ProductImageProcessor.retryBackgroundRemoval(ctx, uri, threshold.toInt()) }
                .onSuccess { result ->
                    processedFile = result.file
                    aiJpegFile = result.aiJpeg
                    backgroundRemoved = result.backgroundRemoved
                    message = if (result.backgroundRemoved) {
                        "حذف بک‌گراند انجام شد و WebP جدید آماده است."
                    } else {
                        result.warning ?: "مدل هنوز آماده نیست؛ ثبت محصول همچنان قابل ادامه است."
                    }
                }
                .onFailure { error = it.message }
            busy = false; stage = ""
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) processImage(uri)
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) {
            cameraUri?.let { processImage(it) }
        } else {
            message = "عکسی ثبت نشد."
        }
    }

    val launchCameraCapture: () -> Unit = {
        runCatching {
            val f = ProductImageProcessor.newCameraFile(ctx)
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
            cameraUri = uri
            camera.launch(uri)
        }.onFailure { t ->
            error = "باز کردن دوربین ناموفق بود: ${t.message ?: "خطای ناشناخته"}"
        }
    }

    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            launchCameraCapture()
        } else {
            error = "برای گرفتن عکس باید اجازه دوربین را به StoreHub بدهی."
        }
    }

    fun takePhoto() {
        error = null
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCameraCapture()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    DinalScreen(nav, "ثبت هوشمند کالا") { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 42.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                DinalHero("از عکس تا ۳ فروشگاه", "حذف پس‌زمینه سفید روی گوشی • پیشنویس AI • تیک سایت‌ها • ثبت ووکامرس") {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(42.dp))
                }
            }
            item {
                SectionCard("۱) عکس محصول", subtitle = "پس‌زمینه روشن از لبه‌های عکس حذف می‌شود؛ سفید داخل خود کالا می‌ماند") {
                    processedFile?.let { file ->
                        AsyncImage(model = file, contentDescription = "تصویر پردازش‌شده", modifier = Modifier.fillMaxWidth().height(280.dp))
                        AssistChip(
                            onClick = {},
                            label = { Text(if (backgroundRemoved) "WebP شفاف • پس‌زمینه حذف شد" else "WebP آماده • پس‌زمینه کم حذف شد") },
                            leadingIcon = { Icon(if (backgroundRemoved) Icons.Rounded.CheckCircle else Icons.Rounded.Info, null, Modifier.size(16.dp)) }
                        )
                        Text("حساسیت حذف پس‌زمینه: ${threshold.toInt()}")
                        Slider(value = threshold, onValueChange = { threshold = it }, valueRange = 24f..80f, enabled = !busy)
                        if (sourceUri != null) {
                            OutlinedButton(onClick = ::retryBackgroundRemoval, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.AutoFixHigh, null); Spacer(Modifier.width(6.dp)); Text("دوباره حذف پس‌زمینه")
                            }
                        }
                    } ?: sourceUri?.let { uri ->
                        AsyncImage(model = uri, contentDescription = "تصویر اولیه", modifier = Modifier.fillMaxWidth().height(240.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = ::takePhoto, enabled = !busy, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.PhotoCamera, null); Spacer(Modifier.width(5.dp)); Text("گرفتن عکس") }
                        OutlinedButton(onClick = { gallery.launch("image/*") }, enabled = !busy, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.PhotoLibrary, null); Spacer(Modifier.width(5.dp)); Text("گالری") }
                    }
                    OutlinedTextField(extraHint, { extraHint = it }, label = { Text("اطلاعاتی که از عکس معلوم نیست (اختیاری)") }, supportingText = { Text("مثلاً: جنس استیل، سایز ۵۱، برند گتر") }, modifier = Modifier.fillMaxWidth())
                    if (processedFile != null) OutlinedButton(onClick = {
                        val f = aiJpegFile ?: processedFile ?: return@OutlinedButton
                        scope.launch { busy = true; error = null; runCatching { runAi(f) }.onFailure { error = it.message }; busy = false }
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.AutoAwesome, null); Spacer(Modifier.width(6.dp)); Text("تولید دوباره پیش‌نویس AI") }
                }
            }

            if (busy) item { SectionCard("در حال پردازش") { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stage) } }
            item { ErrorText(error); message?.let { SuccessText(it) } }

            item {
                SectionCard("۲) پیش‌نویس محصول", subtitle = "قبل از انتشار هر بخش را بخواهی اصلاح کن") {
                    OutlinedTextField(name, { name = it }, label = { Text("نام محصول") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(sku, { sku = it }, label = { Text("SKU (اختیاری)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(shortDescription, { shortDescription = it }, label = { Text("توضیح کوتاه") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                    OutlinedTextField(description, { description = it }, label = { Text("توضیحات کامل") }, modifier = Modifier.fillMaxWidth(), minLines = 8)
                    OutlinedTextField(category, { category = it }, label = { Text("دسته‌بندی پیشنهادی") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(tags, { tags = it }, label = { Text("برچسب‌ها؛ با ویرگول جدا کن") }, modifier = Modifier.fillMaxWidth())
                    BrandDivider()
                    OutlinedTextField(seoTitle, { seoTitle = it }, label = { Text("عنوان پیشنهادی سئو") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(seoDescription, { seoDescription = it }, label = { Text("توضیحات متای پیشنهادی") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                }
            }

            item {
                SectionCard("۳) قیمت و مقصد انتشار", subtitle = "قیمت‌ها در کل StoreHub بر مبنای تومان هستند") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Switch(samePrice, { samePrice = it })
                        Spacer(Modifier.width(8.dp))
                        Text("یک قیمت برای هر ۳ سایت", fontWeight = FontWeight.SemiBold)
                    }
                    if (samePrice) {
                        MoneyTextField(globalRegular, { globalRegular = it }, "قیمت اصلی / قبل از حراج", modifier = Modifier.fillMaxWidth())
                        MoneyTextField(globalSale, { globalSale = it }, "قیمت حراج / بعد از تخفیف", modifier = Modifier.fillMaxWidth(), supportingText = "اگر حراج نداری خالی بگذار")
                    }
                    sites.forEach { site ->
                        val configured = site.baseUrl.isNotBlank() && site.consumerKey.isNotBlank() && site.consumerSecret.isNotBlank()
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)) {
                            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = selectedSites[site.index] == true, onCheckedChange = { selectedSites[site.index] = it }, enabled = configured)
                                    Column(Modifier.weight(1f)) {
                                        Text(site.name, fontWeight = FontWeight.Bold)
                                        Text(if (configured) site.baseUrl else "تنظیم نشده", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (!configured) TextButton(onClick = { nav.navigate("publishing_settings") }) { Text("تنظیم") }
                                }
                                if (!samePrice && selectedSites[site.index] == true) {
                                    MoneyTextField(regularBySite[site.index].orEmpty(), { regularBySite[site.index] = it }, "قیمت اصلی ${site.name}", modifier = Modifier.fillMaxWidth())
                                    MoneyTextField(saleBySite[site.index].orEmpty(), { saleBySite[site.index] = it }, "قیمت حراج ${site.name}", modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                    OutlinedButton(onClick = { nav.navigate("publishing_settings") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Settings, null); Spacer(Modifier.width(6.dp)); Text("تنظیم اتصال ۳ سایت و AI") }
                }
            }

            item {
                SectionCard("۴) تأیید نهایی") {
                    Text("وضعیت ثبت در سایت", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = status == "draft", onClick = { status = "draft" }, label = { Text("پیش‌نویس") })
                        FilterChip(selected = status == "publish", onClick = { status = "publish" }, label = { Text("انتشار مستقیم") })
                    }
                    OutlinedTextField(opening, { opening = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("موجودی اولیه مغازه") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Text("قبل از زدن دکمه، عکس، نام، توضیحات و قیمت‌ها را مرور کن. هیچ محصولی بدون تأیید این مرحله روی سایت ثبت نمی‌شود.", style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = {
                            val file = processedFile ?: return@Button
                            val targets = sites.filter { selectedSites[it.index] == true }
                            scope.launch {
                                busy = true; error = null; message = null; publishResults = emptyList(); stage = "در حال انتشار روی سایت‌ها…"
                                runCatching {
                                    require(targets.isNotEmpty()) { "حداقل یک سایت مقصد انتخاب کن." }
                                    require(name.isNotBlank()) { "نام محصول خالی است." }
                                    val publisher = WooPublisher()
                                    val results = withContext(Dispatchers.IO) {
                                        targets.map { site ->
                                            val regularText = if (samePrice) globalRegular else regularBySite[site.index].orEmpty()
                                            val saleText = if (samePrice) globalSale else saleBySite[site.index].orEmpty()
                                            val reg = parseToman(regularText)
                                            val sale = parseToman(saleText)
                                            publisher.publish(
                                                site,
                                                PublishProductDraft(
                                                    name = name.trim(), sku = sku.trim().ifBlank { null },
                                                    shortDescription = shortDescription.trim(), description = description.trim(),
                                                    seoTitle = seoTitle.trim(), seoDescription = seoDescription.trim(),
                                                    category = category.trim().ifBlank { null },
                                                    tags = tags.split(',', '،').map { it.trim() }.filter { it.isNotBlank() },
                                                    regularPrice = reg, salePrice = sale, status = status
                                                ),
                                                file
                                            )
                                        }
                                    }
                                    publishResults = results
                                    val success = results.firstOrNull { it.success }
                                    if (success != null && !localSaved) {
                                        val primarySite = targets.first { it.index == success.siteIndex }
                                        val regularText = if (samePrice) globalRegular else regularBySite[primarySite.index].orEmpty()
                                        val saleText = if (samePrice) globalSale else saleBySite[primarySite.index].orEmpty()
                                        val reg = parseToman(regularText); val sale = parseToman(saleText)
                                        store.saveProduct(
                                            ProductEntity(
                                                name = name.trim(), sku = sku.trim().ifBlank { null },
                                                price = if (sale > 0) sale else reg,
                                                imageUrl = file.toURI().toString(), productUrl = success.permalink,
                                                isEnabledForStore = true, category = category.trim().ifBlank { null },
                                                source = ProductEntity.SOURCE_MANUAL
                                            ),
                                            opening.toDoubleOrNull() ?: 0.0
                                        )
                                        localSaved = true
                                    }
                                    val ok = results.count { it.success }
                                    val fail = results.size - ok
                                    message = "$ok سایت موفق${if (fail > 0) " • $fail سایت ناموفق" else ""}. نتیجه هر سایت پایین نمایش داده شده است."
                                }.onFailure { error = it.message }
                                busy = false; stage = ""
                            }
                        },
                        enabled = !busy && processedFile != null && name.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(54.dp)
                    ) { Icon(Icons.Rounded.CloudUpload, null); Spacer(Modifier.width(7.dp)); Text(if (status == "draft") "تأیید و ثبت پیش‌نویس" else "تأیید و انتشار") }
                }
            }

            if (publishResults.isNotEmpty()) item {
                SectionCard("نتیجه انتشار") {
                    publishResults.forEach { r ->
                        Surface(shape = RoundedCornerShape(14.dp), color = if (r.success) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer) {
                            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (r.success) Icons.Rounded.CheckCircle else Icons.Rounded.Error, null)
                                    Spacer(Modifier.width(7.dp)); Text(r.siteName, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    r.productId?.let { Text("#$it", style = MaterialTheme.typography.bodySmall) }
                                }
                                Text(r.message, style = MaterialTheme.typography.bodySmall)
                                r.permalink?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                            }
                        }
                    }
                }
            }
        }
    }
}
