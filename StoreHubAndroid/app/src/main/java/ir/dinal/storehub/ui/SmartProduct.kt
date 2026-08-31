package ir.dinal.storehub.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import ir.dinal.storehub.data.*
import ir.dinal.storehub.publishing.IranianCatalogClient
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
    var threshold by remember { mutableFloatStateOf(64f) }
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
    var catalogMatches by remember { mutableStateOf<List<CatalogMatch>>(emptyList()) }
    var catalogDetail by remember { mutableStateOf<CatalogProductDetail?>(null) }
    var catalogImageUrl by remember { mutableStateOf<String?>(null) }
    var catalogAwaiting by remember { mutableStateOf(false) }
    var searchLabel by remember { mutableStateOf("") }

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
            message = listOfNotNull(message, "عکس آماده شد. برای تولید خودکار توضیحات، OpenAI API Key را در تنظیمات انتشار وارد کن؛ یا متن‌ها را دستی تکمیل کن.").joinToString("\n")
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
        message = listOfNotNull(message, "پیش‌نویس هوشمند آماده شد؛ قبل از انتشار همه متن‌ها قابل ویرایش‌اند.").joinToString("\n")
    }

    suspend fun runLocalPipeline(uri: Uri) {
        catalogAwaiting = false
        catalogMatches = emptyList()
        catalogDetail = null
        stage = "آماده‌سازی عکس و ساخت WebP…"
        val processed = ProductImageProcessor.prepareProductImage(ctx, uri, threshold.toInt())
        processedFile = processed.file
        aiJpegFile = processed.aiJpeg
        backgroundRemoved = processed.backgroundRemoved
        runAi(processed.aiJpeg)
        processed.warning?.let { warning ->
            message = listOfNotNull(message, warning).joinToString("\n")
        }
    }

    suspend fun lookupCatalogThenMaybeLocal(uri: Uri) {
        stage = "تشخیص کالا از روی عکس…"
        val jpeg = ProductImageProcessor.jpegForVision(ctx, uri)
        aiJpegFile = jpeg
        val queries = ArrayList<String>()
        extraHint.trim().takeIf { it.isNotBlank() }?.let { queries += it }
        if (prefs.hasOpenAiKey()) {
            val hint = runCatching {
                withContext(Dispatchers.IO) {
                    OpenAiProductClient(prefs.openAiKey(), prefs.openAiModel, prefs.aiProvider, prefs.openAiBaseUrl)
                        .identifySearchQueries(jpeg, extraHint)
                }
            }.getOrNull()
            if (hint != null) {
                queries += hint.queries
                hint.persianName.takeIf { it.isNotBlank() }?.let { queries += it }
                val brandModel = listOf(hint.brand, hint.model).filter { it.isNotBlank() }.joinToString(" ")
                if (brandModel.isNotBlank()) queries += brandModel
                searchLabel = hint.persianName.ifBlank { hint.queries.firstOrNull().orEmpty() }
            } else {
                searchLabel = extraHint.trim()
            }
        } else {
            searchLabel = extraHint.trim()
        }
        val unique = queries.map { it.trim() }.filter { it.length >= 2 }.distinct()
        if (unique.isEmpty()) {
            message = "برای جستجو در سایت‌های ایرانی نام کالا مشخص نشد؛ می‌رویم سراغ ثبت با عکس خودت."
            runLocalPipeline(uri)
            return
        }
        stage = "جستجو در دیجی‌کالا و ترب…"
        val matches = runCatching {
            withContext(Dispatchers.IO) { IranianCatalogClient().search(unique) }
        }.getOrDefault(emptyList())
        if (matches.isEmpty()) {
            message = "این کالا در سایت‌های ایرانی پیدا نشد؛ می‌رویم سراغ ثبت با عکس خودت."
            runLocalPipeline(uri)
            return
        }
        catalogMatches = matches
        catalogAwaiting = true
        message = "${matches.size} کالا در سایت‌های ایرانی پیدا شد. اگر مال توست انتخاب کن؛ اگر نیست با عکس خودت ادامه بده."
    }

    fun processImage(uri: Uri) {
        sourceUri = uri
        publishResults = emptyList(); localSaved = false; backgroundRemoved = false
        processedFile = null
        catalogMatches = emptyList(); catalogDetail = null; catalogImageUrl = null
        catalogAwaiting = false; searchLabel = ""
        scope.launch {
            busy = true; error = null; message = null
            runCatching { lookupCatalogThenMaybeLocal(uri) }.onFailure { error = it.message }
            busy = false; stage = ""
        }
    }

    fun skipCatalog() {
        val uri = sourceUri ?: return
        scope.launch {
            busy = true; error = null
            message = "با عکس خودت ادامه می‌دهیم."
            runCatching { runLocalPipeline(uri) }.onFailure { error = it.message }
            busy = false; stage = ""
        }
    }

    fun openCatalog(match: CatalogMatch) {
        scope.launch {
            busy = true; error = null; stage = "گرفتن توضیحات و عکس از ${match.source}…"
            runCatching {
                val detail = withContext(Dispatchers.IO) { IranianCatalogClient().details(match) }
                catalogDetail = detail
                catalogImageUrl = detail.imageUrls.firstOrNull() ?: detail.match.imageUrl
            }.onFailure { error = it.message }
            busy = false; stage = ""
        }
    }

    fun confirmCatalog() {
        val detail = catalogDetail ?: return
        val uri = sourceUri
        val imageUrl = catalogImageUrl
        scope.launch {
            busy = true; error = null; stage = "آماده‌سازی متن و عکس انتخاب‌شده…"
            runCatching {
                val downloaded = if (!imageUrl.isNullOrBlank()) {
                    withContext(Dispatchers.IO) { IranianCatalogClient().downloadImage(ctx, imageUrl) }
                } else null
                val processed = when {
                    downloaded != null -> ProductImageProcessor.prepareProductImage(ctx, downloaded, threshold.toInt())
                    uri != null -> ProductImageProcessor.prepareProductImage(ctx, uri, threshold.toInt())
                    else -> error("عکسی برای ثبت نیست.")
                }
                processedFile = processed.file
                aiJpegFile = processed.aiJpeg
                backgroundRemoved = processed.backgroundRemoved
                name = detail.match.title
                shortDescription = detail.shortDescription
                description = detail.description
                seoTitle = detail.seoTitle
                seoDescription = detail.seoDescription
                category = detail.category
                tags = (listOfNotNull(detail.brand.takeIf { it.isNotBlank() }) + detail.tags).distinct().joinToString("، ")
                if (globalRegular.isBlank()) {
                    detail.priceToman?.takeIf { it > 0 }?.let { globalRegular = it.toString() }
                }
                catalogAwaiting = false
                message = "متن و عکس از ${detail.match.source} پر شد. قبل از انتشار همه را چک کن."
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
                        "حذف پس‌زمینه انجام شد و WebP جدید آماده است."
                    } else {
                        result.warning ?: "پس‌زمینه سفید کم بود؛ ثبت کالا ادامه دارد. کالا را روی زمینه سفید عکس بگیر."
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
                DinalHero("از عکس تا ۳ فروشگاه", "نسخه ۱۶.۳.۱ — اول جستجو در سایت‌های ایرانی، اگر تأیید نکردی با عکس خودت ادامه می‌دهیم") {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(42.dp))
                }
            }
            item {
                SectionCard("۱) عکس محصول", subtitle = "اول در دیجی‌کالا و ترب می‌گردیم؛ اگر پیدا نشد یا تأیید نکردی، پس‌زمینه عکس خودت حذف می‌شود") {
                    processedFile?.let { file ->
                        TransparentImagePreview(file, Modifier.fillMaxWidth().height(280.dp))
                        AssistChip(
                            onClick = {},
                            label = { Text(if (backgroundRemoved) "WebP شفاف • پس‌زمینه حذف شد" else "WebP آماده • پس‌زمینه کم حذف شد") },
                            leadingIcon = { Icon(if (backgroundRemoved) Icons.Rounded.CheckCircle else Icons.Rounded.Info, null, Modifier.size(16.dp)) }
                        )
                        Text("خانه‌های شطرنجی یعنی آن قسمت شفاف شده. لکه خاکستری یعنی هنوز مانده.")
                        Text("حساسیت حذف پس‌زمینه: ${threshold.toInt()}")
                        Slider(value = threshold, onValueChange = { threshold = it }, valueRange = 20f..100f, enabled = !busy)
                        Text("اگر سایه ماند، عدد را بالا ببر و «دوباره حذف پس‌زمینه» بزن. اگر لبه کالا خورده شد، عدد را کم کن.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    OutlinedTextField(extraHint, { extraHint = it }, label = { Text("نام مدل یا راهنما (اختیاری)") }, supportingText = { Text("اگر قبل از عکس بنویسی، جستجوی دیجی‌کالا دقیق‌تر می‌شود. مثلاً: لاجیتک G102") }, modifier = Modifier.fillMaxWidth())
                    if (sourceUri != null) OutlinedButton(onClick = { sourceUri?.let(::processImage) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Search, null); Spacer(Modifier.width(6.dp)); Text("جستجو دوباره در سایت‌های ایرانی")
                    }
                    if (processedFile != null) OutlinedButton(onClick = {
                        val f = aiJpegFile ?: processedFile ?: return@OutlinedButton
                        scope.launch { busy = true; error = null; runCatching { runAi(f) }.onFailure { error = it.message }; busy = false }
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.AutoAwesome, null); Spacer(Modifier.width(6.dp)); Text("تولید دوباره پیش‌نویس AI") }
                }
            }

            if (catalogAwaiting) item {
                SectionCard(
                    "۲) پیدا شده در سایت‌های ایرانی",
                    subtitle = if (searchLabel.isBlank()) "اگر این کالا مال توست انتخاب کن؛ وگرنه با عکس خودت ادامه بده" else "جستجو: $searchLabel"
                ) {
                    catalogDetail?.let { detail ->
                        Text(detail.match.title, fontWeight = FontWeight.Bold)
                        Text("${detail.match.source}${if (detail.brand.isNotBlank()) " • ${detail.brand}" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (detail.imageUrls.isNotEmpty()) {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                detail.imageUrls.forEach { url ->
                                    val selected = url == catalogImageUrl
                                    AsyncImage(
                                        model = url,
                                        contentDescription = "عکس کاتالوگ",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(92.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .then(
                                                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                                else Modifier
                                            )
                                            .clickable { catalogImageUrl = url }
                                    )
                                }
                            }
                            Text("روی عکس بزن تا همان برای فروشگاه استفاده شود.", style = MaterialTheme.typography.bodySmall)
                        }
                        if (detail.priceToman != null && detail.priceToman > 0) {
                            Text("قیمت تقریبی بازار: ${toman(detail.priceToman.toDouble())}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(detail.description, style = MaterialTheme.typography.bodySmall, maxLines = 12, overflow = TextOverflow.Ellipsis)
                        Button(onClick = ::confirmCatalog, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.CheckCircle, null); Spacer(Modifier.width(6.dp)); Text("همین کالاست؛ متن و عکس را بردار")
                        }
                        OutlinedButton(onClick = { catalogDetail = null; catalogImageUrl = null }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text("این نیست، برگرد به لیست")
                        }
                    } ?: catalogMatches.forEach { match ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
                            modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) { openCatalog(match) }
                        ) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                AsyncImage(
                                    model = match.imageUrl,
                                    contentDescription = match.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp))
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(match.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(match.source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    match.priceToman?.takeIf { it > 0 }?.let {
                                        Text(toman(it.toDouble()), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                    OutlinedButton(onClick = ::skipCatalog, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text("هیچ‌کدام نیست؛ ادامه با عکس خودم")
                    }
                }
            }
            if (busy) item { SectionCard("در حال پردازش") { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stage) } }
            item { ErrorText(error); message?.let { SuccessText(it) } }

            if (!catalogAwaiting) item {
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

            if (!catalogAwaiting) item {
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

            if (!catalogAwaiting) item {
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

@Composable
private fun TransparentImagePreview(file: File, modifier: Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .drawBehind {
                val cell = 14.dp.toPx()
                val light = Color(0xFFE4E8EF)
                val dark = Color(0xFFB8C0CC)
                var row = 0
                var y = 0f
                while (y < size.height) {
                    var col = 0
                    var x = 0f
                    while (x < size.width) {
                        drawRect(
                            color = if ((row + col) % 2 == 0) light else dark,
                            topLeft = Offset(x, y),
                            size = Size(cell, cell)
                        )
                        x += cell
                        col++
                    }
                    y += cell
                    row++
                }
            }
    ) {
        AsyncImage(
            model = file,
            contentDescription = "تصویر پردازش‌شده",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}
