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
                title = "VPN status",
                description = connection?.detail ?: "Not connected",
                icon = Icons.Default.Security,
                isTablet = false,
                onClick = { connection = controller.connection() },
            )
            SettingsGroupDivider(isTablet = false)
            SettingsNavigationRow(
                title = connection?.title ?: "VPN off",
                description = connection?.traffic,
                isTablet = false,
                onClick = { controller.turnOff() },
            )
        }

        SettingsGroup(isTablet = false) {
            SettingsNavigationRow(
                title = "WireGuard profile",
                description = if (ui?.profiles?.isEmpty() != false) {
                    "Import a .conf file to get started"
                } else {
                    "Choose the profile used for playback"
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
                            Icon(Icons.Default.Check, contentDescription = "Selected")
                        }
                        IconButton(onClick = { controller.remove(profile.id) }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove")
                        }
                    },
                    onClick = { controller.selectProfile(profile.id) },
                )
            }
        }

        SettingsGroup(isTablet = false) {
            val currentMode = ui?.mode ?: VpnSettingsController.MODE_OFF
            VpnModeRow(
                label = "Off",
                detail = "Keep VPN protection off",
                selected = currentMode == VpnSettingsController.MODE_OFF,
                enabled = ui?.busy != true,
            ) { controller.setMode(VpnSettingsController.MODE_OFF) }
            SettingsGroupDivider(isTablet = false)
            VpnModeRow(
                label = "HTTP streams only",
                detail = "Protect HTTP playback; leave HTTPS unchanged",
                selected = currentMode == VpnSettingsController.MODE_HTTP,
                enabled = ui?.busy != true,
            ) { controller.setMode(VpnSettingsController.MODE_HTTP) }
            SettingsGroupDivider(isTablet = false)
            VpnModeRow(
                label = "HTTP + HTTPS streams",
                detail = "Protect HTTP and HTTPS playback, including preloads",
                selected = currentMode == VpnSettingsController.MODE_HTTP_HTTPS,
                enabled = ui?.busy != true,
            ) { controller.setMode(VpnSettingsController.MODE_HTTP_HTTPS) }
            SettingsGroupDivider(isTablet = false)
            VpnModeRow(
                label = "Full app",
                detail = "All traffic from Nuvio; other apps are unaffected",
                selected = currentMode == VpnSettingsController.MODE_APP,
                enabled = ui?.busy != true,
            ) { controller.setMode(VpnSettingsController.MODE_APP) }
        }

        SettingsGroup(isTablet = false) {
            SettingsSwitchRow(
                title = "Use for all profiles",
                description = "Share this VPN setup with every Nuvio profile",
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
            text = "Profiles are encrypted on this device. Android system backup stays excluded; " +
                "Setup Sync carries them to your other Nuvio devices.",
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
                Icon(Icons.Default.Check, contentDescription = "Selected")
            }
        },
        onClick = onClick,
    )
}

/** Used by the Settings page: the VPN page only exists when the Android module is installed. */
internal fun isVpnSettingsAvailable(): Boolean = VpnHost.available
