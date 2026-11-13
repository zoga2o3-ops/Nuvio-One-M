package com.nuvio.app.core.diagnostics

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.core.build.AppVersionConfig
import com.nuvio.app.core.logging.InAppLogger
import com.nuvio.app.livetv.model.LiveTvLoadReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
            appendLine(
                "account=" + when (AuthRepository.state.value) {
                    is AuthState.Authenticated -> "signed in"
                    AuthState.Unauthenticated -> "signed out"
                    AuthState.Loading -> "loading"
                }
            )

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
        DebugReportUploader.upload(report)
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

    /**
     * The app's own logcat tail plus the in-app log. Android only lets an app read its own UID's
     * log, which is exactly what a report needs.
     */
    private fun readRecentLogs(maxLines: Int = 600): String {
        val inApp = runCatching {
            InAppLogger.lines.value.takeLast(maxLines).joinToString("\n")
        }.getOrDefault("")
        val logcat = runCatching {
            val process = ProcessBuilder(
                "logcat", "-d", "-v", "time", "-t", maxLines.toString(), "--pid=${Process.myPid()}"
            ).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            output.trimEnd().takeLast(120_000)
        }.getOrDefault("")
        return listOf(inApp, logcat).filter { it.isNotBlank() }.joinToString("\n\n")
            .ifBlank { "No recent log lines were available." }
    }
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
