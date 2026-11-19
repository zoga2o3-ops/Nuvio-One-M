package com.nuvio.app.livetv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.livetv.model.LiveChannel
import com.nuvio.app.livetv.sports.ChannelMatch
import com.nuvio.app.livetv.sports.GameState
import com.nuvio.app.livetv.sports.Side
import com.nuvio.app.livetv.sports.Sport
import com.nuvio.app.livetv.sports.SportsCatalog
import com.nuvio.app.livetv.sports.SportsEvent
import com.nuvio.app.livetv.sports.SportsRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.getString

private val CardBg = Color(0xFF1B1B1F)
private val Accent = Color(0xFFE53935)

/** Sports: today's games with scores, and which of your channels show each one. */
@Composable
fun SportsPage(
    repo: SportsRepository,
    hasChannels: Boolean,
    use24Hour: Boolean,
    day: Int,
    mode: String,
    onMode: (String) -> Unit,
    sport: Sport?,
    onSport: (Sport?) -> Unit,
    onWatch: (LiveChannel) -> Unit,
    onRecord: (SportsEvent, ChannelMatch) -> Unit,
) {
    // Keeps the scores fresh while this page is open.
    DisposableEffect(repo) {
        repo.setViewing(true)
        onDispose { repo.setViewing(false) }
    }
    val events by repo.events.collectAsStateWithLifecycle()
    val matches by repo.matches.collectAsStateWithLifecycle()
    val loading by repo.loading.collectAsStateWithLifecycle()
    val problem by repo.problem.collectAsStateWithLifecycle()
    val settings by repo.settings.collectAsStateWithLifecycle()
    val follows by repo.follows.collectAsStateWithLifecycle()
    var opened by remember { mutableStateOf<SportsEvent?>(null) }
    var showScores by remember { mutableStateOf(false) }
    var pickTeams by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<SportsEvent?>(null) }
    var fixFor by remember { mutableStateOf<SportsEvent?>(null) }
    var windowChannel by remember { mutableStateOf<LiveChannel?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val reminderToast = stringResource(Res.string.live_tv_ui_you_ll_get_a_reminder)
    val sportsLabel = stringResource(Res.string.yt_tab_sports)
    val notifications = rememberLiveTvNotificationPermission()

    val from = remember(day) { repo.dayStart(day) }
    val to = remember(day) { repo.dayStart(day + 1) }
    val ofDay = remember(events, from, to) { events.filter { it.startMs in from until to } }
    val sportsToday = remember(ofDay) {
        ofDay.mapNotNull { SportsCatalog.league(it.leagueId)?.sport }.distinct().sortedBy { it.ordinal }
    }
    val sportChoices = remember(sportsToday, sport) {
        (sportsToday + listOfNotNull(sport)).distinct().sortedBy { it.ordinal }
    }
    val shown = remember(ofDay, sport) {
        ofDay.filter { sport == null || SportsCatalog.league(it.leagueId)?.sport == sport }
    }
    fun isFavourite(ev: SportsEvent): Boolean =
        ev.leagueId in follows.leagues || ev.leagueId in follows.pins ||
            ev.sides.any { follows.followsTeam(ev.leagueId, it.team.id) } ||
            follows.words.any { it.isNotBlank() && ev.title.contains(it, ignoreCase = true) }

    val liveFavourites = remember(shown, follows) {
        shown.filter { it.state == GameState.LIVE && isFavourite(it) }.sortedBy { it.startMs }
    }
    val liveFavouriteIds = remember(liveFavourites) { liveFavourites.mapTo(HashSet()) { it.id } }
    val groups = remember(shown, liveFavouriteIds) {
        shown.filterNot { it.id in liveFavouriteIds }
            .groupBy { it.leagueId }
            .toList()
            .sortedBy { (id, _) -> SportsCatalog.all.indexOfFirst { it.id == id }.let { if (it < 0) Int.MAX_VALUE else it } }
            .map { (id, list) ->
                id to list.sortedWith(compareBy<SportsEvent> { if (it.state == GameState.LIVE) 0 else 1 }.thenBy { it.startMs })
            }
    }
    val clock = remember(use24Hour) { SimpleDateFormat(if (use24Hour) "HH:mm" else "h:mm a", Locale.getDefault()) }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Pill(stringResource(Res.string.live_tv_ui_games), mode == "games") { onMode("games") }
                Pill(stringResource(Res.string.live_tv_ui_my_teams), mode == "mine") { onMode("mine") }
                Pill(stringResource(Res.string.live_tv_ui_tables), mode == "tables") { onMode("tables") }
                Pill(stringResource(Res.string.collections_editor_tmdb_search), mode == "search") { onMode("search") }
            }
        }
        if (mode == "games" && (sportChoices.size > 1 || sport != null)) {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Pill(stringResource(Res.string.collections_tab_all), sport == null) { onSport(null) }
                    sportChoices.forEach { s ->
                        Pill("${s.icon} ${s.label}", sport == s) { onSport(s) }
                    }
                }
            }
        }
        if (mode == "mine") {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                MySportsList(repo, clock, settings.hideScores, onOpen = { opened = it }, onPick = { pickTeams = true })
            }
        } else if (mode == "tables") {
            Box(Modifier.weight(1f).fillMaxWidth()) { SportsTables(repo) }
        } else if (mode == "search") {
            Box(Modifier.weight(1f).fillMaxWidth()) { SportsSearch(repo) }
        }
        if (mode == "games" && !hasChannels) {
            Text(
                stringResource(Res.string.live_tv_ui_add_a_playlist_to_watch_games_you_can_still_see_scores_and_t),
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (mode == "games") problem?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = Accent, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        windowChannel?.let { c ->
            SportsVideoWindow(c, onFullScreen = { windowChannel = null; onWatch(c) }, onClose = { windowChannel = null })
        }
        if (mode == "games") Box(Modifier.weight(1f).fillMaxWidth()) {
            if (groups.isEmpty() && liveFavourites.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (loading) CircularProgressIndicator()
                    else Text(stringResource(Res.string.live_tv_ui_no_games_found_for_this_day), color = LocalContentColor.current.copy(alpha = 0.6f))
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (liveFavourites.isNotEmpty()) {
                        item(key = "h-my-live") {
                            Text(
                                stringResource(Res.string.live_tv_ui_my_live_games),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                        item(key = "my-live-row") {
                            LazyRow(
                                contentPadding = PaddingValues(vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(liveFavourites, key = { it.id }) { ev ->
                                    LiveGameCard(
                                        ev = ev,
                                        sport = SportsCatalog.league(ev.leagueId)?.sport,
                                        best = matches[ev.id]?.firstOrNull(),
                                        clock = clock,
                                        hideScore = settings.hideScores,
                                    ) { opened = ev }
                                }
                            }
                        }
                    }
                    groups.forEach { (leagueId, games) ->
                        item(key = "h$leagueId") {
                            Text(
                                SportsCatalog.league(leagueId)?.name ?: leagueId,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                        items(games, key = { it.id }) { ev ->
                            GameCard(
                                ev = ev,
                                sport = SportsCatalog.league(ev.leagueId)?.sport,
                                best = matches[ev.id]?.firstOrNull(),
                                clock = clock,
                                hideScore = settings.hideScores,
                            ) { opened = ev }
                        }
                    }
                }
            }
        }
    }

    opened?.let { ev ->
        SportsGameScreen(
            repo = repo,
            ev = ev,
            sport = SportsCatalog.league(ev.leagueId)?.sport ?: Sport.OTHER,
            matches = matches[ev.id].orEmpty(),
            clock = clock,
            hideScore = settings.hideScores,
            reminded = ev.id in follows.reminders.map { it.eventId },
            alerted = follows.alertGames.any { it.eventId == ev.id },
            alertsFromFollows = repo.alertsFromFollows(ev, follows),
            onRemind = {
                val was = repo.isReminded(ev)
                repo.toggleReminder(ev)
                val lead = settings.remindMinutes * 60_000L
                val r = com.nuvio.app.livetv.model.Reminder("sport:" + ev.id, SportsCatalog.league(ev.leagueId)?.name ?: sportsLabel, ev.title, ev.startMs + 2 * 60_000L - lead)
                if (was) com.nuvio.app.livetv.reminders.LiveTvReminders.cancel(context, r)
                else {
                    com.nuvio.app.livetv.reminders.LiveTvReminders.schedule(context, r)
                    android.widget.Toast.makeText(context, reminderToast, android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onAlerts = {
                val was = follows.alertGames.any { it.eventId == ev.id }
                if (!was) notifications.ask()
                repo.toggleGameAlerts(ev)
                android.widget.Toast.makeText(
                    context,
                    if (was) "Score alerts off for this game" else "Score alerts on for this game",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            },
            onWatch = { m -> if (settings.videoWindow) windowChannel = m.channel else onWatch(m.channel); opened = null },
            onRecord = { m -> onRecord(ev, m); opened = null },
            onPlayVideo = { url, title ->
                onWatch(
                    LiveChannel(
                        key = "video:$url",
                        sourceId = "sports:video",
                        sourceName = "Sports",
                        name = title,
                        tvgId = null,
                        tvgName = null,
                        logo = null,
                        groupId = "sports:video",
                        group = "Sports",
                        number = 0,
                        url = url,
                    )
                )
            },
            onFix = { fixFor = ev; opened = null },
            onDismiss = { opened = null },
        )
    }
    if (pickTeams) PickTeamsDialog(repo, onFollowed = notifications.ask) { pickTeams = false }
    details?.let { GameDetailsDialog(repo, it) { details = null } }
    fixFor?.let { ev ->
        FixChannelDialog(
            repo = repo, ev = ev,
            onDone = { msg -> android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show(); fixFor = null },
            onDismiss = { fixFor = null },
        )
    }
}

/** The Games pill: opens a menu of the sports playing that day. */
@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) { Text(label, maxLines = 1, style = MaterialTheme.typography.labelLarge) }
}

/** A small red "LIVE" pill, like the live marker on YouTube. */
@Composable
internal fun LiveBadge(modifier: Modifier = Modifier) {
    Text(
        stringResource(Res.string.live_tv_ui_live),
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.clip(RoundedCornerShape(4.dp)).background(Accent).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** One day at a time, with buttons to step back and forward. */
@Composable
internal fun SportsDaySwitcher(day: Int, onDay: (Int) -> Unit, repo: SportsRepository) {
    val compact = LocalConfiguration.current.screenWidthDp < 600
    val button = if (compact) 32.dp else 40.dp
    val dayFmt = remember { SimpleDateFormat("EEE d", Locale.getDefault()) }
    val label = when (day) {
        -1 -> "Yesterday"
        0 -> stringResource(Res.string.library_calendar_today)
        1 -> "Tomorrow"
        else -> dayFmt.format(Date(repo.dayStart(day)))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            enabled = day > MIN_DAY,
            onClick = { onDay(day - 1) },
            modifier = Modifier.size(button),
        ) {
            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = stringResource(Res.string.live_tv_ui_previous_day), modifier = Modifier.size(if (compact) 20.dp else 22.dp))
        }
        Text(
            label,
            maxLines = 1,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.widthIn(min = if (compact) 42.dp else 52.dp),
            textAlign = TextAlign.Center,
        )
        IconButton(
            enabled = day < MAX_DAY,
            onClick = { onDay(day + 1) },
            modifier = Modifier.size(button),
        ) {
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = stringResource(Res.string.live_tv_ui_next_day), modifier = Modifier.size(if (compact) 20.dp else 22.dp))
        }
    }
}

private const val MIN_DAY = -1
private const val MAX_DAY = 6

private fun statusOf(ev: SportsEvent, clock: SimpleDateFormat): String = when (ev.state) {
    GameState.PRE -> if (ev.hasTime) clock.format(Date(ev.startMs)) else kotlinx.coroutines.runBlocking { getString(Res.string.library_calendar_today) }
    GameState.LIVE -> ev.status?.takeIf { it.isNotBlank() } ?: kotlinx.coroutines.runBlocking { getString(Res.string.live_tv_ui_live) }
    GameState.POST -> ev.status?.takeIf { it.isNotBlank() } ?: kotlinx.coroutines.runBlocking { getString(Res.string.live_tv_ui_final) }
    GameState.OFF -> ev.status?.takeIf { it.isNotBlank() } ?: clock.format(Date(ev.startMs))
}

@Composable
private fun GameCard(ev: SportsEvent, sport: Sport?, best: ChannelMatch?, clock: SimpleDateFormat, hideScore: Boolean, onClick: () -> Unit) {
    val live = ev.state == GameState.LIVE
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBg).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (ev.away != null && ev.home != null) {
                val sides = ev.ordered(sport ?: Sport.OTHER)
                sides.forEachIndexed { index, s ->
                    if (index > 0) Spacer(Modifier.height(3.dp))
                    SideRow(
                        s = s,
                        showScore = ev.hasScore && !hideScore,
                        bandBrush = teamBandBrush(s.team),
                    )
                }
            } else {
                Text(ev.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                ev.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.6f), maxLines = 1) }
            }
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 12.dp)) {
            if (live) {
                LiveBadge()
                Spacer(Modifier.height(4.dp))
            }
            Text(
                statusOf(ev, clock),
                style = MaterialTheme.typography.labelLarge,
                color = if (live) Accent else LocalContentColor.current,
                fontWeight = if (live) FontWeight.Bold else FontWeight.Normal,
            )
            if (best != null) {
                Text(
                    stringResource(Res.string.live_tv_ui_on_channel, best.channel.name),
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalContentColor.current.copy(alpha = 0.6f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(140.dp),
                )
            }
        }
    }
}

@Composable
private fun LiveGameCard(
    ev: SportsEvent,
    sport: Sport?,
    best: ChannelMatch?,
    clock: SimpleDateFormat,
    hideScore: Boolean,
    onClick: () -> Unit,
) {
    val sides = ev.ordered(sport ?: Sport.OTHER)
    Column(
        Modifier
            .width(238.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveBadge()
            Spacer(Modifier.weight(1f))
            Text(
                statusOf(ev, clock),
                style = MaterialTheme.typography.labelSmall,
                color = Accent,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(7.dp))
        sides.forEachIndexed { index, s ->
            if (index > 0) Spacer(Modifier.height(3.dp))
            SideRow(s = s, showScore = ev.hasScore && !hideScore, bandBrush = teamBandBrush(s.team))
        }
        if (best != null) {
            Text(
                stringResource(Res.string.live_tv_ui_on_channel, best.channel.name),
                style = MaterialTheme.typography.labelSmall,
                color = LocalContentColor.current.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 7.dp),
            )
        }
    }
}

@Composable
private fun SideRow(s: Side, showScore: Boolean, bandBrush: Brush? = null) {
    Row(
        if (bandBrush != null) {
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(bandBrush).padding(horizontal = 8.dp, vertical = 4.dp)
        } else {
            Modifier.padding(vertical = 2.dp)
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!s.team.logo.isNullOrBlank()) {
            AsyncImage(s.team.logo, null, Modifier.size(24.dp), contentScale = ContentScale.Fit)
        } else Spacer(Modifier.size(24.dp))
        Text(
            s.team.name,
            Modifier.padding(start = 10.dp).weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = if (s.winner) FontWeight.Bold else FontWeight.Normal,
        )
        if (showScore) Text(s.score.orEmpty(), fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Search teams and leagues by name; tap to follow or unfollow. */
@Composable
private fun SportsSearch(repo: SportsRepository) {
    val known by repo.known.collectAsStateWithLifecycle()
    val follows by repo.follows.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val leagues = remember(q) {
        if (q.length < 2) emptyList()
        else repo.activeLeagues().filter { it.name.contains(q, true) || it.short.contains(q, true) }.take(20)
    }
    val teams = remember(q, known) {
        if (q.length < 2) {
            emptyList()
        } else {
            known.asSequence().flatMap { it.sides.asSequence() }.map { it.team }
                .filter { it.fullName.contains(q, true) || it.name.contains(q, true) || it.abbr.equals(q, true) }
                .distinctBy { it.key }
                .take(40)
                .toList()
        }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text(stringResource(Res.string.live_tv_ui_search_teams_or_leagues)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (q.length < 2) {
                item {
                    Text(
                        stringResource(Res.string.live_tv_ui_type_at_least_2_letters_to_search_teams_and_leagues),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.6f),
                    )
                }
            }
            if (leagues.isNotEmpty()) {
                item {
                    Text(
                        stringResource(Res.string.live_tv_ui_leagues_2),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                }
                items(leagues, key = { "l:" + it.id }) { l ->
                    val followed = l.id in follows.leagues || l.id in follows.pins
                    Row(
                        Modifier.fillMaxWidth().clickable { repo.toggleLeague(l.id) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(l.sport.icon + "  " + l.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (followed) "✓" else "+",
                            color = if (followed) MaterialTheme.colorScheme.primary else LocalContentColor.current.copy(alpha = 0.6f),
                        )
                    }
                }
            }
            if (teams.isNotEmpty()) {
                item {
                    Text(
                        stringResource(Res.string.live_tv_ui_teams),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                    )
                }
                items(teams, key = { "t:" + it.key }) { t ->
                    val followed = follows.followedTeam(t) != null
                    Row(
                        Modifier.fillMaxWidth().clickable { repo.toggleTeam(t) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!t.logo.isNullOrBlank()) AsyncImage(t.logo, null, Modifier.size(28.dp), contentScale = ContentScale.Fit)
                        else Box(Modifier.size(28.dp))
                        Text(
                            t.fullName.ifBlank { t.name },
                            Modifier.weight(1f).padding(start = 12.dp),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(if (followed) "★" else "☆", color = if (followed) Color(0xFFFFC107) else LocalContentColor.current)
                    }
                }
            }
        }
    }
}

@Composable
private fun GameDialog(
    ev: SportsEvent,
    sport: Sport?,
    matches: List<ChannelMatch>,
    clock: SimpleDateFormat,
    hideScore: Boolean,
    onWatch: (ChannelMatch) -> Unit,
    onRecord: (ChannelMatch) -> Unit,
    reminded: Boolean = false,
    onRemind: () -> Unit = {},
    onDetails: () -> Unit = {},
    onFix: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(ev.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    listOfNotNull(
                        SportsCatalog.league(ev.leagueId)?.name,
                        SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(ev.startMs)) + "  " + statusOf(ev, clock),
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                )
                if (ev.away != null && ev.home != null) {
                    ev.ordered(sport ?: Sport.OTHER).forEach { SideRow(it, showScore = ev.hasScore && !hideScore) }
                }
                ev.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                ev.situation?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Accent) }
                if (ev.broadcasts.isNotEmpty()) {
                    Text(stringResource(Res.string.live_tv_ui_tv) + ev.broadcasts.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.7f))
                }
                Spacer(Modifier.height(4.dp))
                if (matches.isEmpty()) {
                    Text(stringResource(Res.string.live_tv_ui_none_of_your_channels_are_showing_this_one), color = LocalContentColor.current.copy(alpha = 0.6f))
                } else {
                    Text(stringResource(Res.string.live_tv_ui_your_channels), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    LazyColumn(Modifier.height(220.dp)) {
                        items(matches.take(12), key = { it.channel.key }) { m ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(m.channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        listOfNotNull(m.quality, if (m.sure) "good match" else "possible match").joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LocalContentColor.current.copy(alpha = 0.6f),
                                    )
                                }
                                if (ev.state != GameState.POST) TextButton(onClick = { onRecord(m) }) { Text(stringResource(Res.string.live_tv_ui_record)) }
                                TextButton(onClick = { onWatch(m) }) { Text(stringResource(Res.string.live_tv_ui_watch)) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_close)) } },
        dismissButton = {
            Row {
                TextButton(onClick = onDetails) { Text(stringResource(Res.string.settings_meta_details)) }
                if (ev.state == GameState.PRE) TextButton(onClick = onRemind) { Text(if (reminded) "Remove reminder" else "Remind me") }
                TextButton(onClick = onFix) { Text(stringResource(Res.string.live_tv_ui_wrong_channel)) }
            }
        },
    )
}
