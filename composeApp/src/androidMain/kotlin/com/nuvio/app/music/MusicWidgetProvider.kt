package com.nuvio.app.music

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.nuvio.app.MainActivity
import com.nuvio.app.R
import com.nuvio.app.music.engine.data.model.Song
import com.nuvio.app.music.engine.data.model.artworkAt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_next
import nuvio.composeapp.generated.resources.action_play
import nuvio.composeapp.generated.resources.action_previous
import nuvio.composeapp.generated.resources.compose_action_pause
import nuvio.composeapp.generated.resources.music_ui_nothing_playing
import nuvio.composeapp.generated.resources.yt_tab_music
import org.jetbrains.compose.resources.getString

/** Home-screen controls for the Music section's shared player. */
class MusicWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        MusicWidgetUpdater.start(context)
        MusicWidgetUpdater.updateAll(context)
    }

    override fun onEnabled(context: Context) {
        MusicWidgetUpdater.start(context)
        MusicWidgetUpdater.updateAll(context)
    }

    override fun onDisabled(context: Context) {
        MusicWidgetUpdater.stop()
    }
}

/**
 * Keeps every placed music widget in step with [MusicPlayer]. The playback service owns the
 * process while music is playing, so a single collector there is enough; starting it also from
 * [com.nuvio.app.NuvioApplication] covers the case where the launcher refreshes the widget after
 * the process was recreated.
 */
internal object MusicWidgetUpdater {
    private data class Snapshot(
        val videoId: String?,
        val title: String,
        val artist: String,
        val isPlaying: Boolean
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null
    private var renderJob: Job? = null
    private var artworkVideoId: String? = null
    private var artwork: Bitmap? = null

    fun start(context: Context) {
        if (collectJob?.isActive == true) return
        val app = context.applicationContext
        updateAll(app)
        collectJob = scope.launch {
            MusicPlayer.state
                .map { state ->
                    val song = state.song
                    Snapshot(
                        videoId = song?.videoId,
                        title = song?.title.orEmpty(),
                        artist = song?.artist.orEmpty(),
                        isPlaying = state.isPlaying
                    )
                }
                .distinctUntilChanged()
                .collect { updateAll(app) }
        }
    }

    /** Starts the collector only when at least one widget is on a launcher. */
    fun startIfPlaced(context: Context) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        val ids = manager.getAppWidgetIds(ComponentName(app, MusicWidgetProvider::class.java))
        if (ids.isNotEmpty()) start(app)
    }

    fun stop() {
        collectJob?.cancel()
        collectJob = null
        renderJob?.cancel()
        renderJob = null
    }

    fun updateAll(context: Context) {
        val app = context.applicationContext
        renderJob?.cancel()
        renderJob = scope.launch {
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, MusicWidgetProvider::class.java))
            if (ids.isEmpty()) return@launch
            val state = MusicPlayer.state.value
            val restored = if (state.song == null) {
                withContext(Dispatchers.IO) { MusicQueueStore.restore(app) }
            } else {
                null
            }
            val song = state.song ?: restored?.songs?.getOrNull(restored.index)
            val isPlaying = state.song != null && state.isPlaying
            val views = RemoteViews(app.packageName, R.layout.music_widget)
            val musicLabel = getString(Res.string.yt_tab_music)
            views.setOnClickPendingIntent(R.id.music_widget_root, openApp(app))
            views.setContentDescription(R.id.music_widget_root, musicLabel)

            val previousLabel = getString(Res.string.action_previous)
            val nextLabel = getString(Res.string.action_next)
            val toggleLabel = if (isPlaying) {
                getString(Res.string.compose_action_pause)
            } else {
                getString(Res.string.action_play)
            }
            views.setContentDescription(R.id.music_widget_previous, previousLabel)
            views.setContentDescription(R.id.music_widget_next, nextLabel)
            views.setContentDescription(R.id.music_widget_toggle, toggleLabel)

            if (song == null) {
                views.setTextViewText(R.id.music_widget_title, musicLabel)
                views.setTextViewText(
                    R.id.music_widget_artist,
                    getString(Res.string.music_ui_nothing_playing)
                )
                views.setViewVisibility(R.id.music_widget_controls, View.GONE)
                views.setViewVisibility(R.id.music_widget_art_icon, View.VISIBLE)
                views.setImageViewBitmap(R.id.music_widget_art, null)
            } else {
                views.setTextViewText(R.id.music_widget_title, song.title)
                views.setTextViewText(R.id.music_widget_artist, song.artist)
                views.setViewVisibility(R.id.music_widget_controls, View.VISIBLE)
                views.setImageViewResource(
                    R.id.music_widget_toggle,
                    if (isPlaying) R.drawable.ic_player_pause else R.drawable.ic_player_play
                )
                views.setOnClickPendingIntent(
                    R.id.music_widget_previous,
                    serviceAction(app, MusicPlaybackService.ACTION_PREVIOUS, 1)
                )
                views.setOnClickPendingIntent(
                    R.id.music_widget_toggle,
                    serviceAction(app, MusicPlaybackService.ACTION_TOGGLE, 2)
                )
                views.setOnClickPendingIntent(
                    R.id.music_widget_next,
                    serviceAction(app, MusicPlaybackService.ACTION_NEXT, 3)
                )
                val art = artworkFor(app, song)
                if (art != null) {
                    views.setImageViewBitmap(R.id.music_widget_art, art)
                    views.setViewVisibility(R.id.music_widget_art_icon, View.GONE)
                } else {
                    views.setImageViewBitmap(R.id.music_widget_art, null)
                    views.setViewVisibility(R.id.music_widget_art_icon, View.VISIBLE)
                }
            }
            ids.forEach { manager.updateAppWidget(it, views) }
        }
    }

    private suspend fun artworkFor(context: Context, song: Song): Bitmap? {
        if (song.videoId == artworkVideoId && artwork != null) return artwork
        if (song.videoId != artworkVideoId) {
            artworkVideoId = song.videoId
            artwork = null
        }
        val url = song.thumbnailUrl?.artworkAt(240) ?: return null
        val result = runCatching {
            SingletonImageLoader.get(context).execute(
                ImageRequest.Builder(context)
                    .data(url)
                    .size(240)
                    .build()
            )
        }.getOrNull() as? SuccessResult ?: return null
        val image = runCatching { result.image.toBitmap() }.getOrNull() ?: return null
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (image.config == Bitmap.Config.HARDWARE) {
                runCatching { image.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull() ?: image
            } else {
                image
            }
        } else {
            image
        }
        return runCatching { circleCrop(bitmap, 180) }
            .getOrNull()
            ?.also { artwork = it }
    }

    private fun circleCrop(source: Bitmap, sizePx: Int): Bitmap {
        val side = minOf(source.width, source.height).coerceAtLeast(1)
        val square = Bitmap.createBitmap(
            source,
            (source.width - side) / 2,
            (source.height - side) / 2,
            side,
            side
        )
        val scaled = Bitmap.createScaledBitmap(square, sizePx, sizePx, true)
        val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        Canvas(output).drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)
        if (square !== source) square.recycle()
        if (scaled !== square) scaled.recycle()
        return output
    }

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun serviceAction(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MusicPlaybackService::class.java).setAction(action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(context, requestCode, intent, flags)
        } else {
            PendingIntent.getService(context, requestCode, intent, flags)
        }
    }
}
