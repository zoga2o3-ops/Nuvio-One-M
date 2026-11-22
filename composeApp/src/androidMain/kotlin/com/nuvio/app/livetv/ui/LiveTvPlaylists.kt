package com.nuvio.app.livetv.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nuvio.app.livetv.model.EpgSource
import com.nuvio.app.livetv.model.PlaylistSource
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Your playlists: switch one off, remove it, update everything, or add another. */
@Composable
fun PlaylistsDialog(
    playlists: List<PlaylistSource>,
    epgs: List<EpgSource>,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onAddEpg: () -> Unit,
    onRefresh: () -> Unit,
    onRemove: (String) -> Unit,
    onEnabled: (String, Boolean) -> Unit,
    onRemoveEpg: (String) -> Unit,
    onEpgEnabled: (String, Boolean) -> Unit,
    accounts: Map<String, com.nuvio.app.livetv.model.PlaylistAccount> = emptyMap(),
    onEdit: (PlaylistSource) -> Unit = {},
) {
    var editing by remember { mutableStateOf<PlaylistSource?>(null) }
    var confirmRemove by remember { mutableStateOf<PlaylistSource?>(null) }
    var confirmRemoveEpg by remember { mutableStateOf<EpgSource?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF0E0E0E)) {
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.live_tv_ui_playlists_and_guide), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_done)) }
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                        playlists.forEach { p ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(p.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        listOfNotNull(
                                            if (p.isStalker) stringResource(Res.string.live_tv_ui_stalker_portal) else if (p.isXtream) "Xtream" else "M3U",
                                            "${p.channelCount} channels".takeIf { p.channelCount > 0 },
                                            accounts[p.id]?.expiryLabel()?.let { "expires $it" },
                                            p.lastError,
                                        ).joinToString("  ·  "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (p.lastError != null) Color(0xFFFF6B6B) else Color(0xFFB0B0B0),
                                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                TextButton(onClick = { editing = p }) { Text(stringResource(Res.string.action_edit)) }
                                Switch(checked = p.enabled, onCheckedChange = { onEnabled(p.id, it) })
                            }
                            HorizontalDivider(color = Color(0x22FFFFFF))
                        }
                        Row(Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = onAdd) { Text(stringResource(Res.string.live_tv_ui_add_a_playlist)) }
                            if (playlists.isNotEmpty()) TextButton(onClick = onRefresh) { Text(stringResource(Res.string.live_tv_ui_update_all)) }
                        }
                        Text(stringResource(Res.string.live_tv_ui_guide_sources), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
                        Text(
                            stringResource(Res.string.live_tv_ui_playlists_with_a_guide_link_bring_their_own_add_an_xmltv_lin),
                            style = MaterialTheme.typography.bodySmall, color = Color(0xFFB0B0B0),
                        )
                        epgs.forEach { g ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(g.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        listOfNotNull(
                                            "${g.programCount} programmes".takeIf { g.programCount > 0 },
                                            g.lastError,
                                        ).joinToString("  ·  ").ifBlank { stringResource(Res.string.live_tv_ui_waiting_for_the_first_update) },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (g.lastError != null) Color(0xFFFF6B6B) else Color(0xFFB0B0B0),
                                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                TextButton(onClick = { confirmRemoveEpg = g }) { Text(stringResource(Res.string.action_remove)) }
                                Switch(checked = g.enabled, onCheckedChange = { onEpgEnabled(g.id, it) })
                            }
                            HorizontalDivider(color = Color(0x22FFFFFF))
                        }
                        Row(Modifier.padding(vertical = 16.dp)) {
                            Button(onClick = onAddEpg) { Text(stringResource(Res.string.live_tv_ui_add_a_guide)) }
                        }
                    }
                }
            }
        }
    }
    editing?.let { p ->
        PlaylistEditDialog(
            playlist = p,
            account = accounts[p.id],
            onSave = { onEdit(it); editing = null },
            onRemove = { confirmRemove = p; editing = null },
            onDismiss = { editing = null },
        )
    }
    confirmRemoveEpg?.let { g ->
        AlertDialog(
            onDismissRequest = { confirmRemoveEpg = null },
            title = { Text(stringResource(Res.string.live_tv_ui_remove_named, g.name)) },
            text = { Text(stringResource(Res.string.live_tv_ui_channels_using_it_lose_their_programme_listings)) },
            confirmButton = { TextButton(onClick = { onRemoveEpg(g.id); confirmRemoveEpg = null }) { Text(stringResource(Res.string.action_remove)) } },
            dismissButton = { TextButton(onClick = { confirmRemoveEpg = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
    confirmRemove?.let { p ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text(stringResource(Res.string.live_tv_ui_remove_named, p.name)) },
            text = { Text(stringResource(Res.string.live_tv_ui_its_channels_leave_your_list_you_can_add_it_again_later)) },
            confirmButton = { TextButton(onClick = { onRemove(p.id); confirmRemove = null }) { Text(stringResource(Res.string.action_remove)) } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

/** Add a guide: the link to an XMLTV file (plain or .gz). */
@Composable
fun AddEpgDialog(onDismiss: () -> Unit, onAdd: (name: String, url: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.live_tv_ui_add_a_guide)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(Res.string.live_tv_ui_name_optional)) }, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                OutlinedTextField(
                    url, { url = it }, label = { Text(stringResource(Res.string.live_tv_ui_xmltv_link)) }, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), maxLines = 3,
                )
            }
        },
        confirmButton = { TextButton(enabled = url.isNotBlank(), onClick = { onAdd(name, url) }) { Text(stringResource(Res.string.youtube_settings_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/** Add a playlist link (M3U), or an Xtream login. */
@Composable
fun AddPlaylistDialog(
    onDismiss: () -> Unit,
    onM3u: (name: String, url: String) -> Unit,
    onXtream: (name: String, server: String, username: String, password: String) -> Unit,
    onStalker: (name: String, portal: String, mac: String) -> Unit = { _, _, _ -> },
) {
    // 0: M3U link, 1: Xtream login, 2: Stalker portal
    var mode by remember { mutableStateOf(0) }
    val xtream = mode == 1
    val stalker = mode == 2
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var portal by remember { mutableStateOf("") }
    var mac by remember { mutableStateOf("") }
    var m3uHttps by remember { mutableStateOf(false) }
    var xtreamHttps by remember { mutableStateOf(false) }
    val ready = when (mode) {
        1 -> server.isNotBlank()
        2 -> portal.isNotBlank() && com.nuvio.app.livetv.data.StalkerClient.isValidMac(mac)
        else -> url.isNotBlank()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.live_tv_ui_add_a_playlist)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text(stringResource(Res.string.live_tv_ui_m3u_link)) })
                    FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("Xtream") })
                    FilterChip(selected = mode == 2, onClick = { mode = 2 }, label = { Text("Stalker", maxLines = 1) })
                }
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(Res.string.live_tv_ui_name_optional)) }, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                if (stalker) {
                    OutlinedTextField(
                        portal, { portal = it }, singleLine = true, label = { Text(stringResource(Res.string.live_tv_ui_portal_address)) }, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                        placeholder = { Text(stringResource(Res.string.live_tv_ui_http_host_c), color = Color(0xFF808080)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    OutlinedTextField(
                        mac, { mac = it }, singleLine = true, label = { Text(stringResource(Res.string.live_tv_stalker_mac)) }, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                        placeholder = { Text(stringResource(Res.string.live_tv_ui_00_1a_79_ab_cd_ef), color = Color(0xFF808080)) },
                        isError = mac.isNotBlank() && !com.nuvio.app.livetv.data.StalkerClient.isValidMac(mac),
                    )
                    Text(stringResource(Res.string.live_tv_ui_use_the_mac_address_your_provider_registered_for_you), style = MaterialTheme.typography.bodySmall, color = Color(0xFFB0B0B0))
                } else if (!xtream) {
                    OutlinedTextField(
                        url, { text ->
                            url = text
                            val typed = text.trimStart()
                            when {
                                typed.startsWith("https://", ignoreCase = true) -> m3uHttps = true
                                typed.startsWith("http://", ignoreCase = true) -> m3uHttps = false
                            }
                        }, label = { Text(stringResource(Res.string.live_tv_ui_playlist_link)) }, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), maxLines = 3,
                        placeholder = { Text("host/playlist.m3u", color = Color(0xFF808080)) },
                    )
                    HttpsSwitch(
                        checked = m3uHttps,
                        onCheckedChange = {
                            m3uHttps = it
                            url = PlaylistSource.stripPlaylistAddressScheme(url)
                        },
                    )
                } else {
                    OutlinedTextField(
                        server, { text ->
                            server = text
                            val typed = text.trimStart()
                            when {
                                typed.startsWith("https://", ignoreCase = true) -> xtreamHttps = true
                                typed.startsWith("http://", ignoreCase = true) -> xtreamHttps = false
                            }
                        }, singleLine = true, label = { Text(stringResource(Res.string.live_tv_ui_server_address)) }, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        placeholder = { Text("host:port", color = Color(0xFF808080)) },
                    )
                    HttpsSwitch(
                        checked = xtreamHttps,
                        onCheckedChange = {
                            xtreamHttps = it
                            server = PlaylistSource.stripPlaylistAddressScheme(server)
                        },
                    )
                    OutlinedTextField(user, { user = it }, singleLine = true, label = { Text(stringResource(Res.string.live_tv_username)) }, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                    OutlinedTextField(
                        pass, { pass = it }, singleLine = true, label = { Text(stringResource(Res.string.compose_auth_password)) }, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = ready,
                onClick = {
                    when (mode) {
                        1 -> onXtream(
                            name,
                            PlaylistSource.completePlaylistAddress(server, xtreamHttps),
                            user,
                            pass,
                        )
                        2 -> onStalker(name, portal, mac)
                        else -> onM3u(name, PlaylistSource.completePlaylistAddress(url, m3uHttps))
                    }
                },
            ) { Text(stringResource(Res.string.youtube_settings_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/** Outlines that show up on the dark dialog background. */
@Composable
internal fun fieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = Color(0x66FFFFFF),
    focusedBorderColor = Color.White,
    unfocusedLabelColor = Color(0xFFB0B0B0),
    focusedLabelColor = Color.White,
)

@Composable
private fun HttpsSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.live_tv_ui_use_https),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(Res.string.live_tv_ui_use_https_description),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB0B0B0),
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** One playlist's options: its name, user agent, what to import, stream format, logos and account details. */
@Composable
private fun PlaylistEditDialog(
    playlist: PlaylistSource,
    account: com.nuvio.app.livetv.model.PlaylistAccount?,
    onSave: (PlaylistSource) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(playlist.name) }
    var address by remember {
        mutableStateOf(
            if (playlist.isXtream) playlist.xtreamServer
            else if (playlist.isStalker) ""
            else playlist.url
        )
    }
    var addressHttps by remember { mutableStateOf(address.startsWith("https://", ignoreCase = true)) }
    var agent by remember { mutableStateOf(playlist.userAgent) }
    var zone by remember { mutableStateOf(playlist.serverTimezone) }
    var live by remember { mutableStateOf(playlist.importLive) }
    var vod by remember { mutableStateOf(playlist.importVod) }
    var embedded by remember { mutableStateOf(playlist.useEmbeddedEpg) }
    var format by remember { mutableStateOf(playlist.streamFormat) }
    var logos by remember { mutableStateOf(playlist.logoSource) }
    var portal by remember { mutableStateOf(playlist.stalkerPortal) }
    var mac by remember { mutableStateOf(playlist.stalkerMac) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(playlist.name, maxLines = 1) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(stringResource(Res.string.stream_info_name)) }, colors = fieldColors())
                if (!playlist.isStalker) {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { text ->
                            address = text
                            val typed = text.trimStart()
                            when {
                                typed.startsWith("https://", ignoreCase = true) -> addressHttps = true
                                typed.startsWith("http://", ignoreCase = true) -> addressHttps = false
                            }
                        },
                        singleLine = playlist.isXtream,
                        label = {
                            Text(
                                stringResource(
                                    if (playlist.isXtream) Res.string.live_tv_ui_server_address
                                    else Res.string.live_tv_ui_playlist_link
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        maxLines = if (playlist.isXtream) 1 else 2,
                    )
                    HttpsSwitch(
                        checked = addressHttps,
                        onCheckedChange = {
                            addressHttps = it
                            address = PlaylistSource.stripPlaylistAddressScheme(address)
                        },
                    )
                }
                OutlinedTextField(
                    value = agent, onValueChange = { agent = it }, singleLine = true,
                    label = { Text(stringResource(Res.string.live_tv_ui_user_agent_leave_empty_for_the_default)) }, colors = fieldColors())
                OutlinedTextField(
                    value = zone, onValueChange = { zone = it }, singleLine = true,
                    label = { Text(stringResource(Res.string.live_tv_ui_server_time_zone_optional_e_g_europe_london)) }, colors = fieldColors())
                if (playlist.isStalker) {
                    OutlinedTextField(
                        value = portal, onValueChange = { portal = it }, singleLine = true, label = { Text(stringResource(Res.string.live_tv_ui_portal_address)) }, colors = fieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    OutlinedTextField(
                        value = mac, onValueChange = { mac = it }, singleLine = true, label = { Text(stringResource(Res.string.live_tv_stalker_mac)) }, colors = fieldColors(),
                        isError = mac.isNotBlank() && !com.nuvio.app.livetv.data.StalkerClient.isValidMac(mac),
                    )
                }
                if (playlist.isXtream) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.live_tv_ui_live_tv_channels), Modifier.weight(1f)); Switch(checked = live, onCheckedChange = { live = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.live_tv_ui_movies_and_series_on_demand), Modifier.weight(1f)); Switch(checked = vod, onCheckedChange = { vod = it })
                    }
                    Text(stringResource(Res.string.live_tv_ui_stream_format), style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("auto" to stringResource(Res.string.youtube_settings_auto), "ts" to "TS", "m3u8" to "HLS").forEach { (v, l) ->
                            TextButton(onClick = { format = v }) {
                                Text(l, color = if (format == v) Color(0xFFE53935) else LocalContentColor.current)
                            }
                        }
                    }
                } else if (!playlist.isStalker) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.live_tv_ui_movies_and_series_on_demand), Modifier.weight(1f)); Switch(checked = vod, onCheckedChange = { vod = it })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.live_tv_ui_use_the_guide_the_playlist_points_to), Modifier.weight(1f)); Switch(checked = embedded, onCheckedChange = { embedded = it })
                }
                Text(stringResource(Res.string.live_tv_ui_channel_logos_from), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("playlist" to "The playlist", "epg" to "The guide").forEach { (v, l) ->
                        TextButton(onClick = { logos = v }) {
                            Text(l, color = if (logos == v) Color(0xFFE53935) else LocalContentColor.current)
                        }
                    }
                }
                if (account != null && (account.expiresAtMs > 0 || account.status.isNotBlank() || account.maxConnections > 0)) {
                    HorizontalDivider()
                    Text(stringResource(Res.string.compose_settings_page_account), style = MaterialTheme.typography.titleSmall)
                    account.expiryLabel()?.let { Text(stringResource(Res.string.live_tv_ui_expires, it), color = if (account.expiringSoon()) Color(0xFFFF6B6B) else LocalContentColor.current) }
                    if (account.status.isNotBlank()) Text(stringResource(Res.string.live_tv_ui_status, account.status))
                    if (account.maxConnections > 0) Text(stringResource(Res.string.live_tv_ui_connections_in_use, account.activeConnections, account.maxConnections))
                }
                TextButton(onClick = onRemove) { Text(stringResource(Res.string.live_tv_ui_remove_this_playlist), color = Color(0xFFFF6B6B)) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = playlist.isStalker || address.isNotBlank(),
                onClick = {
                    onSave(
                        playlist.copy(
                            name = name.trim().ifBlank { playlist.name },
                            url = if (!playlist.isStalker && !playlist.isXtream) {
                                PlaylistSource.completePlaylistAddress(address, addressHttps)
                            } else {
                                playlist.url
                            },
                            xtreamServer = if (playlist.isXtream) {
                                PlaylistSource.completePlaylistAddress(address, addressHttps)
                            } else {
                                playlist.xtreamServer
                            },
                            userAgent = agent.trim(),
                            serverTimezone = zone.trim(),
                            importLive = live, importVod = vod, useEmbeddedEpg = embedded, streamFormat = format, logoSource = logos,
                            stalkerPortal = if (playlist.isStalker) portal.trim().ifBlank { playlist.stalkerPortal } else playlist.stalkerPortal,
                            stalkerMac = if (playlist.isStalker) mac.trim().ifBlank { playlist.stalkerMac } else playlist.stalkerMac,
                        )
                    )
                },
            ) { Text(stringResource(Res.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/** Which shared playlists this profile shows. The sources themselves stay shared. */
@Composable
fun PlaylistVisibilityDialog(
    playlists: List<PlaylistSource>,
    hidden: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF0E0E0E)) {
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(Res.string.live_tv_ui_playlists_on_this_profile),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_done)) }
                    }
                    Text(
                        stringResource(Res.string.live_tv_ui_playlists_on_this_profile_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB0B0B0),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                        playlists.forEach { p ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(
                                        p.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        if (p.isStalker) stringResource(Res.string.live_tv_ui_stalker_portal)
                                        else if (p.isXtream) "Xtream" else "M3U",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFB0B0B0),
                                    )
                                }
                                Switch(checked = p.id !in hidden, onCheckedChange = { onToggle(p.id, it) })
                            }
                            HorizontalDivider(color = Color(0x22FFFFFF))
                        }
                    }
                }
            }
        }
    }
}
