package com.nuvio.app.livetv.record

import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import kotlin.concurrent.thread

/**
 * Hands recordings to Nuvio's player as ordinary web links, so they play with its full controls.
 * Files still on this device are read locally; media already uploaded to SMB is read through
 * [SmbClient]. Only this device can reach the server.
 */
object LiveTvRecordingServer {
    private const val TAG = "LiveTvRecordingServer"

    private data class Source(
        val dir: File,
        val smb: SmbConfig? = null,
        val remoteDir: String? = null,
    )

    @Volatile private var server: ServerSocket? = null
    private val sources = java.util.concurrent.ConcurrentHashMap<String, Source>()

    /** The link for a recording's playlist (starts the server if needed). */
    fun urlFor(
        id: String,
        dir: File,
        smb: SmbConfig? = null,
        remoteDir: String? = null,
    ): String {
        sources[id] = Source(dir, smb, remoteDir)
        val port = ensure()
        return "http://127.0.0.1:$port/$id/index.m3u8"
    }

    @Synchronized
    private fun ensure(): Int {
        server?.takeIf { !it.isClosed }?.let { return it.localPort }
        val s = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        server = s
        thread(name = "rec-server", isDaemon = true) {
            while (!s.isClosed) {
                val client = runCatching { s.accept() }.getOrNull() ?: break
                thread(name = "rec-client", isDaemon = true) { runCatching { serve(client) }.onFailure { Log.w(TAG, "serve: ${it.message}") } }
            }
        }
        return s.localPort
    }

    private fun serve(client: Socket) = client.use { sock ->
        sock.soTimeout = 30_000
        val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
        val requestLine = reader.readLine() ?: return@use
        var rangeStart = -1L
        var rangeEnd = -1L
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            if (line.startsWith("Range:", ignoreCase = true)) {
                Regex("""bytes=(\d*)-(\d*)""").find(line)?.let { m ->
                    rangeStart = m.groupValues[1].toLongOrNull() ?: -1L
                    rangeEnd = m.groupValues[2].toLongOrNull() ?: -1L
                }
            }
        }
        val parts = requestLine.split(' ')
        val method = parts.getOrNull(0) ?: "GET"
        val path = URLDecoder.decode(parts.getOrNull(1)?.substringBefore('?') ?: "/", "UTF-8").trimStart('/')
        val source = sources[path.substringBefore('/')] ?: return@use
        val relative = path.substringAfter('/', "")
        if (relative.contains("..")) {
            notFound(sock); return@use
        }
        val local = File(source.dir, relative)
        val remoteRelative = source.remoteDir?.let { it + relative }
        val remote = source.smb != null && remoteRelative != null
        val out = sock.getOutputStream()

        // A recording that has only just started: its playlist appears with the first piece.
        if (local.extension.equals("m3u8", true) && !local.isFile && !remote) {
            var waited = 0
            while (!local.isFile && waited < 15_000) { Thread.sleep(250); waited += 250 }
        }

        val type = when (local.extension.lowercase()) {
            "m3u8" -> "application/vnd.apple.mpegurl"
            "ts" -> "video/mp2t"
            "m4s", "mp4" -> "video/mp4"
            else -> "application/octet-stream"
        }

        // Playlists: keep the local copy (metadata stays local), fall back to SMB after a restart.
        if (type.contains("mpegurl")) {
            val text = if (local.isFile) {
                runCatching { local.readText() }.getOrDefault("")
            } else if (remote) {
                SmbClient.readText(source.smb!!, remoteRelative!!).orEmpty()
            } else {
                ""
            }
            if (text.isNotEmpty()) {
                val body = if (!text.contains("#EXT-X-ENDLIST") && !text.contains("#EXT-X-START")) {
                    text.replaceFirst("#EXTM3U", "#EXTM3U\n#EXT-X-START:TIME-OFFSET=0,PRECISE=YES")
                } else {
                    text
                }.toByteArray()
                out.write(("HTTP/1.1 200 OK\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n" +
                    "Cache-Control: no-cache\r\nConnection: close\r\n\r\n").toByteArray())
                if (!method.equals("HEAD", ignoreCase = true)) out.write(body)
                out.flush()
                return@use
            }
        }

        val length = when {
            local.isFile -> local.length()
            remote -> SmbClient.length(source.smb!!, remoteRelative!!)
            else -> -1L
        }
        if (length < 0) { notFound(sock); return@use }

        val start = if (rangeStart >= 0) rangeStart.coerceAtMost(length) else 0L
        val end = if (rangeEnd >= 0) rangeEnd.coerceAtMost(length - 1) else length - 1
        val count = (end - start + 1).coerceAtLeast(0)
        val head = StringBuilder()
        head.append(if (rangeStart >= 0) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
        head.append("Content-Type: ").append(type).append("\r\n")
        head.append("Content-Length: ").append(count).append("\r\n")
        head.append("Accept-Ranges: bytes\r\n")
        if (rangeStart >= 0) head.append("Content-Range: bytes ").append(start).append('-').append(end).append('/').append(length).append("\r\n")
        head.append("Connection: close\r\n\r\n")
        out.write(head.toString().toByteArray())
        if (method.equals("HEAD", ignoreCase = true)) return@use

        if (local.isFile) {
            RandomAccessFile(local, "r").use { raf ->
                raf.seek(start)
                copyTo({ buffer, offset, length -> raf.read(buffer, offset, length) }, out, count)
            }
        } else if (remote) {
            SmbClient.randomAccess(source.smb!!, remoteRelative!!)?.use { raf ->
                raf.seek(start)
                copyTo({ buffer, offset, length -> raf.read(buffer, offset, length) }, out, count)
            } ?: notFound(sock)
        }
        out.flush()
    }

    private fun copyTo(read: (ByteArray, Int, Int) -> Int, out: java.io.OutputStream, count: Long) {
        val buf = ByteArray(64 * 1024)
        var left = count
        while (left > 0) {
            val n = read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n < 0) break
            out.write(buf, 0, n)
            left -= n
        }
    }

    private fun notFound(sock: Socket) {
        runCatching {
            sock.getOutputStream().write(
                "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(),
            )
        }
    }
}
