package com.nuvio.app.features.music

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Music lives in Android-only code. The Android app sets [screen] at start-up; on other
 * platforms the tab is not shown.
 */
object MusicHost {
    var screen: (@Composable (Modifier) -> Unit)? = null

    /** A small now-playing pill shown above the app's navigation bar. */
    var nowPlayingPill: (@Composable (Modifier, onOpen: () -> Unit) -> Unit)? = null

    /** Incremented when that pill is tapped, so the Music screen opens Now Playing. */
    var openNowPlayingRequest by mutableIntStateOf(0)

    val available: Boolean get() = screen != null
}
