package com.nuvio.app.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import com.nuvio.app.music.engine.data.model.HomeShelf
import com.nuvio.app.music.engine.data.model.ShelfItem
import com.nuvio.app.music.engine.data.model.ShelfLayout
import com.nuvio.app.music.engine.data.model.Song
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MusicHomeScreen(
    state: MusicHomeState,
    recentSongs: List<Song> = emptyList(),
    localPlaylists: List<StoredPlaylist> = emptyList(),
    gemini: MusicGeminiUiState = MusicGeminiUiState(),
    curation: MusicCuration = MusicCuration(),
    curationLoading: Boolean = false,
    homeRows: MusicHomeRowConfig = MusicHomeRowConfig(),
    onShelfItemClick: (HomeShelf, Int) -> Unit,
    onPlayShelfItem: (HomeShelf, Int) -> Unit,
    onPlayCurated: (List<Song>, Int) -> Unit = { _, _ -> },
    onOpenLocalPlaylist: (StoredPlaylist) -> Unit = {},
    onOpenGeminiAlbum: (GeminiAlbum) -> Unit = {},
    onHeroDetails: (ShelfItem) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasPersonal = recentSongs.isNotEmpty() || localPlaylists.isNotEmpty() || !curation.isEmpty || curationLoading ||
        (gemini.enabled && (gemini.tracks.isNotEmpty() || gemini.albums.isNotEmpty()))
    when {
        state.loading && state.shelves.isEmpty() && !hasPersonal -> MusicLoading(modifier.fillMaxSize())
        state.error != null && state.shelves.isEmpty() && !hasPersonal ->
            MusicErrorState(state.error, onRetry, modifier)
        state.shelves.isEmpty() && !hasPersonal -> MusicEmptyState(
            title = stringResource(Res.string.music_ui_no_music_yet),
            message = stringResource(Res.string.music_ui_the_music_service_didn_t_send_anything_back_check_the_connec),
            modifier = modifier
        )
        else -> MusicHomeFeed(
            state = state,
            recentSongs = recentSongs,
            localPlaylists = localPlaylists,
            gemini = gemini,
            curation = curation,
            curationLoading = curationLoading,
            homeRows = homeRows,
            onShelfItemClick = onShelfItemClick,
            onPlayShelfItem = onPlayShelfItem,
            onPlayCurated = onPlayCurated,
            onOpenLocalPlaylist = onOpenLocalPlaylist,
            onOpenGeminiAlbum = onOpenGeminiAlbum,
            onHeroDetails = onHeroDetails,
            onLoadMore = onLoadMore,
            modifier = modifier
        )
    }
}

@Composable
private fun MusicHomeFeed(
    state: MusicHomeState,
    recentSongs: List<Song>,
    localPlaylists: List<StoredPlaylist>,
    gemini: MusicGeminiUiState,
    curation: MusicCuration,
    curationLoading: Boolean,
    homeRows: MusicHomeRowConfig,
    onShelfItemClick: (HomeShelf, Int) -> Unit,
    onPlayShelfItem: (HomeShelf, Int) -> Unit,
    onPlayCurated: (List<Song>, Int) -> Unit,
    onOpenLocalPlaylist: (StoredPlaylist) -> Unit,
    onOpenGeminiAlbum: (GeminiAlbum) -> Unit,
    onHeroDetails: (ShelfItem) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, state.shelves.size) {
        if (nearEnd) onLoadMore()
    }

    val serviceShelfById = state.shelves.associateBy(::serviceHomeRowId)
    val visibleServiceShelves = homeRows.visibleServiceShelves()
    val heroShelf = visibleServiceShelves.firstNotNullOfOrNull { serviceShelfById[it.id] }
    val heroItem = heroShelf?.items?.firstOrNull()
    val compact = LocalConfiguration.current.screenWidthDp < 600
    val short = LocalConfiguration.current.screenHeightDp < 560
    val jumpBackTitle = stringResource(Res.string.music_ui_jump_back_in)
    val yourPlaylistsTitle = stringResource(Res.string.music_home_your_playlists)
    val playlistTrackLabels = localPlaylists.map {
        stringResource(Res.string.music_home_playlist_tracks, it.songs.size)
    }
    val geminiPicksTitle = stringResource(Res.string.music_home_gemini_picks)
    val geminiCheckedNote = stringResource(Res.string.music_home_checked_youtube_music)
    val albumsForYouTitle = stringResource(Res.string.music_home_albums_for_you)
    val geminiBuildingText = stringResource(Res.string.music_home_gemini_building)
    val curationBuildingText = stringResource(Res.string.music_discover_refreshing)
    val quickPicksTitle = stringResource(Res.string.music_home_quick_picks)
    val onRepeatTitle = stringResource(Res.string.music_home_on_repeat)
    val musicVideosTitle = stringResource(Res.string.music_ui_music_video)
    val forgottenTitle = stringResource(Res.string.music_home_forgotten_favorites)
    val dailyMixTitles = curation.dailyMixes.mapIndexed { index, _ ->
        stringResource(Res.string.music_home_daily_mix_number, index + 1)
    }
    val becauseTitles = curation.because.map { feed ->
        stringResource(Res.string.music_home_because_you_listened_to, feed.title)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = if (compact || short) 12.dp else 22.dp,
            bottom = 18.dp
        ),
        verticalArrangement = Arrangement.spacedBy(if (compact || short) 14.dp else 18.dp)
    ) {
        if (heroShelf != null && heroItem != null) {
            item(key = "hero") {
                MusicHero(
                    shelf = heroShelf,
                    item = heroItem,
                    compact = compact,
                    short = short,
                    onClick = { onHeroDetails(heroItem) },
                    onPlay = {
                        if (heroItem.videoId != null) onPlayShelfItem(heroShelf, 0)
                        else onShelfItemClick(heroShelf, 0)
                    }
                )
            }
        }
        homeRows.visibleRows().forEach { row ->
            when (row) {
                MusicHomeRow.RECENTS -> if (recentSongs.isNotEmpty()) {
            item(key = "personal-recents") {
                MusicShelfRow(
                    shelf = HomeShelf(
                        title = jumpBackTitle,
                        items = recentSongs.map {
                            ShelfItem(
                                title = it.title,
                                subtitle = it.artist,
                                thumbnailUrl = it.thumbnailUrl,
                                videoId = it.videoId,
                                browseId = null,
                                isVideo = it.isVideo
                            )
                        },
                        layout = ShelfLayout.LIST
                    ),
                    compact = compact,
                    onItemClick = { _, index -> onPlayCurated(recentSongs, index) }
                )
            }
        }
                MusicHomeRow.PLAYLISTS -> if (localPlaylists.isNotEmpty()) {
            item(key = "personal-playlists") {
                MusicShelfRow(
                    shelf = HomeShelf(
                        title = yourPlaylistsTitle,
                        items = localPlaylists.mapIndexed { index, playlist ->
                            ShelfItem(
                                title = playlist.name,
                                subtitle = playlistTrackLabels[index],
                                thumbnailUrl = playlist.songs.firstOrNull()?.thumbnailUrl,
                                videoId = null,
                                browseId = playlist.id
                            )
                        }
                    ),
                    compact = compact,
                    onItemClick = { _, index ->
                        localPlaylists.getOrNull(index)?.let(onOpenLocalPlaylist)
                    }
                )
            }
        }
                MusicHomeRow.GEMINI_TRACKS -> if (gemini.enabled && gemini.tracks.isNotEmpty()) {
            item(key = "gemini-tracks") {
                MusicShelfRow(
                    shelf = HomeShelf(
                        title = geminiPicksTitle,
                        subtitle = geminiCheckedNote,
                        items = gemini.tracks.map {
                            ShelfItem(
                                title = it.title,
                                subtitle = it.artist,
                                thumbnailUrl = it.thumbnailUrl,
                                videoId = it.videoId,
                                browseId = null,
                                isVideo = it.isVideo
                            )
                        },
                        layout = ShelfLayout.LIST
                    ),
                    compact = compact,
                    onItemClick = { _, index -> onPlayCurated(gemini.tracks, index) }
                )
            }
        }
                MusicHomeRow.GEMINI_ALBUMS -> if (gemini.enabled && gemini.albums.isNotEmpty()) {
            item(key = "gemini-albums") {
                MusicShelfRow(
                    shelf = HomeShelf(
                        title = albumsForYouTitle,
                        items = gemini.albums.map { album ->
                            ShelfItem(
                                title = album.name,
                                subtitle = album.artist,
                                thumbnailUrl = album.artworkUrl,
                                videoId = null,
                                browseId = album.id
                            )
                        }
                    ),
                    compact = compact,
                    onItemClick = { _, index ->
                        gemini.albums.getOrNull(index)?.let(onOpenGeminiAlbum)
                    }
                )
                    }
                }
                MusicHomeRow.QUICK_PICKS -> if (curation.quickPicks.isNotEmpty()) {
                    item(key = "curated-quick-picks") {
                        MusicShelfRow(
                            shelf = curatedSongShelf(quickPicksTitle, curation.quickPicks),
                            compact = compact,
                            onItemClick = { _, index -> onPlayCurated(curation.quickPicks, index) }
                        )
                    }
                }
                MusicHomeRow.DAILY_MIXES -> curation.dailyMixes.forEachIndexed { index, mix ->
                    item(key = "curated-daily-mix-${mix.id}") {
                        MusicShelfRow(
                            shelf = curatedSongShelf(dailyMixTitles[index], mix.tracks, mix.artists.joinToString(" · ")),
                            compact = compact,
                            onItemClick = { _, trackIndex -> onPlayCurated(mix.tracks, trackIndex) }
                        )
                    }
                }
                MusicHomeRow.MUSIC_VIDEOS -> if (curation.musicVideos.isNotEmpty()) {
                    item(key = "curated-music-videos") {
                        MusicShelfRow(
                            shelf = curatedSongShelf(musicVideosTitle, curation.musicVideos),
                            compact = compact,
                            onItemClick = { _, index -> onPlayCurated(curation.musicVideos, index) }
                        )
                    }
                }
                MusicHomeRow.ON_REPEAT -> if (curation.onRepeat.isNotEmpty()) {
                    item(key = "curated-on-repeat") {
                        MusicShelfRow(
                            shelf = curatedSongShelf(onRepeatTitle, curation.onRepeat),
                            compact = compact,
                            onItemClick = { _, index -> onPlayCurated(curation.onRepeat, index) }
                        )
                    }
                }
                MusicHomeRow.BECAUSE -> curation.because.forEachIndexed { index, feed ->
                    item(key = "curated-because-$index") {
                        MusicShelfRow(
                            shelf = curatedSongShelf(becauseTitles[index], feed.tracks),
                            compact = compact,
                            onItemClick = { _, trackIndex -> onPlayCurated(feed.tracks, trackIndex) }
                        )
                    }
                }
                MusicHomeRow.FORGOTTEN -> if (curation.forgotten.isNotEmpty()) {
                    item(key = "curated-forgotten") {
                        MusicShelfRow(
                            shelf = curatedSongShelf(forgottenTitle, curation.forgotten),
                            compact = compact,
                            onItemClick = { _, index -> onPlayCurated(curation.forgotten, index) }
                        )
                    }
                }
            }
        }
        if (gemini.generating) {
            item(key = "gemini-building") {
                Text(
                    text = geminiBuildingText,
                    color = MusicPalette.textMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 26.dp)
                )
            }
        }
        visibleServiceShelves.forEach { row ->
            val shelf = serviceShelfById[row.id] ?: return@forEach
            item(key = "service-home-${row.id}") {
                MusicShelfRow(
                    shelf = shelf,
                    compact = compact,
                    onItemClick = { _, index -> onShelfItemClick(shelf, index) }
                )
            }
        }
        if (state.loadingMore) {
            item(key = "loading-more") {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = MusicPalette.accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
        if (curationLoading) {
            item(key = "curation-building") {
                Text(
                    text = curationBuildingText,
                    color = MusicPalette.textMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 26.dp)
                )
            }
        }
    }
}

private fun curatedSongShelf(title: String, songs: List<Song>, subtitle: String = "") = HomeShelf(
    title = title,
    subtitle = subtitle,
    items = songs.map {
        ShelfItem(
            title = it.title,
            subtitle = it.artist,
            thumbnailUrl = it.thumbnailUrl,
            videoId = it.videoId,
            browseId = null,
            isVideo = it.isVideo
        )
    }
)

@Composable
private fun MusicHero(
    shelf: HomeShelf,
    item: ShelfItem,
    compact: Boolean,
    short: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit
) {
    val colors = musicAuroraColors(item.thumbnailUrl ?: item.title)
    val (first, _) = colors
    val shelfSubtitle = shelf.subtitle.withoutMusicServiceBrand()
    val heroSubtitle = item.subtitle.withoutMusicServiceBrand()

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = if (compact) 14.dp else 26.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        first.copy(alpha = 0.34f),
                        MusicPalette.surface,
                        MusicPalette.background
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(19.dp))
            .padding(horizontal = 18.dp, vertical = if (short) 10.dp else 18.dp)
    ) {
        if (compact) {
            // A phone stacks the artwork above the copy, like the concept's narrow layout.
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                HeroArtwork(
                    colors = colors,
                    item = item,
                    artSize = 220.dp,
                    vinylSize = 194.dp,
                    vinylOffsetX = 48.dp,
                    vinylOffsetY = 6.dp,
                    cornerRadius = 14.dp
                )
                Spacer(Modifier.height(16.dp))
                HeroCopy(
                    item = item,
                    shelfSubtitle = shelfSubtitle,
                    heroSubtitle = heroSubtitle,
                    titleSize = 31.sp,
                    titleLineHeight = 32.sp,
                    onPlay = onPlay,
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (short) 12.dp else 21.dp)
            ) {
                // The mockup splits the hero 1.02fr / 0.98fr, with the record centred in the
                // artwork column instead of hugging the left edge.
                HeroArtwork(
                    colors = colors,
                    item = item,
                    artSize = if (short) 140.dp else 212.dp,
                    vinylSize = if (short) 124.dp else 187.dp,
                    vinylOffsetX = if (short) 34.dp else 55.dp,
                    vinylOffsetY = if (short) 5.dp else 7.dp,
                    cornerRadius = 14.dp,
                    modifier = Modifier.weight(1.02f)
                )
                HeroCopy(
                    item = item,
                    shelfSubtitle = shelfSubtitle,
                    heroSubtitle = heroSubtitle,
                    titleSize = if (short) 30.sp else 44.sp,
                    titleLineHeight = if (short) 32.sp else 42.sp,
                    onPlay = onPlay,
                    onClick = onClick,
                    modifier = Modifier.weight(0.98f)
                )
            }
        }
    }
}

@Composable
private fun HeroArtwork(
    colors: Pair<Color, Color>,
    item: ShelfItem,
    artSize: androidx.compose.ui.unit.Dp,
    vinylSize: androidx.compose.ui.unit.Dp,
    vinylOffsetX: androidx.compose.ui.unit.Dp,
    vinylOffsetY: androidx.compose.ui.unit.Dp,
    cornerRadius: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(artSize),
            contentAlignment = Alignment.Center
        ) {
            MusicVinyl(
                colors = colors,
                modifier = Modifier
                    .size(vinylSize)
                    .offset(x = vinylOffsetX, y = vinylOffsetY),
                spinning = true
            )
            MusicArtwork(
                url = item.thumbnailUrl,
                modifier = Modifier.size(artSize),
                cornerRadius = cornerRadius,
                iconSize = 52.dp,
                artworkPx = 1080
            )
        }
    }
}

@Composable
private fun HeroCopy(
    item: ShelfItem,
    shelfSubtitle: String,
    heroSubtitle: String,
    titleSize: androidx.compose.ui.unit.TextUnit,
    titleLineHeight: androidx.compose.ui.unit.TextUnit,
    onPlay: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MusicTag(
                text = if (item.isVideo) stringResource(Res.string.player_video_settings_title) else stringResource(Res.string.music_ui_featured),
                color = MusicPalette.accentTeal
            )
            if (shelfSubtitle.isNotBlank()) {
                MusicTag(shelfSubtitle)
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            text = item.title,
            color = MusicPalette.text,
            fontFamily = MusicDisplay,
            fontSize = titleSize,
            lineHeight = titleLineHeight,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (heroSubtitle.isNotBlank()) {
            Spacer(Modifier.height(7.dp))
            Text(
                text = heroSubtitle,
                color = MusicPalette.textSecondary,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(19.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HeroAction(
                icon = Icons.Default.PlayArrow,
                label = if (item.videoId != null) stringResource(Res.string.action_play) else stringResource(Res.string.youtube_ui_open),
                primary = true,
                onClick = onPlay
            )
            if (item.browseId != null) {
                HeroAction(
                    icon = Icons.Default.ChevronRight,
                    label = stringResource(Res.string.settings_meta_details),
                    primary = false,
                    onClick = onClick
                )
            }
        }
    }
}

@Composable
private fun HeroAction(
    icon: ImageVector,
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    val background = when {
        focused -> MusicPalette.accent
        primary -> MusicPalette.text
        else -> Color.Transparent
    }
    val contentColor = when {
        focused -> MusicPalette.background
        primary -> MusicPalette.background
        else -> MusicPalette.text
    }
    Row(
        modifier
            .clip(shape)
            .background(background)
            .border(
                1.dp,
                if (focused) Color.White else Color.White.copy(alpha = 0.18f),
                shape
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
            tint = contentColor,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
