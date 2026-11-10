package com.nuvio.app.music

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * The small “what's playing” pill above the navigation bar. It stays out of the way of the
 * screens and opens Now Playing when tapped.
 */
@Composable
fun MusicNowPlayingPill(
    modifier: Modifier = Modifier,
    onOpen: () -> Unit
) {
    val playback by MusicPlayer.state.collectAsState()
    val song = playback.song ?: return
    val shape = RoundedCornerShape(50)
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var widthPx by remember { mutableIntStateOf(0) }

    Row(
        modifier
            .height(62.dp)
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            .onSizeChanged { widthPx = it.width }
            .graphicsLayer {
                val dragged = abs(offsetX.value) / widthPx.coerceAtLeast(1)
                alpha = 1f - dragged.coerceIn(0f, 0.45f)
            }
            .clip(shape)
            // Readable over bright content: the pill itself stays nearly opaque, with a soft
            // edge so it still reads as a floating overlay.
            .background(
                Brush.horizontalGradient(
                    listOf(
                        MusicPalette.surfaceRaised.copy(alpha = 0.98f),
                        MusicPalette.surface.copy(alpha = 0.94f),
                    )
                ),
                shape
            )
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
            .pointerInput(widthPx) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (widthPx > 0 && abs(offsetX.value) > widthPx * 0.32f) {
                            scope.launch {
                                offsetX.animateTo(
                                    if (offsetX.value < 0) -widthPx.toFloat() else widthPx.toFloat(),
                                    tween(180)
                                )
                                MusicPlayer.stop()
                            }
                        } else {
                            scope.launch { offsetX.animateTo(0f, spring()) }
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                    }
                )
            }
            .clickable(onClick = onOpen)
            .padding(start = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MusicArtwork(
            url = song.thumbnailUrl,
            modifier = Modifier.size(46.dp),
            cornerRadius = 23.dp,
            iconSize = 16.dp,
            artworkPx = 180
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = MusicPalette.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = MusicPalette.textMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable { MusicPlayer.toggle() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (playback.isPlaying) "Pause" else "Play",
                tint = MusicPalette.background,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}
