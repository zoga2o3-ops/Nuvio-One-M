package com.nuvio.app.youtube

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.features.profiles.ProfileRepository
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * The code YouTube is waiting for. The activate page is opened automatically as soon as the code
 * arrives, so signing in starts on YouTube's own site and comes back to Nuvio One when approved.
 */
@Composable
fun YouTubeSignInDialog(accounts: YouTubeAccountViewModel, account: YouTubeAccountState) {
    val code = account.code ?: return
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val link = account.activateLink
    val profileState by ProfileRepository.state.collectAsStateWithLifecycle()
    val profileName = profileState.activeProfile?.name ?: "this Nuvio One profile"

    AlertDialog(
        onDismissRequest = accounts::cancelSignIn,
        title = { Text(stringResource(Res.string.youtube_ui_link_youtube_to_nuvio_one)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(Res.string.youtube_ui_enter_this_code_on_the_youtube_page_we_opened_signing_in_lin) +
                        "to your Nuvio One profile, $profileName.",
                )
                Text(code, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = {
            Button(onClick = { link?.let { YouTubeSignInReturn.openActivatePage(context, it) } }) {
                Text(stringResource(Res.string.music_ui_open_youtube))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { clipboard.setText(AnnotatedString(code)) }) { Text(stringResource(Res.string.settings_tracking_device_code_copy)) }
                TextButton(onClick = accounts::cancelSignIn) { Text(stringResource(Res.string.action_cancel)) }
            }
        },
    )
}

/** Who's watching: switch between the accounts and channel profiles on this device, add another, or sign out. */
@Composable
fun YouTubeAccountsDialog(
    account: YouTubeAccountState,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
    profileName: String? = null,
    onLinkToNuvioOne: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.profile_who_is_watching)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                account.linkedAccount?.let { linked ->
                    Text(
                        "Linked to Nuvio One · ${profileName ?: "this profile"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.7f),
                    )
                    Text(linked.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                account.accounts.forEach { a ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onSelect(a.id) }.padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (a.avatar != null) {
                            AsyncImage(
                                model = a.avatar, contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.size(40.dp).clip(CircleShape),
                            )
                        } else {
                            Text(
                                a.name.take(1).uppercase(),
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF3A3A40)).padding(top = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(a.name + if (a.selected) "   ✓" else "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            a.detail?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                TextButton(onClick = onAdd) { Text(stringResource(Res.string.music_ui_add_another_account)) }
                if (account.linkedAccount == null && onLinkToNuvioOne != null) {
                    TextButton(onClick = onLinkToNuvioOne) { Text(stringResource(Res.string.youtube_settings_link_to_nuvio)) }
                }
                TextButton(onClick = onSignOut) { Text(stringResource(Res.string.youtube_ui_sign_out_of, account.accountName ?: stringResource(Res.string.youtube_ui_this_account))) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_close)) } },
    )
}
