package ir.dinal.storehub.publishing

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import ir.dinal.storehub.data.PublishProductDraft
import ir.dinal.storehub.data.WooPublishResult
import ir.dinal.storehub.data.WooPublishSite
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class WooPublisher {
    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(70, TimeUnit.SECONDS)
        .writeTimeout(70, TimeUnit.SECONDS)
        .build()

    fun publish(site: WooPublishSite, draft: PublishProductDraft, imageFile: File): WooPublishResult =
        publish(site, draft, listOf(imageFile))

    fun publish(site: WooPublishSite, draft: PublishProductDraft, imageFiles: List<File>): WooPublishResult = runCatching {
        validate(site, draft)
        val files = imageFiles.filter { it.exists() && it.length() > 0L }
        val mediaIds = ArrayList<Long>()
        val failNotes = ArrayList<String>()
        files.forEachIndexed { index, file ->
            runCatching { uploadMedia(site, file, "${draft.name} ${index + 1}") }
                .onSuccess { mediaIds += it }
                .onFailure { failNotes += (it.message ?: "عکس ${index + 1} آپلود نشد") }
        }
        val categoryId = draft.category?.takeIf { it.isNotBlank() }?.let { findOrCreateCategory(site, it) }
        val json = JsonObject().apply {
            addProperty("name", draft.name)
            addProperty("type", "simple")
            addProperty("status", draft.status)
            addProperty("regular_price", draft.regularPrice.toLong().toString())
            if (draft.salePrice > 0) addProperty("sale_price", draft.salePrice.toLong().toString())
            if (!draft.sku.isNullOrBlank()) addProperty("sku", draft.sku)
            addProperty("short_description", draft.shortDescription)
            addProperty("description", draft.description)
            if (categoryId != null) add("categories", JsonArray().apply { add(JsonObject().apply { addProperty("id", categoryId) }) })
            if (mediaIds.isNotEmpty()) {
                add("images", JsonArray().apply {
                    mediaIds.forEach { id -> add(JsonObject().apply { addProperty("id", id) }) }
                })
            }
            add("meta_data", JsonArray().apply {
                if (draft.seoTitle.isNotBlank()) add(JsonObject().apply { addProperty("key", "_storehub_seo_title"); addProperty("value", draft.seoTitle) })
                if (draft.seoDescription.isNotBlank()) add(JsonObject().apply { addProperty("key", "_storehub_seo_description"); addProperty("value", draft.seoDescription) })
                if (draft.tags.isNotEmpty()) add(JsonObject().apply { addProperty("key", "_storehub_ai_tags"); addProperty("value", draft.tags.joinToString(",")) })
            })
        }
        fun post(includeSku: Boolean): Pair<Boolean, String> {
            if (!includeSku) json.remove("sku")
            val url = site.baseUrl.trimEnd('/') + "/wp-json/" + site.apiVersion.trim('/') + "/products"
            val req = authenticatedWooRequest(site, url, "POST", json.toString())
            client.newCall(req).execute().use { r ->
                return r.isSuccessful to r.body?.string().orEmpty().also { raw ->
                    if (!r.isSuccessful && includeSku) Unit
                    else if (!r.isSuccessful) error("WooCommerce HTTP ${r.code}: ${errorMessage(raw)}")
                }
            }
        }
        var (ok, raw) = post(true)
        if (!ok && !draft.sku.isNullOrBlank()) {
            val retry = post(false)
            ok = retry.first
            raw = retry.second
            if (!ok) error("WooCommerce: ${errorMessage(raw)}")
        } else if (!ok) error("WooCommerce: ${errorMessage(raw)}")
        val o = JsonParser.parseString(raw).asJsonObject
        val id = o.get("id")?.asLong
        val link = o.get("permalink")?.asString
        val extra = when {
            files.isEmpty() -> ""
            mediaIds.isEmpty() -> " کالا ثبت شد ولی عکس‌ها نرفتند: ${failNotes.firstOrNull().orEmpty()}"
            failNotes.isNotEmpty() -> " ${mediaIds.size} عکس رفت، ${failNotes.size} عکس نرفت."
            else -> " ${mediaIds.size} عکس هم آپلود شد."
        }
        WooPublishResult(
            site.index,
            site.name,
            true,
            id,
            link,
            "محصول با موفقیت ثبت شد${if (draft.status == "draft") " (پیش‌نویس)" else ""}.$extra"
        )
    }.getOrElse { e -> WooPublishResult(site.index, site.name, false, message = e.message ?: "خطای انتشار") }

    fun test(site: WooPublishSite): String {
        require(site.baseUrl.startsWith("https://")) { "آدرس سایت باید HTTPS باشد." }
        val u = site.baseUrl.trimEnd('/') + "/wp-json/" + site.apiVersion.trim('/') + "/products?per_page=1"
        val req = authenticatedWooRequest(site, u, "GET", null)
        client.newCall(req).execute().use { r -> if (!r.isSuccessful) error("HTTP ${r.code}"); return "اتصال WooCommerce موفق است." }
    }

    private fun validate(site: WooPublishSite, draft: PublishProductDraft) {
        require(site.baseUrl.startsWith("https://")) { "آدرس ${site.name} باید با https:// شروع شود." }
        require(site.consumerKey.startsWith("ck_")) { "Consumer Key ${site.name} معتبر نیست." }
        require(site.consumerSecret.startsWith("cs_")) { "Consumer Secret ${site.name} معتبر نیست." }
        require(draft.name.isNotBlank()) { "نام کالا خالی است." }
        require(draft.regularPrice > 0) { "قیمت اصلی باید بیشتر از صفر باشد." }
        if (draft.salePrice > 0) require(draft.salePrice < draft.regularPrice) { "قیمت حراج باید از قیمت اصلی کمتر باشد." }
        require(draft.status in setOf("draft", "publish"))
    }

    private fun uploadMedia(site: WooPublishSite, image: File, title: String): Long {
        val mime = when (image.extension.lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
        val filename = "storehub-${System.nanoTime()}.${image.extension.ifBlank { "jpg" }}"
        val attempts = mutableListOf<Pair<String, String>>()
        val user = site.wpUsername.trim()
        val pass = site.wpAppPassword.replace(" ", "")
        if (user.isNotBlank() && pass.isNotBlank()) attempts += user to pass
        attempts += site.consumerKey to site.consumerSecret
        require(attempts.isNotEmpty()) { "برای آپلود تصویر در ${site.name}، نام کاربری وردپرس و Application Password را وارد کن." }
        var last = "آپلود تصویر ناموفق بود."
        val url = site.baseUrl.trimEnd('/') + "/wp-json/wp/v2/media"
        for ((u, p) in attempts) {
            val auth = Credentials.basic(u, p)
            val rawReq = Request.Builder().url(url)
                .header("Authorization", auth)
                .header("Content-Disposition", "attachment; filename=\"$filename\"")
                .header("Content-Type", mime)
                .post(image.asRequestBody(mime.toMediaType()))
                .build()
            val rawId = tryMedia(rawReq)
            if (rawId != null) return rawId
            last = lastMediaError
            val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", filename, image.asRequestBody(mime.toMediaType()))
                .addFormDataPart("title", title)
                .build()
            val mpReq = Request.Builder().url(url)
                .header("Authorization", auth)
                .post(multipart)
                .build()
            val mpId = tryMedia(mpReq)
            if (mpId != null) return mpId
            last = lastMediaError
        }
        error("$last برای ${site.name}. Application Password وردپرس را در تنظیمات همان سایت چک کن.")
    }

    private var lastMediaError = "آپلود تصویر ناموفق بود."
    private fun tryMedia(req: Request): Long? {
        client.newCall(req).execute().use { r ->
            val raw = r.body?.string().orEmpty()
            if (r.isSuccessful) {
                val id = runCatching { JsonParser.parseString(raw).asJsonObject.get("id")?.asLong }.getOrNull()
                if (id != null) return id
                lastMediaError = "پاسخ آپلود عکس شناسه نداشت."
            } else lastMediaError = "HTTP ${r.code}: ${errorMessage(raw)}"
        }
        return null
    }

    private fun findOrCreateCategory(site: WooPublishSite, name: String): Long? {
        val base = site.baseUrl.trimEnd('/') + "/wp-json/" + site.apiVersion.trim('/') + "/products/categories"
        val searchUrl = (base.toHttpUrlOrNull() ?: return null).newBuilder().addQueryParameter("search", name).addQueryParameter("per_page", "50").build().toString()
        client.newCall(authenticatedWooRequest(site, searchUrl, "GET", null)).execute().use { r ->
            if (r.isSuccessful) {
                val arr = JsonParser.parseString(r.body?.string().orEmpty().ifBlank { "[]" }).asJsonArray
                arr.firstOrNull { it.asJsonObject.get("name")?.asString?.equals(name, true) == true }?.asJsonObject?.get("id")?.asLong?.let { return it }
            }
        }
        val body = JsonObject().apply { addProperty("name", name) }.toString()
        client.newCall(authenticatedWooRequest(site, base, "POST", body)).execute().use { r ->
            val raw = r.body?.string().orEmpty()
            if (!r.isSuccessful) return null
            return JsonParser.parseString(raw).asJsonObject.get("id")?.asLong
        }
    }

    private fun authenticatedWooRequest(site: WooPublishSite, urlText: String, method: String, json: String?): Request {
        val parsed = urlText.toHttpUrlOrNull() ?: error("آدرس ووکامرس نامعتبر است.")
        val b = parsed.newBuilder()
        val rb = Request.Builder()
        if (site.queryStringAuth) {
            b.addQueryParameter("consumer_key", site.consumerKey).addQueryParameter("consumer_secret", site.consumerSecret)
        } else {
            rb.header("Authorization", Credentials.basic(site.consumerKey, site.consumerSecret))
        }
        rb.url(b.build())
        return when (method) {
            "GET" -> rb.get().build()
            else -> rb.post((json ?: "{}").toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        }
    }

    private fun errorMessage(raw: String): String = runCatching {
        val o = JsonParser.parseString(raw).asJsonObject
        o.get("message")?.asString ?: o.getAsJsonObject("data")?.get("message")?.asString
    }.getOrNull().orEmpty().ifBlank { raw.take(350) }
}
