package com.nuvio.app.core.diagnostics

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.os.Process
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.core.build.AppVersionConfig
import com.nuvio.app.core.logging.InAppLogger
import com.nuvio.app.livetv.model.LiveTvLoadReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/** A redacted debug report that is ready to upload. */
data class DebugReport(
    val id: String,
    val fileName: String,
    val text: String
)

/**
 * Collects the information needed to investigate a user report: app and device versions, the
 * Live TV start-up report and the recent app log. Everything passes through
 * [DebugReportRedactor] before leaving the device.
 */
object DebugReportBuilder {

    suspend fun buildAndUpload(context: Context): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString().replace("-", "").take(8).uppercase(Locale.US)
        val raw = buildString {
            appendLine("Nuvio One M debug report")
            appendLine("ID: $id")
            appendLine("Generated: ${timestamp()}")

            section("APP")
            appendLine("version=${AppVersionConfig.VERSION_NAME} (${AppVersionConfig.VERSION_CODE})")
            appendLine("package=${context.packageName}")
            appendLine("debugBuild=${isDebuggable(context)}")
            appendLine("reportUpload=${if (DebugReportConfig.DISCORD_WEBHOOK.isBlank()) "not configured" else "configured"}")
            when (val auth = AuthRepository.state.value) {
                is AuthState.Authenticated -> {
                    appendLine("account=" + if (auth.isAnonymous) "local profile" else "signed in")
                    if (!auth.isAnonymous) {
                        // Stable, non-reversible: lets support find the sync row without the UUID
                        // appearing in a public report.
                        appendLine("accountSupportCode=${SupportCode.forUserId(auth.userId)}")
                    }
                }
                AuthState.Unauthenticated -> appendLine("account=signed out")
                AuthState.Loading -> appendLine("account=loading")
            }

            section("LAST CRASH")
            appendLine(LastCrashStore.read(context) ?: "No uncaught Java crash was recorded.")

            section("LAST EXIT")
            appendLine(lastExitInfo(context))

            section("DEVICE")
            appendLine("manufacturer=${Build.MANUFACTURER}")
            appendLine("brand=${Build.BRAND}")
            appendLine("model=${Build.MODEL}")
            appendLine("device=${Build.DEVICE}")
            appendLine("product=${Build.PRODUCT}")
            appendLine("android=${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("abis=${Build.SUPPORTED_ABIS.joinToString(",")}")
            val metrics = context.resources.displayMetrics
            appendLine("screen=${metrics.widthPixels}x${metrics.heightPixels} @ ${metrics.densityDpi}dpi")
            appendLine("locale=${Locale.getDefault().toLanguageTag()}")
            appendLine("timezone=${TimeZone.getDefault().id}")
            val packageManager = context.packageManager
            appendLine("leanback=${packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)}")
            appendLine("television=${packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)}")

            section("MEMORY")
            appendLine(memoryInfo(context))

            section("LIVE TV")
            appendLine(LiveTvLoadReport.shortText(limit = 8_000))

            section("RECENT LOG")
            appendLine(readRecentLogs())
        }
        val report = DebugReport(
            id = id,
            fileName = "debug-report-$id.txt",
            text = DebugReportRedactor.redact(raw, DebugReportConfig.DISCORD_WEBHOOK)
        )
        // Debug builds keep a copy so it can be pulled with `adb shell run-as` while testing.
        if (isDebuggable(context)) {
            runCatching { File(context.filesDir, report.fileName).writeText(report.text) }
        }
        DebugReportUploader.upload(report)
        // The crash reached the server with this report, so the next report can start clean.
        LastCrashStore.clear(context)
        id
    }

    private fun StringBuilder.section(name: String) {
        appendLine()
        appendLine("== $name ==")
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }.format(Date())

    private fun isDebuggable(context: Context): Boolean =
        context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0

    private fun memoryInfo(context: Context): String {
        val mb = 1024L * 1024L
        return buildString {
            runCatching {
                val manager = context.getSystemService(ActivityManager::class.java)
                val info = ActivityManager.MemoryInfo()
                manager?.getMemoryInfo(info)
                appendLine("ramTotalMb=${info.totalMem / mb} ramAvailableMb=${info.availMem / mb}")
                appendLine("lowMemory=${info.lowMemory} lowMemoryThresholdMb=${info.threshold / mb}")
                appendLine("lowRamDevice=${manager?.isLowRamDevice} memoryClass=${manager?.memoryClass} largeMemoryClass=${manager?.largeMemoryClass}")
            }.onFailure { appendLine("device memory unavailable: ${it.message}") }
            runCatching {
                val runtime = Runtime.getRuntime()
                appendLine("javaHeapUsedMb=${(runtime.totalMemory() - runtime.freeMemory()) / mb} javaHeapMaxMb=${runtime.maxMemory() / mb}")
                val memory = Debug.MemoryInfo()
                Debug.getMemoryInfo(memory)
                appendLine("pssKb=${memory.totalPss}")
            }.onFailure { appendLine("app memory unavailable: ${it.message}") }
        }.trimEnd()
    }

    @SuppressLint("NewApi") // Every call is behind the SDK check at the top of the function.
    private fun lastExitInfo(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return "Android 11 or newer is needed for recorded exit reasons."
        }
        val manager = context.getSystemService(ActivityManager::class.java) ?: return "ActivityManager was unavailable."
        return runCatching {
            val exits = manager.getHistoricalProcessExitReasons(context.packageName, 0, 8)
            if (exits.isEmpty()) {
                "No process exits were recorded."
            } else {
                exits.joinToString("\n\n") { info ->
                    buildString {
                        appendLine("reason=${exitReason(info.reason)} (${info.reason})")
                        appendLine("importance=${info.importance}")
                        appendLine("when=${formatTimestamp(info.timestamp)}")
                        info.description?.takeIf { it.isNotBlank() }?.let { appendLine("description=$it") }
                        runCatching {
                            info.traceInputStream?.bufferedReader()?.use { reader -> reader.readText().take(60_000) }
                        }.getOrNull()?.takeIf { it.isNotBlank() }?.let { appendLine(it) }
                    }
                }
            }
        }.getOrElse { "Could not read exit reasons: ${it.message}" }
    }

    @SuppressLint("NewApi") // Only reached from lastExitInfo, after its SDK check.
    private fun exitReason(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> "crash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "low memory"
        ApplicationExitInfo.REASON_SIGNALED -> "signaled"
        ApplicationExitInfo.REASON_EXIT_SELF -> "exit self"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "user requested"
        ApplicationExitInfo.REASON_OTHER -> "other"
        else -> "reason $reason"
    }

    private fun formatTimestamp(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }.format(Date(ms))

    /**
     * The app's own logcat tail plus the in-app log. Android only lets an app read its own UID's
     * log, which is exactly what a report needs. The crash buffer and the unfiltered tail are
     * included because they can still hold a crash from an earlier process of the same app, which
     * is the case that matters most when someone reports that the app closed.
     */
    private fun readRecentLogs(maxLines: Int = 600): String {
        val inApp = runCatching {
            InAppLogger.lines.value.takeLast(maxLines).joinToString("\n")
        }.getOrDefault("")
        val thisSession = logcatTail(buffer = "main", maxLines = 1_000, pid = Process.myPid())
        val olderSession = logcatTail(buffer = "main", maxLines = 20_000, pid = null)
            .takeIf { it.contains("FATAL EXCEPTION") || it.contains("beginning of crash") }
        val crashBuffer = logcatTail(buffer = "crash", maxLines = 300, pid = null)
        val sections = buildList {
            if (inApp.isNotBlank()) add("-- in-app log --\n$inApp")
            if (thisSession.isNotBlank()) add("-- logcat, this session --\n$thisSession")
            if (!olderSession.isNullOrBlank()) add("-- logcat, this app --\n$olderSession")
            if (crashBuffer.isNotBlank()) add("-- logcat, crash buffer --\n$crashBuffer")
        }
        return sections.joinToString("\n\n").takeLast(180_000)
            .ifBlank { "No recent log lines were available." }
    }

    private fun logcatTail(buffer: String, maxLines: Int, pid: Int?): String = runCatching {
        val command = mutableListOf("logcat", "-d", "-v", "time", "-b", buffer, "-t", maxLines.toString())
        if (pid != null) command += "--pid=$pid" else command += "--uid=${Process.myUid()}"
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        // The YouTube engine logs a "Path not found" line for every optional JSON field it reads.
        // One video lookup can fill the buffer and push the app's own lines out of the report.
        output.lineSequence()
            .filterNot { it.contains("JsonPathTypeAdapter") }
            .joinToString("\n")
            .trimEnd()
    }.getOrDefault("")
}

/**
 * Removes credentials and personal data from a report. Addon and stream URLs keep only their
 * host, because the path and query usually contain a user's configuration token.
 */
object DebugReportRedactor {

    private val bearer = Regex("""(?i)\b(bearer\s+)[A-Za-z0-9._~+/=-]{8,}""")
    private val keyValue = Regex(
        """(?i)\b(authorization|proxy-authorization|x-api-key|set-cookie|cookie|access[_-]?token|refresh[_-]?token|api[_-]?key|apikey|debrid[_-]?key|premiumize[_-]?key|torbox[_-]?key|rd[_-]?key|password|passwd|secret|token|auth)(\s*[=:]\s*)("[^"]*"|'[^']*'|[^\s&,;]+)"""
    )
    private val querySecret = Regex(
        """(?i)([?&](?:access[_-]?token|refresh[_-]?token|api[_-]?key|apikey|token|secret|password|auth|code)=)[^&\s]+"""
    )
    private val email = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val url = Regex("""https?://[^\s"'<>]+""", RegexOption.IGNORE_CASE)
    private val ipv4 = Regex(
        """(?<![\d.])(?:(?:25[0-5]|2[0-4]\d|1?\d?\d)\.){3}(?:25[0-5]|2[0-4]\d|1?\d?\d)(?![\d.])"""
    )
    private val uuid = Regex("""(?i)\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b""")
    private val longToken = Regex("""\b[A-Za-z0-9_-]{48,}\b""")

    fun redact(text: String, webhook: String): String {
        var out = text
        if (webhook.isNotBlank()) out = out.replace(webhook, "[redacted webhook]")
        out = bearer.replace(out) { "${it.groupValues[1]}[redacted]" }
        out = keyValue.replace(out) { "${it.groupValues[1]}${it.groupValues[2]}[redacted]" }
        out = querySecret.replace(out) { "${it.groupValues[1]}[redacted]" }
        out = url.replace(out) { match ->
            val value = match.value
            val scheme = value.indexOf("://")
            if (scheme < 0) {
                value
            } else {
                val hostStart = scheme + 3
                val hostEnd = value.indexOfAny(charArrayOf('/', '?', '#'), hostStart)
                    .let { if (it < 0) value.length else it }
                val host = value.substring(hostStart, hostEnd).substringAfterLast('@')
                value.substring(0, hostStart) + host + "/[redacted]"
            }
        }
        out = email.replace(out, "[email]")
        out = ipv4.replace(out, "[ip]")
        out = uuid.replace(out, "[id]")
        out = longToken.replace(out, "[redacted]")
        return out
    }
}
