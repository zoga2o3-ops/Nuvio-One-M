package com.nuvio.app.core.diagnostics

import android.content.Context
import com.nuvio.app.core.build.AppVersionConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last uncaught Java crash on disk so the next debug report can include it. The app's
 * logcat tail only covers the current process; a crash that closed the app would otherwise leave
 * no trace in the report.
 */
internal object LastCrashStore {
    private const val FILE_NAME = "last_crash.txt"
    private const val MAX_CHARS = 200_000

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val text = buildString {
                    appendLine("Time: ${formatTimestamp(System.currentTimeMillis())}")
                    appendLine("Version: ${AppVersionConfig.VERSION_NAME} (${AppVersionConfig.VERSION_CODE})")
                    appendLine("Thread: ${thread.name}")
                    appendLine(error.stackTraceToString())
                }
                File(app.filesDir, FILE_NAME).writeText(text.take(MAX_CHARS))
            }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    fun read(context: Context): String? = runCatching {
        File(context.applicationContext.filesDir, FILE_NAME)
            .takeIf { it.isFile }
            ?.readText()
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.applicationContext.filesDir, FILE_NAME).delete() }
    }

    private fun formatTimestamp(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).apply {
            timeZone = java.util.TimeZone.getDefault()
        }.format(Date(ms))
}
