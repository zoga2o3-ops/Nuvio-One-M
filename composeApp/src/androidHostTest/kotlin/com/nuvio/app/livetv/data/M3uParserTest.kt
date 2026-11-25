package com.nuvio.app.livetv.data

import java.io.BufferedReader
import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals

class M3uParserTest {
    @Test
    fun filtersEntriesBeforeReturningThePlaylist() {
        val text = """
            #EXTM3U
            #EXTINF:-1,Live News
            https://streams.example/live.m3u8|User-Agent=player
            #EXTINF:-1,Movie
            https://streams.example/movie.mp4|User-Agent=player
        """.trimIndent()
        val checked = mutableListOf<String>()

        val playlist = M3uParser.parse(BufferedReader(StringReader(text))) { url ->
            checked += url
            !url.endsWith(".mp4")
        }

        assertEquals(listOf("https://streams.example/live.m3u8", "https://streams.example/movie.mp4"), checked)
        assertEquals(listOf("Live News"), playlist.entries.map { it.name })
    }
}
