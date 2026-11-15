package com.nuvio.app.music

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import com.nuvio.app.music.engine.data.model.Song
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MusicLocalScreen(
    onPlay: (List<Song>, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tracks by LocalMusicRepository.tracks.collectAsState()
    val scanning by LocalMusicRepository.scanning.collectAsState()
    val error by LocalMusicRepository.error.collectAsState()
    val playback by MusicPlayer.state.collectAsState()
    val share by MusicShareServerManager.state.collectAsState()
    val drive by MusicDriveUploader.state.collectAsState()
    LaunchedEffect(Unit) {
        MusicDriveUploader.init(context)
    }
    var permissionRevision by remember { mutableIntStateOf(0) }
    val hasPermission = remember(permissionRevision) {
        LocalMusicRepository.hasPermission(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionRevision++
        if (granted) LocalMusicRepository.scan(context)
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission && tracks.isEmpty() && !scanning) {
            LocalMusicRepository.scan(context)
        }
    }

    val scanFocus = remember { FocusRequester() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 22.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "local-header") {
            val compact = LocalConfiguration.current.screenWidthDp < 600
            val titleBlock: @Composable (Modifier) -> Unit = { blockModifier ->
                Column(blockModifier) {
                    Text(
                        text = stringResource(Res.string.music_ui_local_music),
                        color = MusicPalette.text,
                        fontFamily = MusicDisplay,
                        fontSize = if (compact) 30.sp else 40.sp,
                        lineHeight = if (compact) 32.sp else 43.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = stringResource(Res.string.music_ui_music_on_this_device_usb_drives_and_your_network_share),
                        color = MusicPalette.textSecondary,
                        fontSize = if (compact) 13.sp else 14.sp
                    )
                }
            }
            val actionBlock: @Composable () -> Unit = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LocalActionButton(
                        icon = Icons.Default.Refresh,
                        label = if (scanning) stringResource(Res.string.music_ui_scanning) else stringResource(Res.string.music_ui_scan),
                        enabled = !scanning,
                        modifier = Modifier.focusRequester(scanFocus),
                        onClick = {
                            if (hasPermission) {
                                LocalMusicRepository.scan(context)
                            } else {
                                permissionLauncher.launch(LocalMusicRepository.requiredPermission())
                            }
                        }
                    )
                    LocalActionButton(
                        icon = if (share.running) Icons.Default.Close else Icons.Default.Share,
                        label = if (share.running) stringResource(Res.string.live_tv_ui_stop) else stringResource(Res.string.music_ui_share),
                        enabled = share.running || (hasPermission && tracks.isNotEmpty()),
                        onClick = {
                            if (share.running) {
                                MusicShareServerManager.stop()
                            } else if (hasPermission) {
                                MusicShareServerManager.start(context)
                            } else {
                                permissionLauncher.launch(LocalMusicRepository.requiredPermission())
                            }
                        }
                    )
                    LocalActionButton(
                        icon = Icons.Default.CloudUpload,
                        label = when {
                            drive.busy -> stringResource(Res.string.live_tv_ui_working)
                            drive.signIn != null -> stringResource(Res.string.action_cancel)
                            drive.connected -> "Upload"
                            else -> "Drive"
                        },
                        enabled = !drive.busy,
                        onClick = {
                            when {
                                drive.signIn != null -> MusicDriveUploader.cancelSignIn()
                                drive.connected -> {
                                    if (tracks.isNotEmpty()) MusicDriveUploader.uploadAll(context)
                                }
                                else -> MusicDriveUploader.connect(context)
                            }
                        }
                    )
                }
            }
            if (compact) {
                Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 14.dp)) {
                    titleBlock(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        actionBlock()
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 26.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    titleBlock(Modifier.weight(1f))
                    actionBlock()
                }
            }
        }

        if (share.running || share.error != null) {
            item(key = "local-share") {
                LocalSharePanel(
                    share = share,
                    trackCount = tracks.size,
                    hasPermission = hasPermission,
                    onStart = {
                        if (hasPermission) {
                            MusicShareServerManager.start(context)
                        } else {
                            permissionLauncher.launch(LocalMusicRepository.requiredPermission())
                        }
                    },
                    onStop = { MusicShareServerManager.stop() }
                )
            }
        }

        if (drive.connected || drive.signIn != null || drive.busy || drive.message != null) {
            item(key = "local-drive") {
                LocalDrivePanel(
                    state = drive,
                    trackCount = tracks.size,
                    onConnect = { MusicDriveUploader.connect(context) },
                    onCancel = { MusicDriveUploader.cancelSignIn() },
                    onUpload = { MusicDriveUploader.uploadAll(context) },
                    onDisconnect = { MusicDriveUploader.disconnect() }
                )
            }
        }

        if (!hasPermission) {
            item(key = "local-permission") {
                LocalNotice(
                    icon = Icons.Default.Usb,
                    title = stringResource(Res.string.music_ui_allow_storage_access),
                    message = stringResource(Res.string.music_ui_nuvio_needs_permission_to_read_music_files_on_this_device_an),
                    actionLabel = stringResource(Res.string.music_ui_allow_access),
                    onClick = {
                        permissionLauncher.launch(LocalMusicRepository.requiredPermission())
                    },
                    modifier = Modifier.padding(horizontal = 26.dp)
                )
            }
        } else if (scanning) {
            item(key = "local-scanning") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(
                        color = MusicPalette.accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(Res.string.music_ui_scanning_the_device_and_connected_drives),
                        color = MusicPalette.textSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else if (error != null) {
            item(key = "local-error") {
                MusicErrorState(
                    message = error.orEmpty(),
                    onRetry = { LocalMusicRepository.scan(context) },
                    modifier = Modifier.fillMaxWidth().height(280.dp)
                )
            }
        } else if (tracks.isEmpty()) {
            item(key = "local-empty") {
                LocalNotice(
                    icon = Icons.Default.Usb,
                    title = stringResource(Res.string.music_ui_no_music_found),
                    message = stringResource(Res.string.music_ui_plug_in_a_usb_drive_with_audio_files_then_press_scan),
                    actionLabel = stringResource(Res.string.music_ui_scan_again),
                    onClick = { LocalMusicRepository.scan(context) },
                    modifier = Modifier.padding(horizontal = 26.dp)
                )
            }
        } else {
            item(key = "local-count") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 26.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(Res.string.music_ui_on_this_device),
                        color = MusicPalette.text,
                        fontFamily = MusicDisplay,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${tracks.size} track" + if (tracks.size == 1) "" else "s",
                        color = MusicPalette.textMuted,
                        fontSize = 11.5.sp
                    )
                }
            }
            itemsIndexed(
                items = tracks,
                key = { index, song -> "${song.videoId}#$index" }
            ) { index, song ->
                MusicTrackRow(
                    song = song,
                    index = index,
                    isCurrent = playback.song?.videoId == song.videoId,
                    isPlaying = playback.isPlaying && playback.song?.videoId == song.videoId,
                    onClick = { onPlay(tracks, index) },
                    modifier = Modifier.padding(horizontal = 26.dp)
                )
            }
        }
    }
}

@Composable
private fun LocalSharePanel(
    share: MusicShareState,
    trackCount: Int,
    hasPermission: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 26.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MusicPalette.surface)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.music_ui_transfer_to_a_computer),
                    color = MusicPalette.text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(Res.string.music_ui_open_the_address_in_a_browser_on_the_same_wi_fi_or_ethernet_),
                    color = MusicPalette.textSecondary,
                    fontSize = 13.sp
                )
            }
            if (share.running) {
                LocalActionButton(
                    icon = Icons.Default.Close,
                    label = stringResource(Res.string.music_ui_stop_sharing),
                    onClick = onStop
                )
            } else {
                LocalActionButton(
                    icon = Icons.Default.Share,
                    label = stringResource(Res.string.music_ui_share_over_network),
                    enabled = hasPermission && trackCount > 0,
                    onClick = onStart
                )
            }
        }

        if (share.running) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.music_ui_on_your_computer_open),
                        color = MusicPalette.textSecondary,
                        fontSize = 13.sp
                    )
                    Text(
                        text = share.url ?: stringResource(Res.string.music_ui_finding_this_device_s_network_address),
                        color = MusicPalette.accent,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(Res.string.music_ui_the_page_lists_every_local_track_with_individual_download_li),
                        color = MusicPalette.textMuted,
                        fontSize = 12.sp
                    )
                }
            }
            share.error?.let { error ->
                Text(
                    text = error,
                    color = MusicPalette.error,
                    fontSize = 13.sp
                )
            }
        } else if (!hasPermission) {
            Text(
                text = stringResource(Res.string.music_ui_allow_storage_access_above_to_share_local_music),
                color = MusicPalette.textMuted,
                fontSize = 12.sp
            )
        } else if (trackCount == 0) {
            Text(
                text = stringResource(Res.string.music_ui_scan_the_device_and_any_connected_usb_drive_first),
                color = MusicPalette.textMuted,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun LocalDrivePanel(
    state: MusicDriveState,
    trackCount: Int,
    onConnect: () -> Unit,
    onCancel: () -> Unit,
    onUpload: () -> Unit,
    onDisconnect: () -> Unit
) {
    val uri = LocalUriHandler.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 26.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MusicPalette.surface)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.music_ui_copy_to_google_drive),
                    color = MusicPalette.text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(Res.string.music_ui_uploads_local_tracks_to_a_nuvio_music_folder_in_your_own_dri),
                    color = MusicPalette.textSecondary,
                    fontSize = 13.sp
                )
            }
            when {
                state.busy -> {
                    CircularProgressIndicator(
                        color = MusicPalette.accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                }
                state.connected -> {
                    LocalActionButton(
                        icon = Icons.Default.CloudUpload,
                        label = stringResource(Res.string.music_ui_upload_local_music),
                        enabled = trackCount > 0,
                        onClick = onUpload
                    )
                }
                else -> {
                    LocalActionButton(
                        icon = Icons.Default.CloudUpload,
                        label = stringResource(Res.string.live_tv_ui_connect_google_drive),
                        onClick = onConnect
                    )
                }
            }
        }

        state.signIn?.let { prompt ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.music_ui_open_this_address_to_sign_in),
                        color = MusicPalette.textSecondary,
                        fontSize = 13.sp
                    )
                    Text(
                        text = prompt.url,
                        color = MusicPalette.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            runCatching { uri.openUri(prompt.qrUrl) }
                        }
                    )
                    Text(
                        text = prompt.userCode,
                        color = MusicPalette.accent,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(Res.string.compose_auth_link_waiting),
                        color = MusicPalette.textMuted,
                        fontSize = 12.sp
                    )
                }
                LocalActionButton(
                    icon = Icons.Default.Close,
                    label = stringResource(Res.string.action_cancel),
                    onClick = onCancel
                )
            }
        }

        if (state.connected) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.email?.let { "Connected as $it" } ?: stringResource(Res.string.music_ui_google_drive_connected),
                        color = MusicPalette.textSecondary,
                        fontSize = 13.sp
                    )
                    if (state.busy) {
                        Text(
                            text = "Uploading ${state.uploaded} of ${state.total}…",
                            color = MusicPalette.text,
                            fontSize = 13.sp
                        )
                    }
                    state.folderUrl?.let { url ->
                        Text(
                            text = "Drive folder: $url",
                            color = MusicPalette.textMuted,
                            fontSize = 11.sp
                        )
                    }
                }
                LocalActionButton(
                    icon = Icons.Default.Logout,
                    label = stringResource(Res.string.live_tv_disconnect),
                    onClick = onDisconnect
                )
            }
        }

        state.message?.let { message ->
            Text(
                text = message,
                color = if (state.connected) MusicPalette.textMuted else MusicPalette.textSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun LocalActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(
                when {
                    focused && enabled -> Color.White
                    else -> MusicPalette.surfaceRaised
                }
            )
            .border(
                2.dp,
                if (focused && enabled) Color.White else MusicPalette.border,
                RoundedCornerShape(50)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (focused && enabled) MusicPalette.background else MusicPalette.text,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            color = if (focused && enabled) MusicPalette.background else MusicPalette.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun LocalNotice(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    actionLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MusicPalette.surface)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MusicPalette.textSecondary,
                modifier = Modifier.size(26.dp)
            )
            Text(
                text = title,
                color = MusicPalette.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = message,
            color = MusicPalette.textSecondary,
            fontSize = 14.sp
        )
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (focused) Color.White else MusicPalette.accent)
                .border(
                    2.dp,
                    if (focused) Color.White else Color.Transparent,
                    RoundedCornerShape(50)
                )
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 9.dp)
        ) {
            Text(
                text = actionLabel,
                color = if (focused) MusicPalette.background else Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
