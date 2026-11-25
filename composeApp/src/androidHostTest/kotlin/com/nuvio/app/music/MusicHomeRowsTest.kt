package com.nuvio.app.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicHomeRowsTest {
    @Test
    fun pinningMovesLocalPlaylistsToTheTopOfPersonalRows() {
        val config = MusicHomeRowConfig(
            order = listOf(MusicHomeRow.RECENTS, MusicHomeRow.PLAYLISTS, MusicHomeRow.QUICK_PICKS),
            pinLibrary = true,
        )

        assertEquals(
            listOf(MusicHomeRow.PLAYLISTS, MusicHomeRow.RECENTS, MusicHomeRow.QUICK_PICKS),
            config.visibleRows(),
        )
    }

    @Test
    fun pinningMovesAccountPlaylistShelfAheadOfOtherShelves() {
        val playlists = MusicServiceHomeRow(
            id = "service:your-playlists",
            title = "Your playlists",
            containsUserPlaylists = true,
        )
        val recommended = MusicServiceHomeRow(
            id = "service:recommended",
            title = "Recommended",
        )
        val config = MusicHomeRowConfig(
            serviceShelves = listOf(recommended, playlists),
            serviceShelfOrder = listOf(recommended.id, playlists.id),
            pinLibrary = true,
        )

        assertEquals(listOf(playlists), config.pinnedServiceShelves())
        assertEquals(listOf(recommended), config.unpinnedServiceShelves())
    }

    @Test
    fun hiddenAccountPlaylistShelfIsNotPinnedOrRendered() {
        val playlists = MusicServiceHomeRow(
            id = "service:your-playlists",
            title = "Your playlists",
            containsUserPlaylists = true,
        )
        val config = MusicHomeRowConfig(
            serviceShelves = listOf(playlists),
            serviceShelfOrder = listOf(playlists.id),
            hiddenServiceShelves = setOf(playlists.id),
            pinLibrary = true,
        )

        assertTrue(config.pinnedServiceShelves().isEmpty())
        assertTrue(config.unpinnedServiceShelves().isEmpty())
    }

    @Test
    fun serviceShelfCanMoveAcrossPersonalRows() {
        val order = listOf("recents", "because", "service:quick-picks", "service:throwbacks")

        assertEquals(
            listOf("recents", "service:quick-picks", "because", "service:throwbacks"),
            moveMusicHomeRowId(order, order, "service:quick-picks", -1),
        )
    }
}
