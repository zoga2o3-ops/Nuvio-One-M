package com.nuvio.app.core.diagnostics

/**
 * Filled in by the Android app: sends a redacted debug report through the same Discord webhook
 * the TV app uses. Available only when the build has a webhook configured.
 */
object DebugReportHost {
    var available: Boolean = false
    var send: (suspend () -> Result<String>)? = null
}
