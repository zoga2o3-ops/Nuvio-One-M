package com.nuvio.app.music

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import com.nuvio.app.music.engine.data.model.HomeShelf
import com.nuvio.app.music.engine.data.model.MoodGenre
import com.nuvio.app.music.engine.data.model.ShelfItem
import com.nuvio.app.music.engine.data.model.Song
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MusicExploreScreen(
    state: MusicExploreState,
    curation: MusicCuration = MusicCuration(),
    curationLoading: Boolean = false,
    onMoodClick: (MoodGenre) -> Unit,
    onMoodBack: () -> Unit,
    onShelfItemClick: (HomeShelf, Int) -> Unit,
    onPlayShelfItem: (HomeShelf, Int) -> Unit,
    onRetry: () -> Unit,
    onPlayCurated: (List<Song>, Int) -> Unit = { _, _ -> },
    onOpenRelease: (MusicRelease) -> Unit = {},
    onOpenArtist: (MusicRelatedArtist) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val selected = state.selected
    if (selected == null) {
        when {
            state.loading && state.sections.isEmpty() && curation.isEmpty -> MusicLoading(modifier.fillMaxSize())
            state.error != null && state.sections.isEmpty() && curation.isEmpty ->
                MusicErrorState(state.error, onRetry, modifier)
            state.sections.isEmpty() && curation.isEmpty -> MusicEmptyState(
                title = stringResource(Res.string.music_ui_nothing_to_explore),
                message = stringResource(Res.string.music_ui_the_music_service_didn_t_send_any_moods_or_genres),
                modifier = modifier
            )
            else -> MoodList(
                state = state,
                curation = curation,
                curationLoading = curationLoading,
                onMoodClick = onMoodClick,
                onPlayCurated = onPlayCurated,
                onOpenRelease = onOpenRelease,
                onOpenArtist = onOpenArtist,
                modifier = modifier
            )
        }
    } else {
        MoodDetail(
            state = state,
            onBack = onMoodBack,
            onRetry = { selected?.let(onMoodClick) },
            onShelfItemClick = onShelfItemClick,
            onPlayShelfItem = onPlayShelfItem,
            modifier = modifier
        )
    }
}

@Composable
private fun MoodList(
    state: MusicExploreState,
    curation: MusicCuration,
    curationLoading: Boolean,
    onMoodClick: (MoodGenre) -> Unit,
    onPlayCurated: (List<Song>, Int) -> Unit,
    onOpenRelease: (MusicRelease) -> Unit,
    onOpenArtist: (MusicRelatedArtist) -> Unit,
    modifier: Modifier = Modifier
) {
    val firstMoodFocus = remember { FocusRequester() }
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val columns = if (screenWidth < 600) 2 else 3
    val gridPadding = if (screenWidth < 600) 16.dp else 26.dp
    val artistLabel = stringResource(Res.string.music_ui_artist)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 22.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item(key = "explore-header") {
            ExploreHeader()
        }
        curation.discoverWeekly?.let { weekly ->
            item(key = "curated-discover-weekly") {
                val title = stringResource(Res.string.music_discover_weekly)
                MusicShelfRow(
                    shelf = curatedSongShelf(title, weekly.tracks, weekly.artists.joinToString(" · ")),
                    compact = screenWidth < 600,
                    onItemClick = { _, index -> onPlayCurated(weekly.tracks, index) }
                )
            }
        }
        if (curation.releaseRadar.isNotEmpty()) {
            item(key = "curated-release-radar") {
                val title = stringResource(Res.string.music_discover_release_radar)
                MusicShelfRow(
                    shelf = HomeShelf(
                        title = title,
                        items = curation.releaseRadar.map { release ->
                            ShelfItem(
                                title = release.title,
                                subtitle = listOfNotNull(release.artist, release.year?.toString()).joinToString(" · "),
                                thumbnailUrl = release.thumbnailUrl,
                                videoId = release.videoId,
                                browseId = release.browseId,
                            )
                        }
                    ),
                    compact = screenWidth < 600,
                    onItemClick = { _, index -> curation.releaseRadar.getOrNull(index)?.let(onOpenRelease) }
                )
            }
        }
        if (curation.fansAlsoLike.isNotEmpty()) {
            item(key = "curated-fans-also-like") {
                val title = stringResource(Res.string.music_discover_fans_also_like)
                MusicShelfRow(
                    shelf = HomeShelf(
                        title = title,
                        items = curation.fansAlsoLike.map { artist ->
                            ShelfItem(
                                title = artist.name,
                                subtitle = artistLabel,
                                thumbnailUrl = artist.thumbnailUrl,
                                videoId = null,
                                browseId = artist.browseId,
                            )
                        }
                    ),
                    compact = screenWidth < 600,
                    onItemClick = { _, index -> curation.fansAlsoLike.getOrNull(index)?.let(onOpenArtist) }
                )
            }
        }
        if (curationLoading) {
            item(key = "curated-discover-loading") {
                Text(
                    text = stringResource(Res.string.music_discover_refreshing),
                    color = MusicPalette.textMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 26.dp)
                )
            }
        }
        itemsIndexed(
            items = state.sections,
            key = { index, section -> "${section.title}#$index" }
        ) { sectionIndex, section ->
            Column {
                // The mockup goes straight from the screen head into the mood grid. Keep the
                // label only when the server actually returns several groups.
                if (state.sections.size > 1) {
                    MusicSectionHeader(title = section.title)
                    Spacer(Modifier.height(10.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    section.items.chunked(columns).forEachIndexed { rowIndex, row ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = gridPadding),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            row.forEachIndexed { columnIndex, mood ->
                                val absoluteIndex = rowIndex * columns + columnIndex
                                MoodTile(
                                    mood = mood,
                                    modifier = Modifier
                                        .weight(1f)
                                        .then(
                                            if (sectionIndex == 0 && absoluteIndex == 0) {
                                                Modifier.focusRequester(firstMoodFocus)
                                            } else {
                                                Modifier
                                            }
                                        ),
                                    onClick = { onMoodClick(mood) }
                                )
                            }
                            repeat(columns - row.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
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
private fun ExploreHeader() {
    val compact = LocalConfiguration.current.screenWidthDp < 600
    Column(
        Modifier.fillMaxWidth().padding(
            start = if (compact) 24.dp else 26.dp,
            end = if (compact) 14.dp else 26.dp
        )
    ) {
        Text(
            text = stringResource(Res.string.music_tab_explore),
            color = MusicPalette.text,
            fontFamily = MusicDisplay,
            fontSize = if (compact) 30.sp else 40.sp,
            lineHeight = if (compact) 32.sp else 43.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = stringResource(Res.string.music_ui_moods_moments_and_genres_for_every_listening_room),
            color = MusicPalette.textSecondary,
            fontSize = if (compact) 13.sp else 14.sp
        )
    }
}

@Composable
private fun MoodTile(
    mood: MoodGenre,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = tween(140),
        label = stringResource(Res.string.music_ui_moodtilescale)
    )
    val base = mood.stripeColor?.let { Color(it) } ?: MusicPalette.accentPurple
    // YouTube's stripe colours range from near-black to pale yellow. Keep every tile
    // recognisably its own hue, but pull the whole set toward ink so the title can stay white.
    val light = lerp(base, MusicPalette.background, 0.46f)
    val dark = lerp(base, MusicPalette.background, 0.64f)
    val shape = RoundedCornerShape(15.dp)
    val background = Brush.linearGradient(listOf(light, dark))
    Box(
        modifier
            .height(109.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(background)
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) Color.White else Color.White.copy(alpha = 0.12f),
                shape = shape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(15.dp)
    ) {
        // The oversized ring is the mockup's quiet visual signature for a mood tile.
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 34.dp, y = 40.dp)
                .size(124.dp)
                .clip(CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
        )
        Text(
            text = mood.title,
            color = Color.White,
            fontFamily = MusicDisplay,
            fontSize = 22.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.TopStart)
        )
        Text(
            text = stringResource(Res.string.music_ui_browse),
            color = Color.White.copy(alpha = 0.68f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.align(Alignment.BottomStart)
        )
    }
}

@Composable
private fun MoodDetail(
    state: MusicExploreState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onShelfItemClick: (HomeShelf, Int) -> Unit,
    onPlayShelfItem: (HomeShelf, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val mood = state.selected ?: return
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(mood.browseId) {
        runCatching { backFocus.requestFocus() }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 22.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item(key = "mood-header") {
            Row(
                Modifier.padding(horizontal = 26.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                var focused by remember { mutableStateOf(false) }
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (focused) Color.White else MusicPalette.surfaceRaised)
                        .border(
                            2.dp,
                            if (focused) Color.White else MusicPalette.border,
                            RoundedCornerShape(50)
                        )
                        .focusRequester(backFocus)
                        .onFocusChanged { focused = it.isFocused }
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.action_back),
                        tint = if (focused) MusicPalette.background else MusicPalette.text,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = mood.title,
                    color = MusicPalette.text,
                    fontFamily = MusicDisplay,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
        when {
            state.selectedLoading -> item(key = "mood-loading") {
                Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                    MusicLoading(Modifier.fillMaxSize())
                }
            }
            state.selectedError != null -> item(key = "mood-error") {
                MusicErrorState(
                    message = state.selectedError,
                    onRetry = onRetry,
                    modifier = Modifier.height(260.dp)
                )
            }
            else -> itemsIndexed(
                items = state.selectedShelves,
                key = { index, shelf -> "${shelf.title}#$index" }
            ) { _, shelf ->
                MusicShelfRow(
                    shelf = shelf,
                    onItemClick = { _, index -> onShelfItemClick(shelf, index) }
                )
            }
        }
    }
}
