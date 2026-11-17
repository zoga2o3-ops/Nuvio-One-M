package com.nuvio.app.music

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * The Music library inside the Live TV setup sync, so Liked Songs and playlists match on every
 * device. Two settings, each a list of objects with an "id", so sync combines them item by item
 * like playlists and guides (LiveTvDriveSync.SOURCE_LISTS):
 *  - "music_liked": one entry per liked song, id = the video id;
 *  - "music_playlists": one entry per playlist, id = the playlist id, songs inside.
 *
 * A song is kept small: id, title, artist, length and album; no thumbnail (rebuilt from the
 * video id). Songs that are local files stay on their own device, and Recently Played stays
 * per device.
 */
object MusicLibrarySync {
    const val KEY_LIKED = "music_liked"
    const val KEY_PLAYLISTS = "music_playlists"
    val KEYS = setOf(KEY_LIKED, KEY_PLAYLISTS)

    fun export(context: Context): Map<String, JSONObject> {
        MusicLibrary.init(context)
        val liked = JSONArray()
        MusicLibrary.liked.value.filter { it.syncable() }.forEach { liked.put(encodeSong(it)) }
        val playlists = JSONArray()
        MusicLibrary.playlists.value.forEach { p ->
            val songs = JSONArray()
            p.songs.filter { it.syncable() }.forEach { songs.put(encodeSong(it)) }
            playlists.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("createdAt", p.createdAt)
                    .put("songs", songs)
            )
        }
        return mapOf(KEY_LIKED to entry(liked), KEY_PLAYLISTS to entry(playlists))
    }

    /** Whether a synced setup has any music in it. */
    fun hasMusic(data: JSONObject?): Boolean =
        data != null && KEYS.any { k ->
            runCatching {
                JSONArray(data.optJSONObject(k)?.optString("v").orEmpty()).length() > 0
            }.getOrDefault(false)
        }

    /** Writes the synced library here, keeping this device's local-file songs. True if anything changed. */
    fun import(context: Context, data: JSONObject): Boolean {
        MusicLibrary.init(context)
        var changed = false
        data.optJSONObject(KEY_LIKED)?.optString("v")?.let { raw ->
            val synced = runCatching { readArray(raw).mapNotNull(::decodeSong) }.getOrNull() ?: return@let
            val ids = synced.mapTo(HashSet()) { it.videoId }
            val localOnly = MusicLibrary.liked.value.filter { !it.syncable() && it.videoId !in ids }
            if (MusicLibrary.replaceLiked(synced + localOnly)) changed = true
        }
        data.optJSONObject(KEY_PLAYLISTS)?.optString("v")?.let { raw ->
            val current = MusicLibrary.playlists.value.associateBy { it.id }
            val synced = runCatching {
                readArray(raw).mapNotNull { o ->
                    val id = o.optString("id").ifBlank { return@mapNotNull null }
                    val songs = o.optJSONArray("songs")?.let { arr ->
                        (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::decodeSong) }
                    }.orEmpty()
                    val ids = songs.mapTo(HashSet()) { it.videoId }
                    val localOnly = current[id]?.songs.orEmpty().filter { !it.syncable() && it.videoId !in ids }
                    StoredPlaylist(
                        id = id,
                        name = o.optString("name").ifBlank { "Playlist" },
                        songs = songs + localOnly,
                        createdAt = o.optLong("createdAt")
                    )
                }
            }.getOrNull() ?: return@let
            if (MusicLibrary.replacePlaylists(synced)) changed = true
        }
        return changed
    }

    private fun StoredSong.syncable() = videoId.isNotBlank() && localUri.isNullOrBlank() && localPath.isNullOrBlank()

    private fun entry(list: JSONArray) = JSONObject().put("t", "str").put("v", list.toString())

    private fun readArray(raw: String): List<JSONObject> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    }

    private fun encodeSong(s: StoredSong): JSONObject = JSONObject().apply {
        put("id", s.videoId)
        put("t", s.title)
        put("a", s.artist)
        durationSeconds(s.durationText)?.let { put("d", it) }
        s.albumName?.takeIf { it.isNotBlank() }?.let { put("al", it) }
        s.albumId?.takeIf { it.isNotBlank() }?.let { put("alid", it) }
        s.artistId?.takeIf { it.isNotBlank() }?.let { put("arid", it) }
        if (s.isVideo) put("v", true)
    }

    private fun decodeSong(o: JSONObject): StoredSong? {
        val id = o.optString("id").ifBlank { return null }
        return StoredSong(
            videoId = id,
            title = o.optString("t").ifBlank { id },
            artist = o.optString("a"),
            thumbnailUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
            durationText = o.optInt("d", 0).takeIf { it > 0 }?.let { "%d:%02d".format(it / 60, it % 60) },
            artistId = o.optString("arid").ifBlank { null },
            albumId = o.optString("alid").ifBlank { null },
            albumName = o.optString("al").ifBlank { null },
            isVideo = o.optBoolean("v", false)
        )
    }

    /** "3:45" or "1:02:03" to seconds. */
    private fun durationSeconds(text: String?): Int? {
        val parts = text?.trim()?.split(':')?.map { it.toIntOrNull() ?: return null } ?: return null
        if (parts.isEmpty() || parts.size > 3) return null
        return parts.fold(0) { acc, n -> acc * 60 + n }.takeIf { it > 0 }
    }
}
