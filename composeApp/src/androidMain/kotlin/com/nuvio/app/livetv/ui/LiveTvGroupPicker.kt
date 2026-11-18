package com.nuvio.app.livetv.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
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
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.live_tv_ui_groups
import nuvio.composeapp.generated.resources.settings_homescreen_hidden
import org.jetbrains.compose.resources.stringResource

/** One row in the group picker. */
internal data class GroupChoice(
    val id: String,
    val title: String,
    val count: Int = 0,
    val locked: Boolean = false,
)

/**
 * The Groups button in the Channels heading: the same card style as Playlists, so the channel
 * page keeps a single row of controls.
 */
@Composable
internal fun GroupPickerDialog(
    choices: List<GroupChoice>,
    selected: String,
    onSelect: (String) -> Unit,
    onHidden: (() -> Unit)? = null,
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
                    stringResource(Res.string.live_tv_ui_groups),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    choices.forEach { choice ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(choice.id) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected == choice.id, onClick = null)
                            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                                Text(
                                    choice.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                if (choice.count > 0) {
                                    Text(
                                        if (choice.count == 1) "1 channel" else "${choice.count} channels",
                                        maxLines = 1,
                                        color = Color(0xFF9B9BA7),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            if (choice.locked) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFF9B9BA7),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onHidden != null) {
                        TextButton(onClick = onHidden) {
                            Text(stringResource(Res.string.settings_homescreen_hidden))
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.action_cancel))
                    }
                }
            }
        }
    }
}
