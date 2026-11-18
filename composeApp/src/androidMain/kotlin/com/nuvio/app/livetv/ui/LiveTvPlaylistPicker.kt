package com.nuvio.app.livetv.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nuvio.app.livetv.model.PlaylistSource
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.compose_action_off
import nuvio.composeapp.generated.resources.live_tv_ui_all_playlists
import nuvio.composeapp.generated.resources.live_tv_ui_manage_playlists
import nuvio.composeapp.generated.resources.live_tv_ui_playlists
import org.jetbrains.compose.resources.stringResource

/**
 * The Playlists button in the Live TV header: a small card to choose which playlist the
 * channel list shows, without opening the full playlist manager.
 */
@Composable
internal fun PlaylistPickerDialog(
    playlists: List<PlaylistSource>,
    channelCounts: Map<String, Int>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF17171C),
            contentColor = Color.White,
            tonalElevation = 0.dp,
        ) {
            Column(
                Modifier
                    .widthIn(min = 300.dp, max = 420.dp)
                    .padding(18.dp),
            ) {
                Text(
                    stringResource(Res.string.live_tv_ui_playlists),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    PickerRow(
                        title = stringResource(Res.string.live_tv_ui_all_playlists),
                        subtitle = channelCounts.values.sum().let { if (it == 1) "1 channel" else "$it channels" },
                        selected = selected == null,
                        onClick = { onSelect(null) },
                    )
                    playlists.forEach { playlist ->
                        val count = channelCounts[playlist.id] ?: playlist.channelCount
                        val off = stringResource(Res.string.compose_action_off)
                        val subtitle = buildString {
                            append(count)
                            append(if (count == 1) " channel" else " channels")
                            if (!playlist.enabled) {
                                append(" · ")
                                append(off)
                            }
                            playlist.lastError?.takeIf { it.isNotBlank() }?.let {
                                append(" · ")
                                append(it.take(42))
                            }
                        }
                        PickerRow(
                            title = playlist.name,
                            subtitle = subtitle,
                            selected = selected == playlist.id,
                            enabled = playlist.enabled,
                            onClick = { onSelect(playlist.id) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onManage) {
                        Text(stringResource(Res.string.live_tv_ui_manage_playlists))
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.action_cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
        )
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (enabled) Color.White else Color(0xFF777782),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color(0xFF9B9BA7),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
