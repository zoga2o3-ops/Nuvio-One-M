package com.nuvio.app.music

import android.content.Context
import com.nuvio.app.music.engine.data.model.Song
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Last verified Gemini rows for one profile, so opening Music does not spend API quota. */
internal object MusicGeminiCache {
    fun load(context: Context, profileId: Int): GeminiMusicPicks? = runCatching {
        val file = file(context, profileId)
        if (!file.exists()) return@runCatching null
        val root = JSONObject(file.readText())
        val tracks = root.optJSONArray("tracks").toSongs()
        val albums = buildList {
            val array = root.optJSONArray("albums") ?: JSONArray()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val songs = item.optJSONArray("tracks").toSongs()
                if (songs.isEmpty()) continue
                add(
                    GeminiAlbum(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        artist = item.optString("artist"),
                        artworkUrl = item.optString("artworkUrl").takeIf { it.isNotBlank() },
                        tracks = songs,
                    )
                )
            }
        }
        GeminiMusicPicks(tracks = tracks, albums = albums)
    }.getOrNull()

    fun save(context: Context, profileId: Int, picks: GeminiMusicPicks) = runCatching {
        val root = JSONObject()
            .put("tracks", picks.tracks.toJson())
            .put(
                "albums",
                JSONArray().apply {
                    picks.albums.forEach { album ->
                        put(
                            JSONObject()
                                .put("id", album.id)
                                .put("name", album.name)
                                .put("artist", album.artist)
                                .put("artworkUrl", album.artworkUrl)
                                .put("tracks", album.tracks.toJson())
                        )
                    }
                }
            )
        file(context, profileId).writeText(root.toString())
    }

    fun clear(context: Context, profileId: Int) {
        runCatching { file(context, profileId).delete() }
    }

    private fun file(context: Context, profileId: Int): File =
        File(context.filesDir, "music_gemini_$profileId.json")

    private fun List<Song>.toJson(): JSONArray = JSONArray().apply {
        forEach { song ->
            put(
                JSONObject()
                    .put("videoId", song.videoId)
                    .put("title", song.title)
                    .put("artist", song.artist)
                    .put("thumbnailUrl", song.thumbnailUrl)
                    .put("durationText", song.durationText)
                    .put("albumName", song.albumName)
            )
        }
    }

    private fun JSONArray?.toSongs(): List<Song> {
        val array = this ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val videoId = item.optString("videoId").takeIf { it.isNotBlank() } ?: continue
                add(
                    Song(
                        videoId = videoId,
                        title = item.optString("title"),
                        artist = item.optString("artist"),
                        thumbnailUrl = item.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                        durationText = item.optString("durationText").takeIf { it.isNotBlank() },
                        albumName = item.optString("albumName").takeIf { it.isNotBlank() },
                    )
                )
            }
        }
    }
}
