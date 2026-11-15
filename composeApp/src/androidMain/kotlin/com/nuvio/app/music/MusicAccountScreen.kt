package com.nuvio.app.music

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.nuvio.app.youtube.YouTubeAccountState
import com.nuvio.app.youtube.YouTubeSignInReturn
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.getString

/** The Music section's YouTube account panel, using the same TV device-code sign-in as YouTube. */
@Composable
internal fun MusicAccountScreen(
    account: YouTubeAccountState,
    profileName: String?,
    onClose: () -> Unit,
    onSignIn: () -> Unit,
    onCancelSignIn: () -> Unit,
    onSelectAccount: (Int) -> Unit,
    onAddAccount: () -> Unit,
    onSignOut: () -> Unit,
    onLinkToNuvioOne: () -> Unit
) {
    BackHandler(onBack = onClose)

    val firstActionFocus = remember { FocusRequester() }

    Box(
        Modifier
            .fillMaxSize()
            .background(MusicPalette.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AccountCloseButton(
                    onClick = onClose,
                    onDown = { runCatching { firstActionFocus.requestFocus() } }
                )
                Column {
                    Text(
                        text = "YouTube Music",
                        color = MusicPalette.text,
                        fontFamily = MusicDisplay,
                        fontSize = 34.sp,
                        lineHeight = 37.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = when {
                            account.code != null -> kotlinx.coroutines.runBlocking { getString(Res.string.music_ui_waiting_for_sign_in_approval) }
                            account.signedIn && account.linkedAccount != null ->
                                "${account.accountName ?: stringResource(Res.string.compose_settings_page_account)} · Linked to Nuvio One (${profileName ?: "this profile"})"
                            account.signedIn -> account.accountName ?: stringResource(Res.string.compose_settings_page_account)
                            account.linkedAccount != null ->
                                "Linked to Nuvio One as ${account.linkedAccount.name}"
                            else -> kotlinx.coroutines.runBlocking { getString(Res.string.music_ui_not_signed_in) }
                        },
                        color = MusicPalette.textMuted,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            when {
                account.code != null -> SignInCodePanel(account, profileName, onCancelSignIn, firstActionFocus)
                account.signedIn -> AccountsPanel(
                    account = account,
                    profileName = profileName,
                    onSelectAccount = onSelectAccount,
                    onAddAccount = onAddAccount,
                    onSignOut = onSignOut,
                    onLinkToNuvioOne = onLinkToNuvioOne,
                    firstActionFocus = firstActionFocus
                )
                else -> SignedOutPanel(account, profileName, onSignIn, firstActionFocus)
            }
        }
    }
}

@Composable
private fun SignInCodePanel(
    account: YouTubeAccountState,
    profileName: String?,
    onCancel: () -> Unit,
    firstActionFocus: FocusRequester
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val link = account.activateLink
    val code = account.code.orEmpty()

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstActionFocus.requestFocus() }
    }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = stringResource(Res.string.music_ui_sign_in_to_youtube_music),
            color = MusicPalette.text,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(Res.string.music_ui_we_opened_youtube_in_your_browser_enter_this_code_to_sign_in) +
                "It links the account to your Nuvio One profile, ${profileName ?: "this profile"}.",
            color = MusicPalette.textSecondary,
            fontSize = 15.sp
        )
        Text(
            text = code,
            color = MusicPalette.text,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(Res.string.music_ui_waiting_for_you_to_approve_it),
            color = MusicPalette.textMuted,
            fontSize = 14.sp
        )
        AccountAction(
            icon = Icons.Default.PlayArrow,
            label = stringResource(Res.string.music_ui_open_youtube),
            firstFocus = firstActionFocus,
            onClick = { link?.let { YouTubeSignInReturn.openActivatePage(context, it) } }
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.settings_tracking_device_code_copy))
            }
            TextButton(onClick = onCancel) { Text(stringResource(Res.string.action_cancel)) }
        }
    }
}

@Composable
private fun AccountsPanel(
    account: YouTubeAccountState,
    profileName: String?,
    onSelectAccount: (Int) -> Unit,
    onAddAccount: () -> Unit,
    onSignOut: () -> Unit,
    onLinkToNuvioOne: () -> Unit,
    firstActionFocus: FocusRequester
) {
    LaunchedEffect(account.accounts.size, account.accountName) {
        withFrameNanos { }
        runCatching { firstActionFocus.requestFocus() }
    }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = account.linkedAccount?.let {
                "Linked to Nuvio One · ${profileName ?: "this profile"} as ${it.name}"
            } ?: stringResource(Res.string.music_ui_not_linked_to_nuvio_one),
            color = MusicPalette.textMuted,
            fontSize = 13.sp
        )
        account.accounts.forEachIndexed { index, item ->
            AccountAction(
                icon = if (item.selected) Icons.Default.Check else Icons.Default.AccountCircle,
                label = item.name,
                detail = item.detail,
                selected = item.selected,
                firstFocus = if (index == 0) firstActionFocus else null,
                onClick = { onSelectAccount(item.id) }
            )
        }
        Spacer(Modifier.height(8.dp))
        AccountAction(
            icon = Icons.Default.Add,
            label = stringResource(Res.string.music_ui_add_another_account),
            firstFocus = if (account.accounts.isEmpty()) firstActionFocus else null,
            onClick = onAddAccount
        )
        if (account.linkedAccount == null) {
            AccountAction(
                icon = Icons.Default.Link,
                label = stringResource(Res.string.youtube_settings_link_to_nuvio),
                onClick = onLinkToNuvioOne
            )
        }
        AccountAction(
            icon = Icons.Default.Logout,
            label = stringResource(Res.string.music_ui_sign_out_of_youtube_music),
            onClick = onSignOut
        )
    }
}

@Composable
private fun SignedOutPanel(
    account: YouTubeAccountState,
    profileName: String?,
    onSignIn: () -> Unit,
    firstActionFocus: FocusRequester
) {
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstActionFocus.requestFocus() }
    }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = stringResource(Res.string.music_ui_sign_in_to_youtube_music),
            color = MusicPalette.text,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(Res.string.music_ui_your_likes_playlists_and_personalised_home_follow_the_google) +
                stringResource(Res.string.music_ui_use_for_youtube_and_onetube_signing_in_links_it_to_your_nuvi) +
                "${profileName ?: "this profile"}.",
            color = MusicPalette.textSecondary,
            fontSize = 15.sp
        )
        account.linkedAccount?.let { linked ->
            Text(
                text = "Linked to Nuvio One as ${linked.name}. Sign in on this device to use it.",
                color = MusicPalette.textMuted,
                fontSize = 13.sp
            )
        }
        account.error?.let {
            Text(it, color = MusicPalette.error, fontSize = 13.sp)
        }
        Spacer(Modifier.height(4.dp))
        AccountAction(
            icon = Icons.Default.AccountCircle,
            label = stringResource(Res.string.music_ui_sign_in_with_youtube),
            firstFocus = firstActionFocus,
            onClick = onSignIn
        )
    }
}

/**
 * One focus target for a menu action. Up/Down are handled explicitly so the panel works on
 * Fire TV even though it lives inside a scroll container.
 */
@Composable
private fun AccountAction(
    icon: ImageVector,
    label: String,
    detail: String? = null,
    selected: Boolean = false,
    firstFocus: FocusRequester? = null,
    onClick: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier)
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) MusicPalette.surfaceRaised else Color.Transparent)
            .border(
                2.dp,
                when {
                    focused -> MusicPalette.text
                    selected -> MusicPalette.accent
                    else -> Color.Transparent
                },
                RoundedCornerShape(12.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter -> {
                        onClick()
                        true
                    }
                    Key.DirectionUp -> {
                        focusManager.moveFocus(FocusDirection.Up)
                        true
                    }
                    Key.DirectionDown -> {
                        focusManager.moveFocus(FocusDirection.Down)
                        true
                    }
                    else -> false
                }
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected && !focused) MusicPalette.accent else MusicPalette.textSecondary,
            modifier = Modifier.size(24.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                color = MusicPalette.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            detail?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    color = MusicPalette.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (selected) {
            Text(
                text = stringResource(Res.string.series_status_current),
                color = MusicPalette.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun AccountCloseButton(onClick: () -> Unit, onDown: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(if (focused) Color.White else Color(0x33FFFFFF))
            .border(
                2.dp,
                if (focused) Color.White else Color.Transparent,
                CircleShape
            )
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> {
                        onDown()
                        true
                    }
                    Key.DirectionCenter, Key.Enter -> {
                        onClick()
                        true
                    }
                    else -> false
                }
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = stringResource(Res.string.music_ui_close_account_panel),
            tint = if (focused) MusicPalette.background else Color.White,
            modifier = Modifier.size(26.dp)
        )
    }
}
