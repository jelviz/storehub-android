package ir.dinal.storehub.publishing

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import ir.dinal.storehub.data.CatalogMatch
import ir.dinal.storehub.data.CatalogProductDetail
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class IranianCatalogClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(18, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(35, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun search(queries: List<String>, visualQuery: String? = null): List<CatalogMatch> {
        val q = queries.map { it.trim() }.filter { it.length >= 2 }.distinct().take(4)
        val found = LinkedHashMap<String, CatalogMatch>()
        fun addAll(items: List<CatalogMatch>) {
            items.forEach { found.putIfAbsent(it.source + it.sourceId, it) }
        }
        visualQuery?.trim()?.takeIf { it.length >= 3 }?.let { addAll(runCatching { searchDigikalaLenz(it) }.getOrDefault(emptyList())) }
        for (query in q) {
            addAll(runCatching { searchDigikala(query) }.getOrDefault(emptyList()))
            if (found.size >= 10) break
        }
        if (found.size < 4 && q.isNotEmpty()) {
            addAll(
                runCatching { searchTorob(q.first()) }.getOrDefault(emptyList()).filter { match ->
                    isRelevant(match.title + " " + match.titleEn, q)
                }
            )
        }
        return found.values.take(10)
    }

    fun details(match: CatalogMatch): CatalogProductDetail = when (match.source) {
        SOURCE_DIGIKALA -> digikalaDetails(match)
        else -> torobDetails(match)
    }

    fun downloadImage(context: Context, url: String): File {
        val req = request(url)
        client.newCall(req).execute().use { r ->
            val raw = r.body?.bytes() ?: ByteArray(0)
            if (!r.isSuccessful || raw.isEmpty()) error("دانلود عکس کالا ناموفق بود (HTTP ${r.code}).")
            val ext = when {
                url.contains(".webp", true) || (r.header("Content-Type")?.contains("webp") == true) -> "webp"
                url.contains(".png", true) -> "png"
                else -> "jpg"
            }
            val dir = File(context.cacheDir, "catalog").apply { mkdirs() }
            val out = File(dir, "catalog-${System.nanoTime()}.$ext")
            out.writeBytes(raw)
            return out
        }
    }

    fun downloadImages(context: Context, urls: List<String>): List<File> {
        val seen = LinkedHashSet<String>()
        val files = ArrayList<File>()
        for (url in urls) {
            val clean = url.trim()
            if (clean.isBlank() || !clean.startsWith("http")) continue
            val key = clean.substringBefore('?')
            if (!seen.add(key)) continue
            runCatching { downloadImage(context, largerImage(clean)) }.onSuccess { files += it }
        }
        require(files.isNotEmpty()) { "هیچ عکسی از صفحه کالا دانلود نشد." }
        return files
    }

    private fun searchDigikala(query: String): List<CatalogMatch> =
        parseDigikalaProducts(getJson("https://api.digikala.com/v1/search/?q=${enc(query)}&page=1"))

    private fun searchDigikalaLenz(query: String): List<CatalogMatch> =
        parseDigikalaProducts(getJson("https://api.digikala.com/v1/search/text-lenz/?q=${enc(query)}&page=1"))

    private fun parseDigikalaProducts(root: JsonObject): List<CatalogMatch> {
        val products = root.obj("data")?.arr("products") ?: return emptyList()
        return products.take(8).mapNotNull { el ->
            if (!el.isJsonObject) return@mapNotNull null
            val p = el.asJsonObject
            val id = p.get("id")?.plain() ?: return@mapNotNull null
            val title = p.s("title_fa").ifBlank { p.s("title_en") }
            if (title.isBlank()) return@mapNotNull null
            val img = firstUrl(p.obj("images")?.get("main"))
            val price = rialsToToman(p.obj("default_variant")?.obj("price")?.long("selling_price"))
            val uri = p.obj("url")?.s("uri")
            CatalogMatch(
                source = SOURCE_DIGIKALA,
                sourceId = id,
                title = title,
                titleEn = p.s("title_en"),
                imageUrl = img?.let(::largerImage),
                priceToman = price,
                webUrl = uri?.let { "https://www.digikala.com$it" }
            )
        }
    }

    private fun digikalaDetails(match: CatalogMatch): CatalogProductDetail {
        val root = getJson("https://api.digikala.com/v2/product/${match.sourceId}/")
        val p = root.obj("data")?.obj("product") ?: error("جزئیات کالا از دیجی‌کالا نیامد.")
        val title = p.s("title_fa").ifBlank { match.title }
        val review = p.obj("review")?.s("description").orEmpty()
        val expert = p.obj("expert_reviews")
        val shortReview = expert?.s("short_review").orEmpty()
        val expertDesc = expert?.s("description").orEmpty()
        val body = review.ifBlank { expertDesc }
        val specs = formatSpecs(p.arr("specifications"))
        val description = buildString {
            if (body.isNotBlank()) append(body.trim())
            if (specs.isNotBlank()) {
                if (isNotEmpty()) append("\n\n")
                append("مشخصات فنی:\n")
                append(specs)
            }
        }.ifBlank { title }
        val images = linkedSetOf<String>()
        firstUrl(p.obj("images")?.get("main"))?.let { images.add(largerImage(it)) }
        p.obj("images")?.arr("list")?.forEach { item ->
            firstUrl(item)?.let { images.add(largerImage(it)) }
        }
        match.imageUrl?.let { images.add(it) }
        val brand = p.obj("brand")?.s("title_fa").orEmpty().ifBlank { p.obj("brand")?.s("title_en").orEmpty() }
        val category = p.obj("category")?.s("title_fa").orEmpty()
        val price = rialsToToman(p.obj("default_variant")?.obj("price")?.long("selling_price")) ?: match.priceToman
        val short = shortReview.ifBlank { body.split('.', '؟', '!').firstOrNull()?.trim().orEmpty() }.ifBlank { title }
        val tags = listOfNotNull(brand.takeIf { it.isNotBlank() }, category.takeIf { it.isNotBlank() }, match.titleEn.takeIf { it.isNotBlank() })
        return CatalogProductDetail(
            match = match.copy(title = title, priceToman = price, imageUrl = images.firstOrNull() ?: match.imageUrl),
            description = description,
            shortDescription = short.take(420),
            category = category,
            brand = brand,
            tags = tags,
            imageUrls = images.toList().take(24),
            seoTitle = title.take(70),
            seoDescription = short.take(160),
            priceToman = price
        )
    }

    private fun searchTorob(query: String): List<CatalogMatch> {
        val url = "https://api.torob.com/v4/base-product/search/?query=${enc(query)}&page=0&size=12"
        val root = getJson(url)
        val results = root.arr("results") ?: return emptyList()
        return results.mapNotNull { el ->
            if (!el.isJsonObject) return@mapNotNull null
            val p = el.asJsonObject
            if (p.get("is_adv")?.asBoolean == true) return@mapNotNull null
            val id = p.s("random_key")
            val title = p.s("name1").ifBlank { p.s("name2") }
            if (id.isBlank() || title.isBlank()) return@mapNotNull null
            val rials = p.long("price")
            CatalogMatch(
                source = SOURCE_TOROB,
                sourceId = id,
                title = title,
                titleEn = p.s("name2"),
                imageUrl = p.s("image_url").ifBlank { null },
                priceToman = rialsToToman(rials),
                webUrl = p.s("web_client_absolute_url").takeIf { it.isNotBlank() }?.let { "https://torob.com$it" }
                    ?: p.s("more_info_url").ifBlank { null }
            )
        }
    }

    private fun torobDetails(match: CatalogMatch): CatalogProductDetail {
        val api = match.webUrl?.takeIf { it.contains("api.torob.com") }
            ?: "https://api.torob.com/v4/base-product/details/?prk=${enc(match.sourceId)}"
        val root = runCatching { getJson(api) }.getOrNull()
        val name = root?.s("name").orEmpty().ifBlank { root?.s("persian_name").orEmpty() }.ifBlank { match.title }
        val desc = listOf(
            root?.s("description"),
            root?.s("content"),
            root?.obj("base_product")?.s("description")
        ).firstOrNull { !it.isNullOrBlank() }.orEmpty()
        val images = linkedSetOf<String>()
        match.imageUrl?.let { images.add(it) }
        root?.arr("media_urls")?.forEach { el ->
            if (el.isJsonPrimitive) images.add(el.asString)
        }
        firstUrl(root?.get("image_url"))?.let { images.add(it) }
        val text = desc.ifBlank { name }
        return CatalogProductDetail(
            match = match.copy(title = name),
            description = text,
            shortDescription = text.take(280),
            category = "",
            brand = "",
            tags = emptyList(),
            imageUrls = images.filter { it.startsWith("http") }.distinct().take(24),
            seoTitle = name.take(70),
            seoDescription = text.take(160),
            priceToman = match.priceToman
        )
    }

    private fun getJson(url: String): JsonObject {
        val req = request(url)
        client.newCall(req).execute().use { r ->
            val raw = r.body?.string().orEmpty()
            if (!r.isSuccessful) error("جستجوی فروشگاه ایرانی HTTP ${r.code}")
            val parsed = JsonParser.parseString(raw)
            if (!parsed.isJsonObject) error("پاسخ فروشگاه ایرانی نامعتبر بود.")
            return parsed.asJsonObject
        }
    }

    private fun request(url: String): Request = Request.Builder()
        .url(url.toHttpUrl())
        .header("User-Agent", UA)
        .header("Accept", "application/json,text/plain,*/*")
        .header("Accept-Language", "fa-IR,fa;q=0.9,en;q=0.6")
        .header("Referer", "https://www.digikala.com/")
        .get()
        .build()

    private fun formatSpecs(specs: JsonArray?): String {
        if (specs == null) return ""
        val lines = ArrayList<String>()
        for (group in specs) {
            if (!group.isJsonObject) continue
            val attrs = group.asJsonObject.arr("attributes") ?: continue
            for (attr in attrs) {
                if (!attr.isJsonObject) continue
                val a = attr.asJsonObject
                val title = a.s("title")
                val values = a.arr("values")?.mapNotNull { runCatching { it.asString }.getOrNull() }?.filter { it.isNotBlank() }.orEmpty()
                if (title.isNotBlank() && values.isNotEmpty()) lines += "• $title: ${values.joinToString("، ")}"
            }
        }
        return lines.take(24).joinToString("\n")
    }

    private fun isRelevant(title: String, queries: List<String>): Boolean {
        val hay = normalize(title)
        val tokens = queries.flatMap { it.split(' ', '-', '_', '/', '،') }
            .map { normalize(it) }
            .filter { it.length >= 3 && it !in STOP }
        if (tokens.isEmpty()) return false
        return tokens.count { hay.contains(it) } >= minOf(2, tokens.size)
    }

    private fun normalize(s: String) = s.lowercase()
        .replace('ك', 'ک').replace('ي', 'ی').replace('\u200c', ' ')
        .replace(Regex("[^\\p{L}\\p{Nd}]+"), " ").trim()

    private fun firstUrl(el: JsonElement?): String? {
        if (el == null || el.isJsonNull) return null
        when {
            el.isJsonArray && el.asJsonArray.size() > 0 -> return firstUrl(el.asJsonArray[0])
            el.isJsonPrimitive -> {
                val s = el.asString
                return s.takeIf { it.startsWith("http") }
            }
            el.isJsonObject -> {
                val o = el.asJsonObject
                return firstUrl(o.get("url")) ?: firstUrl(o.get("webp_url")) ?: firstUrl(o.get("image_url"))
            }
        }
        return null
    }

    private fun largerImage(url: String): String =
        url.replace(Regex("h_\\d+,w_\\d+"), "h_1200,w_1200")

    private fun rialsToToman(rials: Long?): Long? {
        if (rials == null || rials <= 0) return null
        return rials / 10
    }

    private fun enc(v: String) = URLEncoder.encode(v, Charsets.UTF_8.name())

    private fun JsonObject.s(k: String): String = get(k)?.plain().orEmpty()

    private fun JsonObject.obj(k: String): JsonObject? =
        get(k)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.arr(k: String): JsonArray? =
        get(k)?.takeIf { it.isJsonArray }?.asJsonArray

    private fun JsonObject.long(k: String): Long? = get(k)?.takeIf { it.isJsonPrimitive }?.let {
        runCatching { it.asLong }.getOrNull()
    }

    private fun JsonElement.plain(): String? {
        if (!isJsonPrimitive) return null
        val p = asJsonPrimitive
        return when {
            p.isString -> p.asString
            p.isNumber -> p.asNumber.toString()
            p.isBoolean -> p.asBoolean.toString()
            else -> null
        }
    }

    companion object {
        const val SOURCE_DIGIKALA = "دیجی‌کالا"
        const val SOURCE_TOROB = "ترب"
        private const val UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
        private val STOP = setOf("مدل", "کالا", "محصول", "اصل", "خرید", "فروش", "برای", "the", "and", "for")
    }
}
