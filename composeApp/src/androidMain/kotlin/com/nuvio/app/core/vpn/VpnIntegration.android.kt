package com.nuvio.app.core.vpn

import com.nuvio.vpn.android.VpnPlaybackProtection

actual object VpnIntegration {
    actual fun setActiveProfile(profileId: Int) {
        runCatching { VpnPlaybackProtection.setActiveUserProfile(profileId) }
    }

    actual fun removeProfile(profileId: Int) {
        runCatching { VpnPlaybackProtection.removeUserProfile(profileId) }
    }
}
