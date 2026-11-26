package com.nuvio.app.music

import android.util.Log
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import nuvio.composeapp.generated.resources.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class GeminiSuggestion(
    val type: String,
    val title: String,
    val artist: String,
    val year: Int?,
    val reason: String,
)

data class GeminiAvailableModel(
    val id: String,
    val displayName: String,
)

/**
 * A small direct client for the user's own Gemini API key. Gemini only proposes titles; the app
 * verifies them against YouTube Music before they appear and never lets Gemini choose a stream.
 */
internal object MusicGeminiClient {
    private const val HOST = "https://generativelanguage.googleapis.com"
    private const val TAG = "MusicGeminiClient"
    private val JSON = "application/json".toMediaType()

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    fun generate(apiKey: String, model: String, prompt: String): Result<List<GeminiSuggestion>> = runCatching {
        val key = apiKey.trim()
        if (key.isBlank()) throw MusicTextException(MusicText.Localized(Res.string.music_gemini_error_add_key))
        val modelId = model.trim().ifBlank { GeminiSettings.DEFAULT_MODEL }
        val body = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                )
            )
            val generationConfig = JSONObject()
                .put("temperature", 0.65)
                .put("responseMimeType", "application/json")
            if (modelId.startsWith("gemini-3")) {
                generationConfig.put(
                    "thinkingConfig",
                    JSONObject().put("thinkingLevel", "low"),
                )
            }
            put("generationConfig", generationConfig)
        }
        val request = Request.Builder()
            .url("$HOST/v1beta/models/$modelId:generateContent")
            .header("x-goog-api-key", key)
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()
        try {
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = errorDetail(raw)
                    Log.w(TAG, "Gemini returned HTTP ${response.code} for model=$modelId; detail=$detail")
                    throw MusicTextException(friendlyError(response.code, raw))
                }
                val root = runCatching { JSONObject(raw) }.getOrElse {
                    throw MusicTextException(MusicText.Localized(Res.string.music_gemini_error_unusable_response))
                }
                val text = root.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    ?.trim()
                    ?.removePrefix("```json")
                    ?.removePrefix("```")
                    ?.removeSuffix("```")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: throw MusicTextException(MusicText.Localized(Res.string.music_gemini_error_no_suggestions))
                parseSuggestions(text)
            }
        } catch (error: InterruptedIOException) {
            Log.w(TAG, "Gemini request timed out for model=$modelId (${error.javaClass.simpleName})")
            throw error
        }
    }

    fun listModels(apiKey: String): Result<List<GeminiAvailableModel>> = runCatching {
        val key = apiKey.trim()
        require(key.isNotBlank()) { "No saved Gemini API key." }

        buildList {
            var pageToken: String? = null
            var pages = 0
            do {
                val url = okhttp3.HttpUrl.Builder()
                    .scheme("https")
                    .host("generativelanguage.googleapis.com")
                    .addPathSegments("v1beta/models")
                    .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
                    .build()
                val request = Request.Builder()
                    .url(url)
                    .header("x-goog-api-key", key)
                    .get()
                    .build()
                val responseBody = http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Couldn't load Gemini models (HTTP ${response.code}).")
                    }
                    response.body?.string().orEmpty()
                }
                val root = JSONObject(responseBody)
                val models = root.optJSONArray("models") ?: JSONArray()
                for (index in 0 until models.length()) {
                    val model = models.optJSONObject(index) ?: continue
                    val methods = model.optJSONArray("supportedGenerationMethods") ?: continue
                    if ((0 until methods.length()).none { methods.optString(it) == "generateContent" }) continue
                    val id = model.optString("name")
                        .removePrefix("models/")
                        .trim()
                    if (id.isBlank()) continue
                    add(
                        GeminiAvailableModel(
                            id = id,
                            displayName = model.optString("displayName").trim().ifBlank { id },
                        )
                    )
                }
                pageToken = root.optString("nextPageToken").takeIf { it.isNotBlank() && it != "null" }
                pages++
            } while (pageToken != null && pages < 20)
        }.distinctBy { it.id }.sortedBy { it.displayName.lowercase() }
    }

    private fun errorDetail(raw: String): String = runCatching {
        JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty()
    }.getOrDefault("")
        .replace(Regex("(?i)AIza[0-9A-Za-z_-]{20,}"), "[redacted]")
        .replace(Regex("(?i)(key\\s*[=:]\\s*)[^\\s,;]+"), "$1[redacted]")
        .replace(Regex("\\s+"), " ")
        .take(240)

    /**
     * Reads the JSON Gemini was asked for. Anything malformed or incomplete is dropped rather
     * than shown, so a partial answer can never become a broken row.
     */
    internal fun parseSuggestions(text: String): List<GeminiSuggestion> {
        val payload = runCatching { JSONObject(text) }.getOrElse {
            throw MusicTextException(MusicText.Localized(Res.string.music_gemini_error_format))
        }
        val items = payload.optJSONArray("items")
            ?: throw MusicTextException(MusicText.Localized(Res.string.music_gemini_error_no_suggestions))
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val type = item.optString("type").lowercase().ifBlank { "track" }
                val title = item.optString("title").trim()
                val artist = item.optString("artist").trim()
                if (title.isBlank() || artist.isBlank()) continue
                if (type != "track" && type != "album") continue
                add(
                    GeminiSuggestion(
                        type = type,
                        title = title,
                        artist = artist,
                        year = item.optInt("year", 0).takeIf { it > 1900 },
                        reason = item.optString("reason").trim(),
                    )
                )
            }
        }.distinctBy { "${it.type}|${it.title.lowercase()}|${it.artist.lowercase()}" }
    }

    private fun friendlyError(code: Int, raw: String): MusicText {
        val detail = runCatching {
            JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("")
        return when (code) {
            400 -> if (detail.contains("API key", ignoreCase = true)) {
                MusicText.Localized(Res.string.music_gemini_error_rejected_key)
            } else {
                MusicText.Localized(Res.string.music_gemini_error_bad_request)
            }
            401, 403 -> MusicText.Localized(Res.string.music_gemini_error_forbidden)
            404 -> MusicText.Localized(Res.string.music_gemini_error_model_missing)
            429 -> MusicText.Localized(Res.string.music_gemini_error_quota)
            in 500..599 -> MusicText.Localized(Res.string.music_gemini_error_unavailable)
            else -> detail.takeIf { it.isNotBlank() }?.let { MusicText.Raw(it) }
                ?: MusicText.Localized(Res.string.music_gemini_error_http, listOf(code))
        }
    }
}
