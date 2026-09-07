package ir.dinal.storehub.publishing

import android.content.Context
import android.net.Uri
import ir.dinal.storehub.data.LocalStore
import ir.dinal.storehub.data.ProductEntity
import ir.dinal.storehub.data.PublishingPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RankedLocalHit(
    val product: ProductEntity,
    val kind: String,
    val note: String = ""
) {
    companion object {
        const val BARCODE = "BARCODE"
        const val EXACT = "EXACT"
        const val SIMILAR = "SIMILAR"
    }
}

object LocalPhotoMatcher {
    suspend fun match(context: Context, uri: Uri, products: List<ProductEntity>): List<ProductEntity> =
        matchRanked(context, uri, products).map { it.product }.distinctBy { it.id }

    suspend fun matchRanked(context: Context, uri: Uri, products: List<ProductEntity>): List<RankedLocalHit> {
        val byId = LinkedHashMap<Long, RankedLocalHit>()
        fun add(product: ProductEntity, kind: String, note: String) {
            val current = byId[product.id]
            if (current == null || rank(kind) < rank(current.kind)) {
                byId[product.id] = RankedLocalHit(product, kind, note)
            }
        }

        val barcode = withContext(Dispatchers.IO) { PhotoBarcode.read(context, uri) }
        if (!barcode.isNullOrBlank()) {
            LocalStore.get(context).findByCode(barcode)?.let { add(it, RankedLocalHit.BARCODE, "بارکد $barcode") }
            products.filter { p ->
                p.barcode == barcode || p.sku == barcode || p.internalCode == barcode
            }.forEach { add(it, RankedLocalHit.BARCODE, "بارکد $barcode") }
        }

        val prefs = PublishingPrefs(context)
        var label = ""
        val queries = ArrayList<String>()
        if (prefs.hasOpenAiKey()) {
            val jpeg = ProductImageProcessor.jpegForVision(context, uri)
            val client = OpenAiProductClient(prefs.openAiKey(), prefs.openAiModel, prefs.aiProvider, prefs.openAiBaseUrl)
            val visionListed = (products.filter { it.isEnabledForStore } + products.filter { !it.isEnabledForStore }).distinctBy { it.id }
            val ranked = withContext(Dispatchers.IO) { client.matchLocalCatalogRanked(jpeg, visionListed) }
            label = ranked.label
            val map = products.associateBy { it.id }
            ranked.exact.forEach { id -> map[id]?.let { add(it, RankedLocalHit.EXACT, label.ifBlank { "همان کالا از روی عکس" }) } }
            ranked.similar.forEach { id -> map[id]?.let { add(it, RankedLocalHit.SIMILAR, "کالای مشابه در همین فروشگاه") } }
            val hint = withContext(Dispatchers.IO) { runCatching { client.identifySearchQueries(jpeg) }.getOrNull() }
            hint?.queries.orEmpty().forEach { queries += it }
            hint?.persianName?.takeIf { it.isNotBlank() }?.let { queries += it; if (label.isBlank()) label = it }
            hint?.visualQuery?.takeIf { it.isNotBlank() }?.let { queries += it }
            hint?.brand?.takeIf { it.isNotBlank() }?.let { queries += it }
            hint?.model?.takeIf { it.isNotBlank() }?.let { queries += it }
        }

        if (queries.isNotEmpty() || label.isNotBlank()) {
            val tokens = tokenize((queries + label).joinToString(" "))
            if (tokens.isNotEmpty()) {
                products.map { it to score(it, tokens) }
                    .filter { it.second > 0 }
                    .sortedByDescending { it.second }
                    .take(12)
                    .forEach { (p, s) ->
                        val kind = if (s >= 6) RankedLocalHit.EXACT else RankedLocalHit.SIMILAR
                        add(p, kind, if (kind == RankedLocalHit.EXACT) "نزدیک به «${label.ifBlank { tokens.first() }}»" else "مشابه در کاتالوگ خودت")
                    }
            }
        }

        val exactCategory = byId.values.firstOrNull { it.kind != RankedLocalHit.SIMILAR }?.product?.category
        if (!exactCategory.isNullOrBlank()) {
            products.filter { it.category.equals(exactCategory, true) && it.id !in byId.keys }
                .take(8)
                .forEach { add(it, RankedLocalHit.SIMILAR, "همان دسته: $exactCategory") }
        }

        return byId.values.sortedWith(
            compareBy<RankedLocalHit> { rank(it.kind) }
                .thenByDescending { it.product.isEnabledForStore }
                .thenBy { it.product.name }
        )
    }

    private fun rank(kind: String) = when (kind) {
        RankedLocalHit.BARCODE -> 0
        RankedLocalHit.EXACT -> 1
        else -> 2
    }

    private fun tokenize(text: String): List<String> = text.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .split(' ')
        .map { it.trim() }
        .filter { it.length >= 2 }
        .distinct()
        .take(16)

    private fun score(product: ProductEntity, tokens: List<String>): Int {
        val hay = listOfNotNull(product.name, product.sku, product.barcode, product.category, product.internalCode)
            .joinToString(" ").lowercase()
        return tokens.count { hay.contains(it) }
    }
}
