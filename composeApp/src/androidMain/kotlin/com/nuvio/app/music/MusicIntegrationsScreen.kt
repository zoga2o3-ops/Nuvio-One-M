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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.getString

/** Last.fm scrobbling, Spotify playlist import and WebDAV in one place. */
@Composable
internal fun MusicIntegrationsScreen(
    onClose: () -> Unit,
    onOpenWebDav: () -> Unit,
    onOpenNavidrome: () -> Unit,
    onOpenSources: () -> Unit
) {
    val context = LocalContext.current
    val lastFm by MusicScrobbler.config.collectAsState()
    val webDav by MusicWebDav.config.collectAsState()
    val navidrome by MusicNavidrome.config.collectAsState()
    val addons by MusicAddonRegistry.configs.collectAsState()
    val closeFocus = remember { FocusRequester() }
    val lastFmLoginFocus = remember { FocusRequester() }
    val spotifyImportFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    var lastFmUsername by remember { mutableStateOf("") }
    var lastFmPassword by remember { mutableStateOf("") }
    var lastFmSigningIn by remember { mutableStateOf(false) }
    var lastFmMessage by remember { mutableStateOf<String?>(null) }
    var lastFmError by remember { mutableStateOf<String?>(null) }

    var spotifyUrl by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    var importProgress by remember { mutableStateOf<String?>(null) }
    var importResult by remember { mutableStateOf<String?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    val spotifyWeb by MusicSpotifyWebSession.state.collectAsState()
    val webSync by MusicSpotifyWebSync.state.collectAsState()
    var spotifyMessage by remember { mutableStateOf<String?>(null) }
    val spotifyLogin = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            spotifyMessage = kotlinx.coroutines.runBlocking { getString(Res.string.music_ui_spotify_connected) }
        }
    }

    LaunchedEffect(Unit) {
        MusicWebDav.init(context)
        MusicNavidrome.init(context)
        MusicScrobbler.init(context)
        MusicAddonRegistry.init(context)
        MusicSpotifyWebSession.init(context)
        withFrameNanos { }
        runCatching { closeFocus.requestFocus() }
    }

    BackHandler(onBack = onClose)

    Box(Modifier.fillMaxSize().background(MusicPalette.background)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                IntegrationCloseButton(
                    modifier = Modifier.focusRequester(closeFocus),
                    onClick = onClose
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.compose_settings_page_integrations),
                        color = MusicPalette.text,
                        fontFamily = MusicDisplay,
                        fontSize = 34.sp,
                        lineHeight = 37.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Scrobbling, playlist import and your own server.",
                        color = MusicPalette.textMuted,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                IntegrationCard(
                    icon = Icons.Default.Album,
                    tint = MusicPalette.accentTeal,
                    title = "Navidrome",
                    subtitle = if (navidrome.configured) {
                        "Connected to ${navidrome.url}"
                    } else {
                        stringResource(Res.string.music_ui_link_a_navidrome_subsonic_music_server)
                    }
                ) {
                    IntegrationPill(
                        icon = Icons.Default.Album,
                        label = if (navidrome.configured) stringResource(Res.string.music_ui_open_navidrome) else stringResource(Res.string.music_ui_link_navidrome),
                        primary = !navidrome.configured,
                        onClick = onOpenNavidrome
                    )
                }

                IntegrationCard(
                    icon = Icons.Default.Favorite,
                    tint = MusicPalette.accentCoral,
                    title = "Last.fm",
                    subtitle = when {
                        lastFm.configured && lastFm.enabled -> "Scrobbling as ${lastFm.username}."
                        lastFm.configured -> "Signed in as ${lastFm.username}, currently off."
                        !lastFm.available -> "Last.fm sign-in isn't configured in this build."
                        else -> "Log in with your Last.fm account to scrobble."
                    }
                ) {
                    if (lastFm.configured) {
                        Text(
                            text = "Signed in as ${lastFm.username}.",
                            color = MusicPalette.textSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            IntegrationPill(
                                icon = Icons.Default.Favorite,
                                label = if (lastFm.enabled) stringResource(Res.string.music_ui_turn_scrobbling_off) else stringResource(Res.string.music_ui_turn_scrobbling_on),
                                primary = !lastFm.enabled,
                                onClick = {
                                    MusicScrobbler.setEnabled(context, !lastFm.enabled)
                                    lastFmMessage = if (lastFm.enabled) {
                                        kotlinx.coroutines.runBlocking { getString(Res.string.music_ui_scrobbling_paused) }
                                    } else {
                                        kotlinx.coroutines.runBlocking { getString(Res.string.music_ui_scrobbling_is_on) }
                                    }
                                }
                            )
                            IntegrationPill(
                                icon = Icons.Default.Favorite,
                                label = stringResource(Res.string.music_ui_sign_out),
                                onClick = {
                                    MusicScrobbler.logout(context)
                                    lastFmUsername = ""
                                    lastFmPassword = ""
                                    lastFmMessage = kotlinx.coroutines.runBlocking { getString(Res.string.music_ui_signed_out_of_last_fm) }
                                    lastFmError = null
                                }
                            )
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IntegrationField(
                                label = stringResource(Res.string.live_tv_username),
                                value = lastFmUsername,
                                placeholder = stringResource(Res.string.music_ui_last_fm_username),
                                onValueChange = { lastFmUsername = it },
                                modifier = Modifier.weight(1f),
                                downFocus = lastFmLoginFocus
                            )
                            IntegrationField(
                                label = stringResource(Res.string.compose_auth_password),
                                value = lastFmPassword,
                                placeholder = stringResource(Res.string.music_ui_last_fm_password),
                                onValueChange = { lastFmPassword = it },
                                password = true,
                                modifier = Modifier.weight(1f),
                                downFocus = lastFmLoginFocus
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(Res.string.music_ui_your_password_is_sent_once_to_last_fm_and_never_stored) +
                                stringResource(Res.string.music_ui_only_the_session_key_stays_on_this_device),
                            color = MusicPalette.textMuted,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            IntegrationPill(
                                icon = Icons.Default.Favorite,
                                label = if (lastFmSigningIn) stringResource(Res.string.music_ui_signing_in) else stringResource(Res.string.music_ui_log_in),
                                modifier = Modifier.focusRequester(lastFmLoginFocus),
                                primary = true,
                                enabled = !lastFmSigningIn && lastFm.available,
                                onClick = {
                                    if (!lastFmSigningIn) {
                                        lastFmSigningIn = true
                                        lastFmError = null
                                        lastFmMessage = null
                                        scope.launch {
                                            MusicScrobbler.login(context, lastFmUsername, lastFmPassword)
                                                .fold(
                                                    onSuccess = { account ->
                                                        lastFmPassword = ""
                                                        lastFmMessage = "Signed in as $account. Scrobbling is on."
                                                        lastFmSigningIn = false
                                                    },
                                                    onFailure = { error ->
                                                        lastFmError = error.message
                                                            ?: kotlinx.coroutines.runBlocking { getString(Res.string.music_ui_last_fm_rejected_the_sign_in) }
                                                        lastFmSigningIn = false
                                                    }
                                                )
                                        }
                                    }
                                }
                            )
                        }
                    }
                    lastFmMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(text = it, color = MusicPalette.accentTeal, fontSize = 11.sp)
                    }
                    lastFmError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(text = it, color = MusicPalette.error, fontSize = 11.sp)
                    }
                }

                IntegrationCard(
                    icon = Icons.Default.Cloud,
                    tint = MusicPalette.accent,
                    title = stringResource(Res.string.music_ui_music_sources),
                    subtitle = when {
                        addons.isEmpty() -> "Add BitChord-style addons to search their catalogues."
                        addons.count { it.enabled } == 0 -> "${addons.size} addon(s), all turned off."
                        else -> "${addons.count { it.enabled }} addon(s) connected."
                    }
                ) {
                    IntegrationPill(
                        icon = Icons.Default.Cloud,
                        label = if (addons.isEmpty()) stringResource(Res.string.music_ui_add_a_source) else stringResource(Res.string.music_ui_manage_sources),
                        primary = addons.isEmpty(),
                        onClick = onOpenSources
                    )
                }

                IntegrationCard(
                    icon = Icons.Default.Favorite,
                    tint = MusicPalette.accentTeal,
                    title = stringResource(Res.string.music_ui_spotify_account),
                    subtitle = if (spotifyWeb.connected) {
                        stringResource(
                            Res.string.settings_trakt_connected_as,
                            spotifyWeb.accountName ?: stringResource(Res.string.music_ui_spotify_account)
                        )
                    } else {
                        stringResource(Res.string.music_ui_spotify_account_subtitle)
                    }
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IntegrationPill(
                            icon = Icons.Default.MusicNote,
                            label = when {
                                webSync.busy -> stringResource(Res.string.music_ui_spotify_syncing)
                                spotifyWeb.connected -> stringResource(Res.string.music_ui_spotify_sync)
                                else -> stringResource(Res.string.music_ui_spotify_sign_in)
                            },
                            primary = true,
                            enabled = !webSync.busy,
                            onClick = {
                                if (spotifyWeb.connected) {
                                    MusicSpotifyWebSync.sync(context)
                                } else {
                                    spotifyLogin.launch(
                                        android.content.Intent(context, SpotifyLoginActivity::class.java)
                                    )
                                }
                            }
                        )
                        if (spotifyWeb.connected) {
                            IntegrationPill(
                                icon = Icons.Default.Favorite,
                                label = stringResource(Res.string.settings_debrid_disconnect),
                                enabled = !webSync.busy,
                                onClick = {
                                    MusicSpotifyWebSession.clear(context)
                                    MusicSpotifyWebSync.reset()
                                    spotifyMessage = kotlinx.coroutines.runBlocking { getString(Res.string.music_ui_spotify_disconnected) }
                                }
                            )
                        }
                    }
                    if (spotifyWeb.connected) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(Res.string.music_ui_spotify_session_stays_encrypted),
                            color = MusicPalette.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                    webSync.progress?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(text = it, color = MusicPalette.textSecondary, fontSize = 11.sp)
                    }
                    webSync.result?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(text = it, color = MusicPalette.accentTeal, fontSize = 11.sp)
                    }
                    webSync.error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(text = it, color = MusicPalette.error, fontSize = 11.sp)
                    }
                    spotifyMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(text = it, color = MusicPalette.accentTeal, fontSize = 11.sp)
                    }
                }

                IntegrationCard(
                    icon = Icons.Default.MusicNote,
                    tint = MusicPalette.accentTeal,
                    title = stringResource(Res.string.music_ui_spotify_playlists),
                    subtitle = stringResource(Res.string.music_ui_import_a_public_playlist_by_link_tracks_are_matched_in_the_m)
                ) {
                    IntegrationField(
                        label = stringResource(Res.string.live_tv_ui_playlist_link),
                        value = spotifyUrl,
                        placeholder = stringResource(Res.string.music_ui_https_open_spotify_com_playlist),
                        onValueChange = { spotifyUrl = it },
                        downFocus = spotifyImportFocus
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IntegrationPill(
                            icon = Icons.Default.MusicNote,
                            label = if (importing) stringResource(Res.string.music_ui_importing) else stringResource(Res.string.music_ui_import_playlist),
                            modifier = Modifier.focusRequester(spotifyImportFocus),
                            primary = true,
                            enabled = !importing,
                            onClick = {
                                if (spotifyUrl.isBlank()) {
                                    importError = kotlinx.coroutines.runBlocking { getString(Res.string.music_ui_paste_a_spotify_playlist_link_first) }
                                } else if (!importing) {
                                    importing = true
                                    importProgress = kotlinx.coroutines.runBlocking { getString(Res.string.music_ui_reading_playlist) }
                                    importResult = null
                                    importError = null
                                    scope.launch {
                                        MusicSpotifyImport.import(
                                            context,
                                            spotifyUrl,
                                            onProgress = { done, total ->
                                                withContext(Dispatchers.Main) {
                                                    importProgress = "Matched $done of $total tracks…"
                                                }
                                            }
                                        ).fold(
                                            onSuccess = { result ->
                                                importProgress = null
                                                importResult = "Imported ${result.imported} of " +
                                                    "${result.total} tracks into " +
                                                    "“${result.playlistName}”."
                                                importing = false
                                            },
                                            onFailure = { error ->
                                                importProgress = null
                                                importError = error.message
                                                    ?: kotlinx.coroutines.runBlocking { getString(Res.string.music_ui_the_playlist_couldn_t_be_imported) }
                                                importing = false
                                            }
                                        )
                                    }
                                }
                            }
                        )
                    }
                    importProgress?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(text = it, color = MusicPalette.textSecondary, fontSize = 11.sp)
                    }
                    importResult?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(text = it, color = MusicPalette.accentTeal, fontSize = 11.sp)
                    }
                    importError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(text = it, color = MusicPalette.error, fontSize = 11.sp)
                    }
                }

                IntegrationCard(
                    icon = Icons.Default.Cloud,
                    tint = MusicPalette.accent,
                    title = "WebDAV",
                    subtitle = if (webDav.configured) {
                        "Connected to ${webDav.url}"
                    } else {
                        stringResource(Res.string.music_ui_stream_a_music_folder_from_your_own_server)
                    }
                ) {
                    IntegrationPill(
                        icon = Icons.Default.Cloud,
                        label = if (webDav.configured) stringResource(Res.string.music_ui_open_webdav) else stringResource(Res.string.music_ui_set_up_webdav),
                        onClick = onOpenWebDav
                    )
                }
            }
        }
    }
}

@Composable
private fun IntegrationCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MusicPalette.surface)
            .border(1.dp, MusicPalette.border, RoundedCornerShape(18.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column {
                Text(
                    text = title,
                    color = MusicPalette.text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(text = subtitle, color = MusicPalette.textSecondary, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun IntegrationField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    downFocus: FocusRequester? = null
) {
    MusicTextField(
        label = label,
        value = value,
        placeholder = placeholder,
        onValueChange = onValueChange,
        modifier = modifier,
        password = password,
        downFocus = downFocus
    )
}

@Composable
private fun IntegrationPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(
                when {
                    focused && enabled -> MusicPalette.text
                    primary -> MusicPalette.accent
                    else -> MusicPalette.surfaceRaised
                }
            )
            .border(
                2.dp,
                if (focused && enabled) MusicPalette.text else MusicPalette.border,
                RoundedCornerShape(50)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = when {
                !enabled -> MusicPalette.textMuted
                focused -> MusicPalette.background
                primary -> MusicPalette.background
                else -> MusicPalette.text
            },
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            color = when {
                !enabled -> MusicPalette.textMuted
                focused -> MusicPalette.background
                primary -> MusicPalette.background
                else -> MusicPalette.text
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun IntegrationCloseButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (focused) Color.White else MusicPalette.surfaceRaised)
            .border(
                2.dp,
                if (focused) Color.White else MusicPalette.border,
                CircleShape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(Res.string.music_ui_close_integrations),
            tint = if (focused) MusicPalette.background else MusicPalette.text,
            modifier = Modifier.size(20.dp)
        )
    }
}
