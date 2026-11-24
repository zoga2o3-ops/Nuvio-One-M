package com.nuvio.app.music

import com.nuvio.app.music.engine.data.YtMusicRepository
import com.nuvio.app.music.engine.data.model.BrowseType
import com.nuvio.app.music.engine.data.model.SearchFilter
import com.nuvio.app.music.engine.data.model.SearchResult
import com.nuvio.app.music.engine.data.model.Song
import com.nuvio.app.music.engine.data.sources.TrackMatcher
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GeminiAlbum(
    val id: String,
    val name: String,
    val artist: String,
    val artworkUrl: String?,
    val tracks: List<Song>,
)

data class GeminiMusicPicks(
    val tracks: List<Song> = emptyList(),
    val albums: List<GeminiAlbum> = emptyList(),
    val unmatched: Int = 0,
)

/**
 * Turns Gemini's title suggestions into real YouTube Music rows. Gemini never chooses a stream:
 * each suggestion is verified against YouTube Music, then the normal add-on-first player resolves
 * the audio when someone presses Play.
 */
internal object MusicGeminiRepository {
    private const val MAX_TRACKS = 10
    private const val MAX_ALBUMS = 3

    suspend fun generate(
        apiKey: String,
        settings: GeminiSettings,
    ): Result<GeminiMusicPicks> = withContext(Dispatchers.IO) {
        runCatching {
            val taste = tasteSummary(settings)
            if (taste.isBlank()) {
                throw IllegalStateException(
                    "Listen to or like a few tracks first, so Gemini has something to work from."
                )
            }
            val prompt = buildPrompt(taste)
            val suggestions = MusicGeminiClient.generate(apiKey, settings.model, prompt).getOrThrow()
            verify(suggestions)
        }
    }

    private fun buildPrompt(taste: String): String = """
        You are curating music for one listener. Suggest real, released music only.
        Never invent an album or track. Prefer a mix of familiar artists and adjacent discoveries.
        Do not include URLs.

        Listener taste:
        $taste

        Return JSON only, exactly in this shape:
        {"items":[
          {"type":"track","title":"...","artist":"...","year":2020,"reason":"short reason"},
          {"type":"album","title":"...","artist":"...","year":2019,"reason":"short reason"}
        ]}

        Give up to $MAX_TRACKS tracks and $MAX_ALBUMS albums. Keep each reason under 12 words.
    """.trimIndent()

    private fun tasteSummary(settings: GeminiSettings): String {
        val artists = LinkedHashSet<String>()
        val tracks = LinkedHashSet<String>()
        val albums = LinkedHashSet<String>()

        val recents = MusicLibrary.recents.value
        val liked = MusicLibrary.liked.value
        val saved = MusicLibrary.playlists.value.flatMap { it.songs }

        if (settings.shareTopArtists) {
            val counts = HashMap<String, Int>()
            (recents + liked + saved).forEach { song ->
                if (song.artist.isNotBlank()) counts[song.artist] = (counts[song.artist] ?: 0) + 1
            }
            counts.entries.sortedByDescending { it.value }.take(30).forEach { artists += it.key }
        }
        if (settings.shareLiked) {
            liked.take(80).forEach { song ->
                if (song.artist.isNotBlank()) artists += song.artist
                if (song.albumName?.isNotBlank() == true) albums += "${song.artist} — ${song.albumName}"
            }
        }
        if (settings.shareRecents) {
            recents.take(60).forEach { song ->
                if (song.artist.isNotBlank()) artists += song.artist
                if (song.title.isNotBlank() && song.artist.isNotBlank()) {
                    tracks += "${song.title} — ${song.artist}"
                }
            }
        }
        if (settings.sharePlaylists) {
            saved.take(120).forEach { savedSong ->
                val song = savedSong.toSong()
                if (song.artist.isNotBlank()) artists += song.artist
                if (song.albumName?.isNotBlank() == true) albums += "${song.artist} — ${song.albumName}"
            }
        }

        return buildString {
            if (artists.isNotEmpty()) appendLine("Artists: " + artists.take(30).joinToString("; "))
            if (tracks.isNotEmpty()) appendLine("Recent tracks: " + tracks.take(25).joinToString("; "))
            if (albums.isNotEmpty()) appendLine("Liked albums: " + albums.take(20).joinToString("; "))
        }.trim()
    }

    private suspend fun verify(suggestions: List<GeminiSuggestion>): GeminiMusicPicks {
        val tracks = ArrayList<Song>()
        val albums = ArrayList<GeminiAlbum>()
        val seenTracks = HashSet<String>()
        val seenAlbums = HashSet<String>()
        var unmatched = 0

        for (suggestion in suggestions) {
            if (suggestion.type == "album" && albums.size < MAX_ALBUMS) {
                val album = findAlbum(suggestion)
                if (album == null) {
                    unmatched++
                } else if (seenAlbums.add(album.id)) {
                    albums += album
                }
            } else if (suggestion.type == "track" && tracks.size < MAX_TRACKS) {
                val track = findTrack(suggestion)
                if (track == null) {
                    unmatched++
                } else if (seenTracks.add(track.videoId)) {
                    tracks += track
                }
            }
        }
        return GeminiMusicPicks(tracks = tracks, albums = albums, unmatched = unmatched)
    }

    private suspend fun findTrack(suggestion: GeminiSuggestion): Song? {
        val page = YtMusicRepository.search(
            "${suggestion.title} ${suggestion.artist}",
            SearchFilter.SONGS,
        ).getOrNull().orEmpty()
        val songs = page.mapNotNull {
            when (it) {
                is SearchResult.TopTrack -> it.song
                is SearchResult.Track -> it.song
                is SearchResult.Browse -> null
            }
        }.filterNot { it.isVideo }
        if (songs.isEmpty()) return null
        val target = Song(
            videoId = "",
            title = suggestion.title,
            artist = suggestion.artist,
            thumbnailUrl = null,
        )
        return TrackMatcher.best(songs, TrackMatcher.targetOf(target))
            ?: songs.firstOrNull {
                normalize(it.title) == normalize(suggestion.title) &&
                    (normalize(it.artist).contains(normalize(suggestion.artist)) ||
                        normalize(suggestion.artist).contains(normalize(it.artist)))
            }
    }

    private suspend fun findAlbum(suggestion: GeminiSuggestion): GeminiAlbum? {
        val page = YtMusicRepository.search(
            "${suggestion.title} ${suggestion.artist}",
            SearchFilter.ALBUMS,
        ).getOrNull().orEmpty()
        val album = page.asSequence()
            .filterIsInstance<SearchResult.Browse>()
            .map { it.item }
            .firstOrNull {
                it.type == BrowseType.ALBUM &&
                    normalize(it.title) == normalize(suggestion.title) &&
                    normalize(it.subtitle).contains(normalize(suggestion.artist))
            }
            ?: page.asSequence()
                .filterIsInstance<SearchResult.Browse>()
                .map { it.item }
                .firstOrNull { it.type == BrowseType.ALBUM }
            ?: return null
        val songs = YtMusicRepository.browseSongs(album.browseId).getOrNull()?.songs.orEmpty()
        if (songs.isEmpty()) return null
        return GeminiAlbum(
            id = "gemini:album:${album.browseId}",
            name = album.title,
            artist = suggestion.artist,
            artworkUrl = album.thumbnailUrl,
            tracks = songs.take(24),
        )
    }

    private fun normalize(value: String): String =
        value.trim().lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
}
