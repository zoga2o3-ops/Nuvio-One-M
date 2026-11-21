package com.nuvio.app.livetv.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.livetv.model.LiveChannel
import com.nuvio.app.livetv.record.LiveTvRecorder
import com.nuvio.app.livetv.record.LiveTvRecording
import com.nuvio.app.livetv.record.RecordingSchedule
import com.nuvio.app.livetv.record.RecordingStatus
import com.nuvio.app.livetv.record.RecordingStorage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

private val CardBg = Color(0xFF1B1B1F)
private val Accent = Color(0xFFE53935)

private fun whenText(ms: Long): String =
    SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault()).format(Date(ms))

private fun lengthText(sec: Double): String {
    val total = sec.toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m} min"
}

/** The Recordings tab: what's recording now, what's set to record, and what's saved. */
@Composable
fun RecordingsList(
    recordings: List<LiveTvRecording>,
    schedules: List<RecordingSchedule>,
    freeBytes: Long,
    onPlay: (LiveTvRecording) -> Unit,
    onStop: (LiveTvRecording) -> Unit,
    onDelete: (LiveTvRecording) -> Unit,
    onCancelSchedule: (RecordingSchedule) -> Unit,
    shareReady: Boolean = false,
    combining: LiveTvRecorder.CombineProgress? = null,
    onCombine: (LiveTvRecording, Boolean) -> Unit = { _, _ -> },
    series: List<com.nuvio.app.livetv.record.SeriesRule> = emptyList(),
    onRemoveSeries: (com.nuvio.app.livetv.record.SeriesRule) -> Unit = {},
    onKeepLast: (com.nuvio.app.livetv.record.SeriesRule, Int) -> Unit = { _, _ -> },
    resumeOf: (LiveTvRecording) -> Pair<Long, Long>? = { null },
    watchedOf: (LiveTvRecording) -> Boolean = { false },
) {
    var confirmDelete by remember { mutableStateOf<LiveTvRecording?>(null) }
    var combineFor by remember { mutableStateOf<LiveTvRecording?>(null) }
    val now = System.currentTimeMillis()
    val active = recordings.filter { it.isRecording }
    val saved = recordings.filter { !it.isRecording }

    if (recordings.isEmpty() && schedules.isEmpty()) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                stringResource(Res.string.live_tv_ui_nothing_recorded_yet),
                color = LocalContentColor.current.copy(alpha = 0.65f),
            )
        }
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (combining != null) {
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBg).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(stringResource(Res.string.live_tv_ui_combining), style = MaterialTheme.typography.titleSmall)
                    Text(combining.title, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.6f))
                    LinearProgressIndicator(
                        progress = { if (combining.totalBytes > 0) combining.doneBytes.toFloat() / combining.totalBytes else 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(
                            Res.string.live_tv_ui_combine_working,
                            RecordingStorage.sizeLabel(combining.doneBytes),
                            RecordingStorage.sizeLabel(combining.totalBytes),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.6f),
                    )
                }
            }
        }
        item {
            Text(
                "Room left: ${RecordingStorage.sizeLabel(freeBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = 0.55f),
            )
        }
        if (active.isNotEmpty()) {
            item { SectionTitle(stringResource(Res.string.live_tv_ui_recording_now)) }
            items(active, key = { it.id }) { r ->
                RecordingCard(
                    r, "Until ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(r.stopAtMs))}",
                    primary = stringResource(Res.string.live_tv_ui_watch) to { onPlay(r) }, secondary = "Stop" to { onStop(r) },
                )
            }
        }
        if (series.isNotEmpty()) {
            item { SectionTitle(stringResource(Res.string.profile_insights_type_series)) }
            items(series, key = { it.id }) { r ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBg).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(r.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "Every airing on ${r.channel.name}  \u00B7  " + if (r.keepLast > 0) "keeping the last ${r.keepLast}" else "keeping them all",
                            style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.6f),
                        )
                    }
                    TextButton(onClick = { onKeepLast(r, (r.keepLast - 1).coerceAtLeast(0)) }) { Text("\u2212") }
                    TextButton(onClick = { onKeepLast(r, (r.keepLast + 1).coerceAtMost(30)) }) { Text("+") }
                    TextButton(onClick = { onRemoveSeries(r) }) { Text("Stop") }
                }
            }
        }
        if (schedules.isNotEmpty()) {
            item { SectionTitle("Scheduled") }
            items(schedules, key = { it.id }) { s ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBg).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(s.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${s.channel.name} · ${whenText(s.startMs)}" + if (s.useCatchup) " · " + stringResource(Res.string.live_tv_ui_saved_from_catchup) else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalContentColor.current.copy(alpha = 0.6f),
                        )
                    }
                    TextButton(onClick = { onCancelSchedule(s) }) { Text(stringResource(Res.string.action_cancel)) }
                }
            }
        }
        if (saved.isNotEmpty()) {
            item { SectionTitle(stringResource(Res.string.profile_insights_hero_library)) }
            items(saved, key = { it.id }) { r ->
                val detail = when (r.status) {
                    RecordingStatus.FAILED -> r.problem ?: stringResource(Res.string.live_tv_ui_couldnt_be_recorded)
                    else -> "${whenText(r.startedAtMs)} · ${lengthText(r.durationSec)} · ${RecordingStorage.sizeLabel(r.sizeBytes)}" +
                        (resumeOf(r)?.let { (pos, _) -> if (pos > 5_000L) "\n" + stringResource(Res.string.live_tv_ui_continue_from, lengthText(pos / 1000.0)) else null } ?: "") +
                        (r.problem?.let { "\n$it" } ?: "")
                }
                RecordingCard(
                    r, detail,
                    primary = if (r.status == RecordingStatus.FAILED) null else stringResource(Res.string.action_play) to { onPlay(r) },
                    secondary = stringResource(Res.string.action_delete) to { confirmDelete = r },
                    combine = if (r.status == RecordingStatus.FAILED) null else ({ combineFor = r }),
                    watched = watchedOf(r),
                )
            }
        }
    }
    confirmDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(Res.string.live_tv_ui_delete_this_recording)) },
            text = { Text(stringResource(Res.string.live_tv_ui_recording_will_be_removed, r.title)) },
            confirmButton = { TextButton(onClick = { onDelete(r); confirmDelete = null }) { Text(stringResource(Res.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
    combineFor?.let { r ->
        AlertDialog(
            onDismissRequest = { combineFor = null },
            title = { Text(stringResource(Res.string.live_tv_ui_combine_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(r.title, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.65f))
                    if (shareReady) TextButton(onClick = { combineFor = null; onCombine(r, true) }) {
                        Text(stringResource(Res.string.live_tv_ui_combine_share))
                    }
                    if (!r.smb) TextButton(onClick = { combineFor = null; onCombine(r, false) }) {
                        Text(stringResource(Res.string.live_tv_ui_combine_device))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { combineFor = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun RecordingCard(
    r: LiveTvRecording,
    detail: String,
    primary: Pair<String, () -> Unit>?,
    secondary: Pair<String, () -> Unit>,
    combine: (() -> Unit)? = null,
    watched: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBg).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                (if (watched) "\u2713 " else if (r.isRecording) "\u25CF " else "") + r.title,
                style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = when {
                    r.isRecording -> Accent
                    watched -> MaterialTheme.colorScheme.primary
                    else -> LocalContentColor.current
                },
            )
            Text(r.channelName, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.6f), maxLines = 1)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.6f))
        }
        if (primary != null) TextButton(onClick = primary.second) { Text(primary.first) }
        if (combine != null) TextButton(onClick = combine) { Text(stringResource(Res.string.live_tv_ui_combine)) }
        TextButton(onClick = secondary.second) { Text(secondary.first) }
    }
}

/** Starts a recording of [channel] now, or sets one for later. */
@Composable
fun RecordChannelDialog(
    channel: LiveChannel,
    currentShow: String?,
    minutesLeftInShow: Int?,
    onRecordNow: (minutes: Int, title: String) -> Unit,
    onSchedule: (title: String, startMs: Long, stopMs: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(currentShow ?: channel.name) }
    var startMs by remember { mutableStateOf(0L) }
    var minutes by remember { mutableStateOf(60) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.live_tv_ui_record_channel, channel.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true, label = { Text(stringResource(Res.string.stream_info_name)) })
                Text(stringResource(Res.string.live_tv_ui_how_long), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(30, 60, 120, 180).forEach { m ->
                        TextButton(onClick = { minutes = m }) {
                            Text(if (m < 60) stringResource(Res.string.live_tv_ui_minutes_short, m) else stringResource(Res.string.live_tv_ui_hours_value, m / 60), color = if (minutes == m) Accent else LocalContentColor.current)
                        }
                    }
                }
                if (minutesLeftInShow != null && minutesLeftInShow > 0) {
                    TextButton(onClick = { minutes = minutesLeftInShow }) { Text(stringResource(Res.string.live_tv_ui_until_show_ends, minutesLeftInShow)) }
                }
                Text(
                    if (startMs == 0L) stringResource(Res.string.live_tv_ui_starts_now) else stringResource(Res.string.live_tv_ui_starts_at, whenText(startMs)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row {
                    TextButton(onClick = { startMs = 0L }) { Text(stringResource(Res.string.live_tv_epg_now)) }
                    TextButton(onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = if (startMs > 0) startMs else System.currentTimeMillis() + 3_600_000L }
                        DatePickerDialog(context, { _, y, mo, d ->
                            TimePickerDialog(context, { _, h, mi ->
                                val picked = Calendar.getInstance().apply { set(y, mo, d, h, mi, 0); set(Calendar.MILLISECOND, 0) }
                                startMs = picked.timeInMillis
                            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                    }) { Text(stringResource(Res.string.live_tv_ui_pick_a_time)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val name = title.trim().ifBlank { channel.name }
                if (startMs == 0L || startMs <= System.currentTimeMillis()) onRecordNow(minutes, name)
                else onSchedule(name, startMs, startMs + minutes * 60_000L)
                onDismiss()
            }) { Text(if (startMs == 0L) stringResource(Res.string.live_tv_ui_record) else "Schedule") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
