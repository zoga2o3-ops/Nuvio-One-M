package com.nuvio.app.features.music

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Music lives in Android-only code. The Android app sets [screen] at start-up; on other
 * platforms the tab is not shown.
 */
object MusicHost {
    var screen: (@Composable (Modifier) -> Unit)? = null
    val available: Boolean get() = screen != null
}
