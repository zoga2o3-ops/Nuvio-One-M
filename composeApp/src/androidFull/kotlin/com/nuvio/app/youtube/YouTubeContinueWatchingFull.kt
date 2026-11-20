package com.nuvio.app.youtube

import android.content.Context
import android.widget.Toast
import com.nuvio.app.features.trailer.InAppYouTubeExtractor
import com.nuvio.app.features.youtube.YouTubeHost
import com.nuvio.app.features.youtube.YouTubePlayRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.youtube_continue_unavailable

/**
 * Full build only: the YouTube Continue Watching row needs the in-app YouTube extractor.
 * Play Store builds don't include it and simply don't install the row.
 */
object YouTubeContinueWatchingFull {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val extractor by lazy { InAppYouTubeExtractor() }

    @Volatile
    private var opening = false

    fun install(context: Context) {
        val app = context.applicationContext
        YouTubeHost.continueWatchingRow = { YouTubeContinueWatchingRow() }
        YouTubeHost.openContinuation = open@{ item ->
            if (opening) return@open
            opening = true
            scope.launch {
                val url = runCatching {
                    extractor.extractSingleUrl("https://www.youtube.com/watch?v=${item.videoId}")
                }.getOrNull()?.takeIf { it.isNotBlank() }
                withContext(Dispatchers.Main.immediate) {
                    opening = false
                    if (url == null) {
                        val message = runCatching {
                            org.jetbrains.compose.resources.getString(Res.string.youtube_continue_unavailable)
                        }.getOrDefault("This video can't be resumed right now.")
                        Toast.makeText(app, message, Toast.LENGTH_SHORT).show()
                    } else {
                        YouTubeHost.openVideo?.invoke(
                            YouTubePlayRequest(
                                url = url,
                                title = item.title,
                                poster = item.poster,
                                videoId = item.videoId,
                                startPositionMs = item.positionMs,
                            ),
                        )
                    }
                }
            }
        }
    }
}
