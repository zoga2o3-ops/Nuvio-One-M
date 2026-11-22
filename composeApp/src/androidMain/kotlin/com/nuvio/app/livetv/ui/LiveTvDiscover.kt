package com.nuvio.app.livetv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.features.livetv.LiveTvPlayRequest
import com.nuvio.app.livetv.LiveTv
import com.nuvio.app.livetv.model.LiveChannel
import com.nuvio.app.livetv.model.LiveTvSettings
import com.nuvio.app.livetv.ondemand.VodItem
import com.nuvio.app.livetv.ondemand.VodKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Channels you can open from Search and Home: not hidden, and never from a PIN-locked group (those
 * stay inside the Live TV page, where the PIN is asked for).
 */
@Composable
private fun rememberOpenChannels(live: LiveTv): List<LiveChannel> {
    // The channel list loads when Live TV is first touched; Search and Home may be the first to.
    LaunchedEffect(Unit) { live.repository.ensureLoaded() }
    val all by live.store.displayChannels.collectAsStateWithLifecycle()
    val user by live.userState.collectAsStateWithLifecycle()
    val settings by live.prefs.settings.collectAsStateWithLifecycle(com.nuvio.app.livetv.model.LiveTvSettings())
    return remember(all, user, settings.parentalPin, settings.lockAdultContent) {
        all.filter {
            it.sourceId !in user.hiddenPlaylists &&
            it.key !in user.hiddenChannels && it.groupId !in user.hiddenGroups && !user.hidesByGroupTag(it, user.keptChannelKeys) && it.groupId !in user.lockedGroups &&
                !(settings.parentalPin.length >= 4 && settings.lockAdultContent && com.nuvio.app.livetv.parental.ParentalControls.isAdult(it.group))
        }
    }
}

private fun open(live: LiveTv, c: LiveChannel, onPlay: (LiveTvPlayRequest) -> Unit) {
    com.nuvio.app.livetv.player.LiveTvReconnect.arm(c.url, c.key)
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { live.prefs.recordWatched(c.key) }
    onPlay(liveTvRequest(c, live.repository.programs.value[c.key], System.currentTimeMillis()))
}

@Composable
private fun Tile(title: String, subtitle: String?, image: String?, compact: Boolean = false, onClick: () -> Unit) {
    val width = if (compact) 84.dp else 112.dp
    val height = if (compact) 48.dp else 72.dp
    Column(Modifier.width(width).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(width, height).clip(RoundedCornerShape(10.dp)).background(Color(0xFF26262B))) {
            if (!image.isNullOrBlank()) AsyncImage(image, null, Modifier.fillMaxWidth().padding(if (compact) 5.dp else 8.dp), contentScale = ContentScale.Fit)
        }
        Text(title, Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.92f), textAlign = TextAlign.Center)
        if (subtitle != null) Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = Color.Gray, textAlign = TextAlign.Center)
    }
}

/** Live TV and On Demand matches under the search box. Draws nothing when there are none. */
@Composable
fun LiveTvSearchSection(query: String, onPlay: (LiveTvPlayRequest) -> Unit) {
    val context = LocalContext.current.applicationContext
    val live = remember { LiveTv.get(context) }
    val open = rememberOpenChannels(live)
    val text = query.trim()
    if (text.length < 2) return
    val channels = remember(open, text) { open.filter { it.name.contains(text, ignoreCase = true) }.take(30) }

    // Shows airing now or in the next few hours whose title matches (the guide for that window is read here if Live TV hasn't).
    val programs by live.repository.programs.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        val t = System.currentTimeMillis()
        runCatching { live.repository.ensureRange(t - 30 * 60_000L, t + 6 * 3_600_000L) }
    }
    val showHits = remember(open, programs, text, channels) {
        val t = System.currentTimeMillis()
        val seen = channels.map { it.key }.toSet()
        open.asSequence().filter { it.key !in seen }.mapNotNull { c ->
            programs[c.key]?.firstOrNull { it.stopMs > t && it.startMs < t + 6 * 3_600_000L && it.title.contains(text, ignoreCase = true) }?.let { c to it }
        }.take(20).toList()
    }

    var movies by remember { mutableStateOf<List<VodItem>>(emptyList()) }
    var openedVod by remember { mutableStateOf<VodItem?>(null) }
    val scope = rememberCoroutineScope()
    // The catalog loads in the background the first time; search again when it has changed.
    val catalogVersion by live.onDemand.version.collectAsStateWithLifecycle()
    LaunchedEffect(text, catalogVersion) {
        delay(300)
        movies = runCatching {
            // Movies and series together, so a show can be opened and an episode picked.
            (live.onDemand.search(VodKind.MOVIE, text, emptySet()).take(20) + live.onDemand.search(VodKind.SERIES, text, emptySet()).take(20))
        }.getOrDefault(emptyList())
    }

    if (showHits.isNotEmpty()) {
        val timeFmt = remember { java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()) }
        Column(Modifier.padding(bottom = 12.dp)) {
            Text(stringResource(Res.string.live_tv_ui_on_tv), Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(showHits, key = { it.first.key }) { (c, p) ->
                    val onNow = System.currentTimeMillis() >= p.startMs
                    Tile(p.title, (if (onNow) stringResource(Res.string.live_tv_epg_now) else timeFmt.format(java.util.Date(p.startMs))) + "  ·  " + c.name, p.icon ?: c.logo) { open(live, c, onPlay) }
                }
            }
        }
    }
    if (channels.isNotEmpty()) {
        Column(Modifier.padding(bottom = 12.dp)) {
            Text("Live TV", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(channels, key = { it.key }) { c -> Tile(c.name, c.group, c.logo) { open(live, c, onPlay) } }
            }
        }
    }
    openedVod?.let { OnDemandDetails(live.onDemand, it, onPlay = { r -> onPlay(r) }, onDismiss = { openedVod = null }) }
    if (movies.isNotEmpty()) {
        Column(Modifier.padding(bottom = 12.dp)) {
            Text("On Demand", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(movies, key = { it.uid }) { m ->
                    Tile(m.name, listOfNotNull(m.year?.toString(), if (m.kind == VodKind.SERIES) "Series" else null).joinToString("  ·  ").ifBlank { null }, m.icon) { openedVod = m }
                }
            }
        }
    }
}

/** A row of the channels you watch most lately (favorites first) for the Home screen. */
@Composable
fun LiveTvHomeRow(onPlay: (LiveTvPlayRequest) -> Unit) {
    val context = LocalContext.current.applicationContext
    val live = remember { LiveTv.get(context) }
    val open = rememberOpenChannels(live)
    val user by live.userState.collectAsStateWithLifecycle()
    val settings by live.prefs.settings.collectAsStateWithLifecycle(com.nuvio.app.livetv.model.LiveTvSettings())
    val profileId by live.profile.activeProfileId.collectAsStateWithLifecycle()
    val programs by live.repository.programs.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()
    LaunchedEffect(Unit) {
        runCatching { live.repository.ensureRange(now - 30 * 60_000L, now + 6 * 3_600_000L) }
    }
    var resume by remember { mutableStateOf<List<com.nuvio.app.livetv.ondemand.VodResume.Entry>>(emptyList()) }
    LaunchedEffect(Unit) { resume = com.nuvio.app.livetv.ondemand.VodResume.unfinished(context).take(15) }
    if (settings.onDemandHomeRowEnabled && resume.isNotEmpty()) Column(Modifier.padding(bottom = 12.dp)) {
        Text(stringResource(Res.string.live_tv_ui_continue_watching_u00b7_on_demand), Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(resume, key = { it.key }) { e ->
                val left = if (e.durationMs > 0) "${((e.durationMs - e.positionMs) / 60_000L).coerceAtLeast(1)} min left" else null
                Tile(e.title, left, e.poster) {
                    onPlay(LiveTvPlayRequest(url = e.url, title = e.title, poster = e.poster, channelKey = e.key, headers = e.headers, startPositionMs = e.positionMs))
                }
            }
        }
    }
    val homeRowOn = settings.homeRowEnabled && profileId !in settings.homeRowHiddenProfiles
    if (!homeRowOn) return
    val list = remember(open, user, settings, programs) {
        val byKey = open.associateBy { it.key }
        val sourceKeys = when (settings.homeRowSource) {
            "recent" -> user.recent
            "favorites", "" -> user.favorites.ifEmpty { user.recent }
            else -> user.customGroups.firstOrNull { it.id == settings.homeRowSource }?.channelKeys.orEmpty()
        }
        val channels = sourceKeys.mapNotNull { byKey[it] }.distinct()
        val t = System.currentTimeMillis()
        val sorted = when (settings.homeRowSort) {
            "number" -> channels.sortedBy { it.number }
            "ending" -> channels.sortedWith(compareBy { c -> programs[c.key]?.firstOrNull { it.stopMs > t }?.stopMs ?: Long.MAX_VALUE })
            else -> channels
        }
        val visible = if (settings.homeRowHidePlaceholders && programs.isNotEmpty()) {
            sorted.filter { c ->
                programs[c.key]?.any { it.isLive(t) && !it.title.isPlaceholderTitle() } == true
            }
        } else sorted
        if (settings.homeRowLimit > 0) visible.take(settings.homeRowLimit) else visible
    }
    if (list.isEmpty()) return
    val clock = remember(settings.use24HourClock) {
        java.text.SimpleDateFormat(if (settings.use24HourClock) "HH:mm" else "h:mm a", java.util.Locale.getDefault())
    }
    Column(Modifier.padding(bottom = 12.dp)) {
        val liveTvTitle = stringResource(Res.string.live_tv_title)
        Text(
            settings.homeRowTitle.ifBlank { liveTvTitle },
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.key }) { c ->
                val next = programs[c.key]?.firstOrNull { it.startMs > now }
                val subtitle = if (!settings.homeRowCompact && settings.homeRowShowNext && next != null) {
                    "Next: ${clock.format(java.util.Date(next.startMs))} ${next.displayTitle()}"
                } else null
                Tile(c.name, subtitle, c.logo, compact = settings.homeRowCompact) { open(live, c, onPlay) }
            }
        }
    }
}

private fun String.isPlaceholderTitle(): Boolean =
    isBlank() || equals("Programming", ignoreCase = true) || equals("No information", ignoreCase = true)

/** New episodes of your shows: what aired in the last few days and what's coming up. */
@Composable
fun LiveTvHomeEpisodes(onOpenShow: (imdb: String, name: String, image: String?) -> Unit) {
    val context = LocalContext.current.applicationContext
    val live = remember { LiveTv.get(context) }
    val settings by live.prefs.settings.collectAsStateWithLifecycle(initialValue = LiveTvSettings())
    if (!settings.calendarNewEpisodeCards) return
    val state by live.calendar.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { live.calendar.refresh() }
    val list = remember(state.upcoming, state.recent) {
        val t = System.currentTimeMillis()
        (state.recent.filter { it.airMs >= t - 3 * 86_400_000L } + state.upcoming.filter { it.airMs <= t + 3 * 86_400_000L })
            .filter { it.imdb != null }.sortedBy { it.airMs }.take(15)
    }
    if (list.isEmpty()) return
    val now = System.currentTimeMillis()
    val dayFmt = remember { java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault()) }
    Column(Modifier.padding(bottom = 12.dp)) {
        Text(stringResource(Res.string.live_tv_ui_new_episodes), Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.showName + it.season + "x" + it.episode }) { e ->
                val day = (if (e.airMs <= now) "Aired " else "") + dayFmt.format(java.util.Date(e.airMs))
                Tile(e.showName, "S${e.season} E${e.episode}  \u00B7  $day", e.image) { onOpenShow(e.imdb!!, e.showName, e.image) }
            }
        }
    }
}
