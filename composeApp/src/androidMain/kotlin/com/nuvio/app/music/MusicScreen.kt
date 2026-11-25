package com.nuvio.app.music

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.LocalNuvioBottomNavigationOverlayPadding
import com.nuvio.app.features.music.MusicHost
import com.nuvio.app.music.engine.data.model.BrowseType
import com.nuvio.app.R
import com.nuvio.app.music.engine.data.model.HomeShelf
import com.nuvio.app.music.engine.data.model.ShelfItem
import com.nuvio.app.music.engine.data.model.Song
import com.nuvio.app.youtube.YouTubeAccountViewModel
import com.nuvio.app.music.LocalOpenSidebar
import com.nuvio.app.music.LocalSidebarExpanded
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_nav_home
import nuvio.composeapp.generated.resources.compose_nav_library
import nuvio.composeapp.generated.resources.compose_nav_search
import nuvio.composeapp.generated.resources.compose_settings_page_integrations
import nuvio.composeapp.generated.resources.compose_settings_page_root
import nuvio.composeapp.generated.resources.music_settings_title
import nuvio.composeapp.generated.resources.music_tab_explore
import nuvio.composeapp.generated.resources.music_tab_local
import nuvio.composeapp.generated.resources.music_tab_party
import org.jetbrains.compose.resources.stringResource
import nuvio.composeapp.generated.resources.*
import kotlin.math.roundToInt

/** One density scale for the whole Music section. */
private const val MUSIC_UI_SCALE = 0.88f

private data class MusicVideoRequest(val video: Song, val startPositionMs: Long)

/** A queue item that can open the full-screen video player instead of audio-only. */
private fun Song.hasMusicVideo(): Boolean =
    localUri == null &&
        !videoId.startsWith("local:") &&
        !videoId.startsWith("webdav:") &&
        !videoId.startsWith("navidrome:")

/**
 * The Music section: YouTube Music browsing, search and an audio-first player, in BitChord's
 * Apple-Music-inspired layout.
 *
 * Ported from BitChord (GPL-3.0); see the Credits section of the README.
 */
@Composable
fun MusicScreen() {
    val context = LocalContext.current
    val viewModel: MusicViewModel = viewModel { MusicViewModel(context) }
    val state by viewModel.state.collectAsState()
    val playback by MusicPlayer.state.collectAsState()
    val liked by MusicLibrary.liked.collectAsState()
    val recents by MusicLibrary.recents.collectAsState()
    val playlists by MusicLibrary.playlists.collectAsState()
    val gemini by MusicGeminiController.state.collectAsState()
    val homeRows by MusicHomeRows.config.collectAsState()
    val party by MusicPartyManager.state.collectAsState()
    val accountViewModel: YouTubeAccountViewModel = viewModel { YouTubeAccountViewModel(context) }
    val account by accountViewModel.state.collectAsState()
    // Read once here: the callbacks below run outside composition.
    val youtubeMusicAccountLabel = stringResource(Res.string.music_ui_youtube_music_account)
    val artistLabel = stringResource(Res.string.music_ui_artist)
    val albumLabel = stringResource(Res.string.music_ui_album)
    val profileState by com.nuvio.app.features.profiles.ProfileRepository.state.collectAsState()
    val profileName = profileState.activeProfile?.name
    var showNowPlaying by remember { mutableStateOf(false) }
    LaunchedEffect(MusicHost.openNowPlayingRequest) {
        if (MusicHost.openNowPlayingRequest > 0 && playback.song != null) {
            showNowPlaying = true
        }
    }
    DisposableEffect(Unit) {
        onDispose { MusicVideoHost.clear() }
    }
    var musicVideo by remember { mutableStateOf<MusicVideoRequest?>(null) }
    var musicVideoMinimized by remember { mutableStateOf(false) }
    var musicVideoMiniPositionMs by remember { mutableStateOf(0L) }
    var miniVideoOffset by remember { mutableStateOf<Offset?>(null) }
    var miniVideoWidthDp by remember { mutableStateOf(220f) }
    LaunchedEffect(musicVideo) {
        if (musicVideo == null) {
            musicVideoMinimized = false
            MusicVideoHost.clear()
        }
    }
    var showSettings by remember { mutableStateOf(false) }
    var showDownloads by remember { mutableStateOf(false) }
    var showParty by remember { mutableStateOf(false) }
    var showWebDav by remember { mutableStateOf(false) }
    var showIntegrations by remember { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }
    var showNavidrome by remember { mutableStateOf(false) }
    var showAccount by remember { mutableStateOf(false) }
    var lastAccountKey by remember { mutableStateOf<String?>(null) }
    var openPlaylistId by remember { mutableStateOf<String?>(null) }
    val topNavFocus = remember { FocusRequester() }
    val openNuvioSidebar = LocalOpenSidebar.current
    val sidebarExpanded = LocalSidebarExpanded.current
    val openPlaylist = playlists.firstOrNull { it.id == openPlaylistId }
    val view = LocalView.current

    /** Opens the video for [song], or returns to Now Playing when it has none. */
    fun showVideoFor(song: Song, startPositionMs: Long, keepMini: Boolean = false) {
        if (!song.hasMusicVideo()) {
            musicVideo = null
            showNowPlaying = true
            return
        }
        MusicPlayer.pause()
        musicVideo = MusicVideoRequest(song, startPositionMs)
        musicVideoMinimized = keepMini
        musicVideoMiniPositionMs = startPositionMs
        showNowPlaying = false
    }

    /**
     * Moves the video screen through the queue. At the end of the queue the audio player
     * decides what happens next (autoplay/stop) and the screen returns to Now Playing.
     */
    fun skipVideo(direction: Int, auto: Boolean = false) {
        val state = MusicPlayer.state.value
        if (state.queue.isEmpty() || state.index !in state.queue.indices) return
        val target = when {
            direction > 0 && state.index < state.queue.lastIndex -> state.index + 1
            direction < 0 && state.index > 0 -> state.index - 1
            direction > 0 && state.repeatMode == RepeatMode.ALL -> 0
            direction < 0 && state.repeatMode == RepeatMode.ALL -> state.queue.lastIndex
            else -> null
        }
        if (target == null) {
            if (auto && direction > 0) {
                MusicPlayer.next(auto = true)
                musicVideo = null
                showNowPlaying = true
            }
            return
        }
        val next = state.queue[target]
        if (next.hasMusicVideo()) {
            MusicPlayer.selectPaused(target)
            showVideoFor(next, 0L, keepMini = musicVideoMinimized)
        } else {
            MusicPlayer.jumpTo(target)
            musicVideo = null
            showNowPlaying = true
        }
    }

    /**
     * Leaves the video screen and returns to the audio player at the same moment, so switching
     * between the video and audio versions does not restart the song.
     */
    fun closeVideo(videoPositionMs: Long) {
        val request = musicVideo ?: return
        val mapped = MusicPlayer.audioPositionForVideo(request.video.videoId, videoPositionMs)
        musicVideo = null
        showNowPlaying = true
        MusicPlayer.jumpTo(MusicPlayer.state.value.index, mapped)
    }

    fun minimizeVideo(videoPositionMs: Long) {
        if (musicVideo == null) return
        musicVideoMiniPositionMs = videoPositionMs
        musicVideoMinimized = true
        showNowPlaying = false
    }

    fun expandVideo(videoPositionMs: Long) {
        if (musicVideo == null) return
        musicVideoMiniPositionMs = videoPositionMs
        musicVideoMinimized = false
    }

    /** Picks a track from the video screen's Up next list and opens its video. */
    fun selectVideoQueue(index: Int) {
        val state = MusicPlayer.state.value
        if (index !in state.queue.indices) return
        val song = state.queue[index]
        if (song.hasMusicVideo()) {
            MusicPlayer.selectPaused(index)
            showVideoFor(song, 0L, keepMini = musicVideoMinimized)
        } else {
            MusicPlayer.jumpTo(index)
            musicVideo = null
            showNowPlaying = true
        }
    }

    // A TV treats a paused video surface as idle and starts its screensaver. Keep the window
    // awake while music is actually playing (or between tracks), while a party is waiting for
    // guests, and let it sleep again when playback stops or the Music section is left.
    val keepAwake = playback.isPlaying || playback.isBuffering || party.running
    DisposableEffect(keepAwake) {
        view.keepScreenOn = keepAwake
        onDispose { view.keepScreenOn = false }
    }

    // Mobile playback keeps running in the background (the foreground service owns the
    // player). Leaving the Music tab, or the app, only saves the queue position.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                MusicPlayer.persistNow()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        MusicPlayer.init(context)
        MusicLibrary.init(context)
        MusicDownloads.init(context)
        MusicGeminiController.init(context)
        MusicHomeRows.init(context)
        accountViewModel.refresh()
    }

    LaunchedEffect(profileState.activeProfile?.profileIndex) {
        MusicHomeRows.useProfile(com.nuvio.app.features.profiles.ProfileRepository.activeProfileId)
        if (state.home.shelves.isNotEmpty()) {
            MusicHomeRows.syncServiceShelves(state.home.shelves)
        }
    }

    LaunchedEffect(state.home.shelves) {
        if (state.home.shelves.isNotEmpty()) {
            MusicHomeRows.syncServiceShelves(state.home.shelves)
        }
    }

    LaunchedEffect(account.checked, account.signedIn, account.accountName, account.accounts) {
        if (!account.checked) return@LaunchedEffect
        val key = buildString {
            append(account.signedIn)
            append(':')
            append(account.accountName)
            account.accounts.firstOrNull { it.selected }?.let {
                append(':')
                append(it.id)
            }
        }
        if (key != lastAccountKey) {
            lastAccountKey = key
            viewModel.reloadForAccount()
        }
    }

    fun play(items: List<Song>, index: Int) {
        if (items.isEmpty()) return
        viewModel.play(items, index.coerceIn(0, items.lastIndex))
        showNowPlaying = true
    }

    fun openShelfItem(shelf: HomeShelf, index: Int) {
        val item = shelf.items.getOrNull(index) ?: return
        if (item.videoId != null) {
            viewModel.playShelfItem(shelf, index)
            showNowPlaying = true
        } else {
            item.browseId?.let { id ->
                viewModel.openDetail(
                    browseId = id,
                    title = item.title,
                    subtitle = item.subtitle,
                    thumbnailUrl = item.thumbnailUrl,
                    type = browseTypeOf(id)
                )
            }
        }
    }

    fun selectTab(tab: MusicTab) {
        openPlaylistId = null
        showIntegrations = false
        showWebDav = false
        showSources = false
        showNavidrome = false
        // Leave a mood/genre detail instead of keeping it as a hidden overlay that would
        // swallow the first Back press on the new tab.
        if (state.explore.selected != null) viewModel.closeMood()
        if (state.detail.open) viewModel.closeDetail()
        viewModel.selectTab(tab)
    }

    val musicOverlayOpen = showNowPlaying || state.detail.open ||
        state.explore.selected != null || openPlaylistId != null ||
        showSettings || showDownloads || showParty || showWebDav ||
        showIntegrations || showSources || showNavidrome || showAccount ||
        musicVideo != null && !musicVideoMinimized

    // Re-anchor focus to the selected top tab whenever the tab changes or an overlay closes.
    // Without this, tapping a tab (which does not move focus) or closing Integrations leaves
    // the remote with no focused control, and the next D-pad press lands somewhere random.
    LaunchedEffect(state.tab, musicOverlayOpen) {
        if (!musicOverlayOpen) {
            kotlinx.coroutines.delay(250)
            runCatching { topNavFocus.requestFocus() }
        }
    }
    // Tapping the art (or artist) in Now Playing opens a detail page; close Now Playing
    // once that page is actually open.
    LaunchedEffect(state.detail.open) {
        if (state.detail.open) showNowPlaying = false
    }

    BackHandler(
        enabled = showNowPlaying || state.detail.open ||
            state.explore.selected != null || openPlaylistId != null
    ) {
        when {
            showNowPlaying -> showNowPlaying = false
            state.detail.open -> viewModel.closeDetail()
            openPlaylistId != null -> openPlaylistId = null
            else -> viewModel.closeMood()
        }
    }

    // Music is a menu page like Live TV/Sports. At its root, Back opens Nuvio's side menu
    // instead of letting the navigation stack pop back to Home. Overlays and the open menu
    // keep their own Back handling.
    BackHandler(enabled = !musicOverlayOpen && !sidebarExpanded) {
        openNuvioSidebar()
    }

    MusicTextTheme {
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }
        val edgePx = with(density) { 14.dp.toPx() }
        val miniBottomClearancePx = with(density) {
            (maxOf(LocalNuvioBottomNavigationOverlayPadding.current, 120.dp) + 8.dp).toPx()
        }
        val maxMiniWidthPx = minOf(
            maxWidthPx - 2 * edgePx,
            (maxHeightPx - miniBottomClearancePx - 2 * edgePx) * 16f / 9f,
        ).coerceAtLeast(with(density) { 160.dp.toPx() })
        val maxMiniWidthDp = with(density) { maxMiniWidthPx.toDp().value }
        val miniWidthPx = with(density) { miniVideoWidthDp.dp.toPx() }
        val miniHeightPx = miniWidthPx * 9f / 16f
        val defaultMiniX = (maxWidthPx - miniWidthPx - edgePx).coerceAtLeast(0f)
        val defaultMiniY = with(density) { 120.dp.toPx() }
        val miniPos = miniVideoOffset ?: Offset(defaultMiniX, defaultMiniY)
        LaunchedEffect(maxWidthPx, maxHeightPx, miniBottomClearancePx, miniVideoWidthDp) {
            miniVideoOffset = (miniVideoOffset ?: Offset(defaultMiniX, defaultMiniY)).let {
                Offset(
                    it.x.coerceIn(0f, (maxWidthPx - miniWidthPx).coerceAtLeast(0f)),
                    it.y.coerceIn(0f, (maxHeightPx - miniHeightPx - miniBottomClearancePx).coerceAtLeast(0f)),
                )
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .background(MusicPalette.background)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            MusicTopNavigation(
                selectedTab = state.tab,
                onTabSelected = ::selectTab,
                playback = playback,
                accountName = account.accountName,
                accountAvatar = account.accounts.firstOrNull { it.selected }?.avatar,
                partyRunning = party.running,
                partyOpen = showParty,
                integrationsOpen = showIntegrations,
                onOpenNowPlaying = { showNowPlaying = true },
                onOpenAccount = { showAccount = true },
                onOpenSettings = { showSettings = true },
                onOpenParty = { showParty = true },
                onOpenIntegrations = {
                    showIntegrations = true
                    showWebDav = false
                    showNavidrome = false
                },
                onOpenNuvioMenu = { openNuvioSidebar() },
                initialFocus = topNavFocus
            )
            if (playback.error != null && playback.song == null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 26.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x33FF6B6B))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = playback.error.orEmpty(),
                        color = MusicPalette.error,
                        fontSize = 13.sp
                    )
                }
            }
            val navBottomPadding = LocalNuvioBottomNavigationOverlayPadding.current
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = navBottomPadding + 24.dp)
            ) {
                when {
                    showWebDav -> MusicWebDavScreen(
                        onClose = {
                            showWebDav = false
                            showIntegrations = true
                        },
                        onPlay = { songs, index ->
                            showWebDav = false
                            showIntegrations = false
                            MusicPlayer.playQueue(songs, index)
                            showNowPlaying = true
                        }
                    )
                    showNavidrome -> MusicNavidromeScreen(
                        onClose = {
                            showNavidrome = false
                            showIntegrations = true
                        },
                        onPlay = { songs, index ->
                            showNavidrome = false
                            showIntegrations = false
                            MusicPlayer.playQueue(songs, index)
                            showNowPlaying = true
                        }
                    )
                    showSources -> MusicSourcesScreen(
                        onClose = {
                            showSources = false
                            showIntegrations = true
                        }
                    )
                    showIntegrations -> MusicIntegrationsScreen(
                        onClose = { showIntegrations = false },
                        onOpenWebDav = {
                            showIntegrations = false
                            showWebDav = true
                        },
                        onOpenNavidrome = {
                            showIntegrations = false
                            showNavidrome = true
                        },
                        onOpenSources = {
                            showIntegrations = false
                            showSources = true
                        }
                    )
                    state.detail.open -> MusicDetailScreen(
                        state = state.detail,
                        onBack = viewModel::closeDetail,
                        onPlay = { index ->
                            viewModel.playDetail(index)
                            showNowPlaying = true
                        },
                        onRetry = {
                            val detail = state.detail
                            viewModel.openDetail(
                                browseId = detail.browseId,
                                title = detail.title,
                                subtitle = detail.subtitle,
                                thumbnailUrl = detail.thumbnailUrl,
                                type = detail.type
                            )
                        },
                        onShelfItemClick = ::openShelfItem,
                        onLoadMore = viewModel::loadMoreDetail
                    )
                    state.tab == MusicTab.HOME -> MusicHomeScreen(
                        state = state.home,
                        recentSongs = recents.map { it.toSong() },
                        localPlaylists = playlists,
                        gemini = gemini,
                        curation = state.home.curation,
                        curationLoading = state.home.curationLoading,
                        homeRows = homeRows,
                        onShelfItemClick = ::openShelfItem,
                        onPlayShelfItem = { shelf, index ->
                            viewModel.playShelfItem(shelf, index)
                            showNowPlaying = true
                        },
                        onPlayCurated = { songs, index -> play(songs, index) },
                        onOpenLocalPlaylist = { openPlaylistId = it.id },
                        onOpenGeminiAlbum = { album ->
                            viewModel.openDetail(
                                browseId = album.id.removePrefix("gemini:album:"),
                                title = album.name,
                                subtitle = album.artist,
                                thumbnailUrl = album.artworkUrl,
                                type = BrowseType.ALBUM
                            )
                        },
                        onHeroDetails = { item ->
                            item.browseId?.let { id ->
                                viewModel.openDetail(
                                    browseId = id,
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    thumbnailUrl = item.thumbnailUrl,
                                    type = browseTypeOf(id)
                                )
                            }
                        },
                        onLoadMore = viewModel::loadMoreHome,
                        onRetry = viewModel::loadHome
                    )
                    state.tab == MusicTab.EXPLORE -> MusicExploreScreen(
                        state = state.explore,
                        curation = state.home.curation,
                        curationLoading = state.home.curationLoading,
                        onMoodClick = viewModel::openMood,
                        onMoodBack = viewModel::closeMood,
                        onShelfItemClick = ::openShelfItem,
                        onPlayShelfItem = { shelf, index ->
                            viewModel.playShelfItem(shelf, index)
                            showNowPlaying = true
                        },
                        onRetry = viewModel::loadExplore,
                        onPlayCurated = { songs, index -> play(songs, index) },
                        onOpenRelease = { release ->
                            viewModel.openDetail(
                                browseId = release.browseId,
                                title = release.title,
                                subtitle = release.artist,
                                thumbnailUrl = release.thumbnailUrl,
                                type = BrowseType.ALBUM
                            )
                        },
                        onOpenArtist = { artist ->
                            viewModel.openDetail(
                                browseId = artist.browseId,
                                title = artist.name,
                                subtitle = artistLabel,
                                thumbnailUrl = artist.thumbnailUrl,
                                type = BrowseType.ARTIST
                            )
                        }
                    )
                    state.tab == MusicTab.LIBRARY && openPlaylist != null ->
                        MusicLocalPlaylistScreen(
                            playlist = openPlaylist,
                            onBack = { openPlaylistId = null },
                            onPlay = { index ->
                                play(openPlaylist.songs.map { it.toSong() }, index)
                            },
                            onDelete = {
                                MusicLibrary.deletePlaylist(openPlaylist.id)
                                openPlaylistId = null
                            }
                        )
                    state.tab == MusicTab.LIBRARY -> MusicLibraryScreen(
                        liked = liked.map { it.toSong() },
                        recents = recents.map { it.toSong() },
                        playlists = playlists,
                        accountName = account.accountName,
                        accountLiked = state.accountLibrary.liked,
                        accountPlaylists = state.accountLibrary.playlists,
                        accountLoading = state.accountLibrary.loading,
                        onRefreshAccount = viewModel::refreshAccountLibrary,
                        onSignIn = {
                            showAccount = true
                            accountViewModel.startSignIn()
                        },
                        onManageAccount = { showAccount = true },
                        onPlayAccountLiked = { index ->
                            play(state.accountLibrary.liked, index)
                        },
                        onOpenAccountPlaylist = { playlist ->
                            viewModel.openDetail(
                                browseId = "account:playlist:${playlist.id}",
                                title = playlist.title,
                                subtitle = youtubeMusicAccountLabel,
                                thumbnailUrl = playlist.thumbnailUrl,
                                type = BrowseType.PLAYLIST
                            )
                        },
                        onCreatePlaylist = {
                            val created = MusicLibrary.createPlaylist()
                            openPlaylistId = created.id
                        },
                        onPlayLiked = { index ->
                            play(liked.map { it.toSong() }, index)
                        },
                        onPlayRecents = { index ->
                            play(recents.map { it.toSong() }, index)
                        },
                        onOpenPlaylist = { playlist -> openPlaylistId = playlist.id },
                        onOpenDownloads = { showDownloads = true },
                        onPlayDownloaded = { songs, index -> play(songs, index) }
                    )
                    state.tab == MusicTab.LOCAL -> MusicLocalScreen(
                        onPlay = { songs, index -> play(songs, index) }
                    )
                    else -> MusicSearchScreen(
                        state = state.search,
                        onQueryChange = viewModel::onSearchQueryChanged,
                        onSubmit = { query ->
                            viewModel.submitSearch(query)
                        },
                        onFilterChange = viewModel::setSearchFilter,
                        onTrackClick = { song ->
                            val songs = state.search.songs
                            val index = songs.indexOfFirst { it.videoId == song.videoId }
                                .coerceAtLeast(0)
                            play(songs, index)
                        },
                        onBrowseClick = { item ->
                            viewModel.openDetail(
                                browseId = item.browseId,
                                title = item.title,
                                subtitle = item.subtitle,
                                thumbnailUrl = item.thumbnailUrl,
                                type = item.type
                            )
                        },
                        onLoadMore = viewModel::loadMoreSearch
                    )
                }
            }
        }
        musicVideo?.let { request ->
            MusicVideoPlayerScreen(
                video = request.video,
                startPositionMs = musicVideoMiniPositionMs,
                minimized = musicVideoMinimized,
                onMinimize = ::minimizeVideo,
                onExpand = ::expandVideo,
                onClose = ::closeVideo,
                onSkip = { skipVideo(it) },
                onSelectQueue = ::selectVideoQueue,
                onEnded = { skipVideo(direction = 1, auto = true) },
                pipGestureModifier = Modifier.pointerInput(maxWidthPx, maxHeightPx, maxMiniWidthDp, miniBottomClearancePx) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newWidthDp = (miniVideoWidthDp * zoom).coerceIn(160f, maxMiniWidthDp)
                        miniVideoWidthDp = newWidthDp
                        val newWidthPx = with(density) { newWidthDp.dp.toPx() }
                        val newHeightPx = newWidthPx * 9f / 16f
                        val base = miniVideoOffset ?: Offset(
                            (maxWidthPx - newWidthPx - edgePx).coerceAtLeast(0f),
                            with(density) { 120.dp.toPx() }
                        )
                        miniVideoOffset = Offset(
                            (base.x + pan.x).coerceIn(0f, (maxWidthPx - newWidthPx).coerceAtLeast(0f)),
                            (base.y + pan.y).coerceIn(
                                0f,
                                (maxHeightPx - newHeightPx - miniBottomClearancePx).coerceAtLeast(0f)
                            ),
                        )
                    }
                },
                modifier = if (musicVideoMinimized) {
                    Modifier
                        .offset { IntOffset(miniPos.x.roundToInt(), miniPos.y.roundToInt()) }
                        .width(miniVideoWidthDp.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.5.dp, MusicPalette.accent.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                } else {
                    Modifier.fillMaxSize()
                }
            )
        }
        }
    }

    if (showNowPlaying) {
        MusicOverlayDialog(onDismiss = { showNowPlaying = false }) {
            MusicNowPlayingScreen(
                onClose = { showNowPlaying = false },
                onOpenSettings = { showSettings = true },
                onOpenMusicVideo = { video, startPositionMs ->
                    showVideoFor(video, startPositionMs)
                },
                onOpenAlbum = { browseId, title, artist, thumbnailUrl ->
                    showNowPlaying = false
                    if (!browseId.isNullOrBlank()) {
                        viewModel.openDetail(
                            browseId = browseId,
                            title = title,
                            subtitle = artist.ifBlank { albumLabel },
                            thumbnailUrl = thumbnailUrl,
                            type = BrowseType.ALBUM
                        )
                    } else {
                        viewModel.openAlbumByQuery(title, artist, thumbnailUrl)
                    }
                },
                onOpenArtist = { browseId, name, thumbnailUrl ->
                    showNowPlaying = false
                    if (!browseId.isNullOrBlank()) {
                        viewModel.openDetail(
                            browseId = browseId,
                            title = name,
                            subtitle = artistLabel,
                            thumbnailUrl = thumbnailUrl,
                            type = BrowseType.ARTIST
                        )
                    } else {
                        viewModel.openArtistByQuery(name, thumbnailUrl)
                    }
                }
            )
        }
    }

    if (showSettings) {
        MusicOverlayDialog(onDismiss = { showSettings = false }) {
            MusicSettingsScreen(onClose = { showSettings = false })
        }
    }

    if (showDownloads) {
        MusicOverlayDialog(onDismiss = { showDownloads = false }) {
            MusicDownloadsScreen(
                onClose = { showDownloads = false },
                onPlay = { songs, index ->
                    showDownloads = false
                    MusicPlayer.playQueue(songs, index)
                    showNowPlaying = true
                }
            )
        }
    }

    if (showParty) {
        MusicOverlayDialog(onDismiss = { showParty = false }) {
            MusicPartyScreen(onClose = { showParty = false })
        }
    }

    if (showAccount) {
        MusicOverlayDialog(onDismiss = { showAccount = false }) {
            MusicAccountScreen(
                account = account,
                profileName = profileName,
                onClose = { showAccount = false },
                onSignIn = { accountViewModel.startSignIn() },
                onCancelSignIn = { accountViewModel.cancelSignIn() },
                onSelectAccount = { id ->
                    accountViewModel.selectAccount(id) { viewModel.reloadForAccount() }
                },
                onAddAccount = { accountViewModel.startSignIn() },
                onSignOut = {
                    accountViewModel.signOut { viewModel.reloadForAccount() }
                },
                onLinkToNuvioOne = accountViewModel::linkToNuvioOne
            )
        }
    }
}

@Composable
private fun MusicTextTheme(content: @Composable () -> Unit) {
    val base = LocalTextStyle.current
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalTextStyle provides base.copy(fontFamily = MusicSans),
        // Music is a dense, editorial section; the TV UI reads better slightly tighter than
        // the host shell. One scale here keeps every screen, row and overlay consistent.
        LocalDensity provides Density(
            density = density.density * MUSIC_UI_SCALE,
            fontScale = density.fontScale
        )
    ) {
        content()
    }
}

@Composable
private fun MusicOverlayDialog(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        MusicTextTheme {
            Box(Modifier.fillMaxSize()) {
                content()
            }
        }
    }
}

@Composable
private fun MusicTopNavigation(
    selectedTab: MusicTab,
    onTabSelected: (MusicTab) -> Unit,
    playback: MusicPlayer.State,
    accountName: String?,
    accountAvatar: String?,
    partyRunning: Boolean,
    partyOpen: Boolean,
    integrationsOpen: Boolean,
    onOpenNowPlaying: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenParty: () -> Unit,
    onOpenIntegrations: () -> Unit,
    onOpenNuvioMenu: () -> Unit,
    initialFocus: FocusRequester
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // A phone has no room for the brand, the page tabs and the account controls on one line.
        // Stack them into two rows there, with the tabs scrolling sideways like the concept.
        // TVs keep the original single-row layout.
        if (maxWidth < 1040.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                Row(
                    Modifier.fillMaxWidth().height(42.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MusicBrand(compact = true)
                    Spacer(Modifier.weight(1f))
                    MusicAccountButton(
                        name = accountName,
                        avatar = accountAvatar,
                        onClick = onOpenAccount
                    )
                    Spacer(Modifier.width(7.dp))
                    MusicSettingsButton(onClick = onOpenSettings)
                }
                Spacer(Modifier.height(5.dp))
                MusicNavigationPill(
                    selectedTab = selectedTab,
                    onTabSelected = onTabSelected,
                    partyRunning = partyRunning,
                    partyOpen = partyOpen,
                    integrationsOpen = integrationsOpen,
                    onOpenParty = onOpenParty,
                    onOpenIntegrations = onOpenIntegrations,
                    onOpenNuvioMenu = onOpenNuvioMenu,
                    initialFocus = initialFocus,
                    scrollable = true,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .padding(horizontal = 24.dp)
            ) {
                MusicBrand(Modifier.align(Alignment.CenterStart))
                MusicNavigationPill(
                    selectedTab = selectedTab,
                    onTabSelected = onTabSelected,
                    partyRunning = partyRunning,
                    partyOpen = partyOpen,
                    integrationsOpen = integrationsOpen,
                    onOpenParty = onOpenParty,
                    onOpenIntegrations = onOpenIntegrations,
                    onOpenNuvioMenu = onOpenNuvioMenu,
                    initialFocus = initialFocus,
                    modifier = Modifier.align(Alignment.Center)
                )
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    MusicAccountButton(
                        name = accountName,
                        avatar = accountAvatar,
                        onClick = onOpenAccount
                    )
                    MusicSettingsButton(onClick = onOpenSettings)
                }
            }
        }
    }
}

@Composable
private fun MusicNavigationPill(
    selectedTab: MusicTab,
    onTabSelected: (MusicTab) -> Unit,
    partyRunning: Boolean,
    partyOpen: Boolean,
    integrationsOpen: Boolean,
    onOpenParty: () -> Unit,
    onOpenIntegrations: () -> Unit,
    onOpenNuvioMenu: () -> Unit,
    initialFocus: FocusRequester,
    scrollable: Boolean = false,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .height(40.dp)
            .clip(shape)
            .background(MusicPalette.surface.copy(alpha = 0.96f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (scrollable) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                MusicNavItems(
                    selectedTab = selectedTab,
                    onTabSelected = onTabSelected,
                    partyRunning = partyRunning,
                    partyOpen = partyOpen,
                    integrationsOpen = integrationsOpen,
                    onOpenParty = onOpenParty,
                    onOpenIntegrations = onOpenIntegrations,
                    onOpenNuvioMenu = onOpenNuvioMenu,
                    initialFocus = initialFocus
                )
            }
        } else {
            MusicNavItems(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
                partyRunning = partyRunning,
                partyOpen = partyOpen,
                integrationsOpen = integrationsOpen,
                onOpenParty = onOpenParty,
                onOpenIntegrations = onOpenIntegrations,
                onOpenNuvioMenu = onOpenNuvioMenu,
                initialFocus = initialFocus
            )
        }
    }
}

@Composable
private fun RowScope.MusicNavItems(
    selectedTab: MusicTab,
    onTabSelected: (MusicTab) -> Unit,
    partyRunning: Boolean,
    partyOpen: Boolean,
    integrationsOpen: Boolean,
    onOpenParty: () -> Unit,
    onOpenIntegrations: () -> Unit,
    onOpenNuvioMenu: () -> Unit,
    initialFocus: FocusRequester
) {
        val overlayOpen = partyOpen || integrationsOpen
        MusicTopNavItem(
            label = stringResource(Res.string.compose_nav_home),
            selected = !overlayOpen && selectedTab == MusicTab.HOME,
            modifier = if (!overlayOpen && selectedTab == MusicTab.HOME) {
                Modifier.focusRequester(initialFocus)
            } else {
                Modifier
            },
            onLeftEdge = onOpenNuvioMenu,
            onClick = { onTabSelected(MusicTab.HOME) }
        )
        MusicTopNavItem(
            label = stringResource(Res.string.music_tab_explore),
            selected = !overlayOpen && selectedTab == MusicTab.EXPLORE,
            modifier = if (!overlayOpen && selectedTab == MusicTab.EXPLORE) {
                Modifier.focusRequester(initialFocus)
            } else {
                Modifier
            },
            onClick = { onTabSelected(MusicTab.EXPLORE) }
        )
        MusicTopNavItem(
            label = stringResource(Res.string.compose_nav_library),
            selected = !overlayOpen && selectedTab == MusicTab.LIBRARY,
            modifier = if (!overlayOpen && selectedTab == MusicTab.LIBRARY) {
                Modifier.focusRequester(initialFocus)
            } else {
                Modifier
            },
            onClick = { onTabSelected(MusicTab.LIBRARY) }
        )
        MusicTopNavItem(
            label = stringResource(Res.string.music_tab_local),
            selected = !overlayOpen && selectedTab == MusicTab.LOCAL,
            modifier = if (!overlayOpen && selectedTab == MusicTab.LOCAL) {
                Modifier.focusRequester(initialFocus)
            } else {
                Modifier
            },
            onClick = { onTabSelected(MusicTab.LOCAL) }
        )
        MusicTopNavItem(
            label = stringResource(Res.string.compose_nav_search),
            selected = !overlayOpen && selectedTab == MusicTab.SEARCH,
            modifier = if (!overlayOpen && selectedTab == MusicTab.SEARCH) {
                Modifier.focusRequester(initialFocus)
            } else {
                Modifier
            },
            onClick = { onTabSelected(MusicTab.SEARCH) }
        )
        MusicTopNavItem(
            label = stringResource(Res.string.music_tab_party),
            selected = partyOpen || partyRunning,
            live = partyRunning,
            onClick = onOpenParty
        )
        MusicTopNavItem(
            label = stringResource(Res.string.compose_settings_page_integrations),
            selected = integrationsOpen,
            onClick = onOpenIntegrations
        )
}

@Composable
private fun MusicBrand(modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.nuvio_one_icon),
            contentDescription = null,
            modifier = Modifier
                .size(if (compact) 30.dp else 34.dp)
                .clip(CircleShape)
        )
        Spacer(Modifier.width(if (compact) 7.dp else 9.dp))
        Text(
            text = "Nuvio One Music",
            color = MusicPalette.text,
            fontSize = if (compact) 12.5.sp else 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.2.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun MusicTopNavItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    live: Boolean = false,
    onLeftEdge: (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    val leftEdgeModifier = if (onLeftEdge == null) {
        Modifier
    } else {
        Modifier.onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                onLeftEdge()
                true
            } else {
                false
            }
        }
    }
    Row(
        modifier
            .height(32.dp)
            .then(leftEdgeModifier)
            .clip(shape)
            .background(
                when {
                    selected -> Color.White
                    focused -> MusicPalette.surfaceFocused
                    else -> Color.Transparent
                }
            )
            .border(
                width = 2.dp,
                color = when {
                    focused && selected -> MusicPalette.accent
                    focused -> Color.White
                    else -> Color.Transparent
                },
                shape = shape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (live) {
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(MusicPalette.accentTeal)
            )
        }
        Text(
            text = label,
            color = when {
                selected -> MusicPalette.background
                focused -> MusicPalette.text
                else -> MusicPalette.textSecondary
            },
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun NowPlayingChip(
    playback: MusicPlayer.State,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .width(185.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(if (focused) MusicPalette.surfaceFocused else MusicPalette.surface)
            .border(
                2.dp,
                if (focused) Color.White else MusicPalette.border,
                RoundedCornerShape(50)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(start = 5.dp, end = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MusicArtwork(
            url = playback.song?.thumbnailUrl,
            modifier = Modifier.size(23.dp),
            cornerRadius = 12.dp,
            iconSize = 10.dp,
            artworkPx = 180
        )
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = playback.song?.title.orEmpty(),
                color = MusicPalette.text,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = playback.song?.artist.orEmpty(),
                color = MusicPalette.textSecondary,
                fontSize = 7.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        MusicPlayingIndicator(
            playing = playback.isPlaying,
            modifier = Modifier.width(13.dp)
        )
    }
}

@Composable
private fun NowPlayingMiniButton(
    playback: MusicPlayer.State,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MusicPalette.surface)
            .border(
                2.dp,
                if (focused) Color.White else MusicPalette.border,
                CircleShape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
    ) {
        MusicArtwork(
            url = playback.song?.thumbnailUrl,
            modifier = Modifier.fillMaxSize().padding(2.dp),
            cornerRadius = 17.dp,
            iconSize = 14.dp,
            artworkPx = 180
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(14.dp)
                .clip(CircleShape)
                .background(MusicPalette.accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = stringResource(Res.string.music_ui_now_playing),
                tint = MusicPalette.background,
                modifier = Modifier.size(9.dp)
            )
        }
    }
}

@Composable
private fun MusicSettingsButton(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(if (focused) MusicPalette.surfaceFocused else MusicPalette.surface)
            .border(
                2.dp,
                if (focused) MusicPalette.text else MusicPalette.border,
                CircleShape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Settings,
            contentDescription = stringResource(Res.string.music_settings_title),
            tint = if (focused) MusicPalette.text else MusicPalette.textSecondary,
            modifier = Modifier.size(17.dp)
        )
    }
}

@Composable
private fun MusicAccountButton(name: String?, avatar: String?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(if (focused) MusicPalette.surfaceFocused else MusicPalette.surface)
            .border(
                2.dp,
                if (focused) MusicPalette.text else MusicPalette.border,
                CircleShape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (avatar != null) {
            AsyncImage(
                model = avatar,
                contentDescription = name ?: stringResource(Res.string.music_ui_youtube_music_account),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        } else {
            Icon(
                Icons.Default.AccountCircle,
                contentDescription = if (name == null) stringResource(Res.string.music_ui_sign_in_to_youtube_music) else name,
                tint = if (focused) MusicPalette.text else MusicPalette.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** The browse id prefixes YouTube Music uses, for cards that don't carry a type. */
private fun browseTypeOf(browseId: String): BrowseType = when {
    browseId.startsWith("mpreb") -> BrowseType.ALBUM
    browseId.startsWith("UC") -> BrowseType.ARTIST
    browseId.startsWith("VL") || browseId.startsWith("PL") ||
        browseId.startsWith("OLAK5uy") -> BrowseType.PLAYLIST
    else -> BrowseType.OTHER
}
