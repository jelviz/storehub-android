package ir.dinal.storehub.sync

import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Generic HTTPS inventory endpoint for Snapp Shop / Tapsi Shop vendor APIs.
 * The seller panel URL+token are stored on-device; StoreHub never reads stock back.
 */
class MarketplaceConnector(
    private val endpointUrl: String,
    private val token: String,
    override val channelCode: String
) : CommerceChannelConnector {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun updateStock(externalProductId: String, externalVariationId: String?, sku: String?, quantity: Int) =
        withContext(Dispatchers.IO) {
            require(endpointUrl.startsWith("https://")) { "آدرس API کانال باید HTTPS باشد." }
            val body = JsonObject().apply {
                addProperty("quantity", quantity.coerceAtLeast(0))
                if (externalProductId.isNotBlank()) addProperty("product_id", externalProductId)
                if (!externalVariationId.isNullOrBlank()) addProperty("variation_id", externalVariationId)
                if (!sku.isNullOrBlank()) addProperty("sku", sku)
            }
            val rb = Request.Builder()
                .url(endpointUrl)
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            if (token.isNotBlank()) rb.header("Authorization", "Bearer $token")
            client.newCall(rb.build()).execute().use { r ->
                if (!r.isSuccessful) error("کانال $channelCode HTTP ${r.code}: ${r.body?.string()?.take(240).orEmpty()}")
            }
        }
}
