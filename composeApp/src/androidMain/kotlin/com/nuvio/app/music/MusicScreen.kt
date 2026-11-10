package com.nuvio.app.music

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
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

/** One density scale for the whole Music section. */
private const val MUSIC_UI_SCALE = 0.88f

private data class MusicVideoRequest(val video: Song, val startPositionMs: Long)

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
    val party by MusicPartyManager.state.collectAsState()
    val accountViewModel: YouTubeAccountViewModel = viewModel { YouTubeAccountViewModel(context) }
    val account by accountViewModel.state.collectAsState()
    val profileState by com.nuvio.app.features.profiles.ProfileRepository.state.collectAsState()
    val profileName = profileState.activeProfile?.name
    var showNowPlaying by remember { mutableStateOf(false) }
    LaunchedEffect(MusicHost.openNowPlayingRequest) {
        if (MusicHost.openNowPlayingRequest > 0 && playback.song != null) {
            showNowPlaying = true
        }
    }
    var musicVideo by remember { mutableStateOf<MusicVideoRequest?>(null) }
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
        accountViewModel.refresh()
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
        showIntegrations || showSources || showNavidrome || showAccount || musicVideo != null

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
                        onShelfItemClick = ::openShelfItem,
                        onPlayShelfItem = { shelf, index ->
                            viewModel.playShelfItem(shelf, index)
                            showNowPlaying = true
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
                        onMoodClick = viewModel::openMood,
                        onMoodBack = viewModel::closeMood,
                        onShelfItemClick = ::openShelfItem,
                        onPlayShelfItem = { shelf, index ->
                            viewModel.playShelfItem(shelf, index)
                            showNowPlaying = true
                        },
                        onRetry = viewModel::loadExplore
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
                                subtitle = "YouTube Music account",
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
    }

    if (showNowPlaying) {
        MusicOverlayDialog(onDismiss = { showNowPlaying = false }) {
            MusicNowPlayingScreen(
                onClose = { showNowPlaying = false },
                onOpenSettings = { showSettings = true },
                onOpenMusicVideo = { video, startPositionMs ->
                    MusicPlayer.pause()
                    musicVideo = MusicVideoRequest(video, startPositionMs)
                    showNowPlaying = false
                },
                onOpenAlbum = { browseId, title, artist, thumbnailUrl ->
                    showNowPlaying = false
                    if (!browseId.isNullOrBlank()) {
                        viewModel.openDetail(
                            browseId = browseId,
                            title = title,
                            subtitle = artist.ifBlank { "Album" },
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
                            subtitle = "Artist",
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

    musicVideo?.let { request ->
        MusicOverlayDialog(onDismiss = { musicVideo = null }) {
            MusicVideoPlayerScreen(
                video = request.video,
                startPositionMs = request.startPositionMs,
                onClose = {
                    musicVideo = null
                    showNowPlaying = true
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
            label = "Home",
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
            label = "Explore",
            selected = !overlayOpen && selectedTab == MusicTab.EXPLORE,
            modifier = if (!overlayOpen && selectedTab == MusicTab.EXPLORE) {
                Modifier.focusRequester(initialFocus)
            } else {
                Modifier
            },
            onClick = { onTabSelected(MusicTab.EXPLORE) }
        )
        MusicTopNavItem(
            label = "Library",
            selected = !overlayOpen && selectedTab == MusicTab.LIBRARY,
            modifier = if (!overlayOpen && selectedTab == MusicTab.LIBRARY) {
                Modifier.focusRequester(initialFocus)
            } else {
                Modifier
            },
            onClick = { onTabSelected(MusicTab.LIBRARY) }
        )
        MusicTopNavItem(
            label = "Local",
            selected = !overlayOpen && selectedTab == MusicTab.LOCAL,
            modifier = if (!overlayOpen && selectedTab == MusicTab.LOCAL) {
                Modifier.focusRequester(initialFocus)
            } else {
                Modifier
            },
            onClick = { onTabSelected(MusicTab.LOCAL) }
        )
        MusicTopNavItem(
            label = "Search",
            selected = !overlayOpen && selectedTab == MusicTab.SEARCH,
            modifier = if (!overlayOpen && selectedTab == MusicTab.SEARCH) {
                Modifier.focusRequester(initialFocus)
            } else {
                Modifier
            },
            onClick = { onTabSelected(MusicTab.SEARCH) }
        )
        MusicTopNavItem(
            label = "Party",
            selected = partyOpen || partyRunning,
            live = partyRunning,
            onClick = onOpenParty
        )
        MusicTopNavItem(
            label = "Integrations",
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
                contentDescription = "Now playing",
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
            contentDescription = "Music settings",
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
                contentDescription = name ?: "YouTube Music account",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        } else {
            Icon(
                Icons.Default.AccountCircle,
                contentDescription = if (name == null) "Sign in to YouTube Music" else name,
                tint = if (focused) MusicPalette.text else MusicPalette.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** The browse id prefixes YouTube Music uses, for cards that don't carry a type. */
private fun browseTypeOf(browseId: String): BrowseType = when {
    browseId.startsWith("MPREb") -> BrowseType.ALBUM
    browseId.startsWith("UC") -> BrowseType.ARTIST
    browseId.startsWith("VL") || browseId.startsWith("PL") ||
        browseId.startsWith("OLAK5uy_") -> BrowseType.PLAYLIST
    else -> BrowseType.OTHER
}
