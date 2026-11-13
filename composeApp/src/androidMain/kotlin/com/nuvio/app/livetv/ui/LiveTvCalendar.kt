package com.nuvio.app.livetv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.livetv.LiveTv
import com.nuvio.app.livetv.calendar.CalendarItem
import com.nuvio.app.livetv.calendar.MobileCalendar
import com.nuvio.app.livetv.sports.ChannelMatch
import com.nuvio.app.livetv.sports.GameState
import com.nuvio.app.livetv.sports.Side
import com.nuvio.app.livetv.sports.SportsCatalog
import com.nuvio.app.livetv.sports.SportsEvent
import com.nuvio.app.livetv.sports.SportsFollows
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class CalendarTab(val label: String) { UPCOMING("Upcoming"), RECENT("Recently aired"), TODAY("Airing today"), FOLLOWING("Following") }

private enum class CalendarFilter(val label: String) { ALL("All"), SHOWS("Shows"), MOVIES("Movies"), SPORTS("Sports") }

/** One row of the Calendar: a show episode, or a game of a team you follow. */
private sealed interface CalendarRow {
    val at: Long
    data class Episode(val item: CalendarItem) : CalendarRow {
        override val at: Long get() = item.airMs
    }
    data class Game(val event: SportsEvent, val channels: List<ChannelMatch>) : CalendarRow {
        override val at: Long get() = event.startMs
    }
}

/**
 * The Calendar: when the next episodes of your shows come out, what just aired, what's on today,
 * and the games of the teams you follow in Sports.
 */
@Composable
fun CalendarPage(
    calendar: MobileCalendar,
    use24Hour: Boolean,
    now: Long = System.currentTimeMillis(),
    onPlay: (com.nuvio.app.features.livetv.LiveTvPlayRequest) -> Unit = {},
) {
    val state by calendar.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(CalendarTab.UPCOMING) }
    var filter by remember { mutableStateOf(CalendarFilter.ALL) }
    var opened by remember { mutableStateOf<CalendarItem?>(null) }
    var pickedGame by remember { mutableStateOf<SportsEvent?>(null) }

    val context = LocalContext.current.applicationContext
    val live = remember(context) { LiveTv.get(context) }
    val sports = remember(live) { live.sports }
    val knownGames by sports.known.collectAsStateWithLifecycle()
    val follows by sports.follows.collectAsStateWithLifecycle()
    val matches by sports.matches.collectAsStateWithLifecycle()
    val sportsSettings by sports.settings.collectAsStateWithLifecycle()
    val liveSettings by live.prefs.settings.collectAsStateWithLifecycle(initialValue = com.nuvio.app.livetv.model.LiveTvSettings())

    LaunchedEffect(Unit) { calendar.refresh() }
    LaunchedEffect(tab) { if (tab == CalendarTab.TODAY) calendar.loadToday() }
    // Keeps scores and channel matching fresh while the Calendar is open.
    DisposableEffect(sports) {
        sports.setViewing(true)
        onDispose { sports.setViewing(false) }
    }
    val timeFmt = remember(use24Hour) { SimpleDateFormat(if (use24Hour) "HH:mm" else "h:mm a", Locale.getDefault()) }
    val dayFmt = remember { SimpleDateFormat("EEEE d MMMM", Locale.getDefault()) }

    val games = remember(knownGames, follows, tab, now, liveSettings.calendarSports) {
        if (!liveSettings.calendarSports || follows.teams.isEmpty()) emptyList()
        else knownGames.filter { ev ->
            val mine = ev.sides.any { follows.followsTeam(ev.leagueId, it.team.id) }
            if (!mine) return@filter false
            when (tab) {
                CalendarTab.UPCOMING -> ev.state != GameState.POST &&
                    ev.startMs >= now - 6 * 3_600_000L && ev.startMs <= now + 90 * 86_400_000L
                CalendarTab.RECENT -> ev.startMs < now && ev.startMs >= now - 21 * 86_400_000L &&
                    (ev.state == GameState.POST || ev.startMs < now - 3 * 3_600_000L)
                CalendarTab.TODAY -> dayKey(ev.startMs) == dayKey(now)
                CalendarTab.FOLLOWING -> false
            }
        }.sortedBy { it.startMs }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CalendarTab.entries.forEach { t ->
                val shape = RoundedCornerShape(8.dp)
                Box(
                    Modifier.clip(shape)
                        .background(if (tab == t) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .clickable { tab = t }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) { Text(t.label, maxLines = 1, style = MaterialTheme.typography.labelLarge) }
            }
        }
        if (tab != CalendarTab.FOLLOWING) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CalendarFilter.entries.forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text(f.label) },
                    )
                }
            }
        }
        val list = when (tab) {
            CalendarTab.UPCOMING -> state.upcoming
            CalendarTab.RECENT -> state.recent
            CalendarTab.TODAY -> state.today
            CalendarTab.FOLLOWING -> emptyList()
        }
        val shownEpisodes = remember(list, filter) {
            list.filter {
                when (filter) {
                    CalendarFilter.ALL -> true
                    CalendarFilter.SHOWS -> !it.isMovie
                    CalendarFilter.MOVIES -> it.isMovie
                    CalendarFilter.SPORTS -> false
                }
            }
        }
        val rows: List<CalendarRow> = remember(shownEpisodes, games, matches, filter) {
            val out = ArrayList<CalendarRow>(shownEpisodes.size + games.size)
            shownEpisodes.forEach { out += CalendarRow.Episode(it) }
            if (filter == CalendarFilter.ALL || filter == CalendarFilter.SPORTS) {
                games.forEach { out += CalendarRow.Game(it, matches[it.id].orEmpty()) }
            }
            out.sortedBy { it.at }
        }
        val busy = if (tab == CalendarTab.TODAY) state.todayLoading else state.loading
        val openGame: (SportsEvent) -> Unit = { ev ->
            val channels = matches[ev.id].orEmpty()
            when {
                channels.isEmpty() -> android.widget.Toast.makeText(
                    context, "No channel found for this game yet.", android.widget.Toast.LENGTH_SHORT
                ).show()
                channels.size == 1 -> onPlay(
                    liveTvRequest(channels[0].channel, listOfNotNull(channels[0].program), System.currentTimeMillis())
                )
                else -> pickedGame = ev
            }
        }
        if (tab == CalendarTab.FOLLOWING) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item { Text("Shows with a bell", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
                if (state.followed.isEmpty()) item { Text("Open an episode and tap Follow to add a show here.", color = LocalContentColor.current.copy(alpha = 0.6f)) }
                items(state.followed.entries.toList(), key = { "f" + it.key }) { (imdb, name) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { calendar.toggleFollow(imdb, name) }) { Text("Unfollow") }
                    }
                }
                item { Text("Taken off the Calendar", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp)) }
                if (state.hidden.isEmpty()) item { Text("Nothing hidden.", color = LocalContentColor.current.copy(alpha = 0.6f)) }
                items(state.hidden.entries.toList(), key = { "h" + it.key }) { (imdb, name) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { calendar.unhide(imdb) }) { Text("Show again") }
                    }
                }
            }
        } else Box(Modifier.weight(1f).fillMaxWidth()) {
            if (rows.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    if (busy) CircularProgressIndicator()
                    else Text(
                        when {
                            filter == CalendarFilter.SPORTS && follows.teams.isEmpty() ->
                                "Follow teams in Sports to see their games here."
                            filter == CalendarFilter.SPORTS && tab == CalendarTab.TODAY -> "No games for your teams today."
                            filter == CalendarFilter.SPORTS && tab == CalendarTab.RECENT -> "No games for your teams lately."
                            filter == CalendarFilter.SPORTS -> "No upcoming games for your teams."
                            tab == CalendarTab.TODAY -> "Nothing found airing today."
                            state.note != null -> state.note!!
                            tab == CalendarTab.RECENT -> "Nothing aired lately from your shows."
                            else -> "No new episodes coming up from your shows."
                        },
                        color = LocalContentColor.current.copy(alpha = 0.65f),
                    )
                }
            } else {
                val grouped = remember(rows) { rows.groupBy { dayKey(it.at) } }
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (busy) item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            Text("  Looking up your shows (${state.shows})…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    grouped.forEach { (key, day) ->
                        item(key = "h$key") {
                            Text(
                                dayTitle(key, dayFmt),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                        items(
                            day,
                            key = { row ->
                                when (row) {
                                    is CalendarRow.Episode ->
                                        row.item.showName + row.item.season + "x" + row.item.episode + row.item.title + row.item.airMs
                                    is CalendarRow.Game -> "game:" + row.event.id
                                }
                            },
                        ) { row ->
                            when (row) {
                                is CalendarRow.Episode -> EpisodeRow(row.item, timeFmt) { opened = row.item }
                                is CalendarRow.Game -> CalendarGameCard(
                                    ev = row.event,
                                    channels = row.channels,
                                    follows = follows,
                                    hideScores = sportsSettings.hideScores,
                                    timeFmt = timeFmt,
                                    now = now,
                                    onClick = { openGame(row.event) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    pickedGame?.let { ev ->
        val channels = matches[ev.id].orEmpty().distinctBy { it.channel.key }
        AlertDialog(
            onDismissRequest = { pickedGame = null },
            title = { Text(ev.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    Text("Watch on", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    channels.forEach { m ->
                        TextButton(onClick = {
                            pickedGame = null
                            onPlay(liveTvRequest(m.channel, listOfNotNull(m.program), System.currentTimeMillis()))
                        }) { Text(m.channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickedGame = null }) { Text("Close") } },
        )
    }
    opened?.let { e ->
        val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
        val live = remember { com.nuvio.app.livetv.LiveTv.get(context) }
        var onTv by remember(e) { mutableStateOf<List<com.nuvio.app.livetv.calendar.OnMyTv>>(emptyList()) }
        var recorded by remember(e) { mutableStateOf(setOf<String>()) }
        LaunchedEffect(e) { onTv = calendar.onMyTv(e.showName) }
        AlertDialog(
            onDismissRequest = { opened = null },
            title = { Text(e.showName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (e.isMovie) "Movie  —  ${e.title.orEmpty()}" else "S${e.season} · E${e.episode}" + (e.title?.let { "  —  $it" } ?: ""), style = MaterialTheme.typography.titleSmall)
                    Text(
                        SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date(e.airMs)) + "  " + timeFmt.format(Date(e.airMs)) +
                            (e.network?.let { "  ·  $it" } ?: "") + if (e.streaming) " (streaming)" else "",
                        style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.7f),
                    )
                    e.runtime?.let { Text("$it min", style = MaterialTheme.typography.bodySmall) }
                    e.summary?.takeIf { it.isNotBlank() && !it.equals("null", true) }?.let { Text(android.text.Html.fromHtml(it, 0).toString().trim(), style = MaterialTheme.typography.bodyMedium, maxLines = 8, overflow = TextOverflow.Ellipsis) }
                    if (onTv.isNotEmpty()) {
                        Text("On my TV", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
                        onTv.forEach { t ->
                            val id = t.channel.key + t.program.startMs
                            val scheduled = id in recorded || live.scheduler.isScheduled(t.channel.key, t.program)
                            val onNow = System.currentTimeMillis() in t.program.startMs until t.program.stopMs
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(t.channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        (if (onNow) "Now  " else SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(t.program.startMs)) + "  ") + timeFmt.format(Date(t.program.startMs)),
                                        style = MaterialTheme.typography.labelSmall, color = LocalContentColor.current.copy(alpha = 0.65f),
                                    )
                                }
                                if (onNow) TextButton(onClick = {
                                    com.nuvio.app.livetv.player.LiveTvReconnect.arm(t.channel.url, t.channel.key)
                                    onPlay(liveTvRequest(t.channel, listOf(t.program), System.currentTimeMillis())); opened = null
                                }) { Text("Watch") }
                                else TextButton(enabled = !scheduled, onClick = {
                                    live.scheduler.add(t.channel, t.program); recorded = recorded + id
                                }) { Text(if (scheduled) "Set" else "Record") }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { opened = null }) { Text("Close") } },
            dismissButton = {
                val imdb = e.imdb
                if (imdb != null) Row {
                    val following = imdb in state.followed
                    TextButton(onClick = { calendar.toggleFollow(imdb, e.showName); opened = null }) { Text(if (following) "Unfollow" else "Follow") }
                    TextButton(onClick = { calendar.hide(imdb, e.showName); opened = null }) { Text("Hide show") }
                }
            },
        )
    }
}

@Composable
private fun CalendarGameCard(
    ev: SportsEvent,
    channels: List<ChannelMatch>,
    follows: SportsFollows,
    hideScores: Boolean,
    timeFmt: SimpleDateFormat,
    now: Long,
    onClick: () -> Unit,
) {
    val league = remember(ev.leagueId) { SportsCatalog.league(ev.leagueId) }
    val sport = league?.sport
    val live = ev.state == GameState.LIVE
    val sides = if (sport != null) ev.ordered(sport) else ev.sides
    val showScore = ev.hasScore && !hideScores && ev.state != GameState.PRE
    val progress = ev.progress ?: league?.minutes?.takeIf { live }?.let { minutes ->
        ((now - ev.startMs).toFloat() / (minutes * 60_000f)).coerceIn(0f, 1f)
    }

    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1B1B1F))
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            league?.let {
                Text(
                    it.short,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(it.color)).padding(horizontal = 5.dp, vertical = 2.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                listOfNotNull(league?.name, ev.subtitle ?: ev.title.takeIf { sides.size != 2 }).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (live) {
                Spacer(Modifier.width(6.dp))
                LiveBadge()
            } else {
                Text(
                    if (ev.hasTime) timeFmt.format(Date(ev.startMs)) else "Today",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.78f),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        if (sides.size == 2) {
            sides.forEachIndexed { i, side ->
                if (i > 0) Spacer(Modifier.height(3.dp))
                TeamBand(side, showScore, follows.followsTeam(ev.leagueId, side.team.id))
            }
        } else {
            Text(ev.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            ev.subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (live && progress != null) {
            Spacer(Modifier.height(7.dp))
            Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f))) {
                Box(
                    Modifier.fillMaxWidth(progress).fillMaxHeight().background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                gameNote(ev, timeFmt, now),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                when {
                    channels.isEmpty() -> "No channel"
                    channels.size == 1 -> "1 channel"
                    else -> "${channels.size} channels"
                },
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = if (channels.isEmpty()) 0.38f else 0.65f),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun TeamBand(side: Side, showScore: Boolean, followed: Boolean) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(teamBandBrush(side.team))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!side.team.logo.isNullOrBlank()) {
            AsyncImage(side.team.logo, null, Modifier.size(26.dp), contentScale = ContentScale.Fit)
        } else {
            Spacer(Modifier.size(26.dp))
        }
        Text(
            side.team.name,
            Modifier.padding(start = 8.dp).weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (side.winner) FontWeight.Bold else FontWeight.SemiBold,
        )
        if (followed) {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Filled.Star,
                contentDescription = "Followed team",
                tint = Color(0xFFFFC107),
                modifier = Modifier.size(14.dp),
            )
        }
        if (showScore) {
            Text(side.score.orEmpty(), fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

private fun gameNote(ev: SportsEvent, timeFmt: SimpleDateFormat, now: Long): String = when (ev.state) {
    GameState.LIVE -> ev.status?.takeIf { it.isNotBlank() } ?: "LIVE"
    GameState.POST -> ev.status?.takeIf { it.isNotBlank() } ?: "Final"
    GameState.OFF -> ev.status?.takeIf { it.isNotBlank() } ?: timeFmt.format(Date(ev.startMs))
    GameState.PRE -> if (!ev.hasTime) "Today" else relativeStart(ev.startMs, now)
}

private fun relativeStart(startMs: Long, now: Long): String {
    val diff = startMs - now
    if (diff <= 0) return "Now"
    val mins = diff / 60_000
    if (mins < 60) return "in $mins min"
    val hours = diff / 3_600_000
    if (hours < 24) return "in $hours h"
    val days = diff / 86_400_000
    val rest = (diff % 86_400_000) / 3_600_000
    return "in $days ${if (days == 1L) "day" else "days"}" + if (rest > 0) " $rest h" else ""
}

@Composable
private fun EpisodeRow(e: CalendarItem, timeFmt: SimpleDateFormat, onClick: () -> Unit) {    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF1B1B1F)).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 84.dp, height = 52.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF26262B))) {
            if (!e.image.isNullOrBlank()) AsyncImage(e.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(e.showName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (e.isMovie) "Movie  ·  ${e.title.orEmpty()}" else "S${e.season} · E${e.episode}" + (e.title?.let { "  $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = LocalContentColor.current.copy(alpha = 0.75f),
            )
            Text(
                timeFmt.format(Date(e.airMs)) + (e.network?.let { "  ·  $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall, maxLines = 1, color = LocalContentColor.current.copy(alpha = 0.55f),
            )
        }
    }
}

private fun dayKey(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ms
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun dayTitle(day: Long, fmt: SimpleDateFormat): String {
    val today = dayKey(System.currentTimeMillis())
    val diff = Math.round((day - today) / 86_400_000.0).toInt()
    return when (diff) {
        0 -> "Today"
        1 -> "Tomorrow"
        -1 -> "Yesterday"
        else -> fmt.format(Date(day))
    }
}
