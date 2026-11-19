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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.nuvio.app.livetv.sports.ChannelMatch
import com.nuvio.app.livetv.sports.GameExtras
import com.nuvio.app.livetv.sports.GameState
import com.nuvio.app.livetv.sports.Performer
import com.nuvio.app.livetv.sports.ScoringPlay
import com.nuvio.app.livetv.sports.Side
import com.nuvio.app.livetv.sports.Sport
import com.nuvio.app.livetv.sports.SportsCatalog
import com.nuvio.app.livetv.sports.SportsEvent
import com.nuvio.app.livetv.sports.SportsRepository
import com.nuvio.app.livetv.sports.SportsVideo
import com.nuvio.app.livetv.sports.StatLine
import com.nuvio.app.livetv.sports.TableRow
import com.nuvio.app.livetv.sports.TeamInfo
import com.nuvio.app.features.youtube.YouTubeHost
import com.nuvio.app.features.youtube.YouTubePlayRequest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.roundToInt
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

import org.jetbrains.compose.resources.getString
private val GameBg = Color(0xFF141419)
private val CardBg = Color(0xFF1B1B1F)
private val Accent = Color(0xFFE53935)
private val AwayBar = Color(0xFFE53935)
private val HomeBar = Color(0xFF3A6EA5)

/** A game's page: the scoreboard, your channels, the latest scoring and the team stats. */
@Composable
internal fun SportsGameScreen(
    repo: SportsRepository,
    ev: SportsEvent,
    sport: Sport,
    matches: List<ChannelMatch>,
    clock: SimpleDateFormat,
    hideScore: Boolean,
    reminded: Boolean,
    alerted: Boolean,
    alertsFromFollows: Boolean,
    onRemind: () -> Unit,
    onAlerts: () -> Unit,
    onWatch: (ChannelMatch) -> Unit,
    onRecord: (ChannelMatch) -> Unit,
    onPlayVideo: (String, String) -> Unit,
    onFix: () -> Unit,
    onDismiss: () -> Unit,
) {
    val league = SportsCatalog.league(ev.leagueId)
    val sides = ev.ordered(sport)
    val showScore = ev.hasScore && !hideScore
    var plays by remember(ev.id) { mutableStateOf<List<ScoringPlay>?>(null) }
    var extras by remember(ev.id) { mutableStateOf<GameExtras?>(null) }
    var videos by remember(ev.id) { mutableStateOf<List<SportsVideo>?>(null) }
    var tab by remember(ev.id) { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(ev.id) {
        plays = runCatching { repo.scoringPlays(ev) }.getOrDefault(emptyList())
        extras = runCatching { repo.gameExtras(ev) }.getOrNull()
    }
    LaunchedEffect(ev.id, ev.state) {
        if (ev.state == GameState.POST) {
            videos = runCatching { repo.highlights(ev) }.getOrDefault(emptyList())
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = GameBg) {
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                        }
                        Text(
                            league?.name ?: stringResource(Res.string.yt_tab_sports),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f).padding(start = 4.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item(key = "score") {
                            Scoreboard(ev, league?.name, sides, showScore, extras)
                        }
                        item(key = "actions") {
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (ev.state == GameState.PRE) {
                                    GameChip(if (reminded) stringResource(Res.string.live_tv_ui_reminded) else stringResource(Res.string.live_tv_ui_remind_me), reminded, onRemind)
                                }
                                if ((ev.state == GameState.PRE || ev.state == GameState.LIVE) && !hideScore && !alertsFromFollows) {
                                    GameChip(
                                        if (alerted) stringResource(Res.string.live_tv_ui_score_alerts_on) else stringResource(Res.string.live_tv_ui_score_alerts),
                                        alerted,
                                        onAlerts,
                                    )
                                }
                                GameChip(stringResource(Res.string.live_tv_ui_fix_channel), false, onFix)
                            }
                        }
                        item(key = "channels-h") { SectionTitle(stringResource(Res.string.live_tv_ui_watch_on_your_channels)) }
                        if (matches.isEmpty()) {
                            item(key = "channels-none") {
                                Text(
                                    stringResource(Res.string.live_tv_ui_none_of_your_channels_are_showing_this_one),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.6f),
                                )
                            }
                        } else {
                            items(matches.take(12), key = { it.channel.key }) { m ->
                                ChannelRow(m, clock, ev.state, onWatch = { onWatch(m) }, onRecord = { onRecord(m) })
                            }
                        }
                        item(key = "tabs") {
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                GameTab(stringResource(Res.string.live_tv_ui_game), tab == 0) { tab = 0 }
                                GameTab(stringResource(Res.string.live_tv_ui_stats), tab == 1) { tab = 1 }
                                GameTab(stringResource(Res.string.live_tv_ui_plays), tab == 2) { tab = 2 }
                                if (ev.state == GameState.POST) {
                                    GameTab(stringResource(Res.string.live_tv_ui_highlights), tab == 3) { tab = 3 }
                                }
                            }
                        }
                        val p = plays
                        val x = extras
                        if (tab == 0) {
                            if (!p.isNullOrEmpty()) {
                                item(key = "scoring-h") { SectionTitle(stringResource(Res.string.live_tv_ui_latest_scoring)) }
                                items(p.asReversed().take(4), key = { it.clock + "|" + it.text }) { play ->
                                    PlayRow(play)
                                }
                            }
                            if (x != null && x.performers.isNotEmpty()) {
                                item(key = "leaders-h") { SectionTitle(stringResource(Res.string.live_tv_ui_leaders)) }
                                items(x.performers.take(4), key = { it.category + "|" + it.name }) { performer ->
                                    PerformerRow(performer)
                                }
                            }
                        }
                        if (tab == 1) {
                            if (x != null && x.stats.isNotEmpty()) {
                                item(key = "stats-h") { SectionTitle(stringResource(Res.string.live_tv_ui_team_stats)) }
                                items(x.stats, key = { it.label }) { stat -> StatBar(stat) }
                            }
                            if (x != null && x.performers.isNotEmpty()) {
                                item(key = "leaders-h") { SectionTitle(stringResource(Res.string.live_tv_ui_leaders)) }
                                items(x.performers.take(6), key = { it.category + "|" + it.name }) { performer ->
                                    PerformerRow(performer)
                                }
                            }
                            if (x != null && x.table.isNotEmpty()) {
                                item(key = "table-h") { SectionTitle(x.tableTitle ?: stringResource(Res.string.live_tv_ui_table)) }
                                item(key = "table") { MiniTable(x.tableHead, x.table) }
                            }
                        }
                        if (tab == 2) {
                            if (p.isNullOrEmpty()) {
                                item(key = "plays-none") {
                                    Text(
                                        stringResource(Res.string.live_tv_ui_no_plays_yet),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.6f),
                                    )
                                }
                            } else {
                                items(p.asReversed(), key = { it.clock + "|" + it.text }) { play ->
                                    PlayRow(play)
                                }
                            }
                        }
                        if (tab == 3) {
                            val v = videos
                            if (v == null) {
                                item(key = "highlights-loading") {
                                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = Color.White.copy(alpha = 0.7f))
                                    }
                                }
                            } else if (v.isEmpty()) {
                                item(key = "highlights-none") {
                                    Text(
                                        stringResource(Res.string.live_tv_ui_no_highlights_yet),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.6f),
                                    )
                                }
                            } else {
                                items(v, key = { it.id }) { video ->
                                    VideoRow(video) {
                                        val youtubeId = video.youtubeId
                                        if (youtubeId != null) {
                                            YouTubeHost.openVideo?.invoke(
                                                YouTubePlayRequest(
                                                    url = video.url,
                                                    title = video.title,
                                                    poster = null,
                                                    videoId = youtubeId
                                                )
                                            )
                                        } else {
                                            scope.launch {
                                                repo.highlightUrl(video)?.takeIf { it.isNotBlank() }?.let { url ->
                                                    onPlayVideo(url, video.title)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Scoreboard(
    ev: SportsEvent,
    leagueName: String?,
    sides: List<Side>,
    showScore: Boolean,
    extras: GameExtras?,
) {
    val live = ev.state == GameState.LIVE
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardBg).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOfNotNull(leagueName, extras?.venue).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (live) LiveBadge()
                Text(
                    statusLabel(ev),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (live) Accent else Color.White.copy(alpha = 0.8f),
                )
            }
        }
        if (sides.size == 2) {
            sides.forEach { s -> TeamBand(s, showScore) }
        } else {
            Text(ev.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            ev.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                statusLine(ev),
                style = MaterialTheme.typography.bodySmall,
                color = if (live) Accent else Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (ev.broadcasts.isNotEmpty()) {
                Text(
                    ev.broadcasts.take(3).joinToString(", "),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TeamBand(side: Side, showScore: Boolean) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(teamBandBrush(side.team))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!side.team.logo.isNullOrBlank()) {
            AsyncImage(side.team.logo, contentDescription = null, modifier = Modifier.size(36.dp), contentScale = ContentScale.Fit)
        } else {
            Spacer(Modifier.size(36.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                side.team.name,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = if (side.winner) FontWeight.Black else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            side.record?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
            }
        }
        if (showScore) {
            Text(
                side.score ?: "–",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun ChannelRow(
    m: ChannelMatch,
    clock: SimpleDateFormat,
    state: GameState,
    onWatch: () -> Unit,
    onRecord: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardBg).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(m.channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(m.quality, if (m.sure) stringResource(Res.string.live_tv_ui_good_match) else stringResource(Res.string.live_tv_ui_possible_match)).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
        if (state != GameState.POST) TextButton(onClick = onRecord) { Text(stringResource(Res.string.live_tv_ui_record)) }
        TextButton(onClick = onWatch) { Text(stringResource(Res.string.live_tv_ui_watch)) }
    }
}

@Composable
private fun GameTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) Accent else Color.Transparent)
            .border(1.dp, if (selected) Accent else Color(0x33FFFFFF), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun PerformerRow(p: Performer) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(p.category, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f))
            Text(p.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
        Text(
            p.line,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.End,
            modifier = Modifier.width(150.dp),
        )
    }
}

@Composable
private fun MiniTable(head: List<String>, rows: List<TableRow>) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardBg).padding(10.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text("", Modifier.width(22.dp))
            Text(stringResource(Res.string.live_tv_ui_team), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f))
            head.forEach { h ->
                Text(h, Modifier.width(34.dp), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f), textAlign = TextAlign.End)
            }
        }
        rows.forEach { r ->
            Row(
                Modifier.fillMaxWidth().padding(top = 5.dp)
                    .then(if (r.mine) Modifier.background(Color(0x22FFC107)) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(r.pos, Modifier.width(22.dp), style = MaterialTheme.typography.labelSmall)
                Text(r.team, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(r.a, Modifier.width(34.dp), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
                Text(r.b, Modifier.width(34.dp), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
                Text(r.c, Modifier.width(34.dp), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun PlayRow(p: ScoringPlay) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            p.clock.ifBlank { "–" },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.width(44.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(p.teamAbbr, if (p.awayScore != null && p.homeScore != null) "${p.awayScore}–${p.homeScore}" else null).joinToString("  ·  "),
                style = MaterialTheme.typography.labelSmall,
                color = Accent,
            )
            Text(p.text, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f), maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun VideoRow(video: SportsVideo, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Accent, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(
                video.title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    kindLabel(video.kind),
                    video.source.takeIf { it.isNotBlank() },
                    video.seconds?.let { seconds -> "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}" },
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun kindLabel(kind: SportsVideo.Kind): String = when (kind) {
    SportsVideo.Kind.CONDENSED -> stringResource(Res.string.live_tv_ui_condensed_game)
    SportsVideo.Kind.RECAP -> stringResource(Res.string.live_tv_ui_recap)
    SportsVideo.Kind.HIGHLIGHTS -> stringResource(Res.string.live_tv_ui_highlights)
    SportsVideo.Kind.PLAY -> stringResource(Res.string.action_play)
}

@Composable
private fun StatBar(stat: StatLine) {
    val away = stat.awayValue
    val home = stat.homeValue
    val total = (away ?: 0.0) + (home ?: 0.0)
    val awayFraction = if (total > 0.0) ((away ?: 0.0) / total).toFloat() else 0.5f
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stat.away, Modifier.width(56.dp), fontWeight = FontWeight.Bold)
            Text(
                stat.label.uppercase(),
                Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(stat.home, Modifier.width(56.dp), textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF2A2A30))) {
            Box(Modifier.weight(awayFraction.coerceAtLeast(0.02f)).fillMaxHeight().background(AwayBar))
            Box(Modifier.weight((1f - awayFraction).coerceAtLeast(0.02f)).fillMaxHeight().background(HomeBar))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun GameChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.clip(shape)
            .background(if (selected) Accent else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, maxLines = 1, style = MaterialTheme.typography.labelLarge)
    }
}

private fun statusLabel(ev: SportsEvent): String = when (ev.state) {
    GameState.LIVE -> ev.status?.takeIf { it.isNotBlank() } ?: "LIVE"
    GameState.POST -> ev.status?.takeIf { it.isNotBlank() } ?: "FINAL"
    GameState.OFF -> ev.status?.takeIf { it.isNotBlank() } ?: "POSTPONED"
    GameState.PRE -> if (ev.hasTime) "UPCOMING" else "TODAY"
}

private fun statusLine(ev: SportsEvent): String = when (ev.state) {
    GameState.LIVE -> "In progress"
    GameState.POST -> "FT · " + SimpleDateFormat("EEE d MMM", java.util.Locale.getDefault()).format(Date(ev.startMs))
    GameState.OFF -> ev.status ?: "Postponed"
    GameState.PRE -> if (ev.hasTime) SimpleDateFormat("EEE d MMM, h:mm a", java.util.Locale.getDefault()).format(Date(ev.startMs)) else kotlinx.coroutines.runBlocking { getString(Res.string.library_calendar_today) }
}

// ---- team colours

private fun bandColor(c: Color): Color =
    lerp(c, Color(0xFF0B0E14), if (c.luminance() > 0.5f) 0.5f else if (c.luminance() > 0.25f) 0.25f else 0.08f)

private fun bandBase(team: TeamInfo?): Color = bandBase(team?.color, team?.altColor)

private fun bandBase(color: Long?, altColor: Long?): Color {
    val main = color?.let { Color(it) }
    val alt = altColor?.let { Color(it) }
    val pick = when {
        main == null -> alt
        main.luminance() > 0.62f && alt != null && alt.luminance() < 0.62f -> alt
        else -> main
    }
    val base = pick ?: Color(0xFF3A4150)
    return if (base.luminance() < 0.04f && alt != null && alt.luminance() > base.luminance() + 0.05f) alt else base
}

internal fun teamBandBrush(team: TeamInfo?): Brush = teamBandBrush(team?.color, team?.altColor)

internal fun teamBandBrush(color: Long?, altColor: Long?): Brush {
    val c = bandColor(bandBase(color, altColor))
    return Brush.horizontalGradient(
        0f to c.copy(alpha = 0.92f),
        0.7f to c.copy(alpha = 0.28f),
        1f to c.copy(alpha = 0.05f),
    )
}

private fun lerp(start: Color, stop: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (stop.red - start.red) * f,
        green = start.green + (stop.green - start.green) * f,
        blue = start.blue + (stop.blue - start.blue) * f,
        alpha = start.alpha + (stop.alpha - start.alpha) * f,
    )
}
