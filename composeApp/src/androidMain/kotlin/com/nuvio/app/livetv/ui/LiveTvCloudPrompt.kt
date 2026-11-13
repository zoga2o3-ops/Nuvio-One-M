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
            title = { Text("Save your Live TV setup?") },
            text = {
                Text("Keep your playlists, guides, favorites, Sports and Calendar choices and settings on every device signed in to your Nuvio account.")
            },
            confirmButton = {
                TextButton(onClick = { sync.answerBackUp(true) }) { Text("Save my setup") }
            },
            dismissButton = {
                TextButton(onClick = { sync.answerBackUp(false) }) { Text("Not now") }
            },
        )
        CloudPrompt.CHOOSE -> AlertDialog(
            onDismissRequest = { sync.answerChoose(CloudChoice.LATER) },
            title = { Text("You have a saved Live TV setup") },
            text = {
                Text("This device has its own setup too. Combine keeps the playlists and guides from both; using the saved setup replaces this device's.")
            },
            confirmButton = {
                TextButton(onClick = { sync.answerChoose(CloudChoice.COMBINE) }) { Text("Combine both") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { sync.answerChoose(CloudChoice.LATER) }) { Text("Not now") }
                    TextButton(onClick = { sync.answerChoose(CloudChoice.USE_SAVED) }) { Text("Use saved") }
                }
            },
        )
        null -> Unit
    }
}
