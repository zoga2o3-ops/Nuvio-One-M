package com.nuvio.app.core.vpn

actual object VpnIntegration {
    actual fun setActiveProfile(profileId: Int) = Unit

    actual fun removeProfile(profileId: Int) = Unit
}
