package com.nuvio.app.music

import com.nuvio.app.music.engine.data.model.Song
import com.nuvio.app.music.MusicFilters.isUsable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicFiltersTest {
    private fun song(
        title: String,
        artist: String = "Artist",
        duration: String? = "3:30",
        videoId: String = "v1"
    ) = Song(
        videoId = videoId,
        title = title,
        artist = artist,
        thumbnailUrl = null,
        durationText = duration
    )

    @Test
    fun variantsAndTalkAreFilteredOut() {
        assertTrue(MusicFilters.isVariant("Song (Karaoke Version)"))
        assertTrue(MusicFilters.isVariant("Song slowed + reverb"))
        assertTrue(MusicFilters.isVariant("Song cover"))
        assertFalse(MusicFilters.isVariant("Song"))
        assertFalse(MusicFilters.isVariant("Discovery"))
        assertTrue(MusicFilters.looksLikeTalk("Song interview"))
        assertTrue(MusicFilters.looksLikeTalk("Album review"))
        assertFalse(MusicFilters.looksLikeTalk("Song"))
        assertFalse(MusicFilters.looksLikeTalk("Preview"))
    }

    @Test
    fun matchingIgnoresDecorationsAndNormalizesArtists() {
        assertTrue(MusicFilters.sameSong(song("Get Lucky"), song("Get Lucky (Official Video)")))
        assertFalse(MusicFilters.sameSong(song("Get Lucky"), song("Lose Yourself")))
        assertEquals("daft punk", MusicFilters.normalizeArtistName("Daft Punk - Topic"))
        assertEquals("моцарт", MusicFilters.normalizeArtistName("Моцарт"))
        assertEquals("Daft Punk", MusicFilters.cleanArtistName("Daft Punk - Topic"))
        assertEquals(listOf("A", "B", "C", "D"), MusicFilters.splitArtistNames("A, B & C feat. D"))
    }

    @Test
    fun unusableDurationsAndNonYoutubeSourcesAreExcluded() {
        assertTrue(song("Song").isUsable())
        assertTrue(song("Song", duration = null).isUsable())
        assertFalse(song("Song", duration = "0:30").isUsable())
        assertFalse(song("Song", duration = "20:00").isUsable())
        assertFalse(song("Song", videoId = "addon:source:id").isUsable())
        assertFalse(song("Song", videoId = "local:1").isUsable())
    }

    @Test
    fun cleanTitleRemovesUploadDecorationAndArtistPrefix() {
        assertEquals(
            "Get Lucky",
            MusicFilters.cleanSongTitle("Daft Punk - Get Lucky (Official Video)", "Daft Punk")
        )
        assertEquals("Song", MusicFilters.cleanSongTitle("Song [Lyrics]", null))
    }
}
