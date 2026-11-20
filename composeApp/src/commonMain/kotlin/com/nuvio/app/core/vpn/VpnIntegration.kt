package com.nuvio.app.core.vpn

/**
 * Bridge between the shared profile/playback code and the Android-only VPN module.
 *
 * iOS and any build without the VPN modules use the no-op actual.
 */
expect object VpnIntegration {
    /** Tells the VPN module which Nuvio profile owns the current VPN choice. */
    fun setActiveProfile(profileId: Int)

    /** Removes a deleted Nuvio profile's VPN override. */
    fun removeProfile(profileId: Int)
}
