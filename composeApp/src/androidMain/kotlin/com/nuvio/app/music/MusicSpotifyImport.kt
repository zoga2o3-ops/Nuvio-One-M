package com.nuvio.app.music

import android.content.Context
import com.nuvio.app.music.engine.data.YtMusicRepository
import com.nuvio.app.music.engine.data.model.SearchFilter
import com.nuvio.app.music.engine.data.model.SearchResult
import com.nuvio.app.music.engine.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class SpotifyImportResult(
    val playlistName: String,
    val imported: Int,
    val matched: Int,
    val total: Int
)

/**
 * Imports a public Spotify playlist by reading the track list from its embed page. Each entry is
 * matched to a YouTube Music song and written into a normal local playlist.
 *
 * No Spotify account, API key or token is involved; this only reads what the public embed page
 * already exposes.
 */
object MusicSpotifyImport {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private val idPattern = Regex("""(?:playlist/|playlist:)([A-Za-z0-9]+)""")

    suspend fun import(
        context: Context,
        playlistUrl: String,
        onProgress: suspend (done: Int, total: Int) -> Unit
    ): Result<SpotifyImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val id = idPattern.find(playlistUrl.trim())?.groupValues?.getOrNull(1)
                ?: error("Paste a Spotify playlist link, for example https://open.spotify.com/playlist/…")
            val html = fetch("https://open.spotify.com/embed/playlist/$id")
            val entity = parseEntity(html)
            val name = entity.optString("name").ifBlank { "Spotify playlist" }
            val tracks = entity.optJSONArray("trackList") ?: error("This playlist has no tracks.")
            val total = tracks.length().coerceAtMost(100)
            val matchedSongs = mutableListOf<Song>()
            for (i in 0 until total) {
                val track = tracks.optJSONObject(i) ?: continue
                val title = track.optString("title").takeIf { it.isNotBlank() } ?: continue
                val artist = track.optString("subtitle").takeIf { it.isNotBlank() }.orEmpty()
                val query = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
                val found = runCatching {
                    withTimeoutOrNull(12_000L) {
                        YtMusicRepository.search(query, SearchFilter.SONGS)
                    }
                }.getOrNull()?.getOrNull()
                val song = found.orEmpty()
                    .asSequence()
                    .mapNotNull { result ->
                        when (result) {
                            is SearchResult.TopTrack -> result.song
                            is SearchResult.Track -> result.song
                            else -> null
                        }
                    }
                    .firstOrNull { it.videoId.isNotBlank() }
                if (song != null) matchedSongs += song
                onProgress(i + 1, total)
            }
            if (matchedSongs.isEmpty()) {
                error("None of those tracks could be matched in the music catalogue.")
            }
            val playlist = withContext(Dispatchers.Main) {
                MusicLibrary.createPlaylist(name)
            }
            val added = withContext(Dispatchers.Main) {
                matchedSongs.count { MusicLibrary.addToPlaylist(playlist.id, it) }
            }
            if (added == 0) {
                withContext(Dispatchers.Main) { MusicLibrary.deletePlaylist(playlist.id) }
                error("The playlist was already imported.")
            }
            SpotifyImportResult(
                playlistName = playlist.name,
                imported = added,
                matched = matchedSongs.size,
                total = total
            )
        }
    }

    private fun fetch(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
            )
            .build()
        client.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "Spotify returned HTTP ${response.code}." }
            return response.body?.string().orEmpty()
        }
    }

    private fun parseEntity(html: String): JSONObject {
        val match = Regex(
            """<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        ).find(html) ?: error("Spotify didn't return playlist details.")
        val root = JSONObject(match.groupValues[1])
        return root.getJSONObject("props")
            .getJSONObject("pageProps")
            .getJSONObject("state")
            .getJSONObject("data")
            .getJSONObject("entity")
    }
}
