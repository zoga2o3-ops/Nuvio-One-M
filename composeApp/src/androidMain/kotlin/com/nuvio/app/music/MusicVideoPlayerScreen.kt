package com.nuvio.app.music

import android.graphics.Color as AndroidColor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import com.nuvio.app.music.engine.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The full-screen YouTube music video for the current song. Music playback is paused before this
 * opens; the video brings its own audio. Leaving the screen releases the video player and returns
 * to Now Playing with the song exactly where it was.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun MusicVideoPlayerScreen(
    video: Song,
    startPositionMs: Long,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(startPositionMs.coerceAtLeast(0L)) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var ended by remember { mutableStateOf(false) }
    val playFocus = remember { FocusRequester() }

    BackHandler(onBack = onClose)

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            player?.release()
            player = null
        }
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player?.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(video.videoId) {
        loading = true
        error = null
        val stream = withContext(Dispatchers.IO) {
            runCatching { MusicStreamResolver.resolve(video.videoId) }.getOrNull()
        }
        if (stream == null) {
            error = "This music video couldn't be loaded."
            loading = false
            return@LaunchedEffect
        }
        val selector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSize(1920, 1080)
                    .setMaxVideoBitrate(8_000_000)
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
            )
        }
        val httpFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(stream.headers)
            .setUserAgent(
                "Mozilla/5.0 (Linux; Android 13; Android TV) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
            )
        val exo = ExoPlayer.Builder(context)
            .setTrackSelector(selector)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    true
                )
                setHandleAudioBecomingNoisy(true)
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        ended = state == Player.STATE_ENDED
                        if (state == Player.STATE_READY) {
                            durationMs = duration.coerceAtLeast(0L)
                            loading = false
                        }
                    }

                    override fun onPlayerError(e: PlaybackException) {
                        error = e.message ?: "This music video couldn't be played."
                        loading = false
                    }
                })
            }
        player = exo
        exo.setMediaItem(MediaItem.fromUri(stream.url))
        exo.prepare()
        if (startPositionMs > 0L) exo.seekTo(startPositionMs)
        exo.playWhenReady = true
    }

    LaunchedEffect(player) {
        while (true) {
            player?.let {
                positionMs = it.currentPosition.coerceAtLeast(0L)
                durationMs = it.duration.coerceAtLeast(0L)
                isPlaying = it.isPlaying
            }
            delay(250L)
        }
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { playFocus.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        player?.let { exo ->
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(AndroidColor.TRANSPARENT)
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        this.player = exo
                    }
                },
                update = { it.player = exo },
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.78f), Color.Transparent)
                    )
                )
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(210.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.86f))
                    )
                )
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            VideoCircleButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                label = "Back to Now Playing",
                onClick = onClose
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = "MUSIC VIDEO",
                    color = MusicPalette.accentTeal,
                    fontSize = 10.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = video.title,
                    color = Color.White,
                    fontFamily = MusicDisplay,
                    fontSize = 28.sp,
                    lineHeight = 31.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = video.artist,
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (loading) {
            CircularProgressIndicator(
                color = MusicPalette.accent,
                strokeWidth = 3.dp,
                modifier = Modifier.align(Alignment.Center).size(42.dp)
            )
        }

        error?.let { message ->
            Column(
                Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 36.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Video unavailable",
                    color = Color.White,
                    fontFamily = MusicDisplay,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(18.dp))
                LibraryActionButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    label = "Back to song",
                    onClick = onClose
                )
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 56.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val duration = durationMs.coerceAtLeast(1L)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatMusicTime(positionMs),
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 11.sp
                )
                Text(
                    text = formatMusicTime(durationMs),
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 11.sp
                )
            }
            Spacer(Modifier.height(5.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.18f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth((positionMs.toFloat() / duration).coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(MusicPalette.accent)
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                VideoCircleButton(
                    icon = Icons.Default.Replay10,
                    label = "Back 10 seconds",
                    onClick = {
                        player?.let {
                            it.seekTo((it.currentPosition - 10_000L).coerceAtLeast(0L))
                        }
                    }
                )
                VideoCircleButton(
                    icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    label = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.focusRequester(playFocus),
                    onClick = {
                        player?.let {
                            when {
                                it.playbackState == Player.STATE_ENDED -> {
                                    it.seekTo(0L)
                                    it.play()
                                }
                                it.isPlaying -> it.pause()
                                else -> it.play()
                            }
                        }
                    }
                )
                VideoCircleButton(
                    icon = Icons.Default.Forward10,
                    label = "Forward 10 seconds",
                    onClick = {
                        player?.let {
                            val max = it.duration.takeIf { d -> d > 0L } ?: Long.MAX_VALUE
                            it.seekTo((it.currentPosition + 10_000L).coerceAtMost(max))
                        }
                    }
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Music stays paused · Back returns to the song",
                color = Color.White.copy(alpha = 0.34f),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun VideoCircleButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(
                if (focused) Color.White else Color.White.copy(alpha = 0.14f)
            )
            .border(
                2.dp,
                if (focused) Color.White else Color.White.copy(alpha = 0.22f),
                CircleShape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (focused) MusicPalette.background else Color.White,
            modifier = Modifier.size(23.dp)
        )
    }
}
