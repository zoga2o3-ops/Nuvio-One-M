package com.nuvio.app.music

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.nuvio.app.music.engine.data.model.Song
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.getString

/**
 * The TV half of Party Mode: hosts the phone page, shows the QR code, and gives the
 * host the final say over additions and the queue.
 */
@Composable
internal fun MusicPartyScreen(onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val party by MusicPartyManager.state.collectAsState()
    val playback by MusicPlayer.state.collectAsState()
    val closeFocus = remember { FocusRequester() }
    val startFocus = remember { FocusRequester() }
    val controlsFocus = remember { FocusRequester() }
    val windowInfo = LocalWindowInfo.current

    BackHandler(onBack = onClose)

    // The overlay's dialog window takes focus after its content composes; wait for that
    // rather than guessing a delay, then hand the remote to the main action.
    LaunchedEffect(party.running, windowInfo) {
        snapshotFlow { windowInfo.isWindowFocused }.collect { focused ->
            if (focused) {
                withFrameNanos { }
                runCatching {
                    if (party.running) controlsFocus.requestFocus()
                    else startFocus.requestFocus()
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MusicPalette.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val compact = LocalConfiguration.current.screenWidthDp < 600
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = if (compact) 24.dp else 40.dp, vertical = 20.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PartyCloseButton(
                    modifier = Modifier.focusRequester(closeFocus),
                    onClick = onClose
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.music_tab_party),
                        color = MusicPalette.text,
                        fontFamily = MusicDisplay,
                        fontSize = 34.sp,
                        lineHeight = 37.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(Res.string.music_ui_everyone_on_this_wi_fi_can_add_songs),
                        color = MusicPalette.textMuted,
                        fontSize = 13.sp
                    )
                }
                if (party.running) {
                    PartyLivePill(locked = party.locked)
                }
            }

            Spacer(Modifier.height(18.dp))

            if (!party.running) {
                PartyStartContent(
                    error = party.error,
                    startFocus = startFocus,
                    compact = compact,
                    onStart = { MusicPartyManager.start(context) }
                )
            } else {
                PartyActiveContent(
                    party = party,
                    playback = playback,
                    controlsFocus = controlsFocus,
                    compact = compact,
                    onLock = { MusicPartyManager.setLocked(!party.locked) },
                    onToggleApproval = {
                        MusicPartyManager.setRequireApproval(!party.requireApproval)
                    },
                    onApprove = { MusicPartyManager.approvePending(it) },
                    onReject = { MusicPartyManager.rejectPending(it) },
                    onSkip = { MusicPlayer.next() },
                    onClearUpNext = {
                        MusicPlayer.clearUpcoming()
                        MusicPartyManager.clearAdditions()
                    },
                    onEnd = { MusicPartyManager.stop() }
                )
            }
        }
    }
}

@Composable
private fun PartyStartContent(
    error: String?,
    startFocus: FocusRequester,
    compact: Boolean,
    onStart: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 700.dp)
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(MusicPalette.accentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Groups,
                    contentDescription = null,
                    tint = MusicPalette.accent,
                    modifier = Modifier.size(42.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(Res.string.music_ui_start_a_listening_party),
                color = MusicPalette.text,
                fontFamily = MusicDisplay,
                fontSize = if (compact) 28.sp else 36.sp,
                lineHeight = if (compact) 30.sp else 39.sp,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.music_ui_guests_scan_one_qr_code_search_the_music_catalogue_on_their_) +
                    stringResource(Res.string.music_ui_and_add_songs_to_this_device_s_queue_no_app_or_account_neede),
                color = MusicPalette.textSecondary,
                fontSize = if (compact) 13.sp else 14.sp,
                lineHeight = if (compact) 18.sp else 20.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(if (compact) 16.dp else 24.dp))
            if (compact) {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PartyStep("1", stringResource(Res.string.music_ui_scan), "Point a phone camera at the screen.", fillWidth = true)
                    PartyStep("2", stringResource(Res.string.collections_editor_tmdb_search), "Find a song, artist or album.", fillWidth = true)
                    PartyStep("3", stringResource(Res.string.youtube_settings_add), "It lands in the shared queue.", fillWidth = true)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PartyStep("1", stringResource(Res.string.music_ui_scan), "Point a phone camera at the screen.")
                    PartyStep("2", stringResource(Res.string.collections_editor_tmdb_search), "Find a song, artist or album.")
                    PartyStep("3", stringResource(Res.string.youtube_settings_add), "It lands in the shared queue.")
                }
            }
            Spacer(Modifier.height(28.dp))
            PartyPillButton(
                icon = Icons.Default.Groups,
                label = stringResource(Res.string.music_ui_start_party),
                primary = true,
                modifier = Modifier.focusRequester(startFocus),
                onClick = onStart
            )
            error?.let {
                Spacer(Modifier.height(16.dp))
                PartyNotice(icon = Icons.Default.WifiOff, text = it, error = true)
            }
        }
    }
}

@Composable
private fun PartyActiveContent(
    party: MusicPartyState,
    playback: MusicPlayer.State,
    controlsFocus: FocusRequester,
    compact: Boolean,
    onLock: () -> Unit,
    onToggleApproval: () -> Unit,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onSkip: () -> Unit,
    onClearUpNext: () -> Unit,
    onEnd: () -> Unit
) {
    val now = playback.queue.getOrNull(playback.index) ?: playback.song
    var showQr by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        if (compact) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PartyPillButton(
                    icon = Icons.Default.FactCheck,
                    label = if (party.requireApproval) stringResource(Res.string.music_ui_approval_on) else stringResource(Res.string.music_ui_approval_off),
                    modifier = Modifier.weight(1f),
                    onClick = onToggleApproval
                )
                PartyPillButton(
                    icon = Icons.Default.QrCode2,
                    label = stringResource(Res.string.music_ui_qr),
                    modifier = Modifier.weight(1f),
                    onClick = { showQr = true }
                )
                PartyPillButton(
                    icon = Icons.Default.StopCircle,
                    label = stringResource(Res.string.music_ui_end_party),
                    destructive = true,
                    modifier = Modifier.weight(1f),
                    onClick = onEnd
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(26.dp)
        ) {
            if (!compact) {
                Column(Modifier.width(360.dp)) {
            PartyQrCard(url = party.url)
            Spacer(Modifier.height(8.dp))
            PartyStatusCard(
                locked = party.locked,
                additions = party.additions,
                urlMissing = party.url == null
            )
            Spacer(Modifier.height(8.dp))
            PartyPillButton(
                icon = Icons.Default.FactCheck,
                label = if (party.requireApproval) stringResource(Res.string.music_ui_approval_on) else stringResource(Res.string.music_ui_approval_off),
                modifier = Modifier.fillMaxWidth(),
                onClick = onToggleApproval
            )
            Spacer(Modifier.height(7.dp))
            PartyPillButton(
                icon = Icons.Default.StopCircle,
                label = stringResource(Res.string.music_ui_end_party),
                destructive = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = onEnd
            )
                }
            }

            Column(Modifier.weight(1f).fillMaxHeight()) {
            PartyNowPlaying(
                song = now,
                isPlaying = playback.isPlaying,
                positionMs = playback.positionMs,
                durationMs = playback.durationMs,
                addedBy = now?.videoId?.let { MusicPartyManager.addedBy(it) }
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                PartyPillButton(
                    icon = if (party.locked) Icons.Default.LockOpen else Icons.Default.Lock,
                    label = if (party.locked) stringResource(Res.string.live_tv_ui_unlock) else stringResource(Res.string.live_tv_ui_lock),
                    modifier = Modifier.weight(1f).focusRequester(controlsFocus),
                    onClick = onLock
                )
                PartyPillButton(
                    icon = Icons.Default.SkipNext,
                    label = stringResource(Res.string.player_skip),
                    modifier = Modifier.weight(1f),
                    onClick = onSkip
                )
                PartyPillButton(
                    icon = Icons.Default.DeleteSweep,
                    label = stringResource(Res.string.music_ui_clear_up_next),
                    modifier = Modifier.weight(1f),
                    onClick = onClearUpNext
                )
            }
            Spacer(Modifier.height(14.dp))
            if (party.pending.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(Res.string.music_ui_requests),
                        color = MusicPalette.text,
                        fontFamily = MusicDisplay,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = party.pending.size.toString(),
                        color = MusicPalette.accentTeal,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = stringResource(Res.string.music_ui_approve_or_decline),
                        color = MusicPalette.textMuted,
                        fontSize = 10.sp
                    )
                }
                Spacer(Modifier.height(6.dp))
                party.pending.take(3).forEach { request ->
                    PartyPendingRow(
                        request = request,
                        onApprove = { onApprove(request.id) },
                        onReject = { onReject(request.id) }
                    )
                    Spacer(Modifier.height(5.dp))
                }
                if (party.pending.size > 3) {
                    Text(
                        text = "+${party.pending.size - 3} more waiting",
                        color = MusicPalette.textMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                }
            }
            val upNext = if (playback.queue.isEmpty() || playback.index < 0) {
                emptyList()
            } else {
                playback.queue.drop(playback.index + 1)
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.settings_playback_page_up_next),
                    color = MusicPalette.text,
                    fontFamily = MusicDisplay,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = upNext.size.toString(),
                    color = MusicPalette.textMuted,
                    fontSize = 12.sp
                )
                Spacer(Modifier.weight(1f))
                if (upNext.isNotEmpty()) {
                    Text(
                        text = stringResource(Res.string.music_ui_select_to_play_trash_to_remove),
                        color = MusicPalette.textMuted,
                        fontSize = 10.sp
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            if (upNext.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MusicPalette.textMuted,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (now == null) {
                                stringResource(Res.string.music_ui_waiting_for_the_first_song)
                            } else {
                                stringResource(Res.string.music_ui_nothing_queued_guests_can_add_the_next_one)
                            },
                            color = MusicPalette.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(
                        items = upNext,
                        key = { index, song -> "${song.videoId}#$index" }
                    ) { index, song ->
                        PartyQueueRow(
                            song = song,
                            onPlay = {
                                MusicPlayer.jumpTo(playback.index + 1 + index)
                            },
                            onRemove = {
                                if (MusicPlayer.removeUpcoming(song.videoId)) {
                                    MusicPartyManager.forgetAddition(song.videoId)
                                }
                            }
                        )
                    }
                }
            }
        }
        }
    }
    if (showQr) {
        Dialog(onDismissRequest = { showQr = false }) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MusicPalette.background)
                    .padding(16.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PartyQrCard(url = party.url)
                    Spacer(Modifier.height(10.dp))
                    PartyPillButton(
                        icon = Icons.Default.Close,
                        label = stringResource(Res.string.action_close),
                        primary = true,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showQr = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun PartyQrCard(url: String?) {
    val bitmap = rememberQrBitmap(url)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MusicPalette.surface)
            .border(1.dp, MusicPalette.border, RoundedCornerShape(18.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(198.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(Res.string.music_ui_party_join_qr_code),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Default.QrCode2,
                    contentDescription = null,
                    tint = MusicPalette.background,
                    modifier = Modifier.size(92.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(Res.string.music_ui_scan_to_join),
            color = MusicPalette.text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = url ?: stringResource(Res.string.music_ui_connect_this_device_to_wi_fi_or_ethernet),
            color = if (url == null) MusicPalette.error else MusicPalette.textMuted,
            fontSize = 10.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun PartyStatusCard(locked: Boolean, additions: Int, urlMissing: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (locked) Color(0x22FB7185) else MusicPalette.accentSoft)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(
                    when {
                        urlMissing -> MusicPalette.error
                        locked -> MusicPalette.accentCoral
                        else -> MusicPalette.accentTeal
                    }
                )
        )
        Text(
            text = when {
                urlMissing -> kotlinx.coroutines.runBlocking { getString(Res.string.music_ui_no_network_address) }
                locked -> kotlinx.coroutines.runBlocking { getString(Res.string.music_ui_additions_locked) }
                else -> kotlinx.coroutines.runBlocking { getString(Res.string.music_ui_open_to_guests) }
            },
            color = if (locked) MusicPalette.accentCoral else MusicPalette.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "$additions added",
            color = MusicPalette.textMuted,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun PartyNowPlaying(
    song: Song?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    addedBy: String?
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MusicPalette.surface)
            .border(1.dp, MusicPalette.border, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        MusicArtwork(
            url = song?.thumbnailUrl,
            modifier = Modifier.size(92.dp),
            cornerRadius = 12.dp,
            iconSize = 30.dp,
            artworkPx = 480
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = when {
                    song == null -> "WAITING"
                    isPlaying -> kotlinx.coroutines.runBlocking { getString(Res.string.music_ui_now_playing) }
                    else -> "PAUSED"
                },
                color = if (song == null) MusicPalette.textMuted else MusicPalette.accentTeal,
                fontSize = 9.sp,
                letterSpacing = 1.6.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = song?.title ?: stringResource(Res.string.music_ui_waiting_for_the_first_song_2),
                color = MusicPalette.text,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song?.artist ?: stringResource(Res.string.music_ui_the_queue_starts_when_a_guest_adds_something),
                color = MusicPalette.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (addedBy != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Added by $addedBy",
                    color = MusicPalette.textMuted,
                    fontSize = 10.sp
                )
            }
            if (song != null) {
                Spacer(Modifier.height(8.dp))
                PartyProgress(
                    progress = if (durationMs > 0L) {
                        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                )
            }
        }
    }
}

@Composable
private fun PartyProgress(progress: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(50))
            .background(MusicPalette.surfaceRaised)
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress)
                .height(4.dp)
                .background(MusicPalette.accent)
        )
    }
}

@Composable
private fun PartyPendingRow(
    request: PartyPendingRequest,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(MusicPalette.surface)
            .border(1.dp, MusicPalette.accent.copy(alpha = 0.28f), RoundedCornerShape(11.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MusicArtwork(
            url = request.song.thumbnailUrl,
            modifier = Modifier.size(38.dp),
            cornerRadius = 7.dp,
            iconSize = 15.dp,
            artworkPx = 160
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = request.song.title,
                color = MusicPalette.text,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    append(request.song.artist)
                    if (request.guestName.isNotBlank()) append(" · ${request.guestName}")
                },
                color = MusicPalette.textSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        PartyMiniAction(
            icon = Icons.Default.Check,
            label = stringResource(Res.string.music_ui_approve_named, request.song.title),
            tint = MusicPalette.accentTeal,
            onClick = onApprove
        )
        PartyMiniAction(
            icon = Icons.Default.Close,
            label = stringResource(Res.string.music_ui_decline_named, request.song.title),
            tint = MusicPalette.accentCoral,
            onClick = onReject
        )
    }
}

@Composable
private fun PartyMiniAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (focused) Color.White else MusicPalette.surfaceRaised)
            .border(
                1.dp,
                if (focused) Color.White else MusicPalette.border,
                CircleShape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (focused) MusicPalette.background else tint,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun PartyQueueRow(
    song: Song,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val addedBy = MusicPartyManager.addedBy(song.videoId)
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(
                if (focused) MusicPalette.surfaceFocused else MusicPalette.surface
            )
            .border(
                2.dp,
                if (focused) Color.White else Color.Transparent,
                RoundedCornerShape(11.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onPlay)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MusicArtwork(
            url = song.thumbnailUrl,
            modifier = Modifier.size(42.dp),
            cornerRadius = 7.dp,
            iconSize = 16.dp,
            artworkPx = 160
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
                text = buildString {
                    append(song.artist)
                    if (!addedBy.isNullOrBlank()) append(" · added by $addedBy")
                },
                color = MusicPalette.textSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        song.durationText?.takeIf { it.isNotBlank() }?.let {
            Text(text = it, color = MusicPalette.textMuted, fontSize = 10.sp)
        }
        PartyTrashButton(onClick = onRemove)
    }
}

@Composable
private fun PartyTrashButton(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (focused) Color.White else MusicPalette.surfaceRaised)
            .border(
                1.dp,
                if (focused) Color.White else MusicPalette.border,
                CircleShape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Delete,
            contentDescription = stringResource(Res.string.music_ui_remove_from_queue),
            tint = if (focused) MusicPalette.background else MusicPalette.textMuted,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun PartyStep(number: String, title: String, detail: String, fillWidth: Boolean = false) {
    Column(
        Modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(186.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(MusicPalette.surface)
            .border(1.dp, MusicPalette.border, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MusicPalette.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = MusicPalette.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(9.dp))
        Text(
            text = title,
            color = MusicPalette.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = detail,
            color = MusicPalette.textSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
    }
}

@Composable
private fun PartyLivePill(locked: Boolean) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (locked) Color(0x22FB7185) else MusicPalette.accentSoft)
            .border(
                1.dp,
                if (locked) MusicPalette.accentCoral.copy(alpha = 0.4f)
                else MusicPalette.accentTeal.copy(alpha = 0.3f),
                RoundedCornerShape(50)
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (locked) MusicPalette.accentCoral else MusicPalette.accentTeal)
        )
        Text(
            text = if (locked) stringResource(Res.string.music_ui_locked) else kotlinx.coroutines.runBlocking { getString(Res.string.music_ui_party_live) },
            color = if (locked) MusicPalette.accentCoral else MusicPalette.accentTeal,
            fontSize = 9.sp,
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PartyPillButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(
                when {
                    focused && !destructive -> MusicPalette.text
                    focused -> MusicPalette.accentCoral
                    primary -> MusicPalette.accent
                    else -> MusicPalette.surfaceRaised
                }
            )
            .border(
                2.dp,
                when {
                    focused -> Color.White
                    destructive -> MusicPalette.accentCoral.copy(alpha = 0.5f)
                    else -> MusicPalette.border
                },
                RoundedCornerShape(50)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = when {
                focused && destructive -> MusicPalette.background
                focused -> MusicPalette.background
                primary -> MusicPalette.background
                destructive -> MusicPalette.accentCoral
                else -> MusicPalette.text
            },
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = label,
            color = when {
                focused && destructive -> MusicPalette.background
                focused -> MusicPalette.background
                primary -> MusicPalette.background
                destructive -> MusicPalette.accentCoral
                else -> MusicPalette.text
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PartyCloseButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (focused) Color.White else MusicPalette.surfaceRaised)
            .border(
                2.dp,
                if (focused) Color.White else MusicPalette.border,
                CircleShape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(Res.string.music_ui_close_party),
            tint = if (focused) MusicPalette.background else MusicPalette.text,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun PartyNotice(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    error: Boolean = false
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (error) Color(0x33FF6B6B) else MusicPalette.accentSoft)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (error) MusicPalette.error else MusicPalette.accent,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            color = if (error) MusicPalette.error else MusicPalette.textSecondary,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun rememberQrBitmap(content: String?): Bitmap? = remember(content) {
    content?.let { value ->
        runCatching {
            val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 640, 640)
            val width = matrix.width
            val height = matrix.height
            val pixels = IntArray(width * height) { index ->
                if (matrix.get(index % width, index / width)) {
                    0xFF000000.toInt()
                } else {
                    0xFFFFFFFF.toInt()
                }
            }
            Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }
}
