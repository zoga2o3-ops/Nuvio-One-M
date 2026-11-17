package com.nuvio.app.music

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.music.engine.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/** One track as it is kept in the local Music library. */
@Serializable
data class StoredSong(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String? = null,
    val durationText: String? = null,
    val artistId: String? = null,
    val albumId: String? = null,
    val albumName: String? = null,
    val isVideo: Boolean = false,
    val localUri: String? = null,
    val localPath: String? = null
) {
    fun toSong(): Song = Song(
        videoId = videoId,
        title = title,
        artist = artist,
        thumbnailUrl = thumbnailUrl,
        durationText = durationText,
        artistId = artistId,
        albumId = albumId,
        albumName = albumName,
        isVideo = isVideo,
        localUri = localUri,
        localPath = localPath
    )
}

@Serializable
data class StoredPlaylist(
    val id: String,
    val name: String,
    val songs: List<StoredSong> = emptyList(),
    val createdAt: Long = 0L
)

/**
 * The Music section's local library: liked songs, playlists and recently played.
 *
 * Kept on the device rather than on a YouTube Music account for this wave, so it works signed
 * out and does not touch the YouTube account. Account sync can be added over this later.
 */
object MusicLibrary {
    private const val PREFS_NAME = "nuvio_music_library"
    private const val KEY_LIKED = "liked_songs"
    private const val KEY_RECENTS = "recent_songs"
    private const val KEY_PLAYLISTS = "playlists"
    /** Liked songs and playlists removed here since the last sync (they stay removed on other devices). */
    private const val KEY_SYNC_REMOVED = "sync_removed"
    private const val MAX_RECENTS = 60

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var prefs: SharedPreferences? = null

    private val _liked = MutableStateFlow<List<StoredSong>>(emptyList())
    val liked: StateFlow<List<StoredSong>> = _liked.asStateFlow()

    private val _recents = MutableStateFlow<List<StoredSong>>(emptyList())
    val recents: StateFlow<List<StoredSong>> = _recents.asStateFlow()

    private val _playlists = MutableStateFlow<List<StoredPlaylist>>(emptyList())
    val playlists: StateFlow<List<StoredPlaylist>> = _playlists.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val store = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = store
        _liked.value = decode(store.getString(KEY_LIKED, null), emptyList<StoredSong>())
        _recents.value = decode(store.getString(KEY_RECENTS, null), emptyList<StoredSong>())
        _playlists.value = decode(store.getString(KEY_PLAYLISTS, null), emptyList<StoredPlaylist>())
    }

    fun isLiked(videoId: String): Boolean = _liked.value.any { it.videoId == videoId }

    /** Returns true when the song is liked after the toggle. */
    @Synchronized
    fun toggleLike(song: Song): Boolean {
        if (song.videoId.isBlank()) return false
        val current = _liked.value
        val existing = current.firstOrNull { it.videoId == song.videoId }
        val next = if (existing != null) {
            noteRemoved(setOf(song.videoId))
            current.filterNot { it.videoId == song.videoId }
        } else {
            listOf(song.toStored()) + current
        }
        _liked.value = next
        persist(KEY_LIKED, next)
        return existing == null
    }

    /** Adds songs to Liked Songs without toggling (library sync). Returns how many were new. */
    @Synchronized
    fun addLikedSongs(songs: List<Song>): Int {
        if (songs.isEmpty()) return 0
        val current = _liked.value
        val existing = current.mapTo(HashSet()) { it.videoId }
        val additions = songs
            .filter { it.videoId.isNotBlank() && existing.add(it.videoId) }
            .map { it.toStored() }
        if (additions.isEmpty()) return 0
        val next = current + additions
        _liked.value = next
        persist(KEY_LIKED, next)
        return additions.size
    }

    /** Moves [song] to the front of Recently Played, keeping one entry per video. */
    fun recordPlayed(song: Song) {
        if (song.videoId.isBlank()) return
        val next = (listOf(song.toStored()) + _recents.value.filterNot { it.videoId == song.videoId })
            .take(MAX_RECENTS)
        _recents.value = next
        persist(KEY_RECENTS, next)
    }

    fun createPlaylist(name: String? = null): StoredPlaylist {
        val title = name?.trim()?.takeIf { it.isNotBlank() }
            ?: "My Playlist ${_playlists.value.size + 1}"
        val playlist = StoredPlaylist(
            id = UUID.randomUUID().toString(),
            name = title,
            createdAt = System.currentTimeMillis()
        )
        _playlists.value = listOf(playlist) + _playlists.value
        persist(KEY_PLAYLISTS, _playlists.value)
        return playlist
    }

    fun addToPlaylist(playlistId: String, song: Song): Boolean {
        val current = _playlists.value
        val playlist = current.firstOrNull { it.id == playlistId } ?: return false
        if (playlist.songs.any { it.videoId == song.videoId }) return false
        val updated = playlist.copy(songs = playlist.songs + song.toStored())
        _playlists.value = current.map { if (it.id == playlistId) updated else it }
        persist(KEY_PLAYLISTS, _playlists.value)
        return true
    }

    /** Creates or replaces a local playlist by name; used by server imports. */
    fun importPlaylist(name: String, songs: List<Song>): StoredPlaylist {
        val title = name.trim().takeIf { it.isNotBlank() } ?: "Imported playlist"
        val existing = _playlists.value.firstOrNull { it.name.equals(title, ignoreCase = true) }
        val id = existing?.id ?: UUID.randomUUID().toString()
        val playlist = StoredPlaylist(
            id = id,
            name = title,
            songs = songs.distinctBy { it.videoId }.map { it.toStored() },
            createdAt = existing?.createdAt ?: System.currentTimeMillis()
        )
        _playlists.value = listOf(playlist) + _playlists.value.filterNot { it.id == id }
        persist(KEY_PLAYLISTS, _playlists.value)
        return playlist
    }

    fun removeFromPlaylist(playlistId: String, videoId: String) {
        val current = _playlists.value
        val playlist = current.firstOrNull { it.id == playlistId } ?: return
        val updated = playlist.copy(songs = playlist.songs.filterNot { it.videoId == videoId })
        _playlists.value = current.map { if (it.id == playlistId) updated else it }
        persist(KEY_PLAYLISTS, _playlists.value)
    }

    @Synchronized
    fun deletePlaylist(playlistId: String) {
        noteRemoved(setOf(playlistId))
        _playlists.value = _playlists.value.filterNot { it.id == playlistId }
        persist(KEY_PLAYLISTS, _playlists.value)
    }

    fun renamePlaylist(playlistId: String, name: String) {
        val title = name.trim().takeIf { it.isNotBlank() } ?: return
        _playlists.value = _playlists.value.map {
            if (it.id == playlistId) it.copy(name = title) else it
        }
        persist(KEY_PLAYLISTS, _playlists.value)
    }

    // ---------- Sync (MusicLibrarySync) ----------

    /** Replaces Liked Songs with a synced list, keeping art and lengths already known here. */
    @Synchronized
    internal fun replaceLiked(songs: List<StoredSong>): Boolean {
        val next = withKnownDetails(songs)
        if (next == _liked.value) return false
        _liked.value = next
        persist(KEY_LIKED, next)
        return true
    }

    /** Replaces the playlists with synced ones, keeping art and lengths already known here. */
    @Synchronized
    internal fun replacePlaylists(playlists: List<StoredPlaylist>): Boolean {
        val next = playlists.map { it.copy(songs = withKnownDetails(it.songs)) }
        if (next == _playlists.value) return false
        _playlists.value = next
        persist(KEY_PLAYLISTS, next)
        return true
    }

    private fun withKnownDetails(songs: List<StoredSong>): List<StoredSong> {
        val known = HashMap<String, StoredSong>()
        (_liked.value + _playlists.value.flatMap { it.songs } + _recents.value).forEach {
            known.putIfAbsent(it.videoId, it)
        }
        return songs.map { s ->
            val k = known[s.videoId] ?: return@map s
            s.copy(
                thumbnailUrl = k.thumbnailUrl ?: s.thumbnailUrl,
                durationText = k.durationText ?: s.durationText,
                albumName = k.albumName ?: s.albumName,
                albumId = k.albumId ?: s.albumId,
                artistId = k.artistId ?: s.artistId
            )
        }
    }

    fun syncRemovedIds(): Set<String> =
        prefs?.getStringSet(KEY_SYNC_REMOVED, null)?.toSet().orEmpty()

    fun forgetSyncRemoved(ids: Set<String>) {
        val store = prefs ?: return
        val current = syncRemovedIds()
        if (ids.none { it in current }) return
        store.edit().putStringSet(KEY_SYNC_REMOVED, current - ids).apply()
    }

    private fun noteRemoved(ids: Set<String>) {
        val store = prefs ?: return
        store.edit().putStringSet(KEY_SYNC_REMOVED, syncRemovedIds() + ids).apply()
    }

    private fun Song.toStored(): StoredSong = StoredSong(
        videoId = videoId,
        title = title,
        artist = artist,
        thumbnailUrl = thumbnailUrl,
        durationText = durationText,
        artistId = artistId,
        albumId = albumId,
        albumName = albumName,
        isVideo = isVideo,
        localUri = localUri,
        localPath = localPath
    )

    private inline fun <reified T> persist(key: String, value: T) {
        prefs?.edit()?.putString(key, json.encodeToString(value))?.apply()
    }

    private inline fun <reified T> decode(raw: String?, fallback: T): T {
        if (raw.isNullOrBlank()) return fallback
        return runCatching { json.decodeFromString<T>(raw) }.getOrDefault(fallback)
    }
}
