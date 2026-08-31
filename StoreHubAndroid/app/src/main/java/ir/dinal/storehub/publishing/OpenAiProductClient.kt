package ir.dinal.storehub.publishing

import android.util.Base64
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import ir.dinal.storehub.data.ProductAiDraft
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OpenAiProductClient(
    private val apiKey: String,
    private val model: String = "gpt-4o-mini",
    private val provider: String = "openai",
    private val baseUrl: String = "https://api.openai.com/v1"
) {
    private val client = OkHttpClient.Builder().connectTimeout(25, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build()

    fun generateDraft(imageFile: File, extraHint: String = ""): ProductAiDraft {
        require(apiKey.isNotBlank()) { "کلید هوش مصنوعی در تنظیمات ثبت نشده است." }
        val image64 = Base64.encodeToString(imageFile.readBytes(), Base64.NO_WRAP)
        val prompt = buildPrompt(extraHint)
        val text = when (provider.lowercase()) {
            "gemini" -> gemini(image64, prompt)
            else -> openAiCompatible(image64, prompt, imageFile.name.endsWith(".png", true))
        }
        return parseDraft(text)
    }

    private fun buildPrompt(extraHint: String) = """
        تصویر یک محصول فروشگاهی را بررسی کن. برای فروشگاه فارسی، پیش‌نویس دقیق و قابل ویرایش محصول بساز.
        اگر مدل/برند/جنس از تصویر قطعاً مشخص نیست، حدس قطعی نزن.
        توضیح کامل باید برای مشتری‌ای باشد که محصول را نمی‌شناسد.
        متن طبیعی و مناسب سئو باشد.
        ${if (extraHint.isBlank()) "" else "اطلاعات تکمیلی فروشنده: $extraHint"}

        فقط یک JSON معتبر بدون markdown برگردان با این ساختار:
        {
          "name":"عنوان محصول",
          "short_description":"توضیح کوتاه 2 تا 4 جمله",
          "description":"توضیحات کامل فارسی با پاراگراف‌بندی و بولت‌های متنی",
          "seo_title":"عنوان پیشنهادی سئو",
          "seo_description":"متای توضیحات حدود 140 تا 160 کاراکتر",
          "category":"دسته‌بندی پیشنهادی",
          "tags":["برچسب1","برچسب2","برچسب3"]
        }
    """.trimIndent()

    private fun openAiCompatible(image64: String, prompt: String, png: Boolean): String {
        val mime = if (png) "image/png" else "image/jpeg"
        val resolvedModel = sanitizeOpenAiModel(model)
        val root = baseUrl.trim().trimEnd('/').ifBlank { "https://api.openai.com/v1" }
        val body = JsonObject().apply {
            addProperty("model", resolvedModel)
            add("messages", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "user")
                    add("content", JsonArray().apply {
                        add(JsonObject().apply { addProperty("type", "text"); addProperty("text", prompt) })
                        add(JsonObject().apply {
                            addProperty("type", "image_url")
                            add("image_url", JsonObject().apply { addProperty("url", "data:$mime;base64,$image64") })
                        })
                    })
                })
            })
            add("response_format", JsonObject().apply { addProperty("type", "json_object") })
            addProperty("temperature", 0.35)
        }
        val req = Request.Builder()
            .url("$root/chat/completions")
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .header("Content-Type", "application/json")
            .header("HTTP-Referer", "https://github.com/jelviz/storehub-android")
            .header("X-Title", "StoreHub")
            .post(body.toString().toRequestBody(JSON))
            .build()
        client.newCall(req).execute().use { r ->
            val raw = r.body?.string().orEmpty()
            if (!r.isSuccessful) error("هوش مصنوعی HTTP ${r.code}: ${extractApiError(raw)}")
            val msg = JsonParser.parseString(raw).asJsonObject
                .getAsJsonArray("choices")?.get(0)?.asJsonObject
                ?.getAsJsonObject("message")?.get("content")?.asString
            if (msg.isNullOrBlank()) error("پاسخ متنی از مدل دریافت نشد.")
            return msg
        }
    }

    private fun gemini(image64: String, prompt: String): String {
        val models = listOfNotNull(
            model.trim().takeIf { it.isNotBlank() && !it.startsWith("gpt-") && it != "gpt-5.6-sol" },
            "gemini-2.0-flash",
            "gemini-2.5-flash",
            "gemini-1.5-flash"
        ).distinct()
        var last = "Gemini پاسخ نداد."
        for (m in models) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent?key=${apiKey.trim()}"
            val body = JsonObject().apply {
                add("contents", JsonArray().apply {
                    add(JsonObject().apply {
                        add("parts", JsonArray().apply {
                            add(JsonObject().apply { addProperty("text", prompt) })
                            add(JsonObject().apply {
                                add("inlineData", JsonObject().apply {
                                    addProperty("mimeType", "image/jpeg")
                                    addProperty("data", image64)
                                })
                            })
                        })
                    })
                })
                add("generationConfig", JsonObject().apply {
                    addProperty("temperature", 0.35)
                    addProperty("responseMimeType", "application/json")
                })
            }
            val req = Request.Builder().url(url).header("Content-Type", "application/json").post(body.toString().toRequestBody(JSON)).build()
            val (ok, code, raw) = client.newCall(req).execute().use { r ->
                Triple(r.isSuccessful, r.code, r.body?.string().orEmpty())
            }
            if (ok) {
                val t = JsonParser.parseString(raw).asJsonObject
                    .getAsJsonArray("candidates")?.get(0)?.asJsonObject
                    ?.getAsJsonObject("content")?.getAsJsonArray("parts")?.get(0)?.asJsonObject
                    ?.get("text")?.asString
                if (!t.isNullOrBlank()) return t
                last = "پاسخ Gemini خالی بود."
            } else {
                last = "Gemini HTTP $code: ${raw.take(280)}"
                if (code !in setOf(400, 404)) error(last)
            }
        }
        error(last)
    }

    private fun parseDraft(raw: String): ProductAiDraft {
        val json = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = json.indexOf('{')
        val end = json.lastIndexOf('}')
        require(start >= 0 && end > start) { "پاسخ هوش مصنوعی JSON نبود." }
        val o = JsonParser.parseString(json.substring(start, end + 1)).asJsonObject
        val tags = o.getAsJsonArray("tags")?.mapNotNull { runCatching { it.asString }.getOrNull() } ?: emptyList()
        val name = o.s("name")
        require(name.isNotBlank()) { "هوش مصنوعی نام کالا را تشخیص نداد. یک راهنمای کوتاه بنویس و دوباره تلاش کن." }
        return ProductAiDraft(
            name = name,
            shortDescription = o.s("short_description"),
            description = o.s("description"),
            seoTitle = o.s("seo_title"),
            seoDescription = o.s("seo_description"),
            category = o.s("category"),
            tags = tags
        )
    }

    private fun extractApiError(raw: String): String = runCatching {
        JsonParser.parseString(raw).asJsonObject.getAsJsonObject("error")?.get("message")?.asString
    }.getOrNull().orEmpty().ifBlank { raw.take(300) }

    private fun JsonObject.s(k: String): String = get(k)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        fun sanitizeOpenAiModel(model: String): String {
            val m = model.trim()
            if (m.isBlank() || m == "gpt-5.6-sol" || m == "gpt-5-mini" || m == "gpt-5.6") return "gpt-4o-mini"
            return m
        }
    }
}
