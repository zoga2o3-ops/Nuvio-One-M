package com.nuvio.app.livetv.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.livetv.LiveTv
import com.nuvio.app.livetv.sync.CloudChoice
import com.nuvio.app.livetv.sync.CloudPrompt
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * The one-time Setup Sync question, shown over Live TV when a Nuvio account is signed in.
 * A device with its own setup is never changed without an answer.
 */
@Composable
fun LiveTvCloudPrompt() {
    val context = LocalContext.current.applicationContext
    val sync = remember(context) { LiveTv.get(context).cloudSync }
    val state by sync.state.collectAsStateWithLifecycle()

    when (state.prompt) {
        CloudPrompt.BACK_UP -> AlertDialog(
            onDismissRequest = { sync.answerBackUp(false) },
            title = { Text(stringResource(Res.string.live_tv_ui_save_your_live_tv_setup)) },
            text = {
                Text(stringResource(Res.string.live_tv_ui_keep_your_playlists_guides_favorites_sports_and_calendar_cho))
            },
            confirmButton = {
                TextButton(onClick = { sync.answerBackUp(true) }) { Text(stringResource(Res.string.live_tv_ui_save_my_setup)) }
            },
            dismissButton = {
                TextButton(onClick = { sync.answerBackUp(false) }) { Text(stringResource(Res.string.live_tv_ui_not_now)) }
            },
        )
        CloudPrompt.CHOOSE -> AlertDialog(
            onDismissRequest = { sync.answerChoose(CloudChoice.LATER) },
            title = { Text(stringResource(Res.string.live_tv_ui_you_have_a_saved_live_tv_setup)) },
            text = {
                Text(stringResource(Res.string.live_tv_ui_this_device_has_its_own_setup_too_combine_keeps_the_playlist))
            },
            confirmButton = {
                TextButton(onClick = { sync.answerChoose(CloudChoice.COMBINE) }) { Text(stringResource(Res.string.live_tv_ui_combine_both)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { sync.answerChoose(CloudChoice.LATER) }) { Text(stringResource(Res.string.live_tv_ui_not_now)) }
                    TextButton(onClick = { sync.answerChoose(CloudChoice.USE_SAVED) }) { Text(stringResource(Res.string.live_tv_ui_use_saved)) }
                }
            },
        )
        null -> Unit
    }
}
