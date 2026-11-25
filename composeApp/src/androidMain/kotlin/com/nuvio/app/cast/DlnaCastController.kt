package com.nuvio.app.cast

import android.app.Application
import android.net.wifi.WifiManager
import android.util.Log
import com.nuvio.app.features.cast.CastHost
import com.nuvio.app.features.cast.CastLoad
import com.nuvio.app.features.cast.CastUi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

/** Basic UPnP AVTransport controller for smart TVs that expose a DLNA MediaRenderer. */
internal object DlnaCastController {
    private const val TAG = "NuvioDLNA"
    private const val SSDP_ADDRESS = "239.255.255.250"
    private const val SSDP_PORT = 1900
    private const val AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"
    private const val RENDERING_CONTROL = "urn:schemas-upnp-org:service:RenderingControl:1"
    private val io = Executors.newCachedThreadPool()
    private val http = OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).build()
    private var application: Application? = null
    @Volatile private var active: Renderer? = null
    @Volatile private var lastLive = false
    @Volatile private var pollingGeneration = 0L

    internal data class Renderer(
        val name: String,
        val location: String,
        val avTransportUrl: String,
        val renderingControlUrl: String?,
        val avTransportType: String,
    )

    fun install(app: Application) {
        application = app
    }

    fun isActive(): Boolean = active != null

    fun discover(onComplete: (List<Renderer>) -> Unit) {
        io.execute {
            val found = discoverBlocking()
            onComplete(found)
        }
    }

    private fun discoverBlocking(): List<Renderer> {
        val app = application ?: return emptyList()
        val wifi = app.applicationContext.getSystemService(WifiManager::class.java)
        val lock = runCatching { wifi?.createMulticastLock("nuvio-dlna").also { it?.setReferenceCounted(false); it?.acquire() } }.getOrNull()
        val found = LinkedHashMap<String, Renderer>()
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 250
                val targets = listOf(
                    "urn:schemas-upnp-org:device:MediaRenderer:1",
                    "urn:schemas-upnp-org:device:MediaRenderer:2",
                    "urn:schemas-upnp-org:service:AVTransport:1",
                    "upnp:rootdevice",
                )
                for (target in targets) {
                    val message = listOf(
                        "M-SEARCH * HTTP/1.1",
                        "HOST: $SSDP_ADDRESS:$SSDP_PORT",
                        "MAN: \"ssdp:discover\"",
                        "MX: 2",
                        "ST: $target",
                        "", "",
                    ).joinToString("\r\n").toByteArray(Charsets.US_ASCII)
                    runCatching {
                        socket.send(DatagramPacket(message, message.size, InetAddress.getByName(SSDP_ADDRESS), SSDP_PORT))
                    }
                }
                val until = System.currentTimeMillis() + 3_200L
                val buffer = ByteArray(8 * 1024)
                while (System.currentTimeMillis() < until) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: java.net.SocketTimeoutException) {
                        continue
                    }
                    val response = String(packet.data, packet.offset, packet.length, Charsets.US_ASCII)
                    val location = Regex("(?im)^LOCATION\\s*:\\s*(\\S+)").find(response)?.groupValues?.get(1) ?: continue
                    if (location !in found) {
                        loadRenderer(location)?.let { renderer -> found[location] = renderer }
                    }
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "SSDP discovery failed: ${error.message}")
        } finally {
            runCatching { if (lock?.isHeld == true) lock.release() }
        }
        Log.i(TAG, "Discovered ${found.size} DLNA renderer(s)")
        return found.values.toList()
    }

    private fun loadRenderer(location: String): Renderer? = runCatching {
        val request = Request.Builder().url(location).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val xml = response.body?.string() ?: return null
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                isXIncludeAware = false
                isExpandEntityReferences = false
            }
            val document = factory.newDocumentBuilder().parse(xml.byteInputStream())
            fun Element.children(tag: String): List<Element> {
                val list = getElementsByTagNameNS("*", tag)
                return (0 until list.length).mapNotNull { list.item(it) as? Element }
            }
            val root = document.documentElement
            val urlBase = root.children("URLBase").firstOrNull()?.textContent?.trim()?.takeIf { it.isNotEmpty() } ?: location
            val device = root.children("device").firstOrNull() ?: return null
            val name = device.children("friendlyName").firstOrNull()?.textContent?.trim()?.takeIf { it.isNotEmpty() } ?: "DLNA TV"
            val services = device.children("service")
            val av = services.firstOrNull { it.children("serviceType").firstOrNull()?.textContent?.contains("AVTransport") == true } ?: return null
            val avType = av.children("serviceType").first().textContent.trim()
            val avControl = av.children("controlURL").firstOrNull()?.textContent?.trim() ?: return null
            val rc = services.firstOrNull { it.children("serviceType").firstOrNull()?.textContent?.contains("RenderingControl") == true }
            val rcType = rc?.children("serviceType")?.firstOrNull()?.textContent?.trim() ?: RENDERING_CONTROL
            val rcControl = rc?.children("controlURL")?.firstOrNull()?.textContent?.trim()?.let { resolveUrl(urlBase, it) }
            Renderer(name, location, resolveUrl(urlBase, avControl), rcControl, avType.ifBlank { AV_TRANSPORT })
        }
    }.onFailure { Log.w(TAG, "Couldn't read DLNA device description: ${it.message}") }.getOrNull()

    private fun resolveUrl(base: String, path: String): String = runCatching { URI(base).resolve(path).toString() }.getOrDefault(path)

    fun select(renderer: Renderer) {
        val previous = active
        if (previous != null && previous !== renderer) io.execute { action(previous, "Stop", mapOf("Speed" to "1")) }
        active = renderer
        lastLive = false
        CastHost.lastPositionMs = 0L
        CastHost.ui.value = CastUi(deviceName = renderer.name, message = "Connecting…")
        bindControls()
        startPolling(renderer)
    }

    fun load(request: CastLoad, mediaUrl: String, contentType: String): Boolean {
        val renderer = active ?: return false
        lastLive = request.live
        io.execute {
            val metadata = didlMetadata(request, mediaUrl, contentType)
            val setResult = action(renderer, "SetAVTransportURI", mapOf(
                "CurrentURI" to mediaUrl,
                "CurrentURIMetaData" to metadata,
            ))
            if (setResult.isFailure) {
                publishMessage("The TV refused this stream (${setResult.exceptionOrNull()?.message ?: "UPnP error"}).")
                return@execute
            }
            val play = action(renderer, "Play", mapOf("Speed" to "1"))
            publishMessage(if (play.isFailure) "The TV loaded the stream but couldn't start playback." else null)
            if (play.isSuccess) CastHost.ui.value = CastHost.ui.value?.copy(playing = true, message = null)
        }
        return true
    }

    fun togglePlayback() {
        val renderer = active ?: return
        io.execute {
            val playing = CastHost.ui.value?.playing == true
            val result = action(renderer, if (playing) "Pause" else "Play", if (playing) emptyMap() else mapOf("Speed" to "1"))
            if (result.isSuccess) CastHost.ui.value = CastHost.ui.value?.copy(playing = !playing)
        }
    }

    fun seekTo(positionMs: Long) {
        val renderer = active ?: return
        if (lastLive) return
        io.execute {
            action(renderer, "Seek", mapOf("Unit" to "REL_TIME", "Target" to formatTime(positionMs.coerceAtLeast(0L))))
                .onFailure { Log.w(TAG, "DLNA seek failed: ${it.message}") }
        }
    }

    fun stop() {
        val renderer = active ?: return
        val current = CastHost.ui.value
        if (current != null) CastHost.lastPositionMs = current.positionMs
        active = null
        io.execute { action(renderer, "Stop", mapOf("Speed" to "1")) }
        CastHost.ui.value = null
        CastRelay.stop()
    }

    fun setVolume(value: Float) {
        val renderer = active ?: return
        val url = renderer.renderingControlUrl ?: return
        io.execute {
            action(renderer, "SetVolume", mapOf("InstanceID" to "0", "Channel" to "Master", "DesiredVolume" to (value.coerceIn(0f, 1f) * 100).toInt().toString()), url, RENDERING_CONTROL)
                .onSuccess { CastHost.ui.value = CastHost.ui.value?.copy(volume = value.coerceIn(0f, 1f)) }
        }
    }

    fun setMuted(muted: Boolean) {
        val renderer = active ?: return
        val url = renderer.renderingControlUrl ?: return
        io.execute {
            action(renderer, "SetMute", mapOf("InstanceID" to "0", "Channel" to "Master", "DesiredMute" to if (muted) "1" else "0"), url, RENDERING_CONTROL)
                .onSuccess { CastHost.ui.value = CastHost.ui.value?.copy(muted = muted) }
        }
    }

    private fun bindControls() {
        CastHost.togglePlayback = ::togglePlayback
        CastHost.seekTo = ::seekTo
        CastHost.stop = ::stop
        CastHost.setVolume = ::setVolume
        CastHost.setMuted = ::setMuted
        CastHost.selectAudio = {}
    }

    private fun startPolling(renderer: Renderer) {
        val generation = ++pollingGeneration
        io.execute {
            while (active === renderer && pollingGeneration == generation) {
                    val info = action(renderer, "GetPositionInfo", mapOf("InstanceID" to "0"))
                    if (info.isSuccess) {
                        val xml = info.getOrThrow()
                        val position = xmlValue(xml, "RelTime")?.let(::parseTime) ?: 0L
                        val duration = xmlValue(xml, "TrackDuration")?.let(::parseTime) ?: 0L
                        val transport = action(renderer, "GetTransportInfo", mapOf("InstanceID" to "0"))
                            .getOrNull()?.let { xmlValue(it, "CurrentTransportState") }
                        val volume = renderer.renderingControlUrl?.let {
                            action(renderer, "GetVolume", mapOf("InstanceID" to "0", "Channel" to "Master"), it, RENDERING_CONTROL)
                                .getOrNull()?.let { body -> xmlValue(body, "CurrentVolume")?.toFloatOrNull()?.div(100f) }
                        }
                        val muted = renderer.renderingControlUrl?.let {
                            action(renderer, "GetMute", mapOf("InstanceID" to "0", "Channel" to "Master"), it, RENDERING_CONTROL)
                                .getOrNull()?.let { body -> xmlValue(body, "CurrentMute") in setOf("1", "true") }
                        }
                        CastHost.ui.value = CastHost.ui.value?.copy(
                            positionMs = position,
                            durationMs = duration,
                            playing = transport.equals("PLAYING", ignoreCase = true),
                            volume = volume ?: CastHost.ui.value?.volume ?: 1f,
                            muted = muted ?: CastHost.ui.value?.muted ?: false,
                        )
                        CastHost.lastPositionMs = position
                    } else if (CastHost.ui.value?.message == "Connecting…") {
                        publishMessage("Connected, waiting for the TV's playback status…")
                    }
                    Thread.sleep(1_500)
            }
        }
    }

    private fun action(
        renderer: Renderer,
        name: String,
        arguments: Map<String, String>,
        controlUrl: String = renderer.avTransportUrl,
        serviceType: String = renderer.avTransportType,
    ): Result<String> = runCatching {
        val inner = arguments.entries.joinToString("") { (key, value) -> "<$key>${xmlEscape(value)}</$key>" }
        val envelope = """<?xml version="1.0" encoding="utf-8"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:$name xmlns:u="$serviceType">$inner</u:$name></s:Body></s:Envelope>"""
        val request = Request.Builder()
            .url(controlUrl)
            .header("SOAPACTION", "\"$serviceType#$name\"")
            .post(envelope.toRequestBody("text/xml; charset=utf-8".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            check(response.isSuccessful) { "HTTP ${response.code}: ${xmlValue(body, "errorDescription") ?: body.take(160)}" }
            body
        }
    }

    private fun didlMetadata(request: CastLoad, mediaUrl: String, mime: String): String {
        val title = xmlEscape(request.title)
        val url = xmlEscape(mediaUrl)
        val protocolInfo = "http-get:*:$mime:*"
        return """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"><item id="0" parentID="0" restricted="1"><dc:title>$title</dc:title><upnp:class>object.item.videoItem</upnp:class><res protocolInfo="$protocolInfo">$url</res></item></DIDL-Lite>"""
    }

    private fun xmlValue(xml: String, tag: String): String? = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val nodes = factory.newDocumentBuilder().parse(xml.byteInputStream()).getElementsByTagNameNS("*", tag)
        nodes.item(0)?.textContent?.trim()
    }.getOrNull()

    private fun publishMessage(message: String?) {
        CastHost.ui.value = CastHost.ui.value?.copy(message = message)
    }

    private fun xmlEscape(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

    private fun formatTime(ms: Long): String {
        val seconds = ms / 1000
        return "%02d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    }

    private fun parseTime(value: String): Long = runCatching {
        val parts = value.split(':').map(String::toLong)
        ((parts.getOrElse(0) { 0 } * 3600 + parts.getOrElse(1) { 0 } * 60 + parts.getOrElse(2) { 0 }) * 1000)
    }.getOrDefault(0L)
}
