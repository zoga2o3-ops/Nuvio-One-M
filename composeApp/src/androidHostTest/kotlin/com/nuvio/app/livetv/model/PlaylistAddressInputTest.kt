package com.nuvio.app.livetv.model

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaylistAddressInputTest {

    @Test
    fun httpsSwitchGivesABareAddressASecureScheme() {
        for (address in listOf(
            "example.test",
            "example.test:8443",
            "example.test/live/playlist.m3u8?token=A%2FB",
            "[2001:db8::1]:8443/list.m3u",
        )) {
            assertEquals("https://$address", PlaylistSource.completePlaylistAddress(address, useHttps = true))
        }
    }

    @Test
    fun httpsSwitchTurnsASchemeRelativeAddressIntoSecure() {
        assertEquals(
            "https://example.test/list.m3u",
            PlaylistSource.completePlaylistAddress("//example.test/list.m3u", useHttps = true),
        )
    }

    @Test
    fun typedSchemeStillWinsOverTheSwitch() {
        for (address in listOf(
            "http://example.test/list.m3u",
            "https://example.test/list.m3u",
            "HTTPS://EXAMPLE.test:8443/Keep/Case",
        )) {
            assertEquals(address, PlaylistSource.completePlaylistAddress(address, useHttps = true))
            assertEquals(address, PlaylistSource.completePlaylistAddress(address, useHttps = false))
        }
    }

    @Test
    fun switchCanTakeOverAnAddressThatAlreadyHasAScheme() {
        assertEquals(
            "example.test:8443/Keep/Case",
            PlaylistSource.stripPlaylistAddressScheme("https://example.test:8443/Keep/Case"),
        )
        assertEquals(
            "example.test/list.m3u",
            PlaylistSource.stripPlaylistAddressScheme("  HTTP://example.test/list.m3u"),
        )
        assertEquals(
            "example.test/list.m3u?next=https://nested.test",
            PlaylistSource.stripPlaylistAddressScheme("example.test/list.m3u?next=https://nested.test"),
        )
    }

    @Test
    fun xtreamServerKeepsHttpsAndDropsThePath() {
        assertEquals(
            "https://example.test:8443",
            PlaylistSource.cleanXtreamServer(
                "https://example.test:8443/get.php?username=user&password=pass&type=m3u_plus",
            ),
        )
        assertEquals(
            "https://example.test",
            PlaylistSource.cleanXtreamServer("HTTPS://example.test/player_api.php?username=user&password=pass"),
        )
        assertEquals("http://example.test:8080", PlaylistSource.cleanXtreamServer("example.test:8080"))
    }
}
