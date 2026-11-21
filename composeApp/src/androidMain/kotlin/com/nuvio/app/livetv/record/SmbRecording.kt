package com.nuvio.app.livetv.record

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import java.io.File
import java.net.URLEncoder
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

/** A network recording destination. Credentials never leave the encrypted preferences. */
data class SmbConfig(
    val host: String,
    val port: Int = 445,
    val share: String,
    val folder: String = "",
    val domain: String = "",
    val username: String = "",
    val password: String = "",
) {
    val label: String
        get() = buildString {
            append(host)
            if (port != 445) append(':').append(port)
            append('/').append(share)
            folder.trim('/').takeIf { it.isNotEmpty() }?.let { append('/').append(it) }
        }

    val baseUrl: String
        get() {
            val cleanHost = host.trim().removePrefix("smb://").trimEnd('/')
            val hostPart = if (port in 1..65535 && port != 445) "$cleanHost:$port" else cleanHost
            return buildString {
                append("smb://").append(hostPart).append('/').append(encode(share.trim('/'))).append('/')
                val parts = folder.trim('/').split('/').filter { it.isNotBlank() }
                if (parts.isNotEmpty()) append(parts.joinToString("/") { encode(it) }).append('/')
            }
        }

    internal fun key(): String =
        "$host|$port|$share|$folder|$domain|$username|${password.hashCode()}"

    companion object {
        fun encode(value: String): String =
            URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }
}

/** Small encrypted store for the one SMB recording destination. */
internal object SmbSettings {
    private const val TAG = "SmbSettings"
    private const val ENCRYPTED = "livetv_smb_recording_secure"
    private const val PLAIN = "livetv_smb_recording"
    private const val HOST = "host"
    private const val PORT = "port"
    private const val SHARE = "share"
    private const val FOLDER = "folder"
    private const val DOMAIN = "domain"
    private const val USER = "user"
    private const val PASSWORD = "password"

    @Volatile private var opened: SharedPreferences? = null

    @Synchronized
    private fun prefs(context: Context): SharedPreferences {
        opened?.let { return it }
        val app = context.applicationContext
        val store = runCatching {
            val key = MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(
                app,
                ENCRYPTED,
                key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrElse { failure ->
            Log.w(TAG, "Keystore-backed SMB preferences unavailable; using a plain file", failure)
            app.getSharedPreferences(PLAIN, Context.MODE_PRIVATE)
        }
        opened = store
        return store
    }

    fun load(context: Context): SmbConfig? = runCatching {
        val p = prefs(context)
        val host = p.getString(HOST, "").orEmpty().trim()
        val share = p.getString(SHARE, "").orEmpty().trim()
        if (host.isBlank() || share.isBlank()) return null
        SmbConfig(
            host = host,
            port = p.getInt(PORT, 445).coerceIn(1, 65535),
            share = share,
            folder = p.getString(FOLDER, "").orEmpty(),
            domain = p.getString(DOMAIN, "").orEmpty(),
            username = p.getString(USER, "").orEmpty(),
            password = p.getString(PASSWORD, "").orEmpty(),
        )
    }.getOrNull()

    fun save(context: Context, config: SmbConfig) {
        prefs(context).edit()
            .putString(HOST, config.host.trim())
            .putInt(PORT, config.port.coerceIn(1, 65535))
            .putString(SHARE, config.share.trim())
            .putString(FOLDER, config.folder.trim('/'))
            .putString(DOMAIN, config.domain.trim())
            .putString(USER, config.username)
            .putString(PASSWORD, config.password)
            .apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}

/** One cached SMB context per destination, so a recording doesn't reconnect for every file. */
internal object SmbClient {
    private const val TAG = "SmbClient"
    private val contexts = ConcurrentHashMap<String, CIFSContext>()

    private fun context(config: SmbConfig): CIFSContext =
        contexts.getOrPut(config.key()) {
            val props = Properties().apply {
                setProperty("jcifs.smb.client.enableSMB2", "true")
                setProperty("jcifs.smb.client.disableSMB1", "true")
                // Most NAS shares leave signing optional; servers that require it still get it.
                // Raw NTLM is what these servers expect (SPNEGO signing can fail on some of them).
                setProperty("jcifs.smb.client.signingPreferred", "false")
                setProperty("jcifs.smb.client.signingEnforced", "false")
                setProperty("jcifs.smb.client.ipcSigningEnforced", "false")
                setProperty("jcifs.smb.useRawNTLM", "true")
                setProperty("jcifs.smb.client.responseTimeout", "30000")
                setProperty("jcifs.smb.client.soTimeout", "35000")
            }
            val base = BaseContext(PropertyConfiguration(props))
            base.withCredentials(
                NtlmPasswordAuthenticator(
                    config.domain.ifBlank { null },
                    config.username.ifBlank { null },
                    config.password,
                )
            )
        }

    fun file(config: SmbConfig, relative: String): SmbFile {
        val clean = relative.trim('/')
        val base = SmbFile(config.baseUrl, context(config))
        return if (clean.isEmpty()) base else SmbFile(base, clean)
    }

    fun recordingDir(config: SmbConfig, id: String): SmbFile =
        SmbFile(config.baseUrl + SmbConfig.encode(id) + "/", context(config))

    fun test(config: SmbConfig): Result<Long> = runCatching {
        val dir = file(config, "")
        dir.mkdirs()
        val probe = file(config, ".nuvio-write-test-${System.nanoTime()}.tmp")
        try {
            probe.openOutputStream().use { it.write("Nuvio SMB test".toByteArray()) }
        } finally {
            runCatching { probe.delete() }
        }
        runCatching { dir.getDiskFreeSpace() }.getOrDefault(0L)
    }

    fun upload(config: SmbConfig, local: File, relative: String) {
        val target = file(config, relative)
        target.parent?.takeIf { it.isNotBlank() }?.let { parent ->
            runCatching { SmbFile(parent, context(config)).mkdirs() }
        }
        local.inputStream().use { input ->
            target.openOutputStream().use { output ->
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                }
            }
        }
    }

    fun deleteRecursively(config: SmbConfig, relative: String): Boolean {
        val clean = relative.trim('/')
        if (clean.isEmpty()) return false
        repeat(2) { attempt ->
            val ok = runCatching {
                val root = SmbFile(config.baseUrl + clean.split('/').joinToString("/") { SmbConfig.encode(it) } + "/", context(config))
                if (!root.exists()) {
                    if (attempt == 0) error("SMB path not visible yet")
                    return true
                }
                fun delete(resource: SmbFile): Boolean {
                    var success = true
                    if (runCatching { resource.isDirectory }.getOrDefault(false)) {
                        runCatching { resource.listFiles() }.getOrDefault(emptyArray())
                            .forEach { child -> if (!delete(child)) success = false }
                    }
                    if (!runCatching { resource.delete(); true }.getOrDefault(false)) {
                        Log.w(TAG, "Could not delete SMB file $resource")
                        success = false
                    }
                    return success
                }
                delete(root)
            }.getOrElse { failure ->
                Log.w(TAG, "SMB delete of $relative failed: ${failure.message}", failure)
                false
            }
            if (ok) return true
            if (attempt == 0) runCatching { Thread.sleep(500L) }
        }
        return false
    }

    fun freeBytes(config: SmbConfig): Long =
        runCatching { file(config, "").getDiskFreeSpace() }.getOrDefault(0L)

    fun length(config: SmbConfig, relative: String): Long =
        runCatching { file(config, relative).length() }.getOrDefault(-1L)

    fun readText(config: SmbConfig, relative: String): String? = runCatching {
        // Random access reads work on servers whose streamed reads are unreliable.
        val target = file(config, relative)
        val length = target.length()
        if (length > 8L * 1024 * 1024) return@runCatching null
        target.openRandomAccess("r").use { raf ->
            val bytes = ByteArray(length.toInt())
            raf.readFully(bytes)
            // Some editors write a BOM; it would stop the JSON/playlist parser at the first byte.
            String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
        }
    }.getOrNull()

    /**
     * Copies a file from the share to [output], reporting progress. [cancelled] is checked
     * between chunks so a long combine can be stopped.
     */
    fun copyTo(
        config: SmbConfig,
        relative: String,
        output: java.io.OutputStream,
        cancelled: () -> Boolean = { false },
        onBytes: (Long) -> Unit = {},
    ): Long {
        val target = file(config, relative)
        val length = target.length()
        val raf = target.openRandomAccess("r") ?: error("Couldn't open $relative on the share")
        raf.use {
            val buffer = ByteArray(256 * 1024)
            var done = 0L
            while (done < length) {
                if (cancelled()) throw kotlinx.coroutines.CancellationException()
                val read = it.read(buffer, 0, minOf(buffer.size.toLong(), length - done).toInt())
                if (read < 0) break
                output.write(buffer, 0, read)
                done += read
                onBytes(done)
            }
            return done
        }
    }

    fun randomAccess(config: SmbConfig, relative: String): SmbRandomAccessFile? =
        runCatching { file(config, relative).openRandomAccess("r") }.getOrNull()

    fun exists(config: SmbConfig, relative: String): Boolean =
        runCatching { file(config, relative).exists() }.getOrDefault(false)

    /** Directories directly under [relative] (the recording folders on the share). */
    fun listDirs(config: SmbConfig, relative: String): List<SmbFile> =
        runCatching {
            file(config, relative).listFiles()?.filter { it.isDirectory }.orEmpty()
        }.getOrDefault(emptyList())

    fun openInputStream(config: SmbConfig, relative: String): java.io.InputStream? =
        runCatching { file(config, relative).openInputStream() }.getOrNull()

    fun openOutputStream(config: SmbConfig, relative: String): java.io.OutputStream? =
        runCatching {
            val target = file(config, relative)
            target.parent?.takeIf { it.isNotBlank() }?.let { parent ->
                SmbFile(parent, context(config)).mkdirs()
            }
            target.openOutputStream()
        }.getOrNull()
}

/**
 * Mirrors a recording folder to SMB while it is being made. The local folder remains the
 * authoritative copy until [finish] uploads everything; then media pieces are removed locally
 * and served from the share by [LiveTvRecordingServer].
 */
internal class SmbMirror(
    private val config: SmbConfig,
    private val localDir: File,
    private val remoteDir: String,
    private val onUploaded: (totalBytes: Long) -> Unit = {},
    private val onProblem: (String) -> Unit = {},
) {
    @Volatile private var running = false
    @Volatile private var thread: Thread? = null
    private val uploaded = HashMap<String, Pair<Long, Long>>()
    @Volatile var uploadedBytes: Long = 0L
        private set
    @Volatile var lastProblem: String? = null
        private set

    fun start() {
        if (running) return
        running = true
        thread = kotlin.concurrent.thread(name = "smb-mirror", isDaemon = true) {
            while (running) {
                sync(final = false)
                try { Thread.sleep(2_000L) } catch (_: InterruptedException) { break }
            }
        }
    }

    fun stop() {
        running = false
        runCatching { thread?.join(5_000L) }
        thread = null
    }

    /** Uploads the last files, then removes media from the local staging folder. */
    fun finish(): Boolean {
        stop()
        return sync(final = true)
    }

    private fun sync(final: Boolean): Boolean {
        if (!localDir.isDirectory) return false
        val files = localDir.walkTopDown().filter { it.isFile }
            .filter { !it.name.endsWith(".tmp", true) && !it.name.endsWith(".part", true) }
            .sortedBy { if (it.name == "info.json") 1 else 0 }
            .toList()
        if (files.isEmpty()) return true
        var ok = true
        for (file in files) {
            val relative = file.relativeTo(localDir).invariantSeparatorsPath
            if (relative.isBlank() || relative.contains("..")) continue
            val stamp = file.length() to file.lastModified()
            val same = uploaded[relative] == stamp
            if (!same || final) {
                try {
                    SmbClient.upload(config, file, remoteDir + relative)
                    uploaded[relative] = stamp
                    uploadedBytes = uploaded.values.sumOf { it.first }
                    lastProblem = null
                    onUploaded(uploadedBytes)
                } catch (failure: Exception) {
                    ok = false
                    lastProblem = failure.message ?: failure.javaClass.simpleName
                    onProblem(lastProblem.orEmpty())
                    break
                }
            }
        }
        if (ok && final) {
            // Metadata and playlists stay local: the server needs them and they are tiny.
            files.filter { !it.name.equals("info.json", true) && !it.name.endsWith(".m3u8", true) }
                .forEach { runCatching { it.delete() } }
        }
        return ok
    }
}

