package com.nuvio.app.music

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import com.nuvio.app.music.engine.data.model.Song
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.getString

private enum class LibraryFilter(val labelRes: StringResource) {
    RECENTS(Res.string.music_ui_recently_played),
    LIKED(Res.string.music_ui_liked_songs),
    PLAYLISTS(Res.string.yt_tab_playlists),
    DOWNLOADS(Res.string.compose_settings_root_downloads_title)
}

@Composable
internal fun MusicLibraryScreen(
    liked: List<Song>,
    recents: List<Song>,
    playlists: List<StoredPlaylist>,
    accountName: String?,
    accountLiked: List<Song>,
    accountPlaylists: List<MusicAccountPlaylist>,
    accountLoading: Boolean,
    onRefreshAccount: () -> Unit,
    onSignIn: () -> Unit,
    onManageAccount: () -> Unit,
    onPlayAccountLiked: (Int) -> Unit,
    onOpenAccountPlaylist: (MusicAccountPlaylist) -> Unit,
    onCreatePlaylist: () -> Unit,
    onPlayLiked: (Int) -> Unit,
    onPlayRecents: (Int) -> Unit,
    onOpenPlaylist: (StoredPlaylist) -> Unit,
    onOpenDownloads: () -> Unit,
    onPlayDownloaded: (List<Song>, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val playback by MusicPlayer.state.collectAsState()
    val downloads by MusicDownloads.state.collectAsState()
    var filter by remember { mutableStateOf(LibraryFilter.RECENTS) }
    val firstFilterFocus = remember { FocusRequester() }
    val contentState = rememberLazyListState()

    val downloadedSongs = remember(downloads.completed) { downloads.completed.map { it.song } }
    val likedCount = liked.size + accountLiked.size
    val playlistCount = playlists.size + accountPlaylists.size

    LaunchedEffect(filter) {
        runCatching { contentState.animateScrollToItem(0) }
    }

    val compact = LocalConfiguration.current.screenWidthDp < 600
    val header: @Composable (Modifier) -> Unit = { headerModifier ->
        Column(headerModifier) {
            Text(
                text = stringResource(Res.string.profile_insights_stat_library),
                color = MusicPalette.text,
                fontFamily = MusicDisplay,
                fontSize = if (compact) 30.sp else 40.sp,
                lineHeight = if (compact) 32.sp else 43.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.music_ui_your_likes_playlists_and_listening_history),
                color = MusicPalette.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(14.dp))

            LibraryRailAccount(
                accountName = accountName,
                onSignIn = onSignIn,
                onManage = onManageAccount
            )
            if (accountName != null) {
                Spacer(Modifier.height(8.dp))
                LibraryActionButton(
                    icon = Icons.Default.Refresh,
                    label = if (accountLoading) stringResource(Res.string.music_ui_refreshing) else stringResource(Res.string.music_ui_refresh_youtube_music),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onRefreshAccount
                )
            }
            Spacer(Modifier.height(14.dp))

            if (compact) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LibraryFilterPill(
                        icon = Icons.Default.Schedule,
                        label = stringResource(LibraryFilter.RECENTS.labelRes),
                        count = recents.size,
                        selected = filter == LibraryFilter.RECENTS,
                        modifier = Modifier.focusRequester(firstFilterFocus),
                        onSelect = { filter = LibraryFilter.RECENTS }
                    )
                    LibraryFilterPill(
                        icon = Icons.Default.Favorite,
                        label = stringResource(LibraryFilter.LIKED.labelRes),
                        count = likedCount,
                        selected = filter == LibraryFilter.LIKED,
                        onSelect = { filter = LibraryFilter.LIKED }
                    )
                    LibraryFilterPill(
                        icon = Icons.Default.PlaylistPlay,
                        label = stringResource(LibraryFilter.PLAYLISTS.labelRes),
                        count = playlistCount,
                        selected = filter == LibraryFilter.PLAYLISTS,
                        onSelect = { filter = LibraryFilter.PLAYLISTS }
                    )
                    LibraryFilterPill(
                        icon = Icons.Default.Download,
                        label = stringResource(LibraryFilter.DOWNLOADS.labelRes),
                        count = downloads.completed.size,
                        selected = filter == LibraryFilter.DOWNLOADS,
                        onSelect = { filter = LibraryFilter.DOWNLOADS }
                    )
                }
            } else {
                LibraryFilterRow(
                    icon = Icons.Default.Schedule,
                    label = stringResource(LibraryFilter.RECENTS.labelRes),
                    count = recents.size,
                    selected = filter == LibraryFilter.RECENTS,
                    modifier = Modifier.focusRequester(firstFilterFocus),
                    onSelect = { filter = LibraryFilter.RECENTS }
                )
                Spacer(Modifier.height(6.dp))
                LibraryFilterRow(
                    icon = Icons.Default.Favorite,
                    label = stringResource(LibraryFilter.LIKED.labelRes),
                    count = likedCount,
                    selected = filter == LibraryFilter.LIKED,
                    onSelect = { filter = LibraryFilter.LIKED }
                )
                Spacer(Modifier.height(6.dp))
                LibraryFilterRow(
                    icon = Icons.Default.PlaylistPlay,
                    label = stringResource(LibraryFilter.PLAYLISTS.labelRes),
                    count = playlistCount,
                    selected = filter == LibraryFilter.PLAYLISTS,
                    onSelect = { filter = LibraryFilter.PLAYLISTS }
                )
                Spacer(Modifier.height(6.dp))
                LibraryFilterRow(
                    icon = Icons.Default.Download,
                    label = stringResource(LibraryFilter.DOWNLOADS.labelRes),
                    count = downloads.completed.size,
                    selected = filter == LibraryFilter.DOWNLOADS,
                    onSelect = { filter = LibraryFilter.DOWNLOADS }
                )
            }

            if (!compact) {
                Spacer(Modifier.weight(1f))

                RailNewPlaylistButton(onClick = onCreatePlaylist)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (downloads.totalBytes > 0L) {
                        "${formatDownloadBytes(downloads.totalBytes)} on this device"
                    } else {
                        stringResource(Res.string.music_ui_nothing_downloaded_yet)
                    },
                    color = MusicPalette.textMuted,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    val content: @Composable (Modifier) -> Unit = { contentModifier ->
        LazyColumn(
            state = contentState,
            modifier = contentModifier,
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "library-hero") {
                val hero = when (filter) {
                    LibraryFilter.RECENTS -> {
                        val canPlay = recents.isNotEmpty()
                        LibraryHeroModel(
                            badge = kotlinx.coroutines.runBlocking { getString(Res.string.music_ui_your_history) },
                            title = stringResource(Res.string.music_ui_jump_back_in),
                            subtitle = if (canPlay) {
                                "${recents.size} tracks · pick up where you left off"
                            } else {
                                stringResource(Res.string.music_ui_tracks_you_play_will_appear_here)
                            },
                            artworkUrl = recents.firstOrNull()?.thumbnailUrl,
                            primaryLabel = stringResource(Res.string.action_play),
                            onPrimary = if (canPlay) ({ onPlayRecents(0) }) else null,
                            secondaryLabel = if (recents.size > 1) stringResource(Res.string.random_episode_title) else null,
                            onSecondary = if (recents.size > 1) {
                                ({ onPlayRecents(recents.indices.random()) })
                            } else {
                                null
                            }
                        )
                    }
                    LibraryFilter.LIKED -> {
                        val firstPlay: (() -> Unit)? = when {
                            accountLiked.isNotEmpty() -> ({ onPlayAccountLiked(0) })
                            liked.isNotEmpty() -> ({ onPlayLiked(0) })
                            else -> null
                        }
                        LibraryHeroModel(
                            badge = kotlinx.coroutines.runBlocking { getString(Res.string.music_ui_liked_songs_header) },
                            title = stringResource(Res.string.music_ui_songs_you_love),
                            subtitle = if (likedCount > 0) {
                                "$likedCount tracks · the ones you keep coming back to"
                            } else {
                                stringResource(Res.string.music_ui_songs_you_like_will_appear_here)
                            },
                            artworkUrl = (accountLiked.firstOrNull() ?: liked.firstOrNull())?.thumbnailUrl,
                            primaryLabel = stringResource(Res.string.action_play),
                            onPrimary = firstPlay
                        )
                    }
                    LibraryFilter.PLAYLISTS -> {
                        val firstLocal = playlists.firstOrNull()
                        LibraryHeroModel(
                            badge = stringResource(Res.string.music_ui_collection),
                            title = stringResource(Res.string.music_ui_your_collection),
                            subtitle = if (playlistCount > 0) {
                                "$playlistCount playlists · on this device and YouTube Music"
                            } else {
                                stringResource(Res.string.music_ui_create_a_playlist_then_add_songs_from_now_playing)
                            },
                            artworkUrl = accountPlaylists.firstOrNull()?.thumbnailUrl
                                ?: playlists.firstOrNull()?.songs?.firstOrNull()?.thumbnailUrl,
                            primaryLabel = stringResource(Res.string.music_ui_new_playlist),
                            onPrimary = onCreatePlaylist,
                            secondaryLabel = if (firstLocal != null) stringResource(Res.string.music_ui_open_first) else null,
                            onSecondary = if (firstLocal != null) {
                                ({ onOpenPlaylist(firstLocal) })
                            } else {
                                null
                            }
                        )
                    }
                    LibraryFilter.DOWNLOADS -> {
                        LibraryHeroModel(
                            badge = stringResource(Res.string.music_ui_offline),
                            title = stringResource(Res.string.music_ui_offline_library),
                            subtitle = if (downloadedSongs.isEmpty()) {
                                stringResource(Res.string.music_ui_save_albums_and_playlists_for_offline_listening)
                            } else {
                                "${downloadedSongs.size} tracks · " +
                                    "${formatDownloadBytes(downloads.totalBytes)} · plays offline"
                            },
                            artworkUrl = downloadedSongs.firstOrNull()?.thumbnailUrl,
                            primaryLabel = stringResource(Res.string.music_ui_open_downloads),
                            onPrimary = onOpenDownloads,
                            secondaryLabel = if (downloadedSongs.isNotEmpty()) stringResource(Res.string.music_ui_play_all) else null,
                            onSecondary = if (downloadedSongs.isNotEmpty()) {
                                ({ onPlayDownloaded(downloadedSongs, 0) })
                            } else {
                                null
                            }
                        )
                    }
                }
                LibraryHero(hero)
            }

            when (filter) {
                LibraryFilter.RECENTS -> {
                    item(key = "recents-header") {
                        LibrarySectionHeader(
                            icon = Icons.Default.Schedule,
                            title = stringResource(Res.string.music_ui_recently_played),
                            count = recents.size,
                            horizontalPadding = 0.dp,
                            onPlayAll = if (recents.isNotEmpty()) {
                                { onPlayRecents(0) }
                            } else {
                                null
                            }
                        )
                    }
                    if (recents.isEmpty()) {
                        item(key = "recents-empty") {
                            LibraryHint(
                                icon = Icons.Default.Schedule,
                                text = stringResource(Res.string.music_ui_tracks_you_play_will_appear_here),
                                horizontalPadding = 0.dp
                            )
                        }
                    } else {
                        itemsIndexed(
                            items = recents.take(30),
                            key = { index, song -> "recent:${song.videoId}#$index" }
                        ) { index, song ->
                            MusicTrackRow(
                                song = song,
                                index = index,
                                isCurrent = playback.song?.videoId == song.videoId,
                                isPlaying = playback.isPlaying &&
                                    playback.song?.videoId == song.videoId,
                                onClick = { onPlayRecents(index) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                LibraryFilter.LIKED -> {
                    if (accountName != null) {
                        item(key = "account-liked-header") {
                            LibrarySectionHeader(
                                icon = Icons.Default.Favorite,
                                title = stringResource(Res.string.music_ui_liked_on_youtube_music),
                                count = accountLiked.size,
                                horizontalPadding = 0.dp,
                                onPlayAll = if (accountLiked.isNotEmpty()) {
                                    { onPlayAccountLiked(0) }
                                } else {
                                    null
                                }
                            )
                        }
                        if (accountLoading && accountLiked.isEmpty()) {
                            item(key = "account-liked-loading") {
                                LibraryHint(
                                    icon = Icons.Default.AccountCircle,
                                    text = stringResource(Res.string.music_ui_loading_your_youtube_music_likes),
                                    horizontalPadding = 0.dp
                                )
                            }
                        } else if (accountLiked.isEmpty()) {
                            item(key = "account-liked-empty") {
                                LibraryHint(
                                    icon = Icons.Default.Favorite,
                                    text = stringResource(Res.string.music_ui_songs_you_like_on_youtube_music_will_appear_here),
                                    horizontalPadding = 0.dp
                                )
                            }
                        } else {
                            itemsIndexed(
                                items = accountLiked,
                                key = { index, song -> "account-liked:${song.videoId}#$index" }
                            ) { index, song ->
                                MusicTrackRow(
                                    song = song,
                                    index = index,
                                    isCurrent = playback.song?.videoId == song.videoId,
                                    isPlaying = playback.isPlaying && playback.song?.videoId == song.videoId,
                                    onClick = { onPlayAccountLiked(index) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    item(key = "local-liked-header") {
                        LibrarySectionHeader(
                            icon = Icons.Default.Favorite,
                            title = if (accountName != null) stringResource(Res.string.music_ui_liked_on_this_device) else stringResource(Res.string.music_ui_liked_songs),
                            count = liked.size,
                            horizontalPadding = 0.dp,
                            onPlayAll = if (liked.isNotEmpty()) {
                                { onPlayLiked(0) }
                            } else {
                                null
                            }
                        )
                    }
                    if (liked.isEmpty()) {
                        item(key = "local-liked-empty") {
                            LibraryHint(
                                icon = Icons.Default.Favorite,
                                text = stringResource(Res.string.music_ui_songs_you_like_will_appear_here_use_the_heart_on_now_playing),
                                horizontalPadding = 0.dp
                            )
                        }
                    } else {
                        itemsIndexed(
                            items = liked,
                            key = { index, song -> "liked:${song.videoId}#$index" }
                        ) { index, song ->
                            MusicTrackRow(
                                song = song,
                                index = index,
                                isCurrent = playback.song?.videoId == song.videoId,
                                isPlaying = playback.isPlaying && playback.song?.videoId == song.videoId,
                                onClick = { onPlayLiked(index) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                LibraryFilter.PLAYLISTS -> {
                    if (accountName != null) {
                        item(key = "account-playlists-header") {
                            LibrarySectionHeader(
                                icon = Icons.Default.PlaylistPlay,
                                title = stringResource(Res.string.music_ui_from_youtube_music),
                                count = accountPlaylists.size,
                                horizontalPadding = 0.dp
                            )
                        }
                        if (accountPlaylists.isEmpty()) {
                            item(key = "account-playlists-empty") {
                                LibraryHint(
                                    icon = Icons.Default.PlaylistPlay,
                                    text = stringResource(Res.string.music_ui_playlists_from_your_youtube_music_account_will_appear_here),
                                    horizontalPadding = 0.dp
                                )
                            }
                        } else {
                            item(key = "account-playlists-grid") {
                                LibraryTileGrid(
                                    columns = if (compact) 2 else 4,
                                    items = accountPlaylists.map { playlist ->
                                        LibraryTile(
                                            artworkUrl = playlist.thumbnailUrl,
                                            title = playlist.title,
                                            subtitle = playlist.count?.let {
                                                "$it track" + if (it == 1) "" else "s"
                                            } ?: "YouTube Music",
                                            onClick = { onOpenAccountPlaylist(playlist) }
                                        )
                                    }
                                )
                            }
                        }
                    }

                    item(key = "local-playlists-header") {
                        LibrarySectionHeader(
                            icon = Icons.Default.PlaylistPlay,
                            title = if (accountName != null) stringResource(Res.string.music_ui_on_this_device) else stringResource(Res.string.yt_tab_playlists),
                            count = playlists.size,
                            horizontalPadding = 0.dp
                        )
                    }
                    item(key = "local-playlists-grid") {
                        LibraryTileGrid(
                            columns = if (compact) 2 else 4,
                            items = playlists.map { playlist ->
                                LibraryTile(
                                    artworkUrl = playlist.songs.firstOrNull()?.thumbnailUrl,
                                    title = playlist.name,
                                    subtitle = stringResource(
                                            if (playlist.songs.size == 1) Res.string.music_ui_track_count
                                            else Res.string.music_ui_track_count_plural,
                                            playlist.songs.size,
                                        ),
                                    onClick = { onOpenPlaylist(playlist) }
                                )
                            } + LibraryTile(
                                artworkUrl = null,
                                title = stringResource(Res.string.music_ui_new_playlist),
                                subtitle = stringResource(Res.string.music_ui_create_a_playlist),
                                isNew = true,
                                onClick = onCreatePlaylist
                            )
                        )
                    }
                }

                LibraryFilter.DOWNLOADS -> {
                    item(key = "downloads-card") {
                        DownloadsLibraryRow(
                            count = downloads.completed.size,
                            bytes = downloads.totalBytes,
                            active = downloads.active.size + downloads.starting.size,
                            onClick = onOpenDownloads,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item(key = "downloads-hint") {
                        LibraryHint(
                            icon = Icons.Default.Download,
                            text = stringResource(Res.string.music_ui_manage_downloads_retry_failures_and_free_space_in_the_downlo),
                            horizontalPadding = 0.dp
                        )
                    }
                    if (downloadedSongs.isNotEmpty()) {
                        item(key = "downloaded-tracks-header") {
                            LibrarySectionHeader(
                                icon = Icons.Default.Download,
                                title = stringResource(Res.string.music_ui_downloaded_tracks),
                                count = downloadedSongs.size,
                                horizontalPadding = 0.dp,
                                onPlayAll = { onPlayDownloaded(downloadedSongs, 0) }
                            )
                        }
                        item(key = "downloaded-tracks-grid") {
                            LibraryTileGrid(
                                columns = if (compact) 2 else 4,
                                items = downloadedSongs.take(12).mapIndexed { index, song ->
                                    LibraryTile(
                                        artworkUrl = song.thumbnailUrl,
                                        title = song.title,
                                        subtitle = song.artist,
                                        onClick = { onPlayDownloaded(downloadedSongs, index) }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    if (compact) {
        Column(
            modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 14.dp, top = 8.dp, bottom = 8.dp)
        ) {
            header(Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            content(Modifier.weight(1f).fillMaxWidth())
        }
    } else {
        Row(
            modifier
                .fillMaxSize()
                .padding(horizontal = 26.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(26.dp)
        ) {
            header(Modifier.width(330.dp).fillMaxHeight())
            content(Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun LibraryRailAccount(
    accountName: String?,
    onSignIn: () -> Unit,
    onManage: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clip(shape)
            .background(if (focused) MusicPalette.surfaceFocused else MusicPalette.surface)
            .border(2.dp, if (focused) Color.White else MusicPalette.border, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable { if (accountName == null) onSignIn() else onManage() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MusicPalette.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AccountCircle,
                contentDescription = null,
                tint = MusicPalette.accent,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.music_ui_youtube_music_account),
                color = MusicPalette.text,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (accountName == null) stringResource(Res.string.music_ui_sign_in_to_youtube_music) else accountName,
                color = MusicPalette.textSecondary,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = if (accountName == null) stringResource(Res.string.youtube_sign_in) else stringResource(Res.string.live_tv_ui_manage),
            color = if (focused) MusicPalette.text else MusicPalette.accent,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun LibraryFilterPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    count: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .height(38.dp)
            .clip(shape)
            .background(
                when {
                    selected -> Color.White
                    focused -> MusicPalette.surfaceFocused
                    else -> MusicPalette.surface.copy(alpha = 0.55f)
                }
            )
            .border(
                2.dp,
                when {
                    focused -> MusicPalette.accent
                    selected -> Color.Transparent
                    else -> MusicPalette.border.copy(alpha = 0.55f)
                },
                shape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) MusicPalette.background else MusicPalette.accent,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = label,
            color = if (selected) MusicPalette.background else MusicPalette.text,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Text(
            text = count.toString(),
            color = if (selected) {
                MusicPalette.background.copy(alpha = 0.55f)
            } else {
                MusicPalette.textMuted
            },
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun LibraryFilterRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    count: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(
                when {
                    selected -> Color.White
                    focused -> MusicPalette.surfaceFocused
                    else -> MusicPalette.surface.copy(alpha = 0.55f)
                }
            )
            .border(
                2.dp,
                when {
                    focused -> MusicPalette.accent
                    selected -> Color.Transparent
                    else -> MusicPalette.border.copy(alpha = 0.55f)
                },
                shape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) MusicPalette.background else MusicPalette.accent,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            color = if (selected) MusicPalette.background else MusicPalette.text,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = count.toString(),
            color = if (selected) {
                MusicPalette.background.copy(alpha = 0.55f)
            } else {
                MusicPalette.textMuted
            },
            fontSize = 11.5.sp
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = if (selected) MusicPalette.background.copy(alpha = 0.55f) else MusicPalette.textMuted,
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
private fun RailNewPlaylistButton(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(50))
            .background(if (focused) Color.White else MusicPalette.surfaceRaised)
            .border(
                2.dp,
                if (focused) Color.White else MusicPalette.border,
                RoundedCornerShape(50)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            tint = if (focused) MusicPalette.background else MusicPalette.text,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = stringResource(Res.string.music_ui_new_playlist),
            color = if (focused) MusicPalette.background else MusicPalette.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private data class LibraryHeroModel(
    val badge: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
    val primaryLabel: String,
    val onPrimary: (() -> Unit)?,
    val secondaryLabel: String? = null,
    val onSecondary: (() -> Unit)? = null
)

@Composable
private fun LibraryHero(model: LibraryHeroModel) {
    val compact = LocalConfiguration.current.screenWidthDp < 600
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(if (compact) 150.dp else 180.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MusicPalette.accent.copy(alpha = 0.28f),
                        MusicPalette.surface,
                        MusicPalette.background
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .padding(if (compact) 14.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 20.dp)
    ) {
        MusicArtwork(
            url = model.artworkUrl,
            modifier = Modifier.size(if (compact) 96.dp else 144.dp),
            cornerRadius = 14.dp,
            iconSize = if (compact) 32.dp else 44.dp,
            artworkPx = 720
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = model.badge,
                color = MusicPalette.accentTeal,
                fontSize = 9.5.sp,
                letterSpacing = 1.7.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = model.title,
                color = MusicPalette.text,
                fontFamily = MusicDisplay,
                fontSize = if (compact) 24.sp else 30.sp,
                lineHeight = if (compact) 26.sp else 33.sp,
                fontWeight = FontWeight.Bold,
                maxLines = if (compact) 2 else 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = model.subtitle,
                color = MusicPalette.textSecondary,
                fontSize = if (compact) 11.5.sp else 12.5.sp,
                lineHeight = if (compact) 15.sp else 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (model.onPrimary != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    if (compact) {
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    } else {
                        Modifier
                    },
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    LibraryActionButton(
                        icon = Icons.Default.PlayArrow,
                        label = model.primaryLabel,
                        onClick = model.onPrimary
                    )
                    if (model.onSecondary != null && model.secondaryLabel != null) {
                        LibraryActionButton(
                            icon = Icons.Default.Shuffle,
                            label = model.secondaryLabel,
                            onClick = model.onSecondary
                        )
                    }
                }
            }
        }
    }
}

private data class LibraryTile(
    val artworkUrl: String?,
    val title: String,
    val subtitle: String,
    val isNew: Boolean = false,
    val onClick: () -> Unit
)

@Composable
private fun LibraryTileGrid(
    items: List<LibraryTile>,
    columns: Int = 4
) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items.chunked(columns).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                row.forEach { item ->
                    LibraryTileCard(item, Modifier.weight(1f))
                }
                repeat(columns - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LibraryTileCard(
    item: LibraryTile,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.02f else 1f,
        animationSpec = tween(140),
        label = stringResource(Res.string.music_ui_librarytilescale)
    )
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(if (focused) MusicPalette.surfaceFocused else Color.Transparent)
            .border(2.dp, if (focused) Color.White else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = item.onClick)
            .padding(6.dp)
    ) {
        if (item.isNew) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MusicPalette.surfaceRaised)
                    .border(1.dp, MusicPalette.border, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = MusicPalette.accent,
                    modifier = Modifier.size(30.dp)
                )
            }
        } else {
            MusicArtwork(
                url = item.artworkUrl,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                cornerRadius = 12.dp,
                iconSize = 28.dp,
                artworkPx = 480
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.title,
            color = MusicPalette.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = item.subtitle,
            color = MusicPalette.textSecondary,
            fontSize = 11.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LibrarySectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    count: Int,
    onPlayAll: (() -> Unit)? = null,
    horizontalPadding: Dp = 26.dp
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MusicPalette.accent,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            color = MusicPalette.text,
            fontFamily = MusicDisplay,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = count.toString(),
            color = MusicPalette.textMuted,
            fontSize = 11.5.sp
        )
        Spacer(Modifier.weight(1f))
        if (onPlayAll != null) {
            LibraryActionButton(
                icon = Icons.Default.PlayArrow,
                label = stringResource(Res.string.music_ui_play_all),
                onClick = onPlayAll
            )
        }
    }
}

@Composable
private fun LibraryHint(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    horizontalPadding: Dp = 26.dp
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .clip(RoundedCornerShape(12.dp))
            .background(MusicPalette.surface)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MusicPalette.textMuted,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = text,
            color = MusicPalette.textSecondary,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun AccountSignInCard(onSignIn: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 26.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MusicPalette.surface)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            Icons.Default.AccountCircle,
            contentDescription = null,
            tint = MusicPalette.accent,
            modifier = Modifier.size(30.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.music_ui_youtube_music_account),
                color = MusicPalette.text,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(Res.string.music_ui_sign_in_to_youtube_music_to_see_your_liked_songs_and_playlis),
                color = MusicPalette.textSecondary,
                fontSize = 13.sp
            )
        }
        LibraryActionButton(
            icon = Icons.Default.AccountCircle,
            label = stringResource(Res.string.youtube_sign_in),
            onClick = onSignIn
        )
    }
}

@Composable
private fun AccountHeader(accountName: String, onManage: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Default.AccountCircle,
            contentDescription = null,
            tint = MusicPalette.accent,
            modifier = Modifier.size(26.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.music_ui_youtube_music_account),
                color = MusicPalette.text,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = accountName,
                color = MusicPalette.textSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        LibraryActionButton(
            icon = Icons.Default.Person,
            label = stringResource(Res.string.live_tv_ui_manage),
            onClick = onManage
        )
    }
}

@Composable
private fun AccountPlaylistGrid(
    playlists: List<MusicAccountPlaylist>,
    onOpen: (MusicAccountPlaylist) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 26.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        playlists.chunked(4).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { playlist ->
                    AccountPlaylistTile(
                        playlist = playlist,
                        modifier = Modifier.weight(1f),
                        onClick = { onOpen(playlist) }
                    )
                }
                repeat(4 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AccountPlaylistTile(
    playlist: MusicAccountPlaylist,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.02f else 1f,
        animationSpec = tween(140),
        label = stringResource(Res.string.music_ui_accountplaylistscale)
    )
    val shape = RoundedCornerShape(12.dp)
    val subtitle = playlist.count?.let {
        "$it track" + if (it == 1) "" else "s"
    } ?: stringResource(Res.string.music_ui_music_playlist)
    Row(
        modifier
            .height(78.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(if (focused) MusicPalette.surfaceFocused else MusicPalette.surface)
            .border(
                2.dp,
                if (focused) Color.White else MusicPalette.border,
                shape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MusicArtwork(
            url = playlist.thumbnailUrl,
            modifier = Modifier.size(48.dp),
            cornerRadius = 9.dp,
            iconSize = 22.dp
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                color = MusicPalette.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = MusicPalette.textSecondary,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DownloadsLibraryRow(
    count: Int,
    bytes: Long,
    active: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (focused) MusicPalette.surfaceFocused else MusicPalette.surface
            )
            .border(
                2.dp,
                if (focused) Color.White else MusicPalette.border,
                RoundedCornerShape(12.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MusicPalette.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Download,
                contentDescription = null,
                tint = MusicPalette.accent,
                modifier = Modifier.size(23.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.compose_settings_root_downloads_title),
                color = MusicPalette.text,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = when {
                    active > 0 -> "$active downloading · $count on this device"
                    count > 0 -> "$count track" + (if (count == 1) "" else "s") +
                        " · ${formatDownloadBytes(bytes)} · plays offline"
                    else -> kotlinx.coroutines.runBlocking { getString(Res.string.music_ui_save_offline_hint) }
                },
                color = if (count > 0) MusicPalette.accentTeal else MusicPalette.textSecondary,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(Res.string.music_ui_open_downloads),
            tint = if (focused) MusicPalette.text else MusicPalette.textMuted,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
internal fun LibraryActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (focused) Color.White else MusicPalette.surfaceRaised)
            .border(
                2.dp,
                if (focused) Color.White else MusicPalette.border,
                RoundedCornerShape(50)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (focused) MusicPalette.background else MusicPalette.text,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            color = if (focused) MusicPalette.background else MusicPalette.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun LocalPlaylistGrid(
    playlists: List<StoredPlaylist>,
    onOpen: (StoredPlaylist) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 26.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        playlists.chunked(4).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { playlist ->
                    LocalPlaylistTile(
                        playlist = playlist,
                        modifier = Modifier.weight(1f),
                        onClick = { onOpen(playlist) }
                    )
                }
                repeat(4 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LocalPlaylistTile(
    playlist: StoredPlaylist,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.02f else 1f,
        animationSpec = tween(140),
        label = stringResource(Res.string.music_ui_localplaylistscale)
    )
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .height(78.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(if (focused) MusicPalette.surfaceFocused else MusicPalette.surface)
            .border(
                2.dp,
                if (focused) Color.White else MusicPalette.border,
                shape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MusicArtwork(
            url = playlist.songs.firstOrNull()?.thumbnailUrl,
            modifier = Modifier.size(48.dp),
            cornerRadius = 9.dp,
            iconSize = 22.dp
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                color = MusicPalette.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${playlist.songs.size} track" +
                    if (playlist.songs.size == 1) "" else "s",
                color = MusicPalette.textSecondary,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun MusicLocalPlaylistScreen(
    playlist: StoredPlaylist,
    onBack: () -> Unit,
    onPlay: (Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playback by MusicPlayer.state.collectAsState()
    val songs = remember(playlist.songs) { playlist.songs.map { it.toSong() } }
    val firstFocus = remember { FocusRequester() }
    var backFocused by remember { mutableStateOf(false) }

    LaunchedEffect(playlist.id) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item(key = "local-playlist-header") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 26.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (backFocused) Color.White else MusicPalette.surfaceRaised)
                            .border(
                                2.dp,
                                if (backFocused) Color.White else MusicPalette.border,
                                RoundedCornerShape(50)
                            )
                            .onFocusChanged { backFocused = it.isFocused }
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
                            tint = if (backFocused) MusicPalette.background else MusicPalette.text,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.size(14.dp))
                    Text(
                        text = stringResource(Res.string.music_ui_playlist_2),
                        color = MusicPalette.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = playlist.name,
                    color = MusicPalette.text,
                    fontFamily = MusicDisplay,
                    fontSize = 40.sp,
                    lineHeight = 45.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${songs.size} track" + if (songs.size == 1) "" else "s",
                    color = MusicPalette.textMuted,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LibraryActionButton(
                        icon = Icons.Default.PlayArrow,
                        label = stringResource(Res.string.action_play),
                        modifier = Modifier.focusRequester(firstFocus),
                        onClick = { if (songs.isNotEmpty()) onPlay(0) }
                    )
                    LibraryActionButton(
                        icon = Icons.Default.Delete,
                        label = stringResource(Res.string.music_ui_delete_playlist),
                        onClick = onDelete
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        if (songs.isEmpty()) {
            item(key = "local-playlist-empty") {
                MusicEmptyState(
                    title = stringResource(Res.string.music_ui_no_songs_yet),
                    message = stringResource(Res.string.music_ui_add_songs_from_now_playing_playlist),
                    modifier = Modifier.fillMaxWidth().height(280.dp)
                )
            }
        } else {
            itemsIndexed(
                items = songs,
                key = { index, song -> "${song.videoId}#$index" }
            ) { index, song ->
                MusicTrackRow(
                    song = song,
                    index = index,
                    isCurrent = playback.song?.videoId == song.videoId,
                    isPlaying = playback.isPlaying && playback.song?.videoId == song.videoId,
                    onClick = { onPlay(index) },
                    modifier = Modifier.padding(horizontal = 26.dp)
                )
            }
        }
    }
}
