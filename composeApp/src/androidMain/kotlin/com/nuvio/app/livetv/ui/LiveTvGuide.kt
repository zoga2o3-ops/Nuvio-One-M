package com.nuvio.app.livetv.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nuvio.app.livetv.data.CatchupUrlBuilder
import com.nuvio.app.livetv.model.EpgProgram
import com.nuvio.app.livetv.model.LiveChannel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

private const val HOUR = 3_600_000L
private const val HALF_HOUR = 1_800_000L
/** How far ahead the guide looks; the past side follows the "Past programmes" setting. */
private const val FUTURE_HOURS = 24

private val BlockBg = Color(0xFF212126)
private val BlockPast = Color(0xFF18181B)
private val BlockLive = Color(0xFF3A2326)
private val Accent = Color(0xFFE53935)

/**
 * The guide: channels down the side, time across. Scroll along any row and they all move together;
 * the channel pictures stay put. Tap a programme for its details and catch-up, or a channel's
 * picture to watch.
 */
@Composable
fun LiveTvGuideGrid(
    channels: List<LiveChannel>,
    programs: Map<String, List<EpgProgram>>,
    now: Long,
    tablet: Boolean,
    /** A phone held sideways: enough room for the channel name, but not a tablet's full label. */
    wide: Boolean = false,
    /** How many hours before now the guide can scroll back (the "Past programmes" setting). */
    pastHours: Int = 24,
    /** Called with the channels on screen so Xtream's catch-up archive can fill their past listings. */
    onArchiveNeeded: (List<LiveChannel>) -> Unit = {},
    onPlay: (LiveChannel) -> Unit,
    onCatchup: (LiveChannel, EpgProgram) -> Unit,
    reminders: List<com.nuvio.app.livetv.model.Reminder> = emptyList(),
    onRemind: (LiveChannel, EpgProgram, Boolean) -> Unit = { _, _, _ -> },
    isRecordSet: (LiveChannel, EpgProgram) -> Boolean = { _, _ -> false },
    onRecord: (LiveChannel, EpgProgram, Boolean) -> Unit = { _, _, _ -> },
    onRecordSeries: (LiveChannel, EpgProgram) -> Unit = { _, _ -> },
) {
    val past = pastHours.coerceAtLeast(1)
    val spanHours = past + FUTURE_HOURS
    val hourWidth = if (tablet) 300.dp else 230.dp
    val labelWidth = if (tablet) 176.dp else if (wide) 140.dp else 84.dp
    val showName = tablet || wide
    val rowHeight = when (LiveTvDisplay.guideSize) { "COMPACT" -> 62.dp; "LARGE" -> 88.dp; "XLARGE" -> 104.dp; else -> 74.dp }
    val totalWidth = hourWidth * spanHours
    // Fixed when the guide opens, on a half-hour mark. It opens with "now" near the left edge;
    // the past programmes from the "Past programmes" setting are to the left.
    val windowStart = remember(past) {
        ((System.currentTimeMillis() - past * HOUR) / HALF_HOUR) * HALF_HOUR
    }
    val windowEnd = windowStart + spanHours * HOUR
    val scroll = rememberScrollState()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val timeFormat = remember { SimpleDateFormat(if (LiveTvDisplay.use24Hour) "HH:mm" else "h:mm a", Locale.getDefault()) }
    val dayFormat = remember { SimpleDateFormat("EEE d", Locale.getDefault()) }
    var selected by remember { mutableStateOf<Pair<LiveChannel, EpgProgram>?>(null) }

    // Start with "now" near the left edge, once the row is wide enough to scroll.
    LaunchedEffect(Unit) {
        snapshotFlow { scroll.maxValue }.first { it > 0 }
        val x = with(density) { (hourWidth * ((System.currentTimeMillis() - windowStart) / HOUR.toFloat())).roundToPx() }
        scroll.scrollTo((x - with(density) { 24.dp.roundToPx() }).coerceAtLeast(0))
    }

    // Ask for the Xtream catch-up archive for the channels on screen. XMLTV listings come from
    // the guide DB; this fills in the past for providers whose guide file only goes back a day.
    LaunchedEffect(channels) {
        if (channels.isNotEmpty()) onArchiveNeeded(channels.take(12))
    }
    LaunchedEffect(channels, listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }
            .distinctUntilChanged()
            .collect { indexes ->
                val visible = indexes.mapNotNull { channels.getOrNull(it) }
                if (visible.isNotEmpty()) onArchiveNeeded(visible)
            }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(30.dp)) {
            Spacer(Modifier.width(labelWidth))
            Box(Modifier.weight(1f).horizontalScroll(scroll)) {
                Box(Modifier.width(totalWidth).fillMaxHeight()) {
                    for (i in 0 until spanHours * 2) {
                        val slot = windowStart + i * HALF_HOUR
                        val calendar = java.util.Calendar.getInstance().apply { timeInMillis = slot }
                        val midnight = calendar.get(java.util.Calendar.HOUR_OF_DAY) == 0 &&
                            calendar.get(java.util.Calendar.MINUTE) == 0
                        Text(
                            if (midnight) dayFormat.format(Date(slot)) else timeFormat.format(Date(slot)),
                            modifier = Modifier.offset(x = hourWidth * (i / 2f)).width(hourWidth / 2).padding(start = 8.dp, top = 6.dp),
                            fontSize = 12.sp,
                            maxLines = 1,
                            color = if (midnight) Accent else LocalContentColor.current.copy(alpha = 0.65f),
                        )
                    }
                }
            }
        }
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 96.dp)) {
            items(channels, key = { it.key }) { channel ->
                GuideRow(
                    channel = channel,
                    programs = programs[channel.key].orEmpty(),
                    now = now,
                    windowStart = windowStart,
                    windowEnd = windowEnd,
                    hourWidth = hourWidth,
                    labelWidth = labelWidth,
                    rowHeight = rowHeight,
                    totalWidth = totalWidth,
                    showName = showName,
                    scroll = scroll,
                    onPlay = { onPlay(channel) },
                    onProgram = { selected = channel to it },
                    canReplay = channel.catchup != null,
                )
            }
        }
    }

    selected?.let { (channel, program) ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(program.displayTitle()) },
            text = {
                Column {
                    Text(
                        "${timeFormat.format(Date(program.startMs))} – ${timeFormat.format(Date(program.stopMs))}  ·  ${channel.name}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    program.category?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.65f), modifier = Modifier.padding(top = 2.dp))
                    }
                    program.description?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
                    }
                    if (program.stopMs <= System.currentTimeMillis() &&
                        !CatchupUrlBuilder.isAvailable(channel.catchup, program.startMs, System.currentTimeMillis())
                    ) {
                        Text(
                            stringResource(Res.string.live_tv_ui_this_channel_cant_replay),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalContentColor.current.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            },
            confirmButton = {
                val replay = CatchupUrlBuilder.isAvailable(channel.catchup, program.startMs, System.currentTimeMillis())
                val aired = program.stopMs <= System.currentTimeMillis()
                val reminded = reminders.any { it.channelKey == channel.key && it.startMs == program.startMs }
                val recordSet = isRecordSet(channel, program)
                Row {
                    if (program.stopMs > System.currentTimeMillis()) {
                        TextButton(onClick = { selected = null; onRecordSeries(channel, program) }) { Text(stringResource(Res.string.live_tv_ui_record_series)) }
                        TextButton(onClick = { selected = null; onRecord(channel, program, !recordSet) }) {
                            Text(if (recordSet) stringResource(Res.string.live_tv_ui_cancel_recording) else stringResource(Res.string.live_tv_ui_record))
                        }
                    }
                    if (program.startMs > System.currentTimeMillis()) {
                        TextButton(onClick = { selected = null; onRemind(channel, program, !reminded) }) {
                            Text(if (reminded) stringResource(Res.string.live_tv_ui_remove_reminder) else stringResource(Res.string.live_tv_ui_remind_me))
                        }
                    }
                    if (replay) TextButton(onClick = { selected = null; onCatchup(channel, program) }) { Text(stringResource(Res.string.live_tv_ui_from_the_start)) }
                    TextButton(onClick = { selected = null; onPlay(channel) }) { Text(if (aired) stringResource(Res.string.live_tv_ui_watch_channel) else stringResource(Res.string.live_tv_ui_watch_live)) }
                }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text(stringResource(Res.string.action_close)) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GuideRow(
    channel: LiveChannel,
    programs: List<EpgProgram>,
    now: Long,
    windowStart: Long,
    windowEnd: Long,
    hourWidth: Dp,
    labelWidth: Dp,
    rowHeight: Dp,
    totalWidth: Dp,
    showName: Boolean,
    scroll: androidx.compose.foundation.ScrollState,
    onPlay: () -> Unit,
    onProgram: (EpgProgram) -> Unit,
    canReplay: Boolean,
) {
    val timeFormat = remember { SimpleDateFormat(if (LiveTvDisplay.use24Hour) "HH:mm" else "h:mm a", Locale.getDefault()) }
    val density = LocalDensity.current
    Row(Modifier.fillMaxWidth().height(rowHeight)) {
        Row(
            Modifier.width(labelWidth).fillMaxHeight().clickable(onClick = onPlay).padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(width = 52.dp, height = 40.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF26262B)),
                contentAlignment = Alignment.Center,
            ) {
                if (!channel.logo.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.logo, contentDescription = channel.name, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                    )
                } else {
                    Text(channel.name.take(2).uppercase(), fontSize = 13.sp, color = Color(0xFFB0B0B0))
                }
            }
            if (showName) {
                Text(
                    channel.name,
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(scroll)) {
            Box(Modifier.width(totalWidth).fillMaxHeight()) {
                val visible = programs.filter { it.stopMs > windowStart && it.startMs < windowEnd }
                if (visible.isEmpty()) {
                    Text(
                        stringResource(Res.string.live_tv_ui_no_guide_for_this_channel),
                        modifier = Modifier.padding(start = 16.dp).align(Alignment.CenterStart),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.4f),
                    )
                }
                visible.forEach { p ->
                    val from = maxOf(p.startMs, windowStart)
                    val to = minOf(p.stopMs, windowEnd)
                    val live = p.isLive(now)
                    val aired = p.stopMs <= now
                    val replayable = aired &&
                        CatchupUrlBuilder.isAvailable(channel.catchup, p.startMs, now)
                    val blockStart = hourWidth * ((from - windowStart) / HOUR.toFloat())
                    val blockWidth = (hourWidth * ((to - from) / HOUR.toFloat()) - 3.dp).coerceAtLeast(4.dp)
                    // A programme that began off to the left keeps its title at the edge while you scroll.
                    val shift = with(density) {
                        (scroll.value - blockStart.roundToPx()).coerceIn(0, (blockWidth - 96.dp).roundToPx().coerceAtLeast(0)).toDp()
                    }
                    Box(
                        Modifier
                            .offset(x = blockStart)
                            .width(blockWidth)
                            .fillMaxHeight()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (live) BlockLive else if (aired) BlockPast else BlockBg)
                            .then(if (live) Modifier.border(1.dp, Accent, RoundedCornerShape(8.dp)) else Modifier)
                            .combinedClickable(
                                // A programme opens its details, where "From the start" and the
                                // record/remind actions live. The channel picture plays live.
                                onClick = { onProgram(p) },
                                onLongClick = { onProgram(p) },
                            ),
                    ) {
                        Column(
                            Modifier.fillMaxSize().padding(vertical = 5.dp),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                        ) {
                            Column(
                                Modifier.padding(
                                    start = 8.dp + shift,
                                    end = if (replayable) 20.dp else 8.dp,
                                )
                            ) {
                                Text(
                                    p.title, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = LocalContentColor.current.copy(alpha = if (aired) (if (canReplay) 0.8f else 0.5f) else 0.95f),
                                )
                                if (LiveTvDisplay.showTimes) Text(
                                    timeFormat.format(Date(p.startMs)) + " – " + timeFormat.format(Date(p.stopMs)), fontSize = 11.sp, maxLines = 1,
                                    color = LocalContentColor.current.copy(alpha = 0.5f),
                                ) else Text(
                                    timeFormat.format(Date(p.startMs)), fontSize = 11.sp, maxLines = 1,
                                    color = LocalContentColor.current.copy(alpha = 0.5f),
                                )
                                if (LiveTvDisplay.showEpisodes && !p.episode.isNullOrBlank()) Text(
                                    p.episode!!, fontSize = 10.sp, maxLines = 1,
                                    color = LocalContentColor.current.copy(alpha = 0.45f),
                                )
                            }
                        }
                        if (replayable) {
                            Icon(
                                Icons.Default.Replay,
                                contentDescription = stringResource(Res.string.live_tv_ui_from_the_start),
                                tint = Color.White.copy(alpha = 0.55f),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 4.dp, end = 5.dp)
                                    .size(12.dp),
                            )
                        }
                    }
                }
                // Where "now" is.
                if (LiveTvDisplay.showNowLine && now in windowStart..windowEnd) {
                    Box(
                        Modifier
                            .offset(x = hourWidth * ((now - windowStart) / HOUR.toFloat()))
                            .width(2.dp).fillMaxHeight().background(Accent),
                    )
                }
            }
        }
    }
}
