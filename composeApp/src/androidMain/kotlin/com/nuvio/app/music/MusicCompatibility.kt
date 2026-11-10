package com.nuvio.app.music

import android.content.Context
import android.net.wifi.WifiManager
import com.liskovsoft.mediaserviceinterfaces.ServiceManager
import com.liskovsoft.sharedutils.prefs.GlobalPreferences
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager
import androidx.compose.runtime.staticCompositionLocalOf
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Music and the YouTube tab share the phone's one YouTube sign-in. The TV app keeps separate
 * account sets; the mobile app does not, so this is a thin adapter over the same engine.
 */
enum class LegacyAccountScope { MUSIC }

object LegacyAccountScopes {
    fun engine(context: Context, scope: LegacyAccountScope): ServiceManager {
        GlobalPreferences.instance(context.applicationContext)
        return YouTubeServiceManager.instance()
    }
}

object DeviceIpAddress {
    fun get(context: Context): String? {
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val wifiIp = wifiManager?.connectionInfo?.ipAddress ?: 0
        if (wifiIp != 0) {
            return "${wifiIp and 0xFF}.${wifiIp shr 8 and 0xFF}.${wifiIp shr 16 and 0xFF}.${wifiIp shr 24 and 0xFF}"
        }
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()
                ?.flatMap { it.inetAddresses.toList() }
                ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                ?.hostAddress
        } catch (_: Exception) {
            null
        }
    }
}

/** Nuvio's side menu does not exist on the phone shell; the Music menu action is a no-op. */
val LocalOpenSidebar = staticCompositionLocalOf<() -> Unit> { {} }

/** The phone shell always has its own navigation visible, so this stays false. */
val LocalSidebarExpanded = staticCompositionLocalOf { false }
