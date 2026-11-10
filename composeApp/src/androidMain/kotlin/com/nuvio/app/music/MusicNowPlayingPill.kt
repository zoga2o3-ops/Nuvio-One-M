package com.nuvio.app.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

    Row(
        modifier
            .height(62.dp)
            .clip(shape)
            .background(MusicPalette.surface.copy(alpha = 0.97f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
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
