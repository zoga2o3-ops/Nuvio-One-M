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
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_next
import nuvio.composeapp.generated.resources.action_play
import nuvio.composeapp.generated.resources.action_previous
import nuvio.composeapp.generated.resources.compose_action_pause
import org.jetbrains.compose.resources.stringResource

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
                color = MusicPalette.textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            PillTransportButton(
                icon = Icons.Default.SkipPrevious,
                label = stringResource(Res.string.action_previous),
                onClick = { MusicPlayer.previous() }
            )
            PillTransportButton(
                icon = if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                label = if (playback.isPlaying) {
                    stringResource(Res.string.compose_action_pause)
                } else {
                    stringResource(Res.string.action_play)
                },
                primary = true,
                onClick = { MusicPlayer.toggle() }
            )
            PillTransportButton(
                icon = Icons.Default.SkipNext,
                label = stringResource(Res.string.action_next),
                onClick = { MusicPlayer.next() }
            )
        }
    }
}

@Composable
private fun PillTransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    primary: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(if (primary) 38.dp else 34.dp)
            .clip(CircleShape)
            .background(if (primary) Color.White else Color.White.copy(alpha = 0.10f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (primary) MusicPalette.background else MusicPalette.text,
            modifier = Modifier.size(if (primary) 19.dp else 18.dp)
        )
    }
}
