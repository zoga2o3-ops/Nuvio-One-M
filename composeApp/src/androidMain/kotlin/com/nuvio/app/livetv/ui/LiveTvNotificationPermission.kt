package com.nuvio.app.livetv.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.nuvio.app.livetv.reminders.LiveTvReminders

/** The notification permission state, and a way to ask for it (or fix it in system settings). */
internal class LiveTvNotificationPermission(
    val canNotify: Boolean,
    val ask: () -> Unit,
)

/**
 * Remembers whether Live TV may post notifications. [LiveTvNotificationPermission.ask] shows the
 * Android 13 permission prompt the first time and opens the app's notification settings once the
 * prompt has been used (or when notifications were switched off some other way).
 */
@Composable
internal fun rememberLiveTvNotificationPermission(): LiveTvNotificationPermission {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var canNotify by remember(context) { mutableStateOf(LiveTvReminders.canNotify(context)) }

    // Coming back from the system settings (or the permission dialog) should update the row.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canNotify = LiveTvReminders.canNotify(context)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        canNotify = granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (!granted) {
            Toast.makeText(
                context,
                "Notifications are off. Turn them on in system settings to get alerts.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    val ask: () -> Unit = {
        LiveTvReminders.ensureChannels(context)
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        when {
            needsPermission -> {
                val asked = permissionPrefs(context).getBoolean(KEY_ASKED, false)
                val rationale = activity != null &&
                    ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
                if (asked && !rationale) {
                    openNotificationSettings(context)
                } else {
                    permissionPrefs(context).edit().putBoolean(KEY_ASKED, true).apply()
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            !NotificationManagerCompat.from(context).areNotificationsEnabled() -> openNotificationSettings(context)
            else -> canNotify = true
        }
    }

    return LiveTvNotificationPermission(canNotify, ask)
}

private const val PREFS = "live_tv_notifications"
private const val KEY_ASKED = "permission_asked"

private fun permissionPrefs(context: Context) =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun openNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
