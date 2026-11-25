package com.nuvio.app.music

import android.content.Context
import android.util.Log
import com.nuvio.app.music.engine.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Looks for a larger catalogue cover when the YouTube Music thumbnail is visibly soft. */
internal object MusicArtworkResolver {
    private const val TAG = "MusicArtworkResolver"
    private const val PREFS = "nuvio_music_artwork_cache"
    private const val CACHE_MAX = 256

    @Volatile private var preferences: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (preferences == null) synchronized(this) {
            if (preferences == null) {
                preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            }
        }
    }

    suspend fun resolve(context: Context, song: Song): String? = withContext(Dispatchers.IO) {
        init(context)
        val title = song.title.trim()
        val artist = song.artist.trim()
        if (title.isBlank() || artist.isBlank()) return@withContext song.thumbnailUrl

        val cacheKey = "art_${(normalize(title) + "|" + normalize(artist)).hashCode().toUInt().toString(16)}"
        val prefs = preferences ?: return@withContext song.thumbnailUrl
        val cached = prefs.getString(cacheKey, null)
        if (cached != null) {
            val parts = cached.split('|', limit = 2)
            if (parts.size == 2 && System.currentTimeMillis() - parts[0].toLongOrNull().orZero() < CACHE_TTL_MS) {
                return@withContext parts[1].takeIf(String::isNotBlank) ?: song.thumbnailUrl
            }
        }

        val resolved = runCatching { findOnItunes(title, artist) }
            .getOrNull()
            ?: runCatching { findOnDeezer(title, artist) }.getOrNull()
        if (!resolved.isNullOrBlank() && resolved != song.thumbnailUrl) {
            val now = System.currentTimeMillis()
            val all = prefs.all.filterKeys { it.startsWith("art_") }
            val edit = prefs.edit().putString(cacheKey, "$now|$resolved")
            if (all.size >= CACHE_MAX) {
                all.minByOrNull { (_, value) -> value?.toString()?.substringBefore('|')?.toLongOrNull() ?: 0L }
                    ?.key?.let(edit::remove)
            }
            edit.apply()
            Log.d(TAG, "Resolved higher-resolution artwork for ${song.title}")
        }
        resolved ?: song.thumbnailUrl
    }

    private fun findOnItunes(title: String, artist: String): String? {
        val term = URLEncoder.encode("$artist $title", Charsets.UTF_8.name())
        val json = getJson("https://itunes.apple.com/search?term=$term&entity=song&limit=8")
        val expectedTitle = normalize(title)
        val artistTokens = artist.split(',', '&', '•').map(::normalize).filter { it.length >= 2 }
        val results = json.optJSONArray("results") ?: return null
        for (index in 0 until results.length()) {
            val item = results.optJSONObject(index) ?: continue
            if (normalize(item.optString("trackName")) != expectedTitle) continue
            val resultArtist = normalize(item.optString("artistName"))
            if (artistTokens.isNotEmpty() && artistTokens.none { token -> resultArtist.contains(token) }) continue
            val artwork = item.optString("artworkUrl100").takeIf(String::isNotBlank) ?: continue
            return artwork.replace(Regex("\\d+x\\d+bb"), "1000x1000bb")
        }
        return null
    }

    private fun findOnDeezer(title: String, artist: String): String? {
        val query = URLEncoder.encode("\"$title\" \"$artist\"", Charsets.UTF_8.name())
        val json = getJson("https://api.deezer.com/search?q=$query&limit=8")
        val expectedTitle = normalize(title)
        val artistTokens = artist.split(',', '&', '•').map(::normalize).filter { it.length >= 2 }
        val results = json.optJSONArray("data") ?: return null
        for (index in 0 until results.length()) {
            val item = results.optJSONObject(index) ?: continue
            if (normalize(item.optString("title")) != expectedTitle) continue
            val resultArtist = normalize(item.optJSONObject("artist")?.optString("name").orEmpty())
            if (artistTokens.isNotEmpty() && artistTokens.none { token -> resultArtist.contains(token) }) continue
            item.optJSONObject("album")?.optString("cover_xl")
                ?.takeIf(String::isNotBlank)?.let { return it }
        }
        return null
    }

    private fun getJson(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 3_000
        connection.readTimeout = 3_000
        connection.setRequestProperty("User-Agent", "NuvioOne/1.0 (Android)")
        return try {
            if (connection.responseCode !in 200..299) error("Artwork lookup returned ${connection.responseCode}")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            require(body.length <= 1_000_000) { "Artwork response too large" }
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .filter(Char::isLetterOrDigit)

    private fun Long?.orZero(): Long = this ?: 0L

    private const val CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1000
}
