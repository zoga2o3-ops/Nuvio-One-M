package com.nuvio.app.features.player.vpn

import android.content.Context
import com.nuvio.vpn.android.VpnPlaybackProtection
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Waits until the VPN route for [source] is ready (or confirms that no tunnel is needed).
 *
 * The player must not open a remote source before this returns: HTTP_ONLY/HTTP_HTTPS would
 * otherwise fall back to a direct connection, and APP_WIDE needs the consent/tunnel first.
 */
internal suspend fun awaitVpnForPlayback(context: Context, source: String) {
    if (!source.startsWith("http://", true) && !source.startsWith("https://", true)) return
    suspendCancellableCoroutine { continuation ->
        val request = VpnPlaybackProtection.prepare(context, source)
        continuation.invokeOnCancellation { request.cancel(false) }
        request.whenComplete { _, failure ->
            if (!continuation.isActive) return@whenComplete
            continuation.resumeWith(
                if (failure == null) Result.success(Unit)
                else Result.failure(failure.cause ?: failure),
            )
        }
    }
}

/** Local proxy for MPV/VLC-style players; close it when the source is replaced or ends. */
internal suspend fun awaitNativeVpnProxy(context: Context): VpnPlaybackProtection.NativeProxy? =
    suspendCancellableCoroutine { continuation ->
        val request = VpnPlaybackProtection.proxyForNativePlayer(context)
        continuation.invokeOnCancellation {
            if (!request.cancel(false)) request.thenAccept { it?.close() }
        }
        request.whenComplete { value, failure ->
            if (!continuation.isActive) {
                value?.close()
            } else {
                continuation.resumeWith(
                    if (failure == null) Result.success(value)
                    else Result.failure(failure.cause ?: failure),
                )
            }
        }
    }
