package com.nuvio.app.youtube

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import com.nuvio.app.features.youtube.YouTubeContinueWatchingItem

/** The videos shown in the YouTube Continue Watching row on Home. */
object YouTubeContinueWatchingRepository {
    private val _items = MutableStateFlow<List<YouTubeContinueWatchingItem>>(emptyList())
    val items: StateFlow<List<YouTubeContinueWatchingItem>> = _items.asStateFlow()

    fun refresh(context: Context) {
        _items.value = if (YouTubeSettings.continueWatchingOnHome(context)) {
            YouTubeProgress.localEntries(context).map {
                YouTubeContinueWatchingItem(
                    videoId = it.videoId,
                    title = it.title,
                    poster = it.poster,
                    positionMs = it.positionMs,
                    durationMs = it.durationMs,
                )
            }
        } else {
            emptyList()
        }
    }
}
