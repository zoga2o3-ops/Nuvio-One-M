package com.nuvio.app.features.settings

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.vpn.VpnHost
import com.nuvio.vpn.android.VpnSettingsController
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * WireGuard-only VPN settings. OpenVPN is deliberately not exposed.
 *
 * The profile list, encrypted storage and connection state come from [VpnSettingsController];
 * this composable only draws them. A profile import never starts protection by itself.
 */
@Composable
fun VpnSettingsContent() {
    val context = LocalContext.current
    var state by remember { mutableStateOf<VpnSettingsController.State?>(null) }
    var connection by remember { mutableStateOf<VpnSettingsController.Connection?>(null) }
    val controller = remember(context) {
        VpnSettingsController(context) { next -> state = next }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) controller.importProfile(uri, false)
    }

    DisposableEffect(controller) {
        controller.refresh()
        onDispose { controller.close() }
    }
    LaunchedEffect(controller) {
        while (isActive) {
            connection = controller.connection()
            delay(1_000)
        }
    }
    // Never expose the profile list or private-key material in recents screenshots.
    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    val ui = state
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SettingsGroup(isTablet = false) {
            SettingsNavigationRow(
                title = stringResource(Res.string.vpn_status),
                description = connection?.detail ?: stringResource(Res.string.vpn_not_connected),
                icon = Icons.Default.Security,
                isTablet = false,
                onClick = { connection = controller.connection() },
            )
            SettingsGroupDivider(isTablet = false)
            SettingsNavigationRow(
                title = connection?.title ?: stringResource(Res.string.vpn_off),
                description = connection?.traffic,
                isTablet = false,
                onClick = { controller.turnOff() },
            )
        }

        SettingsGroup(isTablet = false) {
            SettingsNavigationRow(
                title = stringResource(Res.string.vpn_wireguard_profile),
                description = if (ui?.profiles?.isEmpty() != false) {
                    stringResource(Res.string.vpn_import_conf)
                } else {
                    stringResource(Res.string.vpn_choose_profile)
                },
                isTablet = false,
                onClick = { picker.launch(arrayOf("*/*")) },
            )
            val profiles = ui?.profiles.orEmpty().filter { it.kind.equals("WireGuard", ignoreCase = true) }
            profiles.forEach { profile ->
                SettingsGroupDivider(isTablet = false)
                SettingsNavigationRow(
                    title = profile.name,
                    description = profile.kind,
                    isTablet = false,
                    enabled = ui?.busy != true && profile.readable,
                    trailingContent = {
                        if (profile.selected) {
                            Icon(Icons.Default.Check, contentDescription = stringResource(Res.string.vpn_selected))
                        }
                        IconButton(onClick = { controller.remove(profile.id) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.vpn_remove))
                        }
                    },
                    onClick = { controller.selectProfile(profile.id) },
                )
            }
        }

        SettingsGroup(isTablet = false) {
            val currentMode = ui?.mode ?: VpnSettingsController.MODE_OFF
            VpnModeRow(
                label = stringResource(Res.string.vpn_mode_off),
                detail = stringResource(Res.string.vpn_mode_off_detail),
                selected = currentMode == VpnSettingsController.MODE_OFF,
                enabled = ui?.busy != true,
            ) { controller.setMode(VpnSettingsController.MODE_OFF) }
            SettingsGroupDivider(isTablet = false)
            VpnModeRow(
                label = stringResource(Res.string.vpn_mode_http),
                detail = stringResource(Res.string.vpn_mode_http_detail),
                selected = currentMode == VpnSettingsController.MODE_HTTP,
                enabled = ui?.busy != true,
            ) { controller.setMode(VpnSettingsController.MODE_HTTP) }
            SettingsGroupDivider(isTablet = false)
            VpnModeRow(
                label = stringResource(Res.string.vpn_mode_http_https),
                detail = stringResource(Res.string.vpn_mode_http_https_detail),
                selected = currentMode == VpnSettingsController.MODE_HTTP_HTTPS,
                enabled = ui?.busy != true,
            ) { controller.setMode(VpnSettingsController.MODE_HTTP_HTTPS) }
            SettingsGroupDivider(isTablet = false)
            VpnModeRow(
                label = stringResource(Res.string.vpn_mode_app),
                detail = stringResource(Res.string.vpn_mode_app_detail),
                selected = currentMode == VpnSettingsController.MODE_APP,
                enabled = ui?.busy != true,
            ) { controller.setMode(VpnSettingsController.MODE_APP) }
        }

        SettingsGroup(isTablet = false) {
            SettingsSwitchRow(
                title = stringResource(Res.string.vpn_use_all_profiles),
                description = stringResource(Res.string.vpn_use_all_profiles_detail),
                checked = ui?.shared ?: true,
                enabled = ui?.busy != true,
                isTablet = false,
                onCheckedChange = { controller.setShared(it) },
            )
        }

        val notice = ui?.notice
        if (!notice.isNullOrBlank()) {
            Text(
                text = notice,
                style = MaterialTheme.typography.bodyMedium,
                color = if (ui?.noticeIsError == true) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }

        Text(
            text = stringResource(Res.string.vpn_profiles_encrypted),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun VpnModeRow(
    label: String,
    detail: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    SettingsNavigationRow(
        title = label,
        description = detail,
        isTablet = false,
        enabled = enabled,
        trailingContent = {
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = stringResource(Res.string.vpn_selected))
            }
        },
        onClick = onClick,
    )
}

/** Used by the Settings page: the VPN page only exists when the Android module is installed. */
internal fun isVpnSettingsAvailable(): Boolean = VpnHost.available
