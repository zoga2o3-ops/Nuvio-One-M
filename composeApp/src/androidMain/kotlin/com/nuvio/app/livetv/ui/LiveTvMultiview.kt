@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nuvio.app.livetv.ui

import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import com.nuvio.app.livetv.model.LiveChannel
import com.nuvio.app.livetv.player.StalkerPlayback
import kotlinx.coroutines.delay
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.action_retry
import nuvio.composeapp.generated.resources.compose_nav_search
import nuvio.composeapp.generated.resources.live_tv_ui_channel_wont_play
import nuvio.composeapp.generated.resources.live_tv_ui_multiview
import nuvio.composeapp.generated.resources.live_tv_ui_multiview_pick
import nuvio.composeapp.generated.resources.live_tv_ui_multiview_pick_three
import nuvio.composeapp.generated.resources.live_tv_ui_multiview_sound
import org.jetbrains.compose.resources.stringResource

/** A phone shows three screens at once; a tablet has room for four. */
private const val MAX_TILES_PHONE = 3
private const val MAX_TILES_TABLET = 4
private const val TABLET_MIN_WIDTH_DP = 768
private const val TABLET_MIN_HEIGHT_DP = 600

/** Which channels go in the grid: up to three on a phone, four on a tablet. */
@Composable
internal fun LiveTvMultiviewPicker(
    channels: List<LiveChannel>,
    onStart: (List<LiveChannel>) -> Unit,
    onDismiss: () -> Unit,
) {
    val playable = remember(channels) { channels.filter { it.url.isNotBlank() && it.drm == null } }
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<List<LiveChannel>>(emptyList()) }
    val shown = remember(playable, query) {
        val needle = query.trim()
        if (needle.isBlank()) playable
        else playable.filter { it.name.contains(needle, true) || it.group.contains(needle, true) }
    }
    BackHandler(onBack = onDismiss)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val tablet = maxWidth >= TABLET_MIN_WIDTH_DP.dp && maxHeight >= TABLET_MIN_HEIGHT_DP.dp
            val maxTiles = if (tablet) MAX_TILES_TABLET else MAX_TILES_PHONE
            Column(
                Modifier.fillMaxSize().background(Color(0xFF121216)).padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GridView, contentDescription = null, tint = Color(0xFFE53935))
                    Text(
                        stringResource(Res.string.live_tv_ui_multiview),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).padding(start = 10.dp),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.action_cancel))
                    }
                }
                Text(
                    stringResource(
                        if (maxTiles == MAX_TILES_PHONE) Res.string.live_tv_ui_multiview_pick_three
                        else Res.string.live_tv_ui_multiview_pick
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(Res.string.compose_nav_search)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(shown, key = { it.key }) { channel ->
                        val selected = picked.any { it.key == channel.key }
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) Color(0xFF2A2A31) else Color.Transparent)
                                .clickable {
                                    picked = when {
                                        selected -> picked.filterNot { it.key == channel.key }
                                        picked.size < maxTiles -> picked + channel
                                        else -> picked
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = selected,
                                onCheckedChange = {
                                    picked = when {
                                        selected -> picked.filterNot { it.key == channel.key }
                                        picked.size < maxTiles -> picked + channel
                                        else -> picked
                                    }
                                },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    channel.group,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LocalContentColor.current.copy(alpha = 0.55f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
                    TextButton(
                        onClick = { if (picked.isNotEmpty()) onStart(picked) },
                        enabled = picked.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            stringResource(Res.string.live_tv_ui_multiview),
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The screens at once: a phone stacks three top to bottom when held upright and puts them in a
 * row when held sideways; a tablet keeps the two-by-two grid.
 */
@Composable
internal fun LiveTvMultiviewScreen(
    channels: List<LiveChannel>,
    backupFor: (LiveChannel) -> LiveChannel? = { null },
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var panes by remember(channels) { mutableStateOf(channels) }
    var soundIndex by remember(channels) { mutableStateOf(0) }
    var failedKeys by remember(channels) { mutableStateOf<Set<String>>(emptySet()) }
    var failedIndex by remember(channels) { mutableStateOf<Int?>(null) }
    var playerNonce by remember(channels) { mutableStateOf(0) }
    var labelsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(labelsVisible) {
        if (labelsVisible) {
            delay(5_000)
            labelsVisible = false
        }
    }
    // A pane that can't play moves to another copy of the same channel when one exists. If it
    // still can't play, the tile keeps a Retry button instead of staying black.
    LaunchedEffect(failedIndex, panes) {
        val index = failedIndex ?: return@LaunchedEffect
        val channel = panes.getOrNull(index) ?: return@LaunchedEffect
        val backup = backupFor(channel)?.takeIf { candidate -> panes.none { it.key == candidate.key } }
        if (backup != null) {
            panes = panes.toMutableList().also { it[index] = backup }
        } else {
            failedKeys = failedKeys + channel.key
        }
        failedIndex = null
    }
    val players = remember(panes, playerNonce) {
        panes.mapIndexed { index, channel ->
            runCatching {
                val http = DefaultHttpDataSource.Factory()
                    .setDefaultRequestProperties(channel.headers)
                val dataSource = StalkerPlayback.wrap(DefaultDataSource.Factory(context, http))
                val loadControl = DefaultLoadControl.Builder()
                    .setBufferDurationsMs(15_000, 45_000, 1_500, 3_000)
                    .build()
                ExoPlayer.Builder(context)
                    .setLoadControl(loadControl)
                    .setAudioAttributes(AudioAttributes.DEFAULT, false)
                    .setHandleAudioBecomingNoisy(false)
                    .build()
                    .apply {
                        setMediaSource(
                            DefaultMediaSourceFactory(dataSource, DefaultExtractorsFactory())
                                .createMediaSource(MediaItem.fromUri(channel.url))
                        )
                        addListener(object : Player.Listener {
                            override fun onPlayerError(error: PlaybackException) {
                                failedIndex = index
                            }
                        })
                        playWhenReady = true
                        volume = 0f
                        prepare()
                    }
            }.getOrNull()
        }
    }
    DisposableEffect(players) {
        onDispose {
            players.forEach { player ->
                runCatching {
                    player?.stop()
                    player?.release()
                }
            }
        }
    }
    LaunchedEffect(players, soundIndex) {
        players.forEachIndexed { index, player -> runCatching { player?.volume = if (index == soundIndex) 1f else 0f } }
    }
    BackHandler(onBack = onClose)

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
            val tablet = maxWidth >= TABLET_MIN_WIDTH_DP.dp && maxHeight >= TABLET_MIN_HEIGHT_DP.dp
            val landscape = maxWidth > maxHeight
            val limit = if (tablet) MAX_TILES_TABLET else MAX_TILES_PHONE
            val tiles = panes.take(limit)
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.GridView, contentDescription = null, tint = Color(0xFFE53935))
                    Text(
                        stringResource(Res.string.live_tv_ui_multiview),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                    Text(
                        stringResource(Res.string.live_tv_ui_multiview_sound),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.weight(2f),
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.action_cancel), tint = Color.White)
                    }
                }
                when {
                    tablet -> {
                        val rowCount = if (tiles.size <= 2) 1 else 2
                        val columnCount = if (tiles.size == 1) 1 else 2
                        Column(Modifier.weight(1f).fillMaxWidth()) {
                            repeat(rowCount) { row ->
                                Row(Modifier.weight(1f).fillMaxWidth()) {
                                    repeat(columnCount) { column ->
                                        val index = row * columnCount + column
                                        val channel = tiles.getOrNull(index)
                                        Box(Modifier.weight(1f).fillMaxHeight()) {
                                            if (channel != null) {
                                                MultiviewTile(
                                                    channel = channel,
                                                    player = players.getOrNull(index),
                                                    sound = index == soundIndex,
                                                    failed = channel.key in failedKeys,
                                                    showLabel = labelsVisible,
                                                    onSelect = { soundIndex = index; labelsVisible = true },
                                                    onRetry = {
                                                        failedKeys = failedKeys - channel.key
                                                        playerNonce++
                                                    },
                                                )
                                            } else {
                                                Box(Modifier.fillMaxSize().padding(2.dp).background(Color(0xFF0A0A0C)))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    landscape -> Row(Modifier.weight(1f).fillMaxWidth()) {
                        tiles.forEachIndexed { index, channel ->
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                MultiviewTile(
                                    channel = channel,
                                    player = players.getOrNull(index),
                                    sound = index == soundIndex,
                                    failed = channel.key in failedKeys,
                                    showLabel = labelsVisible,
                                    onSelect = { soundIndex = index; labelsVisible = true },
                                    onRetry = {
                                        failedKeys = failedKeys - channel.key
                                        playerNonce++
                                    },
                                )
                            }
                        }
                    }
                    else -> Column(Modifier.weight(1f).fillMaxWidth()) {
                        tiles.forEachIndexed { index, channel ->
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                MultiviewTile(
                                    channel = channel,
                                    player = players.getOrNull(index),
                                    sound = index == soundIndex,
                                    failed = channel.key in failedKeys,
                                    showLabel = labelsVisible,
                                    onSelect = { soundIndex = index; labelsVisible = true },
                                    onRetry = {
                                        failedKeys = failedKeys - channel.key
                                        playerNonce++
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MultiviewTile(
    channel: LiveChannel,
    player: ExoPlayer?,
    sound: Boolean,
    failed: Boolean,
    showLabel: Boolean,
    onSelect: () -> Unit,
    onRetry: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF08080A))
            .then(if (sound) Modifier.border(2.dp, Color(0xFFE53935), RoundedCornerShape(6.dp)) else Modifier)
            .clickable(onClick = onSelect),
    ) {
        if (player != null) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).also { view ->
                        player.setVideoTextureView(view)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (failed) {
            Column(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(Res.string.live_tv_ui_channel_wont_play, channel.name),
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                TextButton(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
            }
        }
        Text(
            channel.name,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .alpha(if (showLabel) 1f else 0f)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                    )
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}
