package ir.dinal.storehub.publishing

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Lightweight text assistant used by StoreHub.
 *
 * StoreHub never reads ChatGPT app conversations. It sends only the text the user writes
 * plus the local StoreHub context that the user explicitly allows on the assistant screen.
 */
class DinalAssistantClient(
    private val apiKey: String,
    private val model: String = "gpt-4o-mini"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    data class Turn(val role: String, val text: String)

    fun ask(
        question: String,
        history: List<Turn> = emptyList(),
        storeContext: String = ""
    ): String {
        require(apiKey.isNotBlank()) { "کلید OpenAI در تنظیمات وارد نشده است." }
        require(question.isNotBlank()) { "پیام خالی است." }

        val historyText = history.takeLast(10).joinToString("\n") { turn ->
            val who = if (turn.role == "assistant") "دستیار" else "کاربر"
            "$who: ${turn.text.take(1800)}"
        }

        val prompt = buildString {
            appendLine("تو «دستیار DINAL» داخل اپ مدیریت فروشگاه هستی.")
            appendLine("فارسی، کوتاه، عملی و فروشگاهی جواب بده. عددهای مالی را با واحد تومان بنویس.")
            appendLine("اگر اطلاعات محلی کافی نیست، واضح بگو چه چیزی لازم است و چیزی را حدس قطعی نزن.")
            appendLine("هیچ‌وقت ادعا نکن به مکالمات اپ ChatGPT یا حافظه ChatGPT دسترسی داری؛ فقط همین متن و داده محلی زیر را داری.")
            if (storeContext.isNotBlank()) {
                appendLine()
                appendLine("=== داده محلی StoreHub ===")
                appendLine(storeContext.take(28000))
                appendLine("=== پایان داده محلی ===")
            }
            if (historyText.isNotBlank()) {
                appendLine()
                appendLine("=== چند پیام اخیر ===")
                appendLine(historyText)
            }
            appendLine()
            appendLine("پرسش جدید کاربر: $question")
        }

        val body = JsonObject().apply {
            addProperty("model", OpenAiProductClient.sanitizeOpenAiModel(model))
            add("messages", com.google.gson.JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", prompt)
                })
            })
            addProperty("temperature", 0.4)
            addProperty("max_tokens", 1800)
        }

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("OpenAI HTTP ${response.code}: ${extractApiError(raw)}")
            }
            val msg = JsonParser.parseString(raw).asJsonObject
                .getAsJsonArray("choices")?.get(0)?.asJsonObject
                ?.getAsJsonObject("message")?.get("content")?.asString
            if (msg.isNullOrBlank()) error("پاسخ متنی از OpenAI دریافت نشد.")
            return msg
        }
    }

    private fun extractApiError(raw: String): String = runCatching {
        JsonParser.parseString(raw).asJsonObject
            .getAsJsonObject("error")
            ?.get("message")
            ?.asString
    }.getOrNull().orEmpty().ifBlank { raw.take(300) }
}
