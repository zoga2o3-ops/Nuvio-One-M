package com.nuvio.app.features.vpn

import androidx.compose.runtime.Composable

/**
 * Android plugs the WireGuard settings UI in here. iOS and builds without the VPN
 * modules leave it unavailable, so the Settings row does not appear.
 */
object VpnHost {
    var available: Boolean = false
    var settingsContent: (@Composable () -> Unit)? = null
}
