package com.nuvio.app.livetv.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nuvio.app.livetv.model.LiveChannel
import com.nuvio.app.livetv.player.PictureShape
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** One tappable line in an actions list. */
@Composable
private fun ActionRow(label: String, detail: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Asks for one line of text (a name, a number). An empty answer clears it. */
@Composable
internal fun TextPromptDialog(
    title: String,
    initial: String,
    hint: String? = null,
    numeric: Boolean = false,
    label: String? = null,
    onOk: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val focus = androidx.compose.ui.focus.FocusRequester()
                androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = if (numeric) it.filter(Char::isDigit).take(5) else it },
                    singleLine = true,
                    label = { Text(label ?: title) },
                    keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    colors = fieldColors(),
                )
                if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onOk(text.trim()) }) { Text(stringResource(Res.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/** What you can do with one channel, from a long press. */
@Composable
internal fun ChannelActionsDialog(
    channel: LiveChannel,
    favorite: Boolean,
    shape: PictureShape,
    onFavorite: () -> Unit,
    onRename: (String?) -> Unit,
    onNumber: (Int?) -> Unit,
    onHide: () -> Unit,
    onShape: (PictureShape) -> Unit,
    onRemind: (() -> Unit)?,
    onRecord: (() -> Unit)? = null,
    onMove: ((Int) -> Unit)? = null,
    onAddToGroup: (() -> Unit)? = null,
    onCopyToGroup: (() -> Unit)? = null,
    onRemoveFromGroup: (() -> Unit)? = null,
    onGuide: (() -> Unit)? = null,
    onLogo: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    var numbering by remember { mutableStateOf(false) }
    if (renaming) {
        TextPromptDialog(
            title = stringResource(Res.string.live_tv_ui_rename_channel),
            initial = channel.name,
            hint = stringResource(Res.string.live_tv_ui_leave_it_empty_to_go_back_to_the_playlist_s_name),
            onOk = { onRename(it.ifBlank { null }); renaming = false; onDismiss() },
            onDismiss = { renaming = false },
        )
        return
    }
    if (numbering) {
        TextPromptDialog(
            title = stringResource(Res.string.live_tv_ui_channel_number),
            initial = if (channel.number > 0) channel.number.toString() else "",
            hint = stringResource(Res.string.live_tv_ui_leave_it_empty_to_use_the_playlist_s_number),
            numeric = true,
            onOk = { onNumber(it.toIntOrNull()); numbering = false; onDismiss() },
            onDismiss = { numbering = false },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(channel.name, maxLines = 1) },
        text = {
            Column {
                ActionRow(if (favorite) stringResource(Res.string.live_tv_ui_remove_from_favorites) else stringResource(Res.string.live_tv_ui_add_to_favorites)) { onFavorite(); onDismiss() }
                if (onRecord != null) ActionRow(stringResource(Res.string.live_tv_ui_record_u2026)) { onRecord(); onDismiss() }
                if (onMove != null) {
                    ActionRow(stringResource(Res.string.live_tv_ui_move_earlier_in_this_list)) { onMove(-1) }
                    ActionRow(stringResource(Res.string.live_tv_ui_move_later_in_this_list)) { onMove(1) }
                }
                if (onAddToGroup != null) ActionRow(stringResource(Res.string.live_tv_ui_add_to_my_group_u2026)) { onAddToGroup(); onDismiss() }
                if (onCopyToGroup != null) ActionRow(stringResource(Res.string.live_tv_ui_copy_to_another_group_u2026)) { onCopyToGroup(); onDismiss() }
                if (onRemoveFromGroup != null) ActionRow(stringResource(Res.string.live_tv_ui_remove_from_this_group)) { onRemoveFromGroup(); onDismiss() }
                if (onGuide != null) ActionRow(stringResource(Res.string.live_tv_ui_choose_its_guide_u2026)) { onGuide(); onDismiss() }
                ActionRow(stringResource(Res.string.live_tv_ui_rename)) { renaming = true }
                if (onLogo != null) ActionRow(stringResource(Res.string.live_tv_ui_change_logo_u2026)) { onLogo(); onDismiss() }
                ActionRow(stringResource(Res.string.live_tv_ui_set_channel_number)) { numbering = true }
                ActionRow(stringResource(Res.string.live_tv_ui_picture_shape), shape.label) {
                    val all = PictureShape.entries
                    onShape(all[(all.indexOf(shape) + 1) % all.size])
                }
                if (onRemind != null) ActionRow(stringResource(Res.string.live_tv_ui_remind_me_when_the_next_show_starts)) { onRemind(); onDismiss() }
                ActionRow(stringResource(Res.string.live_tv_ui_hide_channel)) { onHide(); onDismiss() }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_done)) } },
    )
}

/** What you can do with a group chip, from a long press. */
@Composable
internal fun GroupActionsDialog(
    title: String,
    locked: Boolean,
    onRename: (String?) -> Unit,
    onHide: () -> Unit,
    onLock: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    if (renaming) {
        TextPromptDialog(
            title = stringResource(Res.string.live_tv_ui_rename_group),
            initial = title,
            hint = stringResource(Res.string.live_tv_ui_leave_it_empty_to_go_back_to_the_playlist_s_name),
            onOk = { onRename(it.ifBlank { null }); renaming = false; onDismiss() },
            onDismiss = { renaming = false },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 1) },
        text = {
            Column {
                ActionRow(stringResource(Res.string.live_tv_ui_rename)) { renaming = true }
                ActionRow(stringResource(Res.string.live_tv_ui_move_earlier)) { onMove(-1) }
                ActionRow(stringResource(Res.string.live_tv_ui_move_later)) { onMove(1) }
                ActionRow(if (locked) stringResource(Res.string.live_tv_ui_remove_pin_lock) else stringResource(Res.string.live_tv_ui_lock_with_pin)) { onLock(); onDismiss() }
                if (onDelete != null) ActionRow(stringResource(Res.string.live_tv_ui_delete_this_group)) { onDelete(); onDismiss() }
                else ActionRow(stringResource(Res.string.live_tv_ui_hide_group)) { onHide(); onDismiss() }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_done)) } },
    )
}

/** A four-digit PIN: asks for it, or sets a new one when [setting]. */
@Composable
internal fun PinDialog(
    title: String,
    setting: Boolean = false,
    expected: String? = null,
    onPin: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(4); wrong = false },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = wrong,
                    supportingText = if (wrong) ({ Text(stringResource(Res.string.live_tv_ui_that_pin_is_wrong)) }) else null, colors = fieldColors())
                if (setting) Text(
                    stringResource(Res.string.live_tv_ui_choose_four_digits_you_ll_need_them_to_open_locked_groups),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = pin.length == 4,
                onClick = { if (expected != null && pin != expected) wrong = true else onPin(pin) },
            ) { Text(if (setting) stringResource(Res.string.live_tv_ui_set_pin) else stringResource(Res.string.live_tv_ui_unlock)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/** Hidden groups and channels, with a way to bring them back. */
@Composable
internal fun HiddenDialog(
    groups: List<Pair<String, String>>,
    channels: List<LiveChannel>,
    onShowGroup: (String) -> Unit,
    onShowChannel: (String) -> Unit,
    onShowAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_homescreen_hidden)) },
        text = {
            if (groups.isEmpty() && channels.isEmpty()) {
                Text(stringResource(Res.string.live_tv_ui_nothing_is_hidden))
            } else {
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(groups, key = { "g" + it.first }) { (id, title) ->
                        ActionRow("$title  (group)", stringResource(Res.string.layout_ratings_show)) { onShowGroup(id) }
                    }
                    items(channels, key = { "c" + it.key }) { c ->
                        ActionRow(c.name, stringResource(Res.string.layout_ratings_show)) { onShowChannel(c.key) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_close)) } },
        dismissButton = { if (groups.isNotEmpty() || channels.isNotEmpty()) TextButton(onClick = onShowAll) { Text(stringResource(Res.string.live_tv_ui_show_all)) } },
    )
}

/** Pick one of a list. */
@Composable
internal fun PickDialog(
    title: String,
    options: List<Pair<String, String>>,
    emptyText: String = stringResource(Res.string.live_tv_ui_nothing_to_pick),
    extra: Pair<String, () -> Unit>? = null,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (options.isEmpty() && extra == null) Text(emptyText)
            else LazyColumn(Modifier.fillMaxWidth()) {
                if (extra != null) item { ActionRow(extra.first) { extra.second() } }
                items(options, key = { it.first }) { (id, label) -> ActionRow(label) { onPick(id) } }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/** Choose which guide channel feeds a playlist channel. */
@Composable
internal fun GuidePickerDialog(
    channelName: String,
    sources: List<com.nuvio.app.livetv.model.EpgSourceChannels>,
    onPick: (com.nuvio.app.livetv.model.EpgAssignment?) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf(channelName.take(20)) }
    val results = remember(sources, query) {
        val q = query.trim().lowercase()
        if (q.length < 2) emptyList() else sources.flatMap { src ->
            src.channels.filter { e -> e.id.lowercase().contains(q) || e.names.any { it.lowercase().contains(q) } }
                .take(40).map { e -> Triple(src.sourceId, e.id, (e.names.firstOrNull() ?: e.id) + "  \u00B7  " + src.name) }
        }.take(80)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.live_tv_ui_guide_for, channelName), maxLines = 1) },
        text = {
            Column {
                OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, label = { Text(stringResource(Res.string.live_tv_ui_search_the_guide)) }, colors = fieldColors())
                ActionRow(stringResource(Res.string.live_tv_ui_automatic_let_the_app_match_it)) { onPick(null) }
                ActionRow(stringResource(Res.string.live_tv_ui_no_guide_for_this_channel)) { onPick(com.nuvio.app.livetv.model.EpgAssignment.None) }
                LazyColumn(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    items(results, key = { it.first + "|" + it.second }) { (src, id, label) ->
                        ActionRow(label) { onPick(com.nuvio.app.livetv.model.EpgAssignment(src, id)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
